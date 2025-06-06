package org.basex.query.func.array;

import org.basex.query.*;
import org.basex.query.CompileContext.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.iter.*;
import org.basex.query.value.*;
import org.basex.query.value.type.*;

/**
 * Function implementation.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class ArrayItems extends StandardFunc {
  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    return toArray(arg(0), qc).itemsIter();
  }

  @Override
  public Value value(final QueryContext qc) throws QueryException {
    return toArray(arg(0), qc).items(qc);
  }

  @Override
  protected Expr opt(final CompileContext cc) {
    final Expr array = arg(0);
    final Type type = array.seqType().type;
    if(type instanceof final ArrayType at) {
      final SeqType vt = at.valueType();
      exprType.assign(vt.with(Occ.ZERO_OR_MORE), vt.one() ? array.structSize() : -1);
    }
    return this;
  }

  @Override
  public Expr simplifyFor(final Simplify mode, final CompileContext cc) throws QueryException {
    final Expr array = arg(0);
    final Expr expr = mode.oneOf(Simplify.STRING, Simplify.NUMBER, Simplify.DATA) &&
        array.seqType().type instanceof ArrayType ? array : this;
    return cc.simplify(this, expr, mode);
  }
}
