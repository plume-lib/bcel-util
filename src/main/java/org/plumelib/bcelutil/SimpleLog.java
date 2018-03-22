package org.plumelib.bcelutil;

/*>>>
import org.checkerframework.checker.formatter.qual.*;
import org.checkerframework.checker.initialization.qual.*;
import org.checkerframework.checker.nullness.qual.*;
*/

/**
 * A logging class with the following features:
 *
 * <ul>
 *   <li>Can be enabled and disabled (when disabled, all operations are no-ops),
 *   <li>Can indent/exdent log output,
 *   <li>Writes to standard output, and
 *   <li>Can provide a backtrace
 * </ul>
 */
public final class SimpleLog {

  /** If false, do no output. */
  public boolean enabled;

  /** The current indentation string. */
  private String indent_str = "";
  /** Indentation string for one level of indentation. */
  public final String INDENT_STR_ONE_LEVEL = "  ";

  /**
   * Create a new SimpleLog object.
   *
   * @param enabled whether the logger starts out enabled
   */
  public SimpleLog(boolean enabled) {
    this.enabled = enabled;
  }

  public SimpleLog() {
    this(true);
  }

  public boolean enabled() {
    return enabled;
  }

  /**
   * Log a message.
   *
   * @param format format string for message
   * @param args values to be substituted into format
   */
  /*@FormatMethod*/
  public void log(String format, /*@Nullable*/ Object... args) {
    if (enabled) {
      System.out.print(indent_str);
      System.out.printf(format, args);
    }
  }

  /** Print a backtrace (traceback, or tb) to the log. */
  public void tb() {
    if (enabled) {
      Throwable t = new Throwable();
      t.fillInStackTrace();
      StackTraceElement[] ste_arr = t.getStackTrace();
      for (int ii = 2; ii < ste_arr.length; ii++) {
        StackTraceElement ste = ste_arr[ii];
        System.out.printf("%s  %s%n", indent_str, ste);
      }
    }
  }

  /** Indents by one level. */
  public void indent() {
    if (enabled) {
      indent_str += INDENT_STR_ONE_LEVEL;
    }
  }

  /** Clears indent. */
  public void clear() {
    if (enabled) {
      indent_str = "";
    }
  }

  /** Exdents: reduces indentation. */
  public void exdent() {
    if (enabled) {
      if (indent_str.isEmpty()) {
        log("Called exdent when indentation was 0.");
        tb();
      } else {
        indent_str = indent_str.substring(0, indent_str.length() - INDENT_STR_ONE_LEVEL.length());
      }
    }
  }
}
