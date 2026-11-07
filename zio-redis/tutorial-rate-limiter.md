# Tutorial: Build a Rate Limiter

> Welcome! In this tutorial we build a small rate limiter with ZIO Redis. By the end, you will have a service that allows at most a fixed number of requests per client in each time window, backed by a Redis counter that expires on its own.

We assume a Redis server on `localhost:6379` and the dependency from [Getting Started](./getting-started.md).

## How It Works

The limiter uses one counter per client and window. Each request increments the counter. The first request in a window also gives the counter an expiry equal to the window length, so Redis deletes it when the window ends. A request is allowed while the counter is at most the limit:

```
request ──> INCR rate:alice ──> count == 1? ──yes──> EXPIRE rate:alice window
                │
                └──> count <= limit ? allow : reject
```

## Step 1: Describe the Limiter

To keep the limiter independent of Redis, we start with a small service interface:

```scala
import zio._
import zio.redis._

trait RateLimiter {
  def allow(clientId: String): IO[RedisError, Boolean]
}
```

## Step 2: Implement It With INCR and EXPIRE

`Redis#incr` creates the key with value `1` when it is missing and returns the new count. When the count is `1`, we set the expiry with `Redis#expire`:

```scala
final case class RedisRateLimiter(redis: Redis, limit: Long, window: Duration) extends RateLimiter {
  def allow(clientId: String): IO[RedisError, Boolean] = {
    val key = s"rate:$clientId"

    for {
      count <- redis.incr(key)
      _     <- redis.expire(key, window).when(count == 1L)
    } yield count <= limit
  }
}
```

## Step 3: Provide a Layer

To make the limiter available to the rest of the program, we expose it as a layer:

```scala
object RateLimiter {
  def layer(limit: Long, window: Duration): URLayer[Redis, RateLimiter] =
    ZLayer.fromFunction((redis: Redis) => RedisRateLimiter(redis, limit, window): RateLimiter)
}
```

## Step 4: Use It

To run the limiter, we provide the `Redis` layer from [Getting Started](./getting-started.md) and call `RateLimiter#allow` repeatedly. With a limit of 3 per 10 seconds, the fourth call is rejected:

```scala
import zio._
import zio.redis._

object RateLimiterApp extends ZIOAppDefault {
  val program: ZIO[RateLimiter, RedisError, Unit] =
    ZIO.foreachDiscard(1 to 5) { i =>
      ZIO.serviceWithZIO[RateLimiter](_.allow("alice")).flatMap { ok =>
        ZIO.debug(s"request $i allowed: $ok")
      }
    }

  def run =
    program.provide(
      RateLimiter.layer(limit = 3, window = 10.seconds),
      Redis.local,
      ZLayer.succeed[CodecSupplier](CodecSupplier.utf8)
    )
}
```

## A Known Weakness

If the process fails between `Redis#incr` and `Redis#expire` on the first request, the counter never expires and the client stays blocked. To close that gap, move both steps into one Lua script; see [Scripting](./commands/scripting.md).

## Next Steps

- [How to Cache Values With a Time to Live](./how-to-cache-with-ttl.md)
- [How to Build a Distributed Lock](./how-to-distributed-lock.md)
