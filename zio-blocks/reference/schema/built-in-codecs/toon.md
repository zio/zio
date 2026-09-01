# TOON Codec Module

> `zio-blocks-schema-toon` is a **schema-driven TOON codec module** for serializing and deserializing Scala types to and from TOON format. It provides comprehensive encoding and decoding with support for 27 primitive types, records, variants, sequences, maps, and recursive types. Core types: `ToonCodec`, `ToonCodecDeriver`, `ToonFormat`, `ToonReader`, `ToonWriter`.

The module integrates with a pure-Scala TOON parser and writer to provide line-oriented, indentation-based serialization that is 30-60% more compact than JSON. TOON (Token-Oriented Object Notation) appears widely across LLM prompts, configuration files, and data exchange where compactness and human readability matter equally.

## Motivation

TOON is a compact, line-oriented text format that encodes the JSON data model with explicit structure and minimal quoting. It appears widely in modern LLM applications, configuration management, and streaming data scenarios where bandwidth and readability are both critical. Manually writing TOON encoders and decoders is error-prone and repetitive, especially for complex types with records, nested structures, and recursive definitions. `zio-blocks-schema-toon` eliminates this friction by deriving codec instances directly from your Scala types using ZIO Schema. You describe your data shape once, and the module handles:
- Full TOON type support (records, sequences, primitives, null)
- Three array formats: inline (`tags[3]: a,b,c`), tabular (`users[2]{id,name}:`), and list (`- item`)
- Automatic schema generation from Scala types
- Customizable field/case name mapping (identity, snake_case, kebab-case, etc.)
- Configurable discriminator strategies for algebraic data types
- Precise error reporting with location traces showing the path to errors
- Recursive type support with automatic cycle detection
- Multiple encoding paths: byte arrays, streams, ByteBuffers, and strings
- Multiple decoding paths: byte arrays, streams, ByteBuffers, and strings
- Cross-platform compatibility (JVM and Scala.js)

Rather than hand-writing TOON parsing logic or relying on external libraries with limited Scala support, you work with strongly-typed schemas that the compiler validates.

## Installation

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon" % "0.0.51"
```

For Scala.js, use `%%%` instead of `%%`:

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-schema-toon" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x

## Introduction

The module provides a complete pipeline for TOON codec derivation and usage:

1. **Define your type** — Any Scala type with a `Schema` instance
2. **Derive a codec** — Use `Schema[A].derive(ToonFormat)` to obtain a `ToonCodec[A]`
3. **Encode or decode** — Call `codec.encode(value)` or `codec.decode(toonBytes)`
4. **Handle errors** — Catch `SchemaError` with location traces showing where the error occurred
5. **Customize output** — Use `WriterConfig` for indentation and formatting, `ReaderConfig` for parsing behavior

The derivation process is automatic for all supported types (all 27 primitives, records, variants, sequences, maps). The module automatically generates TOON-compatible formats and handles encoding/decoding without manual configuration.

## How They Work Together

The TOON codec pipeline flows through these layers:

```
1. User defines Schema[A] for their type
                 ↓
2. Schema[A].derive(ToonFormat) creates ToonCodec[A]
                 ↓
3. ToonCodecDeriver derives Encoder and Decoder implementations
   - For primitives: type-specific TOON scalar encoders/decoders
   - For records: field-by-field composition with indented nesting
   - For variants: union encoding with optional discriminators
   - For sequences: one of three formats (inline, tabular, list)
   - For maps: record-like encoding with key-value pairs
                 ↓
4. ToonCodec provides multiple encoding paths
   - encode(value) → Array[Byte]
   - encode(value, output: OutputStream) → Unit
   - encode(value, buffer: ByteBuffer) → Unit
   - encodeToString(value) → String
                 ↓
5. ToonCodec provides multiple decoding paths
   - decode(bytes: Array[Byte]) → Either[SchemaError, A]
   - decode(input: InputStream) → Either[SchemaError, A]
   - decode(buffer: ByteBuffer) → Either[SchemaError, A]
   - decode(toon: String) → Either[SchemaError, A]
                 ↓
