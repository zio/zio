# Iterators and Range Scans

> Scan a RocksDB database as a ZStream: full scans, range scans from a key, backward scans, and scans over column families.

RocksDB keeps keys sorted, which makes it good at reading many neighbouring keys at once. In this guide we scan a database with `RocksDB#newIterator`, which exposes a RocksDB iterator as a `ZStream`. By the end, you will be able to read all entries, start a scan at a given key, stop at the end of a key prefix, and read the newest entries first.

We start with a full scan, then add a starting position, an upper bound and a direction.

## The Problem

`RocksDB.get` needs the exact key. To list everything stored under a key prefix, or the last few entries written, the application has to ask RocksDB for entries in key order. With the raw `rocksdbjni` API this means creating a `RocksIterator`, positioning it, advancing it in a loop, and closing it on every exit path, including failures and interruption:

```scala
import org.rocksdb.RocksDB

def keysFrom(db: RocksDB, start: Array[Byte]): List[Array[Byte]] = {
  val it = db.newIterator()
  try {
    it.seek(start)
    val keys = List.newBuilder[Array[Byte]]
    while (it.isValid) {
      keys += it.key()
      it.next()
    }
    keys.result()
  } finally it.close()
}
```

A forgotten `close` leaks native memory, and the loop loads everything into memory before the caller can stop it. ZIO RocksDB turns the iterator into a stream that opens the native iterator when the stream starts, closes it when the stream ends, fails or is interrupted, and reads entries only as fast as the consumer pulls them.

## Scanning All Entries

`RocksDB.newIterator` returns a `ZStream[RocksDB, Throwable, (Array[Byte], Array[Byte])]` of key-value pairs, starting at the first key and moving forward. All of the stream operators apply, for example `ZStream#take` to read only the first entries:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.RocksDB

val firstTen: ZIO[RocksDB, Throwable, List[(String, String)]] =
  RocksDB.newIterator
    .take(10)
    .map { case (key, value) => new String(key, UTF_8) -> new String(value, UTF_8) }
    .runCollect
    .map(_.toList)
```

For a database holding the keys `a1`, `a2`, `a3`, `b1`, `b2` and `c1`, a full scan emits them in that order, because RocksDB compares keys byte by byte. Taking the first two entries stops the stream, and the native iterator is closed right away.

## Starting at a Key

To start somewhere other than the first key, use the accessor on the `RocksDB` service, which takes a `Direction` and a `Position`. `Position.Target` seeks to the first key that is greater than or equal to the target, so the target does not have to exist. The `RocksDB` companion object has no accessor for this overload, so we reach it through the service with `ZStream.serviceWithStream`:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.RocksDB
import zio.rocksdb.iterator.{ Direction, Position }
import zio.stream.ZStream

def scanFrom(start: String): ZStream[RocksDB, Throwable, (Array[Byte], Array[Byte])] =
  ZStream.serviceWithStream[RocksDB](
    _.newIterator(Direction.Forward, Position.Target(Chunk.fromArray(start.getBytes(UTF_8))))
  )
```

With the example keys above, `scanFrom("b")` emits `b1`, `b2` and `c1`, and `scanFrom("z")` emits nothing.

## Scanning a Key Prefix

RocksDB iterators have no upper bound, so a scan that starts at a target continues to the end of the database. To read only the entries under a prefix, start at the prefix and stop with `ZStream#takeWhile` as soon as a key no longer starts with it:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.RocksDB
import zio.rocksdb.iterator.{ Direction, Position }
import zio.stream.ZStream

def scanPrefix(prefix: String): ZStream[RocksDB, Throwable, (String, String)] =
  ZStream
    .serviceWithStream[RocksDB](
      _.newIterator(Direction.Forward, Position.Target(Chunk.fromArray(prefix.getBytes(UTF_8))))
    )
    .map { case (key, value) => new String(key, UTF_8) -> new String(value, UTF_8) }
    .takeWhile { case (key, _) => key.startsWith(prefix) }
```

With the example keys, `scanPrefix("b")` emits `b1` and `b2`. Because `ZStream#takeWhile` ends the stream at the first key outside the prefix, the scan reads only one entry past the range.

## Reading Backward

`Direction.Backward` walks from higher keys to lower keys. Together with `Position.Last`, this reads the newest entries first when keys are ordered by time or by a counter. The first `n` entries from the end:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.RocksDB
import zio.rocksdb.iterator.{ Direction, Position }
import zio.stream.ZStream

def lastKeys(n: Long): ZIO[RocksDB, Throwable, List[String]] =
  ZStream
    .serviceWithStream[RocksDB](_.newIterator(Direction.Backward, Position.Last))
    .take(n)
    .map { case (key, _) => new String(key, UTF_8) }
    .runCollect
    .map(_.toList)
```

With the example keys, `lastKeys(3)` returns `c1`, `b2` and `b1`.

:::warning
`Position.Target` always seeks forward to the first key greater than or equal to the target, even when the direction is `Direction.Backward`. If the target key exists, a backward scan starts at it and then walks down. If it does not exist, the scan starts at the next key above the target, which is probably not what a "start at or below this key" query needs. With the example keys, a backward scan from target `b` emits `b1`, `a3`, `a2` and `a1`. A target above the last key emits nothing in either direction.
:::

## Scanning Column Families

A `ColumnFamilyHandle` comes from `RocksDB.createColumnFamily`, which returns the handle of the family it creates. Each overload has a column family variant that takes a handle first: `RocksDB.newIterator(cfHandle)` scans the whole family forward, and `RocksDB.newIterator(cfHandle, direction, position)` takes a direction and a position like the default-family overload. `RocksDB.newIterators` scans several families at once and emits one pair of handle and entry stream for each, always forward from the first key:

```scala
import org.rocksdb.ColumnFamilyHandle
import zio._
import zio.rocksdb.RocksDB

def countEntries(handles: List[ColumnFamilyHandle]): ZIO[RocksDB, Throwable, List[(ColumnFamilyHandle, Long)]] =
  RocksDB
    .newIterators(handles)
    .mapZIO { case (handle, entries) => entries.runCount.map(handle -> _) }
    .runCollect
    .map(_.toList)
```

:::note
Opening a database with column family descriptors does not return the handles through `RocksDB#initialHandles` yet, which is a known bug. Until it is fixed, obtain handles from `RocksDB.createColumnFamily`.
:::

## Decoding Entries

The stream carries raw bytes. To scan typed values, map each pair through a `Deserializer` or `Serde` from [Serialization](./serialization.md). Mapping with `ZStream#mapZIO` and failing with the decoding error keeps the failure inside the stream:

```scala
import zio._
import zio.rocksdb._
import zio.stream.ZStream

def scanLongs(deserializer: Deserializer[Any, Long]): ZStream[RocksDB, Throwable, (Long, Long)] =
  RocksDB.newIterator.mapZIO { case (key, value) =>
    val decoded =
      for {
        k <- deserializer.decode(Chunk.fromArray(key)).map(_.value)
        v <- deserializer.decode(Chunk.fromArray(value)).map(_.value)
      } yield (k, v)
    decoded.mapError(error => new RuntimeException(error.message))
  }
```

## Going Further

For several writes that must succeed or fail together, see [Transactions](./transactions.md) and [Write Batches](./write-batch.md).
