# DynamicValue

> `DynamicValue` is a schema-less, dynamically-typed representation of any structured value in ZIO Blocks. It provides a universal data model that can represent any value without requiring compile-time type information, serving as an intermediate representation for serialization, schema evolution, data transformation, and cross-format conversion.

`DynamicValue` is a schema-less, dynamically-typed representation of any structured value in ZIO Blocks. It provides a universal data model that can represent any value without requiring compile-time type information, serving as an intermediate representation for serialization, schema evolution, data transformation, and cross-format conversion.

## Overview

The `DynamicValue` type represents all structured values with six cases:

```
DynamicValue
 ├── DynamicValue.Primitive   (scalar values: strings, numbers, booleans, temporal types, etc.)
 ├── DynamicValue.Record      (named fields, analogous to case classes or JSON objects)
 ├── DynamicValue.Variant     (tagged unions, analogous to sealed traits)
 ├── DynamicValue.Sequence    (ordered collections: lists, arrays, vectors)
 ├── DynamicValue.Map         (key-value pairs where keys are also DynamicValues)
 └── DynamicValue.Null        (absence of a value)
```

Key design decisions:

- **Type-agnostic** — Works without compile-time type information
- **Preserves structure** — Maintains full fidelity of the original data
- **Supports rich primitives** — All Java time types, BigDecimal, UUID, Currency, etc.
- **Path-based navigation** — Uses `DynamicOptic` for traversal and modification
- **EJSON toString** — Human-readable output format with type annotations

## DynamicValue Variants

### Primitive

Wraps scalar values in a `PrimitiveValue`:

```scala

// Using convenience constructors
val str = DynamicValue.string("hello")
val num = DynamicValue.int(42)
val flag = DynamicValue.boolean(true)
val pi = DynamicValue.double(3.14159)

// Using the Primitive case directly

val instant = DynamicValue.Primitive(
  PrimitiveValue.Instant(java.time.Instant.now())
)
```

### Record

A collection of named fields, analogous to case classes or JSON objects:

```scala

// Using varargs constructor
val person = DynamicValue.Record(
  "name" -> DynamicValue.string("Alice"),
  "age" -> DynamicValue.int(30),
  "active" -> DynamicValue.boolean(true)
)

// Using Chunk constructor
val point = DynamicValue.Record(Chunk(
  ("x", DynamicValue.int(10)),
  ("y", DynamicValue.int(20))
))

// Empty record
val empty = DynamicValue.Record.empty
```

Field order is preserved and significant for equality. Use `sortFields` to normalize for order-independent comparison.

### Variant

A tagged union value, analogous to sealed traits:

```scala

// A Some variant containing a value
val some = DynamicValue.Variant(
  "Some",
  DynamicValue.string("hello")
)

// A None variant with an empty record
val none = DynamicValue.Variant("None", DynamicValue.Record.empty)

// Access case information
some.caseName   // Some("Some")
some.caseValue  // Some(DynamicValue.Primitive(...))
```

### Sequence

An ordered collection of values:

```scala

// Using varargs constructor
val numbers = DynamicValue.Sequence(
  DynamicValue.int(1),
  DynamicValue.int(2),
  DynamicValue.int(3)
)

// Using Chunk constructor
val items = DynamicValue.Sequence(Chunk(
  DynamicValue.string("a"),
  DynamicValue.string("b")
))

// Empty sequence
val empty = DynamicValue.Sequence.empty
```

### Map

Key-value pairs where both keys and values are `DynamicValue`:

```scala

// String keys (common case)
val config = DynamicValue.Map(
  DynamicValue.string("host") -> DynamicValue.string("localhost"),
  DynamicValue.string("port") -> DynamicValue.int(8080)
)

// Non-string keys (unlike Record)
val mapping = DynamicValue.Map(
  DynamicValue.int(1) -> DynamicValue.string("one"),
  DynamicValue.int(2) -> DynamicValue.string("two")
)

// Empty map
val empty = DynamicValue.Map.empty
```

Unlike `Record` which uses String keys, `Map` supports arbitrary `DynamicValue` keys.

### Null

Represents the absence of a value:

```scala

val absent = DynamicValue.Null
```

## PrimitiveValue Types

`PrimitiveValue` is a sealed trait representing all scalar values that can be wrapped in `DynamicValue.Primitive`. Each case preserves full type information:

