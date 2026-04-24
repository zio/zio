# Serialization Formats

> ZIO Blocks Schema provides automatic codec derivation for multiple serialization formats. Once you have a `Schema[A]` for your data type, you can derive codecs for any supported format using the unified `Schema.derive(Format)` pattern.

ZIO Blocks Schema provides automatic codec derivation for multiple serialization formats. Once you have a `Schema[A]` for your data type, you can derive codecs for any supported format using the unified `Schema.derive(Format)` pattern.

A `Format` is an abstraction that bundles together everything needed to serialize and deserialize data in a specific format (JSON, Avro, Protobuf, etc.).

## Overview

Each format defines the types of input for decoding and output for encoding, as well as the typeclass used as a codec for that format. Each format contains a `Deriver` corresponding to its specific MIME type, which is used to derive codecs from schemas:

```scala
trait Format {
  type DecodeInput
  type EncodeOutput
  type TypeClass[A] <: Codec[DecodeInput, EncodeOutput, A]
  def mimeType: String
  def deriver: Deriver[TypeClass]
}
```

It unifies all metadata related to serialization formats, such as MIME type and codec deriver, in a single place. This allows for a consistent API across different formats when deriving codecs from schemas. Having MIME type information helps with runtime content negotiation and format routing, for example in HTTP servers or message queues.

