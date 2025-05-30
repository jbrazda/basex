package org.basex.query.func;

import org.basex.query.expr.*;
import org.basex.util.*;

/**
 * Interface for built-in functions.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
@FunctionalInterface
public interface AFunction {
  /**
   * Returns the function definition.
   * @return definition
   */
  FuncDefinition definition();

  /**
   * Creates a new instance of the function.
   * @param info input info (can be {@code null})
   * @param args function arguments
   * @return function
   */
  default StandardFunc get(final InputInfo info, final Expr... args) {
    return definition().get(info, args);
  }

  /**
   * Checks if the specified expression is an instance of this function.
   * @param ex expression
   * @return result of check
   */
  default boolean is(final Expr ex) {
    return ex instanceof final StandardFunc sf && sf.definition == definition();
  }

  /**
   * Returns a string representation of the function with the specified arguments
   * (see {@link FuncDefinition}).
   * @param args arguments
   * @return string representation with leading space (simplifies nesting of returned string)
   */
  default String args(final Object... args) {
    return definition().args(args);
  }

  /**
   * Returns the class name of a function implementation. Only called by tests.
   * @return class name
   */
  default String className() {
    return Util.className(definition().supplier.get().getClass());
  }
}
