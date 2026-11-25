# Column Families

> Reference for RocksDB column families in ZIO RocksDB: opening with descriptors, opening all existing families, creating and dropping at runtime, and the per-family overloads.

A column family is a separate key space inside one RocksDB database, with its own options. Keys in different families do not collide, and a [write batch](./write-batch.md) or a [transaction](./transactions.md) can span several families atomically. Every database has a `default` family, which the operations without a handle use. This page describes how to open, create, drop and address column families.

## Handles and Descriptors

Two `rocksdbjni` types appear in the API. A `ColumnFamilyDescriptor` names a family and carries its `ColumnFamilyOptions`, and is the input when opening or creating. A `ColumnFamilyHandle` is the output, an open reference to a family, which is passed as the first argument of the per-family overloads.

## Creating and Dropping at Runtime

`RocksDB#createColumnFamily` creates one family and returns its handle. `RocksDB#createColumnFamilies` creates several, either from descriptors or from one shared `ColumnFamilyOptions` and a list of names. `RocksDB#dropColumnFamily` and `RocksDB#dropColumnFamilies` delete families. All have a companion accessor:

```scala
import org.rocksdb.{ ColumnFamilyDescriptor, ColumnFamilyHandle, ColumnFamilyOptions }
import zio._
import zio.rocksdb.RocksDB

val create: ZIO[RocksDB, Throwable, (ColumnFamilyHandle, List[ColumnFamilyHandle])] =
  for {
    users <- RocksDB.createColumnFamily(new ColumnFamilyDescriptor("users".getBytes("UTF-8")))
    more  <- RocksDB.createColumnFamilies(new ColumnFamilyOptions(), List("orders".getBytes("UTF-8"), "audit".getBytes("UTF-8")))
  } yield (users, more)

def drop(handle: ColumnFamilyHandle): ZIO[RocksDB, Throwable, Unit] =
  RocksDB.dropColumnFamily(handle)
```

Dropping a family deletes its data. Keep the handles that the create calls return: they are needed for every per-family operation.

## Opening with Known Families

To open a database whose families you know, pass the descriptors to `RocksDB.live`. A descriptor for `default` has to be in the list when the database already contains other families:

```scala
import org.rocksdb.{ ColumnFamilyDescriptor, DBOptions }
import zio._
import zio.rocksdb.RocksDB

val database: ZLayer[Any, Throwable, RocksDB] =
  RocksDB.live(
    new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true),
    "/tmp/zio-rocksdb-cf",
    List(
      new ColumnFamilyDescriptor("default".getBytes("UTF-8")),
      new ColumnFamilyDescriptor("users".getBytes("UTF-8"))
    )
  )
```

`RocksDB#initialHandles` returns the handles the database was opened with, as a `Task[List[ColumnFamilyHandle]]`.

:::warning
For a `RocksDB` opened through `RocksDB.live` with descriptors, `RocksDB#initialHandles` currently returns an empty list. For a `TransactionDB` it returns the handles. Until this is fixed, create the families with `RocksDB.createColumnFamily` and keep the returned handles.
:::

## Opening All Existing Families

`RocksDB.liveAllColumnFamilies` lists the families that exist at the path and opens all of them, so the program does not have to know the names. `RocksDB.Live.listColumnFamilies(options, path)` returns the raw names without opening the database. `TransactionDB.liveAllColumnFamilies` does the same for a [TransactionDB](./reference-transaction-api.md):

```scala
import org.rocksdb.{ ColumnFamilyOptions, DBOptions }
import zio.rocksdb.RocksDB

val database = RocksDB.liveAllColumnFamilies(new DBOptions(), new ColumnFamilyOptions(), "/tmp/zio-rocksdb-cf")
```

## Per-Family Operations

Most operations that read or write have a variant with a `ColumnFamilyHandle` as the first argument. `Transaction#get` is the exception:

| Operation                                                           | Variant                                                                                        |
| ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `RocksDB#put`                                                       | `put(cfHandle, key, value)`                                                                    |
| `RocksDB#get`                                                       | `get(cfHandle, key)`                                                                           |
| `RocksDB#delete`                                                    | `delete(cfHandle, key)`                                                                        |
| `RocksDB#multiGetAsList`                                            | `multiGetAsList(handles, keys)`                                                                |
| `RocksDB#newIterator`                                               | `newIterator(cfHandle)`, `newIterator(cfHandle, direction, position)`, `newIterators(handles)` |
| `RocksDB#flush`                                                     | `flush(options, cfHandle)`, `flush(options, handles)`                                          |
| `WriteBatch#put`                                                    | `put(cfHandle, key, value)`                                                                    |
| `Transaction#put`, `Transaction#getForUpdate`, `Transaction#delete` | variants taking a handle first; `Transaction#get` has no column family variant                 |

For example, a value written to one family is not visible in another:

```scala
import org.rocksdb.ColumnFamilyHandle
import zio._
import zio.rocksdb.RocksDB

def isolated(a: ColumnFamilyHandle, b: ColumnFamilyHandle): ZIO[RocksDB, Throwable, Option[Array[Byte]]] =
  RocksDB.put(a, "k".getBytes("UTF-8"), "v".getBytes("UTF-8")) *> RocksDB.get(b, "k".getBytes("UTF-8"))
```

The result is `None`. See [How to Use Column Families](./how-to-use-column-families.md) for a worked task.