6. WriterConfig controls output formatting
   - Indentation (default: 2 spaces per level)
   - Key folding strategy (flatten nested keys or expand)
   - Discriminator field for variants
   - Array format selection
                 ↓
7. ReaderConfig controls parsing behavior
   - Path expansion (parse dot-separated keys as nested)
   - Delimiter selection for inline arrays
   - Strict parsing mode (enforce TOON compliance)
   - Discriminator field recognition
                 ↓
8. Errors include location traces
   Shows path (.field[index].nested) to error location
```

A user type flows through the derivation and encoding pipeline as follows:

```
User type (e.g., case class Person)
    ↓
Schema.derived (automatic via macro)
    ↓
Schema[Person].derive(ToonFormat) → ToonCodec[Person]
    ↓
Use codec.encode(person) to serialize → Array[Byte]
Use codec.decode(toonBytes) to deserialize → Either[SchemaError, Person]
    ↓
Handle SchemaError with location trace on failure
```

### Type Relationships

- **`ToonCodec[A]`** — Main public API; contains encoder and decoder for bidirectional serialization
- **`ToonCodecDeriver`** — Configuration and derivation system; generates codecs from Schema
- **`ToonFormat`** — Integration with ZIO Schema format system; enables `Schema[A].derive(ToonFormat)`
- **`WriterConfig`** — Output formatting configuration (indentation, delimiters, discriminators)
- **`ReaderConfig`** — Input parsing configuration (path expansion, strict mode, delimiters)
- **`ToonReader`, `ToonWriter`** — Low-level TOON parsing and serialization
- **`NameMapper`** — Field and case name transformation strategies
- **`ArrayFormat`** — Controls sequence encoding (Inline, Tabular, List, Auto)
- **`Delimiter`** — Specifies separator for inline arrays (Comma, Tab, Pipe)

## Common Patterns

This section shows practical patterns for working with TOON codecs in real-world scenarios.

### Pattern 1: Derive and Encode a Configuration Record

For a case class with primitive fields, derive a codec and encode to TOON string.

To derive a TOON codec for a record type and encode a value:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class AppConfig(name: String, port: Int, debug: Boolean)

object AppConfig {
implicit val schema: Schema[AppConfig] = Schema.derived[AppConfig]
}

val codec = AppConfig.schema.derive(ToonFormat)
val config = AppConfig("MyApp", 8080, true)
val toonBytes = codec.encode(config)
// Output:
// name: MyApp
// port: 8080
// debug: true
```

### Pattern 2: Decode TOON with Error Handling

When decoding TOON data, errors include location traces showing where the problem occurred.

To decode TOON and handle errors with location information:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class DatabaseConfig(host: String, port: Int, username: String)

object DatabaseConfig {
  implicit val schema: Schema[DatabaseConfig] = Schema.derived
}

val codec = DatabaseConfig.schema.derive(ToonFormat)
val toon = """
host: localhost
port: invalid
username: admin
"""

val result = codec.decode(toon)

result match {
  case Right(config) => println(s"Loaded: $config")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
    // Error: Expected int, got: invalid at: .port
}
```

### Pattern 3: Work with Arrays in Different Formats

TOON supports three formats for encoding sequences, selectable via `ArrayFormat`.

To encode arrays using inline format (compact representation):

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Tags(items: List[String])

object Tags {
  implicit val schema: Schema[Tags] = Schema.derived
}

// Default format: auto (inline for primitives)
val codec = Tags.schema.derive(ToonFormat)
val tags = Tags(List("scala", "zio", "functional"))

val toonBytes = codec.encodeToString(tags)
// Output:
// items[3]: scala,zio,functional
```

To encode objects using tabular format for compact representation:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class User(id: Int, name: String)
case class Team(members: List[User])

object User {
  implicit val schema: Schema[User] = Schema.derived
}

object Team {
  implicit val schema: Schema[Team] = Schema.derived
}

// Tabular format for uniform object arrays
val tabularDeriver = ToonCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
val codec = Team.schema.derive(tabularDeriver)
val team = Team(List(User(1, "Alice"), User(2, "Bob")))

