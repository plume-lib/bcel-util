# BCEL-Util change log

## 1.2.3 (June 5, 2025)

- Tested under Java 24
- Both compiles and runs under Java 8.

## 1.2.2 (May 15, 2022)

- Tested under Java 20
- No longer compiles under Java 8, but still runs under Java 8.

## 1.2.1 (December 13, 2022)

Removed deprecated methods:
 * add_new_argument
 * fetch_current_stack_map_table
 * classnameToType

Removed problematic dependency on `error_prone_core`.

## 1.2.0 (December 12, 2022)

Changed naming convention from snake_case to camelCase, which is idiomatic in Java.
This is a huge incompatibility with the previous version, 1.1.16.

## 1.1.16 (October 12, 2022)

- Compiles and runs under Java 18.
- `BcelUtil.javaVersion` works on early access releases.

## 1.1.15 (June 29, 2022)

- Now supports Groovy-generated class files.

## 1.1.14 (December 29, 2021)

- Builds and runs under JDK 17, but not JDK 16.  (Still builds and runs under JDK 8 and 11.)

## 1.1.13 (July 22, 2021)

- Builds and runs under JDK 16.  (Still builds and runs under JDK 8 and 11.)
- Improved handling of gaps (missing local vars) in local variable table.

## 1.1.12 (May 28, 2021)

- Fix exception handler processing for insert instruction list

## 1.1.11 (May 5, 2021)

- SimpleLog supports logging to a file

## 1.1.10

- Update dependencies
- Tweak documentation

## 1.1.9

- Fixed bug in stack map processing
- Prefer new method `binaryNameToType` to `classnameToType`

## 1.1.8

- Add new static field `BcelUtil.javaVersion`

## 1.1.7

- Prefer new method `fqBinaryNameToType` to `classnameToType`

## 1.1.5

- Reduce dependencies

## 1.1.4

- Use reflection-util package instead of signature-util
- Reduce size of diffs against Apache BCEL

## 1.1.0

- Deprecate JvmUtil class; use Signature class instead
- Improve efficiency of string operations

## 1.0.0

- Require Java 8
- Improve code that processes uninitialized new instructions
