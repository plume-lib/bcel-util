package org.plumelib.bcelutil;

import java.util.Arrays;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.StackMapEntry;
import org.apache.bcel.classfile.StackMapType;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.IfInstruction;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.LOOKUPSWITCH;
import org.apache.bcel.generic.LineNumberGen;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.TABLESWITCH;
import org.apache.bcel.verifier.structurals.OperandStack;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This class provides utility methods to maintain and modify a method's InstructionList within a
 * Java class file. It is a subclass of {@link org.plumelib.bcelutil.StackMapUtils} and thus handles
 * all the StackMap side effects of InstructionList modification. It can be thought of as an
 * extension to BCEL.
 *
 * <p>BCEL ought to automatically build and maintain the StackMapTable in a manner similar to the
 * LineNumberTable and the LocalVariableTable. However, for historical reasons, it does not.
 *
 * <p>If you wish to modify a Java class file, you should create a subclass of InstructionListUtils
 * to do the modifications. Then a rough program template for that class would be:
 *
 * <pre>
 *   import org.apache.bcel.classfile.*;
 *   import org.apache.bcel.generic.*;
 *
 *  try {
 *    // Parse the bytes of the classfile, die on any errors
 *    ClassParser parser = new ClassParser(new ByteArrayInputStream(classfileBuffer), className);
 *    JavaClass jc = parser.parse();
 *
 *    // Transform the file
 *    modifyClass(jc);
 *
 *  } catch (Throwable e) {
 *    throw new RuntimeException("Unexpected error", e);
 *  }
 *
 *  void modifyClass(JavaClass jc) {
 *    ClassGen cg = new ClassGen(jc);
 *    String classname = cg.getClassName();
 *    //save ConstantPool for use by StackMapUtils
 *    pool = cg.getConstantPool();
 *
 *    for (Method m : cg.getMethods()) {
 *      try {
 *        MethodGen mg = new MethodGen(m, classname, pool);
 *        // Get the instruction list and skip methods with no instructions
 *        InstructionList il = mg.getInstructionList();
 *        if (il == null) {
 *          continue;
 *        }
 *
 *        // Get existing StackMapTable (if present)
 *        setCurrentStackMapTable(mg, cg.getMajor());
 *        fixLocalVariableTable(mg);
 *
 *        // Create a map of Uninitialized_variable_info offsets to
 *        // InstructionHandles.
 *        buildUninitializedNewMap(il);
 *
 * This is where you would insert your code to modify the current method (mg).
 * Most often this is done with members of the {@link org.apache.bcel.generic}
 * package.  However, you should use the members of InstrutionListUtils to update
 * the byte code instructions of mg rather than similar methods in the BCEL
 * generic package in order to maintain the integrity of the method's StackMapTable.
 *
 *        // Update the Uninitialized_variable_info offsets before
 *        // we write out the new StackMapTable.
 *        updateUninitializedNewOffsets(il);
 *        createNewStackMapAttribute(mg);
 *
 *        // Update the instruction list
 *        mg.setInstructionList(il);
 *        mg.update();
 *
 *        // Update the max stack
 *        mg.setMaxStack();
 *        mg.setMaxLocals();
 *        mg.update();
 *
 *        remove_local_variable_type_table(mg);
 *
 *        // Update the method in the class
 *        cg.replaceMethod(m, mg.getMethod());
 *
 *      } catch (Throwable t) {
 *        throw new Error("Unexpected error processing " + classname + "." + m.getName(), t);
 *      }
 *    }
 *  }
 * </pre>
 *
 * <p>It one only wishes to examine a class file, the use of this class is not necessary. See {@link
 * org.plumelib.bcelutil.BcelUtil} for notes on inspecting a Java class file.
 */
@SuppressWarnings("nullness")
public abstract class InstructionListUtils extends StackMapUtils {

  /** Create a new InstructionListUtils. */
  public InstructionListUtils() {}

