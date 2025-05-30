package org.basex.util;

import static org.basex.query.util.DeepEqualOptions.*;

import java.math.*;
import java.nio.charset.*;
import java.text.*;
import java.util.*;
import java.util.function.*;

import org.basex.io.out.*;
import org.basex.query.util.*;
import org.basex.query.util.collation.*;
import org.basex.util.list.*;

/**
 * <p>This class provides convenience operations for handling 'Tokens'.
 * A token is a UTF-8 encoded string. It is represented as a byte array.</p>
 *
 * <p>In order to ensure a consistent representation of tokens in the project, all string
 * conversions should be done via the methods of this class.</p>
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class Token {
  /** Empty token. */
  public static final byte[] EMPTY = {};
  /** XML token. */
  public static final byte[] XML = token("xml");
  /** XML token with colon. */
  public static final byte[] XML_COLON = token("xml:");
  /** XMLNS token. */
  public static final byte[] XMLNS = token("xmlns");
  /** XMLNS token with colon. */
  public static final byte[] XMLNS_COLON = token("xmlns:");
  /** ID token. */
  public static final byte[] ID = token("id");
  /** IDRef token. */
  public static final byte[] REF = token("ref");
  /** Token 'true'. */
  public static final byte[] TRUE = token("true");
  /** Token 'false'. */
  public static final byte[] FALSE = token("false");
  /** Token 'NaN'. */
  public static final byte[] NAN = token("NaN");
  /** Token 'INF'. */
  public static final byte[] POSITIVE_INF = token("INF");
  /** Token '-INF'. */
  public static final byte[] NEGATIVE_INF = token("-INF");
  /** Token 'Infinity'. */
  public static final byte[] POSITIVE_INFINITY = token("Infinity");
  /** Token '-Infinity'. */
  public static final byte[] NEGATIVE_INFINITY = token("-Infinity");
  /** Minimum long value. */
  public static final byte[] MIN_LONG = token("-9223372036854775808");
  /** Minimum integer. */
  public static final byte[] MIN_INT = token("-2147483648");
  /** Number '-0'. */
  public static final byte[] NEGATIVE_ZERO = { '-', '0' };

  /** Comparator for byte arrays. */
  public static final Comparator<byte[]> COMPARATOR = Token::compare;
  /** Case-insensitive comparator for byte arrays. */
  public static final Comparator<byte[]> LC_COMPARATOR = (o1, o2) -> compare(lc(o1), lc(o2));
  /** Unicode replacement codepoint (\\uFFFD). */
  public static final char REPLACEMENT = '\uFFFD';

  /** US charset. */
  public static final DecimalFormatSymbols LOC = new DecimalFormatSymbols(Locale.US);
  /** Scientific double output. */
  public static final DecimalFormat SD = new DecimalFormat("0.0##################E0", LOC);
  /** Decimal double output. */
  public static final DecimalFormat DD = new DecimalFormat("#####0.0################", LOC);
  /** Scientific float output. */
  public static final DecimalFormat SF = new DecimalFormat("0.0######E0", LOC);
  /** Decimal float output. */
  public static final DecimalFormat DF = new DecimalFormat("#####0.0######", LOC);
  /** Adaptive output. */
  public static final DecimalFormat AD = new DecimalFormat("0.0##########################E0", LOC);

  /** BigInteger representing 2 * ({@link Long#MAX_VALUE} + 1). */
  public static final BigInteger MAX_ULONG = BigInteger.ONE.shiftLeft(64);
  /** Digits used in base conversion. */
  public static final byte[] DIGITS = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b',
    'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
    'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
  };

  /** Hex codes. */
  public static final byte[] HEX_TABLE = token("0123456789ABCDEF");

  /** Maximum number of hash operations. */
  private static final byte MAX_HASH_OPS = 127;
  /** Maximum values for converting tokens to integer values. */
  private static final int MAX_INT = Integer.MAX_VALUE / 10;
  /** Maximum values for converting tokens to long values. */
  private static final long MAX_LONG = Long.MAX_VALUE / 10;

  /** Table with integer sizes. */
  private static final int[] INTSIZE = {
    9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999, Integer.MAX_VALUE
  };
  /** Cache for single ASCII-character tokens. */
  private static final byte[][] CHAR;

  static {
    final int nl = 0x80;
    CHAR = new byte[nl][];
    for(int n = 0; n < nl; n++) CHAR[n] = new byte[] { (byte) n };
  }

  /** Hidden constructor. */
  private Token() { }

  /**
   * Returns the specified token as string.
   * @param token token
   * @return string
   */
  public static String string(final byte[] token) {
    return string(token, 0, token.length);
  }

  /**
   * Converts the specified token to a string.
   * @param token token
   * @param start start position
   * @param length length
   * @return string
   */
  public static String string(final byte[] token, final int start, final int length) {
    return length == 0 ? "" : new String(token, start, length, StandardCharsets.UTF_8);
  }

  /**
   * Checks if the specified token only consists of ASCII characters.
   * @param token token
   * @return result of check
   */
  public static boolean ascii(final byte[] token) {
    for(final byte b : token) {
      if(b < 0) return false;
    }
    return true;
  }

  /**
   * Converts a string to a token.
   * All strings should be converted by this function to guarantee
   * a consistent character conversion.
   * @param string string to be converted
   * @return byte array
   */
  public static byte[] token(final String string) {
    final int sl = string.length();
    return sl == 0 ? EMPTY :
      sl == 1 || sl == 2 && Character.isHighSurrogate(string.charAt(0)) ?
        cpToken(string.codePointAt(0)) :
      string.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Converts the specified strings to tokens.
   * @param strings strings
   * @return tokens
   */
  public static byte[][] tokens(final String... strings) {
    final int sl = strings.length;
    final byte[][] tokens = new byte[sl][];
    for(int s = 0; s < sl; ++s) tokens[s] = token(strings[s]);
    return tokens;
  }

  /**
   * Returns a token representation of the specified object.
   * <ul>
   *   <li> byte arrays are returned as-is.</li>
   *   <li> {@code null} references are replaced by the string "{@code null}".</li>
   *   <li> objects of type {@link Throwable} are converted to a string representation via
   *        {@link Util#message(Throwable)}.</li>
   *   <li> objects of type {@link Class} are converted via {@link Util#className(Class)}.</li>
   *   <li> for all other typer, {@link Object#toString} is called.</li>
   * </ul>
   * @param object object
   * @return token
   */
  public static byte[] token(final Object object) {
    if(object instanceof final byte[] bytes) return bytes;
    if(object instanceof final ArrayOutput ao) return ao.toArray();
    if(object instanceof final TokenBuilder tb) return tb.toArray();
    if(object instanceof final Supplier<?> supplier) return token(supplier.get());

    final String s;
    if(object == null) {
      s = "null";
    } else if(object instanceof final Throwable th) {
      s = Util.message(th);
    } else if(object instanceof final Class<?> clzz) {
      s = Util.className(clzz);
    } else {
      s = object.toString();
    }
    return token(s);
  }

  /**
   * Returns the codepoint of a character at the specified position.
   * Returns a replacement character for invalid values.
   * @param token token
   * @param pos character position
   * @return current character
   */
  public static int cp(final byte[] token, final int pos) {
    // 0xxxxxxx
    final byte b = token[pos];
    if(b >= 0) return b;
    // number of bytes to be read
    final int cl = cl(b);
    if(b < -64 || pos + cl > token.length) return REPLACEMENT;
    // 110xxxxx 10xxxxxx
    if(cl == 2) return (b & 0x1F) << 6 | token[pos + 1] & 0x3F;
    // 1110xxxx 10xxxxxx 10xxxxxx
    if(cl == 3) return (b & 0x0F) << 12 | (token[pos + 1] & 0x3F) << 6 |
      token[pos + 2] & 0x3F;
    // 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
    return (b & 0x07) << 18 | (token[pos + 1] & 0x3F) << 12 |
      (token[pos + 2] & 0x3F) << 6 | token[pos + 3] & 0x3F;
  }

  /**
   * Returns the byte length of a UTF-8 character.
   * @param b first byte of a token
   * @return character length
   */
  public static int cl(final byte b) {
    return b >= 0 || b < -64 ? 1 : b < -32 ? 2 : b < -16 ? 3 : 4;
  }

  /**
   * Returns the byte length of a UTF-8 character at the specified position.
   * @param token token
   * @param pos position
   * @return character length
   */
  public static int cl(final byte[] token, final int pos) {
    return cl(token[pos]);
  }

  /**
   * Returns the codepoints of a token.
   * @param token token
   * @return codepoints
   */
  public static int[] cps(final byte[] token) {
    final int tl = token.length;
    if(ascii(token)) {
      final int[] cps = new int[tl];
      for(int t = 0; t < tl; t++) cps[t] = token[t];
      return cps;
    }
    final IntList list = new IntList(tl);
    forEachCp(token, list::add);
    return list.finish();
  }

  /**
   * Returns the codepoint offsets for Unicode tokens.
   * @param token token
   * @return codepoint offsets, or {@code null} if the token is ASCII only
   */
  public static int[] cpOffsets(final byte[] token) {
    if(ascii(token)) return null;

    final int tl = token.length;
    final IntList list = new IntList(tl);
    for(int t = 0; t < tl; t += cl(token, t)) list.add(t);
    return list.finish();
  }

  /**
   * Converts a character to a token.
   * @param ch codepoint of the character
   * @return token
   */
  public static byte[] cpToken(final int ch) {
    if(ch <= 0x7F) return CHAR[ch];
    if(ch <= 0x7FF) return new byte[] {
      (byte) (ch >>  6 & 0x1F | 0xC0), (byte) (ch & 0x3F | 0x80)
    };
    if(ch <= 0xFFFF) return new byte[] {
      (byte) (ch >> 12 & 0x0F | 0xE0), (byte) (ch >>  6 & 0x3F | 0x80), (byte) (ch & 0x3F | 0x80)
    };
    return new byte[] {
      (byte) (ch >> 18 & 0x07 | 0xF0), (byte) (ch >> 12 & 0x3F | 0x80),
      (byte) (ch >>  6 & 0x3F | 0x80), (byte) (ch & 0x3F | 0x80)
    };
  }

  /**
   * Returns the UTF-8 byte length of a character.
   * @param ch codepoint of the character
   * @return length
   */
  public static int cpLength(final int ch) {
    return ch <= 0x7F ? 1 : ch <= 0x7FF ? 2 : ch <= 0xFFFF ? 3 : 4;
  }

  /**
   * Applies an operation with the codepoints of a token.
   * @param token token
   * @param op operation
   */
  public static void forEachCp(final byte[] token, final IntConsumer op) {
    final int tl = token.length;
    for(int t = 0; t < tl; t += cl(token, t)) op.accept(cp(token, t));
  }

  /**
   * Returns the number of codepoints in the token.
   * @param token token
   * @return number of codepoints
   */
  public static int length(final byte[] token) {
    return length(token, ascii(token));
  }

  /**
   * Returns the number of codepoints in the token.
   * @param token token
   * @param ascii ASCII flag
   * @return number of codepoints
   */
  public static int length(final byte[] token, final boolean ascii) {
    final int tl = token.length;
    if(ascii) return tl;
    int l = 0;
    for(int t = 0; t < tl; t += cl(token, t)) ++l;
    return l;
  }

  /**
   * Creates a byte array representation of the specified boolean value.
   * @param bool boolean value to be converted
   * @return boolean value in byte array
   */
  public static byte[] token(final boolean bool) {
    return bool ? TRUE : FALSE;
  }

  /**
   * Creates a byte array representation of the specified integer value.
   * @param integer int value to be converted
   * @return integer value in byte array
   */
  public static byte[] token(final int integer) {
    if(integer >= 0 && integer <= 9) return cpToken('0' + integer);
    if(integer == Integer.MIN_VALUE) return MIN_INT;

    int n = integer;
    final boolean m = n < 0;
    if(m) n = -n;
    int nl = numDigits(n);
    if(m) ++nl;
    final byte[] num = new byte[nl];

    // faster division by 10 for values < 81920 (see Integer.getChars)
    while(n > 81919) {
      final int q = n / 10;
      num[--nl] = (byte) (n - (q << 3) - (q << 1) + '0');
      n = q;
    }
    while(n != 0) {
      final int q = n * 52429 >>> 19;
      num[--nl] = (byte) (n - (q << 3) - (q << 1) + '0');
      n = q;
    }
    if(m) num[--nl] = '-';
    return num;
  }

  /**
   * Creates a byte array representation of the specified integer value.
   * @param value value to be converted
   * @param radix radix
   * @return integer value in byte array
   */
  public static byte[] token(final long value, final int radix) {
    // use fast variant for powers of two
    for(int shift = 1, p = 2; shift < 6; shift++, p <<= 1) {
      if(radix == p) {
        final byte[] bytes = new byte[(64 + shift - 1) / shift];
        final int mask = (1 << shift) - 1;
        long n = value;
        int pos = bytes.length;
        do {
          bytes[--pos] = DIGITS[(int) (n & mask)];
          n >>>= shift;
        } while(n != 0);
        return substring(bytes, pos);
      }
    }

    final ByteList bl = new ByteList();
    long n = value;
    if(n < 0) {
      // unsigned value doesn't fit in any native type...
      final BigInteger[] dr = BigInteger.valueOf(n).add(MAX_ULONG).divideAndRemainder(
          BigInteger.valueOf(radix));
      n = dr[0].longValue();
      bl.add(DIGITS[dr[1].intValue()]);
    } else {
      bl.add(DIGITS[(int) (n % radix)]);
      n /= radix;
    }
    while(n != 0) {
      bl.add(DIGITS[(int) (n % radix)]);
      n /= radix;
    }
    return bl.reverse().finish();
  }

  /**
   * Checks number of digits of the specified integer.
   * @param integer number to be checked
   * @return number of digits
   */
  public static int numDigits(final int integer) {
    for(int i = 0;; ++i) {
      if(integer <= INTSIZE[i]) return i + 1;
    }
  }

  /**
   * Creates a byte array representation from the specified long value,
   * using Java's standard method.
   * @param integer value to be converted
   * @return byte array
   */
  public static byte[] token(final long integer) {
    return integer >= 0 && integer <= 9 ? cpToken('0' + (int) integer) :
           integer >= Integer.MIN_VALUE && integer <= Integer.MAX_VALUE ?
        token((int) integer) : token(Long.toString(integer));
  }

  /**
   * Creates a byte array representation from the specified double value.
   * @param dbl double value to be converted
   * @return byte array
   */
  public static byte[] token(final double dbl) {
    final byte[] b = fastToken(dbl);
    if(b != null) return b;

    final double a = Math.abs(dbl);
    final String s;
    if(a >= 1.0e-6 && a < 1.0e6) {
      synchronized(DD) { s = DD.format(dbl); }
    } else {
      synchronized(SD) { s = SD.format(dbl); }
    }
    return chopNumber(token(s));
  }

  /**
   * Creates a byte array representation from the specified float value.
   * @param flt float value to be converted
   * @return byte array
   */
  public static byte[] token(final float flt) {
    final byte[] b = fastToken(flt);
    if(b != null) return b;

    final int fl = FLT.length;
    for(int i = 0; i < fl; ++i) {
      if(flt == FLT[i]) return FLTSTR[i];
    }
    final float a = Math.abs(flt);
    final boolean small = a >= 1.0e-6f && a < 1.0e6f;
    String s1;
    if(small) {
      synchronized(DF) { s1 = DF.format(flt); }
    } else {
      synchronized(SF) { s1 = SF.format(flt); }
    }
    final String s2 = Float.toString(flt);
    if(s2.length() < s1.length() && (!s2.contains("E") || !small)) s1 = s2;
    return chopNumber(token(s1));
  }

  /**
   * Tries to create a byte array representation from a double value.
   * @param value value to be converted
   * @return byte array or {@code null}
   */
  public static byte[] fastToken(final double value) {
    if(value == Double.POSITIVE_INFINITY) return POSITIVE_INF;
    if(value == Double.NEGATIVE_INFINITY) return NEGATIVE_INF;
    if(value == 0) return 1 / value > 0 ? cpToken('0') : NEGATIVE_ZERO;
    if(Double.isNaN(value)) return NAN;
    final double a = Math.abs(value);
    if(a < 1.0e6) {
      final int i = (int) value;
      if(i == value) return token(i);
    }
    return null;
  }

  /**
   * Finishes the numeric token, removing trailing zeroes.
   * @param token token to be modified
   * @return token
   */
  public static byte[] chopNumber(final byte[] token) {
    if(!contains(token, '.') || contains(token, 'e') || contains(token, 'E')) return token;
    // remove trailing zeroes
    int tl = token.length;
    while(--tl > 0 && token[tl] == '0');
    return substring(token, 0, token[tl] == '.' ? tl : tl + 1);
  }

  /** Constant float values. */
  private static final float[] FLT = { 1.0E17f, 1.0E15f, 1.0E13f, 1.0E11f,
    -1.0E17f, -1.0E15f, -1.0E13f, -1.0E11f };
  /** Token representations of float values. */
  private static final byte[][] FLTSTR = tokens("1.0E17", "1.0E15",
    "1.0E13", "1.0E11", "-1.0E17", "-1.0E15", "-1.0E13", "-1.0E11");

  /**
   * Converts the specified token into a double value.
   * @param token token to be converted
   * @return resulting double value, or {@link Double#NaN} is returned if the input is invalid
   */
  public static double toDouble(final byte[] token) {
    final int tl = token.length;
    int s = -1;
    while(++s < tl && ws(token[s]));
    if(s == tl) return Double.NaN;

    // check for integer value
    int e = s;
    boolean f = false;
    for(int p = s; p < tl; ++p) {
      final byte b = token[p];
      if(e == s) {
        if(digit(b) || b == '+') continue;
        if(ws(b)) {
          e = p + 1;
        } else {
          f = b == 'e' || b == 'E' || b == '.' || b == '-';
          if(!f) return Double.NaN;
        }
      } else if(!ws(b)) {
        return Double.NaN;
      }
    }
    if(e == s) e = tl;
    if(!f && e - s <= 9) {
      final int d = toInt(token, s, e);
      return d == Integer.MIN_VALUE ? Double.NaN : d;
    }

    // check if the value is a double
    final int l = e - s;
    if(l == 1) return Double.NaN;
    final char[] str = new char[l];
    int sd = 0, se = 0;
    for(int p = 0; p < l; ++p) {
      char b = (char) token[s + p];
      if(b == 'e' || b == 'E') {
        // 'e1', '1e', '1e1e1'
        b = p == 0 || p + 1 == l || ++se > 1 ? 0 : 'e';
      } else if(b == '-') {
        // '1-', '1.-1', '1-1'
        if(p > 0 && (p + 1 == l || str[p - 1] != 'e')) b = 0;
      } else if(b == '.') {
        // '1e.', '1.1.1',
        if(p > 0 && str[p - 1] == 'e' || ++sd > 1) b = 0;
      }
      if(b == 0) return Double.NaN;
      str[p] = b;
    }

    // parse as double value
    try {
      return Double.parseDouble(new String(str));
    } catch(final NumberFormatException ex) {
      Util.debug(ex);
      return Double.NaN;
    }
  }

  /**
   * Converts the specified token into a long value.
   * {@link Long#MIN_VALUE} is returned if the input is invalid.
   * Note that this may also be the actual value ({@link #MIN_LONG}).
   * @param token token to be converted
   * @return resulting long value
   */
  public static long toLong(final byte[] token) {
    return toLong(token, 0, token.length);
  }

  /**
   * Converts the specified token into a long value.
   * {@link Long#MIN_VALUE} is returned if the input is invalid.
   * Note that this may also be the actual value ({@link #MIN_LONG}).
   * @param token token to be converted
   * @param start first byte to be parsed
   * @param end last byte to be parsed - exclusive
   * @return resulting long value
   */
  public static long toLong(final byte[] token, final int start, final int end) {
    int p = start;
    while(p < end && ws(token[p])) ++p;
    if(p == end) return Long.MIN_VALUE;
    boolean m = false;
    if(token[p] == '-' || token[p] == '+') m = token[p++] == '-';
    if(p == end) return Long.MIN_VALUE;
    long v = 0;
    for(; p < end; ++p) {
      final byte b = token[p];
      if(b < '0' || b > '9') break;
      if(v >= MAX_LONG && (b > '7' || v > MAX_LONG)) return Long.MIN_VALUE;
      v = (v << 3) + (v << 1) + b - '0';
    }
    while(p < end && ws(token[p])) ++p;
    return p < end ? Long.MIN_VALUE : m ? -v : v;
  }

  /**
   * Converts the specified token into an integer value.
   * {@link Integer#MIN_VALUE} is returned if the input is invalid.
   * @param token token to be converted
   * @return resulting integer value
   */
  public static int toInt(final byte[] token) {
    return toInt(token, 0, token.length);
  }

  /**
   * Converts the specified token into an integer value.
   * {@link Integer#MIN_VALUE} is returned if the input is invalid.
   * @param token token to be converted
   * @param start first byte to be parsed
   * @param end last byte to be parsed (exclusive)
   * @return resulting integer value
   */
  private static int toInt(final byte[] token, final int start, final int end) {
    int p = start;
    while(p < end && ws(token[p])) ++p;
    if(p == end) return Integer.MIN_VALUE;
    boolean m = false;
    if(token[p] == '-' || token[p] == '+') m = token[p++] == '-';
    if(p == end) return Integer.MIN_VALUE;
    int v = 0;
    for(; p < end; ++p) {
      final byte b = token[p];
      if(b < '0' || b > '9') break;
      if(v >= MAX_INT && (b > '7' || v > MAX_INT)) return Integer.MIN_VALUE;
      v = (v << 3) + (v << 1) + b - '0';
    }
    while(p < end && ws(token[p])) ++p;
    return p < end || v < 0 ? Integer.MIN_VALUE : m ? -v : v;
  }

  /**
   * Calculates a hash code for the specified token.
   * @param token specified token
   * @return hash code
   */
  public static int hashCode(final byte[] token) {
    final int tl = token.length, s = Math.max(1, tl / MAX_HASH_OPS);
    int h = 0;
    for(int t = 0; t < tl; t += s) {
      h = (h << 5) - h + token[t];
    }
    return h;
  }

  /**
   * Compares two tokens for equality.
   * @param token1 first token
   * @param token2 token to be compared
   * @param coll collation (can be {@code null})
   * @return true if the tokens are equal
   */
  public static boolean eq(final byte[] token1, final byte[] token2, final Collation coll) {
    return coll != null ? coll.compare(token1, token2) == 0 : eq(token1, token2);
  }

  /**
   * Compares two tokens for equality.
   * @param token1 first token
   * @param token2 token to be compared
   * @param deep equality
   * @return true if the tokens are equal
   */
  public static boolean eq(final byte[] token1, final byte[] token2, final DeepEqual deep) {
    final Normalizer.Form form = deep.options.get(NORMALIZATION_FORM);
    byte[] t1 = normalize(token1, form), t2 = normalize(token2, form);
    if(deep.options.get(WHITESPACE) == Whitespace.NORMALIZE) {
      t1 = normalize(t1);
      t2 = normalize(t2);
    }
    return eq(t1, t2, deep.coll);
  }

  /**
   * Compares two tokens for equality.
   * @param token1 first token (can be {@code null})
   * @param token2 token to be compared (can be {@code null})
   * @return true if the tokens are equal
   */
  public static boolean eq(final byte[] token1, final byte[] token2) {
    return Arrays.equals(token1, token2);
  }

  /**
   * Compares several tokens for equality.
   * @param token token (can be {@code null})
   * @param tokens tokens to be compared (single tokens can be {@code null})
   * @return true if one test is successful
   */
  public static boolean eq(final byte[] token, final byte[]... tokens) {
    for(final byte[] tok : tokens) {
      if(eq(token, tok)) return true;
    }
    return false;
  }

  /**
   * Compares two tokens lexicographically.
   * @param token first token
   * @param compare token to be compared
   * @return result of comparison (-1, 0, 1)
   */
  public static int compare(final byte[] token, final byte[] compare) {
    final int tl = token.length, cl = compare.length, l = Math.min(tl, cl);
    for(int i = 0; i < l; ++i) {
      final int c = (token[i] & 0xFF) - (compare[i] & 0xFF);
      if(c != 0) return Integer.signum(c);
    }
    return Integer.signum(tl - cl);
  }

  /**
   * Compares two tokens lexicographically.
   * @param token first token
   * @param compare token to be compared
   * @param coll collation (can be {@code null})
   * @return result of comparison (-1, 0, 1)
   */
  public static int compare(final byte[] token, final byte[] compare, final Collation coll) {
    return coll != null ? coll.compare(token, compare) : compare(token, compare);
  }

  /**
   * Returns the smaller token.
   * @param token first token
   * @param compare token to be compared
   * @return smaller token
   */
  public static byte[] min(final byte[] token, final byte[] compare) {
    return compare(token, compare) < 0 ? token : compare;
  }

  /**
   * Returns the bigger token.
   * @param token first token
   * @param compare token to be compared
   * @return bigger token
   */
  public static byte[] max(final byte[] token, final byte[] compare) {
    return compare(token, compare) > 0 ? token : compare;
  }

  /**
   * Checks if the first token contains the second token.
   * @param token token
   * @param sub token to be found
   * @return result of check
   */
  public static boolean contains(final byte[] token, final byte[] sub) {
    return contains(token, sub, 0);
  }

  /**
   * Checks if the first token contains the second token.
   * @param token token
   * @param sub token to be found
   * @param pos start position
   * @return result of check
   */
  public static boolean contains(final byte[] token, final byte[] sub, final int pos) {
    return indexOf(token, sub, pos) != -1;
  }

  /**
   * Checks if the first token contains the specified character.
   * @param token token
   * @param ch character to be found
   * @return result of check
   */
  public static boolean contains(final byte[] token, final int ch) {
    return indexOf(token, ch) != -1;
  }

  /**
   * Returns the position of the specified character.
   * @param token token
   * @param ch character to be found
   * @return position, or {@code -1} if token is not found.
   */
  public static int indexOf(final byte[] token, final int ch) {
    return indexOf(token, ch, 0);
  }

  /**
   * Returns the position of the specified character.
   * @param token token
   * @param ch character to be found
   * @param pos start position
   * @return position, or {@code -1} if token is not found.
   */
  public static int indexOf(final byte[] token, final int ch, final int pos) {
    final int tl = token.length;
    if(ch < 0x80) {
      for(int t = pos; t < tl; t++) {
        if(token[t] == ch) return t;
      }
    } else {
      for(int t = pos; t < tl; t += cl(token, t)) {
        if(cp(token, t) == ch) return t;
      }
    }
    return -1;
  }

  /**
   * Returns the last position of the specified character.
   * @param token token
   * @param ch character to be found
   * @return position, or {@code -1} if token is not found.
   */
  public static int lastIndexOf(final byte[] token, final int ch) {
    final int tl = token.length;
    int p = -1;
    if(ch < 0x80) {
      for(int t = tl - 1; t >= 0; --t) {
        if(token[t] == ch) return t;
      }
    } else {
      for(int t = 0; t < tl; t += cl(token, t)) {
        if(cp(token, t) == ch) p = t;
      }
    }
    return p;
  }

  /**
   * Returns the position of the specified token.
   * @param token token
   * @param sub token to be found
   * @return position, or {@code -1} if token is not found.
   */
  public static int indexOf(final byte[] token, final byte[] sub) {
    return indexOf(token, sub, 0);
  }

  /**
   * Returns the position of the specified token.
   * @param token token
   * @param sub token to be found
   * @param pos start position
   * @return position, or {@code -1} if token is not found.
   */
  public static int indexOf(final byte[] token, final byte[] sub, final int pos) {
    final int sl = sub.length;
    if(sl == 0) return pos;
    final int tl = token.length - sl;
    if(pos > tl) return -1;

    // compare tokens character wise
    for(int t = pos; t <= tl; ++t) {
      int s = 0;
      while(sub[s] == token[t + s]) {
        if(++s == sl) return t;
      }
    }
    return -1;
  }

  /**
   * Checks if the first token starts with the specified character.
   * @param token token
   * @param ch character to be found
   * @return result of check
   */
  public static boolean startsWith(final byte[] token, final int ch) {
    return startsWith(token, ch, 0);
  }

  /**
   * Checks if the first token starts with the specified character.
   * @param token token
   * @param ch character to be found
   * @param pos start position
   * @return result of check
   */
  private static boolean startsWith(final byte[] token, final int ch, final int pos) {
    return pos < token.length && token[pos] == ch;
  }

  /**
   * Checks if the first token starts with the second token.
   * @param token token
   * @param sub token to be found
   * @return result of check
   */
  public static boolean startsWith(final byte[] token, final byte[] sub) {
    return startsWith(token, sub, 0);
  }

  /**
   * Checks if the first token starts with the second token.
   * @param token token
   * @param sub token to be found
   * @param pos start position
   * @return result of check
   */
  public static boolean startsWith(final byte[] token, final byte[] sub, final int pos) {
    final int sl = sub.length;
    if(sl > token.length - pos) return false;
    for(int s = 0, p = pos; s < sl; ++s, ++p) {
      if(sub[s] != token[p]) return false;
    }
    return true;
  }

  /**
   * Checks if the first token starts with the specified character.
   * @param token token
   * @param ch character to be bound
   * @return result of check
   */
  public static boolean endsWith(final byte[] token, final int ch) {
    return token.length != 0 && token[token.length - 1] == ch;
  }

  /**
   * Checks if the first token ends with the second token.
   * @param token token
   * @param sub token to be found
   * @return result of check
   */
  public static boolean endsWith(final byte[] token, final byte[] sub) {
    final int sl = sub.length;
    final int tl = token.length;
    if(sl > tl) return false;
    for(int s = sl; s > 0; s--) {
      if(sub[sl - s] != token[tl - s]) return false;
    }
    return true;
  }

  /**
   * Returns a substring of the specified token.
   * Note that this method ignores Unicode codepoints; use {@link #subtoken} instead.
   * @param token input token
   * @param start start position
   * @return substring
   */
  public static byte[] substring(final byte[] token, final int start) {
    return substring(token, start, token.length);
  }

  /**
   * Returns a substring of the specified token.
   * Note that this method ignores Unicode codepoints; use {@link #subtoken} instead.
   * @param token input token
   * @param start start position
   * @param end end position
   * @return substring
   */
  public static byte[] substring(final byte[] token, final int start, final int end) {
    final int tl = token.length, s = Math.max(0, start), e = Math.min(end, tl);
    return s == 0 && e == tl ? token : s < e ? Arrays.copyOfRange(token, s, e) : EMPTY;
  }

  /**
   * Returns a partial token.
   * @param token input token
   * @param start start position
   * @return resulting text
   */
  public static byte[] subtoken(final byte[] token, final int start) {
    return subtoken(token, start, token.length);
  }

  /**
   * Returns a partial token.
   * @param token input text
   * @param start start position
   * @param end end position
   * @return resulting text
   */
  public static byte[] subtoken(final byte[] token, final int start, final int end) {
    int s = Math.max(0, start);
    final int e = Math.min(end, token.length);
    if(s == 0 && e == token.length) return token;
    if(s >= e) return EMPTY;

    int t = Math.max(0, s - 4);
    for(; t != s && t < e; t += cl(token, t)) {
      if(t >= s) s = t;
    }
    for(; t < e; t += cl(token, t));
    return Arrays.copyOfRange(token, s, t);
  }

  /**
   * Splits a token around matches of the given separator.
   * @param token token to be split
   * @param separator separator character
   * @return array
   */
  public static byte[][] split(final byte[] token, final int separator) {
    return split(token, separator, false);
  }

  /**
   * Splits a token around matches of the given separator.
   * @param token token to be split
   * @param separator separator character
   * @param empty include empty tokens
   * @return array
   */
  public static byte[][] split(final byte[] token, final int separator, final boolean empty) {
    final TokenList split = new TokenList();
    final TokenBuilder tb = new TokenBuilder();
    forEachCp(token, cp -> {
      if(cp == separator) {
        if(empty || !tb.isEmpty()) split.add(tb.next());
      } else {
        tb.add(cp);
      }
    });
    if(empty || !tb.isEmpty()) split.add(tb.finish());
    return split.finish();
  }

  /**
   * Normalizes the specified input and returns its distinct tokens.
   * Optimized for small number of tokens.
   * @param token token
   * @return distinct tokens
   */
  public static byte[][] distinctTokens(final byte[] token) {
    final byte[][] tokens = split(normalize(token), ' ');
    int tl = tokens.length;
    for(int i = 0; i < tl - 1; i++) {
      for(int j = i + 1; j < tl; j++) {
        if(eq(tokens[i], tokens[j])) {
          Array.remove(tokens, j, 1, tl);
          j--; tl--;
        }
      }
    }
    return Array.copyOf(tokens, tl);
  }

  /**
   * Checks if the specified token has only whitespace.
   * @param token token
   * @return true if all characters are whitespace
   */
  public static boolean ws(final byte[] token) {
    for(final byte b : token) {
      if(!ws(b)) return false;
    }
    return true;
  }

  /**
   * Replaces the specified character and returns the result token.
   * @param token token to be checked
   * @param search the character to be replaced
   * @param replace the new character
   * @return resulting token
   */
  public static byte[] replace(final byte[] token, final int search, final int replace) {
    if(!contains(token, search)) return token;

    final TokenBuilder tb = new TokenBuilder(token.length);
    forEachCp(token, cp -> tb.add(cp == search ? replace : cp));
    return tb.finish();
  }

  /**
   * Removes leading and trailing whitespace from the specified token.
   * @param token token to be trimmed
   * @return trimmed token
   */
  public static byte[] trim(final byte[] token) {
    int s = -1, e = token.length;
    while(++s < e) {
      if(!ws(token[s])) break;
    }
    while(--e > s) {
      if(!ws(token[e])) break;
    }
    if(++e == token.length && s == 0) return token;
    return s == e ? EMPTY : Arrays.copyOfRange(token, s, e);
  }

  /**
   * Chops a token to the specified length. Appends trailing dots if the string is too long.
   * @param token token to be chopped
   * @param max maximum length
   * @return chopped token
   */
  public static byte[] chop(final byte[] token, final int max) {
    if(length(token) <= max) return token;
    final TokenBuilder tb = new TokenBuilder(max + 3);
    for(int c = 0, t = 0; c < max; c++, t += cl(token, t)) tb.add(cp(token, t));
    return tb.add('.').add('.').add('.').finish();
  }

  /**
   * Concatenates multiple tokens.
   * @param tokens tokens
   * @return resulting token
   */
  public static byte[] concat(final byte[]... tokens) {
    int sl = 0;
    for(final byte[] token : tokens) sl += token.length;
    final byte[] tmp = new byte[sl];
    int i = 0;
    for(final byte[] token : tokens) {
      final int tl = token.length;
      Array.copyFromStart(token, tl, tmp, i);
      i += tl;
    }
    return tmp;
  }

  /**
   * Concatenates multiple objects.
   * @param objects objects
   * @return resulting token
   */
  public static byte[] concat(final Object... objects) {
    final int ol = objects.length;
    final byte[][] tokens = new byte[ol][];
    for(int o = 0; o < ol; o++) tokens[o] = token(objects[o]);
    return concat(tokens);
  }

  /**
   * Deletes a character from a token.
   * @param token token
   * @param ch character to be removed
   * @return resulting token
   */
  public static byte[] delete(final byte[] token, final int ch) {
    // ascii character
    final int tl = token.length;
    if(ch < 0x80) {
      // skip deletion if character is not found
      if(!contains(token, ch)) return token;

      final TokenBuilder tb = new TokenBuilder(tl);
      for(final byte b : token) {
        if(b != ch) tb.add(b);
      }
      return tb.finish();
    }
    // remove character
    final TokenBuilder tb = new TokenBuilder(tl);
    forEachCp(token, cp -> { if(cp != ch) tb.add(cp); });
    return tb.finish();
  }

  /**
   * Normalizes all whitespace occurrences from the specified token.
   * @param token token
   * @return normalized token
   */
  public static byte[] normalize(final byte[] token) {
    final int tl = token.length;
    if(tl == 0) return token;
    final byte[] tmp = new byte[tl];
    int c = 0;
    boolean ws1 = true;
    for(final byte b : token) {
      final boolean ws2 = ws(b);
      if(ws2 && ws1) continue;
      tmp[c++] = ws2 ? (byte) ' ' : b;
      ws1 = ws2;
    }
    if(c > 0 && ws(tmp[c - 1])) --c;
    return c == tl ? tmp : Arrays.copyOf(tmp, c);
  }

  /**
   * Checks if the specified character is a whitespace.
   * @param ch the character to be checked
   * @return result of check
   */
  public static boolean ws(final int ch) {
    return ch == 0x09 || ch == 0x0A || ch == 0x0D || ch == 0x20;
  }

  /**
   * Checks if the specified character is a computer letter (A - Z, a - z, _).
   * @param ch the character to be checked
   * @return result of check
   */
  public static boolean letter(final int ch) {
    return ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch == '_';
  }

  /**
   * Checks if the specified character is a digit (0 - 9).
   * @param ch the character to be checked
   * @return result of check
   */
  public static boolean digit(final int ch) {
    return ch >= '0' && ch <= '9';
  }

  /**
   * Checks if the specified character is a computer letter or digit.
   * @param ch the character to be checked
   * @return result of check
   */
  public static boolean letterOrDigit(final int ch) {
    return letter(ch) || digit(ch);
  }

  /**
   * Converts the specified token to upper case.
   * @param token token to be converted
   * @return resulting token
   */
  public static byte[] uc(final byte[] token) {
    return uc(token, ascii(token));
  }

  /**
   * Converts the specified token to upper case.
   * @param token token to be converted
   * @param ascii ASCII flag
   * @return resulting token
   */
  public static byte[] uc(final byte[] token, final boolean ascii) {
    if(ascii) {
      final int tl = token.length;
      final byte[] tok = new byte[tl];
      for(int t = 0; t < tl; t++) tok[t] = (byte) uc(token[t]);
      return tok;
    }
    return token(string(token).toUpperCase(Locale.ENGLISH));
  }

  /**
   * Converts the specified token to title case.
   * @param token token to be converted
   * @return resulting token
   */
  public static byte[] tc(final byte[] token) {
    final int tl = token.length;
    final TokenBuilder tb = new TokenBuilder(tl);
    boolean lod = false;
    for(int t = 0; t < tl; t += cl(token, t)) {
      final int cp = cp(token, t);
      tb.add(lod ? lc(cp) : uc(cp));
      lod = Character.isLetterOrDigit(cp);
    }
    return tb.finish();
  }

  /**
   * Converts a character to upper case.
   * @param ch character to be converted
   * @return resulting character
   */
  public static int uc(final int ch) {
    return ch >= 'a' && ch <= 'z' ? ch - 0x20 : ch > 0x7F ? Character.toUpperCase(ch) : ch;
  }

  /**
   * Converts the specified token to lower case.
   * @param token token to be converted
   * @return resulting token
   */
  public static byte[] lc(final byte[] token) {
    return lc(token, ascii(token));
  }

  /**
   * Converts the specified token to lower case.
   * @param token token to be converted
   * @param ascii ASCII flag
   * @return resulting token
   */
  public static byte[] lc(final byte[] token, final boolean ascii) {
    if(ascii) {
      final int tl = token.length;
      final byte[] tok = new byte[tl];
      for(int t = 0; t < tl; t++) tok[t] = (byte) lc(token[t]);
      return tok;
    }
    return token(string(token).toLowerCase(Locale.ENGLISH));
  }

  /**
   * Converts a character to lower case.
   * @param ch character to be converted
   * @return resulting character
   */
  public static int lc(final int ch) {
    return ch >= 'A' && ch <= 'Z' ? ch | 0x20 : ch > 0x7F ? Character.toLowerCase(ch) : ch;
  }

  /**
   * Returns the prefix of the specified token.
   * @param name name
   * @return prefix or empty token if no prefix exists
   */
  public static byte[] prefix(final byte[] name) {
    final int i = indexOf(name, ':');
    return i == -1 ? EMPTY : substring(name, 0, i);
  }

  /**
   * Returns the local name of the specified name.
   * @param name name
   * @return local name
   */
  public static byte[] local(final byte[] name) {
    final int i = indexOf(name, ':');
    return i == -1 ? name : substring(name, i + 1);
  }

  /** URI encoder. */
  public enum UriEncoder {
    /** IRI. */ IRI {
      @Override
      boolean literal(final int ch) { return "!#$%&*'()+,-./:;=?@[]~_".indexOf(ch) != -1; }
    },
    /** URI. */ URI {
      @Override
      boolean literal(final int ch) { return "-._~".indexOf(ch) != -1; }
    },
    /** Escape URI. */ ESCAPE {
      @Override
      boolean literal(final int ch) { return ch >= 0x20 && ch <= 0x7e; }
    },
    /** Path component. */ PATH {
      @Override
      boolean literal(final int ch) { return (ch < 0 || ch > ' ') && "%#+[]/?".indexOf(ch) == -1; }
    },
    /** Query component. */ QUERY {
      @Override
      boolean literal(final int ch) { return (ch < 0 || ch > ' ') && "%#+[]=&".indexOf(ch) == -1; }
    },
    /** Fragment component. */ FRAGMENT {
      @Override
      boolean literal(final int ch) { return (ch < 0 || ch > ' ') && "%#+[]".indexOf(ch) == -1; }
    };

    /**
     * Test if characters needs to be encoded.
     * @param ch character
     * @return result of check
     */
    abstract boolean literal(int ch);
  }

  /**
   * Returns a URI-encoded token.
   * @param token token
   * @param encoder test encoding
   * @return encoded token
   */
  public static byte[] encodeUri(final byte[] token, final UriEncoder encoder) {
    final TokenBuilder tb = new TokenBuilder();
    for(final byte b : token) {
      if(letterOrDigit(b) || encoder.literal(b)) {
        tb.add(b);
      } else {
        tb.add('%').add(hex(b, 2));
      }
    }
    return tb.finish();
  }

  /**
   * Returns a hex representation of the specified value.
   * @param value value
   * @param length length of hex output
   * @return hex representation
   */
  public static byte[] hex(final int value, final int length) {
    final TokenBuilder tb = new TokenBuilder(length);
    for(int l = length - 1; l >= 0; l--) tb.add(HEX_TABLE[value >> (l << 2) & 0xF]);
    return tb.finish();
  }

  /**
   * Returns a hex representation of the specified byte array.
   * @param value values to be mapped
   * @param uc upper case
   * @return hex representation
   */
  public static byte[] hex(final byte[] value, final boolean uc) {
    final int vl = value.length, u = uc ? 0x37 : 0x57;
    final byte[] data = new byte[Array.checkCapacity((long) vl << 1)];
    for(int v = 0, d = 0; v < vl; v++) {
      final int a = value[v], b = a >> 4 & 0x0F, c = a & 0x0F;
      data[d++] = (byte) (b + (b > 9 ? u : '0'));
      data[d++] = (byte) (c + (c > 9 ? u : '0'));
    }
    return data;
  }

  /**
   * Converts a hex character to an integer value.
   * @param ch character
   * @return integer value, or {@code -1} if the input is invalid
   */
  public static int dec(final int ch) {
    if(ch >= '0' && ch <= '9') return ch - '0';
    if(ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F') return (ch & 0x0F) + 9;
    return -1;
  }

  /**
   * Converts hex characters to an integer value.
   * @param ch1 first character
   * @param ch2 second character
   * @return integer value, or {@code -1} if the input is invalid
   */
  public static int dec(final int ch1, final int ch2) {
    final int n1 = dec(ch1), n2 = dec(ch2);
    return n1 < 0 || n2 < 0 ? -1 : n1 << 4 | n2;
  }

  /**
   * Normalizes Unicode characters in the specified token.
   * @param token token
   * @param form normalization form (can be {@code null})
   * @return normalized token
   */
  public static byte[] normalize(final byte[] token, final Normalizer.Form form) {
    return form == null || ascii(token) ? token : token(Normalizer.normalize(string(token), form));
  }
}
