# Extension Syntax

> ZIO Blocks provides convenient extension methods on any value that has a `Schema`. These methods give you fluent, type-safe access to JSON encoding/decoding, pretty-printing, and patching operations directly on your values.

ZIO Blocks provides convenient extension methods on any value that has a `Schema`. These methods give you fluent, type-safe access to JSON encoding/decoding, pretty-printing, and patching operations directly on your values.

## Import

To use the extension syntax, import the schema package:

```scala

```

This brings the extension methods into scope for any type with an implicit `Schema` instance.

## Quick Example

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val alice = Person("Alice", 30)

// Convert to JSON
val json = alice.toJson              // Json AST
val jsonStr = alice.toJsonString     // {"name":"Alice","age":30}
val jsonBytes = alice.toJsonBytes    // Array[Byte]

// Parse from JSON
val parsed = """{"name":"Bob","age":25}""".fromJson[Person]
// Right(Person("Bob", 25))

// Pretty-print
val shown = alice.show               // Record { name = Alice, age = 30 }

// Compute and apply patches
val bob = Person("Bob", 30)
val patch = alice.diff(bob)          // Patch that changes name
val result = alice.applyPatch(patch) // Person("Bob", 30)
```

## JSON Encoding Methods

### toJson

Converts a value to a `Json` AST (abstract syntax tree):

```scala

case class Point(x: Int, y: Int)
object Point {
  implicit val schema: Schema[Point] = Schema.derived
}

val point = Point(10, 20)
val json = point.toJson
// Json.Object(Vector("x" -> Json.Number(10), "y" -> Json.Number(20)))

// Navigate and extract values
json.get("x").as[Int]  // Right(10)
json.get("y").as[Int]  // Right(20)
```

### toJsonString

Converts a value directly to a JSON string:

```scala

