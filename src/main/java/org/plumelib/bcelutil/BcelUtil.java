package org.plumelib.bcelutil;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.LineNumberGen;
import org.apache.bcel.generic.LocalVariableGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.RETURN;
import org.apache.bcel.generic.Type;
import org.checkerframework.checker.index.qual.SameLen;
import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.checker.signature.qual.BinaryNameOrPrimitiveType;
import org.checkerframework.checker.signature.qual.ClassGetName;
import org.checkerframework.checker.signature.qual.FqBinaryName;
import org.checkerframework.checker.signature.qual.InternalForm;
import org.plumelib.reflection.ReflectionPlume;
import org.plumelib.reflection.Signatures;

/** Static utility methods for working with BCEL. */
public final class BcelUtil {
  /** This class is a collection of methods; it does not represent anything. */
  private BcelUtil() {
    throw new Error("do not instantiate");
  }

  /** Controls whether the checks in {@link #checkMgen} are performed. */
  public static boolean skipChecks = false;

  /** The type that represents String[]. */
  private static final Type stringArray = Type.getType("[Ljava.lang.String;");

  /** The major version number of the Java runtime (JRE), such as 8, 11, or 17. */
  public static final int javaVersion = getJavaVersion();

  // Keep in sync with SystemUtil.java (in the Checker Framework).
  /**
   * Returns the major version number from the "java.version" system property, such as 8, 11, or 17.
   *
   * @return the major version of the Java runtime
   */
  private static int getJavaVersion() {
    String version = System.getProperty("java.version");

    // Up to Java 8, from a version string like "1.8.whatever", extract "8".
    if (version.startsWith("1.")) {
      return Integer.parseInt(version.substring(2, 3));
    }

    // Since Java 9, from a version string like "11.0.1" or "11-ea" or "11u25", extract "11".
    // The format is described at http://openjdk.java.net/jeps/223 .
    final Pattern newVersionPattern = Pattern.compile("^(\\d+).*$");
    final Matcher newVersionMatcher = newVersionPattern.matcher(version);
    if (newVersionMatcher.matches()) {
      String v = newVersionMatcher.group(1);
      assert v != null : "@AssumeAssertion(nullness): inspection";
      return Integer.parseInt(v);
    }

    throw new RuntimeException("Could not determine version from property java.version=" + version);
  }

  // 'ToString' methods

  /**
   * Returns a string describing a method declaration. It contains the access flags (public,
   * private, static, etc), the return type, the method name, and the types of each of its
   * parameters.
   *
   * <p>For example, if the original Java source declaration was: private final String
   * constantToString (int index) Then the output of methodDeclarationToString would be: private
   * final java.lang.String constantToString (int)
   *
   * @param m the method
   * @return a string describing the method declaration
   */
  public static String methodDeclarationToString(Method m) {

    StringBuilder sb = new StringBuilder();
    String flags = accessFlagsToString(m);
    boolean argsExist = false;
    if (flags != null && !flags.isEmpty()) {
      sb.append(String.format("%s ", flags));
    }
    sb.append(String.format("%s %s(", m.getReturnType(), m.getName()));
    for (Type at : m.getArgumentTypes()) {
      sb.append(String.format("%s, ", at));
      argsExist = true;
    }
    if (argsExist) {
      sb.setLength(sb.length() - 2); // remove trailing ", "
    }
    sb.append(")");
    return sb.toString();
  }

  /**
   * Return a string representation of the access flags of method m. In the string, the flags are
   * space-separated and in a canonical order.
   *
   * @param m the method whose access flags to retrieve
   * @return a string representation of the access flags of method m
   */
  static String accessFlagsToString(Method m) {

    int flags = m.getAccessFlags();

    StringBuilder buf = new StringBuilder();
    // Note that pow is a binary mask for the flag (= 2^i).
    for (int i = 0, pow = 1; i <= Const.MAX_ACC_FLAG_I; i++) {
      if ((flags & pow) != 0) {
        if (buf.length() > 0) {
          buf.append(" ");
        }
        if (i < Const.ACCESS_NAMES_LENGTH) {
          buf.append(Const.getAccessName(i));
        } else {
          buf.append(String.format("ACC_BIT(%x)", pow));
        }
      }
      pow <<= 1;
    }

    return buf.toString();
  }

