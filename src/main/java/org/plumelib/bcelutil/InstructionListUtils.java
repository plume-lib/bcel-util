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
 *        set_current_stack_map_table(mg, cg.getMajor());
 *        fix_local_variable_table(mg);
 *
 *        // Create a map of Uninitialized_variable_info offsets to
 *        // InstructionHandles.
 *        build_unitialized_NEW_map(il);
 *
 * This is where you would insert your code to modify the current method (mg).
 * Most often this is done with members of the {@link org.apache.bcel.generic}
 * package.  However, you should use the members of InstrutionListUtils to update
 * the byte code instructions of mg rather than similar methods in the BCEL
 * generic package in order to maintain the integrity of the method's StackMapTable.
 *
 *        // Update the Uninitialized_variable_info offsets before
 *        // we write out the new StackMapTable.
 *        update_uninitialized_NEW_offsets(il);
 *        create_new_stack_map_attribute(mg);
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
 * It one only wishes to examine a class file, the use of this class is not necessary. See {@link
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
   * @param new_il InstructionList holding the new code
   */
  protected final void insert_at_method_start(MethodGen mg, InstructionList new_il) {

    // Ignore methods with no instructions
    InstructionList il = mg.getInstructionList();
    if (il == null) {
      return;
    }
    insert_before_handle(mg, il.getStart(), new_il, false);
  }

  /**
   * Inserts a new instruction list into an existing instruction list just prior to the indicated
   * instruction handle (which must be a member of the existing instruction list). If new_il is
   * null, do nothing.
   *
   * @param mg MethodGen containing the instruction handle
   * @param ih InstructionHandle indicating where to insert new code
   * @param new_il InstructionList holding the new code
   * @param redirect_branches flag indicating if branch targets should be moved from ih to new_il
   */
  protected final void insert_before_handle(
      MethodGen mg,
      InstructionHandle ih,
      @Nullable InstructionList new_il,
      boolean redirect_branches) {

    if (new_il == null) {
      return;
    }

    // Ignore methods with no instructions
    InstructionList il = mg.getInstructionList();
    if (il == null) {
      return;
    }

    boolean at_start = (ih.getPrev() == null);
    new_il.setPositions();
    InstructionHandle new_end = new_il.getEnd();
    int new_length = new_end.getPosition() + new_end.getInstruction().getLength();

    print_il(ih, "Before insert_inst");
    debug_instrument.log("  insert_inst: %d%n%s%n", new_il.getLength(), new_il);
    debug_instrument.log("  ih: %s%n", ih);

    // Add the new code in front of the instruction handle.
    InstructionHandle new_start = il.insert(ih, new_il);
    il.setPositions();

    if (redirect_branches) {
      // Move all of the branches from the old instruction to the new start.
      il.redirectBranches(ih, new_start);
    }

    // Move other targets to the new start.
    if (ih.hasTargeters()) {
      for (InstructionTargeter it : ih.getTargeters()) {
        if ((it instanceof LineNumberGen) && redirect_branches) {
          it.updateTarget(ih, new_start);
        } else if (it instanceof LocalVariableGen) {
          LocalVariableGen lvg = (LocalVariableGen) it;
          // If ih is end of local variable range, leave as is.
          // If ih is start of local variable range and we are
          // at the begining of the method go ahead and change
          // start to new_start.  This is to preserve live range
          // for variables that are live for entire method.
          if ((lvg.getStart() == ih) && at_start) {
            it.updateTarget(ih, new_start);
          }
        } else if ((it instanceof CodeExceptionGen) && redirect_branches) {
          CodeExceptionGen exc = (CodeExceptionGen) it;
          if (exc.getStartPC() == ih) exc.updateTarget(ih, new_start);
          else if (exc.getEndPC() == ih) {
            // leave EndPC unchanged
          } else if (exc.getHandlerPC() == ih) {
            exc.setHandlerPC(new_start);
          } else {
            System.out.printf("Malformed CodeException: %s%n", exc);
          }
        }
      }
    }

    // Need to update stack map for change in length of instruction bytes.
    // If redirect_branches is true then we don't want to change the
    // offset of a stack map that was on the original ih, we want it
    // to 'move' to the new_start.  If we did not redirect the branches
    // then we do want any stack map associated with the original ih
    // to stay there. The routine update_stack_map starts looking for
    // a stack map after the location given as its first argument.
    // Thus we need to subtract one from this location if the
    // redirect_branches flag is false.
    il.setPositions();
    update_stack_map_offset(new_start.getPosition() - (redirect_branches ? 0 : 1), new_length);

    print_il(new_start, "After update_stack_map_offset");

    // We need to see if inserting the additional instructions caused
    // a change in the amount of switch instruction padding bytes.
    modify_stack_maps_for_switches(new_start, il);
  }

  /**
   * Print a BCEL instruction list to the debug_instrument log.
   *
   * @param start start of the instruction list
   * @param label a descriptive string for the instruction list
   */
  private void print_il(InstructionHandle start, String label) {
    if (debug_instrument.enabled()) {
      print_stack_map_table(label);
      InstructionHandle tih = start;
      while (tih != null) {
        debug_instrument.log("inst: %s %n", tih);
        if (tih.hasTargeters()) {
          for (InstructionTargeter it : tih.getTargeters()) {
            debug_instrument.log("targeter: %s %n", it);
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
   * Delete instruction(s) from start_ih thru end_ih in an instruction list. start_ih may be the
   * first instruction of the list, but end_ih must not be the last instruction of the list.
   * start_ih may be equal to end_ih. There must not be any targeters on any of the instructions to
   * be deleted except for start_ih. Those targeters will be moved to the first instruction
   * following end_ih.
   *
   * @param mg MethodGen containing the instruction handles
   * @param start_ih InstructionHandle indicating first instruction to be deleted
   * @param end_ih InstructionHandle indicating last instruction to be deleted
   */
  protected final void delete_instructions(
      MethodGen mg, InstructionHandle start_ih, InstructionHandle end_ih) {
    InstructionList il = mg.getInstructionList();
    InstructionHandle new_start = end_ih.getNext();
    if (new_start == null) {
      throw new RuntimeException("Cannot delete last instruction.");
    }

    il.setPositions();
    int size_deletion = start_ih.getPosition() - new_start.getPosition();

    // Move all of the branches from the first instruction to the new start
    il.redirectBranches(start_ih, new_start);

    // Move other targeters to the new start.
    if (start_ih.hasTargeters()) {
      for (InstructionTargeter it : start_ih.getTargeters()) {
        if (it instanceof LineNumberGen) {
          it.updateTarget(start_ih, new_start);
        } else if (it instanceof LocalVariableGen) {
          it.updateTarget(start_ih, new_start);
        } else if (it instanceof CodeExceptionGen) {
          CodeExceptionGen exc = (CodeExceptionGen) it;
          if (exc.getStartPC() == start_ih) exc.updateTarget(start_ih, new_start);
          else if (exc.getEndPC() == start_ih) exc.updateTarget(start_ih, new_start);
          else if (exc.getHandlerPC() == start_ih) exc.setHandlerPC(new_start);
          else System.out.printf("Malformed CodeException: %s%n", exc);
        } else {
          System.out.printf("unexpected target %s%n", it);
        }
      }
    }

    // Remove the old handle(s).  There should not be any targeters left.
    try {
      il.delete(start_ih, end_ih);
    } catch (Exception e) {
      throw new Error("Can't delete instruction list", e);
    }
    // Need to update instruction positions due to delete above.
    il.setPositions();

    // Update stack map to account for deleted instructions.
    update_stack_map_offset(new_start.getPosition(), size_deletion);

    // Check to see if the deletion caused any changes
    // in the amount of switch instruction padding bytes.
    // If so, we may need to update the corresponding stackmap.
    modify_stack_maps_for_switches(new_start, il);
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
  protected final StackMapType[] calculate_live_local_types(MethodGen mg, int location) {
    int max_local_index = -1;
    StackMapType[] local_map_types = new StackMapType[mg.getMaxLocals()];
    Arrays.fill(local_map_types, new StackMapType(Const.ITEM_Bogus, -1, pool.getConstantPool()));
    for (LocalVariableGen lv : mg.getLocalVariables()) {
      if (location >= lv.getStart().getPosition()) {
        if (lv.getLiveToEnd() || location < lv.getEnd().getPosition()) {
          int i = lv.getIndex();
          local_map_types[i] = generate_StackMapType_from_Type(lv.getType());
          max_local_index = Math.max(max_local_index, i);
        }
      }
    }
    return Arrays.copyOf(local_map_types, max_local_index + 1);
  }

  /**
   * Compute the StackMapTypes of the items on the execution stack as described by the OperandStack
   * argument.
   *
   * @param stack an OperandStack object
   * @return an array of StackMapType describing the stack contents
   */
  protected final StackMapType[] calculate_live_stack_types(OperandStack stack) {
    int ss = stack.size();
    StackMapType[] stack_map_types = new StackMapType[ss];
    for (int ii = 0; ii < ss; ii++) {
      stack_map_types[ii] = generate_StackMapType_from_Type(stack.peek(ss - ii - 1));
    }
    return stack_map_types;
  }

  /**
   * Replace instruction ih in list il with the instructions in new_il. If new_il is null, do
   * nothing.
   *
   * @param mg MethodGen containing the instruction handle
   * @param il InstructionList containing ih
   * @param ih InstructionHandle indicating where to insert new code
   * @param new_il InstructionList holding the new code
   */
  protected final void replace_instructions(
      MethodGen mg, InstructionList il, InstructionHandle ih, @Nullable InstructionList new_il) {

    if (new_il == null) {
      return;
    }

    InstructionHandle new_end;
    InstructionHandle new_start;
    int old_length = ih.getInstruction().getLength();

    new_il.setPositions();
    InstructionHandle end = new_il.getEnd();
    int new_length = end.getPosition() + end.getInstruction().getLength();

    debug_instrument.log("  replace_inst: %s %d%n%s%n", ih, new_il.getLength(), new_il);
    print_il(ih, "Before replace_inst");

    // If there is only one new instruction, just replace it in the handle
    if (new_il.getLength() == 1) {
      ih.setInstruction(new_il.getEnd().getInstruction());
      if (old_length == new_length) {
        // no possible changes downstream, so we can exit now
        return;
      }
      print_stack_map_table("replace_inst_with_single_inst B");
      il.setPositions();
      new_end = ih;
      // Update stack map for change in length of instruction bytes.
      update_stack_map_offset(ih.getPosition(), (new_length - old_length));

      // We need to see if inserting the additional instructions caused
      // a change in the amount of switch instruction padding bytes.
      // If so, we may need to update the corresponding stackmap.
      modify_stack_maps_for_switches(new_end, il);
    } else {
      print_stack_map_table("replace_inst_with_inst_list B");
      // We are inserting more than one instruction.
      // Get the start and end handles of the new instruction list.
      new_end = new_il.getEnd();
      new_start = il.insert(ih, new_il);
      il.setPositions();

      // Just in case there is a switch instruction in new_il we need
      // to recalculate new_length as the padding may have changed.
      new_length = new_end.getNext().getPosition() - new_start.getPosition();

      // Move all of the branches from the old instruction to the new start
      il.redirectBranches(ih, new_start);

      print_il(new_end, "replace_inst #1");

      // Move other targets to the new instuctions.
      if (ih.hasTargeters()) {
        for (InstructionTargeter it : ih.getTargeters()) {
          if (it instanceof LineNumberGen) {
            it.updateTarget(ih, new_start);
          } else if (it instanceof LocalVariableGen) {
            it.updateTarget(ih, new_end);
          } else if (it instanceof CodeExceptionGen) {
            CodeExceptionGen exc = (CodeExceptionGen) it;
            if (exc.getStartPC() == ih) exc.updateTarget(ih, new_start);
            else if (exc.getEndPC() == ih) exc.updateTarget(ih, new_end);
            else if (exc.getHandlerPC() == ih) exc.setHandlerPC(new_start);
            else System.out.printf("Malformed CodeException: %s%n", exc);
          } else {
            System.out.printf("unexpected target %s%n", it);
          }
        }
      }

      print_il(new_end, "replace_inst #2");

      // Remove the old handle.  There should be no targeters left to it.
      try {
        il.delete(ih);
      } catch (Exception e) {
        System.out.printf("Can't delete instruction: %s at %s%n", mg.getClassName(), mg.getName());
        throw new Error("Can't delete instruction", e);
      }
      // Need to update instruction address due to delete above.
      il.setPositions();

      print_il(new_end, "replace_inst #3");

      if (needStackMap) {
        // Before we look for branches in the inserted code we need
        // to update any existing stack maps for locations in the old
        // code that are after the inserted code.
        update_stack_map_offset(new_start.getPosition(), (new_length - old_length));

        // We need to see if inserting the additional instructions caused
        // a change in the amount of switch instruction padding bytes.
        // If so, we may need to update the corresponding stackmap.
        modify_stack_maps_for_switches(new_end, il);
        print_stack_map_table("replace_inst_with_inst_list C");

        // Look for branches within the new il; i.e., both the source
        // and target must be within the new il.  If we find any, the
        // target will need a stack map entry.
        // This situation is caused by a call to "instrument_object_call".
        InstructionHandle nih = new_start;
        int target_count = 0;
        int target_offsets[] = new int[2]; // see note below for why '2'

        // Any targeters on the first instruction will be from 'outside'
        // the new il so we start with the second instruction. (We already
        // know there is more than one instruction in the new il.)
        nih = nih.getNext();

        // We assume there is more code after the new il insertion point
        // so this getNext will not fail.
        new_end = new_end.getNext();
        while (nih != new_end) {
          if (nih.hasTargeters()) {
            for (InstructionTargeter it : nih.getTargeters()) {
              if (it instanceof BranchInstruction) {
                target_offsets[target_count++] = nih.getPosition();
                debug_instrument.log("New branch target: %s %n", nih);
              }
            }
          }
          nih = nih.getNext();
        }

        print_il(new_end, "replace_inst #4");

        if (target_count != 0) {
          // Currently, target_count is always 2; but code is
          // written to allow more.
          int cur_loc = new_start.getPosition();
          int orig_size = stack_map_table.length;
          StackMapEntry[] new_stack_map_table = new StackMapEntry[orig_size + target_count];

          // Calculate the operand stack value(s) for revised code.
          mg.setMaxStack();
          OperandStack stack;
          StackTypes stack_types = bcel_calc_stack_types(mg);
          if (stack_types == null) {
            Error e =
                new Error(
                    String.format(
                        "bcel_calc_stack_types failure in %s.%s%n",
                        mg.getClassName(), mg.getName()));
            e.printStackTrace();
            throw e;
          }

          // Find last stack map entry prior to first new branch target;
          // returns -1 if there isn't one. Also sets running_offset and number_active_locals.
          // The '+1' below means new_index points to the first stack map entry after our
          // inserted code.  There may not be one; in which case new_index == orig_size.
          int new_index = find_stack_map_index_before(target_offsets[0]) + 1;

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
          StackMapType[] local_map_types = calculate_live_local_types(mg, cur_loc);
          int local_map_index = local_map_types.length;

          // local_map_index now contains the number of live locals.
          // number_active_locals has been calculated from the existing StackMap.
          // If these two are equal, we should be ok.
          int number_extra_locals = local_map_index - number_active_locals;
          // lets do a sanity check
          assert number_extra_locals >= 0
              : "invalid extra locals count: " + number_active_locals + ", " + local_map_index;

          // Copy any existing stack maps prior to inserted code.
          System.arraycopy(stack_map_table, 0, new_stack_map_table, 0, new_index);

          boolean need_full_maps = false;
          for (int i = 0; i < target_count; i++) {
            stack = stack_types.get(target_offsets[i]);
            debug_instrument.log("stack: %s %n", stack);

            if (number_extra_locals == 0 && stack.size() == 1 && !need_full_maps) {
              // the simple case
              StackMapType stack_map_type0 = generate_StackMapType_from_Type(stack.peek(0));
              StackMapType[] stack_map_types0 = {stack_map_type0};
              new_stack_map_table[new_index + i] =
                  new StackMapEntry(
                      Const.SAME_LOCALS_1_STACK_ITEM_FRAME,
                      0, // byte_code_offset set below
                      null,
                      stack_map_types0,
                      pool.getConstantPool());
            } else {
              // need a FULL_FRAME stack map entry
              need_full_maps = true;
              new_stack_map_table[new_index + i] =
                  new StackMapEntry(
                      Const.FULL_FRAME,
                      0, // byte_code_offset set below
                      calculate_live_local_types(mg, target_offsets[i]),
                      calculate_live_stack_types(stack),
                      pool.getConstantPool());
            }
            // now set the offset from the previous Stack Map entry to our new one.
            new_stack_map_table[new_index + i].updateByteCodeOffset(
                target_offsets[i] - (running_offset + 1));
            running_offset = target_offsets[i];
          }

          // now copy remaining 'old' stack maps
          int remainder = orig_size - new_index;
          if (remainder > 0) {
            // before we copy, we need to update first map after insert
            l1:
            while (nih != null) {
              if (nih.hasTargeters()) {
                for (InstructionTargeter it : nih.getTargeters()) {
                  if (it instanceof BranchInstruction) {
                    stack_map_table[new_index].updateByteCodeOffset(
                        nih.getPosition()
                            - target_offsets[target_count - 1]
                            - 1
                            - stack_map_table[new_index].getByteCodeOffset());
                    break l1;
                  } else if (it instanceof CodeExceptionGen) {
                    CodeExceptionGen exc = (CodeExceptionGen) it;
                    if (exc.getHandlerPC() == nih) {
                      stack_map_table[new_index].updateByteCodeOffset(
                          nih.getPosition()
                              - target_offsets[target_count - 1]
                              - 1
                              - stack_map_table[new_index].getByteCodeOffset());
                      break l1;
                    }
                  }
                }
              }
              nih = nih.getNext();
            }

            // Now we can copy the remaining stack map entries.
            if (need_full_maps) {
              // Must convert all remaining stack map entries to FULL.
              while (remainder > 0) {
                int stack_map_offset = stack_map_table[new_index].getByteCodeOffset();
                running_offset = running_offset + stack_map_offset + 1;
                stack = stack_types.get(running_offset);
                // System.out.printf("running_offset: %d, stack: %s%n", running_offset, stack);
                new_stack_map_table[new_index + target_count] =
                    new StackMapEntry(
                        Const.FULL_FRAME,
                        stack_map_offset,
                        calculate_live_local_types(mg, running_offset),
                        calculate_live_stack_types(stack),
                        pool.getConstantPool());
                new_index++;
                remainder--;
              }
            } else {
              System.arraycopy(
                  stack_map_table,
                  new_index,
                  new_stack_map_table,
                  new_index + target_count,
                  remainder);
            }
          }
          stack_map_table = new_stack_map_table;
        }
      }
    }

    debug_instrument.log("%n");
    print_il(new_end, "replace_inst #5");
  }
}
