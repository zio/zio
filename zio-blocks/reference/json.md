# Json

> `Json` is an algebraic data type (ADT) for representing JSON values in ZIO Blocks. It provides a type-safe, schema-free way to work with JSON data, enabling navigation, transformation, merging, and querying without losing fidelity.

`Json` is an algebraic data type (ADT) for representing JSON values in ZIO Blocks. It provides a type-safe, schema-free way to work with JSON data, enabling navigation, transformation, merging, and querying without losing fidelity.

## Overview

The `Json` type represents all valid JSON values with six cases:

```
Json
 ├── Json.Object   (key-value pairs, order-preserving)
 ├── Json.Array    (ordered sequence of values)
 ├── Json.String   (text)
 ├── Json.Number   (arbitrary precision via BigDecimal)
 ├── Json.Boolean  (true/false)
 └── Json.Null     (null)
```

Key design decisions:

- **Objects use `Vector[(String, Json)]`** to preserve insertion order while providing order-independent equality
- **Numbers use `BigDecimal`** to preserve precision for financial and scientific data
- **All navigation returns `JsonSelection`** for fluent, composable chaining

## Creating JSON Values

### Using Constructors

```scala

// Object with named fields
val person = Json.Object(
  "name" -> Json.String("Alice"),
  "age" -> Json.Number(30),
  "active" -> Json.Boolean(true)
)

// Array of values
val numbers = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))

// Primitive values
val name = Json.String("Bob")
val count = Json.Number(42)
val flag = Json.Boolean(false)
val nothing = Json.Null
```

### Parsing JSON Strings

```scala

// Safe parsing (returns Either)
val parsed: Either[SchemaError, Json] = Json.parse("""{"name": "Alice", "age": 30}""")

// Unsafe parsing (throws on error)
val json = Json.parseUnsafe("""{"items": [1, 2, 3]}""")
```

### String Interpolators

ZIO Blocks provides compile-time validated string interpolators for JSON:

```scala

// JSON literal with compile-time validation
val person = json"""{"name": "Alice", "age": 30}"""

// With Scala value interpolation
val name = "Bob"
val age = 25
val person2 = json"""{"name": $name, "age": $age}"""

// Path interpolator for navigation
val path = p".users[0].name"
```

The `json"..."` interpolator validates JSON syntax at compile time, catching errors before runtime.

## Type Testing and Access

### Unified Type Operations

The `Json` type provides unified methods for type testing and narrowing with path-dependent return types.
`JsonType` also implements `Json => Boolean`, so it can be used directly as a predicate for filtering.

```scala

val json: Json = Json.parseUnsafe("""{"count": 42}""")

// Type testing with is()
json.is(JsonType.Object)  // true
json.is(JsonType.Array)   // false

// Type narrowing with as() - returns Option[jsonType.Type]
val obj: Option[Json.Object] = json.as(JsonType.Object)  // Some(Json.Object(...))
val arr: Option[Json.Array] = json.as(JsonType.Array)    // None

// Value extraction with unwrap() - returns Option[jsonType.Unwrap]
val str: Json = Json.String("hello")
val strValue: Option[String] = str.unwrap(JsonType.String)  // Some("hello")

val num: Json = Json.Number(42)
val numValue: Option[BigDecimal] = num.unwrap(JsonType.Number)  // Some(42)

// JsonType as predicate - use directly in selection query
val strings = json.select.query(JsonType.String)  // all string values in the JSON tree
```

### Direct Value Access

```scala

val obj = Json.Object("a" -> Json.Number(1))
obj.fields  // Chunk(("a", Json.Number(1)))

val arr = Json.Array(Json.Number(1), Json.Number(2))
arr.elements  // Chunk(Json.Number(1), Json.Number(2))
```

## Navigation

### Simple Navigation

Navigate into objects by key and arrays by index:

