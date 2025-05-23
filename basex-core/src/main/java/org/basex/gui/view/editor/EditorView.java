package org.basex.gui.view.editor;

import static org.basex.core.Text.*;
import static org.basex.gui.GUIConstants.*;
import static org.basex.util.Token.*;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

import javax.swing.*;

import org.basex.build.json.*;
import org.basex.core.*;
import org.basex.core.cmd.*;
import org.basex.core.parse.*;
import org.basex.data.*;
import org.basex.gui.*;
import org.basex.gui.dialog.*;
import org.basex.gui.layout.*;
import org.basex.gui.layout.BaseXFileChooser.*;
import org.basex.gui.listener.*;
import org.basex.gui.text.*;
import org.basex.gui.text.TextPanel.Action;
import org.basex.gui.view.*;
import org.basex.gui.view.project.*;
import org.basex.io.*;
import org.basex.io.in.*;
import org.basex.io.parse.json.*;
import org.basex.io.parse.xml.*;
import org.basex.query.*;
import org.basex.query.func.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.util.*;
import org.basex.util.list.*;
import org.xml.sax.*;

/**
 * This view allows the input and evaluation of queries and documents.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class EditorView extends View {
  /** Delay for showing the "please wait" info. */
  private static final int WAIT_DELAY = 250;
  /** Delay for highlighting an error. */
  private static final int SEARCH_DELAY = 100;
  /** Link pattern. */
  private static final Pattern LINK = Pattern.compile("(.*?), (\\d+)/(\\d+)");

  /** Project files. */
  final ProjectView project;
  /** Test button. */
  final AbstractButton test;

  /** History Button. */
  private final AbstractButton history;
  /** Stop Button. */
  private final AbstractButton stop;
  /** Search bar. */
  private final SearchBar search;
  /** Info label. */
  private final BaseXLabel info;
  /** Position label. */
  private final BaseXLabel pos;
  /** Splitter. */
  private final BaseXSplit split;
  /** Tabs. */
  private final BaseXTabs tabs;
  /** Context. */
  private final BaseXLabel context;

  /** Query file that has last been evaluated. */
  private IOFile execFile;
  /** Main-memory document. */
  private DBNode doc;

  /** Parse counter. */
  private final AtomicInteger parseID = new AtomicInteger();
  /** Parse query context. */
  private final AtomicBoolean parsing = new AtomicBoolean();
  /** Current input info (can be {@code null}). */
  private InputInfo inputInfo;

  /**
   * Default constructor.
   * @param notifier view notifier
   */
  public EditorView(final ViewNotifier notifier) {
    super(EDITORVIEW, notifier);
    layout(new BorderLayout());
    setBackground(PANEL);

    tabs = new BaseXTabs(gui);
    tabs.setFocusable(Prop.MAC);
    tabs.addDragDrop();
    tabs.setTabLayoutPolicy(gui.gopts.get(GUIOptions.SCROLLTABS) ? JTabbedPane.SCROLL_TAB_LAYOUT :
      JTabbedPane.WRAP_TAB_LAYOUT);
    tabs.addMouseListener((MouseClickedListener) e -> {
      final int i = tabs.indexAtLocation(e.getX(), e.getY());
      if(i != -1 && SwingUtilities.isMiddleMouseButton(e) &&
          tabs.getComponentAt(i) instanceof final EditorArea edit) close(edit);
    });

    final SearchEditor center = new SearchEditor(gui, tabs, null);
    search = center.bar();

    final AbstractButton newB = BaseXButton.command(GUIMenuCmd.C_EDIT_NEW, gui);
    final AbstractButton openB = BaseXButton.command(GUIMenuCmd.C_EDIT_OPEN, gui);
    final AbstractButton saveB = BaseXButton.get("c_save", SAVE, false, gui);
    final AbstractButton find = search.button(FIND_REPLACE);
    final AbstractButton vars = BaseXButton.command(GUIMenuCmd.C_EXTERNAL_VARIABLES, gui);
    final AbstractButton go = BaseXButton.command(GUIMenuCmd.C_GO, gui);

    history = BaseXButton.get("c_history", BaseXLayout.addShortcut(RECENTLY_OPENED,
        BaseXKeys.HISTORY.toString()), false, gui);
    stop = BaseXButton.command(GUIMenuCmd.C_STOP, gui);
    stop.setEnabled(false);
    test = BaseXButton.get("c_test", BaseXLayout.addShortcut(RUN_TESTS,
        BaseXKeys.TESTS.toString()), false, gui);

    final BaseXToolBar buttons = new BaseXToolBar();
    buttons.add(newB);
    buttons.add(openB);
    buttons.add(saveB);
    buttons.add(history);
    buttons.addSeparator();
    buttons.add(go);
    buttons.add(stop);
    buttons.add(vars);
    buttons.add(test);
    buttons.addSeparator();
    buttons.add(find);

    context = new BaseXLabel("").resize(1.25f);
    context.setForeground(dgray);

    final BaseXBack north = new BaseXBack(false).layout(new BorderLayout(10, 0)).border(0, 0, 4, 0);
    north.add(buttons, BorderLayout.WEST);
    north.add(context, BorderLayout.CENTER);
    north.add(new BaseXHeader(EDITOR), BorderLayout.EAST);

    // status and query pane
    search.editor(addTab(), false);

    info = new BaseXLabel().setText(OK, Msg.SUCCESS).resize(1.25f);
    pos = new BaseXLabel(" ").resize(1.25f);
    posCode.invokeLater();

    final BaseXBack south = new BaseXBack(false).border(8, 0, 0, 0);
    south.layout(new BorderLayout(4, 0));
    south.add(info, BorderLayout.CENTER);
    south.add(pos, BorderLayout.EAST);

    final BaseXBack main = new BaseXBack().border(5);
    main.setOpaque(false);
    main.layout(new BorderLayout());
    main.add(north, BorderLayout.NORTH);
    main.add(center, BorderLayout.CENTER);
    main.add(south, BorderLayout.SOUTH);

    project = new ProjectView(this);
    split = new BaseXSplit(true);
    split.setOpaque(false);
    split.add(project);
    split.add(main);
    split.init(new double[] { 0.3, 0.7 }, new double[] { 0, 1 });
    toggleProject();
    add(split, BorderLayout.CENTER);

    refreshLayout();

    // add listeners
    saveB.addActionListener(e -> {
      final JPopupMenu pop = new JPopupMenu();
      final StringBuilder mnem = new StringBuilder();
      final JMenuItem sa = GUIMenu.newItem(GUIMenuCmd.C_EDIT_SAVE, gui, mnem);
      final JMenuItem sas = GUIMenu.newItem(GUIMenuCmd.C_EDIT_SAVE_AS, gui, mnem);
      sa.setEnabled(GUIMenuCmd.C_EDIT_SAVE.enabled(gui));
      sas.setEnabled(GUIMenuCmd.C_EDIT_SAVE_AS.enabled(gui));
      pop.add(sa);
      pop.add(sas);
      pop.show(saveB, 0, saveB.getHeight());
    });

    history.addActionListener(e -> historyPopup(0));
    refreshHistory(null);

    info.addMouseListener((MouseClickedListener) e -> markError(true));
    test.addActionListener(e -> run(getEditor(), Action.TEST));
    tabs.addChangeListener(e -> {
      final EditorArea ea = getEditor();
      if(ea == null) return;
      search.editor(ea, true);
      gui.refreshControls(false);
      posCode.invokeLater();
      refreshMark();
      run(ea, Action.PARSE);
      gui.setTitle();
    });

    BaseXLayout.addDrop(this, obj -> {
      if(obj instanceof final File file) open(new IOFile(file));
    });
  }

  @Override
  public void refreshInit() { }

  @Override
  public void refreshFocus() { }

  @Override
  public void refreshMark() {
    final EditorArea edit = getEditor();
    test.setEnabled(edit.file().hasSuffix(IO.XQSUFFIXES) && !edit.modified());
  }

  @Override
  public void refreshContext(final boolean more, final boolean quick) { }

  @Override
  public void refreshLayout() {
    for(final EditorArea edit : editors()) edit.refreshLayout(mfont);
    project.refreshLayout();
    search.refreshLayout();
  }

  @Override
  public void refreshUpdate() { }

  @Override
  public boolean visible() {
    return gui.gopts.get(GUIOptions.SHOWEDITOR);
  }

  @Override
  public void visible(final boolean v) {
    gui.gopts.set(GUIOptions.SHOWEDITOR, v);
  }

  @Override
  protected boolean db() {
    return false;
  }

  /**
   * Shows a history popup menu.
   * @param start first entry
   */
  public void historyPopup(final int start) {
    // create list of paths; show opened files first
    final HashSet<String> opened = new HashSet<>();
    for(final EditorArea edit : editors()) opened.add(edit.file().path());
    final List<String> paths = new ArrayList<>(opened);
    for(final String path : gui.gopts.get(GUIOptions.EDITOR)) {
      if(!paths.contains(path)) paths.add(path);
    }
    paths.sort((path1, path2) -> {
      final boolean c1 = opened.contains(path1), c2 = opened.contains(path2);
      return c1 == c2 ? path1.compareTo(path2) : c1 ? -1 : 1;
    });

    final JPopupMenu menu = new JPopupMenu();
    int p = start - 1;
    final int max = Math.min(paths.size(), start + BaseXHistory.MAXPAGE);
    if(start > 0) menu.add(new JMenuItem(DOTS)).addActionListener(
        ac -> historyPopup(start - BaseXHistory.MAXPAGE));
    while(++p < max) {
      final String path = paths.get(p);
      final IOFile file = new IOFile(path);
      final String label = file.name() + " \u00b7 " + BaseXLayout.reversePath(file);
      final JMenuItem item = new JMenuItem(label);
      item.setToolTipText(BaseXLayout.info(file, true));
      if(opened.contains(path)) BaseXLayout.boldFont(item);
      menu.add(item).addActionListener(ac -> open(file));
    }
    if(p < paths.size()) menu.add(new JMenuItem(DOTS)).addActionListener(
        ac -> historyPopup(start + BaseXHistory.MAXPAGE));
    menu.show(history, 0, history.getHeight());
  }

  /**
   * Refreshes the context label.
   */
  public void refreshContextLabel() {
    String label = context();
    label = label.isEmpty() ? "" : CONTEXT + COLS + label;
    if(!context.getText().equals(label)) context.setText(label);
  }

  /**
   * Sets an XML document as context.
   * @param file file
   */
  public void setContext(final IOFile file) {
    try {
      // close database
      if(Close.close(gui.context)) gui.notify.init();
      doc = new DBNode(file);
      // remove context value binding
      final Map<String, String> map = gui.context.options.toMap(MainOptions.BINDINGS);
      map.remove("");
      DialogBindings.assign(map, gui);
      refreshContextLabel();
    } catch(final IOException ex) {
      Util.debug(ex);
      BaseXDialog.error(gui, Util.info(ex));
    }
  }

  /**
   * Returns a string describing the current context.
   * @return context string (can be empty)
   */
  public String context() {
    // check if context binding was set
    String value = gui.context.options.toMap(MainOptions.BINDINGS).get("");
    if(value != null) {
      value = Strings.concat("xs:untypedAtomic(", Atm.get(value), ')');
    }
    // check if database is opened
    if(value == null) {
      final Data data = gui.context.data();
      if(data != null) value = Function._DB_GET.args(data.meta.name);
    }
    // check if main-memory document exists
    if(value == null) {
      if(doc != null) value = Function.DOC.args(new IOFile(doc.data().meta.original).name());
    } else {
      doc = null;
    }
    return value != null ? value.trim() : "";
  }

  /**
   * Shows the project view.
   */
  public void showProject() {
    if(!gui.gopts.get(GUIOptions.SHOWPROJECT)) {
      gui.gopts.invert(GUIOptions.SHOWPROJECT);
      split.visible(true);
    }
  }

  /**
   * Toggles the project view.
   */
  public void toggleProject() {
    final boolean show = gui.gopts.get(GUIOptions.SHOWPROJECT);
    split.visible(show);
    if(show) {
      project.focus();
    } else {
      focusEditor();
    }
  }

  /**
   * Focuses the project view.
   */
  public void findFiles() {
    project.findFiles(getEditor());
  }

  /**
   * Focuses the current editor.
   */
  public void focusEditor() {
    SwingUtilities.invokeLater(() -> getEditor().requestFocusInWindow());
  }

  /**
   * Focuses the currently edited file in the project view.
   */
  public void jumpToFile() {
    project.jumpTo(getEditor().file(), true);
  }

  /**
   * Switches the current editor tab.
   * @param next next/previous tab
   */
  public void tab(final boolean next) {
    final int s = tabs.getTabCount();
    final int i = (s + tabs.getSelectedIndex() + (next ? 1 : -1)) % s;
    tabs.setSelectedIndex(i);
  }

  /**
   * Opens previously opened and new files.
   * @param files files to be opened
   */
  public void init(final ArrayList<IOFile> files) {
    for(final String file : gui.gopts.get(GUIOptions.OPEN)) open(new IOFile(file), true, false);
    for(final IOFile file : files) open(file, true, false);

    // open temporary files
    final EditorArea edit = getEditor();
    final String prefix = Prop.HOMEDIR.hashCode() + "-";
    for(final IOFile file : new IOFile(Prop.TEMPDIR, Prop.PROJECT).children()) {
      if(!file.name().startsWith(prefix)) continue;
      try {
        final byte[] text = read(file);
        if(text != null) {
          final EditorArea ea = addTab();
          ea.setText(text);
          refreshControls(ea, true);
          file.delete();
        }
      } catch(final IOException ex) {
        Util.debug(ex);
      }
    }
    if(!edit.opened()) closeEditor(edit);

    gui.setTitle();
  }

  /**
   * Opens a new file.
   */
  public void open() {
    // open file chooser for XML creation
    final BaseXFileChooser fc = new BaseXFileChooser(gui, OPEN, gui.gopts.get(GUIOptions.WORKPATH));
    fc.filter(XQUERY_FILES, false, IO.XQSUFFIXES);
    fc.filter(BXS_FILES, false, IO.BXSSUFFIX);
    fc.textFilters();
    for(final IOFile f : fc.multi().selectAll(Mode.FOPEN)) open(f);
  }

  /**
   * Saves the contents of the currently opened editor.
   * @return {@code false} if operation was canceled
   */
  public boolean save() {
    final EditorArea edit = getEditor();
    return edit.opened() ? edit.save() : saveAs();
  }

  /**
   * Saves the contents of the currently opened editor under a new name.
   * @return {@code false} if operation was canceled
   */
  public boolean saveAs() {
    // open file chooser for XML creation
    final EditorArea edit = getEditor();
    final String path = edit.opened() ? edit.file().path() : gui.gopts.get(GUIOptions.WORKPATH);
    final BaseXFileChooser fc = new BaseXFileChooser(gui, SAVE_AS, path);
    fc.filter(XQUERY_FILES, false, IO.XQSUFFIXES);
    fc.filter(BXS_FILES, false, IO.BXSSUFFIX);
    fc.textFilters();
    fc.suffix(IO.XQSUFFIX);

    // save new file
    final IOFile file = fc.select(Mode.FSAVE);
    if(file == null || !edit.save(file)) return false;
    // success: parse contents
    run(edit, Action.PARSE);
    return true;
  }

  /**
   * Creates a new file.
   */
  public void newFile() {
    if(!visible()) GUIMenuCmd.C_SHOW_EDITOR.execute(gui);
    refreshControls(addTab(), true);
  }

  /**
   * Deletes a file.
   * @param file file to be deleted
   * @return success flag
   */
  public boolean delete(final IOFile file) {
    final EditorArea edit = find(file);
    if(edit != null) close(edit);
    return file.delete();
  }

  /**
   * Opens and parses the specified query file.
   * @param file query file
   * @return opened editor or {@code null} if file could not be opened
   */
  public EditorArea open(final IOFile file) {
    return open(file, true, true);
  }

  /**
   * Opens and focuses the specified query file.
   * @param file query file
   * @param parse parse contents
   * @param error display error if file does not exist
   * @return opened editor, or {@code null} if file could not be opened
   */
  private EditorArea open(final IOFile file, final boolean parse, final boolean error) {
    if(!visible()) GUIMenuCmd.C_SHOW_EDITOR.execute(gui);

    EditorArea edit = find(file);
    if(edit != null) {
      // display open file
      tabs.setSelectedComponent(edit);
    } else {
      try {
        // check and retrieve content
        final byte[] text = read(file);
        if(text == null) return null;

        // get current editor
        edit = getEditor();
        // create new tab if text in current tab is stored on disk or has been modified
        if(edit.opened() || edit.modified()) edit = addTab();
        edit.initText(text);
        edit.file(file, error);
        if(parse) run(edit, Action.PARSE);
      } catch(final IOException ex) {
        refreshHistory(null);
        Util.debug(ex);
        if(error) BaseXDialog.error(gui, Util.info(FILE_NOT_OPENED_X, file));
        return null;
      }
    }
    focusEditor();
    return edit;
  }

  /**
   * Parses or evaluates the current file.
   */
  public void run() {
    run(getEditor(), Action.EXECUTE);
  }

  /**
   * Parses or evaluates the query in the specified editor.
   * @param editor editor
   * @param action action
   */
  void run(final EditorArea editor, final Action action) {
    refreshControls(editor, false);

    // skip checks if input has not changed
    final byte[] text = editor.getText();
    if(eq(text, editor.last) && action == Action.CHECK) return;
    editor.last = text;

    // save modified files before executing queries
    if(gui.gopts.get(GUIOptions.SAVERUN) && (action == Action.EXECUTE || action == Action.TEST)) {
      for(final EditorArea edit : editors()) {
        if(edit.opened()) edit.save();
      }
    }

    IOFile file = editor.file();
    final boolean xquery = file.hasSuffix(IO.XQSUFFIXES) || !file.name().contains(".");
    final boolean script = file.hasSuffix(IO.BXSSUFFIX);

    if(action == Action.TEST) {
      // test query
      if(xquery) gui.execute(true, new Test(file.path()));
    } else if(action == Action.EXECUTE && script) {
      // execute script
      gui.execute(true, new Execute(string(text)).baseURI(file.path()));
    } else if(action == Action.EXECUTE || xquery) {
      // execute or parse query
      String input = string(text);
      if(action == Action.EXECUTE || gui.gopts.get(GUIOptions.EXECRT)) {
        // find main module if current file cannot be evaluated; return early if no module is found
        if(!xquery || QueryParser.isLibrary(input)) {
          final EditorArea ea = execEditor();
          if(ea == null) return;
          file = ea.file();
          input = string(ea.getText());
        }
        // execute query
        final XQuery cmd = new XQuery(input);
        if(doc != null) cmd.bind(null, doc);
        gui.execute(true, cmd.baseURI(file.path()));
        execFile = file;
      } else {
        // parse: replace empty query with empty sequence (suppresses errors for plain text files)
        parse(input.isEmpty() ? "()" : input, file);
      }
    } else if(file.hasSuffix(IO.JSONSUFFIX)) {
      try {
        final IOContent io = new IOContent(text);
        io.name(file.path());
        JsonConverter.get(new JsonParserOptions()).convert(io);
        info(null);
      } catch(final QueryException | IOException ex) {
        info(ex);
      }
    } else if(script || file.hasSuffix(gui.gopts.xmlSuffixes()) || file.hasSuffix(IO.XSLSUFFIXES)) {
      final ArrayInput ai = new ArrayInput(text);
      try {
        // check XML syntax
        if(startsWith(text, '<') || !script) new XmlParser().parse(ai);
        // check command script
        if(script) CommandParser.get(string(text), gui.context).parse();
        info(null);
      } catch(final Exception ex) {
        info(ex);
      }
    } else if(action != Action.CHECK) {
      info(null);
    } else {
      // no particular file format, no particular action: reset status info
      info.setText(OK, Msg.SUCCESS);
    }
  }

  /**
   * Evaluates the info message resulting from command or query parsing.
   * @param ex exception or {@code null}
   */
  private void info(final Exception ex) {
    info(ex, false, false);
  }

  /**
   * Returns the editor whose contents have been executed last.
   * @return editor or {@code null}
   */
  private EditorArea execEditor() {
    final IOFile file = execFile;
    if(file != null) {
      for(final EditorArea edit : editors()) {
        if(edit.file().path().equals(file.path())) return edit;
      }
      execFile = null;
    }
    return null;
  }

  /**
   * Retrieves the contents of the specified file, or opens it externally.
   * @param file query file
   * @return contents, or {@code null} reference if file is opened externally
   * @throws IOException I/O exception
   */
  private byte[] read(final IOFile file) throws IOException {
    try {
      // try to open as validated UTF-8 document
      return new NewlineInput(file).validate(true).content();
    } catch(final InputException ex) {
      // error...
      Util.debug(ex);
      final String button = BaseXDialog.yesNoCancel(gui, H_FILE_BINARY);
      // open binary as text; do not validate
      if(button == B_NO) return new NewlineInput(file).content();
      // open external application
      if(button == B_YES) {
        try {
          file.open();
        } catch(final IOException ioex) {
          Util.debug(ioex);
          Desktop.getDesktop().open(file.file());
        }
      }
      // return nothing (also applies if dialog is canceled)
      return null;
    }
  }

  /**
   * Refreshes the list of recent query files and updates the query path.
   * @param file new file (can be {@code null})
   */
  void refreshHistory(final IOFile file) {
    final StringList paths = new StringList();
    if(file != null) {
      gui.gopts.setFile(GUIOptions.WORKPATH, file.parent());
      paths.add(file.path());
      tabs.setToolTipTextAt(tabs.getSelectedIndex(), BaseXLayout.info(file, true));
    }
    for(final String old : gui.gopts.get(GUIOptions.EDITOR)) {
      if(paths.size() < BaseXHistory.MAX) paths.addUnique(old);
    }

    // store sorted history
    gui.gopts.setFiles(GUIOptions.EDITOR, paths.finish());
    history.setEnabled(!paths.isEmpty());
  }

  /**
   * Closes all editors.
   */
  public void closeAll() {
    for(final EditorArea ea : editors()) closeEditor(ea);
    gui.saveOptions();
  }

  /**
   * Closes an editor.
   * @param edit editor to be closed (if {@code null}, the currently opened editor will be closed)
   */
  public void close(final EditorArea edit) {
    closeEditor(edit);
    gui.saveOptions();
  }

  /**
   * Closes an editor.
   * @param edit editor to be closed (if {@code null}, the currently opened editor will be closed)
   */
  private void closeEditor(final EditorArea edit) {
    final EditorArea ea = edit != null ? edit : getEditor();
    if(ea.modified() && !confirm(ea)) return;

    // remove reference to last executed file
    if(execFile != null && ea.file().path().equals(execFile.path())) execFile = null;
    tabs.remove(ea);
    // no panels left: open default tab
    if(tabs.getTabCount() == 0) {
      addTab();
      SwingUtilities.invokeLater(this::toggleProject);
    } else {
      focusEditor();
    }
  }

  /**
   * Starts a thread, which shows a waiting info after a short timeout.
   * @param id thread ID
   */
  public void pleaseWait(final int id) {
    new Timer(true).schedule(new TimerTask() {
      @Override
      public void run() {
        if(gui.running(id)) {
          info.setText(PLEASE_WAIT_D, Msg.SUCCESS).setToolTipText(null);
          stop.setEnabled(true);
        }
      }
    }, WAIT_DELAY);
  }

  /**
   * Parses the current query after a little delay.
   * @param input query input
   * @param file file
   */
  private void parse(final String input, final IO file) {
    final int id = parseID.incrementAndGet();
    new Timer(true).schedule(new TimerTask() {
      @Override
      public void run() {
        // let current parser finish; check if thread is obsolete
        while(parsing.get()) Performance.sleep(1);
        if(id != parseID.get()) return;

        // parse query
        parsing.set(true);
        try(QueryContext qc = new QueryContext(gui.context)) {
          qc.parse(input, file.path());
          if(id == parseID.get()) info(null);
        } catch(final QueryException ex) {
          if(id == parseID.get()) info(ex);
        } finally {
          parsing.set(false);
        }
      }
    }, SEARCH_DELAY);
  }

  /**
   * Processes the result from a command or query execution.
   * @param th exception or {@code null}
   * @param stopped {@code true} if evaluation was stopped
   * @param refresh refresh buttons
   */
  public void info(final Throwable th, final boolean stopped, final boolean refresh) {
    // do not refresh view when query is running
    if(!refresh && stop.isEnabled()) return;

    parseID.incrementAndGet();
    final EditorArea editor = getEditor();
    String path = "";
    if(editor != null) {
      path = editor.file().path();
      editor.resetError();
    }

    if(refresh) {
      stop.setEnabled(false);
      refreshMark();
    }

    if(stopped || th == null) {
      info.setCursor(CURSORARROW);
      info.setText(stopped ? INTERRUPTED : OK, Msg.SUCCESS);
      info.setToolTipText(null);
      inputInfo = null;
    } else {
      info.setCursor(CURSORHAND);
      final String msg = Util.message(th), local = th.getLocalizedMessage();
      info.setText(local != null ? local : msg, Msg.ERROR);
      final String tt = msg.replace("<", "&lt;").replace(">", "&gt;").
        replaceAll("\r?\n", "<br/>").replaceAll("(<br/>.*?)<br/>.*", "$1");
      info.setToolTipText("<html>" + tt + "</html>");

      if(th instanceof final QueryIOException ex) {
        inputInfo = ex.getCause().info();
      } else if(th instanceof final QueryException ex) {
        inputInfo = ex.info();
      } else if(th instanceof final SAXParseException ex) {
        inputInfo = new InputInfo(path, ex.getLineNumber(), ex.getColumnNumber());
      } else {
        inputInfo = new InputInfo(path, 1, 1);
      }
      markError(false);
    }
  }

  /**
   * Jumps to the specified file and position.
   * @param link link
   */
  public void jump(final String link) {
    final Matcher m = LINK.matcher(link);
    if(m.matches()) {
      inputInfo = new InputInfo(m.group(1), Strings.toInt(m.group(2)), Strings.toInt(m.group(3)));
      markError(true);
    } else {
      Util.stack("No match found: " + link);
    }
  }

  /**
   * Jumps to the current error.
   * @param jump jump to error position (if necessary, open file)
   */
  public void markError(final boolean jump) {
    InputInfo ii = inputInfo;
    final String path;
    final boolean error = ii == null;
    if(error) {
      final TreeMap<String, InputInfo> errors = project.errors();
      if(errors.isEmpty()) return;
      path = errors.get(errors.keySet().iterator().next()).path();
    } else {
      path = ii.path();
    }
    if(path == null) return;

    final IOFile file = new IOFile(path);
    final EditorArea found = find(file), opened;
    if(jump) {
      opened = open(file, error, true);
      // update error information if file was not already opened
      if(found == null && error) ii = inputInfo;
    } else {
      opened = found;
    }
    // no editor available, no input info
    if(opened == null || ii == null) return;

    // mark error, jump to error position
    final int ep = pos(opened.last, ii.line(), ii.column());
    opened.error(ep);

    if(jump) {
      opened.setCaret(ep);
      posCode.invokeLater();
      // jump to file in project view if file was opened by this function call
      if(found == null) project.jumpTo(opened.file(), false);
    }
  }

  /**
   * Returns an editor offset for the specified line and column.
   * @param text text
   * @param line line
   * @param col column
   * @return position
   */
  private static int pos(final byte[] text, final int line, final int col) {
    final int tl = text.length;
    int ep = tl;
    for(int t = 0, l = 1, c = 1; t < tl; ++c, t += cl(text, t)) {
      if(l > line || l == line && c == col) {
        ep = t;
        break;
      }
      if(text[t] == '\n') {
        ++l;
        c = 0;
      }
    }
    if(ep < tl && Character.isLetterOrDigit(cp(text, ep))) {
      while(ep > 0 && Character.isLetterOrDigit(cp(text, ep - 1))) ep--;
    }
    return ep;
  }

  /**
   * Returns paths of all open files.
   * @return file paths
   */
  public String[] openFiles() {
    // remember opened files
    final StringList files = new StringList();
    for(final EditorArea edit : editors()) {
      if(edit.opened()) files.add(edit.file().path());
    }
    return files.finish();
  }

  /**
   * Returns the current editor.
   * @return editor or {@code null}
   */
  public EditorArea getEditor() {
    return tabs.getSelectedComponent() instanceof final EditorArea edit ? edit : null;
  }

  /**
   * Updates the references to renamed files.
   * @param old old file reference
   * @param renamed updated file reference
   */
  public void rename(final IOFile old, final IOFile renamed) {
    try {
      // use canonical representation and add slash to the directory names
      final boolean dir = renamed.isDir();
      final String oldPath = old.file().getCanonicalPath() + (dir ? File.separator : "");
      // iterate through all tabs
      for(final Component c : tabs.getComponents()) {
        if(!(c instanceof final EditorArea ea) || !ea.opened()) continue;

        final String editPath = ea.file().file().getCanonicalPath();
        if(dir) {
          // change path to files in a renamed directory
          if(editPath.startsWith(oldPath)) {
            ea.file(new IOFile(renamed, editPath.substring(oldPath.length())), true);
          }
        } else if(oldPath.equals(editPath)) {
          // update file reference and label of editor tab
          ea.file(renamed, true);
          ea.label.setText(renamed.name());
          break;
        }
      }
    } catch(final IOException ex) {
      Util.errln(ex);
    }
  }

  /**
   * Refreshes the query modification flag.
   * @param edit editor
   * @param enforce enforce action
   */
  void refreshControls(final EditorArea edit, final boolean enforce) {
    // update modification flag
    final boolean modified = edit.hist != null && edit.hist.modified();
    if(modified == edit.modified() && !enforce) return;

    edit.modified(modified);

    // update tab title
    String title = edit.file().name();
    if(modified) title += '*';
    edit.label.setText(title);

    // update components
    gui.refreshControls(false);
    gui.setTitle();
    posCode.invokeLater();
    refreshMark();
  }

  /** Code for setting cursor position. */
  public final GUICode posCode = new GUICode() {
    @Override
    public void execute(final Object arg) {
      final int[] cp = getEditor().caretPos();
      pos.setText(cp[0] + " : " + cp[1]);
    }
  };

  /**
   * Finds the editor that contains the specified file.
   * @param file file to be found
   * @return editor or {@code null}
   */
  private EditorArea find(final IO file) {
    for(final EditorArea edit : editors()) {
      if(edit.file().eq(file)) return edit;
    }
    return null;
  }

  /**
   * Choose a unique tab file.
   * @return io reference
   */
  private IOFile newTabFile() {
    // collect numbers of existing files
    final BoolList bl = new BoolList();
    for(final EditorArea edit : editors()) {
      if(edit.opened()) continue;
      final String n = edit.file().name().substring(FILE.length());
      bl.set(n.isEmpty() ? 1 : Strings.toInt(n), true);
    }
    // find first free file number
    int b = 0;
    final int bs = bl.size();
    while(++b < bs && bl.get(b));
    // create io reference
    return new IOFile(gui.gopts.get(GUIOptions.WORKPATH), FILE + (b == 1 ? "" : b));
  }

  /**
   * Adds a new editor tab.
   * @return editor reference
   */
  private EditorArea addTab() {
    final EditorArea edit = new EditorArea(this, newTabFile());
    edit.setFont(mfont);

    final BaseXBack tab = new BaseXBack(false).layout(new BorderLayout(10, 0));
    tab.add(edit.label, BorderLayout.CENTER);

    final AbstractButton close = tabButton("e_close", "e_close_hover");
    close.addActionListener(e -> close(edit));
    tab.add(close, BorderLayout.EAST);

    tabs.add(edit, tab, tabs.getTabCount());
    return edit;
  }

  /**
   * Adds a new tab button.
   * @param icon name of button icon
   * @param rollover rollover icon
   * @return button
   */
  private AbstractButton tabButton(final String icon, final String rollover) {
    final AbstractButton close = BaseXButton.get(icon, null, false, gui);
    close.setBorder(BaseXLayout.border(2, 0, 2, 0));
    close.setContentAreaFilled(false);
    close.setFocusable(false);
    close.setRolloverIcon(BaseXImages.icon(rollover));
    return close;
  }

  /**
   * Shows a confirmation dialog for the specified editor, or all editors.
   * @param edit editor to be saved, or {@code null} to save all editors
   * @return {@code true} if all editors were confirmed
   */
  public boolean confirm(final EditorArea edit) {
    final boolean all = edit == null;
    final EditorArea[] eas = all ? editors() : new EditorArea[] { edit };
    final String[] buttons = all && eas.length > 1 ? new String[] { CLOSE_ALL } : new String[0];

    for(final EditorArea ea : eas) {
      tabs.setSelectedComponent(ea);
      if(ea.modified() && (ea.opened() || edit != null && trim(ea.getText()).length != 0)) {
        final String msg = Util.info(CLOSE_FILE_X, ea.file().name());
        final String action = BaseXDialog.yesNoCancel(gui, msg, buttons);
        if(action == null || action.equals(B_YES) && !save()) return false;
        else if(action.equals(CLOSE_ALL)) break;
      }
    }

    // close application: remember opened files
    final IOFile tmpDir = new IOFile(Prop.TEMPDIR, Prop.PROJECT);
    if(edit == null && eas.length > 0 && tmpDir.md()) {
      try {
        final String prefix = Prop.HOMEDIR.hashCode() + "-";
        int c = 0;
        for(final EditorArea ea : eas) {
          final byte[] text = ea.getText();
          if(!ea.opened() && text.length > 0) {
            new IOFile(tmpDir, prefix + c++ + IO.TMPSUFFIX).write(text);
          }
        }
      } catch(final IOException ex) {
        Util.debug(ex);
      }
    }
    return true;
  }

  /**
   * Returns all editors.
   * @return editors
   */
  private EditorArea[] editors() {
    final ArrayList<EditorArea> edits = new ArrayList<>();
    for(final Component c : tabs.getComponents()) {
      if(c instanceof final EditorArea edit) edits.add(edit);
    }
    return edits.toArray(EditorArea[]::new);
  }
}
