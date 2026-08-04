# MessagePack Codec Module

> `zio-blocks-schema-messagepack` is a **schema-driven MessagePack codec module** for serializing and deserializing Scala types to and from MessagePack binary format. It provides comprehensive encoding and decoding with support for 27 primitive types, records, variants, sequences, maps, and recursive types. Core types: `MessagePackCodec`, `MessagePackCodecDeriver`, `MessagePackFormat`.

`zio-blocks-schema-messagepack` is a **schema-driven MessagePack codec module** for serializing and deserializing Scala types to and from MessagePack binary format. It provides comprehensive encoding and decoding with support for 27 primitive types, records, variants, sequences, maps, and recursive types. Core types: `MessagePackCodec`, `MessagePackCodecDeriver`, `MessagePackFormat`.

The module integrates with MessagePack specification to provide compact binary serialization with automatic schema generation and optimized reader/writer pools for high-performance streaming.

## Motivation

MessagePack is a compact binary serialization format that achieves smaller payload sizes than JSON while maintaining compatibility and flexibility. It appears widely across distributed systems, real-time streaming, and space-constrained environments. Manually writing MessagePack encoders and decoders is error-prone and repetitive, especially for complex types with records, nested structures, and recursive definitions. `zio-blocks-schema-messagepack` eliminates this friction by deriving codec instances directly from your Scala types using ZIO Schema. You describe your data shape once, and the module handles:
- Full MessagePack type support (all fixint, fixarray, fixmap, ext types, strings, numbers)
- Automatic schema generation from Scala types
- Highly optimized encoding with minimal overhead
- Reader/writer pool management for efficient coding
- Precise error reporting with location traces showing the path to errors
- Recursive type support with automatic cycle detection
- Multiple encoding paths: byte arrays and ByteBuffer
- Multiple decoding paths: byte arrays and ByteBuffer
- Cross-platform compatibility (JVM and Scala.js)

Rather than writing custom encoders or relying on string-based schema configuration, you work with strongly-typed schemas that the compiler validates.

## Installation

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "0.0.51"
```

For Scala.js, use `%%%` instead of `%%`:

```sbt
libraryDependencies += "dev.zio" %%% "zio-blocks-schema-messagepack" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x

## Introduction

The module provides a complete pipeline for MessagePack codec derivation and usage:

1. **Define your type** — Any Scala type with a `Schema` instance
2. **Derive a codec** — Use `Schema.derive(MessagePackFormat)` to obtain a `MessagePackCodec[A]`
3. **Encode or decode** — Call `codec.encode(value)` or `codec.decode(bytes)`
4. **Handle errors** — Catch `SchemaError` with location traces showing where the error occurred

The derivation process is automatic for all supported types (all 27 primitives, records, variants, sequences, maps). The module automatically generates MessagePack-compatible formats and handles encoding/decoding without manual configuration.

## How They Work Together

The MessagePack codec pipeline flows through these layers:

```
1. User defines Schema[A] for their type
                 ↓
2. Schema[A].derive(MessagePackFormat) creates MessagePackCodec[A]
                 ↓
3. MessagePackCodecDeriver derives Encoder and Decoder implementations
   - For primitives: type-specific MessagePack encoders/decoders
   - For records: field-by-field composition with map encoding
   - For variants: tagged union encoding
   - For sequences: array encoding/decoding
   - For maps: map encoding/decoding
                 ↓
4. MessagePackCodec provides multiple encoding paths
   - encode(value) → Array[Byte]
   - encode(value, buffer: ByteBuffer) → Unit
                 ↓
5. MessagePackCodec provides multiple decoding paths
   - decode(bytes: Array[Byte]) → Either[SchemaError, A]
   - decode(buffer: ByteBuffer) → Either[SchemaError, A]
                 ↓
6. MessagePackReader/Writer manage I/O with pooling
   - Reader pool: reuses instances for streaming decoding
   - Writer pool: reuses instances for streaming encoding
   - Automatic format detection and type-specific optimization
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
Schema[Person].derive(MessagePackFormat) → MessagePackCodec[Person]
    ↓
Use codec.encode(person) to serialize → Array[Byte]
Use codec.decode(bytes) to deserialize → Either[SchemaError, Person]
    ↓
Handle SchemaError with location trace on failure
```

### Type Relationships

- **`MessagePackCodec[A]`** — Main public API; contains encoder and decoder for bidirectional serialization
- **`MessagePackCodecDeriver`** — Configuration and derivation system; generates codecs from Schema
- **`MessagePackFormat`** — Integration with ZIO Schema format system; enables `Schema[A].derive(MessagePackFormat)`
- **`MessagePackReader`** — Low-level binary parsing; stateful format-aware decoder
- **`MessagePackWriter`** — Low-level binary encoding; optimized format-aware encoder
- **`SchemaError`** — Error type with location traces; renders as paths like `.field[0].nested`

