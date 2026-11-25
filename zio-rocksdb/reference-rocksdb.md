# RocksDB

> Reference for the RocksDB service: opening a database, reading, writing, scanning, flushing and column family operations, and which operations have companion accessors.

`RocksDB` is the service trait that represents an open database. Every operation takes and returns `Array[Byte]` and fails with `Throwable`, usually a `RocksDBException`. This page lists the constructors and operations; [Serialization](./serialization.md) covers typed keys and values.

## Opening a Database

Each constructor on the `RocksDB` companion object returns a `ZLayer[Any, Throwable, RocksDB]` that opens the database when the layer is built and closes it when the enclosing `Scope` ends:

```scala
object RocksDB {
  def live(path: String): ZLayer[Any, Throwable, RocksDB]
  def live(options: Options, path: String): ZLayer[Any, Throwable, RocksDB]
  def live(
    options: DBOptions,
    path: String,
    cfDescriptors: List[ColumnFamilyDescriptor]
  ): ZLayer[Any, Throwable, RocksDB]
  def liveAllColumnFamilies(
    options: DBOptions,
    columnFamilyOptions: ColumnFamilyOptions,
    path: String
  ): ZLayer[Any, Throwable, RocksDB]
}
```

`RocksDB.live(path)` and `RocksDB.live(options, path)` open the default column family. The other two open a list of [column families](./reference-column-families.md). To open a database without a layer, use the scoped `RocksDB.Live.open` overloads, which return a `ZIO[Scope, Throwable, RocksDB]`:

```scala
import org.rocksdb.Options
import zio._
import zio.rocksdb.RocksDB

val program: ZIO[Scope, Throwable, Option[Array[Byte]]] =
  for {
    db    <- RocksDB.Live.open(new Options().setCreateIfMissing(true), "/tmp/zio-rocksdb-reference")
    _     <- db.put("k".getBytes("UTF-8"), "v".getBytes("UTF-8"))
    found <- db.get("k".getBytes("UTF-8"))
  } yield found
```

## Reading and Writing

| Method                                         | Description                                          |
| ---------------------------------------------- | ---------------------------------------------------- |
| `put(key, value)`, `put(cfHandle, key, value)` | Writes a key.                                        |
| `get(key)`, `get(cfHandle, key)`               | Reads a key; `None` when it does not exist.          |
| `delete(key)`, `delete(cfHandle, key)`         | Deletes a key; deleting a missing key succeeds.      |
| `multiGetAsList(keys)`                         | Reads several keys from the default column family.   |
| `multiGetAsList(handles, keys)`                | Reads several keys, each from its own column family. |
| `write(writeOptions, batch)`                   | Applies a [WriteBatch](./write-batch.md) atomically. |

`RocksDB#multiGetAsList` returns a list that lines up positionally with the keys, holding `None` for each missing key. The overload with handles pairs the handle at index `i` with the key at index `i`.

## Scanning

| Method                                       | Description                                                           |
| -------------------------------------------- | --------------------------------------------------------------------- |
| `newIterator`                                | All entries of the default column family, forward from the first key. |
| `newIterator(direction, position)`           | The default column family with an explicit start and direction.       |
| `newIterator(cfHandle)`                      | All entries of a column family.                                       |
| `newIterator(cfHandle, direction, position)` | A column family with an explicit start and direction.                 |
| `newIterators(cfHandles)`                    | One entry stream per column family, each forward from the first key.  |

Each stream opens its native iterator when it starts and closes it when it ends, fails or is interrupted. [Iterators and Range Scans](./iterators.md) explains `Position`, `Direction` and prefix scans.

## Durability

| Method                           | Description                                                                       |
| -------------------------------- | --------------------------------------------------------------------------------- |
| `flush(flushOptions)`            | Writes the in-memory tables of the default column family to disk.                 |
| `flush(flushOptions, cfHandle)`  | The same for one column family.                                                   |
| `flush(flushOptions, cfHandles)` | The same for a list of column families.                                           |
| `flushWal(sync)`                 | Moves the buffered write-ahead log to its file, and syncs it when `sync` is true. |

## Column Families

`RocksDB#createColumnFamily`, `RocksDB#createColumnFamilies`, `RocksDB#dropColumnFamily`, `RocksDB#dropColumnFamilies` and `RocksDB#initialHandles` manage column families at runtime. They are described on the [Column Families](./reference-column-families.md) page.

## Companion Accessors

The `RocksDB` companion object extends `Operations[RocksDB]`, which provides an accessor with the same name for most operations, so that `RocksDB.put(key, value)` is a `ZIO[RocksDB, Throwable, Unit]`. Some operations have no accessor and are called on the service, for example with `ZIO.serviceWithZIO[RocksDB]`:

| Operation                                          | Accessor? |
| -------------------------------------------------- | --------- |
| `put`, `get`, `delete` (both variants)             | Yes       |
| `multiGetAsList` (both variants)                   | Yes       |
| `newIterator`, `newIterator(cfHandle)`             | Yes       |
| `newIterator(cfHandle, direction, position)`       | Yes       |
| `newIterator(direction, position)`                 | No        |
| `newIterators`                                     | Yes       |
| `createColumnFamily(ies)`, `dropColumnFamily(ies)` | Yes       |
| `flush`, `flushWal`, `write`, `initialHandles`     | No        |

`TransactionDB` extends `RocksDB` and has the same accessors on its own companion object; see the [Transaction API](./reference-transaction-api.md).