| Type | Description | Example |
|------|-------------|---------|
| `Unit` | Unit value | `PrimitiveValue.Unit` |
| `Boolean` | Boolean | `PrimitiveValue.Boolean(true)` |
| `Byte` | 8-bit integer | `PrimitiveValue.Byte(127)` |
| `Short` | 16-bit integer | `PrimitiveValue.Short(32767)` |
| `Int` | 32-bit integer | `PrimitiveValue.Int(42)` |
| `Long` | 64-bit integer | `PrimitiveValue.Long(9999999999L)` |
| `Float` | 32-bit float | `PrimitiveValue.Float(3.14f)` |
| `Double` | 64-bit float | `PrimitiveValue.Double(3.14159)` |
| `Char` | Unicode character | `PrimitiveValue.Char('A')` |
| `String` | Text | `PrimitiveValue.String("hello")` |
| `BigInt` | Arbitrary precision integer | `PrimitiveValue.BigInt(BigInt("999..."))` |
| `BigDecimal` | Arbitrary precision decimal | `PrimitiveValue.BigDecimal(BigDecimal("3.14159"))` |
| `Instant` | Timestamp | `PrimitiveValue.Instant(Instant.now())` |
| `LocalDate` | Date without time | `PrimitiveValue.LocalDate(LocalDate.now())` |
| `LocalDateTime` | Date and time | `PrimitiveValue.LocalDateTime(LocalDateTime.now())` |
| `LocalTime` | Time without date | `PrimitiveValue.LocalTime(LocalTime.now())` |
| `Duration` | Time duration | `PrimitiveValue.Duration(Duration.ofHours(1))` |
| `Period` | Date-based period | `PrimitiveValue.Period(Period.ofDays(30))` |
| `DayOfWeek` | Day of week | `PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)` |
| `Month` | Month | `PrimitiveValue.Month(Month.JANUARY)` |
| `Year` | Year | `PrimitiveValue.Year(Year.of(2024))` |
| `YearMonth` | Year and month | `PrimitiveValue.YearMonth(YearMonth.of(2024, 1))` |
| `MonthDay` | Month and day | `PrimitiveValue.MonthDay(MonthDay.of(1, 15))` |
| `ZoneId` | Time zone | `PrimitiveValue.ZoneId(ZoneId.of("UTC"))` |
| `ZoneOffset` | Time zone offset | `PrimitiveValue.ZoneOffset(ZoneOffset.UTC)` |
| `ZonedDateTime` | Date/time with zone | `PrimitiveValue.ZonedDateTime(ZonedDateTime.now())` |
| `OffsetDateTime` | Date/time with offset | `PrimitiveValue.OffsetDateTime(OffsetDateTime.now())` |
| `OffsetTime` | Time with offset | `PrimitiveValue.OffsetTime(OffsetTime.now())` |
| `UUID` | Universally unique ID | `PrimitiveValue.UUID(UUID.randomUUID())` |
| `Currency` | Currency | `PrimitiveValue.Currency(Currency.getInstance("USD"))` |

## Creating DynamicValues from Typed Values

Use `Schema.toDynamicValue` to convert typed Scala values to `DynamicValue`:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val person = Person("Alice", 30)
val dynamic: DynamicValue = Schema[Person].toDynamicValue(person)
// Record with "name" and "age" fields

// Works with any type that has a Schema
val listDynamic = Schema[List[Int]].toDynamicValue(List(1, 2, 3))
// Sequence of Primitive(Int) values
```

## Converting DynamicValues Back to Typed Values

Use `Schema.fromDynamicValue` to convert `DynamicValue` back to typed Scala values:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val dynamic = DynamicValue.Record(
  "name" -> DynamicValue.string("Bob"),
  "age" -> DynamicValue.int(25)
)

val result: Either[SchemaError, Person] = Schema[Person].fromDynamicValue(dynamic)
// Right(Person("Bob", 25))

// Type mismatch produces an error
val badDynamic = DynamicValue.string("not a person")
val error = Schema[Person].fromDynamicValue(badDynamic)
// Left(SchemaError(...))
```

## Type Information

### DynamicValueType

Each `DynamicValue` has a corresponding `DynamicValueType` for runtime type checking:

```scala

val dv = DynamicValue.Record("x" -> DynamicValue.int(1))

// Check type
dv.is(DynamicValueType.Record)    // true
dv.is(DynamicValueType.Sequence)  // false

// Narrow to specific type
val record: Option[DynamicValue.Record] = dv.as(DynamicValueType.Record)
// Some(Record(...))

// Extract underlying value

val fields: Option[Chunk[(String, DynamicValue)]] = 
  dv.unwrap(DynamicValueType.Record)
```

