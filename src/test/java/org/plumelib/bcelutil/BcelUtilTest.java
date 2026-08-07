package org.plumelib.bcelutil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.plumelib.bcelutil.Fixtures.code;
import static org.plumelib.bcelutil.Fixtures.method;
import static org.plumelib.bcelutil.Fixtures.methodGen;
import static org.plumelib.bcelutil.Fixtures.nonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.NOP;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.RETURN;
import org.apache.bcel.generic.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Tests for {@link BcelUtil}. */
final class BcelUtilTest {

  /** Creates a BcelUtilTest. */
  BcelUtilTest() {}

  // inJdk

  @Test
  void inJdkAcceptsJdkPackages() {
    assertTrue(BcelUtil.inJdk("java.lang.String"));
    assertTrue(BcelUtil.inJdk("javax.swing.JPanel"));
    assertTrue(BcelUtil.inJdk("jdk.internal.misc.Unsafe"));
    assertTrue(BcelUtil.inJdk("com.sun.tools.javac.Main"));
    assertTrue(BcelUtil.inJdk("sun.misc.Signal"));
    assertTrue(BcelUtil.inJdk("org.w3c.dom.Node"));
    assertTrue(BcelUtil.inJdk("org.xml.sax.XMLReader"));
    assertTrue(BcelUtil.inJdk("org.ietf.jgss.GSSName"));
    assertTrue(BcelUtil.inJdk("org.jcp.xml.dsig.internal.DigesterOutputStream"));
    assertTrue(BcelUtil.inJdk("netscape.javascript.JSObject"));
    assertTrue(BcelUtil.inJdk("org.graalvm.nativeimage.ImageInfo"));
  }

  @Test
  void inJdkRejectsNonJdkPackages() {
    assertFalse(BcelUtil.inJdk("org.plumelib.bcelutil.BcelUtil"));
    assertFalse(BcelUtil.inJdk("org.apache.bcel.Const"));
    // The test is on the package prefix "java.", not on "java".
    assertFalse(BcelUtil.inJdk("javaxyz.Foo"));
    assertFalse(BcelUtil.inJdk("org.w3cfoo.Node"));
  }

  @Test
  void inJdkAcceptsAClassGen() {
    assertFalse(BcelUtil.inJdk(Fixtures.classGen));
  }

  @Test
  void inJdkInternalformAcceptsJdkPackages() {
    assertTrue(BcelUtil.inJdkInternalform("java/lang/String"));
    assertTrue(BcelUtil.inJdkInternalform("javax/swing/JPanel"));
    assertTrue(BcelUtil.inJdkInternalform("jdk/internal/misc/Unsafe"));
    assertTrue(BcelUtil.inJdkInternalform("com/sun/tools/javac/Main"));
    assertTrue(BcelUtil.inJdkInternalform("sun/misc/Signal"));
    assertTrue(BcelUtil.inJdkInternalform("org/w3c/dom/Node"));
    assertTrue(BcelUtil.inJdkInternalform("org/xml/sax/XMLReader"));
    assertTrue(BcelUtil.inJdkInternalform("org/ietf/jgss/GSSName"));
    assertTrue(BcelUtil.inJdkInternalform("netscape/javascript/JSObject"));
    assertTrue(BcelUtil.inJdkInternalform("org/graalvm/nativeimage/ImageInfo"));
  }

  @Test
  void inJdkInternalformRejectsNonJdkPackages() {
    assertFalse(BcelUtil.inJdkInternalform("org/plumelib/bcelutil/BcelUtil"));
    assertFalse(BcelUtil.inJdkInternalform("org/apache/bcel/Const"));
    assertFalse(BcelUtil.inJdkInternalform("javaxyz/Foo"));
  }

  // Type conversion

  @Test
  void binaryNameToTypeConvertsPrimitives() {
    assertEquals(Type.INT, BcelUtil.binaryNameToType("int"));
    assertEquals(Type.BOOLEAN, BcelUtil.binaryNameToType("boolean"));
    assertEquals(Type.BYTE, BcelUtil.binaryNameToType("byte"));
    assertEquals(Type.CHAR, BcelUtil.binaryNameToType("char"));
    assertEquals(Type.DOUBLE, BcelUtil.binaryNameToType("double"));
    assertEquals(Type.FLOAT, BcelUtil.binaryNameToType("float"));
    assertEquals(Type.LONG, BcelUtil.binaryNameToType("long"));
    assertEquals(Type.SHORT, BcelUtil.binaryNameToType("short"));
  }

  @Test
  void binaryNameToTypeConvertsClasses() {
    assertEquals(new ObjectType("java.lang.String"), BcelUtil.binaryNameToType("java.lang.String"));
    assertEquals(
        new ObjectType("java.util.Map$Entry"), BcelUtil.binaryNameToType("java.util.Map$Entry"));
  }

