# ZIO LMDB

> [ZIO LMDB](https://dacr.github.io/zio-lmdb/) is an embedded, ACID, key-value database for ZIO. It needs no infrastructure: the data lives in a single file.

## Introduction

ZIO LMDB is built on top of [lmdbjava](https://github.com/lmdbjava/lmdbjava) and offers a type-safe, ZIO-native API.

Key features of ZIO LMDB:
- **Three Collection Kinds** — `LMDBCollection` (one key to one value), `LMDBMulti` (one key to many values), and `LMDBIndex` (one key to many keys).
- **Customizable Codecs** — Use the JSON codec (`derives LMDBCodecJson`), the bytes-optimized codecs for keys, or our own.
- **Atomic Transactions** — Run transactions on a single collection or across collections.
- **Lexicographic Ordering** — Keys are sorted, so range scans and pagination come for free.

## Installation

In order to use this library, we need to add the following line to our `build.sbt` file:

```scala
libraryDependencies += "fr.janalyse" %% "zio-lmdb" % "2.8.6"
```

## Example

In the following example, we create a collection of `Person` values, insert one, update it, and fetch it back:

```scala
import zio.*, zio.lmdb.*, zio.lmdb.json.*
import java.util.UUID

case class Person(name: String, age: Int) derives LMDBCodecJson

object Example extends ZIOAppDefault:
  def run = (for {
    people <- LMDB.collectionCreate[UUID, Person]("people", failIfExists = false)
    id     <- Random.nextUUID
    _      <- people.insert(id, Person("Alice", 30))
    _      <- people.update(id, previous => previous.copy(age = previous.age + 1))
    result <- people.fetch(id)
    _      <- Console.printLine(result)
  } yield ()).provide(LMDB.liveWithDatabaseName("my-app"), Scope.default)
```

When running with Scala CLI, the JVM needs the following options to give LMDB access to NIO internals:

```scala
//> using dep fr.janalyse::zio-lmdb:2.8.6
//> using javaOpt --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED
```

For the full API reference, including transactions, codecs, indexes, and configuration, see the [ZIO LMDB documentation](https://dacr.github.io/zio-lmdb/).
