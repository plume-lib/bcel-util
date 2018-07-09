package org.plumelib.bcelutil;

import java.io.File;
import java.io.PrintStream;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
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

/*>>>
import org.checkerframework.checker.index.qual.*;
import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.checker.signature.qual.*;
import org.checkerframework.common.value.qual.*;
*/

/** Static utility methods for working with BCEL. */
public final class BcelUtil {
  /** This class is a collection of methods; it does not represent anything. */
  private BcelUtil() {
    throw new Error("do not instantiate");
  }

  /** Controls whether the checks in checkMgen are actually performed. */
  public static boolean skipChecks = false;

  /** The type that represents String[]. */
  private static final Type stringArray = Type.getType("[Ljava.lang.String;");

  /**
   * Prints method declarations to System.out.
   *
   * @param gen class whose methods to print
   */
  static void dumpMethodDeclarations(ClassGen gen) {
    System.out.printf("method signatures for class %s%n", gen.getClassName());
    for (Method m : gen.getMethods()) {
      System.out.printf("  %s%n", getMethodDeclaration(m));
    }
  }

  /**
   * Returns a string describing a method declaration. It contains the access flags (public,
   * private, static, etc), the return type, the method name, and the types of each of its
   * arguments.
   *
   * @param m the method
   * @return a string describing the method declaration
   */
  public static String getMethodDeclaration(Method m) {

    StringBuilder sb = new StringBuilder();
    Formatter f = new Formatter(sb);

    f.format("%s %s %s (", getAccessFlags(m), m.getReturnType(), m.getName());
    for (Type at : m.getArgumentTypes()) {
      f.format("%s, ", at);
    }
    f.format(")");
    return (sb.toString().replace(", )", ")"));
  }

  /**
   * Return a string representation of the access flags of method m.
   *
   * @param m the method whose access flags to retrieve
   * @return a string representation of the access flags of method m
   */
  static String getAccessFlags(Method m) {

    int flags = m.getAccessFlags();

    StringBuilder buf = new StringBuilder();
    for (int i = 0, pow = 1; i <= Const.MAX_ACC_FLAG; i++) {
      if ((flags & pow) != 0) {
        if (buf.length() > 0) {
          buf.append(" ");
        }
        if (i < Const.ACCESS_NAMES_LENGTH) {
          buf.append(Const.getAccessName(i));
        } else {
          buf.append(String.format("ACC_BIT %x", pow));
        }
      }
      pow <<= 1;
    }

    return (buf.toString());
  }

  /**
   * Return the attribute name for the specified attribute.
   *
   * @param a the attribute
   * @return the attribute name for the specified attribute
   */
  public static String getAttributeName(Attribute a) {

    ConstantPool pool = a.getConstantPool();
    int conIndex = a.getNameIndex();
    Constant c = pool.getConstant(conIndex);
    String attName = ((ConstantUtf8) c).getBytes();
    return (attName);
  }

  /**
   * Returns the constant string at the specified offset.
   *
   * @param pool the constant pool
   * @param index the index in the constant pool
   * @return the constant string at the specified offset in the constant pool
   */
  public static String getConstantStr(ConstantPool pool, int index) {

    Constant c = pool.getConstant(index);
    assert c != null : "Bad index " + index + " into pool";
    if (c instanceof ConstantUtf8) {
      return ((ConstantUtf8) c).getBytes();
    } else if (c instanceof ConstantClass) {
      ConstantClass cc = (ConstantClass) c;
      return cc.getBytes(pool) + " [" + cc.getNameIndex() + "]";
    } else {
      throw new Error("unexpected constant " + c + " class " + c.getClass());
    }
  }

  /**
   * Returns whether or not the method is a constructor.
   *
   * @param mg the method to test
   * @return true iff the method is a constructor
   */
  public static boolean isConstructor(MethodGen mg) {
    return (mg.getName().equals("<init>") || mg.getName().equals(""));
  }

  /**
   * Returns whether or not the method is a constructor.
   *
   * @param m the method to test
   * @return true iff the method is a constructor
   */
  public static boolean isConstructor(Method m) {
    return (m.getName().equals("<init>") || m.getName().equals(""));
  }

  /**
   * Returns whether or not the method is a class initializer.
   *
   * @param mg the method to test
   * @return true iff the method is a class initializer
   */
  public static boolean isClinit(MethodGen mg) {
    return (mg.getName().equals("<clinit>"));
  }

