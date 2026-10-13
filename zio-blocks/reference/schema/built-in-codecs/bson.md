# BSON Codec Module

> `zio-blocks-schema-bson` is a **schema-driven BSON codec module** for serializing and deserializing Scala types to and from BSON (Binary JSON) format. It provides comprehensive encoding and decoding with support for 27 primitive types, records, variants, sequences, maps, and recursive types.

Core types: `BsonCodec`, `BsonEncoder`, `BsonDecoder`, `BsonCodecDeriver`, and the compatibility facade `BsonSchemaCodec`.

The module integrates with org.bson to provide native BSON type support including special handling for `ObjectId`, `Decimal128`, and other BSON-specific types.

## Motivation

BSON is the native wire format for MongoDB and appears widely across modern applications. Manually writing BSON encoders and decoders is error-prone and repetitive, especially for complex types with records, nested structures, and recursive definitions. `zio-blocks-schema-bson` eliminates this friction by deriving codec instances directly from your Scala types using ZIO Schema. You describe your data shape once, and the module handles:
- Full BSON type support (documents, arrays, strings, numbers, ObjectId, Decimal128, timestamps, etc.)
- Configurable sum type handling (discriminator fields, wrapper types, or no discriminator)
- Flexible field name mapping for compatibility with MongoDB conventions
- Native ObjectId support with automatic detection and encoding
- Precise error reporting with location traces showing the path to errors
- Recursive type support with automatic cycle detection
- JVM support (not available for Scala.js)

Rather than writing custom encoders or relying on string-based configuration, you work with strongly-typed schemas that the compiler validates.

## Installation

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson" % "0.0.56"
```

**Note:** This module is JVM-only and is not available for Scala.js.

Supported Scala versions: 2.13.x and 3.x

## Introduction

The module provides a complete pipeline for BSON codec derivation and usage:

1. **Define your type** — Any Scala type with a `Schema` instance
2. **Derive a codec** — Use `schema.derive(BsonCodecDeriver)` to obtain a `BsonCodec[A]`
3. **Encode or decode** — Call `codec.encoder.toBsonValue()` or `codec.decoder.fromBsonValue()`
4. **Handle errors** — Catch `BsonDecoder.Error` with location traces showing where the error occurred

The derivation process is automatic for all supported types (all 27 primitives, records, variants, sequences, maps). The module provides flexible configuration for sum type handling, field mapping, and ObjectId support.

## How They Work Together

The BSON codec pipeline flows through these layers:

```
1. User defines Schema[A] for their type
                 ↓
2. Schema[A].derive(BsonCodecDeriver) creates BsonCodec[A]
                 ↓
3. DerivationBuilder traverses the schema and BsonCodecDeriver composes Encoder and Decoder implementations
   - For primitives: type-specific BSON encoders/decoders
   - For records: field-by-field composition
   - For variants: discriminator-based selection
   - For sequences: array encoding/decoding
   - For maps: document/object encoding/decoding
                 ↓
4. BsonEncoder writes values to BsonValue or BsonWriter
   BsonDecoder reads BsonValue or BsonReader to values
                 ↓
5. BsonCodecDeriver configuration methods customize behavior
   - Sum type handling (discriminator, wrapper, or none)
   - Field name mapping for MongoDB conventions
   - ObjectId detection and native BSON encoding
   - Extra field handling (ignore or error)
                 ↓
6. BsonTrace provides location information in errors
   Shows the path (.field[index].nested) to error location
```

**Typical workflow:**

A user type flows through the derivation and encoding pipeline as follows:

```
User type (e.g., case class Person)
    ↓
Schema.derived (automatic via macro)
    ↓
schema.derive(configuredBsonCodecDeriver) → BsonCodec[Person]
    ↓
Use codec.encoder.toBsonValue(person) to serialize
Use codec.decoder.fromBsonValue(bsonValue) to deserialize
    ↓
