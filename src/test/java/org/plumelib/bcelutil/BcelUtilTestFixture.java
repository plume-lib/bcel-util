package org.plumelib.bcelutil;

/**
 * A class that the tests read with BCEL, to obtain bytecode to operate on. Nothing calls its
 * methods; they exist so that the tests have a class containing two constructors, a class
 * initializer, a main method, local variables, a loop, and an exception handler.
 */
public class BcelUtilTestFixture {

  /**
   * A value computed by a class initializer. The initializer is not a compile-time constant, so the
   * compiled class has a {@code <clinit>} method.
   */
  private static final int computed;

  static {
    computed = Integer.parseInt("42");
  }

  /** The value of this object. */
  private final int value;

  /** Creates a BcelUtilTestFixture whose value is 0. */
  public BcelUtilTestFixture() {
    this(0);
  }

  /**
   * Creates a BcelUtilTestFixture.
   *
   * @param value the value of the new object
   */
  public BcelUtilTestFixture(int value) {
    this.value = value;
  }

  /**
   * Returns the value of this object.
   *
   * @return the value of this object
   */
  public int getValue() {
    return value;
  }

  /**
   * Returns the value that the class initializer computed.
   *
   * @return the value that the class initializer computed
   */
  public static int getComputed() {
    return computed;
  }

  /**
   * Returns the sum of the integers in the range [0..limit). Written as a loop so that the compiled
   * method has local variables and a backward branch.
   *
   * @param limit the exclusive upper bound of the range to sum
   * @return the sum of the integers in the range [0..limit)
   */
  public static int sum(int limit) {
    int total = 0;
    for (int i = 0; i < limit; i++) {
      total += i;
    }
    return total;
  }

  /**
   * Returns the integer that {@code s} represents, or {@code fallback} if {@code s} does not
   * represent an integer. Written with a catch block so that the compiled method has an exception
   * handler.
   *
   * @param s the string to parse
   * @param fallback the value to return if {@code s} does not represent an integer
   * @return the integer that {@code s} represents, or {@code fallback}
   */
  public static int parseOrDefault(String s, int fallback) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /**
   * Does nothing. Exists so that the class has a method that {@link BcelUtil#isMain} accepts.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    // Do nothing.
  }

  /**
   * An interface whose method has no body. Tests use it when they need a {@code MethodGen} whose
   * instruction list is null.
   */
  @SuppressWarnings("PMD.ImplicitFunctionalInterface") // never used as a lambda
  public interface NoBody {
    /** Does nothing. */
    void noBody();
  }
}
