# JsonSelection

> `JsonSelection` is a fluent wrapper type that enables composable, chainable navigation through JSON structures. It wraps `Either[SchemaError, Chunk[Json]]`, allowing operations that may fail gracefully or return multiple values.

`JsonSelection` is a fluent wrapper type that enables composable, chainable navigation through JSON structures. It wraps `Either[SchemaError, Chunk[Json]]`, allowing operations that may fail gracefully or return multiple values.

## Overview

`JsonSelection` makes it easy to navigate unknown or deeply nested JSON at runtime without needing to match on `Either` at each step. Chain operations like `JsonSelection#get`, `JsonSelection#apply`, `JsonSelection#filter`, and `JsonSelection#as` to build powerful queries.

**Key characteristics:**
- **Fluent chaining:** Operations chain naturally without unwrapping intermediate results
- **Multi-value support:** Can contain zero, one, or many `Json` values
- **Error propagation:** Errors short-circuit further operations; querying an error selection returns the same error
- **Type extraction:** Use `.as[Type]` to decode to Scala types with Schema-based derivation

## Creating JsonSelection

You can create `JsonSelection` instances in multiple ways: from existing `Json` values, or using companion object constructors. Each approach is useful for different scenarios—direct navigation for existing JSON, and explicit construction for building selections programmatically.

### From Json Values

Create a `JsonSelection` directly from a `Json` value by calling navigation methods:

```scala
import zio.blocks.schema.json.{Json, JsonSelection}

val json = Json.parseUnsafe("""{"name": "Alice"}""")

// Get a single field  
val name: JsonSelection = json.get("name")

// Get array element by index
val values = Json.parseUnsafe("[1, 2, 3]")
val selected: JsonSelection = values.get(0)
```

### From Companion Object

Construct `JsonSelection` instances programmatically using the companion object methods for empty, successful, and failed selections:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonSelection}

// Empty successful selection
val empty = JsonSelection.empty

// Succeed with a single value
val success = JsonSelection.succeed(Json.String("hello"))

// Succeed with multiple values
val many = JsonSelection.succeedMany(
  zio.blocks.chunk.Chunk.from(Seq(Json.Number(1), Json.Number(2)))
)

// Fail with an error
val failure = JsonSelection.fail(SchemaError("not found"))
```

## Navigation Operations

Navigate through JSON structures using three complementary approaches: field access for object properties, array indexing for elements, and path expressions for complex nested navigation.

### Field Navigation

Navigate to object fields using `JsonSelection#get` with a field name:

```scala
import zio.blocks.schema.json.Json

val data = Json.parseUnsafe("""{
  "user": {
    "name": "Bob",
    "email": "bob@example.com"
  }
}""")

// Single field access
val user = data.get("user")

// Chained field access
val name = data.get("user").get("name")

// Multiple levels of nesting
val email = data.get("user").get("email")
```

### Array Navigation

Access array elements by index using `JsonSelection#apply` or `JsonSelection#get`:

```scala
import zio.blocks.schema.json.Json

val data = Json.parseUnsafe("""[
  {"id": 1, "name": "Alice"},
  {"id": 2, "name": "Bob"}
]""")

// Index access (0-based)
val first = data.get(0)

// Chained index and field access
val firstName = data.get(0).get("name")
val secondId = data.get(1).get("id")
```

### Path-Based Navigation

Use path interpolators (e.g., `p".company.employees[0].name"`) to navigate deeply nested structures in a single operation:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.Json

val data = Json.Object(
  "company" -> Json.Object(
    "employees" -> Json.Array(
      Json.Object("name" -> Json.String("Alice"), "department" -> Json.String("Engineering")),
      Json.Object("name" -> Json.String("Bob"), "department" -> Json.String("Sales"))
    )
  )
)

// Navigate using path interpolator
val path = p".company.employees[0].name"
val firstEmpName = data.get(path)  // JsonSelection(Right(Chunk(Json.String("Alice"))))
```

## Filtering and Querying

Reduce selections to only the values you need by filtering by JSON type or custom predicates. This enables working with heterogeneous JSON arrays where values may be of different types.

### Filter by Type

Keep only values of a specific JSON type using type-filtering methods like `JsonSelection#strings`, `JsonSelection#numbers`, and `JsonSelection#booleans`:

```scala
import zio.blocks.schema.json.{Json, JsonSelection}

val mixed = Json.parseUnsafe("""["text", 42, true, "more text"]""")

// Create a selection containing all array elements, then filter by type
val allElements = JsonSelection.succeedMany(mixed.elements)
val strings = allElements.strings    // JsonSelection with string values
val numbers = allElements.numbers    // JsonSelection with number values
val booleans = allElements.booleans  // JsonSelection with boolean values
```

### Filter with Predicate

