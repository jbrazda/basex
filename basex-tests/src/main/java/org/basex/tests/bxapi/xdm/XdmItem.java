package org.basex.tests.bxapi.xdm;

import java.util.*;

import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.util.*;

/**
 * Wrapper for representing XQuery items.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public abstract class XdmItem extends XdmValue {
  /**
   * Returns a new XQuery value.
   * @param val value
   * @return result
   */
  public static XdmItem get(final Item val) {
    return val instanceof final ANode node ? new XdmNode(node) : new XdmAtomic(val);
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public abstract Item internal();

  @Override
  public final Iterator<XdmItem> iterator() {
    return new Iterator<>() {
      private boolean more = true;

      @Override
      public boolean hasNext() {
        return more;
      }

      @Override
      public XdmItem next() {
        more = false;
        return XdmItem.this;
      }

      @Override
      public void remove() {
        throw Util.notExpected();
      }
    };
  }
}