  /**
   * Returns whether or not the method is a class initializer.
   *
   * @param m the method to test
   * @return true iff the method is a class initializer
   */
  public static boolean isClinit(Method m) {
    return (m.getName().equals("<clinit>"));
  }

  /**
   * Returns whether or not the class is part of the JDK (rt.jar).
   *
   * @param gen the class to test
   * @return true iff the class is part of the JDK (rt.jar)
   */
  public static boolean inJdk(ClassGen gen) {
    return (inJdk(gen.getClassName()));
  }

  /**
   * Returns whether or not the class is part of the JDK (rt.jar).
   *
   * @param classname the class to test, in the format of Class.getName(); the class should not be
   *     an array
   * @return true iff the class is part of the JDK (rt.jar)
   */
  public static boolean inJdk(/*@ClassGetName*/ String classname) {
    return classname.startsWith("java.")
        || classname.startsWith("com.oracle.")
        || classname.startsWith("com.sun.")
        || classname.startsWith("javax.")
        || classname.startsWith("jdk.")
        || classname.startsWith("org.ietf.")
        || classname.startsWith("org.jcp.")
        || classname.startsWith("org.omg.")
        || classname.startsWith("org.w3c.")
        || classname.startsWith("org.xml.")
        || classname.startsWith("sun.")
        || classname.startsWith("sunw.");
  }

