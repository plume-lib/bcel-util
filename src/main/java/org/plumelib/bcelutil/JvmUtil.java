package org.plumelib.bcelutil;

import java.util.HashMap;
import java.util.StringTokenizer;
import org.checkerframework.checker.index.qual.Positive;
import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.checker.signature.qual.ClassGetName;
import org.checkerframework.checker.signature.qual.FieldDescriptor;
import org.checkerframework.checker.signature.qual.PrimitiveType;

/**
 * Utility functions for working with the JVM.
 *
 * <p>Currently contains conversion utilities between Java and JVM string formats, for types and
 * signatures.
 *
 * @deprecated Use <a
 *     href="http://plumelib.org/reflection-util/api/org/plumelib/reflection/Signatures.html">org.plumelib.reflection.Signatures</a>
 */
@Deprecated
public final class JvmUtil {

  /** Do not instantiate. */
  private JvmUtil() {
    throw new Error("do not instantiate");
  }

  /** A map from Java primitive type name (such as "int") to field descriptor (such as "I"). */
  private static HashMap<@PrimitiveType String, @FieldDescriptor String>
      primitiveToFieldDescriptor = new HashMap<>(8);

  static {
    primitiveToFieldDescriptor.put("boolean", "Z");
    primitiveToFieldDescriptor.put("byte", "B");
    primitiveToFieldDescriptor.put("char", "C");
    primitiveToFieldDescriptor.put("double", "D");
    primitiveToFieldDescriptor.put("float", "F");
    primitiveToFieldDescriptor.put("int", "I");
    primitiveToFieldDescriptor.put("long", "J");
    primitiveToFieldDescriptor.put("short", "S");
  }

  /**
   * Convert a binary name to a field descriptor. For example, convert "java.lang.Object[]" to
   * "[Ljava/lang/Object;" or "int" to "I" or "pkg.Outer$Inner" to "Lpkg/Outer$Inner;".
   *
   * <p>There are no binary names for primitives or array types. Nonetheless, this method works for
   * them. It converts "java.lang.Object[]" to "[Ljava/lang/Object;" or "int" to "I".
   *
   * @param classname name of the class, in binary class name format
   * @return name of the class, in field descriptor format
   */
  @SuppressWarnings("signature") // conversion routine
  public static @FieldDescriptor String binaryNameToFieldDescriptor(@BinaryName String classname) {
    int dimensions = 0;
    String sansArray = classname;
    while (sansArray.endsWith("[]")) {
      dimensions++;
      sansArray = sansArray.substring(0, sansArray.length() - 2);
    }
    String result = primitiveToFieldDescriptor.get(sansArray);
    if (result == null) {
      result = "L" + sansArray + ";";
    }
    for (int i = 0; i < dimensions; i++) {
      result = "[" + result;
    }
    return result.replace('.', '/');
  }

  /**
   * Convert a primitive Java type name (e.g., "int", "double", etc.) to a field descriptor (e.g.,
   * "I", "D", etc.).
   *
   * @param primitiveName name of the type, in Java format
   * @return name of the type, in field descriptor format
   * @throws IllegalArgumentException if primitiveName is not a valid primitive type name
   */
  public static @FieldDescriptor String primitiveTypeNameToFieldDescriptor(String primitiveName) {
    String result = primitiveToFieldDescriptor.get(primitiveName);
    if (result == null) {
      throw new IllegalArgumentException("Not the name of a primitive type: " + primitiveName);
    }
    return result;
  }

  /**
   * Convert from a BinaryName to the format of {@link Class#getName()}.
   *
   * <p>There are no binary names for primitives or array types. Nonetheless, this method works for
   * them. It converts "java.lang.Object[]" to "[Ljava.lang.Object;" or "int" to "int".
   *
   * @param bn the binary name to convert
   * @return the class name, in Class.getName format
   */
  @SuppressWarnings("signature") // conversion routine
  public static @ClassGetName String binaryNameToClassGetName(@BinaryName String bn) {
    if (bn.endsWith("[]")) {
      return binaryNameToFieldDescriptor(bn).replace('/', '.');
    } else {
      return bn;
    }
  }

  /**
   * Convert from a FieldDescriptor to the format of {@link Class#getName()}.
   *
   * @param fd the class, in field descriptor format
   * @return the class name, in Class.getName format
   */
  @SuppressWarnings("signature") // conversion routine
  public static @ClassGetName String fieldDescriptorToClassGetName(@FieldDescriptor String fd) {
    if (fd.startsWith("[")) {
      return fd.replace('/', '.');
    } else {
      return fieldDescriptorToBinaryName(fd);
    }
  }

