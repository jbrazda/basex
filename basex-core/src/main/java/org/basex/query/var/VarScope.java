package org.basex.query.var;

import java.util.*;
import java.util.Map.Entry;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.scope.*;
import org.basex.query.util.*;
import org.basex.query.value.item.*;
import org.basex.query.value.type.*;
import org.basex.util.*;
import org.basex.util.hash.*;

/**
 * The scope of variables, either the query, a user-defined or an inline function.
 *
 * @author BaseX Team, BSD License
 * @author Leo Woerteler
 */
public final class VarScope {
  /** Local variables in this scope. */
  public final ArrayList<Var> vars = new ArrayList<>();

  /**
   * Adds a variable to this scope.
   * @param var variable to be added
   * @return the variable (for convenience)
   */
  public Var add(final Var var) {
    var.slot = vars.size();
    vars.add(var);
    return var;
  }

  /**
   * Creates a new local variable in this scope.
   * @param name variable name
   * @param st type of the variable (can be {@code null})
   * @param qc query context
   * @param info input info (can be {@code null})
   * @return the variable
   */
  public Var addNew(final QNm name, final SeqType st, final QueryContext qc, final InputInfo info) {
    return add(new Var(name, st, qc, info));
  }

  /**
   * Enters this scope.
   * @param qc query context
   * @return old frame pointer
   */
  public int enter(final QueryContext qc) {
    return qc.stack.enterFrame(vars.size());
  }

  /**
   * Exits this scope.
   * @param fp frame pointer
   * @param qc query context
   */
  public void exit(final int fp, final QueryContext qc) {
    qc.stack.exitFrame(fp);
  }

  /**
   * Deletes all unused variables from this scope and assigns stack slots.
   * @param scope the scope
   */
  public void cleanUp(final Scope scope) {
    final BitSet declared = new BitSet();
    final BitSet used = new BitSet();
    scope.visit(new ASTVisitor() {
      @Override
      public boolean declared(final Var var) {
        declared.set(var.id);
        return true;
      }

      @Override
      public boolean used(final VarRef ref) {
        used.set(ref.var.id);
        return true;
      }
    });

    // purge all unused variables
    final Iterator<Var> iter = vars.iterator();
    while(iter.hasNext()) {
      final Var var = iter.next();
      if(!declared.get(var.id)) {
        var.slot = -1;
        iter.remove();
      }
    }

    // remove unused entries from the closure
    if(scope instanceof final Closure closure) {
      final Iterator<Entry<Var, Expr>> bindings = closure.globalBindings();
      while(bindings.hasNext()) {
        final Var var = bindings.next().getKey();
        if(!used.get(var.id)) {
          bindings.remove();
          var.slot = -1;
          vars.remove(var);
        }
      }
    }

    // assign new stack slots
    for(int v = vars.size(); --v >= 0;) vars.get(v).slot = v;
  }

  @Override
  public String toString() {
    return Util.className(this) + vars;
  }

  /**
   * Stack-frame size needed for this scope.
   * @return stack-frame size
   */
  public int stackSize() {
    return vars.size();
  }

  /**
   * Returns a copy of this variable scope.
   * @param cc compilation context
   * @param vm variable mapping
   */
  public void copy(final CompileContext cc, final IntObjectMap<Var> vm) {
    for(final Var var : vars) cc.copy(var, vm);
  }
}
