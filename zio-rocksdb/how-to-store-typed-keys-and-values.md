# How to Store Typed Keys and Values

> Wrap RocksDB get and put in a typed store that encodes and decodes with a Serde.

In this guide we write a small `TypedStore[K, V]` that hides the byte conversion, so the rest of the program deals only with domain types. [Serialization](./serialization.md) explains how to build the `Serde` values.

## Define the Store

The store holds a key `Serde`, a value `Serde` and the database. Decoding failures are turned into a `Throwable` so they fit the `Task` of the database calls:

```scala
import zio._
import zio.rocksdb._

final class TypedStore[K, V](keys: Serde[Any, K], values: Serde[Any, V], db: RocksDB) {
  private def fail(error: DeserializeError): Throwable = new RuntimeException(error.message)

  def put(key: K, value: V): Task[Unit] =
    for {
      k <- keys(key)
      v <- values(value)
      _ <- db.put(k.toArray, v.toArray)
    } yield ()

  def get(key: K): Task[Option[V]] =
    for {
      k     <- keys(key)
      found <- db.get(k.toArray)
      value <- ZIO.foreach(found)(bytes => values.decode(Chunk.fromArray(bytes)).mapBoth(fail, _.value))
    } yield value

  def delete(key: K): Task[Unit] =
    keys(key).flatMap(k => db.delete(k.toArray))
}
```

## Build It as a Layer

Create the store from the `RocksDB` service:

```scala
def layer[K: Tag, V: Tag](keys: Serde[Any, K], values: Serde[Any, V]): ZLayer[RocksDB, Nothing, TypedStore[K, V]] =
  ZLayer.fromZIO(ZIO.service[RocksDB].map(new TypedStore(keys, values, _)))

val longStore: ZLayer[RocksDB, Nothing, TypedStore[Long, Long]] =
  layer(Serde(Serializer.long, Deserializer.long), Serde(Serializer.long, Deserializer.long))
```

Use one store per key and value type, and one database layer underneath, so that all stores share the same open database.
