package org.plumelib.bcelutil;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.MethodGen;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Gives the tests access to the BCEL representation of {@link BcelUtilTestFixture}. The class is
 * read once, when this class is initialized, rather than by a JUnit lifecycle method, so that the
 * fields can be final.
 */
final class Fixtures {

  /** The BCEL representation of {@link BcelUtilTestFixture}. */
  static final JavaClass javaClass = lookup(BcelUtilTestFixture.class);

  /** A ClassGen for {@link #javaClass}. */
  static final ClassGen classGen = new ClassGen(javaClass);

  /** The BCEL representation of {@link BcelUtilTestFixture.NoBody}. */
  static final JavaClass noBodyClass = lookup(BcelUtilTestFixture.NoBody.class);

  /** A ClassGen for {@link #noBodyClass}. */
  static final ClassGen noBodyClassGen = new ClassGen(noBodyClass);

  /** This class is a collection of static members; it does not represent anything. */
  private Fixtures() {
    throw new Error("do not instantiate");
  }

  /**
   * Returns the BCEL representation of the given class.
   *
   * @param clazz the class to read
   * @return the BCEL representation of {@code clazz}
   */
  private static JavaClass lookup(Class<?> clazz) {
    try {
      return Repository.lookupClass(clazz);
    } catch (ClassNotFoundException e) {
      throw new AssertionError("BCEL cannot read " + clazz.getName(), e);
    }
  }

  /**
   * Returns the fixture class's method with the given name.
   *
   * @param name the name of the method to return
   * @return the fixture class's method named {@code name}
   */
  static Method method(String name) {
    Method result = null;
    for (Method m : javaClass.getMethods()) {
      if (m.getName().equals(name)) {
        if (result != null) {
          throw new AssertionError(
              "multiple methods named " + name + " in " + javaClass.getClassName());
        }
        result = m;
      }
    }
    if (result == null) {
      throw new AssertionError("no method named " + name + " in " + javaClass.getClassName());
    }
    return result;
  }

  /**
   * Returns the fixture class's method with the given name and signature.
   *
   * `@param` name the name of the method to return
   * `@param` signature the JVM signature of the method to return
   * `@return` the fixture class's method named {`@code` name} with signature {`@code` signature}
   */
  static Method method(String name, String signature) {
    for (Method m : javaClass.getMethods()) {
      if (m.getName().equals(name) && m.getSignature().equals(signature)) {
        return m;
      }
    }
    throw new AssertionError(
        "no method " + name + signature + " in " + javaClass.getClassName());
  }

  /**
   * Returns a MethodGen for the fixture class's method with the given name.
   *
   * @param name the name of the method to return
   * @return a MethodGen for the fixture class's method named {@code name}
   */
  static MethodGen methodGen(String name) {
    return new MethodGen(method(name), classGen.getClassName(), classGen.getConstantPool());
  }

  /**
   * Returns the Code attribute of the fixture class's method with the given name.
   *
   * @param name the name of the method whose Code attribute to return
   * @return the Code attribute of the fixture class's method named {@code name}
   */
  static Code code(String name) {
    return nonNull(method(name).getCode(), "Code attribute of " + name);
  }

  /**
   * Returns its first argument, which the test expects to be non-null. {@code
   * Objects.requireNonNull} cannot serve this purpose, because the Checker Framework declares its
   * parameter to be non-null.
   *
   * @param <T> the type of the value
   * @param value the value to check
   * @param description what the value is, for the failure message
   * @return {@code value}
   */
  static <T> T nonNull(@Nullable T value, String description) {
    if (value == null) {
      throw new AssertionError(description + " should not be null");
    }
    return value;
  }

  /**
   * Returns a MethodGen for a method that has no body, and therefore no instruction list.
   *
   * @return a MethodGen whose instruction list is null
   */
  static MethodGen noBodyMethodGen() {
    Method[] methods = noBodyClass.getMethods();
    if (methods.length != 1) {
      throw new AssertionError(
          noBodyClass.getClassName() + " should declare exactly one method, not " + methods.length);
    }
    return new MethodGen(
        methods[0], noBodyClassGen.getClassName(), noBodyClassGen.getConstantPool());
  }
}
