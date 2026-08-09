package org.plumelib.bcelutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/** Tests for {@link SimpleLog}. */
final class SimpleLogTest {

  /** Creates a SimpleLogTest. */
  SimpleLogTest() {}

  /**
   * Returns the contents of the given file.
   *
   * @param file the file to read
   * @return the contents of {@code file}
   * @throws IOException if the file cannot be read
   */
  private static String contents(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8);
  }

  @Test
  void enabledReflectsConstructorArgument() {
    assertTrue(new SimpleLog().enabled());
    assertTrue(new SimpleLog(true).enabled());
    assertFalse(new SimpleLog(false).enabled());
    assertTrue(new SimpleLog((String) null).enabled());
    assertTrue(new SimpleLog(null, true).enabled());
    assertFalse(new SimpleLog(null, false).enabled());
  }

  @Test
  void disabledLogNeverCreatesItsFile(@TempDir Path tempDir) {
    Path logFile = tempDir.resolve("disabled.log");
    SimpleLog log = new SimpleLog(logFile.toString(), false);
    log.log("this should not appear%n");
    log.logStackTrace();
    log.indent();
    log.log("nor this%n");
    assertFalse(Files.exists(logFile), "a disabled SimpleLog must not create its file");
  }

  @Test
  void fileIsCreatedLazily(@TempDir Path tempDir) throws IOException {
    Path logFile = tempDir.resolve("lazy.log");
    SimpleLog log = new SimpleLog(logFile.toString(), true);
    assertFalse(Files.exists(logFile), "the file must not exist before any output is written");
    log.log("hello%n");
    assertTrue(Files.exists(logFile));
    assertEquals(String.format("hello%n"), contents(logFile));
  }

  @Test
  void logInterpolatesFormatArguments(@TempDir Path tempDir) throws IOException {
    Path logFile = tempDir.resolve("format.log");
    SimpleLog log = new SimpleLog(logFile.toString(), true);
    log.log("%s = %d%n", "count", 17);
    assertEquals(String.format("count = 17%n"), contents(logFile));
  }

  @Test
  void indentAndExdentChangeThePrefix(@TempDir Path tempDir) throws IOException {
    Path logFile = tempDir.resolve("indent.log");
    SimpleLog log = new SimpleLog(logFile.toString(), true);
    log.log("zero%n");
    log.indent();
    log.log("one%n");
    log.indent();
    log.log("two%n");
    log.exdent();
    log.log("one again%n");
    log.resetIndent();
    log.log("zero again%n");
    assertEquals(
        String.format("zero%n  one%n    two%n  one again%nzero again%n"), contents(logFile));
  }

  @Test
  void indentationAppliesOnlyAtTheStartOfAMessage(@TempDir Path tempDir) throws IOException {
    Path logFile = tempDir.resolve("multiline.log");
    SimpleLog log = new SimpleLog(logFile.toString(), true);
    log.indent();
    log.log("first%nsecond%n");
    assertEquals(String.format("  first%nsecond%n"), contents(logFile));
  }

  @Test
  void exdentBelowZeroWarnsInsteadOfUnderflowing(@TempDir Path tempDir) throws IOException {
    Path logFile = tempDir.resolve("exdent.log");
    SimpleLog log = new SimpleLog(logFile.toString(), true);
    log.exdent();
    String logged = contents(logFile);
    assertTrue(
        logged.startsWith("Called exdent when indentation level was 0."),
        "unexpected log contents: " + logged);
    assertTrue(
        logged.contains(
            SimpleLogTest.class.getName() + ".exdentBelowZeroWarnsInsteadOfUnderflowing"),
        "exdent should log a stack trace naming the caller: " + logged);
    assertFalse(
        logged.contains(SimpleLog.class.getName() + ".exdent"),
        "exdent itself should not appear in the trace: " + logged);

    // The indentation level must still be 0, not -1.
    log.log("after%n");
    assertTrue(logged.length() < contents(logFile).length());
    assertTrue(contents(logFile).endsWith(String.format("after%n")));
  }

  @Test
  void indentIsIgnoredWhileDisabled(@TempDir Path tempDir) throws IOException {
    Path logFile = tempDir.resolve("disabled-indent.log");
    SimpleLog log = new SimpleLog(logFile.toString(), false);
    log.indent();
    log.indent();
    log.enabled = true;
    log.log("unindented%n");
    assertEquals(String.format("unindented%n"), contents(logFile));
  }

  @Test
  void logStackTraceWritesFrames(@TempDir Path tempDir) throws IOException {
    Path logFile = tempDir.resolve("stacktrace.log");
    SimpleLog log = new SimpleLog(logFile.toString(), true);
    callLogStackTrace(log);
    String logged = contents(logFile);

    // The trace starts with the method that called logStackTrace, and continues outward.
    assertTrue(
        logged.contains(SimpleLogTest.class.getName() + ".callLogStackTrace"),
        "the immediate caller should appear: " + logged);
    assertTrue(
        logged.contains(SimpleLogTest.class.getName() + ".logStackTraceWritesFrames"),
        "the caller's caller should appear: " + logged);
    assertFalse(
        logged.contains(SimpleLog.class.getName() + ".logStackTrace"),
        "logStackTrace itself should not appear: " + logged);
    assertTrue(
        logged.indexOf(SimpleLogTest.class.getName() + ".callLogStackTrace")
            < logged.indexOf(SimpleLogTest.class.getName() + ".logStackTraceWritesFrames"),
        "the trace should run from innermost to outermost: " + logged);
  }

  /**
   * Calls {@link SimpleLog#logStackTrace} so that the test has a known frame between itself and
   * logStackTrace.
   *
   * @param log the log to write to
   */
  private static void callLogStackTrace(SimpleLog log) {
    log.logStackTrace();
  }

  @Test
  @ResourceLock(Resources.SYSTEM_OUT)
  void dashFilenameMeansStandardOutput() {
    PrintStream savedOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    // SimpleLog reads System.out lazily, on the first output, so replacing it here suffices.
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      SimpleLog log = new SimpleLog("-");
      log.log("to stdout%n");
    } finally {
      System.setOut(savedOut);
    }
    assertEquals(String.format("to stdout%n"), captured.toString(StandardCharsets.UTF_8));
  }

  @Test
  @ResourceLock(Resources.SYSTEM_OUT)
  void nullFilenameMeansStandardOutput() {
    PrintStream savedOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      SimpleLog log = new SimpleLog((String) null);
      log.log("also to stdout%n");
    } finally {
      System.setOut(savedOut);
    }
    assertEquals(String.format("also to stdout%n"), captured.toString(StandardCharsets.UTF_8));
  }

  @Test
  void outputAccumulatesAcrossCalls(@TempDir Path tempDir) throws IOException {
    Path logFile = tempDir.resolve("accumulate.log");
    SimpleLog log = new SimpleLog(logFile.toString(), true);
    for (int i = 0; i < 3; i++) {
      log.log("line %d%n", i);
    }
    assertEquals(String.format("line 0%nline 1%nline 2%n"), contents(logFile));
  }
}
