# How to Open Existing Column Families

> Reopen a RocksDB database that contains column families, either by listing them or by opening all of them.

RocksDB refuses to open a database unless every column family it contains is opened. In this guide we reopen a database that has families, first by naming them and then by opening whatever exists.

## Open by Name

List the families with `ColumnFamilyDescriptor`, always including `default`:

```scala
import org.rocksdb.{ ColumnFamilyDescriptor, DBOptions }
import zio._
import zio.rocksdb.RocksDB

val named: ZLayer[Any, Throwable, RocksDB] =
  RocksDB.live(
    new DBOptions(),
    "/tmp/zio-rocksdb-how-to-cf",
    List(
      new ColumnFamilyDescriptor("default".getBytes("UTF-8")),
      new ColumnFamilyDescriptor("users".getBytes("UTF-8")),
      new ColumnFamilyDescriptor("orders".getBytes("UTF-8"))
    )
  )
```

## Open Whatever Exists

When the names are not known in advance, `RocksDB.liveAllColumnFamilies` reads them from the database directory:

```scala
import org.rocksdb.{ ColumnFamilyOptions, DBOptions }
import zio.rocksdb.RocksDB

val all = RocksDB.liveAllColumnFamilies(new DBOptions(), new ColumnFamilyOptions(), "/tmp/zio-rocksdb-how-to-cf")
```

To only look at the names, call `RocksDB.Live.listColumnFamilies(options, path)`.

## Get the Handles

Per-family calls need handles. `RocksDB#initialHandles` returns an empty list for a `RocksDB` opened with descriptors, but returns the handles for a `TransactionDB`. For a `RocksDB`, create the families you need with `RocksDB.createColumnFamily` and keep the returned handles, as in [How to Use Column Families](./how-to-use-column-families.md).
