package org.plumelib.bcelutil;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.StackMap;
import org.apache.bcel.classfile.StackMapEntry;
import org.apache.bcel.classfile.StackMapType;
import org.apache.bcel.generic.BasicType;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.IINC;
import org.apache.bcel.generic.IndexedInstruction;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.LocalVariableInstruction;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.RET;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.Type;
import org.apache.bcel.verifier.VerificationResult;
import org.apache.bcel.verifier.structurals.OperandStack;
import org.checkerframework.checker.index.qual.IndexOrLow;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.interning.qual.InternedDistinct;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.checker.signature.qual.ClassGetName;
import org.checkerframework.dataflow.qual.Pure;

/**
 * This class provides utility methods to maintain and modify a method's StackMapTable within a Java
 * class file. It can be thought of as an extension to BCEL.
 *
 * <p>BCEL ought to automatically build and maintain the StackMapTable in a manner similar to the
 * LineNumberTable and the LocalVariableTable. However, for historical reasons, it does not.
 *
 * <p>This class cannot be a set of static methods (like {@link BcelUtil}) as it maintains state
 * during the client's processing of a method that must be available on a per thread basis. Thus it
 * is an abstract class extended by {@link org.plumelib.bcelutil.InstructionListUtils}. A client
 * would not normally extend this class directly.
 */
@SuppressWarnings("nullness")
public abstract class StackMapUtils {

  /** Create a new StackMapUtils object. */
  public StackMapUtils() {
    // Nothing to do.
  }

  /*
   * NOMENCLATURE
   *
   * 'index' is an item's subscript into a data structure.
   *
   * 'offset' is used to describe two different address types:
   *   * the offset of a byte code from the start of a method's byte codes
   *   * the offset of a variable from the start of a method's stack frame
   *
   *     The Java Virtual Machine Specification uses
   *     'index into the local variable array of the current frame'
   *     or 'slot number' to describe this second case.
   *
   * Unfortunately, BCEL uses the method names getIndex and setIndex
   * to refer to 'offset's into the local stack frame.
   * It uses getPosition and setPosition to refer to 'offset's into
   * the byte codes.
   */

  /**
   * The pool for the method currently being processed. Must be set by the client. See the sample
   * code in {@link InstructionListUtils} for when and how to set this value.
   */
  protected @Nullable ConstantPoolGen pool = null;

  /** A log to which to print debugging information about program instrumentation. */
  protected SimpleLog debugInstrument = new SimpleLog(false);

  /** Whether or not the current method needs a StackMap; set by setCurrentStackMapTable. */
  protected boolean needStackMap = false;

  /** Working copy of StackMapTable; set by setCurrentStackMapTable. */
  protected StackMapEntry @Nullable [] stackMapTable = null;

  /** Original stack map table attribute; set by setCurrentStackMapTable. */
  protected @Nullable StackMap smta = null;

  /** Initial state of StackMapTypes for locals on method entry. */
  protected StackMapType @MonotonicNonNull [] initialTypeList;

  /** The number of local variables in the current method prior to any modifications. */
  protected int initialLocalsCount;

  /**
   * A number of methods in this class search and locate a particular StackMap within the current
   * method. This variable contains the number of live local variables within the range of byte code
   * instructions covered by this StackMap. Set by updateStackMapOffset, findStackMapEqual,
   * findStackMapIndexBefore, or findStackMapIndexAfter.
   */
  protected @NonNegative int numberActiveLocals;

  /**
   * Offset into code that corresponds to the current StackMap of interest. Set by
   * findStackMapIndexBefore.
   */
  protected int runningOffset;

  /**
   * The index of the first 'true' local in the local variable table. That is, after 'this' and any
   * parameters.
   */
  protected int firstLocalIndex;

  /** An empty StackMap used for initialization. */
  @SuppressWarnings("interning") // @InternedDistinct initalization with fresh object
  private StackMapEntry @InternedDistinct [] emptyStackmaptable = {};

  /**
   * A map from instructions that create uninitialized NEW objects to the corresponding StackMap
   * entry. Set by buildUninitializedNewMap.
   */
  private Map<InstructionHandle, Integer> uninitializedNewMap = new HashMap<>();

  /**
   * Returns a String array with newString added to the end of arr.
   *
   * @param arr original string array
   * @param newString string to be added
   * @return the new string array
   */
  protected String[] addString(String[] arr, String newString) {
    String[] newArr = new String[arr.length + 1];
    for (int ii = 0; ii < arr.length; ii++) {
      newArr[ii] = arr[ii];
    }
    newArr[arr.length] = newString;
    return newArr;
  }

  /**
   * Return the attribute name for the specified attribute.
   *
   * @param a the attribute
   * @return the attribute name for the specified attribute
   */
  @Pure
  protected final String get_attribute_name(Attribute a) {
    int conIndex = a.getNameIndex();
    Constant c = pool.getConstant(conIndex);
    String attName = ((ConstantUtf8) c).getBytes();
    return attName;
  }

  /**
   * Returns whether or not the specified attribute is a LocalVariableTypeTable.
   *
   * @param a the attribute
   * @return true iff the attribute is a LocalVariableTypeTable
   */
  @Pure
  protected final boolean is_local_variable_type_table(Attribute a) {
    return get_attribute_name(a).equals("LocalVariableTypeTable");
  }

  /**
   * Returns whether or not the specified attribute is a StackMapTable.
   *
   * @param a the attribute
   * @return true iff the attribute is a StackMapTable
   */
  @Pure
  protected final boolean isStackMapTable(Attribute a) {
    return get_attribute_name(a).equals("StackMapTable");
  }

  /**
   * Find the StackMapTable attribute for a method. Return null if there isn't one.
   *
   * @param mgen the method
   * @return the StackMapTable attribute for the method (or null if not present)
   */
  @Pure
  protected final @Nullable Attribute getStackMapTable_attribute(MethodGen mgen) {
    for (Attribute a : mgen.getCodeAttributes()) {
      if (isStackMapTable(a)) {
        return a;
      }
    }
    return null;
  }

  /**
   * Find the LocalVariableTypeTable attribute for a method. Return null if there isn't one.
   *
   * @param mgen the method
   * @return the LocalVariableTypeTable attribute for the method (or null if not present)
   */
  @Pure
  protected final @Nullable Attribute get_local_variable_type_table_attribute(MethodGen mgen) {
    for (Attribute a : mgen.getCodeAttributes()) {
      if (is_local_variable_type_table(a)) {
        return a;
      }
    }
    return null;
  }

  /**
   * Remove the local variable type table attribute (LVTT) from mgen. Some instrumentation changes
   * require this to be updated, but without BCEL support that would be hard to do. It should be
   * safe to just delete it since it is optional and really only of use to a debugger.
   *
   * @param mgen the method to clear out
   */
  protected final void remove_local_variable_type_table(MethodGen mgen) {
    mgen.removeLocalVariableTypeTable();
  }

