# JSON Codec

> The JSON codec module provides complete, type-safe support for working with JSON data in ZIO Blocks. It includes an ADT for representing JSON values, a fluent navigation API (`JsonSelection`), configurable encoding/decoding (`JsonCodec` with `WriterConfig`/`ReaderConfig`), composable patches for transformations, a diff algorithm for computing minimal changes, and full JSON Schema 2020-12 support for validation and code generation.

The JSON codec module provides complete, type-safe support for working with JSON data in ZIO Blocks. It includes an ADT for representing JSON values, a fluent navigation API (`JsonSelection`), configurable encoding/decoding (`JsonCodec` with `WriterConfig`/`ReaderConfig`), composable patches for transformations, a diff algorithm for computing minimal changes, and full JSON Schema 2020-12 support for validation and code generation.

**Core types:** `Json`, `JsonCodec`, `JsonSelection`, `JsonPatch`, `JsonDiffer`, `JsonSchema`, `JsonType`.

```scala
import zio.blocks.schema._
import zio.blocks.schema.json._

// Represent any JSON value
val person = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))

// Navigate with fluent API
val age = person.get("age")  // JsonSelection

// Encode to string
val encoded = person.print(WriterConfig)

// Compute minimal patches
val updated = person.set(p".age", Json.Number(31))
val patch = JsonPatch.diff(person, updated)

// Validate against a schema
case class Person(name: String, age: Int)
object Person { implicit val schema: Schema[Person] = Schema.derived }
val schema: JsonSchema = Schema[Person].toJsonSchema
schema.conforms(person)  // true
```

## Installation

The JSON codec is included in the ZIO Blocks Schema module. Add it to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.51"
```

For Scala.js projects, use `%%%` instead:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-schema" % "0.0.51"
```

**Supported Scala versions:** 2.13.x and 3.x

The JSON codec is fully integrated into ZIO Blocks Schema and provides complete type-safe JSON support with no external dependencies beyond core ZIO libraries.

## How They Work Together

The JSON module workflow moves through representation, navigation, encoding/decoding, diffing, patching, and validation:

```
Parse/Create JSON
    ↓
Json (ADT: Object, Array, String, Number, Boolean, Null)
    ├─ Navigate with JsonSelection (fluent query API)
    ├─ Understand with JsonType (type information)
    └─ Transform via JsonPatch (composable operations)
    ↓
JsonCodec (encode/decode)
    ├─ WriterConfig (indent, escaping, order)
    ├─ ReaderConfig (strict validation, number handling)
    └─ MergeStrategy (field conflict resolution)
    ↓
JsonDiffer (diff algorithm)
    └─ Produces JsonPatch (minimal changes)
    ↓
JsonPatch.apply (transform JSON)
    ↓
JsonSchema (validate or generate)
    ├─ Derive from Scala types
    ├─ Validate JSON conformance
    └─ Generate documentation
```

**Type Relationships:**
- `Json` is the central value type; all operations start with or produce it
- `JsonSelection` provides fluent navigation through nested `Json` structures
- `JsonCodec` bridges Scala types ↔ `Json` with configurable `WriterConfig` and `ReaderConfig`
- `JsonDiffer` computes `JsonPatch` by comparing two `Json` values
- `JsonPatch` transforms `Json` values with composable operations
- `JsonSchema` validates `Json` conformance and derives from Scala `Schema` types
- `JsonType` provides type information for `Json` values at runtime

## Common Patterns

### Pattern 1: Parse, Transform, Serialize

Read JSON, modify it, and write it back:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, WriterConfig}

val jsonString = """{"name": "Alice", "age": 30}"""
val json = Json.parseUnsafe(jsonString)

// Transform
val updated = json.modify(p".age") {
  case Json.Number(n) => Json.Number(n + 1)
  case other => other
}

// Serialize
val output = updated.print(WriterConfig.withIndentionStep2)
```

### Pattern 2: Diff and Apply

Compute changes and apply them:

```scala
import zio.blocks.schema.json.{Json, JsonPatch}

val original = Json.Object("count" -> Json.Number(0))
val target = Json.Object("count" -> Json.Number(1), "active" -> Json.Boolean(true))

// Compute the diff
val patch = JsonPatch.diff(original, target)

// Apply to reconstruct
val result = patch.apply(original)
// Right({"count": 1, "active": true})
```

### Pattern 3: Validate with Schema

Generate and validate using schemas:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonSchema}

case class User(name: String, email: String, age: Int)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val jsonSchema: JsonSchema = Schema[User].toJsonSchema
val validJson = Json.Object(
  "name" -> Json.String("Alice"),
  "email" -> Json.String("alice@example.com"),
  "age" -> Json.Number(30)
)

jsonSchema.conforms(validJson)  // true
```