Handle BsonDecoder.Error with location trace on failure
```

## Common Patterns

This section shows practical patterns for working with BSON codecs in real-world scenarios.

### Pattern 1: Derive and Encode a Simple Record

To derive and use a BSON codec for a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson._
import org.bson.BsonDocument

case class Person(name: String, age: Int, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(BsonCodecDeriver)
val person = Person("Alice", 30, "alice@example.com")
val bsonValue = codec.encoder.toBsonValue(person)
```

### Pattern 2: Decode BSON with Error Handling

When decoding BSON, errors include location traces showing where the problem occurred.

To decode a BSON document and handle errors with location information:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson._
import org.bson.{BsonDocument, BsonString, BsonInt32}

case class Employee(id: Int, name: String, salary: Double)

object Employee {
  implicit val schema: Schema[Employee] = Schema.derived
}

val codec = BsonSchemaCodec.bsonCodec(Employee.schema)
val doc = new BsonDocument()
doc.put("id", new BsonInt32(123))
doc.put("name", new BsonString("Bob"))
doc.put("salary", new BsonInt32(50000)) // Wrong type - should be number with decimal

val result = codec.decoder.fromBsonValue(doc)

result match {
  case Right(employee) => println(s"Decoded: $employee")
  case Left(error) =>
    println(s"Error at ${error.trace}: ${error.message}")
}
```

### Pattern 3: Configure Sum Type Handling

Sum types (sealed traits with variants) can be encoded in different ways. Configure which approach you prefer.

To configure how variants are encoded in BSON documents:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson._

sealed trait Status
case class Active(since: String) extends Status
case class Inactive(reason: String) extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

val discriminatorConfig = BsonSchemaCodec.Config
  .withSumTypeHandling(BsonSchemaCodec.SumTypeHandling.DiscriminatorField("type"))

val codec = BsonSchemaCodec.bsonCodec(Status.schema, discriminatorConfig)
```

### Pattern 4: Use ObjectId with Native BSON Encoding

ObjectId fields can be encoded using BSON's native ObjectId type for compatibility with MongoDB.

To enable native BSON ObjectId encoding:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson._
import zio.blocks.schema.bson.ObjectIdSupport._
import org.bson.types.ObjectId

case class Document(id: ObjectId, title: String)

object Document {
  implicit val schema: Schema[Document] = Schema.derived
}

val config = BsonSchemaCodec.Config.withNativeObjectId(true)
val codec = BsonSchemaCodec.bsonCodec(Document.schema, config)
```

## BsonCodec[A]

Main codec type for encoding and decoding values to and from BSON format. Contains an encoder and decoder for bidirectional serialization.

### Overview

`BsonCodec[A]` holds both a `BsonEncoder[A]` and `BsonDecoder[A]`, providing a complete solution for serializing and deserializing values. The codec is derived automatically from a `Schema[A]` using `BsonSchemaCodec.bsonCodec()`.

### Access Encoder and Decoder

To access the encoder and decoder from a codec:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson._

case class User(id: Int, name: String)

object User {
  implicit val schema: Schema[User] = Schema.derived
}

val codec = BsonSchemaCodec.bsonCodec(User.schema)
val encoder = codec.encoder
val decoder = codec.decoder
```

### Encoding Values

Use the encoder to convert values to BsonValue:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson._

case class Product(name: String, price: Double)

object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

val codec = BsonSchemaCodec.bsonCodec(Product.schema)
val product = Product("Widget", 9.99)
val bsonValue = codec.encoder.toBsonValue(product)
```

### Decoding Values

Use the decoder to convert BsonValue back to values:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson._
import org.bson.BsonValue

case class Item(name: String, quantity: Int)

object Item {
  implicit val schema: Schema[Item] = Schema.derived
}

val codec = BsonSchemaCodec.bsonCodec(Item.schema)
// Create a BsonValue from an encoded item
val item = Item("Widget", 42)
val bsonValue = codec.encoder.toBsonValue(item)

val result: Either[BsonDecoder.Error, Item] = codec.decoder.fromBsonValue(bsonValue)
```