  @Test
  void fqBinaryNameToTypeConvertsNonArrays() {
    assertEquals(Type.INT, BcelUtil.fqBinaryNameToType("int"));
    assertEquals(
        new ObjectType("java.lang.String"), BcelUtil.fqBinaryNameToType("java.lang.String"));
    assertEquals(
        new ObjectType("java.util.Map$Entry"), BcelUtil.fqBinaryNameToType("java.util.Map$Entry"));
  }

  @Test
  void fqBinaryNameToTypeConvertsArrays() {
    assertEquals(new ArrayType(Type.INT, 1), BcelUtil.fqBinaryNameToType("int[]"));
    assertEquals(new ArrayType(Type.INT, 2), BcelUtil.fqBinaryNameToType("int[][]"));
    assertEquals(
        new ArrayType(new ObjectType("java.lang.String"), 1),
        BcelUtil.fqBinaryNameToType("java.lang.String[]"));
  }

  @Test
  void typeToClassgetnameConvertsTypes() {
    assertEquals("int", BcelUtil.typeToClassgetname(Type.INT));
    assertEquals("double", BcelUtil.typeToClassgetname(Type.DOUBLE));
    assertEquals("java.lang.String", BcelUtil.typeToClassgetname(Type.STRING));
    assertEquals("[I", BcelUtil.typeToClassgetname(new ArrayType(Type.INT, 1)));
    assertEquals(
        "[[Ljava.lang.String;",
        BcelUtil.typeToClassgetname(new ArrayType(new ObjectType("java.lang.String"), 2)));
  }

  @Test
  void typeToClassReturnsTheRuntimeClass() {
    assertEquals(int.class, BcelUtil.typeToClass(Type.INT));
    assertEquals(String.class, BcelUtil.typeToClass(Type.STRING));
    assertEquals(int[].class, BcelUtil.typeToClass(new ArrayType(Type.INT, 1)));
  }

  @Test
  void typeToClassThrowsForAnUnknownClass() {
    Type absent = new ObjectType("no.such.Class");
    assertThrows(RuntimeException.class, () -> BcelUtil.typeToClass(absent));
  }

  // Array helpers

  @Test
  void prependToArrayAddsAtTheBeginning() {
    Type[] original = {Type.INT, Type.STRING};
    Type[] extended = BcelUtil.prependToArray(Type.DOUBLE, original);
    assertArrayEquals(new Type[] {Type.DOUBLE, Type.INT, Type.STRING}, extended);
    // The argument array is unchanged.
    assertArrayEquals(new Type[] {Type.INT, Type.STRING}, original);
  }

  @Test
  void prependToArrayHandlesAnEmptyArray() {
    assertArrayEquals(new Type[] {Type.INT}, BcelUtil.prependToArray(Type.INT, new Type[0]));
  }

  @Test
  void postpendToArrayAddsAtTheEnd() {
    Type[] original = {Type.INT, Type.STRING};
    Type[] extended = BcelUtil.postpendToArray(original, Type.DOUBLE);
    assertArrayEquals(new Type[] {Type.INT, Type.STRING, Type.DOUBLE}, extended);
    assertArrayEquals(new Type[] {Type.INT, Type.STRING}, original);
  }

  @Test
  void postpendToArrayHandlesAnEmptyArray() {
    assertArrayEquals(new Type[] {Type.INT}, BcelUtil.postpendToArray(new Type[0], Type.INT));
  }

  // Method predicates

  @Test
  void isConstructorRecognizesInit() {
    assertFalse(BcelUtil.isConstructor(method("sum")));
    assertFalse(BcelUtil.isConstructor(methodGen("sum")));
    assertFalse(BcelUtil.isConstructor(method("<clinit>")));
  }

  @Test
  void isClinitRecognizesClassInitializers() {
    assertTrue(BcelUtil.isClinit(method("<clinit>")));
    assertTrue(BcelUtil.isClinit(methodGen("<clinit>")));
    assertFalse(BcelUtil.isClinit(methodGen("sum")));
  }

  @Test
  void isMainRecognizesTheMainMethod() {
    assertTrue(BcelUtil.isMain(methodGen("main")));
    // sum is static, but is neither named "main" nor takes a String[].
    assertFalse(BcelUtil.isMain(methodGen("sum")));
    // getValue is neither static nor named "main".
    assertFalse(BcelUtil.isMain(methodGen("getValue")));
  }

  // 'ToString' methods

