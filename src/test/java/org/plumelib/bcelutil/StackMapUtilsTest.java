package org.plumelib.bcelutil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.plumelib.bcelutil.Fixtures.methodGen;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.StackMapType;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;
import org.junit.jupiter.api.Test;

/**
 * Tests for the type-conversion and attribute helpers in {@link StackMapUtils}. StackMapUtils is
 * abstract and its members are protected, so the tests reach them through {@link Subject}.
 */
final class StackMapUtilsTest {

  /** Creates a StackMapUtilsTest. */
  StackMapUtilsTest() {}

  /** Gives the tests an instance of the abstract class under test. */
  private static final class Subject extends StackMapUtils {
    /** Creates a Subject. */
    Subject() {}
  }

  /** The object under test. */
  private final Subject subject = new Subject();

  /**
   * Points {@link #subject} at the constant pool of the fixture class's method with the given name,
   * and returns that method.
   *
   * @param name the name of the method to return
   * @return a MethodGen for the fixture class's method named {@code name}
   */
  private MethodGen useMethod(String name) {
    MethodGen mg = methodGen(name);
    subject.pool = mg.getConstantPool();
    return mg;
  }

  // addString

  @Test
  void addStringAppendsToTheEnd() {
    assertArrayEquals(new String[] {"a"}, subject.addString(new String[0], "a"));
    assertArrayEquals(
        new String[] {"a", "b", "c"}, subject.addString(new String[] {"a", "b"}, "c"));
  }

  @Test
  void addStringDoesNotModifyItsArgument() {
    String[] original = {"a", "b"};
    subject.addString(original, "c");
    assertArrayEquals(new String[] {"a", "b"}, original);
  }

  // typeToClassGetName

  @Test
  void typeToClassGetNameConvertsPrimitives() {
    assertEquals("int", StackMapUtils.typeToClassGetName(Type.INT));
    assertEquals("boolean", StackMapUtils.typeToClassGetName(Type.BOOLEAN));
    assertEquals("double", StackMapUtils.typeToClassGetName(Type.DOUBLE));
    assertEquals("void", StackMapUtils.typeToClassGetName(Type.VOID));
  }

  @Test
  void typeToClassGetNameConvertsObjectTypes() {
    assertEquals("java.lang.String", StackMapUtils.typeToClassGetName(Type.STRING));
    assertEquals(
        "java.util.Map$Entry",
        StackMapUtils.typeToClassGetName(new ObjectType("java.util.Map$Entry")));
  }

  @Test
  void typeToClassGetNameConvertsArrayTypesToDottedDescriptors() {
    assertEquals("[I", StackMapUtils.typeToClassGetName(new ArrayType(Type.INT, 1)));
    assertEquals("[[I", StackMapUtils.typeToClassGetName(new ArrayType(Type.INT, 2)));
    // The signature's slashes become dots.
    assertEquals(
        "[Ljava.lang.String;", StackMapUtils.typeToClassGetName(new ArrayType(Type.STRING, 1)));
  }

  // Type <-> StackMapType

  @Test
  void integralTypesBecomeItemInteger() {
    for (Type t : new Type[] {Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT}) {
      StackMapType smt = subject.generateStackMapTypeFromType(t);
      assertEquals(Const.ITEM_Integer, smt.getType(), "for " + t);
      // All of these are int in the JVM's verification type system.
      assertEquals(Type.INT, subject.generate_Type_from_StackMapType(smt), "for " + t);
    }
  }

  @Test
  void floatingPointAndLongTypesRoundTrip() {
    for (Type t : new Type[] {Type.FLOAT, Type.DOUBLE, Type.LONG}) {
      StackMapType smt = subject.generateStackMapTypeFromType(t);
      assertEquals(t, subject.generate_Type_from_StackMapType(smt), "for " + t);
    }
  }

  @Test
  void objectTypesRoundTrip() {
    StackMapType smt = subject.generateStackMapTypeFromType(Type.STRING);
    assertEquals(Const.ITEM_Object, smt.getType());
    assertEquals(Type.STRING, subject.generate_Type_from_StackMapType(smt));
  }

  @Test
  void arrayTypesRoundTrip() {
    ArrayType intArray = new ArrayType(Type.INT, 2);
    StackMapType smt = subject.generateStackMapTypeFromType(intArray);
    assertEquals(Const.ITEM_Object, smt.getType());
    assertEquals(intArray, subject.generate_Type_from_StackMapType(smt));

    ArrayType stringArray = new ArrayType(Type.STRING, 1);
    assertEquals(
        stringArray,
        subject.generate_Type_from_StackMapType(subject.generateStackMapTypeFromType(stringArray)));
  }

  @Test
  void voidHasNoStackMapType() {
    assertThrows(RuntimeException.class, () -> subject.generateStackMapTypeFromType(Type.VOID));
  }

  @Test
  void itemBogusBecomesNull() {
    // "ITEM_Bogus" is 'top' (undefined) in the JVM's verification nomenclature.
    StackMapType bogus = new StackMapType(Const.ITEM_Bogus, -1, subject.pool.getConstantPool());
    assertNull(subject.generate_Type_from_StackMapType(bogus));
  }

  // getSize

  @Test
  void longAndDoubleOccupyTwoSlots() {
    assertEquals(2, subject.getSize(subject.generateStackMapTypeFromType(Type.LONG)));
    assertEquals(2, subject.getSize(subject.generateStackMapTypeFromType(Type.DOUBLE)));
  }

  @Test
  void otherTypesOccupyOneSlot() {
    assertEquals(1, subject.getSize(subject.generateStackMapTypeFromType(Type.INT)));
    assertEquals(1, subject.getSize(subject.generateStackMapTypeFromType(Type.FLOAT)));
    assertEquals(1, subject.getSize(subject.generateStackMapTypeFromType(Type.STRING)));
    assertEquals(
        1, subject.getSize(subject.generateStackMapTypeFromType(new ArrayType(Type.INT, 1))));
  }

  // Attribute helpers

  @Test
  void stackMapTableIsFoundOnAMethodWithBranches() {
    // "sum" contains a loop, so javac emits a StackMapTable for it.
    Attribute found =
        Fixtures.nonNull(
            subject.getStackMapTable_attribute(useMethod("sum")), "sum's StackMapTable attribute");
    assertEquals("StackMapTable", subject.get_attribute_name(found));
  }

  @Test
  void stackMapTableIsAbsentFromAStraightLineMethod() {
    // "getValue" has no branches, so it needs no StackMapTable.
    assertNull(subject.getStackMapTable_attribute(useMethod("getValue")));
  }

  @Test
  void attributeNameDeterminesTheAttributePredicates() {
    MethodGen mg = useMethod("sum");
    int examined = 0;
    for (Attribute a : mg.getCodeAttributes()) {
      String name = subject.get_attribute_name(a);
      assertFalse(name.isEmpty(), "an attribute name should not be empty");
      assertEquals(name.equals("LocalVariableTypeTable"), subject.is_local_variable_type_table(a));
      assertEquals(name.equals("StackMapTable"), subject.isStackMapTable(a));
      examined++;
    }
    assertEquals(mg.getCodeAttributes().length, examined);
  }

  @Test
  void removeLocalVariableTypeTableLeavesOtherAttributes() {
    MethodGen mg = useMethod("sum");
    subject.remove_local_variable_type_table(mg);
    for (Attribute a : mg.getCodeAttributes()) {
      assertFalse(subject.is_local_variable_type_table(a));
    }
  }
}