```scala

val json = Json.parseUnsafe("""{
  "users": [
    {"name": "Alice", "age": 30},
    {"name": "Bob", "age": 25}
  ]
}""")

// Navigate to a field
val users = json.get("users")  // JsonSelection

// Navigate to an array element
val firstUser = json.get("users")(0)  // JsonSelection

// Chain navigation
val firstName = json.get("users")(0).get("name")  // JsonSelection

// Extract the value
val name: Either[SchemaError, String] = firstName.as[String]  // Right("Alice")
```

### Path-Based Navigation with DynamicOptic

Use `DynamicOptic` paths for complex navigation:

```scala

val json = Json.parseUnsafe("""{
  "company": {
    "employees": [
      {"name": "Alice", "department": "Engineering"},
      {"name": "Bob", "department": "Sales"}
    ]
  }
}""")

// Using path interpolator
val path = p".company.employees[0].name"
val name = json.get(path).as[String]  // Right("Alice")

// Equivalent to chained navigation
val sameName = json.get("company").get("employees")(0).get("name").as[String]
```

## JsonSelection

`JsonSelection` is a fluent wrapper for navigation results, enabling composable chaining:

```scala

val json = Json.parseUnsafe("""{"users": [{"name": "Alice"}]}""")

// Fluent chaining
val result: JsonSelection = json
 .get("users")
 .arrays
 .apply(0)
 .get("name")
 .strings

// Extract values
result.as[String]  // Right("Alice")
result.one         // Right(Json.String("Alice"))
result.isSuccess   // true
result.isFailure   // false
```

### Terminal Operations

```scala

val selection: JsonSelection = ???

// Get single value (exactly one required)
val oneValue: Either[SchemaError, Json] = selection.one
// Get any single value (first of many)
val anyValue: Either[SchemaError, Json] = selection.any
// Get all values condensed (wraps multiple in array)
val allValues: Either[SchemaError, Json] = selection.all

// Get underlying result
val underlying: Option[zio.blocks.chunk.Chunk[Json]] = selection.values
val asChunk: zio.blocks.chunk.Chunk[Json] = selection.toChunk  // empty on error

// Decode to specific types
val asString: Either[SchemaError, String] = selection.as[String]
val asBigDecimal: Either[SchemaError, BigDecimal] = selection.as[BigDecimal]
val asBoolean: Either[SchemaError, Boolean] = selection.as[Boolean]
val asInt: Either[SchemaError, Int] = selection.as[Int]
val asLong: Either[SchemaError, Long] = selection.as[Long]
val asDouble: Either[SchemaError, Double] = selection.as[Double]
```

## Modification

### Setting Values

```scala

val json = Json.parseUnsafe("""{"user": {"name": "Alice", "age": 30}}""")

// Set a value at a path
val updated = json.set(p".user.name", Json.String("Bob"))
// {"user": {"name": "Bob", "age": 30}}

// Set with failure handling
val result = json.setOrFail(p".user.email", Json.String("alice@example.com"))
// Left(SchemaError) - path doesn't exist
```

### Modifying Values

```scala

val json = Json.parseUnsafe("""{"count": 10}""")

// Modify with a function
val incremented = json.modify(p".count") {
  case Json.Number(n) => Json.Number(n + 1)
  case other => other
}
// {"count": 11}

// Modify with failure on missing path
val result = json.modifyOrFail(p".count") {
  case Json.Number(n) => Json.Number(n * 2)
}
// Right({"count": 20})
```

### Deleting Values

```scala

val json = Json.parseUnsafe("""{"a": 1, "b": 2, "c": 3}""")

// Delete a field
val withoutB = json.delete(p".b")
// {"a": 1, "c": 3}

// Delete with failure handling
val result = json.deleteOrFail(p".missing")
// Left(SchemaError) - path doesn't exist
```

### Inserting Values

```scala

val json = Json.parseUnsafe("""{"existing": 1}""")

// Insert a new field
val withNew = json.insert(p".newField", Json.String("value"))
// {"existing": 1, "newField": "value"}
```