case class User(name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val user = User("Alice", "alice@example.com")
val jsonStr = user.toJsonString
// {"name":"Alice","email":"alice@example.com"}
```

### toJsonBytes

Converts a value to a UTF-8 encoded byte array. This is useful for efficient serialization when working with binary protocols or network I/O:

```scala

case class Message(id: Long, content: String)
object Message {
  implicit val schema: Schema[Message] = Schema.derived
}

val msg = Message(42, "Hello, world!")
val bytes: Array[Byte] = msg.toJsonBytes

// Useful for sending over the wire
// socket.write(bytes)
```

## JSON Decoding Methods

### fromJson (on String)

Parses a JSON string into a typed value:

```scala

case class Config(host: String, port: Int)
object Config {
  implicit val schema: Schema[Config] = Schema.derived
}

val jsonStr = """{"host":"localhost","port":8080}"""
val result: Either[SchemaError, Config] = jsonStr.fromJson[Config]
// Right(Config("localhost", 8080))

// Handle parsing errors
val invalid = """{"host":"localhost"}"""  // missing port
val error = invalid.fromJson[Config]
// Left(SchemaError(...))
```

### fromJson (on Array[Byte])

Parses a UTF-8 byte array into a typed value:

```scala

case class Event(name: String, timestamp: Long)
object Event {
  implicit val schema: Schema[Event] = Schema.derived
}

val jsonBytes = """{"name":"click","timestamp":1234567890}"""
  .getBytes(StandardCharsets.UTF_8)

val result: Either[SchemaError, Event] = jsonBytes.fromJson[Event]
// Right(Event("click", 1234567890))
```

## Pretty-Printing

### show

Converts a value to a human-readable string representation using `DynamicValue`:

```scala

case class Address(street: String, city: String, zip: String)
object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

val addr = Address("123 Main St", "Springfield", "12345")
val shown = addr.show
// Record { street = 123 Main St, city = Springfield, zip = 12345 }
```

This is useful for debugging and logging, as it provides a consistent, schema-aware representation of any value.

## Patching Operations

ZIO Blocks includes a powerful patching system for computing and applying differences between values.

### diff

Computes the difference between two values, returning a `Patch`:

```scala

case class Product(name: String, price: Double, stock: Int)
object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

val before = Product("Widget", 9.99, 100)
val after = Product("Widget", 12.99, 95)

val patch = before.diff(after)
// Patch contains: price changed from 9.99 to 12.99, stock from 100 to 95

patch.isEmpty  // false
```

An identical comparison produces an empty patch:

```scala

case class Item(id: Int, name: String)
object Item {
  implicit val schema: Schema[Item] = Schema.derived
}

val item = Item(1, "Example")
val samePatch = item.diff(item)
samePatch.isEmpty  // true
```

### applyPatch

Applies a patch to a value, returning the modified value. Uses lenient mode by default, which means operations that can't be applied are silently skipped:

```scala

case class Counter(name: String, value: Int)
object Counter {
  implicit val schema: Schema[Counter] = Schema.derived
}

val counter = Counter("hits", 100)
val updated = Counter("hits", 150)

val patch = counter.diff(updated)
val result = counter.applyPatch(patch)
// Counter("hits", 150)
```

### applyPatchStrict

Applies a patch strictly, returning an `Either` that contains an error if any operation fails:

```scala

case class Record(id: String, version: Int)
object Record {
  implicit val schema: Schema[Record] = Schema.derived
}

val record = Record("abc", 1)
val newRecord = Record("abc", 2)
val patch = record.diff(newRecord)

val result: Either[SchemaError, Record] = record.applyPatchStrict(patch)
// Right(Record("abc", 2))

// Empty patch also succeeds
val emptyResult = record.applyPatchStrict(Patch.empty[Record])
// Right(Record("abc", 1))
```

## Roundtrip Examples

### JSON Roundtrip

```scala

case class Order(id: Long, items: List[String], total: BigDecimal)
object Order {
  implicit val schema: Schema[Order] = Schema.derived
}

val order = Order(12345, List("apple", "banana"), BigDecimal("19.99"))

// String roundtrip
val jsonStr = order.toJsonString
val decoded1 = jsonStr.fromJson[Order]
// Right(Order(12345, List("apple", "banana"), 19.99))

// Bytes roundtrip
val jsonBytes = order.toJsonBytes
val decoded2 = jsonBytes.fromJson[Order]
// Right(Order(12345, List("apple", "banana"), 19.99))
```

### Patch Roundtrip

```scala

case class Settings(theme: String, fontSize: Int, notifications: Boolean)
object Settings {
  implicit val schema: Schema[Settings] = Schema.derived
}

val defaults = Settings("light", 14, true)
val customized = Settings("dark", 16, false)

// Compute patch and apply
val patch = defaults.diff(customized)
val result = defaults.applyPatch(patch)
// Settings("dark", 16, false)

assert(result == customized)
```

## Edge Cases

### Special Characters

The JSON encoding handles special characters, Unicode, and escape sequences correctly:

```scala

case class Text(content: String)
object Text {
  implicit val schema: Schema[Text] = Schema.derived
}

// Special characters
val special = Text("""John "Jack" O'Brien""")
val json1 = special.toJsonString
val decoded1 = json1.fromJson[Text]
// Right(Text("John \"Jack\" O'Brien"))

// Unicode
val unicode = Text("日本語テキスト")
val json2 = unicode.toJsonString
val decoded2 = json2.fromJson[Text]
// Right(Text("日本語テキスト"))
```

### Empty and Null Values

```scala

case class Profile(name: String, bio: Option[String])
object Profile {
  implicit val schema: Schema[Profile] = Schema.derived
}

// Empty strings
val empty = Profile("", None)
val json = empty.toJsonString
val decoded = json.fromJson[Profile]
// Right(Profile("", None))

// Optional fields
val withBio = Profile("Alice", Some("Developer"))
val withoutBio = Profile("Bob", None)
```

## Scala 2 vs Scala 3

The extension syntax works identically in both Scala 2 and Scala 3, but the implementation differs:

### Scala 3 (Extension Methods)

```scala
extension [A](self: A) {
  def toJson(using schema: Schema[A]): Json = ...
  def show(using schema: Schema[A]): String = ...
  def diff(that: A)(using schema: Schema[A]): Patch[A] = ...
  // ...
}

extension (self: String) {
  def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = ...
}

extension (self: Array[Byte]) {
  def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = ...
}
```

### Scala 2 (Implicit Classes)

```scala
implicit final class SchemaValueOps[A](private val self: A) {
  def toJson(implicit schema: Schema[A]): Json = ...
  def show(implicit schema: Schema[A]): String = ...
  def diff(that: A)(implicit schema: Schema[A]): Patch[A] = ...
  // ...
}

implicit final class StringSchemaOps(private val self: String) {
  def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = ...
}

implicit final class ByteArraySchemaOps(private val self: Array[Byte]) {
  def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = ...
}
```

The API is the same—just import `zio.blocks.schema._` and the appropriate syntax is provided for your Scala version.

## Method Reference

| Method             | Receiver      | Return Type              | Description                    |
|--------------------|---------------|--------------------------|--------------------------------|
| `toJson`           | `A`           | `Json`                   | Convert to JSON AST            |
| `toJsonString`     | `A`           | `String`                 | Convert to JSON string         |
| `toJsonBytes`      | `A`           | `Array[Byte]`            | Convert to UTF-8 bytes         |
| `fromJson[A]`      | `String`      | `Either[SchemaError, A]` | Parse JSON string              |
| `fromJson[A]`      | `Array[Byte]` | `Either[SchemaError, A]` | Parse JSON bytes               |
| `show`             | `A`           | `String`                 | Pretty-print via DynamicValue  |
| `diff`             | `A`           | `Patch[A]`               | Compute patch to another value |
| `applyPatch`       | `A`           | `A`                      | Apply patch (lenient)          |
| `applyPatchStrict` | `A`           | `Either[SchemaError, A]` | Apply patch (strict)           |

All methods require an implicit/given `Schema[A]` in scope.
