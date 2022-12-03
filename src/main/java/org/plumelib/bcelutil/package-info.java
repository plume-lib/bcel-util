/**
 * Utility methods for BCEL, the bytecode engineering library.
 *
 * <p>The Byte Code Engineering Library (Apache Commons BCEL) enables users to analyze, create, and
 * manipulate (binary) Java class files (those ending with .class). Classes are represented by
 * objects which contain all the symbolic information of the given class: methods, fields, and byte
 * code instructions, in particular. Such objects can be read from an existing file, be transformed
 * by a program (e.g., a class loader at run time) and written to a file again.
 *
 * <p>If one wishes to inspect a Java class file, a rough program template would be as follows:
 *
 * <pre>
 *   import org.apache.bcel.classfile.*;
 *
 *   try {
 *     // Parse the bytes of the classfile, die on any errors
 *     ClassParser parser = new ClassParser("path to class file of interest");
 *     JavaClass jc = parser.parse();
 *   } catch (Exception e) {
 *     throw new RuntimeException("Unexpected error", e);
 *   }
 * </pre>
 *
 * <p>At this point one would use the methods of {@link org.apache.bcel.classfile.JavaClass}, the
 * other members of the {@link org.apache.bcel.classfile} package and {@link
 * org.plumelib.bcelutil.BcelUtil} to explore the class file of interest.
 *
 * @see org.plumelib.bcelutil.InstructionListUtils InstructionListUtils for notes on modifing a Java
 *     class file
 * @see <a href="https://commons.apache.org/proper/commons-bcel/index.html">Commons BCEL web site
 *     for details about the BCEL library</a>
 */
package org.plumelib.bcelutil;
