package org.basex.util;

import static org.basex.util.Token.*;

import java.util.*;

/**
 * This class serves as an efficient constructor for {@link Token Tokens}.
 * It bears some resemblance to Java's {@link StringBuilder}.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class TokenBuilder {
  /** New line. */
  public static final byte NLINE = 0x0A;

  /** Unicode, private area (start). */
  public static final int PRIVATE_START = 0xE000;
  /** Unicode, private area (end). */
  public static final int PRIVATE_END = 0xF8FF;

  /** Half new line. */
  public static final int HLINE = PRIVATE_END;
  /** Bold flag. */
  public static final int BOLD = PRIVATE_END - 1;
  /** Standard flag. */
  public static final int NORM = PRIVATE_END - 2;
  /** Mark flag. */
  public static final int MARK = PRIVATE_END - 3;
  /** Underline flag. */
  public static final int ULINE = PRIVATE_END - 4;

  /** Limit for info strings. */
  private static final int LIMIT = 128;
  /** Byte array, storing all characters as UTF8. */
  private byte[] chars;
  /** Current token size. */
  private int size;

  /**
   * Empty constructor.
   */
  public TokenBuilder() {
    this(-1);
  }

  /**
   * Constructor with initial array capacity.
   * @param capacity array capacity
   */
  public TokenBuilder(final long capacity) {
    chars = new byte[Array.initialCapacity(capacity)];
  }

  /**
   * Constructor with initial token.
   * @param token initial token
   */
  public TokenBuilder(final byte[] token) {
    final int sz = token.length;
    chars = Arrays.copyOf(token, sz);
    size = sz;
  }

  /**
   * Returns the number of bytes.
   * @return number of bytes
   */
  public int size() {
    return size;
  }

  /**
   * Sets the number of bytes. Note that no bound check is performed by this method.
   * @param sz number of bytes
   */
  public void size(final int sz) {
    size = sz;
  }

  /**
   * Tests if the token is empty.
   * @return result of check
   */
  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * Resets the token buffer.
   * @return self reference
   */
  public TokenBuilder reset() {
    size = 0;
    return this;
  }

  /**
   * Adds a bold flag. This method should only be called to control text
   * rendering in the visual front end.
   * @return self reference
   */
  public TokenBuilder bold() {
    return add(BOLD);
  }

  /**
   * Adds an underline toggle flag. This method should only be called to control text
   * rendering in the visual front end.
   * @return self reference
   */
  public TokenBuilder uline() {
    return add(ULINE);
  }

  /**
   * Adds a norm flag. This method should only be called to control text
   * rendering in the visual front end.
   * @return self reference
   */
  public TokenBuilder norm() {
    return add(NORM);
  }

  /**
   * Adds a half new line. This method should only be called to control text
   * rendering in the visual front end.
   * @return self reference
   */
  public TokenBuilder hline() {
    return add(HLINE);
  }

  /**
   * Adds a new line. This method should only be called to control text
   * rendering in the visual front end.
   * @return self reference
   */
  public TokenBuilder nline() {
    return add(NLINE);
  }

  /**
   * Adds the specified character.
   * Call {@link #addInt(int)} to add the string value of an integer.
   * @param cp codepoint of the character
   * @return self reference
   */
  public TokenBuilder add(final int cp) {
    if(cp <= 0x7F) {
      addByte((byte) cp);
    } else {
      if(cp <= 0x7FF) {
        addByte((byte) (cp >>  6 & 0x1F | 0xC0));
      } else {
        if(cp <= 0xFFFF) {
          addByte((byte) (cp >> 12 & 0x0F | 0xE0));
        } else {
          addByte((byte) (cp >> 18 & 0x07 | 0xF0));
          addByte((byte) (cp >> 12 & 0x3F | 0x80));
        }
        addByte((byte) (cp >>  6 & 0x3F | 0x80));
      }
      addByte((byte) (cp & 0x3F | 0x80));
    }
    return this;
  }

  /**
   * Removes the last character.
   * @return self reference
   */
  public TokenBuilder removeLast() {
    while(--size > 0 && (chars[size] & 0xC0) == 0x80);
    return this;
  }

  /**
   * Returns the codepoint stored at the specified position.
   * @param pos position
   * @return character
   */
  public int cp(final int pos) {
    return Token.cp(chars, pos);
  }

  /**
   * Returns the length of the codepoints stored at the specified position.
   * @param pos position
   * @return character
   */
  public int cl(final int pos) {
    return Token.cl(chars, pos);
  }

  /**
   * Returns the byte stored at the specified position.
   * @param pos position
   * @return byte
   */
  public byte get(final int pos) {
    return chars[pos];
  }

  /**
   * Sets a byte at the specified position.
   * @param value byte to be set
   * @param pos position
   */
  public void set(final int pos, final byte value) {
    chars[pos] = value;
  }

  /**
   * Deletes bytes from the token.
   * @param start start position
   * @param end end position
   * @return self reference
   */
  public TokenBuilder delete(final int start, final int end) {
    final int length = end - start;
    Array.remove(chars, start, length, size);
    size -= length;
    return this;
  }

  /**
   * Adds a single byte.
   * @param value the byte to be added
   * @return self reference
   */
  public TokenBuilder addByte(final byte value) {
    byte[] chrs = chars;
    final int s = size;
    if(s == chrs.length) chrs = Arrays.copyOf(chrs, Array.newCapacity(s));
    chrs[s] = value;
    chars = chrs;
    size = s + 1;
    return this;
  }

  /**
   * Adds an integer value.
   * @param value value to be added
   * @return self reference
   */
  public TokenBuilder addInt(final int value) {
    return add(token(value));
  }

  /**
   * Adds a number.
   * @param value value to be added
   * @return self reference
   */
  public TokenBuilder addLong(final long value) {
    return add(token(value));
  }

  /**
   * Adds a token.
   * @param token the token to be added
   * @return self reference
   */
  public TokenBuilder add(final byte[] token) {
    return add(token, 0, token.length);
  }

  /**
   * Adds a subtoken.
   * @param token the token
   * @param start start position
   * @param end end position
   * @return self reference
   */
  public TokenBuilder add(final byte[] token, final int start, final int end) {
    final int l = end - start;
    if(l > 0) {
      byte[] chrs = chars;
      final int cl = chrs.length, s = size, ns = s + l;
      if(ns > cl) chrs = Arrays.copyOf(chrs, Array.newCapacity(ns));
      Array.copy(token, start, l, chrs, s);
      chars = chrs;
      size = ns;
    }
    return this;
  }

  /**
   * Adds a string.
   * @param string the string to be added
   * @return self reference
   */
  public TokenBuilder add(final String string) {
    return add(token(string));
  }

  /**
   * Adds multiple strings, separated by the specified string.
   * @param objects the object to be added
   * @param separator separator string
   * @return self reference
   */
  public TokenBuilder addAll(final Object[] objects, final String separator) {
    final int ol = objects.length;
    for(int o = 0; o < ol; o++) {
      if(o > 0) add(separator);
      add(objects[o]);
    }
    return this;
  }

  /**
   * Adds an object.
   * @param object the object to be added
   * @return self reference
   */
  public TokenBuilder add(final Object object) {
    return add(token(object));
  }

  /**
   * Adds the string representation of an object.
   * The specified string may contain {@code %} characters as placeholders.
   * All placeholders will be replaced by the specified extensions. If a digit is specified
   * after the placeholder character, it will be interpreted as insertion position.
   *
   * @param object object to be extended
   * @param extensions optional extension strings
   * @return self reference
   */
  public TokenBuilder addExt(final Object object, final Object... extensions) {
    final byte[] t = token(object);
    final int tl = t.length, el = extensions.length;
    for(int i = 0, e = 0; i < tl; ++i) {
      if(t[i] != '%' || e == el) {
        addByte(t[i]);
      } else {
        final byte c = i + 1 < tl ? t[i + 1] : 0;
        final boolean d = c >= '1' && c <= '9';
        if(d) ++i;
        final int n = d ? c - '1' : e++;
        add(n < el ? extensions[n] : null);
      }
    }
    return this;
  }

  /**
   * Trims leading and trailing whitespace.
   * @return self reference
   */
  public TokenBuilder trim() {
    final byte[] chrs = chars;
    int s = size;
    while(s > 0 && ws(chrs[s - 1])) --s;
    int c = -1;
    while(++c < s && ws(chrs[c]));
    if(c != 0 && c != s) Array.remove(chrs, 0, c, s);
    size = s - c;
    return this;
  }

  /**
   * Normalizes newlines.
   * @return self reference
   */
  public TokenBuilder normalize() {
    final byte[] chrs = chars;
    final int s = size;
    int n = 0;
    for(int c = 0; c < s; c++) {
      byte ch = chrs[c];
      if(ch == '\r') {
        ch = '\n';
        if(c + 1 < s && chrs[c + 1] == '\n') c++;
      }
      chrs[n++] = ch;
    }
    size = n;
    return this;
  }

  /**
   * Returns the token as byte array.
   * @return token
   */
  public byte[] toArray() {
    return finish(chars, size, true);
  }

  /**
   * Returns the token as byte array and resets the token buffer.
   * The call of this function is identical to calling {@link #toArray} and {@link #reset}.
   * @return token
   */
  public byte[] next() {
    final int s = size;
    size = 0;
    return finish(chars, s, true);
  }

  /**
   * Returns the token as byte array, and invalidates the internal array.
   * Warning: the function must only be called if the builder is discarded afterward.
   * @return token
   */
  public byte[] finish() {
    final byte[] chrs = chars;
    chars = null;
    return finish(chrs, size, false);
  }

  /**
   * Returns the token as byte array.
   * @param chars characters
   * @param size token size
   * @param copy copy token
   * @return token
   */
  private static byte[] finish(final byte[] chars, final int size, final boolean copy) {
    return size == 0 ? EMPTY :
           size == 1 && chars[0] >= 0 ? cpToken(chars[0]) :
           size != chars.length || copy ? Arrays.copyOf(chars, size) :
           chars;
  }

  /**
   * Checks if the building of an info string should be continued.
   * @return result of check
   */
  public boolean moreInfo() {
    if(size <= LIMIT) return true;
    if(chars[size - 1] != '.' || chars[size - 2] != '.' || chars[size - 3] != '.') add("...");
    return false;
  }

  @Override
  public String toString() {
    return chars == null ? "" : string(chars, 0, size);
  }
}
