# Codecs

> Every key, field and value that crosses the wire is encoded to bytes by a `BinaryCodec` from ZIO Schema. A `CodecSupplier` tells ZIO Redis which codec to use for a given `Schema`, and the `Redis` layers require one.

## CodecSupplier

`CodecSupplier` is a trait with a single method that returns a codec for any type that has a `Schema`:

```scala
trait CodecSupplier {
  def get[A: Schema]: BinaryCodec[A]
}
```

Two options are available: the built-in UTF-8 supplier and any ZIO Schema codec such as Protobuf.

## The UTF-8 Supplier

`CodecSupplier.utf8` writes primitives as their plain text form, which is what other Redis clients and `redis-cli` expect. It supports primitive types only. Decoding converts `Int`, `Long` and `Double` numerically and returns every other primitive as a `String`. Encoding a case class fails with a `RedisError.CodecError`:

```scala
import zio._
import zio.redis._

val supplier: ULayer[CodecSupplier] =
  ZLayer.succeed[CodecSupplier](CodecSupplier.utf8)
```

## Protobuf and Other Codecs

To store case classes, implement `CodecSupplier` with a ZIO Schema codec. This supplier uses Protobuf, from the `zio-schema-protobuf` dependency listed in [Getting Started](./getting-started.md):

```scala
import zio._
import zio.redis._
import zio.schema.{DeriveSchema, Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}

object ProtobufCodecSupplier extends CodecSupplier {
  def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
}
```

Any other ZIO Schema codec, such as JSON or Avro, plugs in the same way.

## Storing Your Own Types

With a structural codec in place, a case class only needs a `Schema`. To write and read a case class:

```scala
import zio._
import zio.redis._
import zio.schema.{DeriveSchema, Schema}

final case class User(id: Long, name: String)

object User {
  implicit val schema: Schema[User] = DeriveSchema.gen[User]
}

def saveAndLoad(redis: Redis): IO[RedisError, Option[User]] =
  for {
    _    <- redis.set("user:1", User(1L, "Ada"))
    user <- redis.get("user:1").returning[User]
  } yield user
```

Keys use the codec too, so `redis.set("user:1", ...)` encodes the key `"user:1"` with the supplier. The same supplier must be used for writes and reads. Values written by one codec cannot be read by another.
