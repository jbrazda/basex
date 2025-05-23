package org.basex.query.expr.gflwor;

import static org.basex.query.QueryText.*;

import java.util.*;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.util.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * The GFLWOR {@code group by} expression.
 *
 * @author BaseX Team, BSD License
 * @author Leo Woerteler
 */
public final class GroupBy extends Clause {
  /** Grouping specs. */
  private final GroupSpec[] specs;
  /** Non-grouping variable expressions. */
  private Expr[] preExpr;
  /** Non-grouping variables. */
  private Var[] post;
  /** Number of non-occluded grouping variables. */
  private final int nonOcc;

  /**
   * Constructor.
   * @param specs grouping specs
   * @param pre references to pre-grouping variables
   * @param post post-grouping variables
   * @param info input info (can be {@code null})
   */
  public GroupBy(final GroupSpec[] specs, final VarRef[] pre, final Var[] post,
      final InputInfo info) {

    super(info, SeqType.ITEM_ZM, vars(specs, post));
    this.specs = specs;
    this.post = post;
    preExpr = Array.copy(pre, new Expr[pre.length]);
    int n = 0;
    for(final GroupSpec spec : specs) {
      if(!spec.occluded) n++;
    }
    nonOcc = n;
  }

  /**
   * Copy constructor.
   * @param specs grouping specs
   * @param pre pre-grouping expressions
   * @param post post-grouping variables
   * @param nonOcc number of non-occluded grouping variables
   * @param info input info (can be {@code null})
   */
  private GroupBy(final GroupSpec[] specs, final Expr[] pre, final Var[] post, final int nonOcc,
      final InputInfo info) {
    super(info, SeqType.ITEM_ZM, vars(specs, post));
    this.specs = specs;
    preExpr = pre;
    this.post = post;
    this.nonOcc = nonOcc;
  }

  /**
   * Gathers all declared variables.
   * @param gs grouping specs
   * @param vs non-grouping variables
   * @return declared variables
   */
  private static Var[] vars(final GroupSpec[] gs, final Var[] vs) {
    final int gl = gs.length, vl = vs.length;
    final Var[] vars = new Var[gl + vl];
    for(int g = 0; g < gl; g++) vars[g] = gs[g].var;
    Array.copyFromStart(vs, vl, vars, gl);
    return vars;
  }

  @Override
  Eval eval(final Eval sub) {
    return new Eval() {
      /** Groups to iterate over. */
      private Group[] groups;
      /** Current position. */
      private int pos;

      @Override
      public boolean next(final QueryContext qc) throws QueryException {
        if(groups == null) groups = init(qc);
        if(pos == groups.length) return false;

        final Group curr = groups[pos];
        // be nice to the garbage collector
        groups[pos++] = null;

        int p = 0;
        for(final GroupSpec spec : specs) {
          if(!spec.occluded) {
            final Item key = curr.key[p++];
            qc.set(spec.var, key == null ? Empty.VALUE : key);
          }
        }
        final int pl = post.length;
        for(int i = 0; i < pl; i++) qc.set(post[i], curr.ngv[i].value(preExpr[i]));
        return true;
      }

      /**
       * Builds up the groups.
       * @param qc query context
       * @throws QueryException query exception
       */
      private Group[] init(final QueryContext qc) throws QueryException {
        final ArrayList<Group> grps = new ArrayList<>();
        final IntObjectMap<Group> map = new IntObjectMap<>();
        final DeepEqual[] deeps = new DeepEqual[nonOcc];
        int c = 0;
        for(final GroupSpec spec : specs) {
          if(!spec.occluded) deeps[c++] = new DeepEqual(info, spec.coll, qc);
        }

        while(sub.next(qc)) {
          final Item[] key = new Item[nonOcc];
          int p = 0, hash = 1;
          for(final GroupSpec spec : specs) {
            final Item atom = spec.atomItem(qc, info);
            if(!spec.occluded) {
              key[p++] = atom;
              // If the values are compared using a special collation, we let them collide
              // here and let the comparison do all the work later.
              // This enables other non-collation specs to avoid the collision.
              hash = 31 * hash + (atom.isEmpty() || spec.coll != null ? 0 : atom.hashCode());
            }
            qc.set(spec.var, atom);
          }

          // find the group for this key
          final Group fst;
          Group grp = null;
          // no collations, so we can use hashing
          for(Group g = fst = map.get(hash); g != null; g = g.next) {
            if(eq(key, g.key, deeps)) {
              grp = g;
              break;
            }
          }

          final int pl = preExpr.length;
          if(grp == null) {
            // new group, add it to the list
            final ValueBuilder[] ngs = new ValueBuilder[pl];
            final int nl = ngs.length;
            for(int n = 0; n < nl; n++) ngs[n] = new ValueBuilder(qc);
            grp = new Group(key, ngs);
            grps.add(grp);

            // insert the group into the hash table
            if(fst == null) {
              map.put(hash, grp);
            } else {
              final Group nxt = fst.next;
              fst.next = grp;
              grp.next = nxt;
            }
          }

          // add values of non-grouping variables to the group
          for(int g = 0; g < pl; g++) {
            grp.ngv[g].add(preExpr[g].value(qc));
          }
        }

        // we're finished, copy the array so the list can be garbage-collected
        return grps.toArray(Group[]::new);
      }

      /**
       * Checks two keys for equality.
       * @param items1 first keys
       * @param items2 second keys
       * @param deeps deep equality comparisons
       * @return {@code true} if the compare as equal, {@code false} otherwise
       * @throws QueryException query exception
       */
      private static boolean eq(final Item[] items1, final Item[] items2, final DeepEqual[] deeps)
          throws QueryException {

        final int il = items1.length;
        for(int i = 0; i < il; i++) {
          final Item item1 = items1[i], item2 = items2[i];
          final boolean empty1 = item1.isEmpty(), empty2 = item2.isEmpty();
          if(empty1 ^ empty2 || !empty1 && !deeps[i].equal(item1, item2)) return false;
        }
        return true;
      }
    };
  }

