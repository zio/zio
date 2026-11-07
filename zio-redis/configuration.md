# Configuration

> `Redis.singleNode` connects to the server described by a `RedisConfig`; `Redis.local` uses `RedisConfig.Local`. This page lists every setting and shows how to supply it.

## RedisConfig

`RedisConfig` is a case class with the following fields:

| Field               | Type                       | Default                | Meaning                                              |
| ------------------- | -------------------------- | ---------------------- | ---------------------------------------------------- |
| `host`              | `String`                   | none                   | Server host name                                     |
| `port`              | `Int`                      | none                   | Server port                                          |
| `sni`               | `Option[String]`           | `None`                 | Server name sent during the TLS handshake            |
| `ssl`               | `Boolean`                  | `false`                | Whether to connect over TLS                          |
| `verifyCertificate` | `Boolean`                  | `true`                 | Whether to verify the server certificate             |
| `requestQueueSize`  | `Int`                      | `16`                   | Size of the queue of requests waiting to be sent     |
| `auth`              | `Option[RedisConfig.Auth]` | `None`                 | Credentials, sent when the connection opens          |

`RedisConfig.Local` is `RedisConfig("localhost", 6379)`.

## Connecting to a Server

To connect to a named host and supply the config as a layer:

```scala
import zio._
import zio.redis._

val config: ULayer[RedisConfig] =
  ZLayer.succeed(RedisConfig("redis.example.com", 6379))

val redis: ZLayer[Any, RedisError.IOError, Redis] =
  ZLayer.make[Redis](
    config,
    ZLayer.succeed[CodecSupplier](CodecSupplier.utf8),
    Redis.singleNode
  )
```

## Authentication and TLS

To authenticate with a password, set `auth` to a `RedisConfig.Auth`. Add a `username` when the server uses ACL users. To use TLS, set `ssl = true`:

```scala
import zio._
import zio.redis._

val secure: RedisConfig =
  RedisConfig(
    host = "redis.example.com",
    port = 6380,
    ssl = true,
    auth = Some(RedisConfig.Auth("secret", Some("app-user")))
  )
```

Set `verifyCertificate = false` only for development servers with self-signed certificates.

## RedisUri

`RedisUri` identifies a node by host and port. It is used by `RedisClusterConfig` to list cluster addresses, and it renders as `redis://host:port` (or `rediss://` when TLS is on). The companion offers several `apply` overloads:

```scala
import zio.redis._

val plain  = RedisUri("localhost", 6379)
val parsed = RedisUri("localhost:6379")
val tls    = RedisUri("redis.example.com:6380", ssl = true)
```

`RedisUri.apply` with a `"host:port"` string splits on `:`, so it does not accept IPv6 literals or full `redis://` URLs.
