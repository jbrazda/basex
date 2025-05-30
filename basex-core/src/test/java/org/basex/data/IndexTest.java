package org.basex.data;

import static org.basex.query.func.Function.*;

import org.basex.*;
import org.basex.core.*;
import org.basex.core.cmd.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

/**
 * This class tests the {@link MainOptions#UPDINDEX} and {@link MainOptions#AUTOOPTIMIZE} options.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class IndexTest extends SandboxTest {
  /**
   * Finalize test.
   */
  @AfterEach public void after() {
    execute(new DropDB(NAME));
    set(MainOptions.TOKENINDEX, false);
    set(MainOptions.UPDINDEX, false);
    set(MainOptions.AUTOOPTIMIZE, false);
    set(MainOptions.MAINMEM, false);
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void updindexText(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    set(MainOptions.UPDINDEX, true);
    execute(new CreateDB(NAME));
    for(int i = 0; i < 5; i++) {
      execute(new Put("x.xml", "<x><a>A</a><a>B</a></x>"));
    }
    query(_DB_TEXT.args(NAME, "A"), "A");
    query(_DB_TEXT.args(NAME, "B"), "B");
    query(_DB_INFO.args(NAME) + "//textindex/text()", true);
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void updindexText2(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    set(MainOptions.UPDINDEX, true);
    execute(new CreateDB(NAME));
    for(int i = 0; i < 5; i++) {
      execute(new Put("x.xml", "<x><a>A</a><a>B</a></x>"));
      execute(new Put("x.xml", "<x><a>A</a><a>C</a></x>"));
    }
    query(_DB_TEXT.args(NAME, "A"), "A");
    query(_DB_TEXT.args(NAME, "C"), "C");
    query(_DB_TEXT.args(NAME, "B"), "");
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void updindexText3(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    set(MainOptions.UPDINDEX, true);
    execute(new CreateDB(NAME));
    for(int i = 0; i < 5; i++) {
      execute(new Put("x.xml", "<x><a>A</a><a>BC</a><a>DEF</a></x>"));
    }
    query(_DB_TEXT.args(NAME, "A"), "A");
    query(_DB_TEXT.args(NAME, "BC"), "BC");
    query(_DB_TEXT.args(NAME, "DEF"), "DEF");
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void updindexAttribute(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    set(MainOptions.UPDINDEX, true);
    execute(new CreateDB(NAME));
    for(int i = 0; i < 5; i++) {
      execute(new Add("a", "<x c='c'/>"));
      execute(new Add("a", "<x a='a' b='b'/>"));
      execute(new Put("a", "<x/>"));
    }
    query(_DB_ATTRIBUTE.args(NAME, "a"), "");
    query(_DB_ATTRIBUTE.args(NAME, "b"), "");
    query(_DB_ATTRIBUTE.args(NAME, "c"), "");
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void updindexToken(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    set(MainOptions.UPDINDEX, true);
    set(MainOptions.TOKENINDEX, true);
    execute(new CreateDB(NAME));

    execute(new Add("a", "<x c='c'/>"));
    query(_DB_TOKEN.args(NAME, "a"), "");
    query("data(" + _DB_TOKEN.args(NAME, "c") + ')', "c");

    for(int i = 0; i < 5; i++) {
      execute(new Add("a", "<x c='c'/>"));
      execute(new Add("a", "<x a='a' b='b'/>"));
      execute(new Put("a", "<x/>"));
    }
    query(_DB_TOKEN.args(NAME, "a"), "");
    query(_DB_TOKEN.args(NAME, "b"), "");
    query(_DB_TOKEN.args(NAME, "c"), "");
    query(_DB_INFO.args(NAME) + "//tokenindex/text()", true);
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void updindexReplace1(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    set(MainOptions.UPDINDEX, true);
    execute(new CreateDB(NAME, "<X><A>q</A><B>q</B></X>"));
    query("replace node /X/A with 'x', replace node /X/B with 'y'", "");
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void updindexReplace2(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    set(MainOptions.UPDINDEX, true);
    execute(new CreateDB(NAME));
    execute(new Put("A", "<X a='?' b='a' c='1'/>"));
    execute(new Put("A", "<X a='?' b='b' c='2'/>"));
    execute(new Put("A", "<X/>"));
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void updindexOpenClose1(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    final boolean openClose = !mainmem;
    set(MainOptions.UPDINDEX, true);
    execute(new CreateDB(NAME));
    for(int i = 0; i < 5; i++) {
      if(openClose) execute(new Open(NAME));
      execute(new Put("x.xml", "<x><a>A</a><a>BC</a></x>"));
      if(openClose) execute(new Close());
    }
    query(_DB_TEXT.args(NAME, "A"), "A");
    query(_DB_TEXT.args(NAME, "BC"), "BC");
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void updindexOpenClose2(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    final boolean openClose = !mainmem;
    set(MainOptions.UPDINDEX, true);
    execute(new CreateDB(NAME));
    execute(new Put("A", "<a/>"));
    execute(new Put("B", "<a a='1'/>"));
    execute(new Put("C", "<a a='1'/>"));
    execute(new Put("A", "<a a='1'/>"));
    if(openClose) {
      execute(new Close());
      execute(new Open(NAME));
    }
    execute(new Delete("A"));
  }

  /**
   * Test.
   * @param mainmem main memory flag.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void autooptimize(final boolean mainmem) {
    set(MainOptions.MAINMEM, mainmem);
    set(MainOptions.AUTOOPTIMIZE, true);
    execute(new CreateDB(NAME));
    query(_DB_INFO.args(NAME) + "//textindex/text()", true);
    execute(new Put("x.xml", "<a>A</a>"));
    query(_DB_INFO.args(NAME) + "//textindex/text()", true);
    query(_DB_PUT.args(NAME, " <a>B</a>", "x.xml"));
    query(_DB_INFO.args(NAME) + "//textindex/text()", true);

    set(MainOptions.AUTOOPTIMIZE, false);
    execute(new Optimize());
    execute(new Put("x.xml", "<a>C</a>"));
    query(_DB_INFO.args(NAME) + "//textindex/text()", false);

    execute(new Optimize());
    query(_DB_INFO.args(NAME) + "//textindex/text()", true);
    query(_DB_PUT.args(NAME, " <a>D</a>", "x.xml"));
    query(_DB_INFO.args(NAME) + "//textindex/text()", false);
  }
}