  @Test
  void methodDeclarationToStringDescribesTheDeclaration() {
    assertEquals("public static int sum(int)", BcelUtil.methodDeclarationToString(method("sum")));
    assertEquals(
        "public static int parseOrDefault(java.lang.String, int)",
        BcelUtil.methodDeclarationToString(method("parseOrDefault")));
    assertEquals("public int getValue()", BcelUtil.methodDeclarationToString(method("getValue")));
    assertEquals(
        "public static void main(java.lang.String[])",
        BcelUtil.methodDeclarationToString(method("main")));
  }

  @Test
  void accessFlagsToStringIsSpaceSeparatedAndCanonical() {
    assertEquals("public static", BcelUtil.accessFlagsToString(method("sum")));
    assertEquals("public", BcelUtil.accessFlagsToString(method("getValue")));
    assertEquals("static", BcelUtil.accessFlagsToString(method("<clinit>")));
  }

  @Test
  void instructionListToStringListsInstructions() {
    MethodGen mg = methodGen("sum");
    InstructionList il = nonNull(mg.getInstructionList(), "instruction list");
    String printed = BcelUtil.instructionListToString(il, mg.getConstantPool());
    assertTrue(printed.endsWith("\n"), "each instruction is on its own line: " + printed);
    assertEquals(il.getLength(), printed.lines().count(), "one line per instruction: " + printed);
    assertTrue(printed.contains("ireturn"), printed);
  }

  @Test
  void localVariablesToStringDescribesLocals() {
    String printed = BcelUtil.localVariablesToString(methodGen("sum"));
    assertTrue(printed.startsWith("Locals for "), printed);
    assertTrue(printed.contains("limit"), printed);
    assertTrue(printed.contains("[index "), printed);
  }

  @Test
  void attributeNameToStringNamesTheAttribute() {
    Attribute codeAttribute = code("sum");
    assertEquals("Code", BcelUtil.attributeNameToString(codeAttribute));
    assertEquals(
        "Code", BcelUtil.attributeNameToString(codeAttribute, Fixtures.classGen.getConstantPool()));
  }

  @Test
  void isLocalVariableTypeTableRejectsOtherAttributes() {
    assertFalse(
        BcelUtil.isLocalVariableTypeTable(code("sum"), Fixtures.classGen.getConstantPool()));
  }

  @Test
  void getConstantStringReadsUtf8Constants() {
    // A method's name is stored in the constant pool as a ConstantUtf8.
    int nameIndex = method("sum").getNameIndex();
    assertEquals(
        "sum", BcelUtil.getConstantString(Fixtures.javaClass.getConstantPool(), nameIndex));
  }

  @Test
  void getConstantStringRejectsAnUnsuitableConstant() {
    // Index 0 of a constant pool is always unused, so BCEL stores null there.
    assertThrows(
        Throwable.class, () -> BcelUtil.getConstantString(Fixtures.javaClass.getConstantPool(), 0));
  }

  // Consistency checks

  @Test
  @ResourceLock("BcelUtil.skipChecks")
  void skipChecksIsFalseByDefault() {
    assertFalse(BcelUtil.skipChecks);
  }

  @Test
  void checkMgenAcceptsAWellFormedMethod() {
    BcelUtil.checkMgen(methodGen("parseOrDefault"));
  }

  @Test
  void checkMgensAcceptsAWellFormedClass() {
    BcelUtil.checkMgens(Fixtures.classGen);
  }

  @Test
  void checkMgenRejectsADanglingExceptionHandler() {
    assertThrows(Error.class, () -> BcelUtil.checkMgen(methodWithDanglingHandler()));
  }

  @Test
  @ResourceLock("BcelUtil.skipChecks")
  void skipChecksSuppressesChecking() {
    boolean saved = BcelUtil.skipChecks;
    try {
      BcelUtil.skipChecks = true;
      // The same method that checkMgenRejectsADanglingExceptionHandler rejects is now accepted.
      BcelUtil.checkMgen(methodWithDanglingHandler());
    } finally {
      BcelUtil.skipChecks = saved;
    }
  }

  /**
   * Returns a method whose exception handler refers to an instruction that is not in the method's
   * instruction list, which is the inconsistency that {@link BcelUtil#checkMgen} detects.
   *
   * @return a method with a dangling exception handler
   */
  private static MethodGen methodWithDanglingHandler() {
    MethodGen mg =
        new MethodGen(
            Const.ACC_PUBLIC | Const.ACC_STATIC,
            Type.VOID,
            new Type[0],
            new String[0],
            "dangling",
            "Dangling",
            new InstructionList(new RETURN()),
            Fixtures.classGen.getConstantPool());
    InstructionList unrelated = new InstructionList(new NOP());
    mg.addExceptionHandler(
        unrelated.getStart(), unrelated.getStart(), unrelated.getStart(), Type.THROWABLE);
    return mg;
  }

  // Method mutation