## Common Patterns

This section shows practical patterns for working with MessagePack codecs in real-world scenarios.

### Pattern 1: Derive and Encode a Simple Record

To derive and use a MessagePack codec for a record type:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Person(name: String, age: Int, email: String)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(MessagePackFormat)
val person = Person("Alice", 30, "alice@example.com")
val bytes = codec.encode(person)
```

### Pattern 2: Decode MessagePack with Error Handling

When decoding MessagePack data, errors include location traces showing where the problem occurred.

To decode bytes and handle errors with location information:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Employee(id: Int, name: String, salary: Double)

object Employee {
  implicit val schema: Schema[Employee] = Schema.derived
}

val codec = Employee.schema.derive(MessagePackFormat)
val bytes = Array[Byte](1, 2, 3) // truncated data

val result = codec.decode(bytes)

result match {
  case Right(employee) => println(s"Decoded: $employee")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
}
```

### Pattern 3: Stream Large Datasets with Reader Pool

The reader pool efficiently handles streaming decoding of multiple messages from a large dataset.

To decode a sequence of values from a stream with pooled readers:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Event(id: Long, timestamp: Long, action: String)

object Event {
  implicit val schema: Schema[Event] = Schema.derived
}

val codec = Event.schema.derive(MessagePackFormat)
// Encode an event to get sample bytes
val event1 = Event(1L, System.currentTimeMillis(), "click")
val bytes1 = codec.encode(event1)

// Decode the bytes back to an event
val result = codec.decode(bytes1)
result match {
  case Right(decoded) => println(s"Decoded: $decoded")
  case Left(error) => println(s"Error: ${error.getMessage}")
}
```

### Pattern 4: Handle Recursive Types

Recursive types (types that reference themselves) are fully supported with automatic cycle detection.

To define and encode a recursive data structure:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

sealed trait Node
case class Leaf(value: String) extends Node
case class Branch(left: Node, right: Node) extends Node

object Node {
  implicit val schema: Schema[Node] = Schema.derived
}

val codec = Node.schema.derive(MessagePackFormat)
val tree: Node = Branch(Leaf("A"), Branch(Leaf("B"), Leaf("C")))
val bytes = codec.encode(tree)
```

---

## MessagePackCodec[A]

Main codec type for encoding and decoding values to and from MessagePack binary format. Contains encoder and decoder for bidirectional serialization.

### Overview

`MessagePackCodec[A]` holds both an encoder and decoder, providing a complete solution for serializing and deserializing values in MessagePack binary format. The codec is derived automatically from a `Schema[A]` using `MessagePackFormat`.

### Encoding Values to Byte Array

Use the codec to convert values to byte arrays:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Product(name: String, price: Double)

object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

val codec = Product.schema.derive(MessagePackFormat)
val product = Product("Widget", 9.99)
val bytes = codec.encode(product)
```

### Encoding Values to ByteBuffer

Write encoded values directly to a ByteBuffer:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._
import java.nio.ByteBuffer

case class Item(name: String, quantity: Int)

object Item {
  implicit val schema: Schema[Item] = Schema.derived
}

val codec = Item.schema.derive(MessagePackFormat)
val item = Item("Gadget", 42)
val buffer = ByteBuffer.allocate(256)
codec.encode(item, buffer)
val bytes = java.util.Arrays.copyOf(buffer.array(), buffer.position())
```

### Decoding Values from Byte Array

Use the codec to convert byte arrays back to values:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Record(id: Int, value: String)

object Record {
  implicit val schema: Schema[Record] = Schema.derived
}

val codec = Record.schema.derive(MessagePackFormat)
// In real usage, you would have encoded bytes from a previous encoding or external source
val record = Record(123, "test value")
val buffer = java.nio.ByteBuffer.allocate(256)
codec.encode(record, buffer)
buffer.flip()
val bytes = new Array[Byte](buffer.remaining())
buffer.get(bytes)

val result: Either[zio.blocks.schema.SchemaError, Record] = codec.decode(bytes)
```

### Decoding Values from ByteBuffer

Read and decode values from a ByteBuffer:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._
import java.nio.ByteBuffer

case class Data(timestamp: Long, payload: String)

object Data {
  implicit val schema: Schema[Data] = Schema.derived
}

val codec = Data.schema.derive(MessagePackFormat)
// Encode a value first
val sampleData = Data(System.currentTimeMillis(), "example")
val encBuffer = ByteBuffer.allocate(256)
codec.encode(sampleData, encBuffer)
encBuffer.flip()
// Then decode from the buffer
val result = codec.decode(encBuffer)
```