  /**
   * Appends the specified instruction to the end of the specified list. Required because for some
   * reason you can't directly append jump instructions to the list -- but you can create new ones
   * and append them.
   *
   * @param il InstructionList to be modified
   * @param inst Instruction to be appended
   */
  protected final void append_inst(InstructionList il, Instruction inst) {

    // System.out.println ("append_inst: " + inst.getClass().getName());
    if (inst instanceof LOOKUPSWITCH) {
      LOOKUPSWITCH ls = (LOOKUPSWITCH) inst;
      il.append(new LOOKUPSWITCH(ls.getMatchs(), ls.getTargets(), ls.getTarget()));
    } else if (inst instanceof TABLESWITCH) {
      TABLESWITCH ts = (TABLESWITCH) inst;
      il.append(new TABLESWITCH(ts.getMatchs(), ts.getTargets(), ts.getTarget()));
    } else if (inst instanceof IfInstruction) {
      IfInstruction ifi = (IfInstruction) inst;
      il.append(InstructionFactory.createBranchInstruction(inst.getOpcode(), ifi.getTarget()));
    } else {
      il.append(inst);
    }
  }

  /**
   * Inserts an instruction list at the beginning of a method.
   *
   * @param mg MethodGen of method to be modified
   * @param newIl InstructionList holding the new code
   */
  protected final void insertAtMethodStart(MethodGen mg, InstructionList newIl) {

    // Ignore methods with no instructions
    InstructionList il = mg.getInstructionList();
    if (il == null) {
      return;
    }
    insertBeforeHandle(mg, il.getStart(), newIl, false);
  }

  /**
   * Inserts a new instruction list into an existing instruction list just prior to the indicated
   * instruction handle (which must be a member of the existing instruction list). If newIl is null,
   * do nothing.
   *
   * @param mg MethodGen containing the instruction handle
   * @param ih InstructionHandle indicating where to insert new code
   * @param newIl InstructionList holding the new code
   * @param redirectBranches flag indicating if branch targets should be moved from ih to newIl
   */
  protected final void insertBeforeHandle(
      MethodGen mg,
      InstructionHandle ih,
      @Nullable InstructionList newIl,
      boolean redirectBranches) {

    if (newIl == null) {
      return;
    }

    // Ignore methods with no instructions
    InstructionList il = mg.getInstructionList();
    if (il == null) {
      return;
    }

    boolean atStart = (ih.getPrev() == null);
    newIl.setPositions();
    InstructionHandle newEnd = newIl.getEnd();
    final int newLength = newEnd.getPosition() + newEnd.getInstruction().getLength();

    printIl(ih, "Before insert_inst");
    debugInstrument.log("  insert_inst: %d%n%s%n", newIl.getLength(), newIl);
    debugInstrument.log("  ih: %s%n", ih);

    // Add the new code in front of the instruction handle.
    InstructionHandle newStart = il.insert(ih, newIl);
    il.setPositions();

    if (redirectBranches) {
      // Move all of the branches from the old instruction to the new start.
      il.redirectBranches(ih, newStart);
    }

    // Move other targets to the new start.
    if (ih.hasTargeters()) {
      for (InstructionTargeter it : ih.getTargeters()) {
        if ((it instanceof LineNumberGen) && redirectBranches) {
          it.updateTarget(ih, newStart);
        } else if (it instanceof LocalVariableGen) {
          LocalVariableGen lvg = (LocalVariableGen) it;
          // If ih is end of local variable range, leave as is.
          // If ih is start of local variable range and we are
          // at the begining of the method go ahead and change
          // start to newStart.  This is to preserve live range
          // for variables that are live for entire method.
          if ((lvg.getStart() == ih) && atStart) {
            it.updateTarget(ih, newStart);
          }
        } else if ((it instanceof CodeExceptionGen) && redirectBranches) {
          CodeExceptionGen exc = (CodeExceptionGen) it;
          if (exc.getStartPC() == ih) {
            exc.updateTarget(ih, newStart);
          } else if (exc.getEndPC() == ih) {
            // leave EndPC unchanged
          } else if (exc.getHandlerPC() == ih) {
            exc.setHandlerPC(newStart);
          } else {
            System.out.printf("Malformed CodeException: %s%n", exc);
          }
        }
      }
    }

    // Need to update stack map for change in length of instruction bytes.
    // If redirectBranches is true then we don't want to change the
    // offset of a stack map that was on the original ih, we want it
    // to 'move' to the newStart.  If we did not redirect the branches
    // then we do want any stack map associated with the original ih
    // to stay there. The routine updateStackMap starts looking for
    // a stack map after the location given as its first argument.
    // Thus we need to subtract one from this location if the
    // redirectBranches flag is false.
    il.setPositions();
    updateStackMapOffset(newStart.getPosition() - (redirectBranches ? 0 : 1), newLength);

    printIl(newStart, "After updateStackMapOffset");

    // We need to see if inserting the additional instructions caused
    // a change in the amount of switch instruction padding bytes.
    modifyStackMapsForSwitches(newStart, il);
  }

