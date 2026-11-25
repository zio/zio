# Introduction to ZIO BSON

> [ZIO BSON](https://github.com/zio/zio-bson) is a BSON library for Scala that plugs into the MongoDB Java driver.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-bson/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-bson_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-bson_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-bson_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-bson_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-bson_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-bson_2.13) [![ZIO Bson](https://img.shields.io/github/stars/zio/zio-bson?style=social)](https://github.com/zio/zio-bson)

## Introduction

The goal of this project is to create the best all-round BSON library for Scala:

- **Native BSON support** to avoid intermediate JSON conversions and support BSON types.
- **Future-proof**, prepared for Scala 3 and next-generation Java.
- **Simple**, with a small codebase and concise documentation that covers everything.
- **Helpful errors** that are readable by humans and machines.
- **ZIO types** such as `Chunk` and `NonEmptyChunk` have codecs out of the box.

## Installation

To use ZIO BSON, add the following to the `build.sbt` file. The core module is published for Scala 2.12, 2.13 and 3, and the derivation module for Scala 2.13 and 3:

```sbt
libraryDependencies ++= Seq(
  // Encoders, decoders, codecs and syntax (JVM)
  "dev.zio" %% "zio-bson"          % "1.0.12",

  // Automatic derivation of codecs for case classes and sealed traits (JVM)
  "dev.zio" %% "zio-bson-magnolia" % "1.0.12"
)
```

If we only write codecs by hand, `zio-bson` is enough.

## Modules

The `zio-schema-bson` module of the [zio-schema](https://github.com/zio/zio-schema/) project derives BSON codecs from a `Schema`. It is published by that project, not by this one, and it depends on `zio-bson`, so the types stay the same.

## Example

To get a codec for a case class, derive it with `DeriveBsonCodec.derive`, then convert values to and from `BsonValue`:

```scala
import zio.bson._
import zio.bson.magnolia._

case class Banana(curvature: Double)

object Banana {
  implicit val codec: BsonCodec[Banana] = DeriveBsonCodec.derive
}

val bson = Banana(0.2).toBsonValue

bson.as[Banana]
```

## Where to Go Next

- [Getting Started](./getting-started.md) walks through codecs, `BsonValue` conversion, the MongoDB driver and errors.
- [Reference](./bson-encoder.md) pages describe the public types, the [type mappings](./types.md) and the [errors](./bson-errors.md).
- [Tutorial: Store and Load a Case Class with MongoDB](./tutorial-store-and-load.md) builds a complete program.
- [How-to guides](./how-to-use-with-mongodb-driver.md) answer one task each, such as renaming fields or writing a codec by hand.
