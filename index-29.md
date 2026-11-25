# Introduction to ZIO Redis

> [![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-redis/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-redis_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-redis_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-redis_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-redis_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-redis_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-redis_2.13) [![ZIO Redis](https://img.shields.io/github/stars/zio/zio-redis?style=social)](https://github.com/zio/zio-redis)

## Introduction

[ZIO Redis](https://github.com/zio/zio-redis) is a ZIO-native Redis client.
It aims to provide a **type-safe** and **performant** API for accessing Redis
instances.

## Installation

ZIO Redis is published for Scala 2.12, 2.13 and 3 on the JVM (Java 17 or later). Add it to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  // ZIO Redis client (JVM)
  "dev.zio" %% "zio-redis" % "1.3.3"
)
```

Values are encoded and decoded by a `CodecSupplier`. The built-in `CodecSupplier.utf8` handles strings and numbers; to store case classes, add a ZIO Schema codec as described in [Codecs](./concepts/codecs.md).

## Modules

### Testing

`zio-redis-embedded` starts a real Redis process for the duration of a test, so specs run without Docker or a pre-installed server. Use the same version as `zio-redis`. See [How to Test With Embedded Redis](./how-to/test-with-embedded-redis.md).

```scala
libraryDependencies ++= Seq(
  // Embedded Redis server for tests (JVM)
  "dev.zio" %% "zio-redis-embedded" % "1.3.3" % Test
)
```

## Features

- **Type-safe commands.** Results are decoded to any type with a ZIO Schema `Schema`; see [Codecs](./concepts/codecs.md).
- **All major command groups.** Strings, keys, hashes, lists, sets, sorted sets, streams, geo, HyperLogLog and scripting; see the [command reference](./reference/commands/strings.md) pages.
- **Pub/Sub.** Subscriptions are exposed as ZIO streams; see [Pub/Sub](./concepts/pub-sub.md).
- **Pipelining.** `AsyncRedis` sends commands without waiting for replies; see [AsyncRedis](./concepts/async-redis.md).
- **Cluster support.** `Redis.cluster` follows slot redirections; see [Cluster](./concepts/cluster.md).
- **Embedded server for tests.** See [How to Test With Embedded Redis](./how-to/test-with-embedded-redis.md).

## Quick Example

To run an effect that uses Redis, provide the `Redis` layer. It needs a `CodecSupplier` and, for a local server, nothing else. See [Getting Started](./getting-started.md) for the details:

```scala
import zio._
import zio.redis._

object QuickExample extends ZIOAppDefault {
  val myApp: ZIO[Redis, RedisError, Unit] =
    for {
      redis <- ZIO.service[Redis]
      _     <- redis.set("myKey", 8L, Some(1.minute))
      v     <- redis.get("myKey").returning[Long]
      _     <- ZIO.debug(s"Value of myKey: $v")
      _     <- redis.hSet("myHash", ("k1", 6), ("k2", 2))
      _     <- redis.rPush("myList", 1, 2, 3, 4)
      _     <- redis.sAdd("mySet", "a", "b", "a", "c")
    } yield ()

  def run =
    myApp.provide(Redis.local, ZLayer.succeed[CodecSupplier](CodecSupplier.utf8))
}
```
