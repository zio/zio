# Introduction to ZIO Redis

> [![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-redis/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-redis_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-redis_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-redis_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-redis_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-redis_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-redis_2.13) [![ZIO Redis](https://img.shields.io/github/stars/zio/zio-redis?style=social)](https://github.com/zio/zio-redis)

## Introduction

[ZIO Redis](https://github.com/zio/zio-redis) is a ZIO-native Redis client.
It aims to provide a **type-safe** and **performant** API for accessing Redis
instances.

## Installation

To use ZIO Redis, add the following line to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-redis" % "1.3.2"
```

## Features

- **Type-safe commands.** Results are decoded to any type with a ZIO Schema `Schema`; see [Codecs](./codecs.md).
- **All major command groups.** Strings, keys, hashes, lists, sets, sorted sets, streams, geo, HyperLogLog and scripting; see the command reference pages.
- **Pub/Sub.** Subscriptions are exposed as ZIO streams; see [Pub/Sub](./pub-sub.md).
- **Pipelining.** `AsyncRedis` sends commands without waiting for replies; see [AsyncRedis](./async-redis.md).
- **Cluster support.** `Redis.cluster` follows slot redirections; see [Cluster](./cluster.md).
- **Embedded server for tests.** See [Testing](./testing.md).

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

## Resources

- [ZIO Redis](https://www.youtube.com/watch?v=yqFt3b3RBkI) by Dejan Mijic — Redis is one of the most commonly used
  in-memory data structure stores. In this talk, Dejan will introduce ZIO Redis, a purely functional, strongly typed
  client library backed by ZIO, with excellent performance and extensive support for nearly all of Redis' features. He
  will explain the library design using the bottom-up approach - from communication protocol to public APIs. Finally, he
  will wrap the talk by demonstrating the client's usage and discussing its performance characteristics.
