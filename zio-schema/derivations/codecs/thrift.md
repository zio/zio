# Apache Thrift Codecs

> Apache Thrift is an open-source framework that allows seamless communication and data sharing between different programming languages and platforms. In this section, we will explore how to derive Apache Thrift codecs from a ZIO Schema.

## Introduction

Apache Thrift is an open-source framework that allows seamless communication and data sharing between different programming languages and platforms. In this section, we will explore how to derive Apache Thrift codecs from a ZIO Schema.

## Installation

To derive Apache Thrift codecs from a ZIO Schema, we need to add the following dependency to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-thrift" % "1.7.5"
```

## BinaryCodec

The `ThriftCodec` object inside the `zio.schema.codec` package provides the `thriftCodec` operator which allows us to derive Protobuf codecs from a ZIO Schema:

```scala
object ThriftCodec {
  implicit def thriftCodec[A](implicit schema: Schema[A]): BinaryCodec[A] = ???
}
```

## Example

Let's try an example:

```scala

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

```scala
Apache Thrift Codec Example: 
person object encoded to Thrift's binary format: 0b 00 01 00 00 00 04 4a 6f 68 6e 08 00 02 00 00 00 2a 00
Thrift object decoded to Person class: Person(John,42)
```
