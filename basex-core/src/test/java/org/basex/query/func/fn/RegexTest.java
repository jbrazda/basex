package org.basex.query.func.fn;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

import org.basex.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

/**
 * This class tests {@link RegExFn}.
 *
 * @author BaseX Team, BSD License
 * @author Gunther Rademacher
 */
public final class RegexTest extends SandboxTest {
  /**
   * Test.
   * @param regex regular expression
   * @param parentGroups expected parent group IDs
   */
  @ParameterizedTest
  @MethodSource
  public void testParentGroups(final String regex, final int[] parentGroups) {
    final int[] actualGroups = RegExFn.GroupScanner.groupInfo(regex).parentGroups;
    assertArrayEquals(parentGroups, actualGroups,
        () -> "Unexpected result: " + Arrays.toString(actualGroups));
    assertEquals(Pattern.compile(regex).matcher("").groupCount(), parentGroups.length);
  }

  /**
   * Test arguments.
   * @return test arguments
   */
  private static Stream<Arguments> testParentGroups() {
    return Stream.of(
        Arguments.of("(b)(x?)", new int[] {0, 0}),
        Arguments.of("(b(x?))", new int[] {0, 1}),
        Arguments.of("(?:((())(?:())))", new int[] {0, 1, 2, 1}),
        Arguments.of("(([()&&[()]]))()", new int[] {0, 1, 0}),
        Arguments.of("((\\Q()\\E))()", new int[] {0, 1, 0}),
        Arguments.of("(?<x>())", new int[] {0, 1}),
        Arguments.of("(?:<x>)", new int[] {}),
        Arguments.of("^(.*?)d(.*)(?:$(?!\\s))", new int[] {0, 0}),
        Arguments.of("^((?:aa)*)(?:X+((?:\\p{Nd}+|\\-)(?:X+(.+))?))?(?:$(?!\\s))",
            new int[] {0, 0, 2}),
        Arguments.of("^(?:((ab)(ac){0,2})?)(?:$(?!\\s))", new int[] {0, 1, 1}),
        Arguments.of("^(?:[abcd&&[^d]]+)(?:$(?!\\s))", new int[] {}),
        Arguments.of("(?:(((((boy)|(girl))[0-1][x-z]{2})?)|(man|woman)[0-1]?[y\\|n])*)(?:$(?!\\s))",
            new int[] {0, 1, 2, 3, 4, 4, 1}),
        Arguments.of("(((((((((((((((a)(b)))))))))))))))",
            new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 14}),
        Arguments.of("^(?:\\-\\-((0[1-9])|(1(1|2)))\\-\\-)(?:$(?!\\s))", new int[] {0, 1, 1, 3})
    );
  }

  /** Test method. */
  @Test public void gh2328() {
    query("for $p in ('[^0', '[^0-') return try {matches('a', $p)} catch * {}", "");
  }
}
