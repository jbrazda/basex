package org.basex.query.func.db;

import org.basex.query.*;
import org.basex.query.value.*;
import org.basex.query.value.node.*;
import org.basex.query.value.seq.*;
import org.basex.util.list.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-24, BSD License
 * @author Christian Gruen
 */
public final class DbNodePre extends DbNodeId {
  @Override
  protected void addIds(final Value nodes, final LongList ids) throws QueryException {
    if(nodes instanceof DBNodeSeq) {
      for(final int pre : ((DBNodeSeq) nodes).pres()) ids.add(pre);
    } else {
      super.addIds(nodes, ids);
    }
  }

  @Override
  protected int id(final DBNode node) {
    return node.pre();
  }
}
