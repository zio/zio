# Apache Thrift Codecs

> Apache Thrift is an open-source framework that allows seamless communication and data sharing between different programming languages and platforms. The `zio-schema-thrift` module derives Apache Thrift codecs from a ZIO Schema. The codecs write Thrift's binary protocol (`TBinaryProtocol`) using the Apache Thrift Java library.

## Introduction

## Installation

To derive Apache Thrift codecs from a ZIO Schema, add the following dependency to your `build.sbt` file. The module is published for the JVM only:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-thrift" % "1.9.2"
```

## BinaryCodec

The `ThriftCodec` object inside the `zio.schema.codec` package provides `ThriftCodec.thriftCodec`, which allows us to derive Thrift codecs from a ZIO Schema:

```scala
object ThriftCodec {
  implicit def thriftCodec[A](implicit schema: Schema[A]): BinaryCodec[A]
}
```

## Example

The following program encodes a `Person` to Thrift, prints the bytes in hexadecimal, and decodes them back:

```scala
import zio._
import zio.schema.codec._
import zio.schema.{DeriveSchema, Schema}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] =
    DeriveSchema.gen

  implicit val thriftCodec: BinaryCodec[Person] =
    ThriftCodec.thriftCodec(schema)
}

object Main extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("Apache Thrift Codec Example:")
    person: Person = Person("John", 42)
    encoded: Chunk[Byte] = Person.thriftCodec.encode(person)
    _ <- ZIO.debug(s"person object encoded to Thrift's binary format: ${toHex(encoded)}")
    decoded <- ZIO.fromEither(Person.thriftCodec.decode(encoded))
    _ <- ZIO.debug(s"Thrift object decoded to Person class: $decoded")
  } yield ()

  def toHex(bytes: Chunk[Byte]): String =
    bytes.map("%02x".format(_)).mkString(" ")
}
```

Here is the output of running the above program:

```
Apache Thrift Codec Example:
person object encoded to Thrift's binary format: 0b 00 01 00 00 00 04 4a 6f 68 6e 08 00 02 00 00 00 2a 00
Thrift object decoded to Person class: Person(John,42)
```

In the output, `0b 00 01` starts field 1 (`name`, a string), `08 00 02` starts field 2 (`age`, a 32-bit integer), and the final `00` ends the struct.

## Schema Evolution

Thrift identifies fields by their field id. The codec numbers the fields of a case class by position, starting at 1, and it does not read the `@fieldNumber` annotation of the Protobuf module, so the position of a field is its id. Cases of a sealed trait are numbered the same way. Adding a field at the end keeps old data readable only when the new field has a default value or is an `Option` or a sequence. The change works in one direction only: the decoder fails on a field id higher than the number of fields in the case class, so a program with the old case class cannot read data written with the new one. Reordering or removing fields changes the meaning of existing data.

When a field is missing from the input, the decoder uses `None` for `Option` fields, an empty sequence for sequence fields such as `List`, and the default value for fields that have a Scala default parameter or `@fieldDefaultValue`. For a field marked `@optionalField`, it uses the default value of the field's type, such as `0` for an `Int`. Any other missing field makes decoding fail.

A field marked with `@transientField` is not written, and the decoder fills it from its default value.

:::caution
Put transient fields after all other fields. The encoder numbers the remaining fields as if the transient field did not exist, while the decoder counts it, so a `@transientField` placed before another field makes decoding fail.
:::

## Streaming

:::caution
The `BinaryCodec#streamEncoder` of the Thrift codec emits cumulative output: the bytes it emits for element `n` contain the encodings of elements 1 to `n`. The stream decoder also decodes each upstream chunk as one value. To stream Thrift values, encode and decode each element with `BinaryCodec#encode` and `BinaryCodec#decode` instead of the stream pipelines.
:::

The [How to Encode and Decode Streams](../../integration-with-zio-streams.md) guide shows how to send one value per message, and how to frame many values in one byte stream; both patterns work for Thrift.