val toonBytes = codec.encodeToString(team)
// Output:
// members[2]{id,name}:
//   1,Alice
//   2,Bob
```

### Pattern 4: Customize Field Names and Discriminators

Configure the deriver to map field names and control variant encoding.

To derive a codec with custom field name transformation:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Person(firstName: String, lastName: String, emailAddress: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Use snake_case for field names
val customDeriver = ToonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase)
val codec = Person.schema.derive(customDeriver)

val person = Person("Alice", "Smith", "alice@example.com")
val toonBytes = codec.encodeToString(person)
// Output:
// first_name: Alice
// last_name: Smith
// email_address: alice@example.com
```

### Pattern 5: Handle Recursive Types

Recursive types (types that reference themselves) are fully supported with automatic cycle detection.

To define and encode a recursive data structure:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

sealed trait TreeNode
case class Leaf(value: String) extends TreeNode
case class Branch(label: String, children: List[TreeNode]) extends TreeNode

object TreeNode {
  implicit val schema: Schema[TreeNode] = Schema.derived
}

val codec = TreeNode.schema.derive(ToonFormat)
val tree: TreeNode = Branch("root", List(Leaf("a"), Branch("b", List(Leaf("c")))))
val toonBytes = codec.encodeToString(tree)
// Output:
// Branch:
//   label: root
//   children[2]:
//     - Leaf:
//         value: a
//     - Branch:
//         label: b
//         children[1]:
//           - Leaf:
//               value: c
```

---

## ToonCodec[A]

Main codec type for encoding and decoding values to and from TOON format. Contains encoder and decoder for bidirectional serialization.

### Overview

`ToonCodec[A]` holds both an encoder and decoder, providing a complete solution for serializing and deserializing values in TOON format. The codec is derived automatically from a `Schema[A]` using `ToonFormat`.

### Encoding Values to Byte Array

Use the codec to convert values to byte arrays:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Person(name: String, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(ToonFormat)
// codec: ToonCodec[Person] = zio.blocks.schema.toon.ToonCodecDeriver$$anon$5@3163694a
val person = Person("Alice", "alice@example.com")
// person: Person = Person(name = "Alice", email = "alice@example.com")
val bytes = codec.encode(person)
// bytes: Array[Byte] = Array(
//   110,
//   97,
//   109,
//   101,
//   58,
//   32,
//   65,
//   108,
//   105,
//   99,
//   101,
//   10,
//   101,
//   109,
//   97,
//   105,
//   108,
//   58,
//   32,
//   97,
//   108,
//   105,
//   99,
//   101,
//   64,
//   101,
//   120,
//   97,
//   109,
//   112,
//   108,
//   101,
//   46,
//   99,
//   111,
//   109
// )
```

### Encoding Values to String

Convert values to human-readable TOON strings:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Config(host: String, port: Int)

object Config {
  implicit val schema: Schema[Config] = Schema.derived
}

val codec = Config.schema.derive(ToonFormat)
val config = Config("localhost", 8080)
val toonString = codec.encodeToString(config)
```

### Encoding Values to Stream

Write encoded values directly to an output stream:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._
import java.io.ByteArrayOutputStream

case class Data(timestamp: Long, value: String)

object Data {
  implicit val schema: Schema[Data] = Schema.derived
}

val codec = Data.schema.derive(ToonFormat)
val data = Data(System.currentTimeMillis(), "sample")
val output = new ByteArrayOutputStream()
codec.encode(data, output)
val bytes = output.toByteArray
```

### Decoding Values from Byte Array

Use the codec to convert byte arrays back to values:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Settings(debug: Boolean, timeout: Int)

object Settings {
  implicit val schema: Schema[Settings] = Schema.derived
}

val codec = Settings.schema.derive(ToonFormat)
// Create bytes from a previous encoding
val settings = Settings(debug = true, timeout = 30)
val bytes = codec.encode(settings)

val result: Either[zio.blocks.schema.SchemaError, Settings] = codec.decode(bytes)
```

### Decoding Values from String

Parse and decode values from TOON strings:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class ServerConfig(name: String, port: Int)

object ServerConfig {
  implicit val schema: Schema[ServerConfig] = Schema.derived
}

val codec = ServerConfig.schema.derive(ToonFormat)
val toon = """
name: api-server
port: 8080
"""

val result = codec.decode(toon)
```

