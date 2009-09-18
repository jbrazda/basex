package org.basex.index;

import static org.basex.core.Text.*;
import static org.basex.data.DataText.*;
import java.io.IOException;
import org.basex.core.Prop;
import org.basex.data.Data;
import org.basex.io.DataOutput;
import org.basex.util.Num;
import org.basex.util.Token;

/**
 * This main-memory based class builds an index for attribute values and
 * text contents in a tree structure and stores the result to disk.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Christian Gruen
 */
public final class ValueBuilder extends IndexBuilder {
  /** Temporary value tree. */
  private final ValueTree index = new ValueTree();
  /** Index type (attributes/texts). */
  private final boolean text;

  /**
   * Constructor.
   * @param d data reference
   * @param txt value type (text/attribute)
   */
  public ValueBuilder(final Data d, final boolean txt) {
    super(d);
    text = txt;
  }

  @Override
  public Values build() throws IOException {
    final Prop pr = data.meta.prop;
    final String db = data.meta.name;
    final String f = text ? DATATXT : DATAATV;
    int cap = 1 << 2;
    final int max = (int) (pr.dbfile(db, f).length() >>> 7);
    while(cap < max && cap < 1 << 24) cap <<= 1;

    final int type = text ? Data.TEXT : Data.ATTR;
    for(id = 0; id < total; id++) {
      if(data.kind(id) != type) continue;
      checkStop();
      final byte[] tok = text ? data.text(id) : data.attValue(id);
      // skip too long and pure whitespace tokens
      if(tok.length <= Token.MAXLEN && !Token.ws(tok)) index.index(tok, id);
    }

    index.init();
    final int hs = index.size;
    final DataOutput outl = new DataOutput(pr.dbfile(db, f + 'l'));
    outl.writeNum(hs);
    final DataOutput outr = new DataOutput(pr.dbfile(db, f + 'r'));
    while(index.more()) {
      outr.write5(outl.size());
      final int p = index.next();
      final int ds = index.ns[p];
      outl.writeNum(ds);

      // write id lists
      final byte[] tmp = index.pre[p];
      index.pre[p] = null;
      for(int v = 0, ip = 4, o = 0; v < ds; ip += Num.len(tmp, ip), v++) {
        final int pre = Num.read(tmp, ip);
        outl.writeNum(pre - o);
        o = pre;
      }
    }
    index.pre = null;
    index.ns = null;

    outl.close();
    outr.close();

    return new Values(data, text);
  }

  @Override
  public String det() {
    return text ? INDEXTXT : INDEXATT;
  }
}
