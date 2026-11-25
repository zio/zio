# How to Choose Between RocksDB, TransactionDB and WriteBatch

> Decide whether plain RocksDB operations, a WriteBatch or a TransactionDB fits a write path.

ZIO RocksDB offers three ways to write. They differ in what they guarantee and what they cost. This guide helps you pick one for a given write path.

## Decide by the Question

| If you need to...                                                       | Use                                                        |
| ----------------------------------------------------------------------- | ---------------------------------------------------------- |
| write or read one key                                                   | `RocksDB.put`, `RocksDB.get`                               |
| write several keys with values you already know, all or nothing         | `WriteBatch` with `RocksDB#write`                          |
| read a value, then write based on it, without losing concurrent updates | `TransactionDB.atomically` with `Transaction.getForUpdate` |
| write and delete keys together                                          | `TransactionDB`, because `WriteBatch` has no delete        |
| hold a key's lock across several steps                                  | `TransactionDB.beginTransaction`                           |

## Costs

`RocksDB` operations and batches take no locks, so they are the cheapest. A batch buffers its writes in memory until `RocksDB#write`. A `TransactionDB` takes a lock for every key read with `Transaction#getForUpdate`, keeps the transaction's writes in memory until `Transaction#commit`, and can fail with a lock timeout under contention; see [How to Retry on a Lock Timeout](./how-to-retry-on-lock-timeout.md).

## TransactionDB Includes the Rest

`TransactionDB` extends `RocksDB`, so a program that needs transactions in one place can use a `TransactionDB` layer for everything. The reverse does not hold: a `RocksDB` layer cannot run transactions.

To see the subtyping at work, this function accepts a program that needs only `RocksDB` and runs it where a `TransactionDB` is provided:

```scala
import zio._
import zio.rocksdb.{ RocksDB, TransactionDB }

def readOnly(db: ZIO[RocksDB, Throwable, Unit]): ZIO[TransactionDB, Throwable, Unit] =
  db
```

For the details of each tool, read [Write Batches](./write-batch.md) and [Transactions](./transactions.md).