  /**
   * We have inserted additional byte(s) into the instruction list; update the StackMaps, if
   * required. Also sets runningOffset.
   *
   * @param position the location of insertion
   * @param delta the size of the insertion
   */
  protected final void updateStackMapOffset(int position, int delta) {

    runningOffset = -1; // no +1 on first entry
    for (int i = 0; i < stackMapTable.length; i++) {
      runningOffset = stackMapTable[i].getByteCodeOffset() + runningOffset + 1;

      if (runningOffset > position) {
        stackMapTable[i].updateByteCodeOffset(delta);
        // Only update the first StackMap that occurs after the given
        // offset as map offsets are relative to previous map entry.
        return;
      }
    }
  }

  /**
   * Find the StackMap entry whose offset matches the input argument. Also sets runningOffset.
   *
   * @param offset byte code offset
   * @return the corresponding StackMapEntry
   */
  protected final StackMapEntry findStackMapEqual(int offset) {

    runningOffset = -1; // no +1 on first entry
    for (int i = 0; i < stackMapTable.length; i++) {
      runningOffset = stackMapTable[i].getByteCodeOffset() + runningOffset + 1;

      if (runningOffset > offset) {
        throw new RuntimeException("Invalid StackMap offset 1");
      }

      if (runningOffset == offset) {
        return stackMapTable[i];
      }
      // try next map entry
    }

    // no offset matched
    throw new RuntimeException("Invalid StackMap offset 2");
  }

  /**
   * Find the index of the StackMap entry whose offset is the last one before the input argument.
   * Return -1 if there isn't one. Also sets runningOffset and numberActiveLocals.
   *
   * @param offset byte code offset
   * @return the corresponding StackMapEntry index
   */
  protected final int findStackMapIndexBefore(int offset) {

    numberActiveLocals = initialLocalsCount;
    runningOffset = -1; // no +1 on first entry
    for (int i = 0; i < stackMapTable.length; i++) {
      runningOffset = runningOffset + stackMapTable[i].getByteCodeOffset() + 1;
      if (runningOffset >= offset) {
        if (i == 0) {
          // reset offset to previous
          runningOffset = -1;
          return -1;
        } else {
          // back up offset to previous
          runningOffset = runningOffset - stackMapTable[i].getByteCodeOffset() - 1;
          // return previous
          return i - 1;
        }
      }

      // Update number of active locals based on this StackMap entry.
      int frameType = stackMapTable[i].getFrameType();
      if (frameType >= Const.APPEND_FRAME && frameType <= Const.APPEND_FRAME_MAX) {
        numberActiveLocals += frameType - 251;
      } else if (frameType >= Const.CHOP_FRAME && frameType <= Const.CHOP_FRAME_MAX) {
        numberActiveLocals -= 251 - frameType;
      } else if (frameType == Const.FULL_FRAME) {
        numberActiveLocals = stackMapTable[i].getNumberOfLocals();
      }
      // All other frameTypes do not modify locals.
    }

    if (stackMapTable.length == 0) {
      return -1;
    } else {
      return stackMapTable.length - 1;
    }
  }

  /**
   * Find the index of the StackMap entry whose offset is the first one after the input argument.
   * Return -1 if there isn't one. Also sets runningOffset.
   *
   * @param offset byte code offset
   * @return the corresponding StackMapEntry index
   */
  protected final @IndexOrLow("stackMapTable") int findStackMapIndexAfter(int offset) {

    runningOffset = -1; // no +1 on first entry
    for (int i = 0; i < stackMapTable.length; i++) {
      runningOffset = runningOffset + stackMapTable[i].getByteCodeOffset() + 1;
      if (runningOffset > offset) {
        return i;
      }
      // try next map entry
    }

    // no such entry found
    return -1;
  }

  /**
   * Check to see if (due to some instruction modifications) there have been any changes in a switch
   * statement's padding bytes. If so, then update the corresponding StackMap to reflect this
   * change.
   *
   * @param ih where to start looking for a switch instruction
   * @param il instruction list to search
   */
  protected final void modifyStackMapsForSwitches(InstructionHandle ih, InstructionList il) {
    Instruction inst;
    short opcode;

    if (!needStackMap) {
      return;
    }

    // Make sure all instruction offsets are uptodate.
    il.setPositions();

    // Loop through each instruction looking for a switch
    while (ih != null) {
      inst = ih.getInstruction();
      opcode = inst.getOpcode();

      if (opcode == Const.TABLESWITCH || opcode == Const.LOOKUPSWITCH) {
        int currentOffset = ih.getPosition();
        int index = findStackMapIndexAfter(currentOffset);
        if (index == -1) {
          throw new RuntimeException("Invalid StackMap offset 3");
        }
        StackMapEntry stackMap = stackMapTable[index];
        int delta = (currentOffset + inst.getLength()) - runningOffset;
        if (delta != 0) {
          stackMap.updateByteCodeOffset(delta);
        }
        // Since StackMap offsets are relative to the previous one
        // we only have to do the first one after a switch.
        // But we do need to look at all the switch instructions.
      }

      // Go on to the next instruction in the list
      ih = ih.getNext();
    }
  }

  // TODO: From the documentation, I am not sure what this method does or when it should be called.
  /**
   * We need to locate and remember any NEW instructions that create uninitialized objects. Their
   * offset may be contained in a StackMap entry and will probably need to be updated as we add
   * instrumentation code. Note that these instructions are fairly rare.
   *
   * @param il instruction list to search
   */
  protected final void buildUninitializedNewMap(InstructionList il) {

    uninitializedNewMap.clear();
    il.setPositions();

    for (StackMapEntry smte : stackMapTable) {
      int frameType = smte.getFrameType();

      if ((frameType >= Const.SAME_LOCALS_1_STACK_ITEM_FRAME
              && frameType <= Const.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED)
          || (frameType >= Const.APPEND_FRAME && frameType <= Const.APPEND_FRAME_MAX)
          || (frameType == Const.FULL_FRAME)) {

        if (smte.getNumberOfLocals() > 0) {
          for (StackMapType smt : smte.getTypesOfLocals()) {
            if (smt.getType() == Const.ITEM_NewObject) {
              int i = smt.getIndex();
              uninitializedNewMap.put(il.findHandle(i), i);
            }
          }
        }
        if (smte.getNumberOfStackItems() > 0) {
          for (StackMapType smt : smte.getTypesOfStackItems()) {
            if (smt.getType() == Const.ITEM_NewObject) {
              int i = smt.getIndex();
              uninitializedNewMap.put(il.findHandle(i), i);
            }
          }
        }
      }
    }
  }

