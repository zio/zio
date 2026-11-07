# Introduction to ZIO RocksDB

> ZIO RocksDB is a ZIO-native wrapper around the RocksDB embedded key-value store: layers, streaming iterators, transactions and typed serialization.

[ZIO RocksDB](https://github.com/zio/zio-rocksdb) is a ZIO-based interface to [RocksDB](https://rocksdb.org), an embeddable persistent key-value store that is optimized for fast storage. The library wraps the RocksDB Java API (`rocksdbjni`) so that a database handle is a `ZLayer`, every operation is a `ZIO` effect, and scans are `ZStream`s.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-rocksdb/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-rocksdb_2.12.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-rocksdb_2.12) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-rocksdb_2.12%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-rocksdb_2.12/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-rocksdb_2.12/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-rocksdb_2.12) [![ZIO RocksDB](https://img.shields.io/github/stars/zio/zio-rocksdb?style=social)](https://github.com/zio/zio-rocksdb)

## What the Library Provides

ZIO RocksDB covers the parts of the RocksDB API that an application touches most often:

| Capability                          | Entry point                           | Guide                                       |
| ----------------------------------- | ------------------------------------- | ------------------------------------------- |
| Reading and writing keys            | `RocksDB`                             | This page                                   |
| Typed keys and values               | `Serializer`, `Deserializer`, `Serde` | [Serialization](./serialization.md)         |
| Scanning and range queries          | `RocksDB#newIterator`                 | [Iterators and Range Scans](./iterators.md) |
| Atomic multi-key updates            | `TransactionDB`, `Transaction`        | [Transactions](./transactions.md)           |
| Atomic batched writes without locks | `WriteBatch`, `RocksDB#write`         | [Write Batches](./write-batch.md)           |

## Installation

To use the library, add the following line to `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-rocksdb" % "0.4.5"
```

The library is published for Scala 2.12, 2.13 and 3, requires ZIO 2, and runs on JDK 17 or newer. The native RocksDB binaries come with the `rocksdbjni` dependency, so no separate installation of RocksDB is needed.

## Getting Started

Every operation in ZIO RocksDB takes and returns `Array[Byte]`, because that is what RocksDB stores. The examples below encode strings as UTF-8 by hand; to avoid this for richer types, see [Serialization](./serialization.md).

### Opening a Database

To open a database, build a `ZLayer` with `RocksDB.live`. The layer opens the database when the layer is built and closes it when the enclosing `Scope` ends, so the handle cannot leak. To create the database directory when it does not exist yet, pass an `Options` value with `setCreateIfMissing(true)`:

```scala
import org.rocksdb.Options
import zio._
import zio.rocksdb.RocksDB

val database: ZLayer[Any, Throwable, RocksDB] =
  RocksDB.live(new Options().setCreateIfMissing(true), "/tmp/zio-rocksdb-intro")
```

`RocksDB.live(path)` is a shorthand that opens the database with RocksDB's default options.

### Reading, Writing and Deleting Keys

Once a `RocksDB` layer is available, the accessor methods on the `RocksDB` companion object write, read and delete keys. `RocksDB.get` returns `None` when the key does not exist:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import org.rocksdb.Options
import zio._
import zio.rocksdb.RocksDB

object IntroExample extends ZIOAppDefault {
  private val key   = "greeting".getBytes(UTF_8)
  private val value = "hello".getBytes(UTF_8)

  private val program: ZIO[RocksDB, Throwable, Unit] =
    for {
      _       <- RocksDB.put(key, value)
      found   <- RocksDB.get(key)
      _       <- Console.printLine(found.map(new String(_, UTF_8)))
      _       <- RocksDB.delete(key)
      missing <- RocksDB.get(key)
      _       <- Console.printLine(missing.isEmpty)
    } yield ()

  def run =
    program.provide(RocksDB.live(new Options().setCreateIfMissing(true), "/tmp/zio-rocksdb-intro"))
}
```

All operations fail with `Throwable`, which is whatever RocksDB raised (a `RocksDBException` for most storage errors).

### Reading Several Keys at Once

To fetch many keys in one call, use `RocksDB.multiGetAsList`. The result list lines up positionally with the keys that were passed in, with `None` for each missing key:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.RocksDB

val lookup: ZIO[RocksDB, Throwable, List[Option[String]]] =
  RocksDB
    .multiGetAsList(List("a", "b", "c").map(_.getBytes(UTF_8)))
    .map(_.map(_.map(new String(_, UTF_8))))
```

## Where to Go Next

The rest of the documentation builds on the layer and the accessor methods shown above. [Serialization](./serialization.md) explains how to store domain types instead of raw bytes, [Iterators and Range Scans](./iterators.md) shows how to read ranges of keys as a stream, [Transactions](./transactions.md) covers `TransactionDB` for atomic multi-key updates, and [Write Batches](./write-batch.md) shows how to apply several writes atomically without a transaction.
