# YAML Codec Module

> `zio-blocks-schema-yaml` is a **schema-driven YAML codec module** for serializing and deserializing Scala types to and from YAML format. It provides comprehensive encoding and decoding with support for 27 primitive types, records, variants, sequences, maps, and recursive types. Core types: `Yaml`, `YamlCodec`, `YamlCodecDeriver`, `YamlOptions`.

`zio-blocks-schema-yaml` is a **schema-driven YAML codec module** for serializing and deserializing Scala types to and from YAML format. It provides comprehensive encoding and decoding with support for 27 primitive types, records, variants, sequences, maps, and recursive types. Core types: `Yaml`, `YamlCodec`, `YamlCodecDeriver`, `YamlOptions`.

The module integrates with a pure-Scala YAML parser and writer to provide human-readable serialization with optional JSON interoperability, configuration options for pretty-printing, and automatic schema generation.

## Motivation

YAML is a human-readable data format that appears widely across configuration files, Kubernetes manifests, CI/CD pipelines, and data serialization. Manually writing YAML encoders and decoders is error-prone and repetitive, especially for complex types with records, nested structures, and recursive definitions. `zio-blocks-schema-yaml` eliminates this friction by deriving codec instances directly from your Scala types using ZIO Schema. You describe your data shape once, and the module handles:
- Full YAML type support (mappings, sequences, scalars, null)
- Automatic schema generation from Scala types
- Pretty-printed and compact formatting options
- JSON interoperability for seamless conversion
- Precise error reporting with location traces showing the path to errors
- Recursive type support with automatic cycle detection
- Multiple encoding paths: byte arrays and strings
- Multiple decoding paths: byte arrays and strings
- Cross-platform compatibility (JVM and Scala.js)

Rather than hand-writing YAML parsing logic or relying on external libraries with limited Scala support, you work with strongly-typed schemas that the compiler validates.

## Installation

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-schema-yaml" % "0.0.51"
```

For Scala.js, use `%%%` instead of `%%`:

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-schema-yaml" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x

## Introduction

The module provides a complete pipeline for YAML codec derivation and usage:

1. **Define your type** — Any Scala type with a `Schema` instance
2. **Derive a codec** — Use `Schema[A].derive(YamlFormat)` to obtain a `YamlCodec[A]`
3. **Encode or decode** — Call `codec.encodeToString(value)` or `codec.decode(yamlString)`
4. **Handle errors** — Catch `SchemaError` with location traces showing where the error occurred
5. **Configure output** — Use `YamlOptions` for pretty-printing or compact formatting

The derivation process is automatic for all supported types (all 27 primitives, records, variants, sequences, maps). The module automatically generates YAML-compatible formats and handles encoding/decoding without manual configuration.

## How They Work Together

The YAML codec pipeline flows through these layers:

```
1. User defines Schema[A] for their type
                 ↓
2. Schema[A].derive(YamlFormat) creates YamlCodec[A]
                 ↓
3. YamlCodecDeriver derives Encoder and Decoder implementations
   - For primitives: type-specific YAML scalar encoders/decoders
   - For records: field-by-field composition with YAML mapping
   - For variants: union encoding with discriminators
   - For sequences: YAML sequence encoding/decoding
   - For maps: YAML mapping encoding/decoding
                 ↓
4. YamlCodec provides multiple encoding paths
   - encodeToString(value) → String
   - encode(value) → Array[Byte]
                 ↓
5. YamlCodec provides multiple decoding paths
   - decode(yaml: String) → Either[SchemaError, A]
   - decode(bytes: Array[Byte]) → Either[SchemaError, A]
                 ↓
6. YamlOptions controls output formatting
   - Pretty-printing with document markers
   - Compact output without markers
   - Custom indentation and style
                 ↓
7. Yaml AST provides structured representation
   - Mapping (key-value pairs)
   - Sequence (ordered lists)
   - Scalar (string values with optional tags)
   - NullValue (YAML null)
                 ↓
8. Errors include location traces
   Shows path (.field[index].nested) to error location
```

**Typical workflow:**

A user type flows through the derivation and encoding pipeline as follows:

```
User type (e.g., case class Config)
    ↓
Schema.derived (automatic via macro)
    ↓
Schema[Config].derive(YamlFormat) → YamlCodec[Config]
    ↓
Use codec.encodeToString(config) to serialize → String
Use codec.decode(yamlString) to deserialize → Either[SchemaError, Config]
    ↓