---

## BsonEncoder[A]

Trait for encoding Scala values to BSON format. Provides methods for writing to BsonWriter or converting to BsonValue directly.

### Overview

`BsonEncoder[A]` is responsible for converting values of type `A` to BSON representation. It supports skipping absent values and provides two encoding paths: direct BsonValue conversion or streaming to a BsonWriter.

### Key Methods

To encode a value to BsonValue:

```scala
import zio.blocks.schema.bson._

trait BsonEncoder[A] {
  def encode(writer: org.bson.BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit
  def toBsonValue(value: A): org.bson.BsonValue
  def isAbsent(value: A): Boolean = false
}
```

### Contramap for Transformations

Transform the input before encoding using contramap:

```scala
import zio.blocks.schema.bson._
import zio.blocks.schema._

case class WrappedInt(value: Int)
object WrappedInt {
  implicit val schema: Schema[WrappedInt] = Schema.derived
}

val codec = BsonSchemaCodec.bsonCodec(WrappedInt.schema)
// The codec provides encoder and decoder for bidirectional serialization
```

---

## BsonDecoder[A]

Trait for decoding BSON values to Scala types. Provides error handling with precise location information.

### Overview

`BsonDecoder[A]` converts BSON values back to Scala types. It provides two decoding paths: from BsonValue or streaming from a BsonReader. Errors include location traces showing the path where decoding failed.

### Key Methods

To decode a BsonValue:

```scala
import zio.blocks.schema.bson._
import org.bson.BsonValue

trait BsonDecoder[A] {
  def decodeUnsafe(reader: org.bson.BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A
  def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A
}
```

### Error Handling

Errors provide location information for debugging:

```scala
import zio.blocks.schema.bson._

case class BsonError(message: String, trace: List[BsonTrace])

val trace = List(BsonTrace.Field("user"), BsonTrace.Array(0), BsonTrace.Field("age"))
val rendered = BsonTrace.render(trace) // ".user[0].age"
```

---

## BsonCodecDeriver and BsonSchemaCodec

`BsonCodecDeriver` implements the shared `Deriver[BsonCodec]` contract. It supports direct derivation as well as `Schema.deriving` instance and modifier overrides by type, exact optic, or parent type and term name.

`BsonSchemaCodec` retains `bsonCodec`, `bsonEncoder`, `bsonDecoder`, and `Config` as compatibility and convenience APIs. These methods delegate to `BsonCodecDeriver`, so both entry points have the same BSON behavior.

### Configuration

The `Config` class controls codec behavior:

```scala
import zio.blocks.schema.bson._

val defaultConfig = BsonSchemaCodec.Config
val customConfig = defaultConfig
  .withSumTypeHandling(BsonSchemaCodec.SumTypeHandling.DiscriminatorField("_type"))
  .withClassNameMapping(_.toLowerCase)
  .withIgnoreExtraFields(false)
  .withNativeObjectId(true)
```

### Sum Type Handling Options

Choose how variants are encoded in BSON documents:

```scala
import zio.blocks.schema.bson._

// Wrapper with class name field (default)
val wrapper = BsonSchemaCodec.SumTypeHandling.WrapperWithClassNameField

// Discriminator field approach
val discriminator = BsonSchemaCodec.SumTypeHandling.DiscriminatorField("type")

// No discriminator - encode variant directly
val none = BsonSchemaCodec.SumTypeHandling.NoDiscriminator
```

### Deriving Codecs

To create a codec from a schema:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(BsonCodecDeriver)
// codec: BsonCodec[Person] = BsonCodec(
//   encoder = zio.blocks.schema.bson.BsonCodecDeriver$$anon$5@484f978e,
//   decoder = zio.blocks.schema.bson.BsonCodecDeriver$$anon$6@2785badf
// )
val configuredCodec = Person.schema.derive(BsonCodecDeriver.withIgnoreExtraFields(false))
// configuredCodec: BsonCodec[Person] = BsonCodec(
//   encoder = zio.blocks.schema.bson.BsonCodecDeriver$$anon$5@454102ab,
//   decoder = zio.blocks.schema.bson.BsonCodecDeriver$$anon$6@71861e6a
// )