  /**
   * Print a BCEL instruction list to the debugInstrument log.
   *
   * @param start start of the instruction list
   * @param label a descriptive string for the instruction list
   */
  private void printIl(InstructionHandle start, String label) {
    if (debugInstrument.enabled()) {
      printStackMapTable(label);
      InstructionHandle tih = start;
      while (tih != null) {
        debugInstrument.log("inst: %s %n", tih);
        if (tih.hasTargeters()) {
          for (InstructionTargeter it : tih.getTargeters()) {
            debugInstrument.log("targeter: %s %n", it);
          }
        }
        tih = tih.getNext();
      }
    }
  }

  /**
   * Convenience function to build an instruction list.
   *
   * @param instructions a variable number of BCEL instructions
   * @return an InstructionList
   */
  protected final InstructionList build_il(Instruction... instructions) {
    InstructionList il = new InstructionList();
    for (Instruction inst : instructions) {
      append_inst(il, inst);
    }
    return il;
  }

  /**
   * Delete instruction(s) from startIh thru endIh in an instruction list. startIh may be the first
   * instruction of the list, but endIh must not be the last instruction of the list. startIh may be
   * equal to endIh. There must not be any targeters on any of the instructions to be deleted except
   * for startIh. Those targeters will be moved to the first instruction following endIh.
   *
   * @param mg MethodGen containing the instruction handles
   * @param startIh InstructionHandle indicating first instruction to be deleted
   * @param endIh InstructionHandle indicating last instruction to be deleted
   */
  protected final void delete_instructions(
      MethodGen mg, InstructionHandle startIh, InstructionHandle endIh) {
    InstructionList il = mg.getInstructionList();
    InstructionHandle newStart = endIh.getNext();
    if (newStart == null) {
      throw new RuntimeException("Cannot delete last instruction.");
    }

    il.setPositions();
    final int numDeleted = startIh.getPosition() - newStart.getPosition();

    // Move all of the branches from the first instruction to the new start
    il.redirectBranches(startIh, newStart);

    // Move other targeters to the new start.
    if (startIh.hasTargeters()) {
      for (InstructionTargeter it : startIh.getTargeters()) {
        if (it instanceof LineNumberGen) {
          it.updateTarget(startIh, newStart);
        } else if (it instanceof LocalVariableGen) {
          it.updateTarget(startIh, newStart);
        } else if (it instanceof CodeExceptionGen) {
          CodeExceptionGen exc = (CodeExceptionGen) it;
          if (exc.getStartPC() == startIh) {
            exc.updateTarget(startIh, newStart);
          } else if (exc.getEndPC() == startIh) {
            exc.updateTarget(startIh, newStart);
          } else if (exc.getHandlerPC() == startIh) {
            exc.setHandlerPC(newStart);
          } else {
            System.out.printf("Malformed CodeException: %s%n", exc);
          }
        } else {
          System.out.printf("unexpected target %s%n", it);
        }
      }
    }

    // Remove the old handle(s).  There should not be any targeters left.
    try {
      il.delete(startIh, endIh);
    } catch (Exception e) {
      throw new Error("Can't delete instruction list", e);
    }
    // Need to update instruction positions due to delete above.
    il.setPositions();

    // Update stack map to account for deleted instructions.
    updateStackMapOffset(newStart.getPosition(), numDeleted);

    // Check to see if the deletion caused any changes
    // in the amount of switch instruction padding bytes.
    // If so, we may need to update the corresponding stackmap.
    modifyStackMapsForSwitches(newStart, il);
  }

  /**
   * Compute the StackMapTypes of the live variables of the current method at a specific location
   * within the method. There may be gaps ("Bogus" or non-live slots) so we can't just count the
   * number of live variables, we must find the max index of all the live variables.
   *
   * @param mg MethodGen for the current method
   * @param location the code location to be evaluated
   * @return an array of StackMapType describing the live locals at location
   */
  protected final StackMapType[] calculateLiveLocalTypes(MethodGen mg, int location) {
    int maxLocalIndex = -1;
    StackMapType[] localMapTypes = new StackMapType[mg.getMaxLocals()];
    Arrays.fill(localMapTypes, new StackMapType(Const.ITEM_Bogus, -1, pool.getConstantPool()));
    for (LocalVariableGen lv : mg.getLocalVariables()) {
      if (location >= lv.getStart().getPosition()) {
        if (lv.getLiveToEnd() || location < lv.getEnd().getPosition()) {
          int i = lv.getIndex();
          localMapTypes[i] = generateStackMapTypeFromType(lv.getType());
          maxLocalIndex = Math.max(maxLocalIndex, i);
        }
      }
    }
    return Arrays.copyOf(localMapTypes, maxLocalIndex + 1);
  }

