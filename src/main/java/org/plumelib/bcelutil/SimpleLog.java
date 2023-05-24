package org.plumelib.bcelutil;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.formatter.qual.FormatMethod;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A logging class with the following features.
 *
 * <ul>
 *   <li>Can be enabled and disabled (when disabled, all operations are no-ops),
 *   <li>Can indent/exdent log output,
 *   <li>Writes to a file or to standard output, and
 *   <li>Can provide a stack trace.
 * </ul>
 */
public final class SimpleLog {

  /** If false, do no output. */
  public boolean enabled;

  /** Where to write logging output. Null if nothing has been output yet. */
  private @MonotonicNonNull PrintStream logfile;

  /** The file for logging output. If null, System.out is used. */
  private @Nullable String filename;

  /** The current indentation level. */
  private int indentLevel = 0;

  /** Indentation string for one level of indentation. */
  private static final String INDENT_STR_ONE_LEVEL = "  ";

  /**
   * Cache for the current indentation string, or null if needs to be recomputed. Never access this
   * directly; always call {@link #getIndentString}.
   */
  private @Nullable String indentString = null;

  /** Cache of indentation strings that have been computed so far. */
  private List<String> indentStrings;

  /** Create a new SimpleLog object with logging to standard out enabled. */
  public SimpleLog() {
    this(true);
  }

  /**
   * Create a new SimpleLog object with logging to standard out.
   *
   * @param enabled whether the logger starts out enabled
   */
  public SimpleLog(boolean enabled) {
    this(null, enabled);
  }

  /**
   * Create a new SimpleLog object with logging to a file enabled.
   *
   * @param filename file name, or use "-" or null for System.out
   */
  public SimpleLog(@Nullable String filename) {
    this(filename, true);
  }

  /**
   * Create a new SimpleLog object with logging to a file.
   *
   * @param filename file name, or use "-" or null for System.out
   * @param enabled whether the logger starts out enabled
   */
  public SimpleLog(@Nullable String filename, boolean enabled) {
    this.filename = (filename != null && filename.equals("-")) ? null : filename;
    this.enabled = enabled;
    indentStrings = new ArrayList<String>();
    indentStrings.add("");
  }

  /**
   * Return whether logging is enabled.
   *
   * @return whether logging is enabled
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Set the private field {@code logfile} (if it is not set), based on the private field {@code
   * filename}.
   *
   * <p>This creates the file if it does not exist. This should be called lazily, when output is
   * performed. Otherwise, it would be annoying to create a zero-size logfile if no output is ever
   * written.
   */
  @SuppressWarnings("builder:required.method.not.called") // a leak, but only of a single file
  @EnsuresNonNull("logfile")
  private void setLogfile() {
    if (logfile != null) {
      return;
    }
    if (filename == null) {
      logfile = System.out;
    } else {
      try {
        logfile = new PrintStream(filename);
      } catch (Exception e) {
        throw new RuntimeException("Can't open " + filename, e);
      }
    }
  }

  /**
   * Log a message. The message is prepended with the current indentation string. The indentation is
   * only applied at the start of the message, not for every line break within the message.
   *
   * @param format format string for message
   * @param args values to be substituted into format
   */
  @FormatMethod
  public void log(String format, @Nullable Object... args) {
    if (enabled) {
      setLogfile();
      logfile.print(getIndentString());
      logfile.printf(format, args);
      logfile.flush();
    }
  }

  /** Print a stack trace to the log. */
  public void logStackTrace() {
    if (enabled) {
      setLogfile();
      Throwable t = new Throwable();
      t.fillInStackTrace();
      StackTraceElement[] stackTrace = t.getStackTrace();
      for (int ii = 2; ii < stackTrace.length; ii++) {
        StackTraceElement ste = stackTrace[ii];
        logfile.printf("%s  %s%n", getIndentString(), ste);
      }
      logfile.flush();
    }
  }

  /**
   * Return the current indentation string.
   *
   * @return the current indentation string
   */
  private String getIndentString() {
    assert enabled;
    if (indentString == null) {
      for (int i = indentStrings.size(); i <= indentLevel; i++) {
        indentStrings.add(indentStrings.get(i - 1) + INDENT_STR_ONE_LEVEL);
      }
      indentString = indentStrings.get(indentLevel);
    }
    return indentString;
  }

  /** Increases indentation by one level. */
  public void indent() {
    if (enabled) {
      indentLevel++;
      indentString = null;
    }
  }

  /** Decreases indentation by one level. */
  public void exdent() {
    if (enabled) {
      if (indentLevel == 0) {
        log("Called exdent when indentation level was 0.");
        logStackTrace();
      } else {
        indentLevel--;
        indentString = null;
      }
    }
  }

  /** Resets indentation to none. Has no effect if logging is disabled. */
  public void resetIndent() {
    if (enabled) {
      indentLevel = 0;
      indentString = "";
    }
  }
}
