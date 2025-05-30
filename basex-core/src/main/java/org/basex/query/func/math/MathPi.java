package org.basex.query.func.math;

import static java.lang.StrictMath.*;

import org.basex.query.*;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class MathPi extends MathFn {
  @Override
  public Item item(final QueryContext qc, final InputInfo ii) {
    return Dbl.get(PI);
  }
}
