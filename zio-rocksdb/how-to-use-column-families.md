# How to Use Column Families

> Create column families, write to them, read from them and drop them with ZIO RocksDB.

A column family is a separate key space in one database. In this guide we create two families, write to each, read back and drop one. The [Column Families](./reference-column-families.md) reference describes every operation.

## Create the Families

Open a database and create the families at runtime. Keep the handles that `RocksDB.createColumnFamily` returns, because every per-family call needs one:

```scala
import java.nio.charset.StandardCharsets.UTF_8
import org.rocksdb.{ ColumnFamilyDescriptor, ColumnFamilyHandle, Options }
import zio._
import zio.rocksdb.RocksDB

val database: ZLayer[Any, Throwable, RocksDB] =
  RocksDB.live(new Options().setCreateIfMissing(true), "/tmp/zio-rocksdb-how-to-cf")

val families: ZIO[RocksDB, Throwable, (ColumnFamilyHandle, ColumnFamilyHandle)] =
  for {
    users  <- RocksDB.createColumnFamily(new ColumnFamilyDescriptor("users".getBytes(UTF_8)))
    orders <- RocksDB.createColumnFamily(new ColumnFamilyDescriptor("orders".getBytes(UTF_8)))
  } yield (users, orders)
```

## Write and Read

The same key can exist in both families with different values:

```scala
def program: ZIO[RocksDB, Throwable, (Option[String], Option[String])] =
  for {
    handles          <- families
    (users, orders)   = handles
    key               = "42".getBytes(UTF_8)
    _                <- RocksDB.put(users, key, "Ada".getBytes(UTF_8))
    _                <- RocksDB.put(orders, key, "2 books".getBytes(UTF_8))
    user             <- RocksDB.get(users, key)
    order            <- RocksDB.get(orders, key)
  } yield (user.map(new String(_, UTF_8)), order.map(new String(_, UTF_8)))
```

## Drop a Family

`RocksDB.dropColumnFamily` removes a family and its data:

```scala
def dropOrders(orders: ColumnFamilyHandle): ZIO[RocksDB, Throwable, Unit] =
  RocksDB.dropColumnFamily(orders)
```

:::note
A database that contains additional families can only be opened again if all of them are opened. See [How to Open Existing Column Families](./how-to-open-existing-column-families.md).
:::