  /**
   * Convert a fully-qualified argument list from Java format to JVML format. For example, convert
   * "(java.lang.Integer[], int, java.lang.Integer[][])" to
   * "([Ljava/lang/Integer;I[[Ljava/lang/Integer;)".
   *
   * @param arglist an argument list, in Java format
   * @return argument list, in JVML format
   */
  public static String arglistToJvm(String arglist) {
    if (!(arglist.startsWith("(") && arglist.endsWith(")"))) {
      throw new Error("Malformed arglist: " + arglist);
    }
    String result = "(";
    String commaSepArgs = arglist.substring(1, arglist.length() - 1);
    StringTokenizer argsTokenizer = new StringTokenizer(commaSepArgs, ",", false);
    while (argsTokenizer.hasMoreTokens()) {
      @SuppressWarnings("signature") // substring
      @BinaryName String arg = argsTokenizer.nextToken().trim();
      result += binaryNameToFieldDescriptor(arg);
    }
    result += ")";
    // System.out.println("arglistToJvm: " + arglist + " => " + result);
    return result;
  }

  /** A map from field descriptor (sach as "I") to Java primitive type (such as "int"). */
  private static HashMap<String, String> fieldDescriptorToPrimitive = new HashMap<>(8);

  static {
    fieldDescriptorToPrimitive.put("Z", "boolean");
    fieldDescriptorToPrimitive.put("B", "byte");
    fieldDescriptorToPrimitive.put("C", "char");
    fieldDescriptorToPrimitive.put("D", "double");
    fieldDescriptorToPrimitive.put("F", "float");
    fieldDescriptorToPrimitive.put("I", "int");
    fieldDescriptorToPrimitive.put("J", "long");
    fieldDescriptorToPrimitive.put("S", "short");
  }

  // does not convert "V" to "void".  Should it?
  /**
   * Convert a field descriptor to a binary name. For example, convert "[Ljava/lang/Object;" to
   * "java.lang.Object[]" or "I" to "int".
   *
   * @param classname name of the type, in JVML format
   * @return name of the type, in Java format
   */
  @SuppressWarnings("signature") // conversion routine
  public static @BinaryName String fieldDescriptorToBinaryName(String classname) {
    if (classname.equals("")) {
      throw new Error("Empty string passed to fieldDescriptorToBinaryName");
    }
    int dimensions = 0;
    while (classname.startsWith("[")) {
      dimensions++;
      classname = classname.substring(1);
    }
    String result;
    if (classname.startsWith("L") && classname.endsWith(";")) {
      result = classname.substring(1, classname.length() - 1);
    } else {
      result = fieldDescriptorToPrimitive.get(classname);
      if (result == null) {
        throw new Error("Malformed base class: " + classname);
      }
    }
    for (int i = 0; i < dimensions; i++) {
      result += "[]";
    }
    return result.replace('/', '.');
  }

  /**
   * Convert an argument list from JVML format to Java format. For example, convert
   * "([Ljava/lang/Integer;I[[Ljava/lang/Integer;)" to "(java.lang.Integer[], int,
   * java.lang.Integer[][])".
   *
   * <p>The argument is the first part of a method descriptor.
   *
   * @param arglist an argument list, in JVML format
   * @return argument list, in Java format
   */
  public static String arglistFromJvm(String arglist) {
    if (!(arglist.startsWith("(") && arglist.endsWith(")"))) {
      throw new Error("Malformed arglist: " + arglist);
    }
    String result = "(";
    @Positive int pos = 1;
    while (pos < arglist.length() - 1) {
      if (pos > 1) {
        result += ", ";
      }
      int nonarrayPos = pos;
      while (arglist.charAt(nonarrayPos) == '[') {
        nonarrayPos++;
        if (nonarrayPos >= arglist.length()) {
          throw new Error("Malformed arglist: " + arglist);
        }
      }
      char c = arglist.charAt(nonarrayPos);
      if (c == 'L') {
        int semicolonPos = arglist.indexOf(';', nonarrayPos);
        if (semicolonPos == -1) {
          throw new Error("Malformed arglist: " + arglist);
        }
        @SuppressWarnings("signature") // string parsing
        @FieldDescriptor String fieldDescriptor = arglist.substring(pos, semicolonPos + 1);
        result += fieldDescriptorToBinaryName(fieldDescriptor);
        pos = semicolonPos + 1;
      } else {
        @SuppressWarnings("signature") // string parsing
        @FieldDescriptor String fieldDescriptor = arglist.substring(pos, nonarrayPos + 1);
        String maybe = fieldDescriptorToBinaryName(fieldDescriptor);
        if (maybe == null) {
          // return null;
          throw new Error("Malformed arglist: " + arglist);
        }
        result += maybe;
        pos = nonarrayPos + 1;
      }
    }
    return result + ")";
  }
}