### Extracting Primitive Values

```scala

val dv = DynamicValue.int(42)

// Extract with specific primitive type
val intValue: Option[Int] = dv.asPrimitive(PrimitiveType.Int(Validation.None))
// Some(42)

val stringValue: Option[String] = dv.asPrimitive(PrimitiveType.String(Validation.None))
// None (type mismatch)
```

## Navigation

### Simple Navigation

Navigate using `get` methods that return `DynamicValueSelection`:

```scala

val data = DynamicValue.Record(
  "users" -> DynamicValue.Sequence(
    DynamicValue.Record("name" -> DynamicValue.string("Alice")),
    DynamicValue.Record("name" -> DynamicValue.string("Bob"))
  )
)

// Navigate to a field
val users = data.get("users")  // DynamicValueSelection

// Navigate to an array element
val firstUser = data.get("users").apply(0)

// Chain navigation
val firstName = data.get("users").apply(0).get("name")

// Extract the value
val name = firstName.one  // Either[SchemaError, DynamicValue]
```

### Path-Based Navigation with DynamicOptic

Use `DynamicOptic` for complex path expressions:

```scala

val data = DynamicValue.Record(
  "company" -> DynamicValue.Record(
    "employees" -> DynamicValue.Sequence(
      DynamicValue.Record("name" -> DynamicValue.string("Alice"))
    )
  )
)

// Build a path
val path = DynamicOptic.root.field("company").field("employees").at(0).field("name")

// Navigate using the path
val result = data.get(path).one  // Right(DynamicValue.Primitive(String("Alice")))
```

### DynamicValueSelection

`DynamicValueSelection` wraps navigation results and provides fluent chaining:

```scala

val selection: DynamicValueSelection = ???

// Terminal operations
selection.one      // Either[SchemaError, DynamicValue] - exactly one value
selection.any      // Either[SchemaError, DynamicValue] - first of many
selection.all      // Either[SchemaError, DynamicValue] - wrap multiple in Sequence
selection.toChunk  // Chunk[DynamicValue] - empty on error

// Type filtering
selection.primitives  // Only Primitive values
selection.records     // Only Record values
selection.sequences   // Only Sequence values
selection.maps        // Only Map values

// Combinators
selection.map(dv => ???)      // Transform values
selection.filter(dv => ???)   // Filter values
selection.flatMap(dv => ???)  // Chain selections
```

## Path-Based Modification

### Modify

Update values at a path:

```scala

val data = DynamicValue.Record(
  "user" -> DynamicValue.Record(
    "name" -> DynamicValue.string("Alice")
  )
)

val path = DynamicOptic.root.field("user").field("name")

// Modify value at path
val updated = data.modify(path)(dv => DynamicValue.string("Bob"))
// Record("user" -> Record("name" -> "Bob"))
```

### Set

Replace a value at a path:

```scala

val data = DynamicValue.Record("x" -> DynamicValue.int(1))
val path = DynamicOptic.root.field("x")

val updated = data.set(path, DynamicValue.int(99))
// Record("x" -> 99)
```

### Delete

Remove a value at a path:

```scala

val data = DynamicValue.Record(
  "a" -> DynamicValue.int(1),
  "b" -> DynamicValue.int(2)
)

val updated = data.delete(DynamicOptic.root.field("a"))
// Record("b" -> 2)
```

### Insert

Add a value at a path (fails if path exists):

```scala

val data = DynamicValue.Record("a" -> DynamicValue.int(1))

val updated = data.insert(
  DynamicOptic.root.field("b"),
  DynamicValue.int(2)
)
// Record("a" -> 1, "b" -> 2)
```

### Fallible Operations

Use `*OrFail` variants for operations that should fail explicitly:

```scala

val data = DynamicValue.Record("x" -> DynamicValue.int(1))
val badPath = DynamicOptic.root.field("nonexistent")

val result: Either[SchemaError, DynamicValue] = 
  data.setOrFail(badPath, DynamicValue.int(99))
// Left(SchemaError("Path not found"))
```

## EJSON-like toString Format

`DynamicValue.toString` produces an EJSON (Extended JSON) format that:

