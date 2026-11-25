# Introduction to ZIO RocksDB

> ZIO RocksDB is a ZIO-native wrapper around the RocksDB embedded key-value store: layers, streaming iterators, transactions and typed serialization.

[ZIO RocksDB](https://github.com/zio/zio-rocksdb) is a ZIO-based interface to [RocksDB](https://rocksdb.org), an embeddable persistent key-value store that is optimized for fast storage. The library wraps the RocksDB Java API (`rocksdbjni`) so that a database handle is a `ZLayer`, every operation is a `ZIO` effect, and scans are `ZStream`s.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-rocksdb/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-rocksdb_2.12.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-rocksdb_2.12) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-rocksdb_2.12%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-rocksdb_2.12/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-rocksdb_2.12/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-rocksdb_2.12) [![ZIO RocksDB](https://img.shields.io/github/stars/zio/zio-rocksdb?style=social)](https://github.com/zio/zio-rocksdb)

## What the Library Provides

ZIO RocksDB covers the parts of the RocksDB API that an application touches most often:

| Capability                          | Entry point                           | Guide                                             |
| ----------------------------------- | ------------------------------------- | ------------------------------------------------- |
| Reading and writing keys            | `RocksDB`                             | [Getting Started](./getting-started.md)           |
| Typed keys and values               | `Serializer`, `Deserializer`, `Serde` | [Serialization](./serialization.md)               |
| Scanning and range queries          | `RocksDB#newIterator`                 | [Iterators and Range Scans](./iterators.md)       |
| Atomic multi-key updates            | `TransactionDB`, `Transaction`        | [Transactions](./transactions.md)                 |
| Atomic batched writes without locks | `WriteBatch`, `RocksDB#write`         | [Write Batches](./write-batch.md)                 |
| Column families                     | `RocksDB#createColumnFamily`          | [Column Families](./reference-column-families.md) |

## Installation

ZIO RocksDB is a single artifact. Add it to `build.sbt`:

```scala
libraryDependencies ++= Seq(
  // ZIO RocksDB: JVM only, because it wraps the native RocksDB Java API
  "dev.zio" %% "zio-rocksdb" % "0.4.6"
)
```

The library is published for Scala 2.12, 2.13 and 3, requires ZIO 2, and runs on JDK 17 or newer. The native RocksDB binaries come with the `rocksdbjni` dependency, so no separate installation of RocksDB is needed.

## Getting Started

A database is a `ZLayer`: `RocksDB.live` opens it when the layer is built and closes it when the enclosing `Scope` ends. Keys and values are `Array[Byte]`, and `RocksDB.get` returns `None` when the key does not exist:

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
      _     <- RocksDB.put(key, value)
      found <- RocksDB.get(key)
      _     <- Console.printLine(found.map(new String(_, UTF_8)))
    } yield ()

  def run =
    program.provide(RocksDB.live(new Options().setCreateIfMissing(true), "/tmp/zio-rocksdb-intro"))
}
```

The [Getting Started](./getting-started.md) guide continues with typed values and scans.

## Where to Go Next

[Getting Started](./getting-started.md) walks through a first program with typed values. The reference pages cover [the RocksDB service](./reference-rocksdb.md), [Column Families](./reference-column-families.md), [Serialization](./serialization.md), [Iterators and Range Scans](./iterators.md), [Transactions](./transactions.md), [the Transaction API](./reference-transaction-api.md) and [Write Batches](./write-batch.md). To learn by building, follow the [tutorial](./tutorial-user-store.md), and for a specific goal pick a [how-to guide](./how-to-use-column-families.md).