### Decoding Values from Stream

Read and decode values from bytes:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Message(id: Long, text: String)

object Message {
  implicit val schema: Schema[Message] = Schema.derived
}

val codec = Message.schema.derive(ToonFormat)
val message = Message(42, "Hello TOON")
val encoded = codec.encode(message)
val result = codec.decode(encoded)
```

---

## ToonCodecDeriver

Configuration and derivation system for creating `ToonCodec[A]` instances from `Schema[A]`.

### Overview

`ToonCodecDeriver` implements the schema-driven derivation of TOON codecs. It automatically handles 27 primitive types and complex types (records, variants, sequences, maps), generating appropriate TOON encoders and decoders with comprehensive customization options.

### How Derivation Works

To create a codec from a schema:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class User(id: Int, name: String, active: Boolean)

object User {
  implicit val schema: Schema[User] = Schema.derived
}

val codec = User.schema.derive(ToonFormat)
```

### Primitive Type Support

All 27 ZIO Schema primitives are supported:
- Numeric: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`
- Logical: `Boolean`, `Char`, `String`
- Temporal: `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `Month`, `DayOfWeek`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `ZoneId`, `ZoneOffset`
- Special: `UUID`, `Currency`, `Unit`

### Record Type Support

Case classes (records) are fully supported. Each field becomes an indented key-value pair in the TOON output:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Address(street: String, city: String, zipCode: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

val codec = Address.schema.derive(ToonFormat)
```

### Variant Type Support

Sealed traits and sum types are encoded as discriminated variants, with customizable discriminator strategies:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

sealed trait Status
case class Running(pid: Int) extends Status
case class Stopped(exitCode: Int) extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

val codec = Status.schema.derive(ToonFormat)
```

### Configuration Methods

To customize derivation behavior, chain configuration methods on `ToonCodecDeriver`:

To apply field name transformation:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

val snakeCaseDeriver = ToonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase)
```

To select array format:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

val tabularDeriver = ToonCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
```

To customize variant discriminators:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

val fieldDeriver = ToonCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type"))
```

---

## ToonFormat

Integration point with ZIO Schema's format system. Provides `BinaryFormat[ToonCodec]` to enable `Schema[A].derive(ToonFormat)` for any supported type.

### Using ToonFormat

To derive a TOON codec using the standard format:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Sensor(id: Long, temperature: Double, humidity: Float)

object Sensor {
  implicit val schema: Schema[Sensor] = Schema.derived
}

val codec = Sensor.schema.derive(ToonFormat)
```

`ToonFormat` is a singleton object extending `BinaryFormat[ToonCodec]` with the MIME type `"text/toon"` and the `ToonCodecDeriver` as its derivation strategy.

---

## WriterConfig

Configuration for TOON output formatting. Controls indentation, key folding, and discriminator field naming.

### Overview

`WriterConfig` provides predefined configurations and factory methods for customizing TOON serialization output. It controls how records are nested, whether keys are flattened, and how variant discriminators are named.

### Indentation Configuration

Control spacing for nested indentation:

```scala
import zio.blocks.schema.toon._

val config = WriterConfig.withIndent(4)
// Uses 4 spaces per indentation level (default: 2)
```

### Key Folding Strategy

Flatten nested keys for shallow records or keep them separate:

```scala
import zio.blocks.schema.toon._

val config = WriterConfig.withKeyFolding(KeyFolding.Safe)
// Converts nested keys to dot-separated paths for shallow nesting
```

### Discriminator Field Configuration

Configure how variant discriminators appear in output:

```scala
import zio.blocks.schema.toon._

val config = WriterConfig.withDiscriminatorField(Some("_type"))
// Variants use "_type" field instead of default key discriminator
```

---

## ReaderConfig

Configuration for TOON input parsing. Controls path expansion, delimiter selection, and strict parsing mode.

### Overview

`ReaderConfig` provides factory methods for customizing TOON deserialization behavior. It controls whether dot-separated keys expand to nested records, which delimiters separate inline array elements, and whether parsing is strict.

### Path Expansion Configuration

Parse dot-separated keys as nested records:

