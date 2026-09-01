# Avro Codec Module

> `zio-blocks-schema-avro` is a **schema-driven Avro codec module** for serializing and deserializing Scala types to and from Avro binary format. It provides comprehensive encoding and decoding with support for 27 primitive types, records, variants, sequences, maps, and recursive types. Core types: `AvroCodec`, `AvroCodecDeriver`, `AvroFormat`.

The module integrates with Apache Avro to provide native binary serialization with automatic schema generation and support for recursive data structures with cycle detection.

## Motivation

Avro is a powerful serialization format prevalent in distributed systems, messaging platforms, and data pipelines. Manually writing Avro encoders and decoders is error-prone and repetitive, especially for complex types with records, nested structures, and recursive definitions. `zio-blocks-schema-avro` eliminates this friction by deriving codec instances directly from your Scala types using ZIO Schema. You describe your data shape once, and the module handles:
- Full Avro type support (records, unions, arrays, maps, nested structures)
- Automatic Avro schema generation from Scala types
- Configurable sum type handling (union fields with discriminators)
- Precise error reporting with location traces showing the path to errors
- Recursive type support with automatic cycle detection
- Multiple encoding paths: ByteBuffer, byte arrays, and streams
- Multiple decoding paths: ByteBuffer, byte arrays, and streams
- JVM support (not available for Scala.js)

Rather than writing custom encoders or relying on string-based Avro schema configuration, you work with strongly-typed schemas that the compiler validates.

## Installation

Add the module to your `build.sbt` (JVM-only, not available for Scala.js):

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x

## Introduction

The module provides a complete pipeline for Avro codec derivation and usage:

1. **Define your type** — Any Scala type with a `Schema` instance
2. **Derive a codec** — Use `Schema.derive(AvroFormat)` to obtain an `AvroCodec[A]`
3. **Encode or decode** — Call `codec.encode(value)` or `codec.decode(bytes)`
4. **Handle errors** — Catch `SchemaError` with location traces showing where the error occurred

The derivation process is automatic for all supported types (all 27 primitives, records, variants, sequences, maps). The module automatically generates Avro schemas and handles encoding/decoding without manual configuration.

## How They Work Together

The Avro codec pipeline flows through these layers:

```
1. User defines Schema[A] for their type
                 ↓
2. Schema[A].derive(AvroFormat) creates AvroCodec[A]
                 ↓
3. AvroCodecDeriver derives Encoder and Decoder implementations
   - For primitives: type-specific Avro encoders/decoders
   - For records: field-by-field composition with Avro record schema
   - For variants: union type encoding with discriminator support
   - For sequences: array encoding/decoding
   - For maps: map encoding/decoding
                 ↓
4. AvroCodec provides multiple encoding paths
   - encode(value) → Array[Byte]
   - encode(value, output: OutputStream) → Unit
   - encode(value, buffer: ByteBuffer) → Unit
                 ↓
5. AvroCodec provides multiple decoding paths
   - decode(bytes: Array[Byte]) → Either[SchemaError, A]
   - decode(input: InputStream) → Either[SchemaError, A]
   - decode(buffer: ByteBuffer) → Either[SchemaError, A]
                 ↓
6. AvroCodec.avroSchema exposes generated Avro schema
   Useful for compatibility checks and schema documentation
                 ↓
7. Errors include location traces
   Shows path (.field[index].nested) to error location
```

**Typical workflow:**

A user type flows through the derivation and encoding pipeline as follows:

```
User type (e.g., case class Person)
    ↓
Schema.derived (automatic via macro)
    ↓
Schema[Person].derive(AvroFormat) → AvroCodec[Person]
    ↓
Use codec.encode(person) to serialize → Array[Byte]
Use codec.decode(bytes) to deserialize → Either[SchemaError, Person]
    ↓
Handle SchemaError with location trace on failure
```

### Type Relationships

- **`AvroCodec[A]`** — Main public API; contains encoder and decoder for bidirectional serialization
- **`AvroCodecDeriver`** — Configuration and derivation system; generates codecs from Schema
- **`AvroFormat`** — Integration with ZIO Schema format system; enables `Schema[A].derive(AvroFormat)`
- **`SchemaError`** — Error type with location traces; renders as paths like `.field[0].nested`

## Common Patterns

This section shows practical patterns for working with Avro codecs in real-world scenarios.

### Pattern 1: Derive and Encode a Simple Record

