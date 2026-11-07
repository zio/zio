# Getting Started

> ZIO Redis exposes every Redis command as a method on the `Redis` service. This page shows how to build the `Redis` layer, run a first program, and read results back with `returning`.

## Installation

To use ZIO Redis, add the following to `build.sbt`. The example also uses the Protobuf codec from ZIO Schema:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-redis"           % "1.3.2",
  "dev.zio" %% "zio-schema-protobuf" % "1.9.0"
)
```

## The Redis Service

The library defines two service types, both built from the same layers:

| Type         | Command return type              | Use it to                           |
| ------------ | -------------------------------- | ----------------------------------- |
| `Redis`      | `IO[RedisError, A]`              | Run commands one after another      |
| `AsyncRedis` | `UIO[IO[RedisError, A]]`         | Send commands without waiting       |

Most programs only need `Redis`. See [AsyncRedis](./async-redis.md) for the second one.

## Building the Layer

A `Redis` layer needs a [`CodecSupplier`](./codecs.md) and a connection. The following layers create it:

| Layer                | Requires                              | Connects to                                |
| -------------------- | ------------------------------------- | ------------------------------------------ |
| `Redis.local`        | `CodecSupplier`                       | `localhost:6379`                           |
| `Redis.singleNode`   | `CodecSupplier` and `RedisConfig`     | The single server described by the config  |
| `Redis.cluster`      | `CodecSupplier` and `RedisClusterConfig` | A Redis Cluster (see [Cluster](./cluster.md)) |

To use the built-in UTF-8 codec supplier against a local server:

```scala
import zio._
import zio.redis._

val layer: ZLayer[Any, RedisError.IOError, Redis] =
  ZLayer.succeed[CodecSupplier](CodecSupplier.utf8) >>> Redis.local
```

## Running a First Program

The program below stores a value with an expiry and reads it back. Commands such as `Redis#set` and `Redis#get` are methods on the service, so we obtain it with `ZIO.service`:

```scala
import zio._
import zio.redis._

object FirstProgram extends ZIOAppDefault {
  val program: ZIO[Redis, RedisError, Unit] =
    for {
      redis <- ZIO.service[Redis]
      _     <- redis.set("greeting", "hello", Some(1.minute))
      value <- redis.get("greeting").returning[String]
      _     <- ZIO.debug(s"greeting = $value")
    } yield ()

  def run =
    program.provide(Redis.local, ZLayer.succeed[CodecSupplier](CodecSupplier.utf8))
}
```

## Choosing the Return Type

Redis stores bytes, so ZIO Redis cannot know which type a value should decode to. Commands that return user data therefore return a builder, and we finish the call with `returning[A]`, where `A` has a ZIO Schema `Schema`:

```scala
import zio._
import zio.redis._

def readCounter(redis: Redis): IO[RedisError, Option[Long]] =
  redis.get("counter").returning[Long]
```

Calling `redis.get("counter")` without `returning` does not compile. The compiler reports "Use `returning[A]` to specify method's return type".

Keys, field names and values are all encoded with the `CodecSupplier`, so the same codec must be used to write and to read a value.

## Next Steps

- [Configuration](./configuration.md) explains `RedisConfig`, `RedisUri`, authentication and TLS.
- [Codecs](./codecs.md) explains how values are encoded.
- [Error Handling](./error-handling.md) lists every `RedisError`.
