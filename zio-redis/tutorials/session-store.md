# Tutorial: Build a Session Store

> In this tutorial we build a small session store on Redis. A session is a case class that expires after thirty minutes without activity. By the end we can create, read, refresh and delete sessions. We assume the setup from [Getting Started](../getting-started.md).

## Step 1: Model the Session and Choose a Codec

A session is a case class, so it needs a `Schema` and a structural codec; see [Codecs](../concepts/codecs.md). We use Protobuf from `zio-schema-protobuf`:

```scala
import zio._
import zio.redis._
import zio.schema.{DeriveSchema, Schema}
import zio.schema.codec.{BinaryCodec, ProtobufCodec}

final case class Session(userId: Long, name: String)

object Session {
  implicit val schema: Schema[Session] = DeriveSchema.gen[Session]
}

object ProtobufSupplier extends CodecSupplier {
  def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
}
```

## Step 2: Write the Store

The store keeps each session under the key `session:<id>`. `create` writes a session that Redis deletes after thirty minutes. `find` reads it back. `touch` pushes the expiry forward and answers whether the session still existed. `destroy` removes it:

```scala
final class SessionStore(redis: Redis) {
  private val timeout = 30.minutes

  private def key(id: String): String = s"session:$id"

  def create(session: Session): IO[RedisError, String] =
    for {
      id <- Random.nextUUID.map(_.toString)
      _  <- redis.set(key(id), session, Some(timeout))
    } yield id

  def find(id: String): IO[RedisError, Option[Session]] =
    redis.get(key(id)).returning[Session]

  def touch(id: String): IO[RedisError, Boolean] =
    redis.expire(key(id), timeout)

  def destroy(id: String): IO[RedisError, Boolean] =
    redis.del(key(id)).map(_ > 0)
}

object SessionStore {
  val layer: URLayer[Redis, SessionStore] =
    ZLayer.fromFunction(new SessionStore(_))
}
```

`Redis#expire` returns `false` when the key does not exist, which is how `touch` tells an expired session from a live one.

## Step 3: Run It

We provide the store, the `Redis` layer and the codec, then exercise each operation:

```scala
object SessionApp extends ZIOAppDefault {
  val program: ZIO[SessionStore, RedisError, Unit] =
    for {
      store   <- ZIO.service[SessionStore]
      id      <- store.create(Session(1L, "Ada"))
      found   <- store.find(id)
      _       <- ZIO.debug(s"found: $found")
      touched <- store.touch(id)
      _       <- ZIO.debug(s"touched: $touched")
      _       <- store.destroy(id)
      gone    <- store.find(id)
      _       <- ZIO.debug(s"after destroy: $gone")
    } yield ()

  def run =
    program.provide(
      SessionStore.layer,
      Redis.local,
      ZLayer.succeed[CodecSupplier](ProtobufSupplier)
    )
}
```

## What We Learned

We stored a case class with a structural codec, gave it an expiry with `Redis#set`, refreshed the expiry with `Redis#expire`, and removed it with `Redis#del`. To go further, see [How to Cache Values With a Time to Live](../how-to/cache-with-ttl.md) and [How to Build a Distributed Lock](../how-to/distributed-lock.md).
