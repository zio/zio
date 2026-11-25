# How to Pipeline Commands

> This guide shows how to send many commands without waiting for each reply, which cuts the cost of network round trips for bulk work. The difference between `Redis` and `AsyncRedis` is explained in [AsyncRedis](../concepts/async-redis.md).

## Get the Async Service

`Redis.local` and `Redis.singleNode` provide `AsyncRedis` next to `Redis`; `Redis.cluster` does not, so pipelining is available on single nodes only. Build the layers as described in [Redis](../reference/redis.md#layers).

## Queue a Batch and Await the Replies

Every `AsyncRedis` command returns an `UIO` whose result is the effect that waits for the reply. Queue all commands first, then await the inner effects together:

```scala
import zio._
import zio.redis._

def writeAll(redis: AsyncRedis, entries: Chunk[(String, Long)]): IO[RedisError, Unit] =
  for {
    pending <- ZIO.foreach(entries) { case (key, value) => redis.set(key, value) }
    _       <- ZIO.collectAllDiscard(pending)
  } yield ()
```

The first `foreach` puts every command on the wire. The second step waits for the replies, so the batch pays for one round trip rather than one per command.

## Handle Errors

Queuing never fails. A failure of one command shows up when its inner effect is awaited, so call `.either` on each inner effect when one bad command must not hide the other results:

```scala
import zio._
import zio.redis._

def readAll(redis: AsyncRedis, keys: Chunk[String]): UIO[Chunk[Either[RedisError, Option[Long]]]] =
  for {
    pending <- ZIO.foreach(keys)(key => redis.get(key).returning[Long])
    results <- ZIO.foreach(pending)(_.either)
  } yield results
```
