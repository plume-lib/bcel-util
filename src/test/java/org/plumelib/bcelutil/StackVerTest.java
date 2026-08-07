package org.plumelib.bcelutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.plumelib.bcelutil.Fixtures.methodGen;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.verifier.VerificationResult;
import org.apache.bcel.verifier.structurals.OperandStack;
import org.junit.jupiter.api.Test;

/** Tests for {@link StackVer}. */
final class StackVerTest {

  /** Creates a StackVerTest. */
  StackVerTest() {}

  @Test
  void everyFixtureMethodVerifies() {
    for (Method m : Fixtures.javaClass.getMethods()) {
      MethodGen mg;
      try {
        mg = methodGen(m.getName());
      } catch (AssertionError e) {
        // This happens if there is an overload, such as two contructors both named "<init>".
        continue;
      }
      StackVer sv = new StackVer();
      VerificationResult vr = sv.do_stack_ver(mg);
      assertEquals(
          VerificationResult.VERIFIED_OK,
          vr.getStatus(),
          () -> "verification of " + m.getName() + " failed: " + vr.getMessage());
      assertTrue(
          sv.get_stackTypes().toString().startsWith("Instruction 0:"),
          () -> "no stack types recorded for " + m.getName());
    }
  }

  @Test
  void aLoopIsVerifiedAndItsStackRecorded() {
    MethodGen mg = methodGen("sum");
    StackVer sv = new StackVer();
    assertEquals(VerificationResult.VERIFIED_OK, sv.do_stack_ver(mg).getStatus());

    // The verifier records the stack at every reachable instruction.  "sum" ends with ireturn,
    // whose stack holds the single int being returned.
    String printed = sv.get_stackTypes().toString();
    assertTrue(printed.contains("stack:  {int}"), printed);
  }

  @Test
  void anExceptionHandlerStartsWithTheThrowableOnTheStack() {
    MethodGen mg = methodGen("parseOrDefault");
    StackVer sv = new StackVer();
    assertEquals(VerificationResult.VERIFIED_OK, sv.do_stack_ver(mg).getStatus());

    int handlerOffset = mg.getExceptionHandlers()[0].getHandlerPC().getPosition();
    OperandStack atHandler = sv.get_stackTypes().get(handlerOffset);
    assertEquals(
        1, atHandler.size(), "an exception handler starts with the exception on the stack");
    assertEquals("java.lang.NumberFormatException", atHandler.peek().toString());
  }

  @Test
  void getStackTypesReflectsTheMostRecentVerification() {
    StackVer sv = new StackVer();

    sv.do_stack_ver(methodGen("getValue"));
    String forGetValue = sv.get_stackTypes().toString();

    sv.do_stack_ver(methodGen("sum"));
    String forSum = sv.get_stackTypes().toString();

    assertTrue(
        forGetValue.length() < forSum.length(),
        "sum has more instructions than getValue: " + forGetValue + " / " + forSum);
  }

  @Test
  void stackTypesRecordTheParameterTypes() {
    MethodGen mg = methodGen("parseOrDefault");
    StackVer sv = new StackVer();
    sv.do_stack_ver(mg);

    // parseOrDefault is static and takes (String, int), so those are locals 0 and 1 on entry.
    String printed = sv.get_stackTypes().toString();
    assertTrue(printed.contains("locals: {java.lang.String, int"), printed);
  }
}
