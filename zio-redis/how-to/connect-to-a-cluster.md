# How to Connect to a Redis Cluster

> This guide shows how to build a `Redis` layer for a Redis Cluster and how to keep related keys on one node. It assumes you know the basics from [Getting Started](../getting-started.md).

## Build the Layer

List a few startup nodes in a `RedisClusterConfig` and provide `Redis.cluster`. The client learns the remaining nodes from the cluster itself:

```scala
import zio._
import zio.redis._

val redis: ZLayer[Any, RedisError, Redis] =
  ZLayer.make[Redis](
    ZLayer.succeed(
      RedisClusterConfig(Chunk(RedisUri("redis-1.example.com:6379"), RedisUri("redis-2.example.com:6379")))
    ),
    ZLayer.succeed[CodecSupplier](CodecSupplier.utf8),
    Redis.cluster
  )
```

The layer provides `Redis` only. `AsyncRedis` and `RedisSubscription` have no cluster variant.

## Use Hash Tags for Multi-Key Commands

A command that touches several keys must target one slot, otherwise it fails with `RedisError.CrossSlot`. Redis hashes only the part of a key between `{` and `}`, so keys that share a tag share a slot:

```scala
import zio._
import zio.redis._

def addToBoth(redis: Redis): IO[RedisError, Long] =
  for {
    _     <- redis.sAdd("{user:1}:roles", "admin")
    _     <- redis.sAdd("{user:1}:teams", "core")
    union <- redis.sUnionStore("{user:1}:all", "{user:1}:roles", "{user:1}:teams")
  } yield union
```

## Next Steps

For authentication, TLS nodes, retry settings and limitations, see [Cluster](../concepts/cluster.md).
