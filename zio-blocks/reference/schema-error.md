# SchemaError

> `SchemaError` is a **structured error type** for schema operations in ZIO Blocks. It represents one or more validation, conversion, or structural failures that occurred while decoding, encoding, or transforming data, each annotated with a [`DynamicOptic`](./dynamic-optic.md) path that pinpoints the failing location in the data structure.

`SchemaError` is a **structured error type** for schema operations in ZIO Blocks. It represents one or more validation, conversion, or structural failures that occurred while decoding, encoding, or transforming data, each annotated with a [`DynamicOptic`](./dynamic-optic.md) path that pinpoints the failing location in the data structure.

```scala
final case class SchemaError(errors: ::[SchemaError.Single])
  extends Exception with NoStackTrace
```

Here is the full structure of `SchemaError`:

```
SchemaError
 └── errors: ::[Single]       (non-empty list — always at least one failure)
      │
      └── Single (sealed trait)
           │ ├── source: DynamicOptic   (path to the failing location)
           │ └── message: String        (human-readable description)
           │
           ├── ConversionFailed       (type or value conversion failed)
           ├── MissingField           (required field absent)
           ├── DuplicatedField        (same field key appears more than once)
           ├── ExpectationMismatch    (wrong DynamicValue variant encountered)
           ├── UnknownCase            (unrecognised sealed-trait discriminator)
           └── Message                (free-form message, optional path)
```

`SchemaError`:

- Aggregates multiple independent failures into a single error value
- Annotates every failure with a precise traversal path through the data
- Extends `Exception` so it can be thrown and caught with standard JVM mechanisms
- Suppresses stack traces via `NoStackTrace` — error location is conveyed through the path, not the JVM stack

## Motivation

When decoding a complex nested value, a single structural problem — a missing field, a type mismatch, an unknown case discriminator — must be reported together with the location where it occurred. In a large schema, multiple independent problems can coexist, and surfacing them all at once saves the caller round-trips.

Every `Schema#fromDynamicValue`, every `Codec#decode`, and every optic traversal that can fail returns `Either[SchemaError, A]`. The same type carries both structural errors (missing fields, wrong types) and domain validation errors (value out of range, blank string), so callers deal with a single error channel.

`SchemaError` was introduced to replace the earlier `JsonError` and `DynamicValueError` types that existed as separate error channels for each format. Those types used string concatenation (`"error1; error2"`) to combine failures via `++`, which silently discarded path information from the second error onward. `SchemaError` solves this by maintaining a non-empty list (`::`) of `Single` failures — each one independently annotated with its own `DynamicOptic` path — so no information is lost during aggregation.

Here is a quick taste of how `SchemaError` behaves:

```scala

// Create a simple message error
val err = SchemaError("Age must be positive")

// Annotate with the location in the data
val located = SchemaError.missingField(Nil, "email").atField("user")
println(located.message)   // Missing field 'email' at: .user

// Combine two independent failures
val combined = SchemaError("name is blank") ++ SchemaError("age is negative")
println(combined.errors.length)  // 2
```

## Construction / Creating Instances

### `SchemaError.apply`

The simplest constructor — creates a free-form `Message` error at the root path:

```scala
object SchemaError {
  def apply(details: String): SchemaError
}
```

```scala

val err = SchemaError("Value must be positive")
println(err.message)  // Value must be positive
```

### `SchemaError.message`

Creates a `Message` error with a free-form description and an optional `DynamicOptic` path. When no path is supplied it defaults to the root.

```scala
object SchemaError {
  def message(details: String, path: DynamicOptic = DynamicOptic.root): SchemaError
}
```

Here we create a message error at the root, and another with an explicit path:

```scala

// Root-level message (same as SchemaError.apply)
val atRoot = SchemaError.message("Unexpected null")
println(atRoot.message)  // Unexpected null

// Message with an explicit path
val path   = DynamicOptic.root.field("address")
val atPath = SchemaError.message("Unexpected null", path)
println(atPath.message)  // Unexpected null at: .address
```

### `SchemaError.validationFailed`

Convenience factory for validation failures. Equivalent to `SchemaError.conversionFailed(Nil, message)`, designed for smart constructors that return string-based error messages.

```scala
object SchemaError {
  def validationFailed(message: String): SchemaError
}
```

Here is an example:

```scala

val err = SchemaError.validationFailed("Age must be between 0 and 150")
println(err.message)  // Age must be between 0 and 150
```

