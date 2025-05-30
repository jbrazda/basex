package org.basex.query.expr.index;

import org.basex.data.*;
import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.util.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * This class defines a static database source for index operations.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class IndexStaticDb extends IndexDb {
  /**
   * Constructor.
   * @param data data reference
   * @param info input info (can be {@code null})
   */
  public IndexStaticDb(final Data data, final InputInfo info) {
    super(info);
    exprType.data(data);
  }

  @Override
  public void checkUp() {
  }

  @Override
  public Expr compile(final CompileContext cc) {
    return this;
  }

  @Override
  public boolean has(final Flag... flags) {
    return false;
  }

  @Override
  public boolean inlineable(final InlineContext ic) {
    return true;
  }

  @Override
  public VarUsage count(final Var var) {
    return VarUsage.NEVER;
  }

  @Override
  public IndexDb inline(final InlineContext ic) {
    return null;
  }

  @Override
  public boolean accept(final ASTVisitor visitor) {
    return true;
  }

  @Override
  public int exprSize() {
    return 1;
  }

  @Override
  public IndexDb copy(final CompileContext cc, final IntObjectMap<Var> vm) {
    return copyType(new IndexStaticDb(data(), info));
  }

  @Override
  Data data(final QueryContext qc) {
    return data();
  }

  @Override
  public boolean equals(final Object obj) {
    return obj instanceof final IndexStaticDb is && data() == is.data() && super.equals(obj);
  }

  @Override
  public void toXml(final QueryPlan plan) {
    plan.add(plan.create(this));
  }

  @Override
  public void toString(final QueryString qs) {
    qs.quoted(Token.token(data().meta.name));
  }
}
