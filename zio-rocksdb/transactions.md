# Transactions

> Update several RocksDB keys atomically with TransactionDB: atomically, manual transactions, getForUpdate locking, rollback and lock conflicts.

`RocksDB` writes each key on its own: when an application updates two keys and fails in between, the database holds half of the change. In this guide we use `TransactionDB`, which runs a group of reads and writes as one transaction that is applied completely or not at all. By the end, you will be able to run an atomic update, undo one on failure, protect a read-modify-write cycle from concurrent writers, and manage a transaction by hand.

We start with `TransactionDB.atomically`, which covers most uses, and then look at locking and manual transactions. The library uses the RocksDB [pessimistic transaction API](https://github.com/facebook/rocksdb/wiki/Transactions#transactiondb).

## The Problem

Moving money between two accounts is a read-modify-write cycle on two keys. With plain `RocksDB` operations, a failure after the first write leaves the accounts out of balance, and two concurrent transfers can both read the same balance and overwrite each other's result. The application cannot fix either problem on its own, because each `RocksDB.put` is independent and nothing holds a key between a `RocksDB.get` and the `RocksDB.put` that follows it.

Transactions solve both: writes become visible only on commit, a failed transaction leaves no trace, and a key that is read for update stays locked until the transaction ends.

## Opening a TransactionDB

`TransactionDB` extends `RocksDB`, so every operation from the [introduction](./index.md), including `RocksDB.get`, `RocksDB.put` and `RocksDB.newIterator`, also works on a `TransactionDB` layer. Build the layer with `TransactionDB.live`, passing `Options` and the database path, optionally with a `TransactionDBOptions` value in between. The database is closed when the layer's scope ends:

```scala
import org.rocksdb.{ Options, TransactionDBOptions }
import zio._
import zio.rocksdb.TransactionDB

val database: ZLayer[Any, Throwable, TransactionDB] =
  TransactionDB.live(new Options().setCreateIfMissing(true), "/tmp/zio-rocksdb-tx")

val databaseWithOptions: ZLayer[Any, Throwable, TransactionDB] =
  TransactionDB.live(
    new Options().setCreateIfMissing(true),
    new TransactionDBOptions().setTransactionLockTimeout(2000),
    "/tmp/zio-rocksdb-tx"
  )
```

## Running an Atomic Update

`TransactionDB.atomically` takes a program that needs a `Transaction` in its environment, runs it inside a new transaction, and commits when the program succeeds. The functions on the `Transaction` companion object (`Transaction.get`, `Transaction.put` and `Transaction.delete`) are the building blocks. To move an amount between two accounts stored as UTF-8 numbers:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.{ Transaction, TransactionDB }

def balance(bytes: Option[Array[Byte]]): Long =
  bytes.map(new String(_, UTF_8).toLong).getOrElse(0L)

def encode(amount: Long): Array[Byte] = amount.toString.getBytes(UTF_8)

def transfer(from: String, to: String, amount: Long): ZIO[TransactionDB, Throwable, Unit] =
  TransactionDB.atomically[Throwable, Unit] {
    for {
      fromBalance <- Transaction.getForUpdate(from.getBytes(UTF_8), exclusive = true).map(balance)
      toBalance   <- Transaction.getForUpdate(to.getBytes(UTF_8), exclusive = true).map(balance)
      _           <- ZIO.fail(new IllegalStateException("insufficient funds")).when(fromBalance < amount)
      _           <- Transaction.put(from.getBytes(UTF_8), encode(fromBalance - amount))
      _           <- Transaction.put(to.getBytes(UTF_8), encode(toBalance + amount))
    } yield ()
  }
```

When the program fails, the transaction is not committed, and none of its writes are applied. The error returned by `TransactionDB.atomically` is the error of the program, whose type must be a supertype of `Throwable`. For example, writing a key and then failing leaves the previously stored value unchanged: the stored `v0` is still there after `TransactionDB.atomically` returns the `RuntimeException`.

Inside the transaction, reads see the transaction's own writes, and other readers do not see them until the commit. Reading the key with `RocksDB.get` while the transaction is open returns the old value.

## Choosing Between Overloads

`TransactionDB.atomically` has four overloads, which differ in two ways. Each exists with and without a `jrocks.WriteOptions` argument, for control over how the commit is written to disk, for example `new WriteOptions().setSync(true)`. And each exists for programs that need only `Transaction` and for programs that need `Transaction` plus other services:

```scala
import org.rocksdb.WriteOptions
import zio._
import zio.rocksdb.{ Transaction, TransactionDB }

val key = "key".getBytes("UTF-8")

val syncedWrite: ZIO[TransactionDB, Throwable, Unit] =
  TransactionDB.atomically[Throwable, Unit](new WriteOptions().setSync(true))(Transaction.put(key, key))

final case class Prefix(value: String)

val withService: ZIO[TransactionDB with Prefix, Throwable, Unit] =
  TransactionDB.atomically[Prefix, Throwable, Unit] {
    ZIO.service[Prefix].flatMap(prefix => Transaction.put(key, prefix.value.getBytes("UTF-8")))
  }
```

The overloads that take extra services have the type parameters `[R, E, A]`, and the ones for a `Transaction`-only program have `[E, A]`. The compiler cannot always infer which one applies, and reports an ambiguous overload when it cannot, so pass the type arguments explicitly when that happens, as both examples above do. The resulting effect needs a `TransactionDB` and whatever else the program needs besides `Transaction`.

## Protecting a Read-Modify-Write Cycle

A transaction does not stop other writers from changing a key after it was read with `Transaction.get`. To lock the key for the rest of the transaction, read it with `Transaction.getForUpdate`. Its `exclusive` flag selects between an exclusive lock for a key that will be written and a shared lock for a key that is only read for validation.

A counter incremented from ten fibers at once is the classic case. Each fiber locks the key, reads it, and writes the next value, and the locks make the increments run one after the other:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.{ Transaction, TransactionDB }

val counterKey = "COUNT".getBytes(UTF_8)

val increment: ZIO[TransactionDB, Throwable, Unit] =
  TransactionDB.atomically[Throwable, Unit] {
    Transaction.getForUpdate(counterKey, exclusive = true).flatMap { current =>
      val next = current.map(new String(_, UTF_8).toInt).getOrElse(0) + 1
      Transaction.put(counterKey, next.toString.getBytes(UTF_8))
    }
  }

val program: ZIO[TransactionDB, Throwable, Option[String]] =
  for {
    _     <- ZIO.foreachParDiscard(0 until 10)(_ => increment)
    value <- TransactionDB.get(counterKey)
  } yield value.map(new String(_, UTF_8))
```

Running this program stores and returns `10`: no increment is lost. Without the exclusive lock, concurrent transactions could read the same value and write the same result.

## Handling Lock Conflicts

A transaction that asks for a key locked by another transaction waits for the lock. When the lock is not released within the lock timeout, the call fails with a `RocksDBException` whose message is `TimedOut(LockTimeout)`. The timeout comes from `TransactionDBOptions`, as in the options example above.

Since `TransactionDB.atomically` starts a fresh transaction on every run, retrying the whole call is safe. A retry policy on the `TransactionDB.atomically` effect handles conflicts without any state to reset:

```scala
import zio._
import zio.rocksdb.TransactionDB

def withRetries[A](update: ZIO[TransactionDB, Throwable, A]): ZIO[TransactionDB, Throwable, A] =
  update.retry(Schedule.recurs(3) && Schedule.spaced(50.millis))
```

## Managing a Transaction by Hand

When one transaction has to span several steps that `TransactionDB.atomically` cannot wrap, `TransactionDB.beginTransaction` returns a `Transaction` that lives as long as the enclosing `Scope`. Nothing is committed automatically: the program calls `Transaction#commit` itself, or `Transaction#rollback` to discard the changes. Leaving the scope without a commit closes the transaction and discards its writes:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.TransactionDB

def writeBoth(a: String, b: String): ZIO[TransactionDB, Throwable, Unit] =
  ZIO.scoped {
    for {
      tx <- TransactionDB.beginTransaction()
      _  <- tx.put("a".getBytes(UTF_8), a.getBytes(UTF_8))
      _  <- tx.put("b".getBytes(UTF_8), b.getBytes(UTF_8))
      _  <- tx.commit
    } yield ()
  }
```

The `Transaction` methods mirror the `Transaction` companion functions used in `TransactionDB.atomically`, so the same code can be moved between the two styles. Each `Transaction` serializes its operations with a semaphore, so it is safe to use one from parallel fibers, for example with `<&>`:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import zio._
import zio.rocksdb.{ Transaction, TransactionDB }

val writeTogether: ZIO[TransactionDB, Throwable, Unit] =
  TransactionDB.atomically[Throwable, Unit] {
    Transaction.put("key0".getBytes(UTF_8), "value0".getBytes(UTF_8)) <&>
      Transaction.put("key1".getBytes(UTF_8), "value1".getBytes(UTF_8))
  }
```

## Going Further

The operations that take a column family have variants with a `ColumnFamilyHandle` as the first argument on both `Transaction` and its companion object. To apply writes atomically without locking or reading, see [Write Batches](./write-batch.md).