That is, you can easily call [`Schema[A].derive(format)`](./type-class-derivation.md#using-the-deriver-to-derive-type-class-instances) for any format that implements the `Format` trait, and receive a codec that can encode and decode values of type `A` according to the rules of that format.

Formats are categorized into `BinaryFormat` and `TextFormat`, which specify the types of input and output for encoding and decoding:

```scala
sealed trait Format
abstract class BinaryFormat[...](...) extends Format { ... }
abstract class TextFormat[...](...) extends Format { ... }
```

For example, the `JsonFormat` is a `BinaryFormat` that represents a JSON binary format, where the input for decoding is `ByteBuffer` and the output for encoding is also `ByteBuffer`, the MIME type is `application/json`, and the deriver for generating codecs from schemas is `JsonCodecDeriver`:

```scala
object JsonFormat extends BinaryFormat("application/json", JsonCodecDeriver)
```

## Built-in Formats

Here's a summary of the formats currently supported by ZIO Blocks. Each format provides a `BinaryFormat` object that can be passed to `derive`:

| Format Object       | Codec Type            | MIME Type             | Module                          |
|---------------------|-----------------------|-----------------------|---------------------------------|
| `JsonFormat`        | `JsonCodec[A]`        | `application/json`    | `zio-blocks-schema`             |
| `AvroFormat`        | `AvroCodec[A]`        | `application/avro`    | `zio-blocks-schema-avro`        |
| `BsonFormat`        | `BsonCodec[A]`        | `application/bson`    | `zio-blocks-schema-bson`        |
| `CsvFormat`         | `CsvCodec[A]`         | `text/csv`            | `zio-blocks-schema-csv`         |
| `MessagePackFormat` | `MessagePackCodec[A]` | `application/msgpack` | `zio-blocks-schema-messagepack` |
| `ThriftFormat`      | `ThriftCodec[A]`      | `application/thrift`  | `zio-blocks-schema-thrift`      |
| `ToonFormat`        | `ToonCodec[A]`        | `text/toon`           | `zio-blocks-schema-toon`        |
| `XmlFormat`         | `XmlCodec[A]`         | `application/xml`     | `zio-blocks-schema-xml`         |
| `YamlFormat`        | `YamlCodec[A]`        | `application/yaml`    | `zio-blocks-schema-yaml`        |

## Defining a Custom Format

To add a new serialization format, define a `BinaryFormat` (or `TextFormat`) singleton with a custom `Deriver`:

```scala

// 1. Define your codec base class
abstract class MyCodec[A] extends BinaryCodec[A]

// 2. Implement a Deriver[MyCodec] (see Type-class Derivation docs)
// val myDeriver: Deriver[MyCodec] = ...

// 3. Create the format singleton
// object MyFormat extends BinaryFormat[MyCodec]("application/x-myformat", myDeriver)
```

For details on implementing a `Deriver`, see [Type-class Derivation](./type-class-derivation.md).

## Codec Derivation System

All serialization formats in ZIO Blocks follow the same pattern: given a `Schema[A]`, you derive a codec by calling `derive` with a format object:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive codec for any format (using TOON as an example)
val codec = Schema[Person].derive(ToonFormat)

// Encode to bytes
val bytes: Array[Byte] = codec.encode(Person("Alice", 30))

// Decode from bytes
val result: Either[SchemaError, Person] = codec.decode(bytes)
```

## JSON Format

JSON format is the most commonly used text-based serialization format. See the dedicated [JSON documentation](json.md) for comprehensive coverage of the `Json` ADT, navigation, and transformation features.

### Installation

JSON support is included in the core schema module:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "<version>"
```

### Basic Usage

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val person = Person("Alice", 30)
val bytes: Array[Byte] = person.toJsonBytes
val decoded: Either[SchemaError, Person] = bytes.fromJson[Person]
```

## Avro Format

Apache Avro is a compact binary format with schema evolution support, commonly used in big data systems like Kafka and Spark.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro" % "<version>"
```

Requires the Apache Avro library (1.12.x).

### Basic Usage

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive Avro codec
val codec = Schema[Person].derive(AvroFormat)

// Encode to Avro binary format
val person = Person("Alice", 30)
val bytes: Array[Byte] = codec.encode(person)

// Decode from Avro binary format
val decoded: Either[SchemaError, Person] = codec.decode(bytes)
```

### Avro Schema Generation

Each `AvroCodec` exposes an `avroSchema` property containing the Apache Avro schema:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(AvroFormat)
val avroSchema: AvroSchema = codec.avroSchema
println(avroSchema.toString(true))
// {
//   "type": "record",
//   "name": "Person",
//   "fields": [
//     {"name": "name", "type": "string"},
//     {"name": "age", "type": "int"}
//   ]
// }
```

### Avro Type Mappings

| Scala Type | Avro Type |
|------------|-----------|
| `Boolean` | `boolean` |
| `Byte`, `Short`, `Int` | `int` |
| `Long` | `long` |
| `Float` | `float` |
| `Double` | `double` |
| `String`, `Char` | `string` |
| `BigInt` | `bytes` |
| `BigDecimal` | Record (mantissa, scale, precision, roundingMode) |
| `UUID` | 16-byte fixed |
| `Currency` | 3-byte fixed |
| `java.time.*` | Records or primitives |
| Case classes | `record` |
| Sealed traits | `union` |
| `List[A]`, `Set[A]` | `array` |
| `Map[String, V]` | `map` |

### ADT Encoding

Sealed traits are encoded as Avro unions with an integer index prefix:

```scala

sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived
}

val codec = Schema[Shape].derive(AvroFormat)

// The variant index (0 for Circle, 1 for Rectangle) is written first,
// followed by the record data
val circle: Shape = Circle(5.0)
val bytes = codec.encode(circle)
```

## TOON Format (LLM-Optimized)

TOON (Token-Oriented Object Notation) is a line-oriented, indentation-based text format that encodes the JSON data model with explicit structure and minimal quoting. It is 30-60% more compact than JSON, making it particularly efficient for LLM prompts and responses.

### Why TOON?

- **Token efficient**: 30-60% fewer tokens than equivalent JSON
- **Human readable**: Clean, YAML-like syntax without YAML's complexity
- **LLM optimized**: Designed for AI/ML use cases where token count matters
- **Explicit lengths**: Arrays declare their size upfront for reliable parsing
- **Cross-platform**: Works on JVM and Scala.js

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon" % "<version>"
```

### Basic Usage

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive TOON codec
val codec = Schema[Person].derive(ToonFormat)

// Encode to TOON
val person = Person("Alice", 30)
val bytes: Array[Byte] = codec.encode(person)
// name: Alice
// age: 30

// Decode from TOON
val decoded: Either[SchemaError, Person] = codec.decode(bytes)
```

### TOON Format Examples

TOON uses indentation and explicit array lengths:

```
# Simple object
name: Alice
age: 30
email: alice@example.com

# Inline primitive arrays (comma-separated)
tags[3]: scala,zio,functional

# Nested object
address:
  street: 123 Main St
  city: Springfield

# Object arrays use list format
orders[2]:
  - id: 1
    total: 99.99
  - id: 2
    total: 149.5

# Or tabular format (more compact)
orders[2]{id,total}:
  1,99.99
  2,149.5
```

### Configuration Options

The `ToonCodecDeriver` provides extensive configuration:

```scala

case class Person(firstName: String, lastName: String)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Custom deriver with snake_case field names
val customDeriver = ToonCodecDeriver
  .withFieldNameMapper(NameMapper.SnakeCase)
  .withArrayFormat(ArrayFormat.Tabular)
  .withDiscriminatorKind(DiscriminatorKind.Field("type"))

val codec = Schema[Person].derive(customDeriver)
// first_name: Alice
// last_name: Smith
```

| Option | Description | Default |
|--------|-------------|---------|
| `withFieldNameMapper` | Transform field names (Identity, SnakeCase, KebabCase) | `Identity` |
| `withCaseNameMapper` | Transform variant/case names | `Identity` |
| `withDiscriminatorKind` | ADT discriminator style (Key, Field, None) | `Key` |
| `withArrayFormat` | Array encoding (Auto, Tabular, Inline, List) | `Auto` |
| `withDelimiter` | Inline array delimiter (Comma, Tab, Pipe) | `Comma` |
| `withRejectExtraFields` | Error on unknown fields during decoding | `false` |
| `withEnumValuesAsStrings` | Encode enum values as strings | `true` |
| `withTransientNone` | Omit None values from output | `true` |
| `withTransientEmptyCollection` | Omit empty collections | `true` |
| `withTransientDefaultValue` | Omit fields with default values | `true` |

### ADT Encoding Styles

```scala

sealed trait Shape
case class Circle(radius: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived
}

// Key discriminator (default)
val keyCodec = Schema[Shape].derive(ToonFormat)
// Circle:
//   radius: 5

// Field discriminator
val fieldDeriver = ToonCodecDeriver
  .withDiscriminatorKind(DiscriminatorKind.Field("type"))
val fieldCodec = Schema[Shape].derive(fieldDeriver)
// type: Circle
// radius: 5
```

## MessagePack Format

MessagePack is an efficient binary serialization format that is more compact than JSON while remaining schema-less and cross-language compatible.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "<version>"
```

### Basic Usage

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive MessagePack codec
val codec = Schema[Person].derive(MessagePackFormat)

// Encode to MessagePack
val person = Person("Alice", 30)
val bytes: Array[Byte] = codec.encode(person)

// Decode from MessagePack
val decoded: Either[SchemaError, Person] = codec.decode(bytes)
```

### Binary Efficiency

MessagePack provides significant space savings compared to JSON:

- Typically 50-80% of JSON size
- Uses variable-width integer encoding
- No string escaping overhead
- No key quoting or colons/commas

### MessagePack Type Mappings

| Scala Type | MessagePack Type |
|------------|------------------|
| `Unit` | nil |
| `Boolean` | bool |
| `Byte`, `Short`, `Int`, `Long` | int (variable width) |
| `Float` | float32 |
| `Double` | float64 |
| `String`, `Char` | str |
| `Array[Byte]` | bin |
| `List[A]`, `Vector[A]`, `Set[A]` | array |
| `Map[K, V]` | map |
| `Option[A]` | array (0 or 1 element) |
| `Either[A, B]` | map with "left" or "right" key |
| Case classes | map with field names as keys |
| Sealed traits | int index followed by value |

### ADT Encoding

Sealed traits encode a variant index followed by the case value:

```scala

sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived
}

val codec = Schema[Shape].derive(MessagePackFormat)

// Circle is encoded as: 0 followed by {radius: 5.0}
val circle: Shape = Circle(5.0)
val bytes = codec.encode(circle)
```

## BSON Format

BSON (Binary JSON) is the binary format used by MongoDB. The ZIO Blocks BSON module provides integration with the MongoDB BSON library.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson" % "<version>"
```

Requires the MongoDB BSON library (5.x).

### Basic Usage

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive BSON encoder/decoder
val encoder: BsonEncoder[Person] = BsonSchemaCodec.bsonEncoder(Schema[Person])
val decoder: BsonDecoder[Person] = BsonSchemaCodec.bsonDecoder(Schema[Person])

// Or get both as a codec
val codec: BsonCodec[Person] = BsonSchemaCodec.bsonCodec(Schema[Person])
```

### MongoDB ObjectId Support

BSON provides native support for MongoDB ObjectIds:

```scala

// Import ObjectId schema

case class Document(_id: ObjectId, title: String)

object Document {
  implicit val schema: Schema[Document] = Schema.derived
}

// ObjectId is encoded using BSON's native OBJECT_ID type
val codec = BsonSchemaCodec.bsonCodec(Schema[Document])
```

### Configuration Options

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Custom configuration
val config = Config
  .withSumTypeHandling(SumTypeHandling.DiscriminatorField("_type"))
  .withIgnoreExtraFields(true)
  .withNativeObjectId(true)

val codec = BsonSchemaCodec.bsonCodec(Schema[Person], config)
```

| Option | Description | Default |
|--------|-------------|---------|
| `withSumTypeHandling` | ADT discrimination strategy | `WrapperWithClassNameField` |
| `withClassNameMapping` | Transform class names | `identity` |
| `withIgnoreExtraFields` | Ignore unknown fields on decode | `true` |
| `withNativeObjectId` | Use native BSON ObjectId type | `false` |

### Sum Type Handling

```scala

// Option 1: Wrapper with class name as field key (default)
SumTypeHandling.WrapperWithClassNameField
// {"Circle": {"radius": 5.0}}

// Option 2: Discriminator field
SumTypeHandling.DiscriminatorField("_type")
// {"_type": "Circle", "radius": 5.0}

// Option 3: No discriminator (tries each case)
SumTypeHandling.NoDiscriminator
```

## Thrift Format

Apache Thrift is a binary protocol format with field ID-based encoding, supporting forward-compatible schema evolution.

### Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-thrift" % "<version>"
```

Requires the Apache Thrift library (0.22.x).

### Basic Usage

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive Thrift codec
val codec = Schema[Person].derive(ThriftFormat)

// Encode to Thrift binary format
val person = Person("Alice", 30)
val bytes: Array[Byte] = codec.encode(person)

// Decode from Thrift binary format
val decoded: Either[SchemaError, Person] = codec.decode(bytes)

// ByteBuffer API
val buffer = ByteBuffer.allocate(1024)
codec.encode(person, buffer)
buffer.flip()
val fromBuffer: Either[SchemaError, Person] = codec.decode(buffer)
```

### Thrift-Specific Features

- **Field ID-based encoding**: Uses 1-based field IDs corresponding to case class field positions
- **Forward compatibility**: Unknown fields are skipped during decoding
- **Out-of-order decoding**: Fields can arrive in any order on the wire
- **TBinaryProtocol**: Uses the standard Thrift binary protocol

### Thrift Type Mappings

| Scala Type | Thrift Type |
|------------|-------------|
| `Unit` | VOID |
| `Boolean` | BOOL |
| `Byte` | BYTE |
| `Short`, `Char` | I16 |
| `Int` | I32 |
| `Long` | I64 |
| `Float`, `Double` | DOUBLE |
| `String` | STRING |
| `BigInt` | Binary (STRING) |
| `BigDecimal` | STRUCT |
| `java.time.*` | STRING (ISO format) or I32 |
| `List[A]` | LIST |
| `Map[K, V]` | MAP |
| Case classes | STRUCT |
| Sealed traits | Indexed variant |

## Supported Types

All formats support the full set of ZIO Blocks Schema primitive types:

**Numeric Types**:
- `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`
- `BigInt`, `BigDecimal`

**Text Types**:
- `String`

**Special Types**:
- `Unit`, `UUID`, `Currency`

**Java Time Types**:
- `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`
- `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`
- `Duration`, `Period`
- `Year`, `YearMonth`, `MonthDay`
- `DayOfWeek`, `Month`
- `ZoneId`, `ZoneOffset`

**Composite Types**:
- Records (case classes)
- Variants (sealed traits)
- Sequences (`List`, `Vector`, `Set`, `Array`, etc.)
- Maps (`Map[K, V]`)
- Options (`Option[A]`)
- Eithers (`Either[A, B]`)
- Wrappers (newtypes)

## Cross-Platform Support

| Format | JVM | Scala.js |
|--------|-----|----------|
| JSON | ✓ | ✓ |
| TOON | ✓ | ✓ |
| MessagePack | ✓ | ✓ |
| Avro | ✓ | ✗ |
| Thrift | ✓ | ✗ |
| BSON | ✓ | ✗ |

## Error Handling

All formats return `Either[SchemaError, A]` for decoding operations. Errors include path information for debugging:

```scala

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(ToonFormat)

// Example: decoding invalid bytes
val invalidBytes = "invalid: data\nwrong: format".getBytes
val result = codec.decode(invalidBytes)

result match {
  case Right(person) => println(s"Decoded: $person")
  case Left(error) => 
    // SchemaError includes information about the decode failure
    error.errors.foreach(e => println(s"Error: ${e.message}"))
}
```