### `SchemaError.conversionFailed`

Creates a `ConversionFailed` error for a failed type or value conversion. Two overloads exist.

```scala
object SchemaError {
  def conversionFailed(trace: List[DynamicOptic.Node], details: String): SchemaError
  def conversionFailed(contextMessage: String, cause: SchemaError): SchemaError
}
```

The first overload is used by codecs — `trace` is the list of path nodes accumulated during decoding. Pass `Nil` when constructing an error manually and use the `at*` methods to set the path. The second overload wraps a nested `SchemaError` with additional context; the nested failures are rendered under a "Caused by:" section.

```scala

// Root-level conversion failure
val err = SchemaError.conversionFailed(Nil, "Expected a positive integer")
println(err.message)  // Expected a positive integer

// Wrapping a nested failure with context
val inner = SchemaError("name must not be empty") ++
            SchemaError("age must be positive")
val outer = SchemaError.conversionFailed("Person construction failed", inner)
println(outer.message)
// Person construction failed
//   Caused by:
//   - name must not be empty
//   - age must be positive
```

### `SchemaError.missingField`

Creates a `MissingField` error indicating that a required field was absent from the decoded representation.

```scala
object SchemaError {
  def missingField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError
}
```

```scala

val err = SchemaError.missingField(Nil, "email").atField("user")
println(err.message)  // Missing field 'email' at: .user
```

### `SchemaError.duplicatedField`

Creates a `DuplicatedField` error indicating that the same field key appeared more than once in the encoded form.

```scala
object SchemaError {
  def duplicatedField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError
}
```

```scala

val err = SchemaError.duplicatedField(Nil, "id").atField("record")
println(err.message)  // Duplicated field 'id' at: .record
```

### `SchemaError.expectationMismatch`

Creates an `ExpectationMismatch` error indicating that the encountered `DynamicValue` variant does not match what the schema expected.

```scala
object SchemaError {
  def expectationMismatch(trace: List[DynamicOptic.Node], expectation: String): SchemaError
}
```

```scala

val err = SchemaError
  .expectationMismatch(Nil, "Expected Record, got Sequence")
  .atField("data")
println(err.message)  // Expected Record, got Sequence at: .data
```

### `SchemaError.unknownCase`

Creates an `UnknownCase` error indicating that the decoded discriminator value does not correspond to any known variant of a sealed trait.

```scala
object SchemaError {
  def unknownCase(trace: List[DynamicOptic.Node], caseName: String): SchemaError
}
```

```scala

val err = SchemaError.unknownCase(Nil, "Triangle").atField("shape")
println(err.message)  // Unknown case 'Triangle' at: .shape
```

## Core Operations

### Error Messages

#### `message`

Returns all individual error messages joined with newlines.

```scala
final case class SchemaError(errors: ::[SchemaError.Single]) {
  def message: String
}
```

```scala

val err = SchemaError("first failure") ++ SchemaError("second failure")
println(err.message)
// first failure
// second failure
```

#### `getMessage`

Delegates to `message`. Because `SchemaError` extends `Exception`, `getMessage` is called by the JVM when the exception is printed or logged by frameworks.

```scala
final case class SchemaError(errors: ::[SchemaError.Single]) {
  def getMessage: String
}
```

```scala

val err = SchemaError("something went wrong")
assert(err.getMessage == err.message)
```

### Error Aggregation

#### `++`

Combines two `SchemaError` values into one, preserving all individual `Single` failures from both sides. We use `++` to accumulate errors from independent parts of a schema — for example, multiple record fields that are each decoded independently.

```scala
final case class SchemaError(errors: ::[SchemaError.Single]) {
  def ++(other: SchemaError): SchemaError
}
```

```scala

val nameError = SchemaError.missingField(Nil, "name")
val ageError  = SchemaError.conversionFailed(Nil, "Age must be positive")
val combined  = nameError ++ ageError

println(combined.errors.length)   // 2
println(combined.message)
// Missing field 'name' at: .
// Age must be positive
```

`++` is associative: `(a ++ b) ++ c` and `a ++ (b ++ c)` produce the same set of errors.

### Path Annotation

Path annotation methods prepend a path segment to the `source` of every `SchemaError.Single` inside the error. Codecs call these methods as they unwind the call stack — the innermost call adds the innermost path segment, and the outermost call adds the outermost one.

#### `atField`

Prepends a record field access to the path of all errors.