  /**
   * Return a printed description of the given instructions.
   *
   * @param il the instructions to describe
   * @param pool the constant pool the instructions refer to
   * @return a printed representation of the instructions in {@code il}
   */
  public static String instructionListToString(InstructionList il, ConstantPoolGen pool) {

    StringBuilder out = new StringBuilder();
    for (Iterator<InstructionHandle> i = il.iterator(); i.hasNext(); ) {
      InstructionHandle handle = i.next();
      out.append(handle.getInstruction().toString(pool.getConstantPool()) + "\n");
    }
    return out.toString();
  }

  /**
   * Return a description of the local variables (one per line).
   *
   * @param mg the method whose local variables to describe
   * @return a description of the local variables (one per line)
   */
  public static String localVariablesToString(MethodGen mg) {

    StringBuilder out = new StringBuilder();
    out.append(String.format("Locals for %s [cnt %d]%n", mg, mg.getMaxLocals()));
    LocalVariableGen[] lvgs = mg.getLocalVariables();
    if ((lvgs != null) && (lvgs.length > 0)) {
      for (LocalVariableGen lvg : lvgs) {
        out.append(String.format("  %s [index %d]%n", lvg, lvg.getIndex()));
      }
    }
    return out.toString();
  }

  /**
   * Return the attribute name for the specified attribute, looked up in the original class file
   * ConstantPool.
   *
   * @param a the attribute
   * @return the attribute name for the specified attribute
   */
  public static String attributeNameToString(Attribute a) {

    ConstantPool pool = a.getConstantPool();
    int conIndex = a.getNameIndex();
    Constant c = pool.getConstant(conIndex);
    String attName = ((ConstantUtf8) c).getBytes();
    return attName;
  }

  /**
   * Return the attribute name for the specified attribute, looked up in the given ConstantPoolGen.
   *
   * @param a the attribute
   * @param pool the constant pool
   * @return the attribute name for the specified attribute
   */
  public static String attributeNameToString(Attribute a, ConstantPoolGen pool) {

    int conIndex = a.getNameIndex();
    Constant c = pool.getConstant(conIndex);
    String attName = ((ConstantUtf8) c).getBytes();
    return attName;
  }

  // 'is' (boolean test) methods

  /**
   * Returns whether or not the method is a constructor.
   *
   * @param mg the MethodGen to test
   * @return true iff the method is a constructor
   */
  public static boolean isConstructor(MethodGen mg) {
    if (mg.getName().equals("")) {
      throw new Error("method name cannot be empty");
    }
    return mg.getName().equals("<init>");
  }

  /**
   * Returns whether or not the method is a constructor.
   *
   * @param m the Method to test
   * @return true iff the method is a constructor
   */
  public static boolean isConstructor(Method m) {
    if (m.getName().equals("")) {
      throw new Error("method name cannot be empty");
    }
    return m.getName().equals("<init>");
  }

  /**
   * Returns whether or not the method is a class initializer.
   *
   * @param mg the method to test
   * @return true iff the method is a class initializer
   */
  public static boolean isClinit(MethodGen mg) {
    return mg.getName().equals("<clinit>");
  }

  /**
   * Returns whether or not the method is a class initializer.
   *
   * @param m the method to test
   * @return true iff the method is a class initializer
   */
  public static boolean isClinit(Method m) {
    return m.getName().equals("<clinit>");
  }

  /**
   * Returns whether or not the class is part of the JDK (rt.jar).
   *
   * @param gen the class to test
   * @return true iff the class is in a package that is in the JDK (rt.jar)
   */
  public static boolean inJdk(ClassGen gen) {
    return inJdk(gen.getClassName());
  }

