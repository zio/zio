# JSON Configuration

> The JSON codec module provides four configuration types for controlling encoding and decoding behavior: `WriterConfig`, `ReaderConfig`, `MergeStrategy`, and `NameMapper`. These types allow fine-grained control over how JSON is serialized and deserialized.

## WriterConfig

`WriterConfig` controls the formatting and content of encoded JSON output. Use it when calling `Json#print` on `Json` values or when encoding values with specific formatting requirements.

### Configuration Options

| Option             | Type    | Default | Purpose                                                       |
|--------------------|---------|---------|---------------------------------------------------------------|
| `indentionStep`    | Int     | 0       | Spaces per indentation level (0 = compact JSON)               |
| `escapeUnicode`    | Boolean | false   | Escape non-ASCII characters as `\uXXXX` for ASCII-only output |
| `preferredBufSize` | Int     | 32768   | Internal buffer size in bytes for streaming                   |

### Usage Examples

Pretty-print JSON output with configurable indentation:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{WriterConfig}

case class Config(server: String, port: Int, debug: Boolean)
object Config { implicit val schema: Schema[Config] = Schema.derived }

val config = Config("localhost", 8080, true)
val json = config.toJson

// Pretty-printed with 2-space indentation
val pretty = json.print(WriterConfig.withIndentionStep(2))
// {
//   "server": "localhost",
//   "port": 8080,
//   "debug": true
// }
```

Configure custom indentation for JSON output:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.WriterConfig

case class User(name: String, email: String, age: Int)
object User { implicit val schema: Schema[User] = Schema.derived }

val user = User("Alice", "alice@example.com", 30)
val json = user.toJson

// 4-space indentation
val sorted = json.print(WriterConfig
  .withIndentionStep(4)
)
// {
//     "name": "Alice",
//     "email": "alice@example.com",
//     "age": 30
// }
```

Escape non-ASCII characters for ASCII-only transmission:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.WriterConfig

case class Message(text: String)
object Message { implicit val schema: Schema[Message] = Schema.derived }

val msg = Message("Hello 世界")
val json = msg.toJson

// Escape non-ASCII characters - non-ASCII become \uXXXX sequences
val ascii = json.print(WriterConfig.withEscapeUnicode(true))
// Output: {"text":"Hello \\u4e16\\u754c"}
```

## ReaderConfig

`ReaderConfig` controls how JSON input is parsed and validated during decoding. Use it when calling `Json.parse()` with custom parsing behavior.

### Configuration Options

| Option                 | Type    | Default | Purpose                                |
|------------------------|---------|---------|----------------------------------------|
| `checkForEndOfInput`   | Boolean | true    | Verify no extra input after valid JSON |
| `preferredCharBufSize` | Int     | 8192    | Internal character buffer size         |

### Usage Examples

Allow trailing whitespace after valid JSON during parsing:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, ReaderConfig}

// Lenient: allow trailing whitespace
val lenientConfig = ReaderConfig.withCheckForEndOfInput(false)

val jsonString = """{"name": "Alice"}   """  // Trailing spaces
val result = Json.parse(jsonString, lenientConfig)
// Right(Json.Object(...))
```

## MergeStrategy

`MergeStrategy` determines how field conflicts are resolved when merging two JSON objects. It provides strategies for different use cases, from strict validation to lenient concatenation.

**Available Strategies:**
- `Strict` — Fails if the same field appears in both objects
- `Right` — The right object's values override the left's
- `Left` — The left object's values take precedence
- Additional strategies may be available depending on your use case

For details on merge strategies and their usage, see the Merging section of the [Json](./json.md) documentation.

## NameMapper

`NameMapper` customizes how field names are transformed between Scala types and JSON representation. It enables patterns like camelCase ↔ snake_case conversion.

### Built-in Mappers

Use identity mapping to keep field names unchanged:

```scala
import zio.blocks.schema._

case class User(firstName: String, lastName: String)
object User { 
  implicit val schema: Schema[User] = Schema.derived
}

// Default: no transformation
val user = User("Alice", "Smith")
val json = user.toJson
// {"firstName": "Alice", "lastName": "Smith"}
```

**Snake Case:**
Convert camelCase to snake_case:

```scala
import zio.blocks.schema._

case class User(firstName: String, lastName: String)
object User { 
  implicit val schema: Schema[User] = Schema.derived
  implicit val nameMapper: NameMapper = NameMapper.SnakeCase
}

val user = User("Alice", "Smith")
val json = user.toJson
// {"first_name": "Alice", "last_name": "Smith"}
```

**Kebab Case:**
Convert camelCase to kebab-case:

```scala
import zio.blocks.schema._

case class User(firstName: String, lastName: String)
object User { 
  implicit val schema: Schema[User] = Schema.derived
  implicit val nameMapper: NameMapper = NameMapper.KebabCase
}

val user = User("Alice", "Smith")
val json = user.toJson
// {"first-name": "Alice", "last-name": "Smith"}
```

**Custom Mappers:**
Additional custom name mappers may be available depending on your needs. The built-in mappers (Identity, SnakeCase, KebabCase) cover most common use cases.

## Combining Configuration

Use multiple configuration options together for fine-grained control:

```scala
import zio.blocks.schema._
import zio.blocks.schema.json.WriterConfig

case class Config(server: String, port: Int, timeout: Option[Int])
object Config { implicit val schema: Schema[Config] = Schema.derived }

val config = Config("example.com", 443, None)
val json = config.toJson

// Pretty-printed with 4-space indentation
val output = json.print(WriterConfig
  .withIndentionStep(4)
)
// {
//     "server": "example.com",
//     "port": 443
// }
```

## Performance Implications

- **WriterConfig:** Indentation increases output size but has minimal performance impact
- **ReaderConfig:** `checkForEndOfInput` adds one comparison
- **MergeStrategy:** Strategy choice affects merge performance based on use case
- **NameMapper:** Applied once per field during schema derivation; zero runtime cost if identity mapping

## Best Practices

1. **Use WriterConfig for human-readable output only** — Compact output is faster for wire transmission
2. **Choose MergeStrategy based on use case** — Strict for validation, others for defaults
3. **Standardize on one NameMapper** across your codebase to avoid confusion
4. **Cache WriterConfig/ReaderConfig instances** — They're immutable and can be reused
