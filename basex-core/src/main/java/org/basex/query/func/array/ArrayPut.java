package org.basex.query.func.array;

import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.array.Array;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-16, BSD License
 * @author Christian Gruen
 */
public final class ArrayPut extends ArrayFn {
  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    Array array = toArray(exprs[0], qc);
    final long p = checkPos(array, toLong(exprs[1], qc), false);
    final Value v = qc.value(exprs[2]);
    return array.remove(p).insertBefore(p, v);
  }
}