Use custom predicates with `JsonSelection#filter` to keep only values that match specific conditions:

```scala
import zio.blocks.schema.json.{Json, JsonSelection}

val data = Json.parseUnsafe("[1, 2, 3, 4]")

// Create selection and filter with predicate
val allElements = JsonSelection.succeedMany(data.elements)
val evenOnly = allElements.filter { json =>
  json match {
    case Json.Number(n) => n.toInt % 2 == 0
    case _ => false
  }
}
```

## Extracting Values

Extract concrete values from selections by decoding them to Scala types, checking single vs. multiple values, or inspecting selection state without extraction.

### Type Decoding

Decode a single selected value to a Scala type using `JsonSelection#as`, which fails if more than one value is selected:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.Json

// Decode to Scala types
val selection = Json.parseUnsafe("""{"count": 42}""")

val count: Either[SchemaError, Int] = selection.get("count").as[Int]
val str: Either[SchemaError, String] = selection.get("count").as[String]  // Left (type mismatch)
```

### Multiple Decoding

Decode all selected values to a collection using `JsonSelection#asAll`, which succeeds even if the selection is empty:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonSelection}

val data = Json.parseUnsafe("""["Alice", "Bob", "Charlie"]""")

// Create selection of all array elements and decode
val allElements = JsonSelection.succeedMany(data.elements)
val names: Either[SchemaError, Seq[String]] = allElements.asAll[String].map(_.toSeq)
```

### Extract Single Value

Use `JsonSelection#one` to extract exactly one value, failing if the selection contains zero or more than one value:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonSelection}

val arr = Json.parseUnsafe("[1, 2]")

// Get exactly one value from a selection (fails if 0 or more than 1)
val single = arr.get(0).one    // Right(Json.Number(1))
val multiple = JsonSelection.succeedMany(arr.elements).one  // Left(SchemaError(...))
```

### Check and Get

Inspect selection state without extraction using properties like `JsonSelection#values`, `JsonSelection#error`, `JsonSelection#isSuccess`, and `JsonSelection#isFailure`:

```scala
import zio.blocks.schema.json.{Json, JsonSelection}

val data = Json.Object("x" -> Json.Number(1))

// Safe option extraction
val x: Option[Json] = data.get("x").values.flatMap(_.headOption)

// Check if selection succeeded
val success = data.get("exists").isSuccess    // true if "exists" field found
val failed = data.get("notFound").isFailure   // true if field not found
```

## Size and Existence Checks

Query selection size and emptiness using `JsonSelection#size`, `JsonSelection#isEmpty`, and `JsonSelection#nonEmpty`:

```scala
import zio.blocks.schema.json.{Json, JsonSelection}

val data = Json.parseUnsafe("[1, 2, 3]")

// Create selection and check size
val selection = JsonSelection.succeedMany(data.elements)
val size = selection.size        // 3
val isEmpty = selection.isEmpty  // false
val nonEmpty = selection.nonEmpty  // true
```

## Modifying Selections

Use `JsonSelection` to update values at specific paths using `set` for replacement or `modify` for transformation:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.Json

val original = Json.Object("count" -> Json.Number(0))

// Set a new value
val updated = original.set(p".count", Json.Number(1))

// Modify with a function
val modified = original.modify(p".count") {
  case Json.Number(n) => Json.Number(n + 1)
  case other => other
}
```

## Error Handling

Selections propagate errors through the chain:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.Json

val data = Json.Object("name" -> Json.String("Alice"))

// Missing field returns error
val missing = data.get("age").error  // Some(SchemaError("field not found"))

// Chain continues with error
val stillMissing = data.get("age").get("nested")  // Still carries the error

// Check error state
val result = data.get("x")
val maybeError: Option[SchemaError] = result.error
val maybeValues = result.values      // None if error
```

## Integration with Codecs

`JsonSelection` integrates seamlessly with `JsonCodec` and `Schema`:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonSelection}
import zio.blocks.chunk.Chunk

case class User(name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val users = Json.parseUnsafe("""[
  {"name": "Alice", "email": "alice@example.com"},
  {"name": "Bob", "email": "bob@example.com"}
]""")

// Navigate and decode in one chain
val firstUser: Either[SchemaError, User] = users.get(0).as[User]
val allUsers: Either[SchemaError, Chunk[User]] = JsonSelection.succeedMany(users.elements).asAll[User]
```

## Performance Notes

- **Zero-allocation navigation:** `JsonSelection` itself is a value type (AnyVal) and compiles to no allocation
- **Lazy chaining:** Operations chain without intermediate allocations; only final extraction materializes values
- **Error short-circuiting:** Failed selections don't execute further operations
- **Streaming-friendly:** For large JSON, use `JsonSelection#get` selectively rather than iterating over all values