  @Test
  void addToStartInsertsInstructionsAtTheFront() {
    MethodGen mg = methodGen("sum");
    InstructionList original = nonNull(mg.getInstructionList(), "instruction list");
    int originalLength = original.getLength();
    String originalFirst = original.getStart().getInstruction().getName();

    BcelUtil.addToStart(mg, new InstructionList(new NOP()));

    InstructionList updated = nonNull(mg.getInstructionList(), "instruction list");
    assertEquals(originalLength + 1, updated.getLength());
    assertEquals("nop", updated.getStart().getInstruction().getName());
    assertEquals(originalFirst, updated.getStart().getNext().getInstruction().getName());
  }

  @Test
  void makeMethodBodyEmptyLeavesOnlyAReturn() {
    MethodGen mg = methodGen("parseOrDefault");
    assertTrue(mg.getExceptionHandlers().length > 0, "the fixture method should have a handler");

    BcelUtil.makeMethodBodyEmpty(mg);

    InstructionList il = nonNull(mg.getInstructionList(), "instruction list");
    assertEquals(1, il.getLength());
    assertEquals("return", il.getStart().getInstruction().getName());
    assertEquals(0, mg.getExceptionHandlers().length);
    assertEquals(0, mg.getLineNumbers().length);
    assertEquals(0, nonNull(mg.getLocalVariables(), "local variables").length);
  }

  @Test
  void resetLocalsToFormalsKeepsOnlyParameters() {
    MethodGen mg = methodGen("sum");
    assertTrue(
        nonNull(mg.getLocalVariables(), "local variables").length > 1,
        "the fixture method should have a non-parameter local");
    assertEquals(
        "limit",
        nonNull(mg.getLocalVariables(), "local variables")[0].getName(),
        "the LocalVariableTable should carry the declared parameter name");

    BcelUtil.resetLocalsToFormals(mg);

    // sum is static and takes one parameter, so exactly one local remains.
    assertEquals(1, nonNull(mg.getLocalVariables(), "local variables").length);
    // resetLocalsToFormals names the new locals from MethodGen.getArgumentNames(), and BCEL's
    // MethodGen(Method, ...) constructor synthesizes "arg0", "arg1", ... rather than reading the
    // LocalVariableTable.  So the declared parameter name is lost.
    assertEquals("arg0", nonNull(mg.getLocalVariables(), "local variables")[0].getName());
  }

  @Test
  void resetLocalsToFormalsAddsThisForInstanceMethods() {
    MethodGen mg = methodGen("getValue");

    BcelUtil.resetLocalsToFormals(mg);

    assertEquals(1, nonNull(mg.getLocalVariables(), "local variables").length);
    assertEquals("this", nonNull(mg.getLocalVariables(), "local variables")[0].getName());
  }

  @Test
  void removeLocalVariableTypeTablesLeavesOtherAttributes() {
    MethodGen mg = methodGen("sum");
    int before = mg.getCodeAttributes().length;
    BcelUtil.removeLocalVariableTypeTables(mg);
    for (Attribute a : mg.getCodeAttributes()) {
      assertFalse(BcelUtil.isLocalVariableTypeTable(a, mg.getConstantPool()));
    }
    // sum uses no type variables, so it has no LocalVariableTypeTable to remove.
    assertEquals(before, mg.getCodeAttributes().length);
  }

  // Dumping

  @Test
  void dumpWritesAReadableFile(@TempDir Path tempDir) throws IOException {
    BcelUtil.dump(Fixtures.javaClass, tempDir.toFile());

    Path dumped = tempDir.resolve(Fixtures.javaClass.getClassName() + ".bcel");
    assertTrue(Files.exists(dumped), "dump should create " + dumped);
    String contents = Files.readString(dumped, StandardCharsets.UTF_8);
    assertTrue(
        contents.startsWith("class " + Fixtures.javaClass.getClassName() + " extends "), contents);
    assertTrue(contents.contains("Fields"), contents);
    assertTrue(contents.contains("Methods"), contents);
    assertTrue(contents.contains("Constant Pool:"), contents);
    assertTrue(contents.contains("sum"), contents);
  }

  @Test
  void dumpCreatesTheDirectoryNamedByAString(@TempDir Path tempDir) {
    Path subdir = tempDir.resolve("created-by-dump");
    BcelUtil.dump(Fixtures.javaClass, subdir.toString());
    assertTrue(Files.exists(subdir.resolve(Fixtures.javaClass.getClassName() + ".bcel")));
  }

  // Miscellaneous

  @Test
  void javaVersionMatchesTheRunningRuntime() {
    assertEquals(Runtime.version().feature(), BcelUtil.javaVersion);
    assertTrue(BcelUtil.javaVersion >= 17, "this library requires Java 17 or later");
  }
}
