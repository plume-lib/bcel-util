# BCEL-Util change log

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
