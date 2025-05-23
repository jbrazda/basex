package org.basex.io.serial;

import static org.basex.data.DataText.*;
import static org.basex.util.Token.*;

import java.io.*;
import java.util.*;

import org.basex.data.*;
import org.basex.io.serial.csv.*;
import org.basex.io.serial.json.*;
import org.basex.query.*;
import org.basex.query.iter.*;
import org.basex.query.util.ft.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.type.*;
import org.basex.util.*;
import org.basex.util.hash.*;
import org.basex.util.list.*;

/**
 * This is an interface for serializing XQuery values.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public abstract class Serializer implements Closeable {
  /** Stack with names of opened elements. */
  protected final Stack<QNm> opened = new Stack<>();
  /** Current level. */
  protected int level;
  /** Current element name. */
  protected QNm elem;
  /** Most recent tag, in case it was a closing tag. */
  protected QNm closed = QNm.EMPTY;
  /** Indentation flag. */
  protected boolean indent;

  /** Stack with currently available namespaces. */
  private final Atts nspaces = new Atts(2).add(XML, QueryText.XML_URI).add(EMPTY, EMPTY);
  /** Stack with namespace size pointers. */
  private final IntList nstack = new IntList();

  /** Static context. */
  protected StaticContext sc;
  /** Indicates if at least one item was already serialized. */
  protected boolean more;
  /** Flag for skipping elements. */
  protected int skip;
  /** Indicates if an element is currently being opened. */
  protected boolean opening;

  /**
   * Returns a default serializer.
   * @param os output stream reference
   * @return serializer
   * @throws IOException I/O exception
   */
  public static Serializer get(final OutputStream os) throws IOException {
    return get(os, null);
  }

  /**
   * Returns a specific serializer.
   * @param os output stream reference
   * @param sopts serialization parameters (can be {@code null})
   * @return serializer
   * @throws IOException I/O exception
   */
  public static Serializer get(final OutputStream os, final SerializerOptions sopts)
      throws IOException {

    // choose serializer
    final SerializerOptions so = sopts == null ? SerializerMode.DEFAULT.get() : sopts;
    return switch(so.get(SerializerOptions.METHOD)) {
      case XHTML    -> new XHTMLSerializer(os, so);
      case HTML     -> new HTMLSerializer(os, so);
      case TEXT     -> new TextSerializer(os, so);
      case CSV      -> CsvSerializer.get(os, so);
      case JSON     -> JsonSerializer.get(os, so);
      case XML      -> new XMLSerializer(os, so);
      case ADAPTIVE -> new AdaptiveSerializer(os, so);
      default       -> new BaseXSerializer(os, so);
    };
  }

  // PUBLIC METHODS ===============================================================================

  /**
   * Serializes the specified item, which may be a node or an atomic item.
   * @param item item to be serialized
   * @throws IOException I/O exception
   */
  public void serialize(final Item item) throws IOException {
    if(item instanceof final ANode node) {
      node(node);
    } else if(item instanceof final FItem fitem) {
      function(fitem);
    } else {
      atomic(item);
    }
    more = true;
  }

  /**
   * Closes the serializer.
   * @throws IOException I/O exception
   */
  @Override
  public void close() throws IOException { }

  /**
   * Tests if the serialization was interrupted.
   * @return result of check
   */
  public boolean finished() {
    return false;
  }

  /**
   * Resets the serializer (indentation, etc.).
   */
  public void reset() { }

  /**
   * Assigns the static context.
   * @param sctx static context
   * @return self-reference
   */
  public Serializer sc(final StaticContext sctx) {
    sc = sctx;
    return this;
  }

  // PROTECTED METHODS ============================================================================

  /**
   * Serializes the specified node.
   * @param node node to be serialized
   * @throws IOException I/O exception
   */
  protected void node(final ANode node) throws IOException {
    if(node instanceof final DBNode dbnode) {
      node(dbnode);
    } else {
      node((FNode) node);
    }
  }

  /**
   * Opens an element.
   * @param name element name
   * @throws IOException I/O exception
   */
  protected final void openElement(final QNm name) throws IOException {
    prepare();
    opening = true;
    elem = name;
    startOpen(name);
    closed = QNm.EMPTY;
    nstack.push(nspaces.size());
  }

  /**
   * Closes an element.
   * @throws IOException I/O exception
   */
  protected final void closeElement() throws IOException {
    nspaces.size(nstack.pop());
    if(opening) {
      finishEmpty();
      opening = false;
      closed = elem;
    } else {
      elem = opened.peek();
      level--;
      finishClose();
      closed = opened.pop();
    }
  }

  /**
   * Opens a document.
   * @param name name
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void openDoc(final byte[] name) throws IOException { }

  /**
   * Closes a document.
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void closeDoc() throws IOException { }

  /**
   * Serializes a namespace.
   * @param prefix prefix
   * @param uri namespace URI
   * @param standalone standalone flag
   * @throws IOException I/O exception
   */
  protected void namespace(final byte[] prefix, final byte[] uri, final boolean standalone)
      throws IOException {

    final byte[] ancUri = nsUri(prefix);
    if(ancUri == null || !eq(ancUri, uri)) {
      attribute(prefix.length == 0 ? XMLNS : concat(XMLNS_COLON, prefix), uri, standalone);
      nspaces.add(prefix, uri);
    }
  }

  /**
   * Returns the namespace URI currently bound by the given prefix.
   * @param prefix namespace prefix
   * @return URI if found, {@code null} otherwise
   */
  protected final byte[] nsUri(final byte[] prefix) {
    for(int n = nspaces.size() - 1; n >= 0; n--) {
      if(eq(nspaces.name(n), prefix)) return nspaces.value(n);
    }
    return null;
  }

  /**
   * Checks if an element should be skipped.
   * @param node node to be serialized
   * @return result of check
   */
  @SuppressWarnings("unused")
  protected boolean skipElement(final ANode node) {
    return false;
  }

  /**
   * Serializes an attribute.
   * @param name name
   * @param value value
   * @param standalone standalone flag
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void attribute(final byte[] name, final byte[] value, final boolean standalone)
      throws IOException { }

  /**
   * Starts an element.
   * @param name element name
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void startOpen(final QNm name) throws IOException { }

  /**
   * Finishes an opening element node.
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void finishOpen() throws IOException { }

  /**
   * Closes an empty element.
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void finishEmpty() throws IOException { }

  /**
   * Closes an element.
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void finishClose() throws IOException { }

  /**
   * Serializes a text.
   * @param value value
   * @param ftp full-text positions, used for visualization highlighting
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void text(final byte[] value, final FTPos ftp) throws IOException { }

  /**
   * Serializes a comment.
   * @param value value
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void comment(final byte[] value) throws IOException { }

  /**
   * Serializes a processing instruction.
   * @param name name
   * @param value value
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void pi(final byte[] name, final byte[] value) throws IOException { }

  /**
   * Serializes an atomic item.
   * @param item item
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void atomic(final Item item) throws IOException { }

  /**
   * Serializes a function item.
   * @param item item
   * @throws IOException I/O exception
   */
  @SuppressWarnings("unused")
  protected void function(final FItem item) throws IOException { }

  // PRIVATE METHODS ==============================================================================

  /**
   * Serializes a node of the specified data reference.
   * @param node database node
   * @throws IOException I/O exception
   */
  private void node(final DBNode node) throws IOException {
    final Data data = node.data();
    int pre = node.pre(), kind = data.kind(pre);

    // document node: output all children
    final int size = pre + data.size(pre, kind);
    if(kind == Data.DOC) {
      openDoc(data.text(pre++, true));
      while(pre < size && !finished()) {
        node((ANode) new DBNode(data, pre));
        pre += data.size(pre, data.kind(pre));
      }
      closeDoc();
      return;
    }

    final FTPosData ftData = node instanceof final FTPosNode ft ? ft.ftpos : null;
    final boolean nsExist = !data.nspaces.isEmpty();
    final TokenSet nsSet = nsExist ? new TokenSet() : null;
    final IntList parentStack = new IntList();
    final BoolList indentStack = new BoolList();

    // loop through all table entries
    while(pre < size && !finished()) {
      kind = data.kind(pre);
      final int par = data.parent(pre, kind);

      // close opened elements...
      while(!parentStack.isEmpty() && parentStack.peek() >= par) {
        closeElement();
        indent = indentStack.pop();
        parentStack.pop();
      }

      if(kind == Data.TEXT) {
        prepareText(data.text(pre, true), ftData != null ? ftData.get(data, pre) : null);
        pre++;
      } else if(kind == Data.COMM) {
        prepareComment(data.text(pre++, true));
      } else if(kind == Data.PI) {
        preparePi(data.name(pre, Data.PI), data.atom(pre++));
      } else if(skip > 0 && skipElement(new DBNode(data, pre, kind))) {
        // ignore specific elements
        pre += data.size(pre, kind);
      } else {
        // element node:
        final byte[] name = data.name(pre, kind);
        byte[] nsPrefix = EMPTY, nsUri = null;
        if(nsExist) {
          nsPrefix = prefix(name);
          nsUri = data.nspaces.uri(data.uriId(pre, kind));
        }
        // open element, serialize namespace declaration if it's new
        openElement(new QNm(name, nsUri));
        if(nsUri == null) nsUri = EMPTY;
        namespace(nsPrefix, nsUri, false);

        // database contains namespaces: add declarations
        if(nsExist) {
          nsSet.add(nsUri);
          int p = pre;
          do {
            final Atts ns = data.namespaces(p);
            final int nl = ns.size();
            for(int n = 0; n < nl; n++) {
              nsPrefix = ns.name(n);
              if(nsSet.add(nsPrefix)) namespace(nsPrefix, ns.value(n), false);
            }
            // check ancestors only on top level
            if(level != 0) break;

            p = data.parent(p, data.kind(p));
          } while(p >= 0 && data.kind(p) == Data.ELEM);

          // reset namespace cache
          nsSet.clear();
        }

        // serialize attributes
        indentStack.push(indent);
        final int as = pre + data.attSize(pre, kind);
        while(++pre != as) {
          final byte[] n = data.name(pre, Data.ATTR), v = data.text(pre, false);
          attribute(n, v, false);
          if(eq(n, XML_SPACE) && indent) indent = !eq(v, PRESERVE);
        }
        parentStack.push(par);
      }
    }

    // process remaining elements...
    while(!parentStack.isEmpty()) {
      closeElement();
      indent = indentStack.pop();
      parentStack.pop();
    }
  }

  /**
   * Serializes a node fragment.
   * @param node database node
   * @throws IOException I/O exception
   */
  private void node(final FNode node) throws IOException {
    final Type type = node.type;
    if(type == NodeType.COMMENT) {
      prepareComment(node.string());
    } else if(type == NodeType.TEXT) {
      prepareText(node.string(), null);
    } else if(type == NodeType.PROCESSING_INSTRUCTION) {
      preparePi(node.name(), node.string());
    } else if(type == NodeType.ATTRIBUTE) {
      attribute(node.name(), node.string(), true);
    } else if(type == NodeType.NAMESPACE_NODE) {
      namespace(node.name(), node.string(), true);
    } else if(type == NodeType.DOCUMENT_NODE) {
      openDoc(node.baseURI());
      for(final ANode nd : node.childIter()) node(nd);
      closeDoc();
    } else if(skip == 0 || !skipElement(node)) {
      // serialize elements (code will never be called for attributes)
      final QNm name = node.qname();
      openElement(name);

      // serialize declared namespaces
      final Atts nsp = node.namespaces();
      final int ps = nsp.size();
      for(int p = 0; p < ps; p++) namespace(nsp.name(p), nsp.value(p), false);
      // add new or updated namespace
      namespace(name.prefix(), name.uri(), false);

      // serialize attributes
      final boolean i = indent;
      BasicNodeIter iter = node.attributeIter();
      for(ANode nd; (nd = iter.next()) != null;) {
        final byte[] n = nd.name(), v = nd.string();
        attribute(n, v, false);
        if(eq(n, XML_SPACE) && indent) indent = !eq(v, PRESERVE);
      }

      // serialize children
      iter = node.childIter();
      for(ANode n; (n = iter.next()) != null;) {
        node(n);
      }
      closeElement();
      indent = i;
    }
  }

  /**
   * Serializes a comment.
   * @param value value
   * @throws IOException I/O exception
   */
  private void prepareComment(final byte[] value) throws IOException {
    prepare();
    comment(value);
  }

  /**
   * Serializes a text.
   * @param value text bytes
   * @param ftp full-text positions, used for visualization highlighting
   * @throws IOException I/O exception
   */
  private void prepareText(final byte[] value, final FTPos ftp) throws IOException {
    prepare();
    text(value, ftp);
  }

  /**
   * Serializes a processing instruction.
   * @param name name
   * @param value value
   * @throws IOException I/O exception
   */
  private void preparePi(final byte[] name, final byte[] value) throws IOException {
    prepare();
    pi(name, value);
  }

  /**
   * Finishes an opening element node if necessary.
   * @throws IOException I/O exception
   */
  private void prepare() throws IOException {
    if(!opening) return;
    opening = false;
    finishOpen();
    opened.push(elem);
    level++;
  }

  // STATIC METHODS ===============================================================================

  /**
   * Serializes the specified value.
   * @param value value
   * @param chop chop large tokens
   * @param quote character for quoting the value; ignored if {@code 0}
   * @return value
   */
  public static byte[] value(final byte[] value, final char quote, final boolean chop) {
    final boolean quoting = quote != 0;
    final TokenBuilder tb = new TokenBuilder();
    if(quoting) tb.add(quote);

    int c = 0;
    for(final TokenParser tp = new TokenParser(value); tp.more(); c++) {
      if(chop && c == 200) {
        tb.add(QueryText.DOTS);
        break;
      }
      final int cp = tp.next();
      if(cp == '&') tb.add(E_AMP);
      else if(cp == '\r') tb.add(E_CR);
      else if(cp == '\n') tb.add(E_NL);
      else if(cp == quote) tb.add(quote).add(quote);
      else tb.add(cp);
    }

    if(quoting) tb.add(quote);
    return tb.finish();
  }
}
