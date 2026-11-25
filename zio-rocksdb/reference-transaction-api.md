# Transaction API

> Reference for TransactionDB, Transaction and Atomically: constructors, atomically overloads, beginTransaction and every Transaction operation.

This page lists the types behind [Transactions](./transactions.md). `TransactionDB` is a `RocksDB` that can start transactions, and `Transaction` is one open transaction. The library uses RocksDB's pessimistic transaction API: reads for update take locks that last until the transaction ends.

## Opening a TransactionDB

`TransactionDB` extends `RocksDB`, so every operation on the [RocksDB page](./reference-rocksdb.md) works on it too. The constructors on the `TransactionDB` companion object return a layer that closes the database with its scope:

```scala
object TransactionDB {
  def live(options: Options, path: String): ZLayer[Any, Throwable, TransactionDB]
  def live(
    options: Options,
    transactionDBOptions: TransactionDBOptions,
    path: String
  ): ZLayer[Any, Throwable, TransactionDB]
  def liveAllColumnFamilies(
    options: DBOptions,
    columnFamilyOptions: ColumnFamilyOptions,
    transactionDBOptions: TransactionDBOptions,
    path: String
  ): ZLayer[Any, Throwable, TransactionDB]
}
```

There is no `live(path)` shorthand. Scoped, layer-free variants live in `TransactionDB.Live.open`, whose overloads take a path, `Options`, `TransactionDBOptions` or a list of column family descriptors.

## TransactionDB#atomically

`TransactionDB#atomically` runs a program inside a new transaction and commits when the program succeeds. It exists in four overloads, with and without `WriteOptions`, and for programs that need only `Transaction` or `Transaction` plus other services. Both the service methods and the companion accessors have them. The table shows the environment of the companion accessors; the service methods need only `Any` or `R`:

| Overload                                                  | Environment of the companion accessor's result |
| --------------------------------------------------------- | ---------------------------------------------- |
| `atomically[E, A](zio: ZIO[Transaction, E, A])`           | `TransactionDB`                                |
| `atomically[E, A](writeOptions)(zio)`                     | `TransactionDB`                                |
| `atomically[R, E, A](zio: ZIO[Transaction with R, E, A])` | `TransactionDB with R`                         |
| `atomically[R, E, A](writeOptions)(zio)`                  | `TransactionDB with R`                         |

`E` must be a supertype of `Throwable`. The overloads take an implicit marker, `Atomically.TransactionOnly` or `Atomically.TransactionWithSomething`, that the compiler finds by itself; it exists to tell the overloads apart. When type inference cannot choose, pass the type arguments explicitly.

## TransactionDB#beginTransaction

`TransactionDB#beginTransaction` returns a `Transaction` bound to the enclosing `Scope`. The overloads are `beginTransaction()` and `beginTransaction(writeOptions)`, as accessors on the companion object, and `beginTransaction` and `beginTransaction(writeOptions)` on the service. Nothing is committed automatically. Leaving the scope closes the transaction and discards uncommitted writes.

## Transaction Operations

Each operation exists as a method on the `Transaction` trait and as an accessor on the `Transaction` companion object, whose result requires `Transaction` in the environment.

| Operation                                       | Description                                          |
| ----------------------------------------------- | ---------------------------------------------------- |
| `get(key)`, `get(readOptions, key)`             | Reads a key; sees the transaction's own writes.      |
| `getForUpdate(key, exclusive)`                  | Reads a key and locks it until the transaction ends. |
| `getForUpdate(readOptions, key, exclusive)`     | The same with explicit `ReadOptions`.                |
| `getForUpdate(cf, key, exclusive)`              | The same in a column family.                         |
| `getForUpdate(readOptions, cf, key, exclusive)` | Column family and `ReadOptions`.                     |
| `put(key, value)`, `put(cf, key, value)`        | Writes a key.                                        |
| `delete(key)`, `delete(cf, key)`                | Deletes a key.                                       |
| `commit`                                        | Makes the writes visible.                            |
| `rollback`                                      | Discards the writes.                                 |
| `close`                                         | Releases the native transaction; cannot fail.        |

`Transaction#get` has no column family overload. All operations of one transaction are serialized by a semaphore, so one transaction can be shared by parallel fibers.

To run a program against a database handle you opened yourself, `Transaction.live(db)` and `Transaction.live(db, writeOptions)` return a `ZLayer[Any, Throwable, Transaction]` for a `rocksdb.TransactionDB` value. This is rarely needed, because `atomically` and `beginTransaction` do it for you.

To read a key for update inside a transaction that commits when the program succeeds, and to pass explicit `WriteOptions`:

```scala
import org.rocksdb.{ ReadOptions, WriteOptions }
import zio._
import zio.rocksdb.{ Transaction, TransactionDB }

val read: ZIO[TransactionDB, Throwable, Option[Array[Byte]]] =
  TransactionDB.atomically[Throwable, Option[Array[Byte]]](new WriteOptions().setSync(true)) {
    Transaction.getForUpdate(new ReadOptions(), "k".getBytes("UTF-8"), true)
  }
```
