# Introduction to ZIO Schema

> [ZIO Schema](https://github.com/zio/zio-schema) is a [ZIO](https://zio.dev)-based library for modeling the schema of data structures as first-class values.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-schema/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-schema_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-schema_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-schema_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-schema_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-schema_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-schema_2.13) [![ZIO Schema](https://img.shields.io/github/stars/zio/zio-schema?style=social)](https://github.com/zio/zio-schema)

## Introduction

ZIO Schema helps us solve some of the most common problems in distributed computing, such as serialization, deserialization, and data migration.

It turns a compile-time construct (the type of a data structure) into a runtime construct (a value that can be read, manipulated, and composed at runtime). A schema is a structure of a data type. ZIO Schema reifies the concept of structure for data types. It makes a high-level description of any data type and makes them first-class values.

Creating a schema for a data type helps us to write codecs for that data type. ZIO Schema ships codecs for JSON, Protobuf, Avro, BSON, MessagePack, Thrift, and XML, and the same schema drives all of them.

## What Problems Does ZIO Schema Solve?

With schema descriptions that can be automatically derived for case classes and sealed traits, _ZIO Schema_ provides powerful features for free:

1. Generic programming without writing macros yourself. The derivation macros inspect your types once, and everything else works on the resulting schema value:
    1. Creating serialization and deserialization codecs for any supported protocol (JSON, Protobuf, etc.)
    2. Deriving an `Ordering` from the structure of the data, and deriving your own type classes with a `Deriver`
    3. Default values for data types
2. Automate ETL (Extract, Transform, Load) pipelines
    1. Diffing: diffing between two values of the same type
    2. Patching: applying a diff to a value to update it
    3. Migration: migrating values from one type to another
3. Computations as data: Not only can we turn types into values, but we can also turn computations into values. This opens up a whole new world of possibilities concerning distributed computing.

When our data structures need to be serialized, deserialized, persisted, or transported across the wire, then _ZIO Schema_ lets us focus on data modeling and automatically tackle all the low-level, messy details for us.

A growing number of ZIO libraries build on _ZIO Schema_, including [ZIO Flow](https://zio.dev/zio-flow), [ZIO Redis](https://zio.dev/zio-redis), [ZIO SQL](https://zio.dev/zio-sql) and [ZIO DynamoDB](https://zio.dev/zio-dynamodb).

## Installation

Every project needs two modules: `zio-schema`, which holds `Schema` itself, and `zio-schema-derivation`, which derives schemas for case classes and sealed traits. On the JVM, add them to `build.sbt`:

```scala
libraryDependencies ++= Seq(
  // Schema, DynamicValue, diff and patch, migration, validation (JVM, Scala.js, Native)
  "dev.zio" %% "zio-schema"            % "1.9.2",

  // DeriveSchema.gen and the Deriver API for your own type classes (JVM, Scala.js, Native)
  "dev.zio" %% "zio-schema-derivation" % "1.9.2"
)
```

In a Scala.js or Scala Native project, use the `%%%` operator instead:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %%% "zio-schema"            % "1.9.2",

  "dev.zio" %%% "zio-schema-derivation" % "1.9.2"
)
```

On Scala 2, schema derivation uses macros that need `scala-reflect` at compile time. Scala 3 has no `scala-reflect` artifact, so a build that cross-compiles adds it only for Scala 2:

```scala
libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided)
    case _            => Seq.empty
  }
}
```

ZIO Schema is published for Scala 2.12, 2.13, and 3.

:::caution[Scala 3 Projects Need Scala 3.9 or Later]
The Scala 3 artifacts are compiled with Scala 3.9, so a Scala 3 project must use Scala 3.9 or later to depend on them.
:::

[Getting Started](getting-started.md) walks through a first program: deriving a schema, encoding JSON, and validating values. The [Modules](#modules) section below lists the optional modules for codecs, optics, and testing.

## Modules

Every other feature lives in its own optional module, and we add only the modules we use. All modules share the core module's version, and each one already depends on `zio-schema` and `zio-schema-derivation`.

### Codecs

A codec module encodes and decodes values in one data format, using their schemas. On the JVM, every codec is available. Copy the block and delete the lines you don't need:

```scala
libraryDependencies ++= Seq(
  // JSON codec (JVM, Scala.js, Native)
  "dev.zio" %% "zio-schema-json"     % "1.9.2",

  // Protocol Buffers codec (JVM, Scala.js, Native)
  "dev.zio" %% "zio-schema-protobuf" % "1.9.2",

  // XML codec (JVM, Scala.js, Native)
  "dev.zio" %% "zio-schema-xml"      % "1.9.2",

  // Apache Avro codec (JVM only)
  "dev.zio" %% "zio-schema-avro"     % "1.9.2",

  // BSON codec for MongoDB (JVM only)
  "dev.zio" %% "zio-schema-bson"     % "1.9.2",

  // MessagePack codec (JVM only)
  "dev.zio" %% "zio-schema-msg-pack" % "1.9.2",

  // Apache Thrift codec (JVM only)
  "dev.zio" %% "zio-schema-thrift"   % "1.9.2"
)
```

In a Scala.js or Scala Native project, only the JSON, Protocol Buffers, and XML codecs are available. The Avro, BSON, MessagePack, and Thrift codecs have no Scala.js or Scala Native artifacts:

```scala
libraryDependencies ++= Seq(
  // JSON codec
  "dev.zio" %%% "zio-schema-json"     % "1.9.2",

  // Protocol Buffers codec
  "dev.zio" %%% "zio-schema-protobuf" % "1.9.2",

  // XML codec
  "dev.zio" %%% "zio-schema-xml"      % "1.9.2"
)
```

Each codec has its own page: [JSON](derivations/codecs/json.md), [Protocol Buffers](derivations/codecs/protobuf.md), [XML](derivations/codecs/xml.md), [Apache Avro](derivations/codecs/avro.md), [BSON](derivations/codecs/bson.md), [MessagePack](derivations/codecs/message-pack.md), and [Apache Thrift](derivations/codecs/thrift.md).

### Optics

The optics module derives lenses, prisms, and traversals from a schema, for reading and updating nested data (JVM, Scala.js, Native):

```scala
libraryDependencies += "dev.zio" %% "zio-schema-optics" % "1.9.2"
```

In a Scala.js or Scala Native project, use `%%%` instead of `%%`. [Optics Derivation](derivations/optics-derivation.md) describes the module.

### Testing

The ZIO Test module derives ZIO Test generators (`Gen`) from a schema, for property-based tests (JVM, Scala.js, Native). Tests are its only users, so it goes in the `Test` configuration:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-zio-test" % "1.9.2" % Test
```

