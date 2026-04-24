# MessagePack Codecs

> MessagePack is a binary serialization format designed for efficient data exchange between different systems and languages. In this section, we will explore how to derive MessagePack codecs from a ZIO Schema. MessagePack codecs allow us to easily serialize and deserialize data in MessagePack format.

## Introduction

MessagePack is a binary serialization format designed for efficient data exchange between different systems and languages. In this section, we will explore how to derive MessagePack codecs from a ZIO Schema. MessagePack codecs allow us to easily serialize and deserialize data in MessagePack format.

## Installation

To use MessagePack codecs, you need to add the following dependency to your build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-msg-pack" % "1.7.5"
```

## BinaryCodec

The `MessagePackCodec` object inside the `zio.schema.codec` package provides the `messagePackCodec` operator which allows us to derive MessagePack codecs from a ZIO Schema:

```scala
object MessagePackCodec {
  implicit def messagePackCodec[A](implicit schema: Schema[A]): BinaryCodec[A] = ???
}
```

## Example

Let's try an example to see how it works:

```scala

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

```scala
MessagePack Codec Example:
person object encoded to MessagePack's binary format: 82 a4 6e 61 6d 65 a4 4a 6f 68 6e a3 61 67 65 2a
MessagePack object decoded to Person class: Person(John,42)
```
