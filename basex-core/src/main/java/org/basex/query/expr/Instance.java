package org.basex.query.expr;

import static org.basex.query.QueryText.*;

import org.basex.query.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * Instance test.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class Instance extends Single {
  /** Sequence type to check for. */
  public final SeqType seqType;
  /** Check: 1: only check occurrence indicator, 2: only check item type. */
  private int check;

  /**
   * Constructor.
   * @param info input info (can be {@code null})
   * @param expr expression
   * @param seqType sequence type to check for
   */
  public Instance(final InputInfo info, final Expr expr, final SeqType seqType) {
    super(info, expr, SeqType.BOOLEAN_O);
    this.seqType = seqType;
  }

  @Override
  public Expr compile(final CompileContext cc) throws QueryException {
    return super.compile(cc).optimize(cc);
  }

  @Override
  public Expr optimize(final CompileContext cc) throws QueryException {
    // check value
    if(expr instanceof Value) return cc.preEval(this);

    // check static type
    final SeqType et = expr.seqType();
    if(!expr.has(Flag.NDT)) {
      // (1, 2)[. = 1] instance of xs:numeric*
      if(et.instanceOf(seqType)) return cc.replaceWith(this, Bln.TRUE);
      // (1, 2)[. = 1] instance of xs:string
      if(et.intersect(seqType) == null) return cc.replaceWith(this, Bln.FALSE);
    }

    // 1: only check item type, 2: only check occurrence indicator
    check = et.with(seqType.occ).instanceOf(seqType) ? 1 : et.occ.instanceOf(seqType.occ) ? 2 : 0;
    return this;
  }

  @Override
  public Bln item(final QueryContext qc, final InputInfo ii) throws QueryException {
    return Bln.get(test(qc, ii, 0));
  }

  @Override
  public boolean test(final QueryContext qc, final InputInfo ii, final long pos)
      throws QueryException {
    // check instance of value
    final Iter iter = expr.iter(qc);
    if(iter.valueIter()) return seqType.instance(iter.value(qc, expr));

    // only check item type
    if(check == 2) {
      for(Item item; (item = qc.next(iter)) != null;) {
        if(!seqType.instance(item)) return false;
      }
      return true;
    }

    // only check occurrence indicator
    final long max = seqType.occ.max;
    if(check == 1) return iter.next() == null ? !seqType.oneOrMore() :
      max > 1 || max > 0 && iter.next() == null;

    // check both occurrence indicator and type
    long c = 0;
    for(Item item; (item = qc.next(iter)) != null;) {
      if(++c > max || !seqType.instance(item)) return false;
    }
    return c != 0 || !seqType.oneOrMore();
  }

  @Override
  public Instance copy(final CompileContext cc, final IntObjectMap<Var> vm) {
    final Instance ex = copyType(new Instance(info, expr.copy(cc, vm), seqType));
    ex.check = check;
    return ex;
  }

  @Override
  public boolean equals(final Object obj) {
    return this == obj || obj instanceof final Instance inst && seqType.eq(inst.seqType) &&
        super.equals(obj);
  }

  @Override
  public void toXml(final QueryPlan plan) {
    plan.add(plan.create(this, OF, seqType), expr);
  }

  @Override
  public void toString(final QueryString qs) {
    qs.token(expr).token(INSTANCE).token(OF).token(seqType);
  }
}