- Uses unquoted field names for Records (like Scala syntax)
- Uses quoted string keys for Maps
- Adds `@ {tag: "..."}` annotations for Variants
- Adds `@ {type: "..."}` annotations for typed primitives (Instant, Duration, etc.)

```scala

val person = DynamicValue.Record(
  "name" -> DynamicValue.string("Alice"),
  "age" -> DynamicValue.int(30)
)

println(person.toString)
// {
//   name: "Alice",
//   age: 30
// }

val variant = DynamicValue.Variant(
  "Some",
  DynamicValue.string("hello")
)

println(variant.toString)
// "hello" @ {tag: "Some"}

val timestamp = DynamicValue.Primitive(
  PrimitiveValue.Instant(java.time.Instant.ofEpochMilli(1700000000000L))
)

println(timestamp.toString)
// 1700000000000 @ {type: "instant"}
```

Use `toEjson(indent)` to control indentation level.

## Merging Strategies

Merge two `DynamicValue` structures using configurable strategies:

```scala

val left = DynamicValue.Record(
  "a" -> DynamicValue.int(1),
  "b" -> DynamicValue.int(2)
)

val right = DynamicValue.Record(
  "b" -> DynamicValue.int(99),
  "c" -> DynamicValue.int(3)
)

// Deep merge (default): recursively merge containers
val merged = left.merge(right, DynamicValueMergeStrategy.Auto)
// Record("a" -> 1, "b" -> 99, "c" -> 3)
```

### Available Strategies

| Strategy | Behavior |
|----------|----------|
| `Auto` | Deep merge: Records by field, Sequences by index, Maps by key. Right wins at leaves. |
| `Replace` | Complete replacement: right value replaces left entirely |
| `KeepLeft` | Always keep left value |
| `Shallow` | Merge only at root level, nested containers replaced |
| `Concat` | Concatenate Sequences instead of merging by index |
| `Custom(f, r)` | Custom function with custom recursion control |

```scala

val list1 = DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2))
val list2 = DynamicValue.Sequence(DynamicValue.int(3))

// Concat sequences instead of index-based merge
val concatted = list1.merge(list2, DynamicValueMergeStrategy.Concat)
// Sequence(1, 2, 3)
```

## Normalization

Transform `DynamicValue` structures for comparison or serialization:

```scala

val data = DynamicValue.Record(
  "z" -> DynamicValue.int(1),
  "a" -> DynamicValue.Null,
  "m" -> DynamicValue.int(2)
)

// Sort fields alphabetically
data.sortFields
// Record("a" -> null, "m" -> 2, "z" -> 1)

// Remove null values
data.dropNulls
// Record("z" -> 1, "m" -> 2)

// Remove empty containers
data.dropEmpty

// Remove Unit primitives
data.dropUnits

// Apply all normalizations
data.normalize
// Sorted, no nulls, no units, no empty containers
```

## Transformation

### Transform Up/Down

Apply functions to all values in a structure:

```scala

val data = DynamicValue.Record(
  "values" -> DynamicValue.Sequence(
    DynamicValue.int(1),
    DynamicValue.int(2)
  )
)

// Bottom-up: children transformed before parents
val doubled = data.transformUp { (path, dv) =>
  dv match {
    case DynamicValue.Primitive(pv: PrimitiveValue.Int) =>
      DynamicValue.int(pv.value * 2)
    case other => other
  }
}

// Top-down: parents transformed before children
val topDown = data.transformDown { (path, dv) => ??? }
```

### Transform Field Names

Rename all record fields:

```scala

val data = DynamicValue.Record(
  "first_name" -> DynamicValue.string("Alice"),
  "last_name" -> DynamicValue.string("Smith")
)

// Convert snake_case to camelCase
val camelCase = data.transformFields { (path, name) =>
  name.split("_").zipWithIndex.map {
    case (word, 0) => word
    case (word, _) => word.capitalize
  }.mkString
}
// Record("firstName" -> "Alice", "lastName" -> "Smith")
```

## Folding

Aggregate values from a `DynamicValue` tree:

```scala

val data = DynamicValue.Record(
  "a" -> DynamicValue.int(1),
  "b" -> DynamicValue.int(2),
  "c" -> DynamicValue.int(3)
)

// Sum all integers
val sum = data.foldUp(0) { (path, dv, acc) =>
  dv match {
    case DynamicValue.Primitive(pv: PrimitiveValue.Int) => acc + pv.value
    case _ => acc
  }
}
// 6
```

