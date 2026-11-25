# How to Test With Embedded Redis

> Tests that talk to Redis need a server. The `zio-redis-embedded` module starts a real Redis process for the duration of a test and shuts it down afterwards, so specs run without Docker or a pre-installed server.

## Prerequisites

Add `zio-redis-embedded` to your test dependencies, as shown in [Modules](../index.md#modules).

## Starting the Embedded Server

`EmbeddedRedis.layer` starts a Redis server on a free port and produces a `RedisConfig` for it. See [EmbeddedRedis](../reference/embedded-redis.md#layer) for the type and lifecycle.

## Writing a Spec

The spec below builds a `Redis` service on top of the embedded server and exercises it. The layer is provided with `provideShared`, so one server is started for the whole suite:

```scala
import zio._
import zio.redis._
import zio.redis.embedded.EmbeddedRedis
import zio.test._

object EmbeddedRedisSpec extends ZIOSpecDefault {
  def spec: Spec[Any, Any] =
    suite("EmbeddedRedis")(
      test("stores and reads back a value") {
        for {
          redis <- ZIO.service[Redis]
          _     <- redis.set("greeting", "hello")
          value <- redis.get("greeting").returning[String]
        } yield assertTrue(value.contains("hello"))
      },
      test("counts with incr") {
        for {
          redis <- ZIO.service[Redis]
          one   <- redis.incr("counter")
          two   <- redis.incr("counter")
        } yield assertTrue(one == 1L, two == 2L)
      }
    ).provideShared(
      EmbeddedRedis.layer,
      ZLayer.succeed[CodecSupplier](CodecSupplier.utf8),
      Redis.singleNode
    ) @@ TestAspect.sequential
}
```

`CodecSupplier.utf8` is enough for primitive values. To store case classes, supply a structural codec instead, as shown in [Codecs](../concepts/codecs.md).

## Keeping Tests Independent

All tests in a suite share one server, so data written by one test is visible to the next. There are two ways to avoid interference:

- Use unique keys per test, for example by prefixing them with a random UUID from `Random.nextUUID`.
- Call `Redis#flushall` at the start of each test, and run the suite with `TestAspect.sequential`.

To clear the database before each test:

```scala
import zio._
import zio.redis._
import zio.redis.embedded.EmbeddedRedis
import zio.test._

object CleanSpec extends ZIOSpecDefault {
  def spec: Spec[Any, Any] =
    (suite("clean database")(
      test("sees only its own key") {
        for {
          redis <- ZIO.service[Redis]
          _     <- redis.set("key", "value")
          keys  <- redis.keys("*").returning[String]
        } yield assertTrue(keys.size == 1)
      }
    ) @@ TestAspect.before(ZIO.serviceWithZIO[Redis](_.flushall(sync = true))) @@ TestAspect.sequential).provideShared(
      EmbeddedRedis.layer,
      ZLayer.succeed[CodecSupplier](CodecSupplier.utf8),
      Redis.singleNode
    )
}
```

## Testing Pub/Sub

`RedisSubscription.singleNode` accepts the same `RedisConfig`, so the embedded server also works for subscription tests. Provide both layers from one `EmbeddedRedis.layer`, which `provideShared` shares between them. See [Pub/Sub](../concepts/pub-sub.md) for the services.

## Limitations

The embedded server is a single node. Tests of [Cluster](../concepts/cluster.md) behavior need a real cluster, which the library's own integration tests start with Docker Compose. The Redis version is whatever the embedded library ships, so it can differ from your production version.
