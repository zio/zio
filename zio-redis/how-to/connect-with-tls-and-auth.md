# How to Connect With TLS and Authentication

> This guide shows how to connect to a managed Redis service that requires a password and TLS, reading the settings from environment variables. All fields are described in [Configuration](../reference/configuration.md).

## Read the Settings From the Environment

Build the `RedisConfig` with `ZLayer.fromZIO`, so the settings are read when the layer is built. A missing host or port falls back to a default, and a missing password or user leaves `auth` unset:

```scala
import zio._
import zio.redis._

val config: ZLayer[Any, SecurityException, RedisConfig] =
  ZLayer.fromZIO {
    for {
      host     <- System.envOrElse("REDIS_HOST", "localhost")
      port     <- System.envOrElse("REDIS_PORT", "6379").map(_.toInt)
      password <- System.env("REDIS_PASSWORD")
      username <- System.env("REDIS_USER")
    } yield RedisConfig(
      host = host,
      port = port,
      ssl = true,
      auth = password.map(RedisConfig.Auth(_, username))
    )
  }
```

## Provide the Redis Layer

Wire the config into `Redis.singleNode`. The library authenticates every connection it opens:

```scala
import zio._
import zio.redis._

def redisLayer(config: ZLayer[Any, Throwable, RedisConfig]): ZLayer[Any, Throwable, Redis] =
  ZLayer.make[Redis](
    config,
    ZLayer.succeed[CodecSupplier](CodecSupplier.utf8),
    Redis.singleNode
  )
```

## Set the Server Name When Needed

When the server certificate is issued for a different name than the host you connect to, set `sni` to that name. Keep `verifyCertificate = true` in production and turn it off only for local servers with self-signed certificates:

```scala
import zio.redis._

val behindProxy: RedisConfig =
  RedisConfig(host = "10.0.0.5", port = 6380, ssl = true, sni = Some("redis.example.com"))
```
