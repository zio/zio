# Bson Codecs

> BSON (Binary JSON) is a binary serialization format used to store and exchange data efficiently. In this article, we will explore how to derive BSON codecs from a ZIO Schema. The `zio-schema-bson` module, provides support for deriving codecs from ZIO Schema, and makes it easy to communicate data in BSON format.

## Introduction

BSON (Binary JSON) is a binary serialization format used to store and exchange data efficiently. In this article, we will explore how to derive BSON codecs from a ZIO Schema. The `zio-schema-bson` module, provides support for deriving codecs from ZIO Schema, and makes it easy to communicate data in BSON format.

## Installation

To use BSON codecs, you need to add the following dependency to your Scala project:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-bson" % 1.7.5
```

## BsonSchemaCodec

The `BsonSchemaCodec` object inside the `zio.schema.codec` package provides the `bsonCodec` operator which allows us to derive Protobuf codecs from a ZIO Schema:

```scala
object BsonSchemaCodec {
  def bsonCodec[A](schema: Schema[A]): BsonCodec[A]
}
```

## Example

Let's see an example of how to derive a BSON codec for a case class using ZIO Schema:

```scala

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
  implicit val bsonCodec: BsonCodec[Person] =
    BsonSchemaCodec.bsonCodec(Person.schema)
}

object Main extends ZIOAppDefault {
  def run = for {
    _ <- ZIO.debug("Bson Example:")
    person: Person     = Person("John", 42)
    encoded: BsonValue = person.toBsonValue
    _       <- ZIO.debug(s"person object encoded to BsonValue: $encoded")
    decoded <- ZIO.fromEither(encoded.as[Person])
    _ <- ZIO.debug(s"BsonValue of person object decoded to Person: $decoded")
  } yield ()
}
```

In the example above, we defined a case class `Person` with fields `name` and `age`. We then derived a ZIO Schema for the `Person` case class using `DeriveSchema.gen`.

The `BsonSchemaCodec.bsonCodec` method allowed us to create a BSON codec for the `Person` case class by passing its corresponding ZIO Schema. Now, we can effortlessly encode `Person` objects to BSON and decode BSON values back to Person instances.