In a Scala.js or Scala Native project, use `%%%` instead of `%%`. [Derivation of ZIO Test Generators](derivations/zio-test-gen-derivation.md) describes the module.

### Internal Modules

The build also publishes `zio-schema-macros`, an internal helper that the other modules pull in, so you never add it yourself.

## Example

In this example, we derive a schema for `Person` and then encode a `Person` value as a stream of Protocol Buffers bytes. The example needs the `zio-schema-protobuf` module:

```scala
import zio._
import zio.stream._
import zio.schema.codec.{BinaryCodec, ProtobufCodec}
import zio.schema.{DeriveSchema, Schema}

import java.io.IOException

final case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person]    = DeriveSchema.gen
  val protobufCodec: BinaryCodec[Person] = ProtobufCodec.protobufCodec
}

object Main extends ZIOAppDefault {
  def run: ZIO[Any, IOException, Unit] =
    ZStream
      .succeed(Person("John", 43))
      .via(Person.protobufCodec.streamEncoder)
      .runCollect
      .flatMap(x =>
        Console.printLine(s"Encoded data with protobuf codec: ${toHex(x)}")
      )

  def toHex(chunk: Chunk[Byte]): String =
    chunk.map("%02X".format(_)).mkString
}
```

Here is the output of running the above program:

```text
Encoded data with protobuf codec: 0A044A6F686E102B
```

## Resources

- [Zymposium - ZIO Schema](https://www.youtube.com/watch?v=GfNiDaL5aIM) by John A. De Goes, Adam Fraser, and Kit Langton (May 2021)
- [ZIO SCHEMA: A Toolkit For Functional Distributed Computing](https://www.youtube.com/watch?v=lJziseYKvHo&t=481s) by Dan Harris (Functional Scala 2021)
- [Creating Declarative Query Plans With ZIO Schema](https://www.youtube.com/watch?v=ClePN4P9_pg) by Dan Harris (ZIO World 2022)
- [Describing Data...with free applicative functors (and more)](https://www.youtube.com/watch?v=oRLkb6mqvVM) by Kris Nuttycombe (Scala World) on the idea behind the [xenomorph](https://github.com/nuttycom/xenomorph) library
