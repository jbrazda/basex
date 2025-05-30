package org.basex.query.expr;

import static org.basex.query.func.Function.*;

import java.util.function.*;

import org.basex.query.*;
import org.basex.query.CompileContext.*;
import org.basex.query.func.Function;
import org.basex.query.util.*;
import org.basex.query.util.list.*;
import org.basex.query.value.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * Abstract array expression.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public abstract class Arr extends ParseExpr {
  /** Expressions. */
  public Expr[] exprs;

  /**
   * Constructor.
   * @param info input info (can be {@code null})
   * @param seqType sequence type
   * @param exprs expressions
   */
  protected Arr(final InputInfo info, final SeqType seqType, final Expr... exprs) {
    super(info, seqType);
    this.exprs = exprs;
  }

  @Override
  public void checkUp() throws QueryException {
    checkNoneUp(exprs);
  }

  @Override
  public Expr compile(final CompileContext cc) throws QueryException {
    final int el = exprs.length;
    for(int e = 0; e < el; e++) exprs[e] = exprs[e].compile(cc);
    return optimize(cc);
  }

  @Override
  public boolean has(final Flag... flags) {
    if(flags.length != 0) {
      for(final Expr expr : exprs) {
        if(expr.has(flags)) return true;
      }
    }
    return false;
  }

  @Override
  public boolean inlineable(final InlineContext ic) {
    for(final Expr expr : exprs) {
      if(!expr.inlineable(ic)) return false;
    }
    return true;
  }

  @Override
  public VarUsage count(final Var var) {
    return VarUsage.sum(var, exprs);
  }

  @Override
  public Expr inline(final InlineContext ic) throws QueryException {
    return ic.inline(exprs) ? optimize(ic.cc) : null;
  }

  /**
   * Creates a deep copy of the given array.
   * @param <T> element type
   * @param cc compilation context
   * @param vm variable mapping
   * @param arr array to copy
   * @return deep copy of the array
   */
  @SuppressWarnings("unchecked")
  public static <T extends Expr> T[] copyAll(final CompileContext cc, final IntObjectMap<Var> vm,
      final T[] arr) {

    final T[] copy = arr.clone();
    final int cl = copy.length;
    for(int c = 0; c < cl; c++) copy[c] = (T) copy[c].copy(cc, vm);
    return copy;
  }

  /**
   * Returns true if all expressions are values (possibly of small size).
   * @param limit check if result size of any expression is not too large
   * @param cc compilation context
   * @return result of check
   */
  protected boolean values(final boolean limit, final CompileContext cc) {
    return cc.values(limit, exprs);
  }

  /**
   * Simplifies all expressions for requests of the specified type.
   * @param mode mode of simplification
   * @param cc compilation context
   * @return simplified or original expressions
   * @throws QueryException query exception
   */
  protected final Expr[] simplifyAll(final Simplify mode, final CompileContext cc)
      throws QueryException {

    boolean changed = false;
    final ExprList list = new ExprList(exprs.length);
    for(final Expr expr : exprs) {
      final Expr ex = expr.simplifyFor(mode, cc);
      if(ex != expr) changed = true;
      list.add(ex);
    }
    return changed ? list.finish() : exprs;
  }

  /**
   * Flattens nested expressions.
   * @param cc compilation context
   */
  protected final void flatten(final CompileContext cc) {
    // flatten nested expressions
    final ExprList list = new ExprList(exprs.length);
    final Class<? extends Arr> clazz = getClass();
    for(final Expr expr : exprs) {
      if(clazz.isInstance(expr)) {
        list.add(expr.args());
        cc.info(QueryText.OPTFLAT_X_X, expr, (Supplier<?>) this::description);
      } else {
        list.add(expr);
      }
    }
    exprs = list.finish();
  }

  /**
   * Removes empty expressions.
   * @param cc compilation context
   */
  protected final void removeEmpty(final CompileContext cc) {
    if(!((Checks<Expr>) expr -> expr == Empty.VALUE).any(exprs)) return;

    final ExprList list = new ExprList(exprs.length - 1);
    for(final Expr expr : exprs) {
      if(expr != Empty.VALUE) list.add(expr);
    }
    cc.info(QueryText.OPTREMOVE_X_X, Empty.VALUE, (Supplier<?>) this::description);
    exprs = list.finish();
  }

  /**
   * Returns the first expression that yields an empty sequence. If all expressions return non-empty
   * results, the original expression is returned.
   * @return empty or original expression
   */
  final Expr emptyExpr() {
    // pre-evaluate if one value is empty (e.g.: () = local:expensive() )
    for(final Expr expr : exprs) {
      if(expr.seqType().zero()) return expr;
    }
    return this;
  }

  /**
   * Tries to merge consecutive EBV tests.
   * @param or union or intersection
   * @param predicate predicate test
   * @param cc compilation context
   * @return {@code true} if evaluation can be skipped
   * @throws QueryException query exception
   */
  final boolean optimizeEbv(final boolean or, final boolean predicate, final CompileContext cc)
      throws QueryException {

    final ExprList list = new ExprList(exprs.length);
    boolean pos = false;
    for(final Expr expr : exprs) {
      // pre-evaluate values
      if(expr instanceof Value && (!predicate || !expr.seqType().mayBeNumber())) {
        // skip evaluation: true() or $bool  ->  true()
        if(expr.test(cc.qc, info, 0) == or) return true;
        // ignore result: true() and $bool  ->  $bool
        cc.info(QueryText.OPTREMOVE_X_X, expr, (Supplier<?>) this::description);
      } else if(!pos && list.contains(expr) && !expr.has(Flag.NDT)) {
        // ignore duplicates: A[$node and $node]  ->  A[$node]
        cc.info(QueryText.OPTREMOVE_X_X, expr, (Supplier<?>) this::description);
      } else {
        list.add(expr);
        // preserve entries after positional predicates
        if(predicate && !pos) pos = mayBePositional(expr);
      }
    }
    exprs = list.next();

    if(!(predicate && has(Flag.POS))) {
      final Class<? extends Arr> clazz = or ? And.class : Or.class;
      final QueryBiFunction<Boolean, Expr[], Expr> func = (invert, args) ->
        invert == or ? new And(info, args) : new Or(info, args);
      final Expr tmp = rewrite(clazz, func, cc);
      if(tmp != null) {
        exprs = new Expr[] { tmp };
        cc.info(QueryText.OPTREWRITE_X_X, (Supplier<?>) this::description, this);
      }
    }

    list.add(exprs);
    for(int l = 0; l < list.size(); l++) {
      for(int m = l + 1; m < list.size(); m++) {
        final Expr expr1 = list.get(l), expr2 = list.get(m);
        if(!expr1.has(Flag.NDT) && !(predicate && expr1.has(Flag.POS))) {
          // A or not(A)  ->  true()
          // A[not(B)][B]  ->  ()
          // empty(A) or exists(A)  ->  true()
          if(contradict(expr1, expr2, true) || contradict(expr2, expr1, true)) return true;

          // 'a'[. = 'a' or . = 'b']  ->  'a'[. = ('a', 'b')]
          // $v[. != 'a'][. != 'b']  ->  $v[not(. = ('a', 'b')]
          final Expr merged = expr1.mergeEbv(expr2, or, cc);
          if(merged != null) {
            cc.info(QueryText.OPTSIMPLE_X_X, (Supplier<?>) this::description, this);
            list.set(l, merged);
            list.remove(m--);
          }
        }
      }
    }
    exprs = list.next();

    // not($a) and not($b)  ->  not($a or $b)
    final Function not = NOT;
    final Checks<Expr> fnNot = ex -> not.is(ex) && !(predicate && ex.has(Flag.POS));
    if(exprs.length > 1 && fnNot.all(exprs)) {
      final ExprList tmp = new ExprList(exprs.length);
      for(final Expr expr : exprs) tmp.add(expr.arg(0));
      final Expr expr = or ? new And(info, tmp.finish()) : new Or(info, tmp.finish());
      exprs = list.add(cc.function(not, info, expr.optimize(cc))).finish();
    }
    return false;
  }

  /**
   * Checks if the specified expressions contradict each other.
   * @param expr1 first expression
   * @param expr2 second expression
   * @param ebv consider ebv checks
   * @return result of check
   */
  static boolean contradict(final Expr expr1, final Expr expr2, final boolean ebv) {
    // boolean(A), not(A)
    Expr arg = BOOLEAN.is(expr1) ? expr1.arg(0) : expr1;
    if(NOT.is(expr2) && expr2.arg(0).equals(arg)) return true;

    // empty(A), exists(A)
    arg = EXISTS.is(expr1) ? expr1.arg(0) : ebv && expr1.seqType().type instanceof NodeType ?
      expr1 : null;
    if(EMPTY.is(expr2) && arg != null && expr2.arg(0).equals(arg)) return true;

    // A = B, A != B
    return expr1 instanceof Cmp && expr2 instanceof final Cmp cmp && expr1.equals(cmp.invert());
  }

  /**
   * Rewrites logical and set expressions.
   * @param inverse inverse operator
   * @param newExpr function for creating a new expression
   * @param cc compilation context
   * @return optimized expression or null
   * @throws QueryException query exception
   */
  final Expr rewrite(final Class<? extends Arr> inverse,
      final QueryBiFunction<Boolean, Expr[], Expr> newExpr, final CompileContext cc)
      throws QueryException {

    // skip if only one operand is left, or if children have no operands that can be optimized
    if(exprs.length < 2 || !((Checks<Expr>) inverse::isInstance).any(exprs))
      return null;

    // check if expressions have common operands
    final java.util.function.Function<Expr, ExprList> entries = ex ->
      new ExprList().add(inverse.isInstance(ex) ? ex.args() : new Expr[] { ex });
    final int el = exprs.length;
    final ExprList lefts = new ExprList().add(entries.apply(exprs[0]));
    for(int e = 1; e < el && !lefts.isEmpty(); ++e) {
      final ExprList curr = entries.apply(exprs[e]);
      for(int c = lefts.size() - 1; c >= 0; c--) {
        if(!curr.contains(lefts.get(c))) lefts.remove(c);
      }
    }
    if(lefts.isEmpty()) return null;

    // common operands found: recombine expressions
    final QueryBiFunction<Boolean, Expr[], Expr> f = (invert, args) ->
      args.length == 1 ? args[0] : newExpr.apply(invert, args).optimize(cc);

    final Expr left = f.apply(true, lefts.toArray());
    final ExprList rights = new ExprList(exprs.length);
    for(final Expr expr : exprs) {
      final ExprList curr = entries.apply(expr).removeAll(lefts);
      if(curr.isEmpty()) {
        // no additional tests: return common tests
        // A intersect (A union B)  ->  A
        // (A and B) or (A and B and C)  ->  A
        return left.seqType().type instanceof NodeType && !left.ddo() ?
          cc.function(Function.DISTINCT_ORDERED_NODES, info, left) : left;
      } else if(curr.size() == 1) {
        // single additional test: add this test
        // (A and B) or (A and C)  ->  A and (B or C)
        rights.add(curr.get(0));
      } else {
        // multiple additional tests: simplify expression
        // (A and B) or (A and C and D)  ->  A and (B or (C and D))
        rights.add(f.apply(true, curr.finish()));
      }
    }
    return f.apply(true, new Expr[] { left, f.apply(false, rights.finish()) });
  }

  /**
   * Checks if the specified expression may be positional.
   * @param expr expression
   * @return result of check
   */
  protected static boolean mayBePositional(final Expr expr) {
    return expr.seqType().mayBeNumber() || expr.has(Flag.POS);
  }

  @Override
  public boolean accept(final ASTVisitor visitor) {
    return visitAll(visitor, exprs);
  }

  @Override
  public final Expr[] args() {
    return exprs;
  }

  /**
   * Re-assigns the rewritten version of an argument/operand.
   * @param a index of argument
   * @param rewrite function for rewriting the argument
   * @throws QueryException query exception
   */
  public final void arg(final int a, final QueryFunction<Expr, Expr> rewrite)
      throws QueryException {
    exprs[a] = rewrite.apply(arg(a));
  }

  @Override
  public int exprSize() {
    int size = 1;
    for(final Expr expr : exprs) size += expr.exprSize();
    return size;
  }

  /**
   * {@inheritDoc}
   * Must be overwritten by implementing class.
   */
  @Override
  public boolean equals(final Object obj) {
    return obj instanceof final Arr arr && Array.equals(exprs, arr.exprs);
  }

  @Override
  public void toXml(final QueryPlan plan) {
    plan.add(plan.create(this), exprs);
  }
}
