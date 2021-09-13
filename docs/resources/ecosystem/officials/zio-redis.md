---
id: zio-redis
title: "ZIO Redis"
---

[ZIO Redis](https://github.com/zio/zio-redis) is a ZIO native Redis client.

## Introduction

ZIO Redis is in the experimental phase of development, but its goals are:

- **Type Safety**
- **Performance**
- **Minimum Dependency**
- **ZIO Native**

## Installation

Since the ZIO Redis is in the experimental phase, it is not released yet.

## Example

To execute our ZIO Redis effect, we should provide the `RedisExecutor` layer to that effect. To create this layer we should also provide the following layers:

- **Logging** — For simplicity, we ignored the logging functionality.
- **RedisConfig** — Using default one, will connect to the `localhost:6379` Redis instance.
- **Codec** — In this example, we are going to use the built-in `StringUtf8Codec` codec.

```scala
import zio.console.{Console, putStrLn}
import zio.duration._
import zio.logging.Logging
import zio.redis._
import zio.redis.codec.StringUtf8Codec
import zio.schema.codec.Codec
import zio.{ExitCode, URIO, ZIO, ZLayer}

object ZIORedisExample extends zio.App {

  val myApp: ZIO[Console with RedisExecutor, RedisError, Unit] = for {
    _ <- set("myKey", 8L, Some(1.minutes))
    v <- get[String, Long]("myKey")
    _ <- putStrLn(s"Value of myKey: $v").orDie
    _ <- hSet("myHash", ("k1", 6), ("k2", 2))
    _ <- rPush("myList", 1, 2, 3, 4)
    _ <- sAdd("mySet", "a", "b", "a", "c")
  } yield ()

  val layer: ZLayer[Any, RedisError.IOError, RedisExecutor] =
    Logging.ignore ++ ZLayer.succeed(RedisConfig.Default) ++ ZLayer.succeed(StringUtf8Codec) >>> RedisExecutor.live

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.provideCustomLayer(layer).exitCode
}
```
