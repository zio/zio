# Getting Started with ZIO RocksDB

> Add ZIO RocksDB to a project, open a database as a layer, store typed values and scan them back.

In this guide we write a first program that opens a RocksDB database, stores a typed value, reads it back and scans the database. We assume a ZIO 2 project; the library needs JDK 17 or newer.

## Installation

Add the dependency as shown in the [installation section](./index.md#installation) of the introduction. The native RocksDB binaries come with the `rocksdbjni` dependency, so nothing else has to be installed.

## Opening a Database

A database is a `ZLayer`. `RocksDB.live` opens it when the layer is built and closes it when the layer's `Scope` ends:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import org.rocksdb.Options
import zio._
import zio.rocksdb._

val database: ZLayer[Any, Throwable, RocksDB] =
  RocksDB.live(new Options().setCreateIfMissing(true), "/tmp/zio-rocksdb-getting-started")
```

## Storing a Typed Value

RocksDB stores bytes. A `Serde` converts a type to bytes and back, and the library ships encoders for the primitive types. For a `Long` key and a `String` value:

```scala
val stringSerde: Serde[Any, String] =
  Serde(Serializer.string, Deserializer.bytes.map(bytes => new String(bytes.toArray, UTF_8)))

val longSerde: Serde[Any, Long] = Serde(Serializer.long, Deserializer.long)

def put(id: Long, name: String): ZIO[RocksDB, Throwable, Unit] =
  for {
    key   <- longSerde(id)
    value <- stringSerde(name)
    _     <- RocksDB.put(key.toArray, value.toArray)
  } yield ()

def get(id: Long): ZIO[RocksDB, Throwable, Option[String]] =
  for {
    key   <- longSerde(id)
    found <- RocksDB.get(key.toArray)
    name <- ZIO.foreach(found) { bytes =>
             stringSerde
               .decode(Chunk.fromArray(bytes))
               .mapBoth(error => new RuntimeException(error.message), _.value)
           }
  } yield name
```

`RocksDB.get` returns `None` when the key does not exist. [Serialization](./serialization.md) explains how to build a `Serde` for your own records.

## Scanning the Database

`RocksDB.newIterator` returns every entry in key order as a `ZStream`. Because keys are compared byte by byte, big-endian non-negative `Long` keys such as those written above come out in numeric order:

```scala
val names: ZIO[RocksDB, Throwable, List[String]] =
  RocksDB.newIterator.map { case (_, value) => new String(value, UTF_8) }.runCollect.map(_.toList)
```

## Running the Program

Provide the layer to the program:

```scala
object GettingStarted extends ZIOAppDefault {
  def run =
    (put(1L, "Ada") *> put(2L, "Grace") *> get(1L).debug("get 1") *> names.debug("all names"))
      .provide(database)
}
```

## Next Steps

- [Tutorial: Building a User Store](./tutorial-user-store.md) builds a small application step by step.
- [The RocksDB service](./reference-rocksdb.md) lists every operation.
- [How-to guides](./how-to-use-column-families.md) cover single tasks, such as column families and testing.