  /**
   * One of uninitialized NEW instructions has moved. Update its offset in StackMap entries. Note
   * that more than one entry could refer to the same instruction. This is a helper routine used by
   * updateUninitializedNewOffsets.
   *
   * @param oldOffset original location of NEW instruction
   * @param newOffset new location of NEW instruction
   */
  private final void updateNewObjectStackMapEntries(int oldOffset, int newOffset) {

    for (StackMapEntry smte : stackMapTable) {
      int frameType = smte.getFrameType();

      if ((frameType >= Const.SAME_LOCALS_1_STACK_ITEM_FRAME
              && frameType <= Const.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED)
          || (frameType >= Const.APPEND_FRAME && frameType <= Const.APPEND_FRAME_MAX)
          || (frameType == Const.FULL_FRAME)) {

        if (smte.getNumberOfLocals() > 0) {
          for (StackMapType smt : smte.getTypesOfLocals()) {
            if (smt.getType() == Const.ITEM_NewObject) {
              if (oldOffset == smt.getIndex()) {
                smt.setIndex(newOffset);
              }
            }
          }
        }
        if (smte.getNumberOfStackItems() > 0) {
          for (StackMapType smt : smte.getTypesOfStackItems()) {
            if (smt.getType() == Const.ITEM_NewObject) {
              if (oldOffset == smt.getIndex()) {
                smt.setIndex(newOffset);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Check to see if any of the uninitialized NEW instructions have moved. Again, these are rare, so
   * a linear pass is fine.
   *
   * @param il instruction list to search
   */
  protected final void updateUninitializedNewOffsets(InstructionList il) {

    il.setPositions();

    for (Map.Entry<InstructionHandle, Integer> e : uninitializedNewMap.entrySet()) {
      InstructionHandle ih = e.getKey();
      int oldOffset = e.getValue().intValue();
      int newOffset = ih.getPosition();
      if (oldOffset != newOffset) {
        updateNewObjectStackMapEntries(oldOffset, newOffset);
        e.setValue(newOffset);
      }
    }
  }

  /**
   * Process the instruction list, adding size (1 or 2) to the index of each Instruction that
   * references a local that is equal or higher in the local map than indexFirstMovedlocal. Size
   * should be the size of the new local that was just inserted at indexFirstMovedlocal.
   *
   * @param mgen MethodGen to be modified
   * @param indexFirstMovedlocal original index of first local moved "up"
   * @param size size of new local added (1 or 2)
   */
  protected final void adjust_code_for_locals_change(
      MethodGen mgen, int indexFirstMovedlocal, int size) {

    InstructionList il = mgen.getInstructionList();
    for (InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
      Instruction inst = ih.getInstruction();
      int origLength = inst.getLength();
      int operand;

      if ((inst instanceof RET) || (inst instanceof IINC)) {
        IndexedInstruction indexInst = (IndexedInstruction) inst;
        if (indexInst.getIndex() >= indexFirstMovedlocal) {
          indexInst.setIndex(indexInst.getIndex() + size);
        }
      } else if (inst instanceof LocalVariableInstruction) {
        // BCEL handles all the details of which opcode and if index
        // is implicit or explicit; also, and if needs to be WIDE.
        operand = ((LocalVariableInstruction) inst).getIndex();
        if (operand >= indexFirstMovedlocal) {
          ((LocalVariableInstruction) inst).setIndex(operand + size);
        }
      }
      // Unfortunately, BCEL doesn't take care of incrementing the
      // offset within StackMapEntrys.
      int delta = inst.getLength() - origLength;
      if (delta > 0) {
        il.setPositions();
        updateStackMapOffset(ih.getPosition(), delta);
        modifyStackMapsForSwitches(ih, il);
      }
    }
  }

  /**
   * Get existing StackMapTable from the MethodGen argument. If there is none, create a new empty
   * one. Sets both smta and stackMapTable. Must be called prior to any other methods that
   * manipulate the stackMapTable!
   *
   * @param mgen MethodGen to search
   * @param javaClassVersion Java version for the classfile; stackMapTable is optional before Java
   *     1.7 (= classfile version 51)
   */
  @EnsuresNonNull({"stackMapTable"})
  protected final void setCurrentStackMapTable(MethodGen mgen, int javaClassVersion) {

    needStackMap = false;
    smta = (StackMap) getStackMapTable_attribute(mgen);
    if (smta != null) {
      // get a deep copy of the original StackMapTable.
      stackMapTable = ((StackMap) smta.copy(smta.getConstantPool())).getStackMap();
      needStackMap = true;

      debugInstrument.log(
          "Attribute tag: %s length: %d nameIndex: %d%n",
          smta.getTag(), smta.getLength(), smta.getNameIndex());
      // Delete existing stack map - we'll add a new one later.
      mgen.removeCodeAttribute(smta);
    } else {
      stackMapTable = emptyStackmaptable;
      if (javaClassVersion > Const.MAJOR_1_6) {
        needStackMap = true;
      }
    }
    printStackMapTable("Original");
  }

  /**
   * Print the contents of the StackMapTable to the debugInstrument.log.
   *
   * @param prefix label to display with table
   */
  protected final void printStackMapTable(String prefix) {

    debugInstrument.log("%nStackMap(%s) %s items:%n", prefix, stackMapTable.length);
    runningOffset = -1; // no +1 on first entry
    for (int i = 0; i < stackMapTable.length; i++) {
      runningOffset = stackMapTable[i].getByteCodeOffset() + runningOffset + 1;
      debugInstrument.log("@%03d %s %n", runningOffset, stackMapTable[i]);
    }
  }

  /**
   * Create a new StackMap code attribute from stackMapTable.
   *
   * @param mgen MethodGen to add attribute to
   * @throws IOException if cannot create the attribute
   */
  protected final void createNewStackMapAttribute(MethodGen mgen) throws IOException {

    if (!needStackMap) {
      return;
    }
    if (stackMapTable == emptyStackmaptable) {
      return;
    }
    printStackMapTable("Final");

    // Build new StackMapTable attribute
    StackMap mapTable =
        new StackMap(pool.addUtf8("StackMapTable"), 0, null, pool.getConstantPool());
    mapTable.setStackMap(stackMapTable);
    mgen.addCodeAttribute(mapTable);
  }

  /**
   * Convert a Type name to a Class name.
   *
   * @param t type whose name is to be converted
   * @return a String containing the class name
   */
  @SuppressWarnings("signature") // conversion routine
  protected static @ClassGetName String typeToClassGetName(Type t) {

    if (t instanceof ObjectType) {
      return ((ObjectType) t).getClassName();
    } else if (t instanceof BasicType) {
      // Use reserved keyword for basic type rather than signature to
      // avoid conflicts with user defined types.
      return t.toString();
    } else {
      // Array type: just convert '/' to '.'
      return t.getSignature().replace('/', '.');
    }
  }

  /**
   * Convert a Type to a StackMapType.
   *
   * @param t Type to be converted
   * @return result StackMapType
   */
  protected final StackMapType generateStackMapTypeFromType(Type t) {

    switch (t.getType()) {
      case Const.T_BOOLEAN:
      case Const.T_CHAR:
      case Const.T_BYTE:
      case Const.T_SHORT:
      case Const.T_INT:
        return new StackMapType(Const.ITEM_Integer, -1, pool.getConstantPool());
      case Const.T_FLOAT:
        return new StackMapType(Const.ITEM_Float, -1, pool.getConstantPool());
      case Const.T_DOUBLE:
        return new StackMapType(Const.ITEM_Double, -1, pool.getConstantPool());
      case Const.T_LONG:
        return new StackMapType(Const.ITEM_Long, -1, pool.getConstantPool());
      case Const.T_ARRAY:
      case Const.T_OBJECT:
        return new StackMapType(
            Const.ITEM_Object, pool.addClass(typeToClassGetName(t)), pool.getConstantPool());
        // UNKNOWN seems to be used for Uninitialized objects.
        // The second argument to the constructor should be the code offset
        // of the corresponding 'new' instruction.  Just using 0 for now.
      case Const.T_UNKNOWN:
        return new StackMapType(Const.ITEM_NewObject, 0, pool.getConstantPool());
      default:
        throw new RuntimeException("Invalid type: " + t + t.getType());
    }
  }

  /**
   * Convert a StackMapType to a Type.
   *
   * @param smt StackMapType to be converted
   * @return result Type
   */
  protected final Type generate_Type_from_StackMapType(StackMapType smt) {

    switch (smt.getType()) {
      case Const.ITEM_Bogus: // 'top' (undefined) in JVM verification nomenclature
      case Const.ITEM_Null: // no idea what this means, but Groovy generates it (mlr)
        return null;
      case Const.ITEM_Integer:
        return Type.INT;
      case Const.ITEM_Float:
        return Type.FLOAT;
      case Const.ITEM_Double:
        return Type.DOUBLE;
      case Const.ITEM_Long:
        return Type.LONG;
      case Const.ITEM_Object:
        Constant c = pool.getConstantPool().getConstant(smt.getIndex());
        @SuppressWarnings("signature") // ConstantPool CONSTANT_Class entry is a ClassName
        @BinaryName String className = ((ConstantClass) c).getBytes(pool.getConstantPool());
        if (className.charAt(0) == '[') {
          // special case, className is descriptor of array type
          return Type.getType(className);
        } else {
          return new ObjectType(className);
        }
      default:
        Thread.dumpStack();
        assert false : "Invalid StackMapType: " + smt + smt.getType();
        throw new RuntimeException("Invalid StackMapType: " + smt + smt.getType());
    }
  }

  /**
   * Return the operand size of this type (2 for long and double, 1 otherwise).
   *
   * @param smt a StackMapType object
   * @return the operand size of this type
   */
  protected final int getSize(StackMapType smt) {
    switch (smt.getType()) {
      case Const.ITEM_Double:
      case Const.ITEM_Long:
        return 2;
      default:
        return 1;
    }
  }

  /**
   * Update any FULL_FRAME StackMap entries to include a new local var. The locals array is a copy
   * of the local variables PRIOR to the addition of the new local in question.
   *
   * @param offset offset into stack of the new variable we are adding
   * @param typeNewVar type of new variable we are adding
   * @param locals a copy of the local variable table prior to this modification
   */
  protected final void update_full_frameStackMap_entries(
      int offset, Type typeNewVar, LocalVariableGen[] locals) {
    @NonNegative int index; // locals index

    for (int i = 0; i < stackMapTable.length; i++) {
      if (stackMapTable[i].getFrameType() == Const.FULL_FRAME) {

        int numLocals = stackMapTable[i].getNumberOfLocals();
        StackMapType[] newLocalTypes = new StackMapType[numLocals + 1];
        StackMapType[] oldLocalTypes = stackMapTable[i].getTypesOfLocals();

        // System.out.printf ("update_full_frame %s %s %s %n", offset, numLocals, locals.length);

        for (index = 0; index < numLocals; index++) {
          if (index >= locals.length) {
            // there are hidden compiler temps in map
            break;
          }
          if (locals[index].getIndex() >= offset) {
            // we've reached the point of insertion
            break;
          }
          newLocalTypes[index] = oldLocalTypes[index];
        }
        newLocalTypes[index++] = generateStackMapTypeFromType(typeNewVar);
        while (index <= numLocals) {
          newLocalTypes[index] = oldLocalTypes[index - 1];
          index++;
        }

        stackMapTable[i].setTypesOfLocals(newLocalTypes);
      }
    }
  }

  /**
   * Add a new parameter to the method. This will be added after last current parameter and before
   * the first local variable. This might have the side effect of causing us to rewrite the method
   * byte codes to adjust the offsets for the local variables - see below for details.
   *
   * <p>Must call fixLocalVariableTable (just once per method) before calling this routine.
   *
   * @param mgen MethodGen to be modified
   * @param argName name of new parameter
   * @param argType type of new parameter
   * @return a LocalVariableGen for the new parameter
   */
  protected final LocalVariableGen addNewParameter(MethodGen mgen, String argName, Type argType) {
    // We add a new parameter, after any current ones, and then
    // we need to make a pass over the byte codes to update the local
    // offset values of all the locals we just shifted up.  This may have
    // a 'knock on' effect if we are forced to change an instruction that
    // references implict local #3 to an instruction with an explict
    // reference to local #4 as this would require the insertion of an
    // offset into the byte codes. This means we would need to make an
    // additional pass to update branch targets (no - BCEL does this for
    // us) and the StackMapTable (yes - BCEL should do this, but it doesn't).
    //

    LocalVariableGen argNew = null;
    // get a copy of the locals before modification
    LocalVariableGen[] locals = mgen.getLocalVariables();
    Type[] argTypes = mgen.getArgumentTypes();
    int newIndex = 0;
    int newOffset = 0;

    boolean hasCode = (mgen.getInstructionList() != null);

    if (hasCode) {
      if (!mgen.isStatic()) {
        // Skip the 'this' pointer.
        newIndex++;
        newOffset++; // size of 'this' is 1
      }

      if (argTypes.length > 0) {
        LocalVariableGen lastArg;
        newIndex = newIndex + argTypes.length;
        // newIndex is now positive, because argTypes.length is
        lastArg = locals[newIndex - 1];
        newOffset = lastArg.getIndex() + lastArg.getType().getSize();
      }

      // Insert our new local variable into existing table at 'newOffset'.
      argNew = mgen.addLocalVariable(argName, argType, newOffset, null, null);

      // Update the index of the first 'true' local in the local variable table.
      firstLocalIndex++;
    }
    initialLocalsCount++;

    // Update the method's parameter information.
    argTypes = BcelUtil.postpendToArray(argTypes, argType);
    String[] argNames = addString(mgen.getArgumentNames(), argName);
    mgen.setArgumentTypes(argTypes);
    mgen.setArgumentNames(argNames);

    if (hasCode) {
      // we need to adjust the offset of any locals after our insertion
      for (int i = newIndex; i < locals.length; i++) {
        LocalVariableGen lv = locals[i];
        lv.setIndex(lv.getIndex() + argType.getSize());
      }
      mgen.setMaxLocals(mgen.getMaxLocals() + argType.getSize());

      debugInstrument.log(
          "Added arg    %s%n",
          argNew.getIndex() + ": " + argNew.getName() + ", " + argNew.getType());

      // Now process the instruction list, adding one to the offset
      // within each LocalVariableInstruction that references a
      // local that is 'higher' in the local map than new local
      // we just inserted.
      adjust_code_for_locals_change(mgen, newOffset, argType.getSize());

      // Finally, we need to update any FULL_FRAME StackMap entries to
      // add in the new local variable type.
      update_full_frameStackMap_entries(newOffset, argType, locals);

      debugInstrument.log("New LocalVariableTable:%n%s%n", mgen.getLocalVariableTable(pool));
    }
    return argNew;
  }

  /**
   * Create a new local with a scope of the full method. This means we need to search the existing
   * locals to find the proper index for our new local. This might have the side effect of causing
   * us to rewrite the method byte codes to adjust the offsets for the existing local variables -
   * see below for details.
   *
   * <p>Must call fixLocalVariableTable (just once per method) before calling this routine.
   *
   * @param mgen MethodGen to be modified
   * @param localName name of new local
   * @param localType type of new local
   * @return a LocalVariableGen for the new local
   */
  protected final LocalVariableGen create_method_scope_local(
      MethodGen mgen, String localName, Type localType) {
    // BCEL sorts local vars and presents them in offset order.  Search
    // locals for first var with start != 0. If none, just add the new
    // var at the end of the table and exit. Otherwise, insert the new
    // var just prior to the local we just found.
    // Now we need to make a pass over the byte codes to update the local
    // offset values of all the locals we just shifted up.  This may have
    // a 'knock on' effect if we are forced to change an instruction that
    // references implict local #3 to an instruction with an explict
    // reference to local #4 as this would require the insertion of an
    // offset into the byte codes. This means we would need to make an
    // additional pass to update branch targets (no - BCEL does this for
    // us) and the StackMapTable (yes - BCEL should do this, but it doesn't).
    //
    // We never want to insert our local prior to any parameters.  This would
    // happen naturally, but some old class files have non zero addresses
    // for 'this' and/or the parameters so we need to add an explicit
    // check to make sure we skip these variables.

    LocalVariableGen lvNew;
    int maxOffset = 0;
    int newOffset = -1;
    // get a copy of the local before modification
    LocalVariableGen[] locals = mgen.getLocalVariables();
    @IndexOrLow("locals") int compilerTempI = -1;
    int newIndex = -1;
    int i;

    for (i = 0; i < locals.length; i++) {
      LocalVariableGen lv = locals[i];
      if (i >= firstLocalIndex) {
        if (lv.getStart().getPosition() != 0) {
          if (newOffset == -1) {
            if (compilerTempI != -1) {
              newOffset = locals[compilerTempI].getIndex();
              newIndex = compilerTempI;
            } else {
              newOffset = lv.getIndex();
              newIndex = i;
            }
          }
        }
      }

      // calculate max local size seen so far (+1)
      maxOffset = lv.getIndex() + lv.getType().getSize();

      if (lv.getName().startsWith("DaIkOnTeMp")) {
        // Remember the index of a compiler temp.  We may wish
        // to insert our new local prior to a temp to simplfy
        // the generation of StackMaps.
        if (compilerTempI == -1) {
          compilerTempI = i;
        }
      } else {
        // If there were compiler temps prior to this local, we don't
        // need to worry about them as the compiler will have already
        // included them in the StackMaps. Reset the indicators.
        compilerTempI = -1;
      }
    }

    // We have looked at all the local variables; if we have not found
    // a place for our new local, check to see if last local was a
    // compiler temp and insert prior.
    if ((newOffset == -1) && (compilerTempI != -1)) {
      newOffset = locals[compilerTempI].getIndex();
      newIndex = compilerTempI;
    }

    // If newOffset is still unset, we can just add our local at the
    // end.  There may be unnamed compiler temps here, so we need to
    // check for this (via maxOffset) and move them up.
    if (newOffset == -1) {
      newOffset = maxOffset;
      if (newOffset < mgen.getMaxLocals()) {
        mgen.setMaxLocals(mgen.getMaxLocals() + localType.getSize());
      }
      lvNew = mgen.addLocalVariable(localName, localType, newOffset, null, null);
    } else {
      // insert our new local variable into existing table at 'newOffset'
      lvNew = mgen.addLocalVariable(localName, localType, newOffset, null, null);
      // we need to adjust the offset of any locals after our insertion
      for (i = newIndex; i < locals.length; i++) {
        LocalVariableGen lv = locals[i];
        lv.setIndex(lv.getIndex() + localType.getSize());
      }
      mgen.setMaxLocals(mgen.getMaxLocals() + localType.getSize());
    }

    debugInstrument.log(
        "Added local  %s%n", lvNew.getIndex() + ": " + lvNew.getName() + ", " + lvNew.getType());

    // Now process the instruction list, adding one to the offset
    // within each LocalVariableInstruction that references a
    // local that is 'higher' in the local map than new local
    // we just inserted.
    adjust_code_for_locals_change(mgen, newOffset, localType.getSize());

    // Finally, we need to update any FULL_FRAME StackMap entries to
    // add in the new local variable type.
    update_full_frameStackMap_entries(newOffset, localType, locals);

    debugInstrument.log("New LocalVariableTable:%n%s%n", mgen.getLocalVariableTable(pool));
    return lvNew;
  }

  // //////////////////////////////////////////////////////////////////////
  // fixLocalVariableTable
  //

  // The rest of the code in this file is "fixLocalVariableTable" and the methods it calls,
  // directly or indirectly. The following five variables are used by this code to contain the live
  // range, type and size of a local variable while it is being processed.

  /** The start of a local variable's live range: the first instruction in the range. */
  protected InstructionHandle liveRangeStart = null;

  /** The end of a local variable's live range: the first instruction after the range. */
  protected InstructionHandle liveRangeEnd = null;

  /** The type of a local variable during its live range. */
  protected Type liveRangeType = null;

  /** The storage size of local variable during its live range. */
  protected int liveRangeOperandSize = 0;

  /** The types of elements on the operand stack for current method. */
  protected StackTypes stackTypes = null;

  /**
   * Under some circumstances, there may be gaps in the LocalVariable table. These gaps occur when
   * the Java compiler adds unnamed parameters and/or unnamed local variables. A gap may also occur
   * for a local variable declared in the source whose lifetime does not cross a StackMap location.
   * This routine creates LocalVariable entries for these missing items.
   *
   * <ol>
   *   <li>The java Compiler allocates a hidden parameter for the constructor of an inner class.
   *       These items are given the name $hidden$ appended with their offset.
   *   <li>The Java compiler allocates unnamed local temps for:
   *       <ul>
   *         <li>saving the exception in a finally clause
   *         <li>the lock for a synchronized block
   *         <li>interators
   *         <li>user declared locals that never appear in a StackMap
   *         <li>(others?)
   *       </ul>
   *       These items are given the name DaIkOnTeMp appended with their offset.
   * </ol>
   *
   * @param mgen MethodGen to be modified
   */
  @EnsuresNonNull("initialTypeList")
  protected final void fixLocalVariableTable(MethodGen mgen) {
    InstructionList il = mgen.getInstructionList();
    if (il == null) {
      // no code so nothing to do
      firstLocalIndex = 0;
      return;
    }

    // Get the current local variables (includes 'this' and parameters)
    LocalVariableGen[] locals = mgen.getLocalVariables();
    LocalVariableGen l;
    LocalVariableGen newLvg;

    // We need a deep copy
    for (int ii = 0; ii < locals.length; ii++) {
      locals[ii] = (LocalVariableGen) locals[ii].clone();
    }

    // The arg types are correct and include all parameters.
    final Type[] argTypes = mgen.getArgumentTypes();

    // Initial offset into the stack frame
    int offset = 0;

    // Index into locals of the first parameter
    int locIndex = 0;

    // Rarely, the java compiler gets the max locals count wrong (too big).
    // This would cause problems for us later, so we need to recalculate
    // the highest local used based on looking at code offsets.
    mgen.setMaxLocals();
    final int maxLocals = mgen.getMaxLocals();
    // Remove the existing locals
    mgen.removeLocalVariables();
    // Reset MaxLocals to 0 and let code below rebuild it.
    mgen.setMaxLocals(0);

    // Determine the first 'true' local index into the local variables.
    // The object 'this' pointer and the parameters form the first n
    // entries in the list.
    firstLocalIndex = argTypes.length;

    if (!mgen.isStatic()) {
      // Add the 'this' pointer back in.
      l = locals[0];
      newLvg = mgen.addLocalVariable(l.getName(), l.getType(), l.getIndex(), null, null);
      debugInstrument.log(
          "Added <this> %s%n",
          newLvg.getIndex() + ": " + newLvg.getName() + ", " + newLvg.getType());
      locIndex = 1;
      offset = 1;
      firstLocalIndex++;
    } else {
      // The java method sun/misc/ProxyGenerator generates proxy classes at run time.  For some
      // unknown reason when it generates code for <clinit> it allocates local 0 but never uses it.
      if (mgen.getClassName().startsWith("com.sun.proxy.") && mgen.getName().equals("<clinit>")) {
        newLvg = mgen.addLocalVariable("$clinit$hidden$" + offset, Type.INT, offset, null, null);
        debugInstrument.log(
            "Added hidden proxy local  %s%n",
            newLvg.getIndex() + ": " + newLvg.getName() + ", " + newLvg.getType());
        offset = 1;
        firstLocalIndex++;
      }
    }

    // Loop through each parameter
    for (int ii = 0; ii < argTypes.length; ii++) {

      // If this parameter doesn't have a matching local
      if ((locIndex >= locals.length) || (offset != locals[locIndex].getIndex())) {

        // Create a local variable to describe the missing parameter
        newLvg = mgen.addLocalVariable("$hidden$" + offset, argTypes[ii], offset, null, null);
      } else {
        l = locals[locIndex];
        newLvg = mgen.addLocalVariable(l.getName(), l.getType(), l.getIndex(), null, null);
        locIndex++;
      }
      debugInstrument.log(
          "Added param  %s%n",
          newLvg.getIndex() + ": " + newLvg.getName() + ", " + newLvg.getType());
      offset += argTypes[ii].getSize();
    }

    // At this point the LocalVaraibles contain:
    //   the 'this' pointer (if present)
    //   the parameters to the method
    // This will be used to construct the initial state of the
    // StackMapTypes.
    LocalVariableGen[] initialLocals = mgen.getLocalVariables();
    initialLocalsCount = initialLocals.length;
    initialTypeList = new StackMapType[initialLocalsCount];
    for (int ii = 0; ii < initialLocalsCount; ii++) {
      initialTypeList[ii] = generateStackMapTypeFromType(initialLocals[ii].getType());
    }

    // Add back the true locals
    //
    // NOTE that the Java compiler uses unnamed local temps for:
    //   saving the exception in a finally clause
    //   the lock for a synchronized block
    //   iterators
    //   (others?)
    // Also, the javac compiler does not make a LocalVariable for user declared
    // locals that never appear in a StackMap.
    // We will create a 'fake' local for these cases.

    // set stack operand types to unknown
    stackTypes = null;

    for (int ii = firstLocalIndex; ii < locals.length; ii++) {
      l = locals[ii];
      if (l.getIndex() > offset) {
        // A gap in index values indicates a compiler allocated temp.
        // (if offset is 0, probably a lock object)
        // there is at least one hidden compiler temp before the next local
        offset = gen_locals(mgen, offset);
        ii--; // need to revisit same local
      } else {
        newLvg =
            mgen.addLocalVariable(l.getName(), l.getType(), l.getIndex(), l.getStart(), l.getEnd());
        debugInstrument.log(
            "Added local  %s%n",
            newLvg.getIndex() + ": " + newLvg.getName() + ", " + newLvg.getType());
        offset = newLvg.getIndex() + newLvg.getType().getSize();
      }
    }

    // Check after last declared local for any compiler temps and/or user declared
    // locals not currently in the LocalVariables table.
    while (offset < maxLocals) {
      offset = gen_locals(mgen, offset);
    }

    // Recalculate the highest local used based on looking at code offsets.
    mgen.setMaxLocals();
  }

  /**
   * Find the live range of the compiler temp(s) and/or user declared local(s) at the given offset
   * and create a LocalVariableGen for each. Note the compiler might generate temps of different
   * sizes at the same offset (must have disjoint lifetimes). In general, these variables will not
   * have a live range of the entire method. We try to calculate the true live range so if, at some
   * later point, we need to generate a new StackMap we can include the correct list of active
   * locals.
   *
   * @param mgen the method
   * @param offset compiler assigned local offset of hidden temp(s) or local(s)
   * @return offset incremented by size of smallest variable found at offset
   */
  @RequiresNonNull("initialTypeList")
  protected final int gen_locals(MethodGen mgen, int offset) {
    int liveStart = 0;
    Type liveType = null;
    InstructionList il = mgen.getInstructionList();
    il.setPositions();

    // Set up inital state of StackMap info on entry to method.
    int localsOffsetHeight = 0;
    int byteCodeOffset = -1;
    LocalVariableGen newLvg;
    int minSize = 3; // only sizes are 1 or 2; start with something larger.

    numberActiveLocals = initialLocalsCount;
    StackMapType[] typesOfActiveLocals = new StackMapType[numberActiveLocals];
    for (int ii = 0; ii < numberActiveLocals; ii++) {
      typesOfActiveLocals[ii] = initialTypeList[ii];
      localsOffsetHeight += getSize(initialTypeList[ii]);
    }

    // update state for each StackMap entry
    for (StackMapEntry smte : stackMapTable) {
      int frameType = smte.getFrameType();
      byteCodeOffset += smte.getByteCodeOffset() + 1;

      if (frameType >= Const.APPEND_FRAME && frameType <= Const.APPEND_FRAME_MAX) {
        // number to append is frameType - 251
        typesOfActiveLocals =
            Arrays.copyOf(typesOfActiveLocals, numberActiveLocals + frameType - 251);
        for (StackMapType smt : smte.getTypesOfLocals()) {
          typesOfActiveLocals[numberActiveLocals++] = smt;
          localsOffsetHeight += getSize(smt);
        }
      } else if (frameType >= Const.CHOP_FRAME && frameType <= Const.CHOP_FRAME_MAX) {
        int numberToChop = 251 - frameType;
        while (numberToChop > 0) {
          localsOffsetHeight -= getSize(typesOfActiveLocals[--numberActiveLocals]);
          numberToChop--;
        }
        typesOfActiveLocals = Arrays.copyOf(typesOfActiveLocals, numberActiveLocals);
      } else if (frameType == Const.FULL_FRAME) {
        localsOffsetHeight = 0;
        numberActiveLocals = 0;
        typesOfActiveLocals = new StackMapType[smte.getNumberOfLocals()];
        for (StackMapType smt : smte.getTypesOfLocals()) {
          typesOfActiveLocals[numberActiveLocals++] = smt;
          localsOffsetHeight += getSize(smt);
        }
      }
      // all other frameTypes do not modify locals.

      // System.out.printf("byteCodeOffset: %d, temp offset: %d, localsOffsetHeight: %d,
      // numberActiveLocals: %d, local types: %s%n",
      //       byteCodeOffset, offset, localsOffsetHeight, numberActiveLocals,
      // Arrays.toString(typesOfActiveLocals));

      // System.out.printf ("offset: %d, bco: %d, lstart: %d, ltype: %s, loh: %d%n",
      // offset, byteCodeOffset, liveStart, liveType, localsOffsetHeight);

      if (liveStart == 0) {
        // did the latest StackMap entry define the temp or local in question?
        if (offset < localsOffsetHeight) {
          liveStart = byteCodeOffset;
          int runningOffset = 0;
          for (StackMapType smt : typesOfActiveLocals) {
            if (runningOffset == offset) {
              liveType = generate_Type_from_StackMapType(smt);
              break;
            }
            runningOffset += getSize(smt);
          }
          if (liveType == null) {
            // No matching offset in stack maps or StackMapType was Bogus (Top).
            // Just skip to next stack map.
            liveStart = 0;
          }
        }
      } else {
        // did the latest StackMap entry undefine the temp or local in question?
        if (offset >= localsOffsetHeight) {
          // create a LocalVariable
          newLvg =
              mgen.addLocalVariable(
                  "DaIkOnTeMp" + offset,
                  liveType,
                  offset,
                  il.findHandle(liveStart),
                  il.findHandle(byteCodeOffset));
          debugInstrument.log(
              "Added local  %s, %d, %d : %s, %s%n",
              newLvg.getIndex(),
              newLvg.getStart().getPosition(),
              newLvg.getEnd().getPosition(),
              newLvg.getName(),
              newLvg.getType());
          minSize = Math.min(minSize, liveType.getSize());
          // reset to look for more temps or locals at same offset
          liveStart = 0;
          liveType = null;
        }
      }
      // go on to next StackMap entry
    }

    // System.out.printf ("end of stack maps%n");
    // System.out.printf ("offset: %d, bco: %d, lstart: %d, ltype: %s, loh: %d%n",
    // offset, byteCodeOffset, liveStart, liveType, localsOffsetHeight);

    // we are done with stack maps; need to see if there is a temp or local still active
    if (liveStart != 0) {
      // must find end of live range and create the LocalVariable
      liveRangeStart = il.findHandle(liveStart);
      liveRangeEnd = liveRangeStart; // not necessarily true, but only needs to be !null
      liveRangeType = liveType;
      liveRangeOperandSize = minSize;
      minSize = gen_locals_from_byte_codes(mgen, offset, il.findHandle(byteCodeOffset));
    } else {
      if (minSize == 3) {
        // We did not find the offset in any of the stack maps; that must mean
        // the live range is in between two stack maps or after the last stack map.
        // We need to scan all the byte codes to calculate the live range and type.
        minSize = gen_locals_from_byte_codes(mgen, offset);
        // offset is never mentioned in code; go on to next location
        if (minSize == 3) {
          return offset + 1;
        }
      }
    }
    return offset + minSize;
  }

  /**
   * Calculate the live range of a local variable (or variables).
   *
   * @param mgen MethodGen of method to search
   * @param offset offset of the local
   * @return minimum size of local(s) found at offset
   */
  protected final int gen_locals_from_byte_codes(MethodGen mgen, int offset) {
    // The same local offset could be used for multiple local variables
    // with disjoint lifetimes.  We attempt to deal with this by looking
    // at the type of a store instruction and if it does not equal the
    // current type we create a temp from the current live range data
    // and then reset to start a new live range.

    // reset globals
    liveRangeStart = null;
    liveRangeEnd = null;
    liveRangeType = null;
    // only sizes are 1 or 2; start with something larger.
    liveRangeOperandSize = 3;
    return gen_locals_from_byte_codes(mgen, offset, mgen.getInstructionList().getStart());
  }

  /**
   * Calculate the live range of a local variable starting from the given InstructionHandle. The
   * following live_range globals must be set:
   *
   * <ul>
   *   <li>liveRangeStart
   *   <li>liveRangeEnd
   *   <li>liveRangeType
   *   <li>liveRangeOperandSize
   * </ul>
   *
   * @param mgen MethodGen of method to search
   * @param offset offset of the local
   * @param start search forward from this instruction
   * @return minimum size of local(s) found at offset
   */
  protected final int gen_locals_from_byte_codes(
      MethodGen mgen, int offset, InstructionHandle start) {
    OperandStack stack;
    set_method_stackTypes(mgen);
    InstructionList il = mgen.getInstructionList();
    for (InstructionHandle ih = start; ih != null; ih = ih.getNext()) {
      Instruction inst = ih.getInstruction();

      debugInstrument.log(
          "gen_locals_from_byte_codes for offset: %d :: position: %d, inst: %s%n",
          offset, ih.getPosition(), inst);

      if (inst instanceof StoreInstruction) {
        if (offset != ((LocalVariableInstruction) inst).getIndex()) {
          continue;
        }
        stack = stackTypes.get(ih.getPosition());
        // get type of item about to be stored
        Type tos = stack.peek(0);
        // System.out.printf ("tos: %s, liveType: %s%n", tos, liveRangeType);
        // Store of a null does not change type.
        // UNDONE: if tos is subclass of liveRangeType, should not start new range
        if (liveRangeStart == null || (!tos.equals(Type.NULL) && !tos.equals(liveRangeType))) {
          // close current live range
          create_local_from_live_range(mgen, offset);
          // start a new live range
          liveRangeType = tos;
          liveRangeStart = ih.getNext();
        }
        // update liveRangeEnd
        liveRangeEnd = ih.getNext();

      } else if (inst instanceof IINC) {
        if (offset != ((IndexedInstruction) inst).getIndex()) {
          continue;
        }
        if (liveRangeType == null) {
          throw new RuntimeException("gen_locals_from_byte_code: no store before IINC");
        } else if (liveRangeType != Type.INT) {
          throw new RuntimeException("gen_locals_from_byte_code: IINC operand not type int");
        }
        // update liveRangeEnd
        liveRangeEnd = ih.getNext();

      } else if (inst instanceof RET) {
        if (offset != ((IndexedInstruction) inst).getIndex()) {
          continue;
        }
        if (liveRangeType == null) {
          throw new RuntimeException("gen_locals_from_byte_code: no store before RET");
        } else if (liveRangeType.getType() != Const.T_ADDRESS) {
          throw new RuntimeException(
              "gen_locals_from_byte_code: RET operand not type returnAddress");
        }
        // update liveRangeEnd
        liveRangeEnd = ih.getNext();

      } else if (inst instanceof LoadInstruction) {
        if (offset != ((LocalVariableInstruction) inst).getIndex()) {
          continue;
        }
        stack = stackTypes.get(ih.getPosition() + inst.getLength());
        // get type of item about to be loaded
        Type tos = stack.peek(0);
        if (liveRangeType == null) {
          throw new RuntimeException("gen_locals_from_byte_code: no store before load");
        } else if (!tos.equals(liveRangeType)) {
          // Load type can be super class of store type.  Rather than write code
          // using reflection to verify, we just assume compiler got it right.
          // throw new RuntimeException("gen_locals_from_byte_code: store/load types do not match");
        }
        // update liveRangeEnd
        liveRangeEnd = ih.getNext();
      }
    }
    // If we've reached the end of the method without seeing the end of the live range, we set it to
    // be the end of the method. Note that there may not be an active live_range but that will be
    // checked in create_local_from_live_range.
    if (liveRangeEnd == null) {
      liveRangeEnd = il.getEnd();
    }
    // close current live range
    create_local_from_live_range(mgen, offset);

    return liveRangeOperandSize;
  }

  /**
   * Create a new LocalVariable from the live_range data. Does nothing if {@link #liveRangeStart} is
   * null.
   *
   * @param mgen MethodGen of method to search
   * @param offset offset of the local
   */
  protected final void create_local_from_live_range(MethodGen mgen, int offset) {
    if (liveRangeStart == null) {
      return;
    }
    // Type.getType doesn't understand NULL which is the type of the top of operand stack
    // after the JVM aconst_null instruction.
    if (Type.NULL.equals(liveRangeType)) {
      liveRangeType = Type.OBJECT;
    }
    // convert returnAddress to Object
    if (liveRangeType.getType() == Const.T_ADDRESS) {
      liveRangeType = Type.OBJECT;
    }
    LocalVariableGen newLvg =
        mgen.addLocalVariable(
            "DaIkOnTeMp" + offset, liveRangeType, offset, liveRangeStart, liveRangeEnd);
    debugInstrument.log(
        "Added local  %s, %d, %d : %s, %s%n",
        newLvg.getIndex(),
        newLvg.getStart().getPosition(),
        newLvg.getEnd().getPosition(),
        newLvg.getName(),
        newLvg.getType());
    liveRangeOperandSize = Math.min(liveRangeOperandSize, liveRangeType.getSize());
  }

  /**
   * Calculates the stack types for each byte code offset of the current method, and stores them in
   * variable {@link #stackTypes}. Does nothing if {@link #stackTypes} is already set.
   *
   * @param mgen MethodGen of method whose stack types to compute
   */
  protected final void set_method_stackTypes(MethodGen mgen) {
    // We cache the stack types for the current method.
    // fixLocalVariableTable sets stackTypes to null at the start of each method.
    if (stackTypes == null) {
      // bcelCalcStackTypes needs MaxLocals set properly.
      mgen.setMaxLocals();
      stackTypes = bcelCalcStackTypes(mgen);
      if (stackTypes == null) {
        Error e =
            new Error(
                String.format(
                    "bcelCalcStackTypes failure in %s.%s%n", mgen.getClassName(), mgen.getName()));
        e.printStackTrace();
        throw e;
      }
    }
  }

  /**
   * Calculates the types on the stack for each instruction using the BCEL stack verification
   * routines.
   *
   * @param mg MethodGen for the method to be analyzed
   * @return a StackTypes object for the method
   */
  protected final StackTypes bcelCalcStackTypes(MethodGen mg) {

    StackVer stackver = new StackVer();
    VerificationResult vr;
    try {
      vr = stackver.do_stack_ver(mg);
    } catch (Throwable t) {
      System.out.printf("Warning: StackVer exception for %s.%s%n", mg.getClassName(), mg.getName());
      System.out.printf("Exception: %s%n", t);
      System.out.printf("Method is NOT instrumented%n");
      return null;
    }
    if (vr != VerificationResult.VR_OK) {
      System.out.printf(
          "Warning: StackVer failed for %s.%s: %s%n", mg.getClassName(), mg.getName(), vr);
      System.out.printf("Method is NOT instrumented%n");
      return null;
    }
    return stackver.get_stackTypes();
  }

  //
  // end of fixLocalVariableTable section of file
  //

}
