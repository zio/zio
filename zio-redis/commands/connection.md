# Connection

> Connection commands act on the connection that the `Redis` service uses, rather than on data. This page covers the six commands on `Redis` for authenticating, selecting a database, naming the connection and checking that the server is alive.

## Authentication

`Redis#auth` authenticates the connection. With one argument it sends a password, and with two it sends a username and password for the Redis ACL system. Both return `Unit` on success and fail with a `RedisError` when the credentials are wrong:

```scala
import zio._
import zio.redis._

def login(redis: Redis): IO[RedisError, Unit] =
  redis.auth("app-user", "secret")
```

Most programs do not call `Redis#auth` themselves. When a `RedisConfig` has credentials, the library authenticates every connection it opens, including after a reconnect. See [Configuration](../configuration.md) for how to set them.

## Choosing a Database

`Redis#select` switches the connection to the database with the given zero-based index. New connections start on database `0`. The selected database belongs to the connection, so it is lost if the connection is replaced after a failure:

```scala
import zio._
import zio.redis._

def useDatabaseOne(redis: Redis): IO[RedisError, Unit] =
  redis.select(1L)
```

## Naming the Connection

A connection name makes the connection easy to find in `CLIENT LIST` output on the server. The following table lists the related commands:

| Command               | Returns          | Purpose                                              |
| --------------------- | ---------------- | ---------------------------------------------------- |
| `Redis#clientSetName` | `Unit`           | Assigns a name to the connection                     |
| `Redis#clientGetName` | `Option[String]` | Returns the name, or `None` if none was set          |
| `Redis#clientId`      | `Long`           | Returns the connection ID, which never repeats       |

The ID increases with each new connection, so a larger ID means a later connection. The following example names the connection and reads both values back:

```scala
import zio._
import zio.redis._

def describe(redis: Redis): IO[RedisError, (Option[String], Long)] =
  for {
    _    <- redis.clientSetName("billing-service")
    name <- redis.clientGetName
    id   <- redis.clientId
  } yield (name, id)
```

## Checking the Server

`Redis#ping` returns `"PONG"` when called without a message, and echoes the message back when given one. The parameter has a default of `None`, so `redis.ping()` needs the parentheses:

```scala
import zio._
import zio.redis._

def healthCheck(redis: Redis): IO[RedisError, Boolean] =
  redis.ping().map(_ == "PONG")

def echo(redis: Redis): IO[RedisError, String] =
  redis.ping(Some("hello"))
```

## Option Types

The only type in `options/Connection.scala` is `Address`, a case class with an `ip: InetAddress` and a `port: Int`. No command in this group takes or returns it, so application code does not use it.

## Related Pages

- [Configuration](../configuration.md) explains credentials, TLS and the connection settings.
- [Error Handling](../error-handling.md) lists the errors raised, such as authentication failures.
- [Keys](./keys.md) covers commands that act on data in the selected database.
