package org.plumelib.bcelutil;

import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.Type;
import org.apache.bcel.verifier.structurals.Frame;
import org.apache.bcel.verifier.structurals.LocalVariables;
import org.apache.bcel.verifier.structurals.OperandStack;
import org.apache.bcel.verifier.structurals.UninitializedObjectType;

/*>>>
import org.checkerframework.checker.lock.qual.*;
import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.dataflow.qual.*;
*/

/**
 * Stores the types on the stack at each instruction (identified by byte code offset) in a method.
 */
public final class StackTypes {

  /** The state of the operand stack at each instruction location. */
  OperandStack[] os_arr;

  /** The state of the live local variables at each instruction location. */
  LocalVariables[] loc_arr;

  /**
   * Create a record of the types on the stack at each instruction in a method. The created object
   * starts out empty, with no type information.
   *
   * @param mg the method
   */
  public StackTypes(MethodGen mg) {
    InstructionList il = mg.getInstructionList();
    int size = 0;
    if (il != null) size = il.getEnd().getPosition();
    os_arr = new OperandStack[size + 1];
    loc_arr = new LocalVariables[size + 1];
  }

  /**
   * Sets the stack for the instruction at the specified offset.
   *
   * @param offset the offset at which the instruction appears
   * @param f the stack frame to use for the instruction
   */
  public void set(int offset, Frame f) {

    OperandStack os = f.getStack();
    // logger.info ("stack[" + offset + "] = " + toString(os));

    loc_arr[offset] = (LocalVariables) (f.getLocals().clone());
    os_arr[offset] = (OperandStack) (os.clone());
  }

  /**
   * Returns the stack contents at the specified offset.
   *
   * @param offset the offset to which to get the stack contents
   * @return the stack at the (instruction at the) given offset
   */
  public OperandStack get(int offset) {
    return os_arr[offset];
  }

  @SuppressWarnings({"purity", "lock"}) // local StringBuilder
  /*@SideEffectFree*/
  @Override
  public String toString(/*>>>@GuardSatisfied StackTypes this*/ ) {

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < os_arr.length; i++) {
      if (os_arr[i] != null) {
        sb.append(String.format("Instruction %d:\n", i));
        sb.append(String.format("  stack:  %s\n", toString(os_arr[i])));
        sb.append(String.format("  locals: %s\n", toString(loc_arr[i])));
      }
    }

    return (sb.toString());
  }

  /**
   * Return a printed representation of the given OperandStack.
   *
   * @param os the OperandStack to print
   * @return a printed representation of {@code os}
   */
  /*@SideEffectFree*/
  public String toString(/*>>>@GuardSatisfied StackTypes this,*/ OperandStack os) {

    String buff = "";

    for (int i = 0; i < os.size(); i++) {
      if (buff.length() > 0) buff += ", ";
      Type t = os.peek(i);
      if (t instanceof UninitializedObjectType) {
        buff += "uninitialized-object";
      } else {
        buff += t;
      }
    }

    return ("{" + buff + "}");
  }

  /**
   * Return a printed representation of the given LocalVariables.
   *
   * @param lv the LocalVariablesStack to print
   * @return a printed representation of {@code lv}
   */
  /*@SideEffectFree*/
  public String toString(/*>>>@GuardSatisfied StackTypes this,*/ LocalVariables lv) {

    String buff = "";

    for (int i = 0; i < lv.maxLocals(); i++) {
      if (buff.length() > 0) buff += ", ";
      Type t = lv.get(i);
      if (t instanceof UninitializedObjectType) {
        buff += "uninitialized-object";
      } else {
        buff += t;
      }
    }
    return ("{" + buff + "}");
  }
}