## Converting to/from JSON

### To JSON

```scala

val dynamic = DynamicValue.Record(
  "name" -> DynamicValue.string("Alice"),
  "age" -> DynamicValue.int(30)
)

val json: Json = dynamic.toJson
// Json.Object with "name" and "age" fields
```

### From JSON

```scala

val json = Json.parseUnsafe("""{"name": "Alice", "age": 30}""")

val dynamic: DynamicValue = json.toDynamicValue
// DynamicValue.Record with "name" and "age" fields
```

## Querying

Search recursively for values matching a predicate:

```scala

val data = DynamicValue.Record(
  "users" -> DynamicValue.Sequence(
    DynamicValue.Record("name" -> DynamicValue.string("Alice"), "active" -> DynamicValue.boolean(true)),
    DynamicValue.Record("name" -> DynamicValue.string("Bob"), "active" -> DynamicValue.boolean(false))
  )
)

// Find all string values
val strings = data.select.query(_.is(DynamicValueType.Primitive))
  .filter(_.primitiveValue.exists(_.isInstanceOf[PrimitiveValue.String]))

// Query with path predicate
val atDepth2 = data.select.queryPath(path => path.nodes.length == 2)
```

## Use Cases

### Schema-less Operations

Work with data when the schema isn't known at compile time:

```scala

def processAnyData(data: DynamicValue): DynamicValue = {
  // Add a timestamp to any record
  data match {
    case r: DynamicValue.Record =>
      DynamicValue.Record(
        r.fields :+ ("processedAt" -> DynamicValue.Primitive(
          PrimitiveValue.Instant(java.time.Instant.now())
        ))
      )
    case other => other
  }
}
```

### Schema Migrations

Transform data between schema versions:

```scala

def migrateV1toV2(data: DynamicValue): DynamicValue = {
  data.transformFields { (path, name) =>
    // Rename deprecated field
    if (name == "userName") "name"
    else name
  }.transformUp { (path, dv) =>
    // Add default for new required field
    dv match {
      case r: DynamicValue.Record if path.nodes.isEmpty =>
        DynamicValue.Record(r.fields :+ ("version" -> DynamicValue.int(2)))
      case other => other
    }
  }
}
```

### Dynamic Queries

Build queries at runtime:

```scala

def buildPath(fields: List[String]): DynamicOptic =
  fields.foldLeft(DynamicOptic.root)(_.field(_))

def getValue(data: DynamicValue, path: List[String]): Option[DynamicValue] =
  data.get(buildPath(path)).one.toOption

// Usage
val data = DynamicValue.Record(
  "user" -> DynamicValue.Record(
    "profile" -> DynamicValue.Record(
      "email" -> DynamicValue.string("alice@example.com")
    )
  )
)

val email = getValue(data, List("user", "profile", "email"))
// Some(DynamicValue.Primitive(String("alice@example.com")))
```

### Cross-Format Conversion

Use `DynamicValue` as an intermediate format:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// JSON -> DynamicValue -> Typed
val json = Json.parseUnsafe("""{"name": "Alice", "age": 30}""")
val dynamic = json.toDynamicValue
val person = Schema[Person].fromDynamicValue(dynamic)

// Typed -> DynamicValue -> JSON
val dynamic2 = Schema[Person].toDynamicValue(Person("Bob", 25))
val json2 = dynamic2.toJson
```

## Comparison and Ordering

`DynamicValue` has a total ordering for sorting and comparison:

```scala

val a = DynamicValue.int(1)
val b = DynamicValue.int(2)

a.compare(b)  // negative
a < b         // true
a >= b        // false

// Type ordering: Primitive < Record < Variant < Sequence < Map < Null
val primitive = DynamicValue.int(1)
val record = DynamicValue.Record.empty
primitive < record  // true
```

## Diff and Patch

Compute differences between `DynamicValue` instances:

```scala

val old = DynamicValue.Record(
  "name" -> DynamicValue.string("Alice"),
  "age" -> DynamicValue.int(30)
)

val new_ = DynamicValue.Record(
  "name" -> DynamicValue.string("Alice"),
  "age" -> DynamicValue.int(31)
)

val patch: DynamicPatch = old.diff(new_)
// Patch that updates "age" from 30 to 31
```

## See Also

- [DynamicSchema](./dynamic-schema.md) — validate `DynamicValue` instances against a structural schema, or transport schemas as `DynamicValue` blobs.