---

## MessagePackCodecDeriver

Configuration and derivation system for creating `MessagePackCodec[A]` instances from `Schema[A]`.

### Overview

`MessagePackCodecDeriver` implements the schema-driven derivation of MessagePack codecs. It automatically handles 27 primitive types and complex types (records, variants, sequences, maps), generating appropriate MessagePack encoders and decoders.

### How Derivation Works

To create a codec from a schema:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Person.schema.derive(MessagePackFormat)
// codec: MessagePackCodec[Person] = zio.blocks.schema.msgpack.MessagePackCodecDeriver$$anon$3@7752c755
```

### Primitive Type Support

All 27 ZIO Schema primitives are supported:
- Numeric: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `BigInt`, `BigDecimal`
- Logical: `Boolean`, `Char`, `String`
- Temporal: `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `Month`, `DayOfWeek`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `ZoneId`, `ZoneOffset`
- Special: `UUID`, `Currency`, `Unit`

### Record Type Support

Case classes (records) are fully supported. Each field becomes a named key in the MessagePack map encoding:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Address(street: String, city: String, zip: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

val codec = Address.schema.derive(MessagePackFormat)
```

### Variant Type Support

Sealed traits and sum types are encoded as MessagePack maps with discriminator fields:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

sealed trait Status
case class Active(since: String) extends Status
case class Inactive(reason: String) extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

val codec = Status.schema.derive(MessagePackFormat)
```

---

## MessagePackFormat

Integration point with ZIO Schema's format system. Provides `BinaryFormat[MessagePackCodec]` to enable `Schema[A].derive(MessagePackFormat)` for any supported type.

### Using MessagePackFormat

To derive a MessagePack codec using the standard format:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Sensor(id: Long, temperature: Double, humidity: Float)

object Sensor {
  implicit val schema: Schema[Sensor] = Schema.derived
}

val codec = Sensor.schema.derive(MessagePackFormat)
```

`MessagePackFormat` is a singleton object extending `BinaryFormat[MessagePackCodec]` with the MIME type `"application/msgpack"` and the `MessagePackCodecDeriver` as its derivation strategy.

---

## MessagePackReader

Low-level binary parser implementing MessagePack format with type-specific optimizations and pooling support.

### Overview

`MessagePackReader` provides stateful reading of MessagePack-encoded data with automatic format detection and efficient handling of all MessagePack types (fixint, fixarray, fixmap, ext, string, binary, float, etc.).

### Reading Primitives

To read individual values from a MessagePack-encoded byte array:

```scala
import zio.blocks.schema.msgpack._
import zio.blocks.schema._

val bytes = Array[Byte](42) // MessagePack-encoded integer

// Use the codec API for public access to decoding
case class Value(data: Int)
object Value {
  implicit val schema: Schema[Value] = Schema.derived
}
val codec = Value.schema.derive(MessagePackFormat)
val result = codec.decode(bytes)
```

---

## MessagePackWriter

Low-level binary encoder implementing MessagePack format with optimizations for compact representation.

### Overview

`MessagePackWriter` provides efficient writing of Scala values into MessagePack binary format with automatic selection of compact encodings (fixint, fixarray, fixmap, etc.).

### Writing Primitives

To write individual values to MessagePack format:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._
import java.io.ByteArrayOutputStream

// Use the codec API for public access to encoding
case class Value(data: Int)
object Value {
  implicit val schema: Schema[Value] = Schema.derived
}
val codec = Value.schema.derive(MessagePackFormat)
val value = Value(42)

// MessagePackCodec.encode returns Array[Byte] directly
val bytes = codec.encode(value)
```

---

## Error Handling

MessagePack decoding errors include location traces showing the path through nested structures where the error occurred.

### Understanding Error Traces

Errors render as paths like `.field[0].nested.value` showing exactly where decoding failed:

```scala
import zio.blocks.schema._
import zio.blocks.schema.msgpack._

case class Contact(emails: Seq[String])

object Contact {
  implicit val schema: Schema[Contact] = Schema.derived
}

val codec = Contact.schema.derive(MessagePackFormat)
// Example invalid bytes - in real code, this might come from untrusted input
val invalidBytes: Array[Byte] = Array(0xFF.toByte, 0xFF.toByte) // Invalid MessagePack data

val result = codec.decode(invalidBytes)

result match {
  case Right(contact) => println(s"Success: $contact")
  case Left(error) =>
    println(s"Error: ${error.getMessage}")
}
```

### Zero-Overhead Error Handling

Errors use zero-overhead exceptions (no stack traces) for efficient error reporting in streaming scenarios where errors are expected and handled inline.