### Pattern 4: Navigate and Extract with JsonSelection

Use fluent API to navigate nested structures and extract values:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.Json

val data = Json.Object(
  "users" -> Json.Array(
    Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30)),
    Json.Object("name" -> Json.String("Bob"), "age" -> Json.Number(25))
  )
)

// Navigate to first user's age
val age: Either[SchemaError, Int] = data
  .get("users")        // JsonSelection
  (0)                 // Navigate to first array element
  .get("age")         // Navigate to age field
  .as[Int]            // Decode to Int

// Check values
val ageValue = data.get("users")(0).get("age").one  // Right(Json.Number(30))
```

### Pattern 5: Encode and Decode with Configuration

Control output formatting and parsing behavior:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, WriterConfig}

case class Config(host: String, port: Int)
object Config {
  implicit val schema: Schema[Config] = Schema.derived
}

val config = Config("localhost", 8080)
val json = config.toJson

// Encode with custom formatting
val pretty = json.print(WriterConfig.withIndentionStep(2))

// Decode: parse back to Json, then decode
val parsed: Json = Json.parseUnsafe(pretty)
val config2: Either[SchemaError, Config] = parsed.as[Config]
```

### Pattern 6: Compose Multiple Patches

Chain multiple transformations into a single patch:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonPatch}

val original = Json.Object("x" -> Json.Number(1), "y" -> Json.Number(2))

// Build patches independently
val patchX = JsonPatch.diff(
  original,
  original.set(p".x", Json.Number(10))
)

val patchY = JsonPatch.diff(
  original.set(p".x", Json.Number(10)),
  original.set(p".x", Json.Number(10)).set(p".y", Json.Number(20))
)

// Compose them
val combined = patchX ++ patchY
combined.apply(original)
// Right({"x": 10, "y": 20})
```

## Integration Points

**With other codecs:** `Json` values convert to/from other formats (Avro, TOON, etc.) via `DynamicValue`:

```scala
import zio.blocks.schema.json.Json
import zio.blocks.schema.DynamicValue

val json = Json.parseUnsafe("""{"name": "Alice"}""")
val dynamic: DynamicValue = json.toDynamicValue

// Convert to other formats using DynamicValue
```

**With Schema system:** `Schema` enables automatic JSON encoding/decoding:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.Json

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Encode using Schema-based extension methods
val person = Person("Alice", 30)
val json = person.toJson
val jsonString = person.toJsonString

// Decode: parse and extract with Schema
val parsed: Json = Json.parseUnsafe(jsonString)
val decoded: Either[SchemaError, Person] = parsed.as[Person]
```

**With patching system:** `JsonPatch` and `JsonDiffer` integrate with generic `Patch` infrastructure:

```scala
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.patch.DynamicPatch

val jsonPatch: JsonPatch = JsonPatch.root(JsonPatch.Op.Set(Json.Null))
val dynamicPatch: DynamicPatch = jsonPatch.toDynamicPatch
```

**With optics and navigation:** `JsonSelection` provides query-like access complementary to `Optic` system:

- `JsonSelection` — runtime navigation through unknown JSON structures
- `Optic` — compile-time, type-safe navigation through known Scala types
- Both support nested access, filtering, and composition

**With validation:** `JsonSchema` integrates with Schema `Validation` system:

- Derive `JsonSchema` from Scala `Schema[A]`
- Validate raw JSON before type-safe decoding
- Compose with logical operators (allOf, anyOf, oneOf)

## Type Pages

- **[Json](./json.md)** — The core ADT representing JSON values with six cases (Object, Array, String, Number, Boolean, Null). Covers construction, navigation, modification, transformation, filtering, merging, and encoding.

- **[JsonPatch](./json-patch.md)** — Composable patches for transforming JSON values. Create patches via diff or manually, compose them with `++`, and apply with different failure modes (Strict, Lenient, Clobber).

- **[JsonDiffer](./json-differ.md)** — Diff algorithm computing minimal patches. Uses smart strategies per type: NumberDelta for numbers, LCS-based StringEdit for strings, ArrayEdit with insertion/deletion for arrays, and ObjectEdit for fields.

- **[JSON Schema](./json-schema.md)** — Full JSON Schema 2020-12 support for validation and code generation. Derive schemas from Scala types, validate JSON, construct schemas manually with builders, and combine with logical operators.
