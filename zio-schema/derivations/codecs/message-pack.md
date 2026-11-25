# MessagePack Codecs

> MessagePack is a binary serialization format designed for efficient data exchange between different systems and languages. The `zio-schema-msg-pack` module derives MessagePack codecs from a ZIO Schema, so we can serialize and deserialize data in MessagePack format without writing the encoding by hand.

## Introduction

## Installation

To use MessagePack codecs, add the following dependency to your `build.sbt` file. The module is published for the JVM only:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-msg-pack" % "1.9.2"
```

## BinaryCodec

The `MessagePackCodec` object inside the `zio.schema.codec` package provides `MessagePackCodec.messagePackCodec`, which allows us to derive MessagePack codecs from a ZIO Schema:

```scala
object MessagePackCodec {
  implicit def messagePackCodec[A](implicit schema: Schema[A]): BinaryCodec[A]
}
```

## Example

The following program encodes a `Person` to MessagePack, prints the bytes in hexadecimal, and decodes them back:

```scala
import zio._
import zio.schema.codec._
import zio.schema.{DeriveSchema, Schema}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] =
    DeriveSchema.gen
  implicit val msgPackCodec: BinaryCodec[Person] =
    MessagePackCodec.messagePackCodec(schema)
}

object Main extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("MessagePack Codec Example:")
    person: Person = Person("John", 42)
    encoded: Chunk[Byte] = Person.msgPackCodec.encode(person)
    _ <- ZIO.debug(s"person object encoded to MessagePack's binary format: ${toHex(encoded)}")
    decoded <- ZIO.fromEither(Person.msgPackCodec.decode(encoded))
    _ <- ZIO.debug(s"MessagePack object decoded to Person class: $decoded")
  } yield ()

  def toHex(bytes: Chunk[Byte]): String =
    bytes.map("%02x".format(_)).mkString(" ")
}
```

The output of the above program is:

```
MessagePack Codec Example:
person object encoded to MessagePack's binary format: 82 a4 6e 61 6d 65 a4 4a 6f 68 6e a3 61 67 65 2a
MessagePack object decoded to Person class: Person(John,42)
```

## Encoding Layout

The bytes in the example follow the layout the codec uses for every type. Knowing it helps when another program reads the data:

| ZIO Schema type     | MessagePack encoding                                                 |
| ------------------- | -------------------------------------------------------------------- |
| Case class (record) | A map from field name to value; `82` above is a map with two entries |
| Sealed trait (enum) | The case's position, starting at 0, followed by the case's value     |
| `Option`            | An array with zero elements for `None`, or one element for `Some`    |
| `Either`            | A map with one entry, keyed `"left"` or `"right"`                    |
| Sequences and sets  | An array                                                             |

Field names come from the schema, so a field renamed with `@fieldName` is written under its new name. Cases are identified by position, so reordering the cases of a sealed trait changes the encoding.

## Schema Evolution

The decoder is strict. It fails when the input has a different number of fields than the case class, when it has a field the case class does not declare, or when a field is missing. It does not fill in default values, and it ignores `@optionalField`, `@fieldDefaultValue`, and `@transientField`. Old and new versions of a case class cannot read each other's data, so change a MessagePack model only together with every program that reads it.

## Streaming

:::caution
The `BinaryCodec#streamEncoder` of the MessagePack codec emits cumulative output: the bytes it emits for element `n` contain the encodings of elements 1 to `n`, so a stream of three values produces six encoded values. The stream decoder also decodes each upstream chunk as one value. To stream MessagePack values, encode and decode each element with `BinaryCodec#encode` and `BinaryCodec#decode` instead of the stream pipelines.
:::

The [How to Encode and Decode Streams](../../integration-with-zio-streams.md) guide shows how to send one value per message, and how to frame many values in one byte stream; both patterns work for MessagePack.