// Compatibility facade
val compatibleCodec = BsonSchemaCodec.bsonCodec(Person.schema)
// compatibleCodec: BsonCodec[Person] = BsonCodec(
//   encoder = zio.blocks.schema.bson.BsonCodecDeriver$$anon$5@6580f105,
//   decoder = zio.blocks.schema.bson.BsonCodecDeriver$$anon$6@2899e344
// )
val configuredCompatibleCodec = BsonSchemaCodec.bsonCodec(Person.schema, BsonSchemaCodec.Config)
// configuredCompatibleCodec: BsonCodec[Person] = BsonCodec(
//   encoder = zio.blocks.schema.bson.BsonCodecDeriver$$anon$5@6affb8b8,
//   decoder = zio.blocks.schema.bson.BsonCodecDeriver$$anon$6@815743b
// )
```

### Derivation overrides

Use `Schema.deriving` when part of a schema needs a custom codec or runtime modifier:

```scala
val codec = Person.schema
  .deriving(BsonCodecDeriver)
  .instance(Person.schema.reflect.typeId, "age", customIntCodec)
  .modifier(Person.schema.reflect.typeId, "name", Modifier.rename("fullName"))
  .derive
```

Type-level overrides affect every matching occurrence. Parent-type and term-name overloads are also available for fields and variant cases. String map keys remain BSON document field names; override map values or the whole map codec when custom map behavior is needed.

Record-level `Modifier.fieldNaming` and `Modifier.noExtraFields` annotations override the corresponding default BSON behavior. Variant-level `Modifier.caseNaming` and `Modifier.discriminator` annotations override class-name mapping and sum-type handling for the annotated variant.

### Semantic Json values

`zio.blocks.schema.json.Json` objects, arrays, scalars, and nulls map directly to their semantic BSON equivalents. Finite BSON doubles other than signed zero retain their exact value when converted through `Json`; BSON signed zero is normalized to JSON zero because `Json.Number` does not retain its sign. Duplicate `Json.Object` field names are rejected because `BsonDocument` cannot represent them without data loss.

---

## BsonTrace

Error location information for BSON decoding errors. Shows the path to the error in the document.

### Overview

`BsonTrace` elements build up a path through nested documents and arrays. When an error occurs, the complete trace is rendered as a path like `.field[0].nested.value`.

### Trace Elements

The two types of trace elements:

```scala
import zio.blocks.schema.bson._

sealed trait BsonTrace

case class Field(name: String) extends BsonTrace  // Document field
case class Array(idx: Int) extends BsonTrace      // Array index
```

### Rendering Traces

Convert a trace list to a human-readable path:

```scala
import zio.blocks.schema.bson._

val trace = List(
  BsonTrace.Field("user"),
  BsonTrace.Array(0),
  BsonTrace.Field("email")
)

val path = BsonTrace.render(trace) // ".user[0].email"
```

---

## ObjectIdSupport

Special support for `org.bson.types.ObjectId` with automatic detection for native BSON ObjectId encoding.

### Overview

`ObjectIdSupport` provides a Schema instance for ObjectId that enables native BSON ObjectId type encoding (12-byte format) when detected by BsonSchemaCodec.

### Using ObjectId

To use ObjectId in your schema, import ObjectIdSupport:

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson.ObjectIdSupport._
import org.bson.types.ObjectId

case class MongoDocument(id: ObjectId, title: String)

object MongoDocument {
  implicit val schema: Schema[MongoDocument] = Schema.derived
}

// ObjectId will automatically use native BSON ObjectId encoding
```

### ObjectId and Configuration

When ObjectIdSupport is imported, ObjectId automatically uses native BSON encoding regardless of the `useNativeObjectId` configuration setting.