  /**
   * Returns whether or not the class is part of the JDK (rt.jar).
   *
   * @param classname the class to test, in internal form
   * @return true iff the class is part of the JDK (rt.jar)
   */
  public static boolean inJdkInternalform(/*@InternalForm*/ String classname) {
    return classname.startsWith("java/")
        || classname.startsWith("com/oracle/")
        || classname.startsWith("com/sun/")
        || classname.startsWith("javax/")
        || classname.startsWith("jdk/")
        || classname.startsWith("org/ietj/")
        || classname.startsWith("org/jcp/")
        || classname.startsWith("org/omg/")
        || classname.startsWith("org/w3c/")
        || classname.startsWith("org/xml/")
        || classname.startsWith("sun/")
        || classname.startsWith("sunw/");
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
   * Checks the specific method for consistency.
   *
   * @param mgen the class to check
   */
  public static void checkMgen(MethodGen mgen) {

    if (skipChecks) {
      return;
    }

    try {
      mgen.toString(); // ensure it can be formatted without exceptions
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
      System.out.printf("failure in method %s.%s%n", mgen.getClassName(), mgen.getName());
      t.printStackTrace();
      throw new Error(t);
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

    if (false) {
      Throwable t = new Throwable();
      t.fillInStackTrace();
      StackTraceElement[] ste = t.getStackTrace();
      if (ste.length < 2) {
        System.out.println("No stack trace information available");
      } else {
        StackTraceElement caller = ste[1];
        System.out.printf(
            "%s.%s (%s line %d)",
            caller.getClassName(),
            caller.getMethodName(),
            caller.getFileName(),
            caller.getLineNumber());
        for (int ii = 2; ii < ste.length; ii++) {
          System.out.printf(" [%s line %d]", ste[ii].getFileName(), ste[ii].getLineNumber());
        }
        System.out.printf("%n");
      }
      dumpMethods(gen);
    }
  }

  /**
   * Adds code in nl to start of method mg.
   *
   * @param mg method to be augmented
   * @param nl instructions to prepend to the method
   */
  public static void addToStart(MethodGen mg, InstructionList nl) {

    // Add the code before the first instruction
    InstructionList il = mg.getInstructionList();
    InstructionHandle oldStart = il.getStart();
    InstructionHandle newStart = il.insert(nl);

    // Move any LineNumbers and local variable that currently point to
    // the first instruction to include the new instructions. Other
    // targeters (branches, exceptions) should not include the new
    // code
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
   * Dumps the contents of the specified class to the specified directory. The file is named
   * dumpDir/[class].bcel. It contains a synopsis of the fields and methods followed by the jvm code
   * for each method.
   *
   * @param jc javaclass to dump
   * @param dumpDir directory in which to write the file
   * @see #dump(JavaClass, File)
   */
  public static void dump(JavaClass jc, String dumpDir) {

    dump(jc, new File(dumpDir));
  }

  /**
   * Dumps the contents of the specified class to the specified directory. The file is named
   * dumpDir/[class].bcel. It contains a synopsis of the fields and methods followed by the jvm code
   * for each method.
   *
   * @param jc javaclass to dump
   * @param dumpDir directory in which to write the file
   */
  public static void dump(JavaClass jc, File dumpDir) {

    try {
      dumpDir.mkdir();
      File path = new File(dumpDir, jc.getClassName() + ".bcel");
      PrintStream p = new PrintStream(path);

      // Print the class, super class and interfaces
      p.printf("class %s extends %s%n", jc.getClassName(), jc.getSuperclassName());
      String[] inames = jc.getInterfaceNames();
      if ((inames != null) && (inames.length > 0)) {
        p.printf("   ");
        for (String iname : inames) {
          p.printf("implements %s ", iname);
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

      // If this is not an interface, print the code for each method
      if (!jc.isInterface()) {
        for (Method m : jc.getMethods()) {
          p.printf("%nMethod %s%n", m);
          Code code = m.getCode();
          if (code != null) {
            p.printf("  %s%n", code.toString().replace("\n", "\n  "));
          }
        }
      }

      // Print the details of the constant pool.
      p.printf("Constant Pool:%n");
      ConstantPool cp = jc.getConstantPool();
      Constant[] constants = cp.getConstantPool();
      for (int ii = 0; ii < constants.length; ii++) {
        p.printf("  %d %s%n", ii, constants[ii]);
      }

      p.close();

    } catch (Exception e) {
      throw new Error("Unexpected error dumping javaclass", e);
    }
  }

  /**
   * Return a printed description of the given instructions.
   *
   * @param il the instructions to describe
   * @param pool the constant pool the instructions refer to
   * @return a printed representation of the instructions in {@code il}
   */
  @SuppressWarnings("rawtypes")
  public static String instructionDescr(InstructionList il, ConstantPoolGen pool) {

    StringBuilder out = new StringBuilder();
    // not generic because BCEL is not generic
    for (Iterator i = il.iterator(); i.hasNext(); ) {
      InstructionHandle handle = (InstructionHandle) i.next();
      out.append(handle.getInstruction().toString(pool.getConstantPool()) + "\n");
    }
    return (out.toString());
  }

  /**
   * Return a description of the local variables (one per line).
   *
   * @param mg the method whose local variables to describe
   * @return a description of the local variables (one per line)
   */
  public static String localVarDescr(MethodGen mg) {

    StringBuilder out = new StringBuilder();
    out.append(String.format("Locals for %s [cnt %d]%n", mg, mg.getMaxLocals()));
    LocalVariableGen[] lvgs = mg.getLocalVariables();
    if ((lvgs != null) && (lvgs.length > 0)) {
      for (LocalVariableGen lvg : lvgs) {
        out.append(String.format("  %s [index %d]%n", lvg, lvg.getIndex()));
      }
    }
    return (out.toString());
  }

  /**
   * Builds an array of line numbers for the specified instruction list. Each opcode is assigned the
   * next source line number starting at 1000.
   *
   * @param mg the method whose line numbers to extract
   * @param il the instruction list to augment with line numbers
   */
  public static void addLineNumbers(MethodGen mg, InstructionList il) {

    il.setPositions(true);
    for (InstructionHandle ih : il.getInstructionHandles()) {
      mg.addLineNumber(ih, 1000 + ih.getPosition());
    }
  }

  /**
   * Sets the locals to 'this' and each of the arguments. Any other locals are removed. An
   * instruction list with at least one instruction must exist.
   *
   * @param mg the method whose locals to set
   */
  public static void setupInitLocals(MethodGen mg) {

    // Get the parameter types and names.
    @SuppressWarnings(
        "nullness") // The arguments to the annotation aren't necessarily initialized before they
    // are written here. Since annotations are erased at runtime, this is safe.
    Type /*@SameLen({"argTypes", "mg.getArgumentTypes()"})*/[] argTypes = mg.getArgumentTypes();
    @SuppressWarnings(
        "nullness") // The arguments to the annotation aren't necessarily initialized before they
    // are written here. Since annotations are erased at runtime, this is safe.
    String /*@SameLen({"argTypes", "argNames", "mg.getArgumentTypes()", "mg.getArgumentNames()"})*/
            []
        argNames = mg.getArgumentNames();

    // Remove any existing locals
    mg.setMaxLocals(0);
    mg.removeLocalVariables();

    // Add a local for the instance variable (this)
    if (!mg.isStatic()) {
      mg.addLocalVariable("this", new ObjectType(mg.getClassName()), null, null);
    }

    // Add a local for each parameter
    for (int ii = 0; ii < argNames.length; ii++) {
      mg.addLocalVariable(argNames[ii], argTypes[ii], null, null);
    }

    // Reset the current number of locals so that when other locals
    // are added they get added at the correct offset
    mg.setMaxLocals();

    return;
  }

  /**
   * Empties the method of all code (except for a return). This includes line numbers, exceptions,
   * local variables, etc.
   *
   * @param mg the method to clear out
   */
  public static void emptyMethod(MethodGen mg) {

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
   * Returns whether or not the specified attribute is a local variable type table.
   *
   * @param a the attribute
   * @param pool the constant pool
   * @return true iff the attribute is a local variable type table
   */
  public static boolean isLocalVariableTypeTable(Attribute a, ConstantPoolGen pool) {
    return (getAttributeName(a, pool).equals("LocalVariableTypeTable"));
  }

  /**
   * Return the attribute name for the specified attribute.
   *
   * @param a the attribute
   * @param pool the constant pool
   * @return the attribute name for the specified attribute
   */
  public static String getAttributeName(Attribute a, ConstantPoolGen pool) {

    int conIndex = a.getNameIndex();
    Constant c = pool.getConstant(conIndex);
    String attName = ((ConstantUtf8) c).getBytes();
    return (attName);
  }

  /**
   * Returns whether or not this is a standard main method (static, name is 'main', and one argument
   * of string array).
   *
   * @param mg the method to check
   * @return true iff the method is a main method
   */
  public static boolean isMain(MethodGen mg) {
    Type[] argTypes = mg.getArgumentTypes();
    return (mg.isStatic()
        && mg.getName().equals("main")
        && (argTypes.length == 1)
        && argTypes[0].equals(stringArray));
  }

  /**
   * Returns the Java class name, in the format of {@link Class#getName()}, that corresponds to
   * type.
   *
   * @param type the type
   * @return the Java classname that corresponds to type
   */
  public static /*@ClassGetName*/ String typeToClassgetname(Type type) {
    String signature = type.getSignature();
    return JvmUtil.fieldDescriptorToClassGetName(signature);
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
      Class<?> c = classForName(classname);
      return c;
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
    Type[] newTypes = new Type[types.length + 1];
    System.arraycopy(types, 0, newTypes, 0, types.length);
    newTypes[types.length] = newType;
    Type[] newTypesCast = newTypes;
    return (newTypesCast);
  }

  /**
   * Returns a copy of the given type array, with newType added to the end.
   *
   * @deprecated use {@link #postpendToArray}
   * @param types the array to extend
   * @param newType the element to add to the end of the array
   * @return a new array, with newType at the end
   */
  @Deprecated
  public static Type[] addType(Type[] types, Type newType) {
    return postpendToArray(types, newType);
  }

  /**
   * Returns a copy of the given type array, with newType inserted at the beginning.
   *
   * @param types the array to extend
   * @param newType the element to add to the beginning of the array
   * @return a new array, with newType at the beginning
   */
  public static Type[] prependToArray(Type newType, Type[] types) {
    @SuppressWarnings({
      "index", // newTypes is @MinLen(1) except in the presence of overflow,
      // which the Value Checker accounts for, but the Index Checker does not.
      "value" // newTypes is @MinLen(1) except in the presence of overflow,
      // which the Value Checker accounts for, but the Index Checker does not.
    })
    Type /*@MinLen(1)*/[] newTypes = new Type[types.length + 1];
    System.arraycopy(types, 0, newTypes, 1, types.length);
    newTypes[0] = newType;
    Type[] newTypesCast = newTypes;
    return (newTypesCast);
  }

  /**
   * Returns a copy of the given type array, with newType inserted at the beginning.
   *
   * @deprecated use {@link #prependToArray}
   * @param types the array to extend
   * @param newType the element to add to the beginning of the array
   * @return a new array, with newType at the beginning
   */
  @Deprecated
  public static Type[] insertType(Type newType, Type[] types) {
    return prependToArray(newType, types);
  }

  /**
   * Return the type corresponding to a given class name.
   *
   * @param classname the binary name of a class (= fully-qualified name, except for inner classes)
   * @return the type corresponding to the given class name
   */
  public static Type classnameToType(/*@BinaryName*/ String classname) {

    // Get the array depth (if any)
    int arrayDepth = 0;
    while (classname.endsWith("[]")) {
      @SuppressWarnings("signature") // removing trailing "[]" leaves the string a binary name
      /*@BinaryName*/ String sansArray = classname.substring(0, classname.length() - 2);
      classname = sansArray;
      arrayDepth++;
    }
    @SuppressWarnings("signature") // test of no trailing "[]" => has type @BinaryNameForNonArray
    /*@BinaryNameForNonArray*/ String tmp = classname;
    classname = tmp.intern();

    // Get the base type
    Type t = null;
    if (classname == "int") { // interned
      t = Type.INT;
    } else if (classname == "boolean") { // interned
      t = Type.BOOLEAN;
    } else if (classname == "byte") { // interned
      t = Type.BYTE;
    } else if (classname == "char") { // interned
      t = Type.CHAR;
    } else if (classname == "double") { // interned
      t = Type.DOUBLE;
    } else if (classname == "float") { // interned
      t = Type.FLOAT;
    } else if (classname == "long") { // interned
      t = Type.LONG;
    } else if (classname == "short") { // interned
      t = Type.SHORT;
    } else { // must be a non-primitive
      t = new ObjectType(classname);
    }

    // If there was an array, build the array type
    if (arrayDepth > 0) {
      t = new ArrayType(t, arrayDepth);
    }

    return t;
  }

  /** Used by {@link #classForName}. */
  private static HashMap<String, Class<?>> primitiveClasses = new HashMap<String, Class<?>>(8);

  static {
    primitiveClasses.put("boolean", Boolean.TYPE);
    primitiveClasses.put("byte", Byte.TYPE);
    primitiveClasses.put("char", Character.TYPE);
    primitiveClasses.put("double", Double.TYPE);
    primitiveClasses.put("float", Float.TYPE);
    primitiveClasses.put("int", Integer.TYPE);
    primitiveClasses.put("long", Long.TYPE);
    primitiveClasses.put("short", Short.TYPE);
  }

  // TODO: This is a private copy (but protected to permit testing).  When
  // the method is moved from monolithic plume-lib, perhaps depend on the
  // new version.  Or just keep depending on this small implementation.
  // TODO: should create a method that works exactly for the desired argument type.
  /**
   * Like {@link Class#forName(String)}, but also works when the string represents a primitive type
   * or a fully-qualified name (as opposed to a binary name).
   *
   * <p>If the given name can't be found, this method changes the last '.' to a dollar sign ($) and
   * tries again. This accounts for inner classes that are incorrectly passed in in fully-qualified
   * format instead of binary format. (It should try multiple dollar signs, not just at the last
   * position.)
   *
   * <p>Recall the rather odd specification for {@link Class#forName(String)}: the argument is a
   * binary name for non-arrays, but a field descriptor for arrays. This method uses the same rules,
   * but additionally handles primitive types and, for non-arrays, fully-qualified names.
   *
   * @param className name of the class
   * @return the Class corresponding to className
   * @throws ClassNotFoundException if the class is not found
   */
  // The annotation encourages proper use, even though this can take a
  // fully-qualified name (only for a non-array).
  // TODO: protected
  public static Class<?> classForName(
      /*@ClassGetName*/ String className) throws ClassNotFoundException {
    Class<?> result = primitiveClasses.get(className);
    if (result != null) {
      return result;
    } else {
      try {
        return Class.forName(className);
      } catch (ClassNotFoundException e) {
        int pos = className.lastIndexOf('.');
        if (pos < 0) {
          throw e;
        }
        @SuppressWarnings("signature") // checked below & exception is handled
        /*@ClassGetName*/ String innerName =
            className.substring(0, pos) + "$" + className.substring(pos + 1);
        try {
          return Class.forName(innerName);
        } catch (ClassNotFoundException ee) {
          throw e;
        }
      }
    }
  }
}
