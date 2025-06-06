package org.basex.query.up.primitives.node;

import org.basex.data.*;
import org.basex.query.up.*;
import org.basex.query.up.atomic.*;
import org.basex.query.up.primitives.*;
import org.basex.query.util.list.*;
import org.basex.query.value.node.*;
import org.basex.util.*;

/**
 * Insert into as last primitive.
 *
 * @author BaseX Team, BSD License
 * @author Lukas Kircher
 */
public final class InsertIntoAsLast extends NodeCopy {
  /**
   * Constructor for an insertInto which is part of a replaceElementContent substitution.
   * @param pre target PRE value
   * @param data target data instance
   * @param info input info (can be {@code null})
   * @param nodes node copy insertion sequence
   */
  public InsertIntoAsLast(final int pre, final Data data, final InputInfo info,
      final ANodeList nodes) {
    super(UpdateType.INSERTINTOLAST, pre, data, info, nodes);
  }

  @Override
  public void merge(final Update update) {
    final ANodeList newInsert = ((NodeCopy) update).nodes;
    for(final ANode node : newInsert) nodes.add(node);
  }

  @Override
  public void addAtomics(final AtomicUpdateCache auc) {
    final int s = data.size(pre, data.kind(pre));
    auc.addInsert(pre + s, pre, insseq);
  }

  @Override
  public void update(final NamePool pool) {
  }
}