  @Override
  public boolean has(final Flag... flags) {
    for(final Expr expr : preExpr) {
      if(expr.has(flags)) return true;
    }
    for(final GroupSpec spec : specs) {
      if(spec.has(flags)) return true;
    }
    return false;
  }

  @Override
  public GroupBy compile(final CompileContext cc) throws QueryException {
    for(final Expr expr : preExpr) expr.compile(cc);
    for(final GroupSpec spec : specs) spec.compile(cc);
    return optimize(cc);
  }

  @Override
  public GroupBy optimize(final CompileContext cc) throws QueryException {
    final int pl = preExpr.length;
    for(int p = 0; p < pl; p++) {
      post[p].refineType(preExpr[p].seqType().union(Occ.ONE_OR_MORE), cc);
    }
    exprType.assign(SeqType.union(specs, true));
    return this;
  }

  @Override
  public boolean inlineable(final InlineContext ic) {
    for(final GroupSpec spec : specs) {
      if(!spec.inlineable(ic)) return false;
    }
    return true;
  }

  @Override
  public VarUsage count(final Var var) {
    return VarUsage.sum(var, specs).plus(VarUsage.sum(var, preExpr));
  }

  @Override
  public Clause inline(final InlineContext ic) throws QueryException {
    // inline both grouping specs and non-grouping variable expressions
    final boolean changed1 = ic.inline(specs), changed2 = ic.inline(preExpr);
    return changed1 || changed2 ? optimize(ic.cc) : null;
  }

  @Override
  public GroupBy copy(final CompileContext cc, final IntObjectMap<Var> vm) {
    // copy the pre-grouping expressions
    final Expr[] pEx = Arr.copyAll(cc, vm, preExpr);

    // create fresh copies of the post-grouping variables
    final Var[] ps = new Var[post.length];
    final int pl = ps.length;
    for(int p = 0; p < pl; p++) ps[p] = cc.copy(post[p], vm);

    // done
    return copyType(new GroupBy(Arr.copyAll(cc, vm, specs), pEx, ps, nonOcc, info));
  }

  @Override
  public boolean accept(final ASTVisitor visitor) {
    if(!visitAll(visitor, specs)) return false;
    for(final Expr ng : preExpr) {
      if(!ng.accept(visitor)) return false;
    }
    for(final Var ng : post) {
      if(!visitor.declared(ng)) return false;
    }
    return true;
  }

  @Override
  boolean clean(final IntObjectMap<Var> decl, final BitArray used) {
    final int len = preExpr.length;
    for(int p = 0; p < post.length; p++) {
      if(!used.get(post[p].id)) {
        preExpr = Array.remove(preExpr, p);
        post = Array.remove(post, p--);
      }
    }
    return preExpr.length < len;
  }

  @Override
  boolean skippable(final Clause cl) {
    return false;
  }

  /**
   * Returns a group specification that can be further rewritten and simplified.
   * @return group specification
   */
  GroupSpec group() {
    if(specs.length == 1 && post.length == 0) {
      final GroupSpec spec = specs[0];
      final SeqType st = spec.expr.seqType();
      if(spec.coll == null && spec.var.declType == null && st.one() && !st.mayBeArray()) {
        return spec;
      }
    }
    return null;
  }

  @Override
  public void checkUp() throws QueryException {
    checkNoneUp(preExpr);
    checkNoneUp(specs);
  }

  @Override
  public void calcSize(final long[] minMax) {
    minMax[0] = Math.min(minMax[0], 1);
  }

  @Override
  public int exprSize() {
    int size = 0;
    for(final Expr expr : preExpr) size += expr.exprSize();
    for(final Expr spec : specs) size += spec.exprSize();
    return size;
  }

  @Override
  public boolean equals(final Object obj) {
    return this == obj || obj instanceof final GroupBy group && Array.equals(specs, group.specs) &&
        Array.equals(preExpr, group.preExpr) && Array.equals(post, group.post);
  }

  @Override
  public void toXml(final QueryPlan plan) {
    plan.add(plan.create(this), specs);
  }

  @Override
  public void toString(final QueryString qs) {
    final int pl = post.length;
    for(int p = 0; p < pl; p++) {
      qs.token(LET).token("(: post-group :)").token(post[p]).token(":=").token(preExpr[p]);
    }
    qs.token(GROUP).token(BY).tokens(specs, SEP);
  }
}
