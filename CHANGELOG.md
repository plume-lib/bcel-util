# BCEL-Util change log

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