```scala
import zio.blocks.schema.toon._

val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
// Interprets "user.name" as nested {user: {name: value}}
```

### Delimiter Configuration

Specify separator for inline arrays:

```scala
import zio.blocks.schema.toon._

val config = ReaderConfig.withDelimiter(Delimiter.Tab)
// Expects tab characters instead of commas in inline arrays
```

### Strict Parsing Mode

Enable or disable strict TOON parsing:

```scala
import zio.blocks.schema.toon._

val config = ReaderConfig.withStrict(false)
// Allows lenient parsing (default: true for strict compliance)
```

---

## ArrayFormat

Controls how sequences are encoded in TOON output.

### Overview

`ArrayFormat` selects from three distinct strategies for representing sequences in TOON format: compact inline representation, tabular grid format, or list-style items.

### Inline Format

Encode arrays as comma-separated values on a single line:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withArrayFormat(ArrayFormat.Inline)
// tags[3]: scala,zio,functional
```

### Tabular Format

Encode uniform object arrays as compact tables with headers:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
// users[2]{id,name}:
//   1,Alice
//   2,Bob
```

### List Format

Encode arrays using list-style items with dashes:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withArrayFormat(ArrayFormat.List)
// items:
//   - item1
//   - item2
```

### Auto Format

Let the encoder select the best format based on content:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withArrayFormat(ArrayFormat.Auto)
// Inline for primitives, tabular for uniform objects, list for mixed
```

---

## Delimiter

Specifies the separator character for inline array elements.

### Overview

`Delimiter` controls which character separates elements in inline array format.

### Available Delimiters

Use comma as the default separator:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withDelimiter(Delimiter.Comma)
// tags[3]: scala,zio,functional
```

Use tab for wider compatibility with tab-separated values:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withDelimiter(Delimiter.Tab)
// tags[3]: scala	zio	functional
```

Use pipe for visibility in complex inline arrays:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withDelimiter(Delimiter.Pipe)
// tags[3]: scala|zio|functional
```

---

## ToonReader

Low-level binary parser implementing TOON format with state management and efficient token scanning.

### Overview

`ToonReader` provides stateful reading of TOON-encoded data with automatic token recognition and error reporting at the byte level. It handles indentation tracking, quoted string parsing, and numeric value extraction.

### Reading from Byte Array

To parse TOON data from a byte array:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Person(name: String, age: Int)
object Person { implicit val schema: Schema[Person] = Schema.derived }

val toonBytes = "name: Alice\nage: 30".getBytes("UTF-8")
// Use the codec API for reading (ToonReader is an internal implementation detail)
val codec = Person.schema.derive(ToonFormat)
val result = codec.decode(toonBytes)
```

### Low-Level Parsing

Direct token-by-token parsing for custom scenarios:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

// ToonReader is an internal implementation detail of the codec
// Use the public codec API instead:
case class Data(id: Int, name: String)
object Data { implicit val schema: Schema[Data] = Schema.derived }

val codec = Data.schema.derive(ToonFormat)
// Codec provides token-aware parsing and composition for reading
val toonString = "id: 1\nname: test"
val result = codec.decode(toonString)
```

---

## ToonWriter

Low-level binary encoder implementing TOON format with optimization for compact representation.

### Overview

`ToonWriter` provides efficient writing of Scala values into TOON binary format with automatic formatting, indentation, and type-specific encoding.

### Writing to Byte Array

To encode values to TOON format:

```scala
import zio.blocks.schema.toon._
```

### Custom Encoding Strategies

The writer selects encoding strategies based on value type and configuration:

```scala
import zio.blocks.schema.toon._
```

---

## NameMapper

Transformation strategies for field and case names during encoding and decoding.

### Overview

`NameMapper` provides predefined transformation functions for converting between Scala naming conventions and TOON field names. This enables encoding data with different naming styles without changing your Scala types.

### Identity Mapping

Use Scala field names as-is without transformation:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withFieldNameMapper(NameMapper.Identity)
// firstName → firstName
```

### Snake Case Mapping

