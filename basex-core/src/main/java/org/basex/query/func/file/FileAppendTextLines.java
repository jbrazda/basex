package org.basex.query.func.file;

import java.io.*;

import org.basex.query.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;

/**
 * Function implementation.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class FileAppendTextLines extends FileWriteTextLines {
  @Override
  public Item item(final QueryContext qc) throws IOException, QueryException {
    write(true, true, qc);
    return Empty.VALUE;
  }
}