## Transformation

### Transform Up (Bottom-Up)

Transform children before parents:

```scala

val json = Json.parseUnsafe("""{"values": [1, 2, 3]}""")

// Double all numbers
val doubled = json.transformUp { (path, value) =>
  value match {
    case Json.Number(n) => Json.Number(n * 2)
    case other => other
  }
}
// {"values": [2, 4, 6]}
```

### Transform Down (Top-Down)

Transform parents before children:

```scala

val json = Json.parseUnsafe("""{"items": [{"x": 1}, {"x": 2}]}""")

// Add a field to all objects
val withId = json.transformDown { (path, value) =>
  value match {
    case Json.Object(fields) if !fields.exists(_._1 == "id") =>
      new Json.Object(("id" -> Json.String(path.toString)) +: fields)
    case other => other
  }
}
```

### Transform Keys

Rename object keys throughout the structure:

```scala

val json = Json.parseUnsafe("""{"user_name": "Alice", "user_age": 30}""")

// Convert snake_case to camelCase
val camelCase = json.transformKeys { (path, key) =>
  key.split("_").zipWithIndex.map {
    case (word, 0) => word
    case (word, _) => word.capitalize
  }.mkString
}
// {"userName": "Alice", "userAge": 30}
```

## Filtering

### Filter Values

Keep only values matching a predicate using `retain`, or remove values using `prune`:

```scala

val json = Json.parseUnsafe("""{"a": 1, "b": null, "c": 2, "d": null}""")

// Remove nulls using prune (removes values matching predicate)
val noNulls = json.prune(_.is(JsonType.Null))
// {"a": 1, "c": 2}

// Keep only numbers using retain (keeps values matching predicate)
val onlyNumbers = json.retain(_.is(JsonType.Number))
// {"a": 1, "c": 2}
```

### Project Paths

Extract only specific paths:

```scala

val json = Json.parseUnsafe("""{
  "user": {"name": "Alice", "email": "alice@example.com", "password": "secret"},
  "metadata": {"created": "2024-01-01"}
}""")

// Keep only specific fields
val projected = json.project(p".user.name", p".user.email")
// {"user": {"name": "Alice", "email": "alice@example.com"}}
```

### Partition

Split based on a predicate:

```scala

val json = Json.parseUnsafe("""{"a": 1, "b": "text", "c": 2}""")

// Separate numbers from non-numbers
val (numbers, nonNumbers) = json.partition(_.is(JsonType.Number))
// numbers: {"a": 1, "c": 2}
// nonNumbers: {"b": "text"}
```

## Folding

### Fold Up (Bottom-Up)

Accumulate values from children to parents:

```scala

val json = Json.parseUnsafe("""{"values": [1, 2, 3, 4, 5]}""")

// Sum all numbers
val sum = json.foldUp(BigDecimal(0)) { (path, value, acc) =>
  value match {
    case n: Json.Number => acc + n.value
    case _ => acc
  }
}
// sum = 15
```

### Fold Down (Top-Down)

Accumulate values from parents to children:

```scala

val json = Json.parseUnsafe("""{"a": {"b": {"c": 1}}}""")

// Collect all paths
val paths = json.foldDown(Vector.empty[DynamicOptic]) { (path, value, acc) =>
  acc :+ path
}
```

## Merging

Combine two JSON values using different strategies:

```scala

val base = Json.parseUnsafe("""{"a": 1, "b": {"x": 10}}""")
val overlay = Json.parseUnsafe("""{"b": {"y": 20}, "c": 3}""")

// Auto strategy (default) - deep merge objects, concat arrays
val merged = base.merge(overlay)
// {"a": 1, "b": {"x": 10, "y": 20}, "c": 3}

// Shallow merge (only top-level)
val shallow = base.merge(overlay, MergeStrategy.Shallow)

// Replace (right wins)
val replaced = base.merge(overlay, MergeStrategy.Replace)
// {"b": {"y": 20}, "c": 3}

// Concat arrays
val concat = base.merge(overlay, MergeStrategy.Concat)

// Custom strategy
val custom = base.merge(overlay, MergeStrategy.Custom { (path, left, right) =>
  // Your merge logic here
  right
})
```

