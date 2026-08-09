package org.plumelib.bcelutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.plumelib.bcelutil.Fixtures.methodGen;
import static org.plumelib.bcelutil.Fixtures.nonNull;

import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.Type;
import org.apache.bcel.verifier.structurals.Frame;
import org.apache.bcel.verifier.structurals.LocalVariables;
import org.apache.bcel.verifier.structurals.OperandStack;
import org.junit.jupiter.api.Test;

/** Tests for {@link StackTypes}. */
final class StackTypesTest {

  /** Creates a StackTypesTest. */
  StackTypesTest() {}

  @Test
  void operandStackIsPrintedTopFirst() {
    StackTypes st = new StackTypes(methodGen("sum"));
    OperandStack os = new OperandStack(4);
    os.push(Type.INT);
    os.push(Type.STRING);
    // peek(0) is the top of the stack, so the most recently pushed type prints first.
    assertEquals("{java.lang.String, int}", st.toString(os));
  }

  @Test
  void anEmptyOperandStackPrintsAsEmptyBraces() {
    StackTypes st = new StackTypes(methodGen("sum"));
    assertEquals("{}", st.toString(new OperandStack(4)));
  }

  @Test
  void localVariablesArePrintedInIndexOrder() {
    StackTypes st = new StackTypes(methodGen("sum"));
    LocalVariables lv = new LocalVariables(3);
    lv.set(0, Type.INT);
    lv.set(1, Type.STRING);
    lv.set(2, Type.DOUBLE);
    assertEquals("{int, java.lang.String, double}", st.toString(lv));
  }

  @Test
  void aNewStackTypesHasNoRecordedTypes() {
    assertEquals("", new StackTypes(methodGen("sum")).toString());
  }

  @Test
  void aMethodWithNoBodyProducesAnEmptyStackTypes() {
    // An abstract method has no InstructionList; StackTypes must still be constructible.
    MethodGen noBody = Fixtures.noBodyMethodGen();
    assertEquals("", new StackTypes(noBody).toString());
  }

  @Test
  void setRecordsTheStackReportedByTheVerifier() {
    MethodGen mg = methodGen("sum");
    StackVer sv = new StackVer();
    sv.do_stack_ver(mg);
    StackTypes st = sv.get_stackTypes();

    // Offset 0 is the first instruction of the method, so nothing is on the stack yet.
    assertEquals(0, st.get(0).size());
  }

  @Test
  void toStringDescribesEachVisitedInstruction() {
    MethodGen mg = methodGen("sum");
    StackVer sv = new StackVer();
    sv.do_stack_ver(mg);
    String printed = sv.get_stackTypes().toString();

    assertTrue(printed.startsWith("Instruction 0:"), printed);
    assertTrue(printed.contains("  stack:  {"), printed);
    assertTrue(printed.contains("  locals: {"), printed);
    // "sum" takes one int parameter, so local 0 holds an int at the start of the method.
    assertTrue(printed.contains("locals: {int"), printed);
  }

  @Test
  void setStoresACopyOfTheFrame() {
    StackTypes st = new StackTypes(methodGen("sum"));
    Frame frame = new Frame(2, 4);
    frame.getStack().push(Type.INT);
    frame.getLocals().set(0, Type.STRING);

    st.set(0, frame);

    // The verifier reuses and mutates its frames, so `set` must record a snapshot.  Mutating the
    // frame after the call must not change what was recorded.
    frame.getStack().push(Type.DOUBLE);
    frame.getLocals().set(0, Type.INT);

    assertEquals("{int}", st.toString(st.get(0)));
    // The locals must be snapshotted too, not aliased.
    assertEquals(Type.STRING, nonNull(st.localVariableses[0], "recorded locals").get(0));
  }
}