  /**
   * Returns whether or not the class is part of the JDK (rt.jar).
   *
   * @param classname the class to test, in the format of Class.getName(); the class should not be
   *     an array
   * @return true iff the class is in a package that is in the JDK (rt.jar)
   */
  public static boolean inJdk(@ClassGetName String classname) {
    if (classname.startsWith("java.")
        || classname.startsWith("com.sun.")
        || classname.startsWith("javax.")
        || classname.startsWith("jdk.")
        || classname.startsWith("org.ietf.")
        || classname.startsWith("org.jcp.")
        || classname.startsWith("org.w3c.")
        || classname.startsWith("org.xml.")
        || classname.startsWith("sun.")) {
      return true;
    }
    if (javaVersion <= 8) {
      if (classname.startsWith("com.oracle.") || classname.startsWith("org.omg.")) {
        return true;
      }
    } else {
      if (classname.startsWith("netscape.javascript.") || classname.startsWith("org.graalvm.")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns whether or not the class is part of the JDK (rt.jar).
   *
   * @param classname the class to test, in internal form
   * @return true iff the class is part of the JDK (rt.jar)
   */
  public static boolean inJdkInternalform(@InternalForm String classname) {
    if (classname.startsWith("java/")
        || classname.startsWith("com/sun/")
        || classname.startsWith("javax/")
        || classname.startsWith("jdk/")
        || classname.startsWith("org/ietj/")
        || classname.startsWith("org/jcp/")
        || classname.startsWith("org/w3c/")
        || classname.startsWith("org/xml/")
        || classname.startsWith("sun/")) {
      return true;
    }
    if (javaVersion <= 8) {
      if (classname.startsWith("com/oracle/") || classname.startsWith("org/omg/")) {
        return true;
      }
    } else {
      if (classname.startsWith("netscape/javascript/") || classname.startsWith("org/graalvm/")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns whether or not the specified attribute is a local variable type table.
   *
   * @param a the attribute
   * @param pool the constant pool
   * @return true iff the attribute is a local variable type table
   */
  public static boolean isLocalVariableTypeTable(Attribute a, ConstantPoolGen pool) {
    return attributeNameToString(a, pool).equals("LocalVariableTypeTable");
  }

  /**
   * Returns whether or not this is a standard main method (static, void, name is 'main', and one
   * formal parameter: a string array).
   *
   * @param mg the method to check
   * @return true iff the method is a main method
   */
  public static boolean isMain(MethodGen mg) {
    Type[] argTypes = mg.getArgumentTypes();
    return mg.isStatic()
        && (mg.getReturnType() == Type.VOID)
        && mg.getName().equals("main")
        && (argTypes.length == 1)
        && argTypes[0].equals(stringArray);
  }

  // consistency check methods

  /**
   * Checks the specified method for consistency.
   *
   * <p>Does nothing if {@link #skipChecks} is false.
   *
   * @param mgen the class to check
   */
  public static void checkMgen(MethodGen mgen) {

    if (skipChecks) {
      return;
    }

    try {
      @SuppressWarnings("UnusedVariable")
      String ignore = mgen.toString(); // ensure it can be formatted without exceptions
      mgen.getLineNumberTable(mgen.getConstantPool());

      InstructionList ilist = mgen.getInstructionList();
      if (ilist == null || ilist.getStart() == null) {
        return;
      }
      CodeExceptionGen[] exceptionHandlers = mgen.getExceptionHandlers();
      for (CodeExceptionGen gen : exceptionHandlers) {
        assert ilist.contains(gen.getStartPC())
            : "exception handler "
                + gen
                + " has been forgotten in "
                + mgen.getClassName()
                + "."
                + mgen.getName();
      }
      MethodGen nmg = new MethodGen(mgen.getMethod(), mgen.getClassName(), mgen.getConstantPool());
      nmg.getLineNumberTable(mgen.getConstantPool());
    } catch (Throwable t) {
      Error e =
          new Error(
              String.format(
                  "failure while checking method %s.%s%n", mgen.getClassName(), mgen.getName()),
              t);
      e.printStackTrace();
      throw e;
    }
  }

  /**
   * Checks all of the methods in gen for consistency.
   *
   * @param gen the class to check
   */
  public static void checkMgens(final ClassGen gen) {

    if (skipChecks) {
      return;
    }

    Method[] methods = gen.getMethods();
    for (int i = 0; i < methods.length; i++) {
      Method method = methods[i];
      // System.out.println ("Checking method " + method + " in class "
      // + gen.getClassName());
      checkMgen(new MethodGen(method, gen.getClassName(), gen.getConstantPool()));
    }

    // Diagnostic output
    if (false) {
      dumpStackTrace();
      dumpMethods(gen);
    }
  }

  // 'dump' methods

  /** Print the current java call stack. */
  public static void dumpStackTrace() {

    StackTraceElement[] ste = Thread.currentThread().getStackTrace();
    // [0] is getStackTrace
    // [1] is dumpStackTrace
    if (ste.length < 3) {
      System.out.println("No stack trace information available");
    } else {
      StackTraceElement caller = ste[2];
      System.out.printf(
          "%s.%s (%s line %d)",
          caller.getClassName(),
          caller.getMethodName(),
          caller.getFileName(),
          caller.getLineNumber());
      for (int ii = 3; ii < ste.length; ii++) {
        System.out.printf(" [%s line %d]", ste[ii].getFileName(), ste[ii].getLineNumber());
      }
      System.out.printf("%n");
    }
  }

  /**
   * Print the methods in the class, to standard output.
   *
   * @param gen the class whose methods to print
   */
  static void dumpMethods(ClassGen gen) {

    System.out.printf("Class %s methods:%n", gen.getClassName());
    for (Method m : gen.getMethods()) {
      System.out.printf("  %s%n", m);
    }
  }

  /**
   * Dumps the contents of the specified class to the specified directory. The file is named
   * dumpDir/[class].bcel. It contains a synopsis of the fields and methods followed by the JVM code
   * for each method.
   *
   * @param jc JavaClass to dump
   * @param dumpDir directory in which to write the file
   * @see #dump(JavaClass, File)
   */
  public static void dump(JavaClass jc, String dumpDir) {

    dump(jc, new File(dumpDir));
  }

  /**
   * Dumps the contents of the specified class to the specified directory. The file is named
   * dumpDir/[class].bcel. It contains a synopsis of the fields and methods followed by the JVM code
   * for each method.
   *
   * @param jc JavaClass to dump
   * @param dumpDir directory in which to write the file
   */
  public static void dump(JavaClass jc, File dumpDir) {

    dumpDir.mkdir();
    try (PrintStream p = new PrintStream(new File(dumpDir, jc.getClassName() + ".bcel"))) {
      // Print the class, superclass, and interfaces
      p.printf("class %s extends %s%n", jc.getClassName(), jc.getSuperclassName());
      String[] inames = jc.getInterfaceNames();
      boolean first = true;
      if ((inames != null) && (inames.length > 0)) {
        p.printf("   implements ");
        for (String iname : inames) {
          if (!first) {
            p.printf(", ");
          }
          p.printf("%s", iname);
          first = false;
        }
        p.printf("%n");
      }

      // Print each field
      p.printf("%nFields%n");
      for (Field f : jc.getFields()) {
        p.printf("  %s%n", f);
      }

      // Print the signature of each method
      p.printf("%nMethods%n");
      for (Method m : jc.getMethods()) {
        p.printf("  %s%n", m);
      }

      for (Method m : jc.getMethods()) {
        Code code = m.getCode();
        if (code != null) {
          p.printf("%nMethod %s%n", m);
          p.printf("  %s%n", code.toString().replace("\n", "\n  "));
        }
      }

      // Print the details of the constant pool.
      p.printf("Constant Pool:%n");
      ConstantPool cp = jc.getConstantPool();
      Constant[] constants = cp.getConstantPool();
      for (int ii = 0; ii < constants.length; ii++) {
        p.printf("  %d %s%n", ii, constants[ii]);
      }
    } catch (Exception e) {
      throw new Error(
          "Unexpected error dumping JavaClass: " + jc.getClassName() + " to " + dumpDir.getName(),
          e);
    }
  }

  // miscellaneous methods

  /**
   * Adds instructions to the start of a method.
   *
   * @param mg method to be augmented
   * @param newList instructions to prepend to the method
   */
  public static void addToStart(MethodGen mg, InstructionList newList) {

    // Add the code before the first instruction
    InstructionList il = mg.getInstructionList();
    InstructionHandle oldStart = il.getStart();
    InstructionHandle newStart = il.insert(newList);

    // Move any LineNumbers and local variables that currently point to
    // the first instruction to include the new instructions. Other
    // targeters (branches, exceptions) should not include the new code.
    if (oldStart.hasTargeters()) {
      // getTargeters() returns non-null because hasTargeters => true
      for (InstructionTargeter it : oldStart.getTargeters()) {
        if ((it instanceof LineNumberGen) || (it instanceof LocalVariableGen)) {
          it.updateTarget(oldStart, newStart);
        }
      }
    }
    mg.setMaxStack();
    mg.setMaxLocals();
  }

  /**
   * Returns the constant string at the specified offset.
   *
   * @param pool the constant pool
   * @param index the index in the constant pool
   * @return the constant string at the specified offset in the constant pool
   */
  public static String getConstantString(ConstantPool pool, int index) {

    Constant c = pool.getConstant(index);
    assert c != null : "Bad index " + index + " into pool";
    if (c instanceof ConstantUtf8) {
      return ((ConstantUtf8) c).getBytes();
    } else if (c instanceof ConstantClass) {
      ConstantClass cc = (ConstantClass) c;
      return cc.getBytes(pool) + " [" + cc.getNameIndex() + "]";
    } else {
      throw new Error("unexpected constant " + c + " of class " + c.getClass());
    }
  }

  /**
   * Sets the locals to be the formal parameters. Any other locals are removed. An instruction list
   * with at least one instruction must exist.
   *
   * @param mg the method whose locals to set
   */
  public static void resetLocalsToFormals(MethodGen mg) {

    // Remove any existing locals
    mg.setMaxLocals(0);
    mg.removeLocalVariables();

    // Add a local for the instance variable (this)
    if (!mg.isStatic()) {
      mg.addLocalVariable("this", new ObjectType(mg.getClassName()), null, null);
    }

    // Get the parameter types and names.
    Type @SameLen({"argTypes", "mg.getArgumentTypes()"}) [] argTypes = mg.getArgumentTypes();
    String @SameLen({"argTypes", "argNames", "mg.getArgumentTypes()", "mg.getArgumentNames()"}) []
        argNames = mg.getArgumentNames();

    // Add a local for each parameter
    for (int ii = 0; ii < argNames.length; ii++) {
      mg.addLocalVariable(argNames[ii], argTypes[ii], null, null);
    }

    // Reset the current number of locals so that when other locals
    // are added they get added at the correct offset.
    mg.setMaxLocals();

    return;
  }

  /**
   * Empties the method of all code (except for a return). This includes line numbers, exceptions,
   * local variables, etc.
   *
   * @param mg the method to clear out
   */
  public static void makeMethodBodyEmpty(MethodGen mg) {

    mg.setInstructionList(new InstructionList(new RETURN()));
    mg.removeExceptionHandlers();
    mg.removeLineNumbers();
    mg.removeLocalVariables();
    mg.setMaxLocals();
  }

  /**
   * Remove the local variable type table attribute (LVTT) from mg. Evidently some changes require
   * this to be updated, but without BCEL support that would be hard to do. It should be safe to
   * just delete it since it is optional and really only of use to a debugger.
   *
   * @param mg the method to clear out
   */
  public static void removeLocalVariableTypeTables(MethodGen mg) {

    for (Attribute a : mg.getCodeAttributes()) {
      if (isLocalVariableTypeTable(a, mg.getConstantPool())) {
        mg.removeCodeAttribute(a);
      }
    }
  }

  /**
   * Returns the Java class name, in the format of {@link Class#getName()}, that corresponds to
   * type.
   *
   * @param type the type
   * @return the Java classname that corresponds to type
   */
  public static @ClassGetName String typeToClassgetname(Type type) {
    String signature = type.getSignature();
    return Signatures.fieldDescriptorToClassGetName(signature);
  }

  /**
   * Returns the class that corresponds to type.
   *
   * @param type the type
   * @return the Java class that corresponds to type
   */
  public static Class<?> typeToClass(Type type) {

    String classname = typeToClassgetname(type);
    try {
      return ReflectionPlume.classForName(classname);
    } catch (Exception e) {
      throw new RuntimeException("can't find class for " + classname, e);
    }
  }

  /**
   * Returns a copy of the given type array, with newType added to the end.
   *
   * @param types the array to extend
   * @param newType the element to add to the end of the array
   * @return a new array, with newType at the end
   */
  public static Type[] postpendToArray(Type[] types, Type newType) {
    if (types.length == Integer.MAX_VALUE) {
      throw new Error("array " + Arrays.toString(types) + " is too large to extend");
    }
    Type[] newTypes = new Type[types.length + 1];
    System.arraycopy(types, 0, newTypes, 0, types.length);
    newTypes[types.length] = newType;
    return newTypes;
  }

  /**
   * Returns a copy of the given type array, with newType inserted at the beginning.
   *
   * @param types the array to extend
   * @param newType the element to add to the beginning of the array
   * @return a new array, with newType at the beginning
   */
  public static Type[] prependToArray(Type newType, Type[] types) {
    if (types.length == Integer.MAX_VALUE) {
      throw new Error("array " + Arrays.toString(types) + " is too large to extend");
    }
    Type[] newTypes = new Type[types.length + 1];
    System.arraycopy(types, 0, newTypes, 1, types.length);
    newTypes[0] = newType;
    return newTypes;
  }

  /**
   * Return the type corresponding to a given binary name or primitive type name.
   *
   * @param classname the binary name of a class (= fully-qualified name, except for inner classes),
   *     or a primitive type name, but not an array
   * @return the type corresponding to the given class name
   * @see #fqBinaryNameToType
   */
  public static Type binaryNameToType(@BinaryNameOrPrimitiveType String classname) {

    classname = classname.intern();

    if (classname == "int") { // interned
      return Type.INT;
    } else if (classname == "boolean") { // interned
      return Type.BOOLEAN;
    } else if (classname == "byte") { // interned
      return Type.BYTE;
    } else if (classname == "char") { // interned
      return Type.CHAR;
    } else if (classname == "double") { // interned
      return Type.DOUBLE;
    } else if (classname == "float") { // interned
      return Type.FLOAT;
    } else if (classname == "long") { // interned
      return Type.LONG;
    } else if (classname == "short") { // interned
      return Type.SHORT;
    } else {
      @SuppressWarnings("signature") // It's not a primitive, so it's a proper binary name.
      @BinaryName String binaryName = classname;
      return new ObjectType(binaryName);
    }
  }

  /**
   * Return the type corresponding to a given fully-qualified binary name.
   *
   * @param classname the fully-qualified binary name of a type, which is like a
   *     fully-qualified-name but uses "$" rather than "." for nested classes
   * @return the type corresponding to the given name
   */
  public static Type fqBinaryNameToType(@FqBinaryName String classname) {

    Signatures.ClassnameAndDimensions cad =
        Signatures.ClassnameAndDimensions.parseFqBinaryName(classname);
    Type eltType = fqBinaryNameToType(cad.classname);
    if (cad.dimensions == 0) {
      return eltType;
    } else {
      return new ArrayType(eltType, cad.dimensions);
    }
  }
}
