package org.plumelib.bcelutil;

import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.checker.signature.qual.ClassGetName;
import org.checkerframework.checker.signature.qual.FieldDescriptor;
import org.checkerframework.checker.signature.qual.FullyQualifiedName;
import org.junit.Test;

/*>>>
import org.checkerframework.checker.index.qual.*;
import org.checkerframework.checker.lock.qual.*;
import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.checker.signature.qual.*;
import org.checkerframework.common.value.qual.*;
*/

/** Test code for the bcel-util package. */
@SuppressWarnings({
  "interning", // interning is due to apparent bugs
  "UseCorrectAssertInTests" // I don't see the problem with using `assert`
})
public final class TestJvmUtil {

  // This cannot be static because it instantiates an inner class.
  @SuppressWarnings("ArrayEquals")
  @Test
  public void testJvmUtil() {

    // public static String binaryNameToFieldDescriptor(String classname)
    assert JvmUtil.binaryNameToFieldDescriptor("boolean").equals("Z");
    assert JvmUtil.binaryNameToFieldDescriptor("byte").equals("B");
    assert JvmUtil.binaryNameToFieldDescriptor("char").equals("C");
    assert JvmUtil.binaryNameToFieldDescriptor("double").equals("D");
    assert JvmUtil.binaryNameToFieldDescriptor("float").equals("F");
    assert JvmUtil.binaryNameToFieldDescriptor("int").equals("I");
    assert JvmUtil.binaryNameToFieldDescriptor("long").equals("J");
    assert JvmUtil.binaryNameToFieldDescriptor("short").equals("S");
    assert JvmUtil.binaryNameToFieldDescriptor("Integer").equals("LInteger;");
    assert JvmUtil.binaryNameToFieldDescriptor("Java.lang.Integer").equals("LJava/lang/Integer;");
    assert JvmUtil.binaryNameToFieldDescriptor("int[][]").equals("[[I");
    assert JvmUtil.binaryNameToFieldDescriptor("Java.lang.Integer[][][]")
        .equals("[[[LJava/lang/Integer;");

    // public static @ClassGetName String binaryNameToClassGetName(/*BinaryName*/ String bn)
    assert JvmUtil.binaryNameToClassGetName("boolean").equals("boolean");
    assert JvmUtil.binaryNameToClassGetName("byte").equals("byte");
    assert JvmUtil.binaryNameToClassGetName("char").equals("char");
    assert JvmUtil.binaryNameToClassGetName("double").equals("double");
    assert JvmUtil.binaryNameToClassGetName("float").equals("float");
    assert JvmUtil.binaryNameToClassGetName("int").equals("int");
    assert JvmUtil.binaryNameToClassGetName("long").equals("long");
    assert JvmUtil.binaryNameToClassGetName("short").equals("short");
    assert JvmUtil.binaryNameToClassGetName("Integer").equals("Integer");
    assert JvmUtil.binaryNameToClassGetName("Java.lang.Integer").equals("Java.lang.Integer");
    assert JvmUtil.binaryNameToClassGetName("int[][]").equals("[[I");
    assert JvmUtil.binaryNameToClassGetName("Java.lang.Integer[][][]")
        .equals("[[[LJava.lang.Integer;");

    // public static String arglistToJvm(String arglist)
    assert JvmUtil.arglistToJvm("()").equals("()");
    assert JvmUtil.arglistToJvm("(int)").equals("(I)");
    assert JvmUtil.arglistToJvm("(int, int)").equals("(II)");
    assert JvmUtil.arglistToJvm("(int, long, short)").equals("(IJS)");
    assert JvmUtil.arglistToJvm("(java.lang.Integer, int, java.lang.Integer)")
        .equals("(Ljava/lang/Integer;ILjava/lang/Integer;)");
    assert JvmUtil.arglistToJvm("(int[])").equals("([I)");
    assert JvmUtil.arglistToJvm("(int[], int, int)").equals("([III)");
    assert JvmUtil.arglistToJvm("(int, int[][], int)").equals("(I[[II)");
    assert JvmUtil.arglistToJvm("(java.lang.Integer[], int, java.lang.Integer[][])")
        .equals("([Ljava/lang/Integer;I[[Ljava/lang/Integer;)");

    // public static String fieldDescriptorToBinaryName(String classname)
    assert JvmUtil.fieldDescriptorToBinaryName("Z").equals("boolean");
    assert JvmUtil.fieldDescriptorToBinaryName("B").equals("byte");
    assert JvmUtil.fieldDescriptorToBinaryName("C").equals("char");
    assert JvmUtil.fieldDescriptorToBinaryName("D").equals("double");
    assert JvmUtil.fieldDescriptorToBinaryName("F").equals("float");
    assert JvmUtil.fieldDescriptorToBinaryName("I").equals("int");
    assert JvmUtil.fieldDescriptorToBinaryName("J").equals("long");
    assert JvmUtil.fieldDescriptorToBinaryName("S").equals("short");
    assert JvmUtil.fieldDescriptorToBinaryName("LInteger;").equals("Integer");
    assert JvmUtil.fieldDescriptorToBinaryName("LJava/lang/Integer;").equals("Java.lang.Integer");
    assert JvmUtil.fieldDescriptorToBinaryName("[[I").equals("int[][]");
    assert JvmUtil.fieldDescriptorToBinaryName("[[LJava/lang/Integer;")
        .equals("Java.lang.Integer[][]");

    // public static @ClassGetName String
    //     fieldDescriptorToClassGetName(/*FieldDescriptor*/ String fd)
    assert JvmUtil.fieldDescriptorToClassGetName("Z").equals("boolean");
    assert JvmUtil.fieldDescriptorToClassGetName("B").equals("byte");
    assert JvmUtil.fieldDescriptorToClassGetName("C").equals("char");
    assert JvmUtil.fieldDescriptorToClassGetName("D").equals("double");
    assert JvmUtil.fieldDescriptorToClassGetName("F").equals("float");
    assert JvmUtil.fieldDescriptorToClassGetName("I").equals("int");
    assert JvmUtil.fieldDescriptorToClassGetName("J").equals("long");
    assert JvmUtil.fieldDescriptorToClassGetName("S").equals("short");
    assert JvmUtil.fieldDescriptorToClassGetName("LInteger;").equals("Integer");
    assert JvmUtil.fieldDescriptorToClassGetName("LJava/lang/Integer;").equals("Java.lang.Integer");
    assert JvmUtil.fieldDescriptorToClassGetName("[[I").equals("[[I");
    assert JvmUtil.fieldDescriptorToClassGetName("[[LJava/lang/Integer;")
        .equals("[[LJava.lang.Integer;");

    // public static String arglistFromJvm(String arglist)
    assert JvmUtil.arglistFromJvm("()").equals("()");
    assert JvmUtil.arglistFromJvm("(I)").equals("(int)");
    assert JvmUtil.arglistFromJvm("(II)").equals("(int, int)");
    assert JvmUtil.arglistFromJvm("(IJS)").equals("(int, long, short)");
    assert JvmUtil.arglistFromJvm("(Ljava/lang/Integer;ILjava/lang/Integer;)")
        .equals("(java.lang.Integer, int, java.lang.Integer)");
    assert JvmUtil.arglistFromJvm("([I)").equals("(int[])");
    assert JvmUtil.arglistFromJvm("([III)").equals("(int[], int, int)");
    assert JvmUtil.arglistFromJvm("(I[[II)").equals("(int, int[][], int)");
    assert JvmUtil.arglistFromJvm("([Ljava/lang/Integer;I[[Ljava/lang/Integer;)")
        .equals("(java.lang.Integer[], int, java.lang.Integer[][])");

    // More tests for type representation conversions.
    // Table from Signature Checker manual.
    checkTypeStrings("int", "int", "int", "I");
    checkTypeStrings("int[][]", "int[][]", "[[I", "[[I");
    checkTypeStrings("MyClass", "MyClass", "MyClass", "LMyClass;", true);
    checkTypeStrings("MyClass[]", "MyClass[]", "[LMyClass;", "[LMyClass;", true);
    checkTypeStrings(
        "java.lang.Integer", "java.lang.Integer", "java.lang.Integer", "Ljava/lang/Integer;");
    checkTypeStrings(
        "java.lang.Integer[]",
        "java.lang.Integer[]",
        "[Ljava.lang.Integer;",
        "[Ljava/lang/Integer;");
    checkTypeStrings(
        "java.lang.Byte.ByteCache",
        "java.lang.Byte$ByteCache",
        "java.lang.Byte$ByteCache",
        "Ljava/lang/Byte$ByteCache;");
    checkTypeStrings(
        "java.lang.Byte.ByteCache[]",
        "java.lang.Byte$ByteCache[]",
        "[Ljava.lang.Byte$ByteCache;",
        "[Ljava/lang/Byte$ByteCache;");
  }

  private static void checkTypeStrings(
      @FullyQualifiedName String fqn,
      @BinaryName String bn,
      @ClassGetName String cgn,
      @FieldDescriptor String fd) {
    checkTypeStrings(fqn, bn, cgn, fd, false);
  }

  private static void checkTypeStrings(
      @FullyQualifiedName String fqn,
      @BinaryName String bn,
      @ClassGetName String cgn,
      @FieldDescriptor String fd,
      boolean skipClassForName) {
    if (!skipClassForName) {
      try {
        BcelUtil.classForName(cgn); // ensure this does not crash
      } catch (ClassNotFoundException e) {
        throw new Error(e);
      }
    }
    assert fd.equals(JvmUtil.binaryNameToFieldDescriptor(bn));
    assert cgn.equals(JvmUtil.binaryNameToClassGetName(bn))
        : bn + " => " + JvmUtil.binaryNameToClassGetName(bn) + ", should be " + cgn;
    assert cgn.equals(JvmUtil.fieldDescriptorToClassGetName(fd)) : fd + " => " + cgn;
    assert bn.equals(JvmUtil.fieldDescriptorToBinaryName(fd));
  }
}
