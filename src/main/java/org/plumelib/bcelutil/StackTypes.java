package org.plumelib.bcelutil;

import java.util.StringJoiner;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.Type;
import org.apache.bcel.verifier.structurals.Frame;
import org.apache.bcel.verifier.structurals.LocalVariables;
import org.apache.bcel.verifier.structurals.OperandStack;
import org.apache.bcel.verifier.structurals.UninitializedObjectType;
import org.checkerframework.checker.index.qual.IndexFor;
import org.checkerframework.checker.index.qual.SameLen;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.dataflow.qual.SideEffectFree;

/**
 * Stores the types on the stack at each instruction (identified by byte code offset) in a method.
 */
public final class StackTypes {

  /**
   * The state of the operand stack at each instruction location. The instruction's byte code offset
   * is used as the index.
   */
  OperandStack @SameLen("localVariableses") [] operandStacks;

  /**
   * The state of the live local variables at each instruction location. The instruction's byte code
   * offset is used as the index.
   */
  LocalVariables @SameLen("operandStacks") [] localVariableses;

  /**
   * Create a record of the types on the stack at each instruction in a method. The created object
   * starts out empty, with no type information.
   *
   * @param mg the method
   */
  public StackTypes(MethodGen mg) {
    InstructionList il = mg.getInstructionList();
    int size = (il == null) ? 0 : il.getEnd().getPosition();
    operandStacks = new OperandStack[size + 1];
    localVariableses = new LocalVariables[size + 1];
  }

  /**
   * Sets the stack for the instruction at the specified offset to a copy of the information in the
   * given frame.
   *
   * @param offset the offset at which the instruction appears
   * @param f the stack frame to use for the instruction
   */
  public void set(@IndexFor({"localVariableses", "operandStacks"}) int offset, Frame f) {

    OperandStack os = f.getStack();
    // logger.info ("stack[" + offset + "] = " + toString(os));

    localVariableses[offset] = (LocalVariables) f.getLocals().clone();
    operandStacks[offset] = (OperandStack) os.clone();
  }

  /**
   * Returns the stack contents at the specified offset.
   *
   * @param offset the offset to which to get the stack contents
   * @return the stack at the (instruction at the) given offset
   */
  public OperandStack get(@IndexFor({"localVariableses", "operandStacks"}) int offset) {
    return operandStacks[offset];
  }

  @SuppressWarnings({"allcheckers:purity", "lock"}) // local StringBuilder
  @SideEffectFree
  @Override
  public String toString(@GuardSatisfied StackTypes this) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < operandStacks.length; i++) {
      if (operandStacks[i] != null) {
        sb.append(String.format("Instruction %d:\n", i));
        sb.append(String.format("  stack:  %s\n", toString(operandStacks[i])));
        sb.append(String.format("  locals: %s\n", toString(localVariableses[i])));
      }
    }
    return sb.toString();
  }

  /**
   * Returns a printed representation of the given OperandStack.
   *
   * @param os the OperandStack to print
   * @return a printed representation of {@code os}
   */
  @SuppressWarnings({
    "allcheckers:purity.not.sideeffectfree.call",
    "lock:method.guarantee.violated"
  }) // side effect to local state
  @SideEffectFree
  public String toString(@GuardSatisfied StackTypes this, OperandStack os) {
    StringJoiner sj = new StringJoiner(", ", "{", "}");
    for (int i = 0; i < os.size(); i++) {
      Type t = os.peek(i);
      sj.add(t instanceof UninitializedObjectType ? "uninitialized-object" : t.toString());
    }
    return sj.toString();
  }

  /**
   * Returns a printed representation of the given LocalVariables.
   *
   * @param lv the LocalVariablesStack to print
   * @return a printed representation of {@code lv}
   */
  @SuppressWarnings({
    "allcheckers:purity.not.sideeffectfree.call",
    "lock:method.guarantee.violated"
  }) // side effect to local state
  @SideEffectFree
  public String toString(@GuardSatisfied StackTypes this, LocalVariables lv) {
    StringJoiner sj = new StringJoiner(", ", "{", "}");
    for (int i = 0; i < lv.maxLocals(); i++) {
      Type t = lv.get(i);
      sj.add(t instanceof UninitializedObjectType ? "uninitialized-object" : t.toString());
    }
    return sj.toString();
  }
}