### Merge Strategies

| Strategy | Objects | Arrays | Primitives |
|----------|---------|--------|------------|
| `Auto` | Deep merge | Concatenate | Replace |
| `Deep` | Recursive merge | Concatenate | Replace |
| `Shallow` | Top-level only | Concatenate | Replace |
| `Replace` | Right wins | Right wins | Right wins |
| `Concat` | Merge keys | Concatenate | Replace |
| `Custom(f)` | User-defined | User-defined | User-defined |

## Normalization

Clean up JSON values:

```scala

val json = Json.parseUnsafe("""{
  "z": 1,
  "a": null,
  "m": {"empty": {}},
  "b": 2
}""")

// Sort object keys alphabetically
val sorted = json.sortKeys
// {"a": null, "b": 2, "m": {"empty": {}}, "z": 1}

// Remove null values
val noNulls = json.dropNulls
// {"z": 1, "m": {"empty": {}}, "b": 2}

// Remove empty objects and arrays
val noEmpty = json.dropEmpty
// {"z": 1, "a": null, "b": 2}

// Apply all normalizations
val normalized = json.normalize
// {"b": 2, "z": 1}
```

## Encoding and Decoding

### Built-in Codecs

```scala

// Primitives
Schema[String].jsonCodec
Schema[Int].jsonCodec
Schema[Long].jsonCodec
Schema[Double].jsonCodec
Schema[Boolean].jsonCodec
Schema[BigDecimal].jsonCodec

// Collections
Schema[List[Int]].jsonCodec
Schema[Vector[String]].jsonCodec
Schema[Map[String, Int]].jsonCodec
Schema[Option[String]].jsonCodec

// Java time/util types
Schema[java.time.Instant].jsonCodec
Schema[java.time.LocalDate].jsonCodec
Schema[java.time.ZonedDateTime].jsonCodec
Schema[java.util.UUID].jsonCodec
```

### Encoding/Decoding of Primitives

```scala

// Encode Scala values to Json
val intJson = 42.toJson  // Json.Number(42)
val strJson = "hello".toJson  // Json.String("hello")

// Decode Json to Scala values
val intResult = intJson.as[Int]  // Right(42)
val strResult = strJson.as[String]  // Right("hello")
```

### Encoding/Decoding of Case Classes

For complex types, use Schema-based derivation:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val person = Person("Alice", 30)
val json = person.toJson
val decoded = json.as[Person]
```

### Extension Syntax

When a `Schema` is in scope, you can use convenient extension methods directly on values:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val person = Person("Alice", 30)

// Convert to Json AST
val json = person.toJson  // Json.Object(...)

// Convert directly to JSON string
val jsonString = person.toJsonString  // {"name":"Alice","age":30}

// Convert to UTF-8 bytes
val jsonBytes = person.toJsonBytes  // Array[Byte]

// Parse JSON string back to a typed value
val parsed = """{"name":"Bob","age":25}""".fromJson[Person]  // Right(Person("Bob", 25))

// Parse from bytes
val fromBytes = jsonBytes.fromJson[Person]  // Right(Person("Alice", 30))
```

These extension methods provide a more ergonomic API compared to explicitly creating encoders/decoders.

### Using the `as` Method

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val json = Json.parseUnsafe("""{"name": "Alice", "age": 30}""")

// Decode to a specific type
val person: Either[SchemaError, Person] = json.as[Person]

// Unsafe version (throws on error)
val personUnsafe: Person = json.asUnsafe[Person]
```

## Printing JSON

### Basic Printing

```scala

