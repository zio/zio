# Protobuf Codecs

> Protocol Buffers (protobuf) is a binary serialization format developed by Google. It is designed for efficient data exchange between different systems and languages. In this article, we will explore how to derive Protobuf codecs from a ZIO Schema. Protobuf codecs allow us to easily serialize and deserialize data in Protobuf format, making it simple to interact with APIs and data sources that use Protobuf as their data format.

## Introduction

Protocol Buffers (protobuf) is a binary serialization format developed by Google. It is designed for efficient data exchange between different systems and languages. In this article, we will explore how to derive Protobuf codecs from a ZIO Schema. Protobuf codecs allow us to easily serialize and deserialize data in Protobuf format, making it simple to interact with APIs and data sources that use Protobuf as their data format.

## Installation

To start using Protobuf codecs in ZIO, you need to add the following dependency to your build.sbt file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-protobuf" % "1.7.5"
```

## BinaryCodec

The `ProtobufCodec` object inside the `zio.schema.codec` package provides the `protobufCodec` operator which allows us to derive Protobuf codecs from a ZIO Schema:

```scala
object ProtobufCodec {
  implicit def protobufCodec[A](implicit schema: Schema[A]): BinaryCodec[A] = ???
}
```

Optionally the `@fieldNumber(1)` annotation can be used on fields to specify the field number for a case class field. This together with default values
can be used to keep binary compatibility when evolving schemas. Default field numbers are indexed starting from 1.

For example considering the following three versions of a record:

```scala
final case class RecordV1(x: Int, y: Int)
final case class RecordV2(x: Int = 100, y: Int, z: Int)
final case class RecordV3(@fieldNumber(2) y: Int, @fieldNumber(4) extra: String = "unknown", @fieldNumber(3) z: Int)
```

The decoder of V1 can decode a binary encoded by V2, but cannot decode a binary encoded by V3 because it does not have a field number 1 (x).
The decoder of V2 can decode a binary encoded by V3 because it has a default value for field number 1 (x), 100. The decoder of V3 can read V2 but
cannot read V1 (as it does not have field number 3 (z)). As demonstrated, using explicit field numbers also allows reordering the fields without 
breaking the format. 

```scala

## Example: BinaryCodec

Let's try an example:

```scala mdoc:compile-only

case class Person(name: String, age: Int)

object Person {
  implicit val schema       : Schema[Person]      =
    DeriveSchema.gen
  implicit val protobufCodec: BinaryCodec[Person] =
    ProtobufCodec.protobufCodec(schema)
}

object Main extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("Protobuf Codec Example:")
    person: Person = Person("John", 42)
    encoded: Chunk[Byte] = Person.protobufCodec.encode(person)
    _ <- ZIO.debug(
      s"person object encoded to Protobuf's binary format: ${toHex(encoded)}"
    )
    decoded <- ZIO.fromEither(Person.protobufCodec.decode(encoded))
    _ <- ZIO.debug(s"Protobuf object decoded to Person class: $decoded")
  } yield ()

  def toHex(bytes: Chunk[Byte]): String =
    bytes.map("%02x".format(_)).mkString(" ")
}
```

Here is the output of running the above program:

```scala
Protobuf Codec Example:
person object encoded to Protobuf's binary format: 0a 04 4a 6f 68 6e 10 2a
Protobuf object decoded to Person class: Person(John,42)
```

## Example: Streaming Codecs

The following example shows how to use Protobuf codecs to encode and decode streams of data:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] =
    DeriveSchema.gen
  implicit val protobufCodec: BinaryCodec[Person] =
    ProtobufCodec.protobufCodec(schema)
}

object Main extends ZIOAppDefault {

  def run = for {
    _ <- ZIO.debug("Protobuf Stream Codecs Example:")
    person = Person("John", 42)

    personToProto = Person.protobufCodec.streamEncoder
    protoToPerson = Person.protobufCodec.streamDecoder

    newPerson <- ZStream(person)
      .via(personToProto)
      .via(protoToPerson)
      .runHead
      .some
      .catchAll(error => ZIO.debug(error))
    _ <- ZIO.debug(
      "is old person the new person? " + (person == newPerson).toString
    )
    _ <- ZIO.debug("old person: " + person)
    _ <- ZIO.debug("new person: " + newPerson)
  } yield ()
}
```

The output of running the above program is:

```scala
Protobuf Stream Codecs Example:
is old person the new person? true
old person: Person(John,42)
new person: Person(John,42)
```