Convert camelCase field names to snake_case:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase)
// firstName → first_name
// emailAddress → email_address
```

### Kebab Case Mapping

Convert camelCase field names to kebab-case:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withFieldNameMapper(NameMapper.KebabCase)
// firstName → first-name
// emailAddress → email-address
```

---

## DiscriminatorKind

Controls how variant (sealed trait) discriminators appear in TOON output.

### Overview

`DiscriminatorKind` determines whether variant types are identified by a key discriminator, a field discriminator, or no discriminator at all.

### Key Discriminator

Use the variant case name as the TOON key (default):

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Key)
// Running:
//   pid: 42
```

### Field Discriminator

Include a dedicated field for the variant type:

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type"))
// type: Running
// pid: 42
```

### No Discriminator

Omit discriminators entirely (for unambiguous contexts):

```scala
import zio.blocks.schema.toon._

val deriver = ToonCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None)
// pid: 42
```

---

## KeyFolding

Strategy for flattening nested record keys into dot-separated paths during encoding.

### Overview

`KeyFolding` controls whether deeply nested fields are flattened using dot notation or kept as indented structures. This is useful when generating TOON for consumption by systems expecting flat key-value pairs.

### Off (No Flattening)

Keep nested structure as indented records:

```scala
import zio.blocks.schema.toon._

val config = WriterConfig.withKeyFolding(KeyFolding.Off)
// address:
//   street: Main St
//   city: Springfield
```

### Safe Flattening

Flatten shallow nesting with dot-separated keys:

```scala
import zio.blocks.schema.toon._

val config = WriterConfig.withKeyFolding(KeyFolding.Safe)
// address.street: Main St
// address.city: Springfield
```

---

## PathExpansion

Strategy for parsing dot-separated keys as nested records during decoding.

### Overview

`PathExpansion` controls whether TOON keys like `user.name` are interpreted as nested structures or literal key names. This enables reading flat TOON data as deeply nested types.

### Off (No Expansion)

Treat dots in keys as literal characters:

```scala
import zio.blocks.schema.toon._

val config = ReaderConfig.withExpandPaths(PathExpansion.Off)
// Literal key "user.name" stays flat
```

### Safe Expansion

Expand dot-separated keys to nested records:

```scala
import zio.blocks.schema.toon._

val config = ReaderConfig.withExpandPaths(PathExpansion.Safe)
// Key "user.name" expands to {user: {name: value}}
```

---

## Integration Points

The TOON codec module integrates seamlessly with ZIO Schema and other ZIO Blocks modules:

**With ZIO Schema:**
- The module uses `Schema[A]` to derive codecs automatically
- Supports all schema types: primitives, records, variants, sequences, maps, options
- Integrates through the standard `BinaryFormat` mechanism

**Internal Type Relationships:**
- `ToonCodecDeriver` drives all codec derivation via `ToonCodecDeriver.derive(schema)`
- `WriterConfig` controls `ToonWriter` behavior during encoding
- `ReaderConfig` controls `ToonReader` behavior during decoding
- `NameMapper`, `ArrayFormat`, `Delimiter`, `DiscriminatorKind` all flow through `ToonCodecDeriver` configuration
- `KeyFolding` and `PathExpansion` affect how nested structures are serialized/deserialized

**With Other Modules:**
- TOON codecs work alongside other codec modules (JSON, Thrift, YAML, MessagePack) via the format system
- Can be combined with `Schema` evolution utilities for schema versioning
- Integrates with `DynamicValue` for generic data handling

---

## Error Handling

TOON decoding errors include location traces showing the path through nested structures where the error occurred.

### Understanding Error Traces

Errors render as paths like `.field[0].nested.value` showing exactly where decoding failed:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Team(name: String, members: List[String])

object Team {
  implicit val schema: Schema[Team] = Schema.derived
}

val codec = Team.schema.derive(ToonFormat)
val invalidToon = """
name: Engineering
members: invalid
"""

val result = codec.decode(invalidToon)

result match {
  case Right(team) => println(s"Loaded: $team")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
    // Error: Expected sequence, got: invalid at: .members
}
```

### Zero-Overhead Error Handling

Errors use zero-overhead exceptions (no stack traces) for efficient error reporting in scenarios where errors are expected and handled inline.