val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))

// Compact output
val compact: String = json.print
// {"name":"Alice","age":30}
```

### With Writer Config

```scala

val json = Json.Object("name" -> Json.String("Alice"))

// Pretty-printed output (2-space indentation)
val pretty = json.print(WriterConfig.withIndentionStep2)
// {
//   "name": "Alice"
// }

// Custom indentation
val indented4 = json.print(WriterConfig.withIndentionStep(4))
```

### WriterConfig Options

`WriterConfig` controls JSON output formatting:

| Option | Default | Description |
|--------|---------|-------------|
| `indentionStep` | `0` | Spaces per indentation level (0 = compact) |
| `escapeUnicode` | `false` | Escape non-ASCII characters as `\uXXXX` |
| `preferredBufSize` | `32768` | Internal buffer size in bytes |

```scala

// Compact output (default)
val compact = WriterConfig

// Pretty-printed with 2-space indentation
val pretty = WriterConfig.withIndentionStep(2)

// Escape Unicode for ASCII-only output
val ascii = WriterConfig.withEscapeUnicode(true)

// Combine options
val custom = WriterConfig
  .withIndentionStep(2)
  .withEscapeUnicode(true)
  .withPreferredBufSize(65536)
```

### ReaderConfig Options

`ReaderConfig` controls JSON parsing behavior:

| Option | Default | Description |
|--------|---------|-------------|
| `preferredBufSize` | `32768` | Preferred byte buffer size |
| `preferredCharBufSize` | `4096` | Preferred char buffer size for strings |
| `maxBufSize` | `33554432` | Maximum byte buffer size (32MB) |
| `maxCharBufSize` | `4194304` | Maximum char buffer size (4MB) |
| `checkForEndOfInput` | `true` | Error on trailing non-whitespace |

```scala

// Default configuration
val default = ReaderConfig

// Allow trailing content (useful for streaming)
val lenient = ReaderConfig.withCheckForEndOfInput(false)

// Increase buffer sizes for large documents
val largeDoc = ReaderConfig
  .withPreferredBufSize(65536)
  .withPreferredCharBufSize(8192)
```

### To Bytes

```scala

val json = Json.Object("x" -> Json.Number(1))

// As byte array
val bytes: Array[Byte] = json.printBytes
```

## Query Operations

### Query with Predicate

Find all values matching a condition:

```scala

val json = Json.parseUnsafe("""{
  "users": [
    {"name": "Alice", "active": true},
    {"name": "Bob", "active": false},
    {"name": "Charlie", "active": true}
  ]
}""")

// Find all active users using queryBoth on a selection
val activeUsers = json.select.queryBoth { (path, value) =>
  value.get("active").as[Boolean].getOrElse(false)
}
```

### Convert to Key-Value Pairs

Flatten to path-value pairs:

```scala

val json = Json.parseUnsafe("""{"a": {"b": 1, "c": 2}}""")

val pairs: Chunk[(DynamicOptic, Json)] = json.toKV
// Chunk(
//   ($.a.b, Json.Number(1)),
//   ($.a.c, Json.Number(2))
// )
```

## Comparison and Equality

### Object Equality

Objects are compared **order-independently** (keys are compared as sorted sets):

```scala

val obj1 = Json.parseUnsafe("""{"a": 1, "b": 2}""")
val obj2 = Json.parseUnsafe("""{"b": 2, "a": 1}""")

obj1 == obj2  // true (order-independent)
```

### Ordering

JSON values have a total ordering for sorting:

```scala

val values = List(
  Json.String("z"),
  Json.Number(1),
  Json.Null,
  Json.Boolean(true)
)

// Sort by type, then by value
val sorted = values.sortWith((a, b) => a.compare(b) < 0)
// [null, true, 1, "z"]
```

Type ordering: Null < Boolean < Number < String < Array < Object

## JSON Diffing

`JsonDiffer` computes the difference between two JSON values, producing a [`JsonPatch`](./json-patch.md) that transforms the source into the target:

```scala

