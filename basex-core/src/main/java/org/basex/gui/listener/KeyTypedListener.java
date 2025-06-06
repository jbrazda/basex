package org.basex.gui.listener;

import java.awt.event.*;

/**
 * Listener interface for released keys.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
@FunctionalInterface
public interface KeyTypedListener extends KeyListener {
  @Override
  default void keyPressed(final KeyEvent e) { }

  @Override
  default void keyReleased(final KeyEvent e) { }
}
