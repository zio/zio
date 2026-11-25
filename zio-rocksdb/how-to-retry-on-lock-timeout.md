# How to Retry on a Lock Timeout

> Retry a TransactionDB.atomically call when another transaction holds a lock for too long.

When two transactions want the same key, the second waits for the lock. If the wait exceeds the lock timeout, the call fails with a `RocksDBException` whose message is `TimedOut(LockTimeout)`. In this guide we retry only that failure.

## Detect the Timeout

The failure is a plain `RocksDBException` with a status code, so match on its status:

```scala
import org.rocksdb.{ RocksDBException, Status }
import zio._
import zio.rocksdb.TransactionDB

def isLockTimeout(error: Throwable): Boolean =
  error match {
    case e: RocksDBException =>
      Option(e.getStatus).exists(_.getCode == Status.Code.TimedOut)
    case _ => false
  }
```

## Retry the Whole Call

`TransactionDB.atomically` starts a new transaction on every run, so retrying the effect is safe. Use a schedule with a short delay, and retry only while the failure is a lock timeout:

```scala
def retrying[A](update: ZIO[TransactionDB, Throwable, A]): ZIO[TransactionDB, Throwable, A] =
  update.retry(Schedule.recurs(5) && Schedule.exponential(20.millis) && Schedule.recurWhile[Throwable](isLockTimeout))
```

## Tune the Timeout

The wait time comes from `TransactionDBOptions`. A longer timeout makes retries rarer and slows down the failing call:

```scala
import org.rocksdb.{ Options, TransactionDBOptions }

val database =
  TransactionDB.live(
    new Options().setCreateIfMissing(true),
    new TransactionDBOptions().setTransactionLockTimeout(5000),
    "/tmp/zio-rocksdb-lock-timeout"
  )
```

See [Transactions](./transactions.md) for how locks are taken with `Transaction#getForUpdate`.