val source = Json.parseUnsafe("""{"name": "Alice", "age": 30}""")
val target = Json.parseUnsafe("""{"name": "Alice", "age": 31, "active": true}""")

// Compute the diff
val patch: JsonPatch = JsonPatch.diff(source, target)

// The patch describes the minimal changes:
// - NumberDelta for age: 30 -> 31
// - Add field "active": true
```

The differ uses optimal operations:
- **NumberDelta** for numeric changes (stores the delta, not the new value)
- **StringEdit** for string changes when edits are more compact than replacement
- **ArrayEdit** with LCS-based Insert/Delete operations for arrays
- **ObjectEdit** with Add/Remove/Modify operations for objects

## JSON Patching

`JsonPatch` represents a sequence of operations that transform a JSON value. Patches are composable and can be applied with different failure modes:

### Computing and Applying Patches

```scala

val original = Json.parseUnsafe("""{"count": 10, "items": ["a", "b"]}""")
val modified = Json.parseUnsafe("""{"count": 15, "items": ["a", "b", "c"]}""")

// Compute the patch
val patch = JsonPatch.diff(original, modified)

// Apply with default (Strict) mode - fails on any precondition violation
val result1: Either[SchemaError, Json] = patch(original)

// Apply with Lenient mode - skips failing operations
val result2 = patch(original, PatchMode.Lenient)

// Apply with Clobber mode - forces changes on conflicts
val result3 = patch(original, PatchMode.Clobber)
```

### Patch Modes

| Mode | Behavior |
|------|----------|
| `Strict` | Fail immediately on any precondition violation |
| `Lenient` | Skip operations that fail preconditions |
| `Clobber` | Force changes, overwriting on conflicts |

### Composing Patches

```scala

val patch1 = JsonPatch.diff(
  Json.parseUnsafe("""{"x": 1}"""),
  Json.parseUnsafe("""{"x": 2}""")
)

val patch2 = JsonPatch.diff(
  Json.parseUnsafe("""{"x": 2}"""),
  Json.parseUnsafe("""{"x": 2, "y": 3}""")
)

// Compose patches - applies patch1, then patch2
val combined = patch1 ++ patch2

// Apply the combined patch
val result = combined(Json.parseUnsafe("""{"x": 1}"""))
// Right({"x": 2, "y": 3})
```

### Converting to DynamicPatch

`JsonPatch` can be converted to and from `DynamicPatch` for interoperability with the typed patching system:

```scala

val jsonPatch: JsonPatch = ???

// Convert to DynamicPatch
val dynamicPatch: DynamicPatch = jsonPatch.toDynamicPatch

// Convert from DynamicPatch (may fail for unsupported operations)
val restored: Either[SchemaError, JsonPatch] = JsonPatch.fromDynamicPatch(dynamicPatch)
```

## Conversion to DynamicValue

Convert JSON to ZIO Blocks' semi-structured `DynamicValue`:

```scala

val json = Json.parseUnsafe("""{"name": "Alice"}""")

val dynamic: DynamicValue = json.toDynamicValue
```

This enables interoperability with other ZIO Blocks formats (Avro, TOON, etc.).

## Error Handling

### SchemaError

Errors include path information for debugging:

```scala

val json = Json.parseUnsafe("""{"users": [{"name": "Alice"}]}""")

val result = json.get("users")(5).get("name").as[String]
// Left(SchemaError: Index 5 out of bounds at path $.users[5])
```

### Error Properties

```scala

val error: SchemaError = ???

error.message            // Error description
error.errors.head.source // DynamicOptic path to error location
```

## Cross-Platform Support

The `Json` type works across 2 platforms:

- **JVM** - Full functionality
- **Scala.js** - Browser and Node.js

String interpolators use compile-time validation that works on both platforms too.
