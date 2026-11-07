# Write Batches

> Apply several RocksDB writes atomically with WriteBatch and RocksDB#write, and control durability with flush and flushWal.

A `WriteBatch` collects several writes and hands them to RocksDB in one call, so that they are applied atomically: after a crash, either all of them are visible or none are. In this guide we build a batch, write it to the database, and then look at the `RocksDB#flush` and `RocksDB#flushWal` operations that control when data reaches disk.

A batch is lighter than a [transaction](./transactions.md): it takes no locks and cannot read, so it fits writes whose values are already known. When a write depends on a value read earlier, use a transaction instead.

## The Problem

With plain `RocksDB.put`, every key is written separately. An application that stores a record under two keys, such as a user and an index entry that points to the user, has a window between the two calls in which a crash leaves a user without an index entry or an index entry without a user. Each `RocksDB.put` call is atomic on its own, but the pair is not.

## Writing a Batch

`WriteBatch.open` creates a batch inside a `Scope`, and the native batch is released when the scope ends. `WriteBatch#put` adds a key-value pair to the batch without touching the database. `RocksDB#write` applies the whole batch with the given `WriteOptions`. The `RocksDB` companion object has no accessor for `write`, so the call goes through the service:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import org.rocksdb.WriteOptions
import zio._
import zio.rocksdb.{ RocksDB, WriteBatch }

val storeUser: ZIO[RocksDB, Throwable, Unit] =
  ZIO.scoped {
    for {
      batch <- WriteBatch.open
      _     <- batch.put("user:42".getBytes(UTF_8), "Ada".getBytes(UTF_8))
      _     <- batch.put("index:name:Ada".getBytes(UTF_8), "42".getBytes(UTF_8))
      _     <- ZIO.serviceWithZIO[RocksDB](_.write(new WriteOptions(), batch))
    } yield ()
  }
```

After the call, both keys are readable, and a crash during the write cannot leave only one of them.

`WriteBatch.open(reservedBytes)` reserves buffer space up front for large batches, and `WriteBatch.open(serialized)` rebuilds a batch from the bytes of an earlier one.

## Writing to a Column Family

Each key can go to a different column family: `WriteBatch#put(cfHandle, key, value)` takes a `ColumnFamilyHandle` as its first argument, so a single batch can span families:

```scala
import org.rocksdb.{ ColumnFamilyHandle, WriteOptions }
import zio._
import zio.rocksdb.{ RocksDB, WriteBatch }

def storeInBoth(first: ColumnFamilyHandle, second: ColumnFamilyHandle): ZIO[RocksDB, Throwable, Unit] =
  ZIO.scoped {
    for {
      batch <- WriteBatch.open
      _     <- batch.put(first, "k".getBytes("UTF-8"), "1".getBytes("UTF-8"))
      _     <- batch.put(second, "k".getBytes("UTF-8"), "2".getBytes("UTF-8"))
      _     <- ZIO.serviceWithZIO[RocksDB](_.write(new WriteOptions(), batch))
    } yield ()
  }
```

:::note
`WriteBatch` currently exposes only `WriteBatch#put`. To delete keys atomically together with writes, use a [transaction](./transactions.md).
:::

## Controlling Durability

RocksDB first buffers writes in memory and in a write-ahead log (WAL). `RocksDB#flush` writes the in-memory tables to disk, and `RocksDB#flushWal` moves the buffered write-ahead log to its file, syncing it when `sync` is `true`. Neither has a companion accessor, so call them on the service:

```scala
import org.rocksdb.FlushOptions
import zio._
import zio.rocksdb.RocksDB

val persist: ZIO[RocksDB, Throwable, Unit] =
  ZIO.serviceWithZIO[RocksDB] { db =>
    db.flush(new FlushOptions().setWaitForFlush(true)) *> db.flushWal(sync = true)
  }
```

`RocksDB#flush` also has variants that take one `ColumnFamilyHandle` or a list of them. To make a single write durable without a flush, pass `WriteOptions` with `setSync(true)` to `RocksDB#write` or to the transaction functions.
