# How to Update Several Keys Atomically

> Write several keys so that all of them or none of them are applied, with a WriteBatch or a transaction.

Which tool fits depends on whether the new values depend on values you read first. This guide shows both.

## When the Values Are Known: WriteBatch

A [write batch](./write-batch.md) applies its writes together, without locks:

```scala
import org.rocksdb.WriteOptions
import zio._
import zio.rocksdb.{ RocksDB, WriteBatch }

val program: ZIO[RocksDB, Throwable, Unit] =
  ZIO.scoped {
    for {
      batch <- WriteBatch.open
      _     <- batch.put("a".getBytes("UTF-8"), "1".getBytes("UTF-8"))
      _     <- batch.put("b".getBytes("UTF-8"), "2".getBytes("UTF-8"))
      _     <- ZIO.serviceWithZIO[RocksDB](_.write(new WriteOptions(), batch))
    } yield ()
  }
```

## When You Read First: Transaction

If the new value depends on the old one, open a `TransactionDB` and use `Transaction.getForUpdate` so that no other writer changes the key in between. When the function fails, nothing is written:

```scala
import zio._
import zio.rocksdb.{ Transaction, TransactionDB }

def increment(key: Array[Byte]): ZIO[TransactionDB, Throwable, Unit] =
  TransactionDB.atomically[Throwable, Unit] {
    Transaction.getForUpdate(key, exclusive = true).flatMap { current =>
      val next = current.map(new String(_, "UTF-8").toLong).getOrElse(0L) + 1
      Transaction.put(key, next.toString.getBytes("UTF-8"))
    }
  }
```

See [Transactions](./transactions.md) for locking and manual commit and rollback.
