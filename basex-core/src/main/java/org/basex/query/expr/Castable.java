package org.basex.query.expr;

import static org.basex.query.QueryText.*;

import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * Castable expression.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class Castable extends Convert {
  /**
   * Constructor.
   * @param info input info (can be {@code null})
   * @param expr expression
   * @param seqType sequence type to check
   */
  public Castable(final InputInfo info, final Expr expr, final SeqType seqType) {
    super(info, expr, seqType, SeqType.BOOLEAN_O);
  }

  @Override
  public Expr optimize(final CompileContext cc) throws QueryException {
    super.optimize(cc);

    final SeqType st = castType();
    final Boolean test = castable(st);
    if(test != null) return cc.replaceWith(this, Bln.get(test));

    final Expr arg = simplify(st, cc);
    if(arg != null) return new Castable(info, arg, seqType).optimize(cc);

    return expr instanceof Value ? cc.preEval(this) : this;
  }

  @Override
  public Bln item(final QueryContext qc, final InputInfo ii) throws QueryException {
    return Bln.get(test(qc, ii, 0));
  }

  @Override
  public boolean test(final QueryContext qc, final InputInfo ii, final long pos)
      throws QueryException {
    return seqType.cast(expr.atomValue(qc, info), false, qc, info) != null;
  }

  @Override
  public Expr copy(final CompileContext cc, final IntObjectMap<Var> vm) {
    return copyType(new Castable(info, expr.copy(cc, vm), seqType));
  }

  @Override
  public boolean equals(final Object obj) {
    return this == obj || obj instanceof final Castable cst && seqType.eq(cst.seqType) &&
        super.equals(obj);
  }

  @Override
  public void toString(final QueryString qs) {
    qs.token("(").token(expr).token(CASTABLE).token(AS).token(seqType).token(')');
  }
}