```scala
final case class SchemaError(errors: ::[SchemaError.Single]) {
  def atField(name: String): SchemaError
}
```

```scala

// Codec decoding 'city' inside 'address' inside 'user'
val err = SchemaError.missingField(Nil, "city")
  .atField("address")  // called by the address codec
  .atField("user")     // called by the user codec
println(err.message)   // Missing field 'city' at: .user.address
```

#### `atIndex`

Prepends a sequence index access to the path of all errors.

```scala
final case class SchemaError(errors: ::[SchemaError.Single]) {
  def atIndex(index: Int): SchemaError
}
```

```scala

val err = SchemaError("invalid phone number").atIndex(2).atField("phones")
println(err.message)  // invalid phone number at: .phones[2]
```

#### `atCase`

Prepends a sealed-trait case access to the path of all errors. In the compact path notation used by `Message`, the case name appears as `<CaseName>`.

```scala
final case class SchemaError(errors: ::[SchemaError.Single]) {
  def atCase(name: String): SchemaError
}
```

```scala

val err = SchemaError("conversion failed").atField("value").atCase("Right")
println(err.message)  // conversion failed at: <Right>.value
```

#### `atKey`

Prepends a map key access to the path of all errors. The key is a [`DynamicValue`](./dynamic-value.md), rendered with `{key}` in the compact path notation.

```scala
final case class SchemaError(errors: ::[SchemaError.Single]) {
  def atKey(key: DynamicValue): SchemaError
}
```

```scala

val key = DynamicValue.string("config")
val err = SchemaError("missing required entry").atKey(key)
println(err.message)  // missing required entry at: {"config"}
```

### Path Chaining

All path methods can be chained. Each call prepends to the existing path, so the outermost call appears as the leftmost segment in the rendered message.

```scala

val err = SchemaError("value out of range")
  .atField("amount")       // innermost — added first
  .atIndex(0)
  .atCase("Credit")
  .atField("transactions") // outermost — added last
println(err.message)
// value out of range at: .transactions<Credit>[0].amount
```

Path annotation applies to **every** `Single` inside the error, so combined errors accumulate paths correctly:

```scala

val error1 = SchemaError.missingField(Nil, "name")
val error2 = SchemaError.conversionFailed(Nil, "age must be positive")
val combined = (error1 ++ error2).atField("person")

// Both errors now include the "person" field prefix
println(combined.errors.head.source.nodes.nonEmpty)  // true (name)
println(combined.errors.tail.head.source.nodes.nonEmpty)  // true (age)
```

## Subtypes / Variants

`SchemaError.Single` is the sealed base trait for every individual failure. Each variant carries a `source: DynamicOptic` and a `message: String`.

```
SchemaError.Single (sealed trait)
 ├── SchemaError.IntoError (sealed sub-trait — marks conversion errors)
 │   └── ConversionFailed(source, details, cause: Option[SchemaError])
 ├── MissingField(source, fieldName)
 ├── DuplicatedField(source, fieldName)
 ├── ExpectationMismatch(source, expectation)
 ├── UnknownCase(source, caseName)
 └── Message(source, details)
```

| Subtype               | Factory                                | Typical cause                                                                             |
|-----------------------|----------------------------------------|-------------------------------------------------------------------------------------------|
| `ConversionFailed`    | `conversionFailed`, `validationFailed` | Type conversion or smart-constructor failure; may carry a nested `SchemaError` as `cause` |
| `MissingField`        | `missingField`                         | Required field absent in the decoded representation                                       |
| `DuplicatedField`     | `duplicatedField`                      | Same field key appears more than once                                                     |
| `ExpectationMismatch` | `expectationMismatch`                  | Wrong `DynamicValue` variant encountered                                                  |
| `UnknownCase`         | `unknownCase`                          | Discriminator names an unrecognised sealed-trait variant                                  |
| `Message`             | `message`, `apply`                     | Free-form error with an optional path                                                     |

We can pattern match on `errors` to handle specific failure kinds:

```scala

val err = SchemaError.missingField(Nil, "email") ++
          SchemaError.conversionFailed(Nil, "age must be positive")

err.errors.foreach {
  case SchemaError.MissingField(source, fieldName) =>
    println(s"Missing '$fieldName' at ${source.toScalaString}")
  case SchemaError.ConversionFailed(source, details, _) =>
    println(s"Conversion failed: $details")
  case other =>
    println(other.message)
}
```

