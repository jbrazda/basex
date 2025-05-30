package org.basex.query.func.user;

import org.basex.query.*;
import org.basex.query.value.item.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class UserCurrent extends UserFn {
  @Override
  public Item item(final QueryContext qc, final InputInfo ii) {
    return Str.get(qc.user.name());
  }
}
