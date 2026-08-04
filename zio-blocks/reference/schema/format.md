# Format Type

> A `Format` is an abstraction that bundles together everything needed to serialize and deserialize data in a specific format (JSON, Avro, MessagePack, etc.). It unifies metadata related to serialization formats, such as MIME type and codec deriver, in a single place.

A `Format` is an abstraction that bundles together everything needed to serialize and deserialize data in a specific format (JSON, Avro, MessagePack, etc.). It unifies metadata related to serialization formats, such as MIME type and codec deriver, in a single place.

## Overview

The `Format` trait defines the structure for any serialization format. Each format specifies the types for decoding input, encoding output, the codec typeclass, MIME type, and the deriver used to generate codecs from schemas:

```scala
import zio.blocks.schema.codec.Codec
import zio.blocks.schema.derive.Deriver

trait Format {
  type DecodeInput
  type EncodeOutput
  type TypeClass[A] <: Codec[DecodeInput, EncodeOutput, A]
  def mimeType: String
  def deriver: Deriver[TypeClass]
}
```

This design allows for a consistent API across different formats when deriving codecs from schemas. Having MIME type information helps with runtime content negotiation and format routing, for example in HTTP servers or message queues.

## Using a Format

You can easily call [`Schema[A].derive(format)`](./type-class-derivation.md#using-the-deriver-to-derive-type-class-instances) for any format that implements the `Format` trait to obtain a codec that can encode and decode values of type `A` according to that format's rules:

```scala
import zio.blocks.schema._
import zio.blocks.schema.toon._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(ToonFormat)
val bytes: Array[Byte] = codec.encode(Person("Alice", 30))
val decoded: Either[SchemaError, Person] = codec.decode(bytes)
```

## Format Categories

Formats are categorized into `BinaryFormat` and `TextFormat`, which specify the types of input and output for encoding and decoding:

```scala
sealed trait Format
abstract class BinaryFormat[A] extends Format { }
abstract class TextFormat[A] extends Format { }
```

**BinaryFormat** — Encodes and decodes data as bytes. Used for compact, efficient serialization (e.g., Avro, Thrift, MessagePack).

**TextFormat** — Encodes and decodes data as text strings. Used for human-readable formats (e.g., JSON, YAML, TOON).

### Example: JsonFormat

The `JsonFormat` is a `BinaryFormat` that represents JSON serialization, where both the input for decoding and output for encoding are `ByteBuffer`, the MIME type is `application/json`, and the deriver for generating codecs from schemas is `JsonCodecDeriver`:

```scala
import zio.blocks.schema.json._

object MyJsonFormat extends zio.blocks.schema.codec.BinaryFormat("application/json", JsonCodecDeriver)
```

## Defining a Custom Format

To add a new serialization format, define a `BinaryFormat` (or `TextFormat`) singleton with a custom `Deriver`:

```scala
import zio.blocks.schema.codec.BinaryCodec

// 1. Define your codec base class
abstract class MyCodec[A] extends BinaryCodec[A]

// 2. Implement a Deriver[MyCodec] (see Type-class Derivation docs)
// val myDeriver: Deriver[MyCodec] = ...

// 3. Create the format singleton
// object MyFormat extends BinaryFormat[MyCodec]("application/x-myformat", myDeriver)
```

For details on implementing a `Deriver`, see [Type-class Derivation](./type-class-derivation.md).

## Available Formats

ZIO Blocks provides multiple built-in formats. See the [Built-in Formats and Codecs](./built-in-codecs/index.md) section for a complete list of supported serialization formats and their respective codec modules.
