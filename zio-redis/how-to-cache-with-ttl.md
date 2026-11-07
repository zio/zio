# How to Cache Values With a Time to Live

> This guide shows how to use Redis as a cache that expires entries automatically, and how to load a missing entry on demand. It assumes a `Redis` layer as described in [Getting Started](./getting-started.md).

## Store a Value With an Expiry

To store a value that Redis deletes after one minute, pass an expiry to `Redis#set`:

```scala
import zio._
import zio.redis._

def cachePage(redis: Redis, url: String, html: String): IO[RedisError, Boolean] =
  redis.set(s"page:$url", html, Some(1.minute))
```

The result is `true` when Redis stored the value. `Redis#setEx` is an equivalent command with the key, the expiry and the value as positional arguments.

## Read the Remaining Lifetime

To see how long an entry has left, call `Redis#ttl`. It returns a `Duration`:

```scala
import zio._
import zio.redis._

def remaining(redis: Redis, url: String): IO[RedisError, Duration] =
  redis.ttl(s"page:$url")
```

## Load on a Cache Miss

To return the cached value when it exists and otherwise compute and store it, combine `Redis#get` with `Redis#set`:

```scala
import zio._
import zio.redis._

def cached[E](redis: Redis, key: String, ttl: Duration)(
  load: => IO[E, String]
): IO[Either[RedisError, E], String] =
  redis.get(key).returning[String].mapError(Left(_)).flatMap {
    case Some(hit) => ZIO.succeed(hit)
    case None      =>
      for {
        value <- load.mapError(Right(_))
        _     <- redis.set(key, value, Some(ttl)).mapError(Left(_))
      } yield value
  }
```

Two callers that miss at the same time both run `load`. To prevent that, guard the load with a lock as shown in [How to Build a Distributed Lock](./how-to-distributed-lock.md).

## Invalidate an Entry

To remove an entry before it expires, delete its key with `Redis#del`:

```scala
import zio._
import zio.redis._

def invalidate(redis: Redis, url: String): IO[RedisError, Long] =
  redis.del(s"page:$url")
```

`Redis#del` returns the number of keys that existed.