Handle SchemaError with location trace on failure
```

### Type Relationships

- **`Yaml`** — Sealed trait AST representing YAML data structures
- **`YamlCodec[A]`** — Main public API; contains encoder and decoder for bidirectional serialization
- **`YamlCodecDeriver`** — Configuration and derivation system; generates codecs from Schema
- **`YamlOptions`** — Configuration for output formatting (pretty, compact, indentation)
- **`YamlReader`, `YamlWriter`** — Low-level YAML parsing and serialization

## Common Patterns

This section shows practical patterns for working with YAML codecs in real-world scenarios.

### Pattern 1: Derive and Encode a Configuration Record

To derive and use a YAML codec for a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class AppConfig(name: String, port: Int, debug: Boolean)

object AppConfig {
implicit val schema: Schema[AppConfig] = Schema.derived[AppConfig]
}

val codec = AppConfig.schema.derive(YamlFormat)
val config = AppConfig("MyApp", 8080, true)
val yamlString = codec.encodeToString(config)
println(yamlString)
// name: MyApp
// port: 8080
// debug: true
```

### Pattern 2: Decode YAML with Error Handling

When decoding YAML data, errors include location traces showing where the problem occurred.

To decode YAML and handle errors with location information:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class DatabaseConfig(host: String, port: Int, username: String)

object DatabaseConfig {
  implicit val schema: Schema[DatabaseConfig] = Schema.derived
}

val codec = DatabaseConfig.schema.derive(YamlFormat)
val yaml = """
host: localhost
port: invalid
username: admin
"""

val result = codec.decode(yaml)

result match {
  case Right(config) => println(s"Loaded: $config")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
}
```

### Pattern 3: Pretty-Print with Configuration

Use `YamlOptions` to control output formatting with indentation and document markers.

To encode with pretty-printing:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Server(name: String, endpoints: List[String], timeout: Int)

object Server {
  implicit val schema: Schema[Server] = Schema.derived
}

val codec = Server.schema.derive(YamlFormat)
val server = Server("api-server", List("GET /health", "POST /data"), 30)

val prettyYaml = codec.encodeToString(server)
// ---
// name: api-server
// endpoints:
//   - GET /health
//   - POST /data
// timeout: 30
```

### Pattern 4: Handle Recursive Types

Recursive types (types that reference themselves) are fully supported with automatic cycle detection.

To define and encode a recursive data structure:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

sealed trait TreeNode
case class Leaf(value: String) extends TreeNode
case class Branch(label: String, children: List[TreeNode]) extends TreeNode

object TreeNode {
  implicit val schema: Schema[TreeNode] = Schema.derived
}

val codec = TreeNode.schema.derive(YamlFormat)
val tree: TreeNode = Branch("root", List(Leaf("a"), Branch("b", List(Leaf("c")))))
val yaml = codec.encodeToString(tree)
```

### Pattern 5: YAML-JSON Interoperability

Convert between YAML and JSON representations seamlessly using YamlJsonInterop.

To convert Yaml to Json:

```scala
import zio.blocks.schema.yaml._
import zio.blocks.schema.json._

val yamlString = """
name: Alice
age: 30
"""

val yaml = YamlReader.read(yamlString)
val json = yaml.toJson
// json represents the same data as JSON
```

---

## Yaml

Sealed trait representing a YAML node. Provides a complete AST for YAML data with four possible cases: `Mapping`, `Sequence`, `Scalar`, and `NullValue`.

### Overview

`Yaml` is a sealed trait that represents any valid YAML value. It can be constructed directly or derived from codecs.

### YAML AST Structure

To work with the YAML AST directly:

```scala
import zio.blocks.schema.yaml._

// Create a mapping (key-value pairs)
val mapping = Yaml.Mapping.fromStringKeys(
  "name" -> Yaml.Scalar("Alice"),
  "age" -> Yaml.Scalar("30")
)

// Create a sequence (ordered list)
val sequence = Yaml.Sequence(
  Yaml.Scalar("item1"),
  Yaml.Scalar("item2")
)

// Create a scalar (string value)
val scalar = Yaml.Scalar("hello")

// The null value
val nullValue = Yaml.NullValue
```

### Printing YAML

Format YAML nodes using compact or pretty-printed output:

```scala
import zio.blocks.schema.yaml._

val yaml = Yaml.Mapping.fromStringKeys(
  "name" -> Yaml.Scalar("Bob"),
  "active" -> Yaml.Scalar("true")
)

val compact = yaml.print
// name: Bob
// active: true

val pretty = yaml.printPretty
// ---
// name: Bob
// active: true
```

### Converting to JSON

Transform Yaml nodes to JSON for interoperability:

```scala
import zio.blocks.schema.yaml._

