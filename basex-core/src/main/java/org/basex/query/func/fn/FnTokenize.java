package org.basex.query.func.fn;

import static org.basex.query.func.Function.*;

import java.util.regex.*;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.iter.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.util.*;
import org.basex.util.list.*;

/**
 * Function implementation.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class FnTokenize extends RegExFn {
  /** Default pattern. */
  private static final byte[] DEFAULT = Token.cpToken(' ');
  /** Placeholder for default search. */
  private static final byte[] WHITESPACE = Token.token("\\s+");

  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    final byte[] pattern = pattern(qc);
    final byte[] value = input(pattern, qc);
    final byte[] flags = toZeroToken(arg(2), qc);
    final boolean simple = pattern == DEFAULT || flags.length == 0;
    final int vl = value.length;

    if(simple) {
      final int ch = patternChar(pattern);
      if(ch != -1) {
        final int sl = pattern.length;
        return vl == 0 ? Empty.ITER : new Iter() {
          int start;

          @Override
          public Item next() {
            if(start == -1) return null;
            final int e = Token.indexOf(value, ch, start);
            return e != -1 ? next(e, e + sl) : next(vl, -1);
          }

          private Str next(final int end, final int next) {
            final int b = start;
            start = next;
            return Str.get(Token.substring(value, b, end));
          }
        };
      }
    }

    final Pattern p = pattern(pattern, flags);
    return vl == 0 ? Empty.ITER : new Iter() {
      final String string = Token.string(value);
      final Matcher matcher = p.matcher(string);
      int start;

      @Override
      public Item next() {
        return start == -1 ? null : matcher.find() ?
          next(matcher.start(), matcher.end()) : next(string.length(), -1);
      }

      private Str next(final int end, final int next) {
        final int b = start;
        start = next;
        return Str.get(Token.token(string.substring(b, end)));
      }
    };
  }

  @Override
  public Value value(final QueryContext qc) throws QueryException {
    final byte[] pattern = pattern(qc);
    final byte[] value = input(pattern, qc);
    final byte[] flags = toZeroToken(arg(2), qc);
    final boolean simple = pattern == DEFAULT || flags.length == 0;
    final int vl = value.length;

    if(simple) {
      final int ch = patternChar(pattern);
      if(ch != -1) return vl == 0 ? Empty.VALUE : StrSeq.get(Token.split(value, ch, true));
    }

    final Pattern p = pattern(pattern, flags);
    if(vl == 0) return Empty.VALUE;

    final TokenList tl = new TokenList();
    final String string = Token.string(value);
    final int len = string.length();
    int start = 0;
    for(final Matcher matcher = p.matcher(string); matcher.find();) {
      final int ms = matcher.start(), me = matcher.end();
      if(ms != len && me != 0) {
        tl.add(string.substring(start, ms));
        start = me;
      }
    }
    return StrSeq.get(tl.add(string.substring(start)));
  }

  /**
   * Returns the pattern argument.
   * @param qc query context
   * @return pattern or {@code null}
   * @throws QueryException query exception
   */
  private byte[] pattern(final QueryContext qc) throws QueryException {
    final byte[] pattern = toTokenOrNull(arg(1), qc);
    return pattern != null ? pattern : DEFAULT;
  }

  /**
   * Returns the input argument.
   * @param pattern pattern
   * @param qc query context
   * @return input
   * @throws QueryException query exception
   */
  private byte[] input(final byte[] pattern, final QueryContext qc) throws QueryException {
    final byte[] value = toZeroToken(arg(0), qc);
    return pattern == DEFAULT ? Token.normalize(value) : value;
  }

  @Override
  protected Expr opt(final CompileContext cc) throws QueryException {
    final Expr value = arg(0), pattern = arg(1);

    // tokenize(normalize-space(A), ' ')  ->  tokenize(A)
    if(NORMALIZE_SPACE.is(value) && pattern instanceof final Str str &&
        Token.eq(str.string(), Token.cpToken(' '))) {
      final Expr arg = value.args().length == 1 ? value.arg(0) : ContextValue.get(cc, info);
      return cc.function(TOKENIZE, info, arg);
    }
    return this;
  }

  /**
   * Indicates if default whitespace tokenization is to be performed.
   * @return result of check
   */
  public boolean whitespace() {
    final Expr pattern = arg(1);
    return pattern == Empty.VALUE || pattern == Empty.UNDEFINED ||
        pattern instanceof final Str str && Token.eq(str.string(), WHITESPACE);
  }
}
