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

/*>>>
import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.checker.signature.qual.*;
import org.checkerframework.dataflow.qual.*;
*/

/**
 * BCEL should automatically build and maintain the StackMapTable in a manner similar to the
 * LineNumberTable and the LocalVariableTable. However, for historical reasons it does not. Hence,
 * we provide a set of methods to manipulate BCEL InstructionLists that handle all the StackMap side
 * effects.
 */
@SuppressWarnings("nullness")
public abstract class InstructionListUtils extends StackMapUtils {

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
    if (il == null) return;
    insert_before_handle(mg, il.getStart(), new_il, false);
  }

  /**
   * Inserts a new instruction list into an existing instruction list just prior to the indicated
   * instruction handle. (Which must be a member of the existing instruction list.) If new_il is
   * null, do nothing.
   *
   * @param mg MethodGen containing the instruction handle
   * @param ih InstructionHandle indicating where to insert new code
   * @param new_il InstructionList holding the new code
   * @param redirect_branches flag indicating if branch targets should be moved from ih to new_il
   */
  protected final void insert_before_handle(
      MethodGen mg, InstructionHandle ih, InstructionList new_il, boolean redirect_branches) {

    if (new_il == null) return;

    // Ignore methods with no instructions
    InstructionList il = mg.getInstructionList();
    if (il == null) return;

    new_il.setPositions();
    InstructionHandle new_end = new_il.getEnd();
    int new_length = new_end.getPosition() + new_end.getInstruction().getLength();

    print_stack_map_table("Before insert_inst");
    debug_instrument.log("  insert_inst: %d%n%s%n", new_il.getLength(), new_il);

    // Add the new code in front of the instruction handle.
    InstructionHandle new_start = il.insert(ih, new_il);

    if (redirect_branches) {
      // Move all of the branches from the old instruction to the new start.
      il.redirectBranches(ih, new_start);
    }

    // Move other targets to the new start.
    if (ih.hasTargeters()) {
      for (InstructionTargeter it : ih.getTargeters()) {
        if (it instanceof LineNumberGen) {
          it.updateTarget(ih, new_start);
        } else if (it instanceof LocalVariableGen) {
          it.updateTarget(ih, new_end); //QUESTION: was new_start
        } else if ((it instanceof CodeExceptionGen) && redirect_branches) {
          CodeExceptionGen exc = (CodeExceptionGen) it;
          if (exc.getStartPC() == ih) exc.updateTarget(ih, new_start);
          else if (exc.getEndPC() == ih) exc.updateTarget(ih, new_end);
          else if (exc.getHandlerPC() == ih) exc.setHandlerPC(new_start);
          else System.out.printf("Malformed CodeException: %s%n", exc);
        }
      }
    }

    // Need to update stack map for change in length of instruction bytes.
    il.setPositions();
    // We use position-1 as we want any stack map associated with the
    // ih to stay associated and not move to new_start.
    // QUESTION: is this still true if redirect_branches?
    update_stack_map_offset(new_start.getPosition() - 1, new_length);

    // We need to see if inserting the additional instructions caused
    // a change in the amount of switch instruction padding bytes.
    modify_stack_maps_for_switches(new_start, il);
  }

  /** Convenience function to build an instruction list */
  protected final InstructionList build_il(Instruction... instructions) {
    InstructionList il = new InstructionList();
    for (Instruction inst : instructions) {
      append_inst(il, inst);
    }
    return il;
  }

  /**
   * Compute the StackMapTypes of the live variables of the current method at a specific location
   * within the method. There may be gaps ("Bogus" or non-live slots) so we can't just count the
   * number of live variables, we must find the max index of all the live variables.
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
      MethodGen mg, InstructionList il, InstructionHandle ih, InstructionList new_il) {

    if (new_il == null) return;

    InstructionHandle new_end;
    InstructionHandle new_start;
    int old_length = ih.getInstruction().getLength();

    new_il.setPositions();
    InstructionHandle end = new_il.getEnd();
    int new_length = end.getPosition() + end.getInstruction().getLength();

    print_stack_map_table("Before replace_inst");
    debug_instrument.log("  replace_inst: %s %d%n%s%n", ih, new_il.getLength(), new_il);

    if (debug_instrument.enabled) {
      InstructionHandle tih = ih;
      debug_instrument.log("replace_inst #0 %n");
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

      if (debug_instrument.enabled) {
        InstructionHandle tih = new_end;
        debug_instrument.log("replace_inst #0.5 %n");
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

      // Move all of the branches from the old instruction to the new start
      il.redirectBranches(ih, new_start);

      if (debug_instrument.enabled) {
        InstructionHandle tih = new_end;
        debug_instrument.log("replace_inst #1 %n");
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

      if (debug_instrument.enabled) {
        InstructionHandle tih = new_end;
        debug_instrument.log("replace_inst #2 %n");
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

      // Remove the old handle.  There should be no targeters left to it.
      try {
        il.delete(ih);
      } catch (Exception e) {
        System.out.printf("Can't delete instruction: %s at %s%n", mg.getClassName(), mg.getName());
        throw new Error("Can't delete instruction", e);
      }
      // Need to update instruction address due to delete above.
      il.setPositions();

      if (debug_instrument.enabled) {
        InstructionHandle tih = new_end;
        debug_instrument.log("replace_inst #3 %n");
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

        if (debug_instrument.enabled) {
          InstructionHandle tih = new_end;
          debug_instrument.log("replace_inst #4 %n");
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

        if (target_count != 0) {
          // Currently, target_count is always 2; but code is
          // written to allow more.
          int cur_loc = new_start.getPosition();
          int orig_size = stack_map_table.length;
          StackMapEntry[] new_stack_map_table = new StackMapEntry[orig_size + target_count];

          // Calculate the operand stack value(s) for revised code.
          mg.setMaxStack();
          StackTypes stack_types = bcel_calc_stack_types(mg);
          OperandStack stack;

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
    print_stack_map_table("replace_inst After");
    if (debug_instrument.enabled) {
      debug_instrument.log("replace_inst #5 %n");
      while (new_end != null) {
        debug_instrument.log("inst: %s %n", new_end);
        if (new_end.hasTargeters()) {
          for (InstructionTargeter it : new_end.getTargeters()) {
            debug_instrument.log("targeter: %s %n", it);
          }
        }
        new_end = new_end.getNext();
      }
    }
  }
}