val yaml = Yaml.Scalar("42")
val json = yaml.toJson
```

---

## YamlCodec[A]

Main codec type for encoding and decoding values to and from YAML. Contains encoder and decoder for bidirectional serialization.

### Overview

`YamlCodec[A]` holds both an encoder and decoder, providing a complete solution for serializing and deserializing values in YAML format. The codec is derived automatically from a `Schema[A]`.

### Encoding Values to String

Use the codec to convert values to YAML strings:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Person(name: String, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(YamlFormat)
val person = Person("Alice", "alice@example.com")
val yaml = codec.encodeToString(person)
```

### Encoding Values to Byte Array

Convert values to YAML bytes:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Data(timestamp: Long, value: String)

object Data {
  implicit val schema: Schema[Data] = Schema.derived
}

val codec = Data.schema.derive(YamlFormat)
val data = Data(System.currentTimeMillis(), "sample")
val bytes = codec.encode(data)
```

### Decoding Values from String

Use the codec to convert YAML strings back to values:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Config(host: String, port: Int)

object Config {
  implicit val schema: Schema[Config] = Schema.derived
}

val codec = Config.schema.derive(YamlFormat)
val yaml = """
host: localhost
port: 8080
"""

val result: Either[zio.blocks.schema.SchemaError, Config] = codec.decode(yaml)
```

### Decoding Values from Byte Array

Read and decode values from YAML bytes:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Settings(debug: Boolean, timeout: Int)

object Settings {
  implicit val schema: Schema[Settings] = Schema.derived
}

val codec = Settings.schema.derive(YamlFormat)
// Use encoded bytes from a previous encoding
val settings = Settings(debug = true, timeout = 30)
val bytes = codec.encodeToString(settings).getBytes("UTF-8")

val result = codec.decode(bytes)
```

---

## YamlCodecDeriver

Configuration and derivation system for creating `YamlCodec[A]` instances from `Schema[A]`.

### Overview

`YamlCodecDeriver` implements the schema-driven derivation of YAML codecs. It automatically handles 27 primitive types and complex types (records, variants, sequences, maps), generating appropriate YAML encoders and decoders.

### How Derivation Works

To create a codec from a schema:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class User(id: Int, name: String, active: Boolean)

object User {
  implicit val schema: Schema[User] = Schema.derived
}

val codec = User.schema.derive(YamlFormat)
```

### Primitive Type Support

All 27 ZIO Schema primitives are supported:
- Numeric: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`
- Logical: `Boolean`, `Char`, `String`
- Temporal: `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `Month`, `DayOfWeek`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `ZoneId`, `ZoneOffset`
- Special: `UUID`, `Currency`, `Unit`

### Record Type Support

Case classes (records) are fully supported. Each field becomes a named key in the YAML mapping:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Address(street: String, city: String, zipCode: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

val codec = Address.schema.derive(YamlFormat)
```

### Variant Type Support

Sealed traits and sum types are encoded as YAML mappings with discriminator information:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

sealed trait Status
case class Running(pid: Int) extends Status
case class Stopped(exitCode: Int) extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

val codec = Status.schema.derive(YamlFormat)
```

---

## YamlOptions

Configuration for YAML output formatting. Controls indentation, document markers, and presentation style.

### Overview

`YamlOptions` provides predefined configurations and factory methods for customizing YAML serialization output.

### Pretty-Printing Configuration

Enable pretty-printed output with document markers:

```scala
import zio.blocks.schema.yaml._

val prettyOptions = YamlOptions.pretty
// Enables:
// - Document markers (---)
// - Indentation (default 2 spaces)
// - Readable formatting
```

### Compact Output

Use compact formatting without document markers:

```scala
import zio.blocks.schema.yaml._

val compactOptions = YamlOptions.default
// Disables document markers for inline or file formats
```

---

## Error Handling

YAML decoding errors include location traces showing the path through nested structures where the error occurred.

### Understanding Error Traces

Errors render as paths like `.field[0].nested.value` showing exactly where decoding failed:

```scala
import zio.blocks.schema._
import zio.blocks.schema.yaml._

case class Team(name: String, members: List[String])

object Team {
  implicit val schema: Schema[Team] = Schema.derived
}

val codec = Team.schema.derive(YamlFormat)
val invalidYaml = """
name: Engineering
members: invalid
"""

val result = codec.decode(invalidYaml)

result match {
  case Right(team) => println(s"Loaded: $team")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
}
```

### Zero-Overhead Error Handling

Errors use zero-overhead exceptions (no stack traces) for efficient error reporting in scenarios where errors are expected and handled inline.