  /**
   * Compute the StackMapTypes of the items on the execution stack as described by the OperandStack
   * argument.
   *
   * @param stack an OperandStack object
   * @return an array of StackMapType describing the stack contents
   */
  protected final StackMapType[] calculateLiveStackTypes(OperandStack stack) {
    int ss = stack.size();
    StackMapType[] stackMapTypes = new StackMapType[ss];
    for (int ii = 0; ii < ss; ii++) {
      stackMapTypes[ii] = generateStackMapTypeFromType(stack.peek(ss - ii - 1));
    }
    return stackMapTypes;
  }

  /**
   * Replace instruction ih in list il with the instructions in newIl. If newIl is null, do nothing.
   *
   * @param mg MethodGen containing the instruction handle
   * @param il InstructionList containing ih
   * @param ih InstructionHandle indicating where to insert new code
   * @param newIl InstructionList holding the new code
   */
  protected final void replaceInstructions(
      MethodGen mg, InstructionList il, InstructionHandle ih, @Nullable InstructionList newIl) {

    if (newIl == null) {
      return;
    }

    InstructionHandle newEnd;
    InstructionHandle newStart;
    int oldLength = ih.getInstruction().getLength();

    newIl.setPositions();
    InstructionHandle end = newIl.getEnd();
    int newLength = end.getPosition() + end.getInstruction().getLength();

    debugInstrument.log("  replace_inst: %s %d%n%s%n", ih, newIl.getLength(), newIl);
    printIl(ih, "Before replace_inst");

    // If there is only one new instruction, just replace it in the handle
    if (newIl.getLength() == 1) {
      ih.setInstruction(newIl.getEnd().getInstruction());
      if (oldLength == newLength) {
        // no possible changes downstream, so we can exit now
        return;
      }
      printStackMapTable("replace_inst_with_single_inst B");
      il.setPositions();
      newEnd = ih;
      // Update stack map for change in length of instruction bytes.
      updateStackMapOffset(ih.getPosition(), (newLength - oldLength));

      // We need to see if inserting the additional instructions caused
      // a change in the amount of switch instruction padding bytes.
      // If so, we may need to update the corresponding stackmap.
      modifyStackMapsForSwitches(newEnd, il);
    } else {
      printStackMapTable("replace_inst_with_inst_list B");
      // We are inserting more than one instruction.
      // Get the start and end handles of the new instruction list.
      newEnd = newIl.getEnd();
      newStart = il.insert(ih, newIl);
      il.setPositions();

      // Just in case there is a switch instruction in newIl we need
      // to recalculate newLength as the padding may have changed.
      newLength = newEnd.getNext().getPosition() - newStart.getPosition();

      // Move all of the branches from the old instruction to the new start
      il.redirectBranches(ih, newStart);

      printIl(newEnd, "replace_inst #1");

      // Move other targets to the new instuctions.
      if (ih.hasTargeters()) {
        for (InstructionTargeter it : ih.getTargeters()) {
          if (it instanceof LineNumberGen) {
            it.updateTarget(ih, newStart);
          } else if (it instanceof LocalVariableGen) {
            it.updateTarget(ih, newEnd);
          } else if (it instanceof CodeExceptionGen) {
            CodeExceptionGen exc = (CodeExceptionGen) it;
            if (exc.getStartPC() == ih) {
              exc.updateTarget(ih, newStart);
            } else if (exc.getEndPC() == ih) {
              exc.updateTarget(ih, newEnd);
            } else if (exc.getHandlerPC() == ih) {
              exc.setHandlerPC(newStart);
            } else {
              System.out.printf("Malformed CodeException: %s%n", exc);
            }
          } else {
            System.out.printf("unexpected target %s%n", it);
          }
        }
      }

      printIl(newEnd, "replace_inst #2");

      // Remove the old handle.  There should be no targeters left to it.
      try {
        il.delete(ih);
      } catch (Exception e) {
        System.out.printf("Can't delete instruction: %s at %s%n", mg.getClassName(), mg.getName());
        throw new Error("Can't delete instruction", e);
      }
      // Need to update instruction address due to delete above.
      il.setPositions();

      printIl(newEnd, "replace_inst #3");

      if (needStackMap) {
        // Before we look for branches in the inserted code we need
        // to update any existing stack maps for locations in the old
        // code that are after the inserted code.
        updateStackMapOffset(newStart.getPosition(), (newLength - oldLength));

        // We need to see if inserting the additional instructions caused
        // a change in the amount of switch instruction padding bytes.
        // If so, we may need to update the corresponding stackmap.
        modifyStackMapsForSwitches(newEnd, il);
        printStackMapTable("replace_inst_with_inst_list C");

        // Look for branches within the new il; i.e., both the source
        // and target must be within the new il.  If we find any, the
        // target will need a stack map entry.
        // This situation is caused by a call to "instrument_object_call".
        InstructionHandle nih = newStart;
        int targetCount = 0;
        int[] targetOffsets = new int[2]; // see note below for why '2'

        // Any targeters on the first instruction will be from 'outside'
        // the new il so we start with the second instruction. (We already
        // know there is more than one instruction in the new il.)
        nih = nih.getNext();

        // We assume there is more code after the new il insertion point
        // so this getNext will not fail.
        newEnd = newEnd.getNext();
        while (nih != newEnd) {
          if (nih.hasTargeters()) {
            for (InstructionTargeter it : nih.getTargeters()) {
              if (it instanceof BranchInstruction) {
                targetOffsets[targetCount++] = nih.getPosition();
                debugInstrument.log("New branch target: %s %n", nih);
              }
            }
          }
          nih = nih.getNext();
        }

        printIl(newEnd, "replace_inst #4");

        if (targetCount != 0) {
          // Currently, targetCount is always 2; but code is
          // written to allow more.
          int curLoc = newStart.getPosition();
          int origSize = stackMapTable.length;
          final StackMapEntry[] newStackMapTable = new StackMapEntry[origSize + targetCount];

          // Calculate the operand stack value(s) for revised code.
          mg.setMaxStack();
          OperandStack stack;
          StackTypes stackTypes = bcelCalcStackTypes(mg);
          if (stackTypes == null) {
            Error e =
                new Error(
                    String.format(
                        "bcelCalcStackTypes failure in %s.%s%n", mg.getClassName(), mg.getName()));
            e.printStackTrace();
            throw e;
          }

          // Find last stack map entry prior to first new branch target;
          // returns -1 if there isn't one. Also sets runningOffset and numberActiveLocals.
          // The '+1' below means newIndex points to the first stack map entry after our
          // inserted code.  There may not be one; in which case newIndex == origSize.
          int newIndex = findStackMapIndexBefore(targetOffsets[0]) + 1;

          // The Java compiler can 'simplfy' the generated class file by not
          // inserting a stack map entry every time a local is defined if
          // that stack map entry is not needed as a branch target.  Thus,
          // there can be more live locals than defined by the stack maps.
          // This leads to serious complications if we wish to insert instrumentation
          // code, that contains internal branches and hence needs stack
          // map entries, into a section of code with 'extra' live locals.
          // If we don't include these extra locals in our inserted stack
          // map entries and they are subsequently referenced, that would
          // cause a verification error.  But if we do include them the locals
          // state might not be correct when execution reaches the first
          // stack map entry after our inserted code and that
          // would also cause a verification error. Dicey stuff.

          // I think one possibllity would be to insert a nop instruction
          // with a stack map of CHOP right after the last use of an 'extra'
          // local prior to the next stack map entry. An interesting alternative
          // might be, right as we start to process a method, make a pass over
          // the byte codes and add any 'missing' stack maps. This would
          // probably be too heavy as most instrumentation does not contain branches.
          // Not sure which of these two methods is 'easier' at this point.

          // I'm going to try a simplification.  If there are 'extra' locals
          // and we need to generate a FULL stack map - then go through and
          // make all subsequent StackMaps FULL as well.

          // The inserted code has pushed an object reference on the stack.
          // The StackMap(s) we create as targets of an internal branch
          // must account for this item.  Normally, we would use
          // SAME_LOCALS_1_STACK_ITEM_FRAME for this case, but there is a
          // possibility that the compiler has already allocated extra local items
          // that it plans to identify in a subsequent StackMap APPEND entry.

          // First, lets calculate the number and types of the live locals.
          StackMapType[] localMapTypes = calculateLiveLocalTypes(mg, curLoc);
          int localMapIndex = localMapTypes.length;

          // localMapIndex now contains the number of live locals.
          // numberActiveLocals has been calculated from the existing StackMap.
          // If these two are equal, we should be ok.
          int numberExtraLocals = localMapIndex - numberActiveLocals;
          // lets do a sanity check
          assert numberExtraLocals >= 0
              : "invalid extra locals count: " + numberActiveLocals + ", " + localMapIndex;

          // Copy any existing stack maps prior to inserted code.
          System.arraycopy(stackMapTable, 0, newStackMapTable, 0, newIndex);

          boolean needFullMaps = false;
          for (int i = 0; i < targetCount; i++) {
            stack = stackTypes.get(targetOffsets[i]);
            debugInstrument.log("stack: %s %n", stack);

            if (numberExtraLocals == 0 && stack.size() == 1 && !needFullMaps) {
              // the simple case
              StackMapType stackMapType0 = generateStackMapTypeFromType(stack.peek(0));
              StackMapType[] stackMapTypes0 = {stackMapType0};
              newStackMapTable[newIndex + i] =
                  new StackMapEntry(
                      Const.SAME_LOCALS_1_STACK_ITEM_FRAME,
                      0, // byteCodeOffset set below
                      null,
                      stackMapTypes0,
                      pool.getConstantPool());
            } else {
              // need a FULL_FRAME stack map entry
              needFullMaps = true;
              newStackMapTable[newIndex + i] =
                  new StackMapEntry(
                      Const.FULL_FRAME,
                      0, // byteCodeOffset set below
                      calculateLiveLocalTypes(mg, targetOffsets[i]),
                      calculateLiveStackTypes(stack),
                      pool.getConstantPool());
            }
            // now set the offset from the previous Stack Map entry to our new one.
            newStackMapTable[newIndex + i].updateByteCodeOffset(
                targetOffsets[i] - (runningOffset + 1));
            runningOffset = targetOffsets[i];
          }

          // now copy remaining 'old' stack maps
          int remainder = origSize - newIndex;
          if (remainder > 0) {
            // before we copy, we need to update first map after insert
            l1:
            while (nih != null) {
              if (nih.hasTargeters()) {
                for (InstructionTargeter it : nih.getTargeters()) {
                  if (it instanceof BranchInstruction) {
                    stackMapTable[newIndex].updateByteCodeOffset(
                        nih.getPosition()
                            - targetOffsets[targetCount - 1]
                            - 1
                            - stackMapTable[newIndex].getByteCodeOffset());
                    break l1;
                  } else if (it instanceof CodeExceptionGen) {
                    CodeExceptionGen exc = (CodeExceptionGen) it;
                    if (exc.getHandlerPC() == nih) {
                      stackMapTable[newIndex].updateByteCodeOffset(
                          nih.getPosition()
                              - targetOffsets[targetCount - 1]
                              - 1
                              - stackMapTable[newIndex].getByteCodeOffset());
                      break l1;
                    }
                  }
                }
              }
              nih = nih.getNext();
            }

            // Now we can copy the remaining stack map entries.
            if (needFullMaps) {
              // Must convert all remaining stack map entries to FULL.
              while (remainder > 0) {
                int stackMapOffset = stackMapTable[newIndex].getByteCodeOffset();
                runningOffset = runningOffset + stackMapOffset + 1;
                stack = stackTypes.get(runningOffset);
                // System.out.printf("runningOffset: %d, stack: %s%n", runningOffset, stack);
                newStackMapTable[newIndex + targetCount] =
                    new StackMapEntry(
                        Const.FULL_FRAME,
                        stackMapOffset,
                        calculateLiveLocalTypes(mg, runningOffset),
                        calculateLiveStackTypes(stack),
                        pool.getConstantPool());
                newIndex++;
                remainder--;
              }
            } else {
              System.arraycopy(
                  stackMapTable, newIndex, newStackMapTable, newIndex + targetCount, remainder);
            }
          }
          stackMapTable = newStackMapTable;
        }
      }
    }

    debugInstrument.log("%n");
    printIl(newEnd, "replace_inst #5");
  }
}
