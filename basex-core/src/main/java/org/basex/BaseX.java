package org.basex;

import static org.basex.core.Text.*;

import java.io.*;
import java.util.*;

import org.basex.core.*;
import org.basex.core.cmd.*;
import org.basex.core.cmd.Check;
import org.basex.core.cmd.Set;
import org.basex.core.parse.*;
import org.basex.io.*;
import org.basex.io.out.*;
import org.basex.io.serial.*;
import org.basex.util.*;
import org.basex.util.list.*;
import org.basex.util.options.Options.*;

/**
 * This is the starter class for the stand-alone console mode.
 * It executes all commands locally.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public class BaseX extends CLI {
  /** Default prompt. */
  private static final String PROMPT = "> ";
  /** Commands to be executed. */
  private IntList ops;
  /** Command arguments. */
  private StringList vals;
  /** Console mode. May be set to {@code false} during execution. */
  private volatile boolean console;

  /**
   * Main method, launching the standalone mode.
   * Command-line arguments are listed with the {@code -h} argument.
   * @param args command-line arguments
   */
  public static void main(final String... args) {
    try {
      new BaseX(args);
    } catch(final IOException ex) {
      Util.errln(ex);
      System.exit(1);
    }
  }

  /**
   * Constructor.
   * @param args command-line arguments
   * @throws IOException I/O exception
   */
  public BaseX(final String... args) throws IOException {
    super(new Context(), args);

    // create session to show optional login request
    session();

    console = true;
    try {
      // loop through all commands
      final StringBuilder bind = new StringBuilder();
      SerializerOptions sopts = null;
      boolean v = false, qi = false, qp = false;
      final int os = ops.size();
      for(int o = 0; o < os; o++) {
        final int c = ops.get(o);
        String value = vals.get(o);

        switch(c) {
          case 'b':
            if(!bind.isEmpty()) bind.append(',');
            // commas are escaped by a second comma
            value = bind.append(value.replace(",", ",,")).toString();
            execute(new Set(MainOptions.BINDINGS, value), false);
            break;
          case 'c':
            console = false;
            if(!execute(commands(value))) return;
            break;
          case 'C':
            console = false;
            if(!execute(script(value))) return;
            break;
          case 'i':
            execute(new Set(MainOptions.MAINMEM, true), false);
            execute(new Check(value), verbose);
            execute(new Set(MainOptions.MAINMEM, false), false);
            break;
          case 'I':
            if(!bind.isEmpty()) bind.append(',');
            // commas are escaped by a second comma
            value = bind.append('=').append(value.replace(",", ",,")).toString();
            execute(new Set(MainOptions.BINDINGS, value), false);
            break;
          case 'o':
            if(out != System.out) out.close();
            out = new PrintOutput(new IOFile(value));
            session().setOutputStream(out);
            break;
          case 'O': {
            final String[] kv = value.split("=", 2);
            execute(new Set(kv[0], kv.length > 1 ? kv[1] : ""), false);
            break;
          }
          case 'q':
            console = false;
            execute(new XQuery(value), verbose);
            break;
          case 'Q':
            console = false;
            final IO io = IO.get(value);
            execute(new XQuery(io.readString()).baseURI(io.path()), verbose);
            break;
          case 'r':
            execute(new Set(MainOptions.RUNS, Strings.toInt(value)), false);
            break;
          case 'R':
            execute(new Set(MainOptions.RUNQUERY, null), false);
            break;
          case 's': {
            if(sopts == null) sopts = new SerializerOptions();
            final String[] kv = value.split("=", 2);
            sopts.assign(kv[0], kv.length > 1 ? kv[1] : "");
            execute(new Set(MainOptions.SERIALIZER, sopts), false);
            break;
          }
          case 't':
            console = false;
            execute(new Test(value), verbose);
            break;
          case 'u':
            execute(new Set(MainOptions.WRITEBACK, null), false);
            break;
          case 'v':
            v ^= true;
            break;
          case 'V':
            qi ^= true;
            execute(new Set(MainOptions.QUERYINFO, null), false);
            break;
          case 'w':
            execute(new Set(MainOptions.STRIPWS, null), false);
            break;
          case 'W':
            if(sopts == null) sopts = new SerializerOptions();
            sopts.set(SerializerOptions.INDENT, YesNo.YES);
            execute(new Set(MainOptions.SERIALIZER, sopts), false);
            break;
          case 'x':
            execute(new Set(MainOptions.XMLPLAN, null), false);
            qp ^= true;
            break;
          case 'z':
            execute(new Set(MainOptions.SERIALIZE, null), false);
            break;
        }
        verbose = qi || qp || v;
      }
      if(console) console();
    } finally {
      quit();
    }
  }

  /**
   * Launches the console mode, which reads and executes user input.
   */
  private void console() {
    Util.println(header() + NL + TRY_MORE_X);
    verbose = true;

    // create console reader
    try(ConsoleReader cr = ConsoleReader.get()) {
      // loop until console is set to false (may happen in server mode)
      while(console) {
        // get next line
        final String in = cr.readLine(PROMPT);
        // end of input: break loop
        if(in == null) break;
        // skip empty lines
        if(in.isEmpty()) continue;

        try {
          if(!execute(CommandParser.get(in, context).pwReader(cr))) {
            // show goodbye message if method returns false
            Util.println(BYE[new Random().nextInt(4)]);
            break;
          }
        } catch(final IOException ex) {
          // output error messages
          Util.errln(ex);
        }
      }
    }
  }

  /**
   * Quits the console mode.
   * @throws IOException I/O exception
   */
  private void quit() throws IOException {
    if(out == System.out || out == System.err) out.flush();
    else out.close();
  }

  @Override
  protected final void parseArgs() throws IOException {
    ops = new IntList();
    vals = new StringList();

    final MainParser arg = new MainParser(this);
    while(arg.more()) {
      final char ch;
      String v = null;
      if(arg.dash()) {
        ch = arg.next();
        if(ch == 'd') {
          // activate debug mode
          Prop.debug = true;
        } else if(Strings.contains("t", ch) && local() || Strings.contains("bcCiIoOqQrs", ch)) {
          // options followed by a string
          v = arg.string();
        } else if(Strings.contains("Du", ch) && local() || Strings.contains("RvVwWxXz", ch)) {
          // toggle options
          v = "";
        } else if(!local()) {
          // client options: need to be set before other options
          switch(ch) {
            // set server name
            case 'n': context.soptions.set(StaticOptions.HOST, arg.string()); break;
            // set server port
            case 'p': context.soptions.set(StaticOptions.PORT, arg.number()); break;
            // specify password
            case 'P': context.soptions.set(StaticOptions.PASSWORD, arg.string()); break;
            // specify username
            case 'U': context.soptions.set(StaticOptions.USER, arg.string()); break;
            default: throw arg.usage();
          }
        } else {
          throw arg.usage();
        }
      } else {
        v = arg.string().trim();
        ch = isFile(v) ? v.endsWith(IO.BXSSUFFIX) ? 'C' : 'Q' : 'q';
      }
      if(v != null) {
        ops.add(ch);
        vals.add(v);
      }
    }
  }

  @Override
  public String header() {
    return Util.info(S_CONSOLE_X, local() ? S_STANDALONE : S_CLIENT);
  }

  @Override
  public String usage() {
    return local() ? S_LOCALINFO : S_CLIENTINFO;
  }
}