To derive and use an Avro codec for a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class Person(name: String, age: Int, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(AvroFormat)
val person = Person("Alice", 30, "alice@example.com")
val bytes = codec.encode(person)
```

### Pattern 2: Decode Avro with Error Handling

When decoding Avro data, errors include location traces showing where the problem occurred.

To decode bytes and handle errors with location information:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class Employee(id: Int, name: String, salary: Double)

object Employee {
  implicit val schema: Schema[Employee] = Schema.derived
}

val codec = Employee.schema.derive(AvroFormat)
val bytes = Array[Byte](1, 4, 6) // truncated data

val result = codec.decode(bytes)

result match {
  case Right(employee) => println(s"Decoded: $employee")
  case Left(error) =>
    println(s"Error at ${error.getMessage}")
}
```

### Pattern 3: Inspect the Generated Avro Schema

Access the derived Avro schema to verify compatibility or document the serialization format.

To inspect the Avro schema for a type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class Product(name: String, price: Double, inStock: Boolean)

object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

val codec = Product.schema.derive(AvroFormat)
val avroSchema = codec.avroSchema
println(avroSchema.toString)
```

### Pattern 4: Handle Recursive Types

Recursive types (types that reference themselves) are fully supported with automatic cycle detection.

To define and encode a recursive data structure:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

sealed trait Tree
case class Leaf(value: Int) extends Tree
case class Branch(left: Tree, right: Tree) extends Tree

object Tree {
  implicit val schema: Schema[Tree] = Schema.derived
}

val codec = Tree.schema.derive(AvroFormat)
val tree: Tree = Branch(Leaf(1), Branch(Leaf(2), Leaf(3)))
val bytes = codec.encode(tree)
```

---

## AvroCodec[A]

Main codec type for encoding and decoding values to and from Avro binary format. Contains encoder and decoder for bidirectional serialization.

### Overview

`AvroCodec[A]` holds both an encoder and decoder, providing a complete solution for serializing and deserializing values in Avro binary format. The codec is derived automatically from a `Schema[A]` using `AvroFormat`.

### Accessing the Avro Schema

To get the derived Avro schema from a codec:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class User(id: Int, name: String)

object User {
  implicit val schema: Schema[User] = Schema.derived
}

val codec = User.schema.derive(AvroFormat)
val avroSchema = codec.avroSchema
```

### Encoding Values to Byte Array

Use the codec to convert values to byte arrays:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class Product(name: String, price: Double)

object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

val codec = Product.schema.derive(AvroFormat)
val product = Product("Widget", 9.99)
val bytes = codec.encode(product)
```

### Encoding Values to OutputStream

Write encoded values directly to an output stream:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._
import java.io.ByteArrayOutputStream

case class Item(name: String, quantity: Int)

object Item {
  implicit val schema: Schema[Item] = Schema.derived
}

val codec = Item.schema.derive(AvroFormat)
val item = Item("Gadget", 42)
val output = new ByteArrayOutputStream()
codec.encode(item, output)
val bytes = output.toByteArray
```

### Decoding Values from Byte Array

Use the codec to convert byte arrays back to values:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class Record(id: Int, value: String)

object Record {
  implicit val schema: Schema[Record] = Schema.derived
}

val codec = Record.schema.derive(AvroFormat)
// In real usage, bytes would come from a previous encoding or external source
val record = Record(123, "test")
val buffer = java.nio.ByteBuffer.allocate(256)
codec.encode(record, buffer)
buffer.flip()
val bytes = new Array[Byte](buffer.remaining())
buffer.get(bytes)

val result: Either[zio.blocks.schema.SchemaError, Record] = codec.decode(bytes)
```

### Decoding Values from InputStream

Read and decode values from an input stream:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._
import java.io.ByteArrayInputStream

case class Data(timestamp: Long, payload: String)

object Data {
  implicit val schema: Schema[Data] = Schema.derived
}

val codec = Data.schema.derive(AvroFormat)
// Use encoded bytes from a previous encoding
val data = Data(System.currentTimeMillis(), "example payload")
val buffer = java.nio.ByteBuffer.allocate(256)
codec.encode(data, buffer)
buffer.flip()
val bytes = new Array[Byte](buffer.remaining())
buffer.get(bytes)
val input = new ByteArrayInputStream(bytes)
val result = codec.decode(input)
```

---

## AvroCodecDeriver

Configuration and derivation system for creating `AvroCodec[A]` instances from `Schema[A]`.

### Overview

`AvroCodecDeriver` implements the schema-driven derivation of Avro codecs. It automatically handles 27 primitive types and complex types (records, variants, sequences, maps), generating appropriate Avro schemas and encoder/decoder implementations.

### How Derivation Works

To create a codec from a schema:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(AvroFormat)
// codec: AvroCodec[Person] = zio.blocks.schema.avro.AvroCodecDeriver$$anon$4@3d8dcca9
```

### Primitive Type Support

All 27 ZIO Schema primitives are supported:
- Numeric: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`
- Logical: `Boolean`, `Char`, `String`
- Temporal: `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `Month`, `DayOfWeek`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `ZoneId`, `ZoneOffset`
- Special: `UUID`, `Currency`, `Unit`

### Record Type Support

Case classes (records) are fully supported. Each field becomes a named field in the Avro record schema:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class Address(street: String, city: String, zip: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

val codec = Address.schema.derive(AvroFormat)
```

### Variant Type Support

Sealed traits and sum types are encoded as Avro union types:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

sealed trait Status
case class Active(since: String) extends Status
case class Inactive(reason: String) extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

val codec = Status.schema.derive(AvroFormat)
```

---

## AvroFormat

Integration point with ZIO Schema's format system. Provides `BinaryFormat[AvroCodec]` to enable `Schema[A].derive(AvroFormat)` for any supported type.

### Using AvroFormat

To derive an Avro codec using the standard format:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class Sensor(id: Long, temperature: Double, humidity: Float)

object Sensor {
  implicit val schema: Schema[Sensor] = Schema.derived
}

val codec = Sensor.schema.derive(AvroFormat)
```

`AvroFormat` is a singleton object extending `BinaryFormat[AvroCodec]` with the MIME type `"application/avro"` and the `AvroCodecDeriver` as its derivation strategy.

---

## Error Handling

Avro decoding errors include location traces showing the path through nested structures where the error occurred.

### Understanding Error Traces

Errors render as paths like `.field[0].nested.value` showing exactly where decoding failed:

```scala
import zio.blocks.schema._
import zio.blocks.schema.avro._

case class Contact(emails: Seq[String])

object Contact {
  implicit val schema: Schema[Contact] = Schema.derived
}

val codec = Contact.schema.derive(AvroFormat)
// Example invalid Avro bytes that will fail decoding
val invalidBytes: Array[Byte] = Array(0xFF.toByte, 0xFF.toByte)

val result = codec.decode(invalidBytes)

result match {
  case Right(contact) => println(s"Success: $contact")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
}
```

### Zero-Overhead Error Handling

Errors use zero-overhead exceptions (no stack traces) for efficient error reporting in stream processing scenarios where errors are expected and handled inline.
