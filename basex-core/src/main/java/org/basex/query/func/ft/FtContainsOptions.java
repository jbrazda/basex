package org.basex.query.func.ft;

import org.basex.util.ft.*;
import org.basex.util.options.*;

/**
 * Full-text options.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class FtContainsOptions extends FtIndexOptions {
  /** Option: case. */
  public static final EnumOption<FTCase> CASE = new EnumOption<>("case", FTCase.class);
  /** Option: case. */
  public static final EnumOption<FTDiacritics> DIACRITICS =
      new EnumOption<>("diacritics", FTDiacritics.class);
  /** Option: stemming. */
  public static final BooleanOption STEMMING = new BooleanOption("stemming");
  /** Option: language. */
  public static final StringOption LANGUAGE = new StringOption("language");
}
