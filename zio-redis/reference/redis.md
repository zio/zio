# Redis

> `Redis` is the service that exposes every Redis command except the subscription commands. `AsyncRedis` is the same service in pipelined form. Both are produced by the layers on this page, and both finish commands that return a value with a result builder.

## Services

Both services are aliases for `GenRedis`, which differs only in what each command returns:

```scala
type Redis      = GenRedis[GenRedis.Sync]
type AsyncRedis = GenRedis[GenRedis.Async]
```

A command on `Redis` returns `IO[RedisError, A]`. The same command on `AsyncRedis` returns `UIO[IO[RedisError, A]]`; see [AsyncRedis](../concepts/async-redis.md). Subscription commands live on a separate service, `RedisSubscription`; see [Pub/Sub](../concepts/pub-sub.md).

## Layers

`Redis.local` connects to `localhost:6379`. It needs a `CodecSupplier` and provides both services:

```scala
lazy val local: ZLayer[CodecSupplier, RedisError.IOError, Redis & AsyncRedis]
```

`Redis.singleNode` connects to the server described by a [`RedisConfig`](./configuration.md#redisconfig). It provides both services:

```scala
lazy val singleNode: ZLayer[CodecSupplier & RedisConfig, RedisError.IOError, Redis & AsyncRedis]
```

`Redis.cluster` connects to a Redis Cluster described by a `RedisClusterConfig`. It provides only `Redis`, and its error type is the general `RedisError`; see [Cluster](../concepts/cluster.md):

```scala
lazy val cluster: ZLayer[CodecSupplier & RedisClusterConfig, RedisError, Redis]
```

Every layer opens its connection when it is built and closes it when its scope ends. All three need a `CodecSupplier`; see [Codecs](../concepts/codecs.md).

## Result Builders

Redis stores bytes, so commands that return user data cannot know which type to decode to. They return a result builder, and the call is finished with `returning`. The number of type parameters depends on the shape of the reply:

| Builder                | Method                                  | Used by commands returning        |
| ---------------------- | --------------------------------------- | --------------------------------- |
| `ResultBuilder1`       | `returning[R: Schema]`                  | one value type, such as `Option[R]` or `Chunk[R]` |
| `ResultBuilder2`       | `returning[R1: Schema, R2: Schema]`     | two value types, such as `Map[R1, R2]` |
| `ResultBuilder3`       | `returning[R1: Schema, R2: Schema, R3: Schema]` | three value types, as in stream entries |
| `ResultOutputBuilder`  | `returning[R: Output]`                  | script results, decoded with an `Output` |
| `ResultStreamBuilder1` | `returning[R: Schema]`                  | a `Stream[RedisError, ...]`, as in subscriptions |

For example, `get` returns a `ResultBuilder1[Option, G]`, so its result type is chosen at the call site:

```scala
import zio._
import zio.redis._

def readName(redis: Redis): IO[RedisError, Option[String]] =
  redis.get("name").returning[String]

def readProfile(redis: Redis): IO[RedisError, Map[String, String]] =
  redis.hGetAll("profile").returning[String, String]
```

A builder cannot be used as an effect. Its `map` and `flatMap` require an implicit `NeedsReturnType`, which is never available, so forgetting `returning` is a compile error that reads "Use `returning[A]` to specify method's return type".
