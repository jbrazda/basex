package org.basex.io;

import static org.basex.util.Token.*;
import static org.basex.util.FTToken.*;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.xml.transform.stream.*;

import org.basex.core.*;
import org.basex.data.*;
import org.basex.io.in.*;
import org.basex.io.out.*;
import org.basex.util.*;
import org.xml.sax.*;

/**
 * Generic representation for inputs and outputs. The underlying source can
 * be a local file ({@link IOFile}), a URL ({@link IOUrl}), a byte array
 * ({@link IOContent}), or a stream ({@link IOStream}).
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public abstract class IO {
  /** Temporary file suffix. */
  public static final String TMPSUFFIX = ".tmp";
  /** Database file suffix. */
  public static final String BASEXSUFFIX = ".basex";
  /** Command script suffix. */
  public static final String BXSSUFFIX = ".bxs";
  /** XQuery file suffix. */
  public static final String XQSUFFIX = ".xq";
  /** XQuery module suffix. */
  public static final String XQMSUFFIX = ".xqm";
  /** XML file suffix. */
  public static final String XMLSUFFIX = ".xml";
  /** ZIP file suffix. */
  public static final String ZIPSUFFIX = ".zip";
  /** CSV file suffix. */
  public static final String CSVSUFFIX = ".csv";
  /** JSON file suffix. */
  public static final String JSONSUFFIX = ".json";
  /** JAR file suffix. */
  public static final String JARSUFFIX = ".jar";
  /** TGZIP file suffix. */
  public static final String TARGZSUFFIX = ".tar.gz";
  /** TGZIP file suffix. */
  public static final String TGZSUFFIX = ".tgz";
  /** GZIP file suffix. */
  public static final String GZSUFFIX = ".gz";
  /** TAR file suffix. */
  public static final String TARSUFFIX = ".tar";
  /** XAR file suffix. */
  public static final String XARSUFFIX = ".xar";
  /** XQuery log suffix. */
  public static final String LOGSUFFIX = ".log";
  /** Ignore suffix. */
  public static final String IGNORESUFFIX = ".ignore";
  /** File prefix. */
  public static final String FILEPREF = "file:/";
  /** JAR prefix. */
  public static final String JARPREF = "jar:";

  /** XQuery suffixes. */
  public static final String[] XQSUFFIXES =
    { XQSUFFIX, XQMSUFFIX, ".xqy", ".xql", ".xqu", ".xquery", ".xpath" };
  /** Archive suffixes. */
  public static final String[] ARCHIVESUFFIXES = {
    ZIPSUFFIX, GZSUFFIX, TGZSUFFIX, TARSUFFIX, XARSUFFIX,
    ".docx", ".pptx", ".xlsx", ".odt", ".odp", ".ods", ".epub", ".idml"
  };
  /** XSL suffixes. */
  public static final String[] XSLSUFFIXES = { ".xsl", ".xslt", ".fo", ".fob" };
  /** HTML suffixes. */
  public static final String[] HTMLSUFFIXES = { ".html", ".htm" };
  /** Text suffixes. */
  public static final String[] TXTSUFFIXES = { ".txt", ".text", ".ini", ".conf", ".md", ".log" };
  /** JS file suffixes. */
  public static final String[] JSSUFFIXES = { ".js", ".java", ".ts", ".vue" };

  /** Disk block/page size (12). */
  public static final int BLOCKPOWER = 12;
  /** Disk block/page size (4096). */
  public static final int BLOCKSIZE = 1 << BLOCKPOWER;
  /** Table node size power (4). */
  public static final int NODEPOWER = 4;
  /** Table node size power (16). */
  public static final int NODESIZE = 1 << NODEPOWER;
  /** Entries per block (256). */
  public static final int ENTRIES = BLOCKSIZE >>> NODEPOWER;

  /** Maximum number of attributes (see bit layout in {@link Data} class). */
  public static final int MAXATTS = 0x1F;

  /** Absolute file path. All paths have forward-slashes, no matter which OS is used. */
  protected String pth;
  /** Input size in bytes ({@code -1} if unknown). */
  protected long len = -1;
  /** Filename. */
  private String nm;

  /**
   * Protected constructor.
   * @param path path
   * @param single assign empty string as filename if path points to a directory
   */
  IO(final String path, final boolean single) {
    pth = path;
    nm = single && path.endsWith("/") ? "" : path.replaceAll("^(?:.*/)?([^/]+)/?$", "$1");
  }

  /**
   * <p>Returns a class instance for the specified location string. The type of the
   * returned instance depends on the string value:</p>
   * <ul>
   * <li>{@link IOFile}: if the string starts with {@code file:}, or if it
   *   does not contain the substring {@code ://}, it is interpreted as
   *   local file instance</li>
   * <li>{@link IOUrl}: if it starts with a valid scheme, it is handled as URL</li>
   * <li>{@link IOContent}: otherwise, it is interpreted as XML fragment and internally
   *   represented as byte array</li>
   * </ul>
   * If the content of the string value is known in advance, it is advisable
   * to call the direct constructors of the correspondent subclass.
   *
   * @param location location
   * @return IO reference
   */
  public static IO get(final String location) {
    if(location == null) return new IOContent("");
    final String s = location.trim();
    return s.indexOf('<') == 0 ? new IOContent(s) :
           IOUrl.isFileURL(s)  ? new IOFile(IOFile.toPath(s)) :
           IOUrl.isValid(s)    ? new IOUrl(s) :
           IOFile.isValid(s)   ? new IOFile(s) :
           new IOContent(s);
  }

  /**
   * Returns the binary content.
   * @return binary content
   * @throws IOException I/O exception
   */
  public abstract byte[] read() throws IOException;

  /**
   * Returns the contents as string. The input encoding will be guessed by analyzing the input.
   * @return string content
   * @throws IOException I/O exception
   */
  public final String readString() throws IOException {
    return new TextInput(this).cache().toString();
  }

  /**
   * Tests if the reference exists.
   * Returns {@code true} for IO instances other than {@link IOFile}.
   * @return result of check
   */
  public boolean exists() {
    return true;
  }

  /**
   * Tests if this is a local directory instance, or if the path ends with a slash.
   * @return result of check
   */
  public boolean isDir() {
    return Strings.endsWith(pth, '/');
  }

  /**
   * Tests if this is an absolute path.
   * @return result of check
   */
  public boolean isAbsolute() {
    return true;
  }

  /**
   * Tests if the file suffix matches the specified suffixes.
   * @param suffixes suffixes to compare with
   * @return result of check
   */
  public final boolean hasSuffix(final String... suffixes) {
    return checkSuffix(pth, suffixes);
  }

  /**
   * Returns the time stamp (modification date) of this file.
   * Returns the current time for IO instances other than {@link IOFile}.
   * @return time stamp
   */
  public long timeStamp() {
    return System.currentTimeMillis();
  }

  /**
   * Sets the number of bytes of the input.
   * @param length length
   */
  public final void length(final long length) {
    len = length;
  }

  /**
   * Returns the number of bytes of the input.
   * @return length
   */
  public long length() {
    return len;
  }

  /**
   * Returns an input source.
   * @return input source
   */
  public abstract InputSource inputSource();

  /**
   * Returns a stream source.
   * @return stream source
   */
  public abstract StreamSource streamSource();

  /**
   * Returns an input stream.
   * @return input stream
   * @throws IOException I/O exception
   */
  public abstract InputStream inputStream() throws IOException;

  /**
   * Returns the encoding.
   * @return encoding, or {@code null} if unknown
   */
  public String encoding() {
    return null;
  }

  /**
   * Merges two paths.
   * @param path path to be merged
   * @return resulting reference
   */
  public final IO merge(final String path) {
    if(path.isEmpty()) return this;
    final IO io = get(path);
    if(io.isAbsolute()) return io;
    if(IOUrl.isJarURL(pth)) {
      try {
        return get(new URL(new URL(pth), path).toString());
      } catch(final MalformedURLException ex) {
        Util.stack(ex);
      }
    }
    return get((Strings.endsWith(pth, '/') ? pth : dir()) + path);
  }

  /**
   * Checks if this file is an archive.
   * @return result of check
   */
  public final boolean isArchive() {
    return hasSuffix(ARCHIVESUFFIXES);
  }

  /**
   * Generates a database name from the name of this resource. The path will be chopped and
   * special characters from the remaining name will be removed.
   * @return database name
   */
  public final String dbName() {
    final TokenBuilder tb = new TokenBuilder();
    final byte[] token = token(name());
    int nl = lastIndexOf(token, '.');
    if(nl == -1) nl = token.length;
    for(int n = 0; n < nl; n += cl(token, n)) {
      final int ch = noDiacritics(cp(token, n));
      if(Databases.validChar(ch, n == 0 || n + 1 == nl)) tb.add(ch);
    }
    // return hash code if string is empty (mainly required for CHECK command)
    return tb.isEmpty() ? Integer.toString(hashCode()) : tb.toString();
  }

  /**
   * Returns the name of the resource.
   * @return file name
   */
  public final String name() {
    return nm;
  }

  /**
   * Sets the name of the resource.
   * @param name file name
   */
  public final void name(final String name) {
    nm = name;
    if(pth.isEmpty()) pth = name;
  }

  /**
   * Returns the full path to the resource.
   * The path uses forward-slashes, no matter which OS is used.
   * @return path
   */
  public final String path() {
    return pth;
  }

  /**
   * Returns the directory path (all characters up to the last slash).
   * No check will be performed if the directory exists.
   * @return directory path
   */
  public final String dir() {
    return pth.substring(0, pth.lastIndexOf('/') + 1);
  }

  /**
   * Creates a URL from the specified path.
   * Returns the original path for IO instances other than {@link IOFile}.
   * @return URL
   */
  public String url() {
    return pth;
  }

  /**
   * Compares the filename of the specified IO reference.
   * @param io io reference
   * @return result of check
   */
  public boolean eq(final IO io) {
    return pth.equals(io.pth);
  }

  @Override
  public String toString() {
    return pth;
  }

  /**
   * Tests if the file suffix of a path matches the specified suffixes.
   * @param path path
   * @param suffixes suffixes to compare with
   * @return result of check
   */
  public static boolean checkSuffix(final String path, final String... suffixes) {
    final int i = path.lastIndexOf('.');
    if(i == -1) return false;
    final String suf = path.substring(i).toLowerCase(Locale.ENGLISH);
    for(final String suffix : suffixes) {
      if(suf.equals(suffix)) return true;
    }
    return false;
  }

  /**
   * Writes data from an input stream to an output stream.
   * @param in input stream
   * @param out output stream
   * @throws IOException I/O exception
   */
  public static void write(final InputStream in, final OutputStream out) throws IOException {
    try(BufferInput bi = BufferInput.get(in); BufferOutput bo = BufferOutput.get(out)) {
      for(int c; (c = bi.read()) != -1;) bo.write(c);
    }
  }

  /**
   * Compares file names for equality. Ignores the case, depending on the operating system.
   * @param string1 first string
   * @param string2 strings to be compared
   * @return {@code true} if test is successful
   */
  public static boolean equals(final String string1, final String string2) {
    return Prop.CASE ? string1.equals(string2) : string1.equalsIgnoreCase(string2);
  }
}
