# Mongo4cats

> [Mongo4cats](https://github.com/Kirill5k/mongo4cats) is a Scala library that provides a functional and idiomatic interface for working with MongoDB. It is built on top of the official MongoDB Java driver and has a dedicated ZIO 2 module.

## Introduction

Mongo4cats integrates with both Cats Effect/FS2 and ZIO. The ZIO module exposes the client, database, and collection as ZIO services (`ZMongoClient`, `ZMongoDatabase`, and `ZMongoCollection`), with a fluent API for building filters, projections, and sorting. It is available for Scala 2.12, 2.13, and 3.

## Installation

In order to use this library with ZIO 2, we need to add the following lines to our `build.sbt` file:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio" % "0.8.0"
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-embedded" % "0.8.0" % Test
```

The `mongo4cats-zio-embedded` module is optional and provides an embedded MongoDB for tests. To encode and decode documents with ZIO JSON, also add:

```scala
libraryDependencies += "io.github.kirill5k" %% "mongo4cats-zio-json" % "0.8.0"
```

## Example

The following example connects to a local MongoDB, inserts some documents, and queries them:

```scala
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.operations.{Filter, Projection}
import mongo4cats.zio.{ZMongoClient, ZMongoCollection, ZMongoDatabase}
import zio._

object Zio extends ZIOAppDefault {

  val client     = ZLayer.scoped[Any](ZMongoClient.fromConnectionString("mongodb://localhost:27017"))
  val database   = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoClient](_.getDatabase("my-db")))
  val collection = ZLayer.fromZIO(ZIO.serviceWithZIO[ZMongoDatabase](_.getCollection("docs")))

  val program = for {
    coll <- ZIO.service[ZMongoCollection[Document]]
    _    <- coll.insertMany((0 to 100).map(i => Document("name" := s"doc-$i", "index" := i)))
    docs <- coll.find
      .filter(Filter.gte("index", 10) && Filter.regex("name", "doc-[1-9]0"))
      .projection(Projection.excludeId)
      .sortByDesc("name")
      .limit(5)
      .all
    _ <- Console.printLine(docs.mkString("[\n", ",\n", "\n]"))
  } yield ()

  override def run = program.provide(client, database, collection)
}
```

For the full documentation, see the [Mongo4cats microsite](https://kirill5k.github.io/mongo4cats/docs/).