### `SchemaError.IntoError`

`IntoError` is a sealed sub-trait of `Single` that marks errors produced during `Into` (type conversion) operations. Its only current implementation is `ConversionFailed`. Codec code pattern-matches on `IntoError` to distinguish conversion errors from structural schema errors:

```scala
sealed trait IntoError extends SchemaError.Single {
  def source: DynamicOptic
}
```

### `SchemaError.ConversionFailed`

Represents a failed type or value conversion. When a `cause: Option[SchemaError]` is present, the rendered `message` includes a "Caused by:" section showing the nested failures.

```scala

// Single nested cause
val inner1 = SchemaError.conversionFailed(Nil, "name is blank")
val outer1 = SchemaError.conversionFailed("Person construction failed", inner1)
println(outer1.message)
// Person construction failed
//   Caused by: name is blank

// Multiple nested causes
val inner2 = SchemaError.conversionFailed(Nil, "name is blank") ++
             SchemaError.conversionFailed(Nil, "age is negative")
val outer2 = SchemaError.conversionFailed("Person construction failed", inner2)
println(outer2.message)
// Person construction failed
//   Caused by:
//   - name is blank
//   - age is negative
```

## Integration

### With Schema Decoding

`Schema#fromDynamicValue` returns `Either[SchemaError, A]`. Every codec accumulates path nodes during decoding and calls `atField`, `atIndex`, or `atCase` as it unwinds, producing a fully-annotated `SchemaError` on failure.

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val result: Either[SchemaError, Person] =
  Schema[Person].fromDynamicValue(Schema[Person].toDynamicValue(Person("Alice", 30)))

result match {
  case Right(person) => println(s"Decoded: $person")
  case Left(err)     => println(s"Error:\n${err.message}")
}
```

See [Schema](./schema.md) and [DynamicValue](./dynamic-value.md) for the full encoding and decoding API.

### With Schema#transform

`Schema#transform` accepts `to` and `from` functions that can throw `SchemaError` to signal validation failures during encoding or decoding. We use `SchemaError.validationFailed` (which wraps a `ConversionFailed`) to turn a smart-constructor rejection into a structured schema error:

```scala

case class PositiveInt private (value: Int)

object PositiveInt {
  def make(n: Int): PositiveInt =
    if (n > 0) PositiveInt(n)
    else throw SchemaError.validationFailed("must be positive")

  implicit val schema: Schema[PositiveInt] =
    Schema[Int].transform(make, _.value)
}
```

When `make` throws a `SchemaError`, the codec catches it, preserves the full error (including any path already annotated), and surfaces it as `Left(schemaError)` from `Schema#fromDynamicValue` or `Codec#decode`. See [Schema](./schema.md) for the full `transform` API.

A runnable version of this example, including composite types and error aggregation, is available in the `schema-examples` module:

```bash
sbt "schema-examples/runMain schemaerror.SchemaErrorExample"
```

### With Validation

The [Validation](./validation.md) system uses `SchemaError` to report constraint violations. When a `PrimitiveType` carries a `Validation` and the value fails the check, the codec surfaces a `SchemaError.ConversionFailed` at the appropriate path.

### With DynamicOptic and Optics

Path annotation (`atField`, `atIndex`, `atKey`, `atCase`) builds a [`DynamicOptic`](./dynamic-optic.md) inside each error. Operations such as `DynamicValue#setOrFail` and `DynamicValue#modifyAtPathOrFail` return `Either[SchemaError, DynamicValue]`, using the same factory methods.

```scala

val data   = DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2))
val optic  = DynamicOptic.root.at(10)

val result: Either[SchemaError, DynamicValue] = data.setOrFail(optic, DynamicValue.int(99))
result match {
  case Left(err) => println(err.message)  // index out of range or similar
  case Right(v)  => println(v)
}
```

### As an Exception

Because `SchemaError` extends `Exception`, it can be thrown and caught with standard try/catch (In functional code, prefer `Either[SchemaError, A]` or `Option[SchemaError]` instead):

```scala

try {
  throw SchemaError("Unexpected data shape")
} catch {
  case e: SchemaError => println(s"Caught schema error: ${e.getMessage}")
}
```

:::note
`SchemaError` extends `scala.util.control.NoStackTrace`. Stack traces are suppressed for performance — error location is conveyed through the `DynamicOptic` path inside each `Single`, not the JVM stack.
:::
