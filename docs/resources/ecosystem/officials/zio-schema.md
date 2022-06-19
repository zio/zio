---
id: zio-schema
title: "ZIO Schema"
---

[ZIO Schema](https://github.com/zio/zio-schema) is a [ZIO](https://zio.dev)-based library for modeling the schema of data structures as first-class values.

## Introduction

Schema is a structure of a data type. ZIO Schema reifies the concept of structure for data types. It makes a high-level description of any data type and makes them as first-class values.

Creating a schema for a data type helps us to write codecs for that data type. So this library can be a host of functionalities useful for writing codecs and protocols like JSON, Protobuf, CSV, and so forth.

With schema descriptions that can be automatically derived for case classes and sealed traits, _ZIO Schema_ will be going to provide powerful features for free (Note that the project is in the development stage and all these features are not supported yet):

- Codecs for any supported protocol (JSON, protobuf, etc.), so data structures can be serialized and deserialized in a principled way
- Diffing, patching, merging, and other generic-data-based operations
- Migration of data structures from one schema to another compatible schema
- Derivation of arbitrary type classes (`Eq`, `Show`, `Ord`, etc.) from the structure of the data

When our data structures need to be serialized, deserialized, persisted, or transported across the wire, then _ZIO Schema_ lets us focus on data modeling and automatically tackle all the low-level, messy details for us.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema" % "0.0.6"
```

## Example

In this simple example first, we create a schema for `Person` and then run the _diff_ operation on two instances of the `Person` data type, and finally we encode a Person instance using _Protobuf_ protocol:

```scala
import zio.console.putStrLn
import zio.schema.codec.ProtobufCodec._
import zio.schema.{DeriveSchema, Schema}
import zio.stream.ZStream
import zio.{Chunk, ExitCode, URIO}

final case class Person(name: String, age: Int, id: String)
object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

Person.schema

import zio.schema.syntax._

Person("Alex", 31, "0123").diff(Person("Alex", 31, "124"))

def toHex(chunk: Chunk[Byte]): String =
  chunk.toArray.map("%02X".format(_)).mkString

zio.Runtime.default.unsafe.run(
  ZStream
    .succeed(Person("Thomas", 23, "2354"))
    .transduce(
      encoder(Person.schema)
    )
    .runCollect
    .flatMap(x => putStrLn(s"Encoded data with protobuf codec: ${toHex(x)}"))
).getOrThrowFiberFailure
```

## Resources

- [Zymposium - ZIO Schema](https://www.youtube.com/watch?v=GfNiDaL5aIM) by John A. De Goes, Adam Fraser and Kit Langton (May 2021)
