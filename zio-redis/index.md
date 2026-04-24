# Introduction to ZIO Redis

> [![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-redis/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-redis_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-redis_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-redis_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-redis_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-redis-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-redis-docs_2.13) [![ZIO Redis](https://img.shields.io/github/stars/zio/zio-redis?style=social)](https://github.com/zio/zio-redis)

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-redis/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-redis_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-redis_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-redis_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-redis_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-redis-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-redis-docs_2.13) [![ZIO Redis](https://img.shields.io/github/stars/zio/zio-redis?style=social)](https://github.com/zio/zio-redis)

## Introduction

[ZIO Redis](https://github.com/zio/zio-redis) is a ZIO-native Redis client.
It aims to provide a **type-safe** and **performant** API for accessing Redis
instances.

## Installation

To use ZIO Redis, add the following line to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-redis" % "1.1.10"
```

## Example

To execute our ZIO Redis effect, we should provide the `RedisExecutor` layer to that effect. To create this layer we
should also provide the following layers:

- **RedisConfig** — Using default one, will connect to the `localhost:6379` Redis instance.
- **BinaryCodec** — In this example, we are going to use the built-in `ProtobufCodec` codec from zio-schema project.

To run this example we should put following dependencies in our `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-redis" % "1.1.10",
  "dev.zio" %% "zio-schema-protobuf" % "1.6.1"
)
```

```scala

object ZIORedisExample extends ZIOAppDefault {
  
  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }
  
  val myApp: ZIO[Redis, RedisError, Unit] = 
    for {
      redis <- ZIO.service[Redis]
      _     <- redis.set("myKey", 8L, Some(1.minutes))
      v     <- redis.get("myKey").returning[Long]
      _     <- Console.printLine(s"Value of myKey: $v").orDie
      _     <- redis.hSet("myHash", ("k1", 6), ("k2", 2))
      _     <- redis.rPush("myList", 1, 2, 3, 4)
      _     <- redis.sAdd("mySet", "a", "b", "a", "c")
    } yield ()

  override def run = 
    myApp.provide(Redis.local, ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier))
}
```

## Testing

To test you can use the embedded redis instance by adding to your build:

```scala
libraryDependencies := "dev.zio" %% "zio-redis-embedded" % "1.1.10"
```

Then you can supply `EmbeddedRedis.layer.orDie` as your `RedisConfig` and you're good to go!

```scala

object EmbeddedRedisSpec extends ZIOSpecDefault {
  object ProtobufCodecSupplier extends CodecSupplier {
    def get[A: Schema]: BinaryCodec[A] = ProtobufCodec.protobufCodec
  }
  
  final case class Item private (id: UUID, name: String, quantity: Int)
  object Item {
    implicit val itemSchema: Schema[Item] = DeriveSchema.gen[Item]
  }
  
  def spec = suite("EmbeddedRedis should")(
    test("set and get values") {
      for {
        redis <- ZIO.service[Redis]
        item   = Item(UUID.randomUUID, "foo", 2)
        _     <- redis.set(s"item.${item.id.toString}", item)
        found <- redis.get(s"item.${item.id.toString}").returning[Item]
      } yield assert(found)(isSome(equalTo(item)))
    }
  ).provideShared(
    EmbeddedRedis.layer,
    ZLayer.succeed[CodecSupplier](ProtobufCodecSupplier),
    Redis.singleNode
  ) @@ TestAspect.silentLogging
}
```

## Resources

- [ZIO Redis](https://www.youtube.com/watch?v=yqFt3b3RBkI) by Dejan Mijic — Redis is one of the most commonly used
  in-memory data structure stores. In this talk, Dejan will introduce ZIO Redis, a purely functional, strongly typed
  client library backed by ZIO, with excellent performance and extensive support for nearly all of Redis' features. He
  will explain the library design using the bottom-up approach - from communication protocol to public APIs. Finally, he
  will wrap the talk by demonstrating the client's usage and discussing its performance characteristics.
