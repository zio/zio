# How to Connect to a Broker

> This guide shows how to open a connection to a RabbitMQ broker, how to pick between the three `Amqp.connect` overloads, and how to read connection settings from the environment, retry a failed connection, and use TLS.

All snippets assume these imports:

```scala
import com.rabbitmq.client.{ Connection, ConnectionFactory }
import zio._
import zio.amqp._
import zio.amqp.model._

import java.net.URI
```

## Connect with an AMQPConfig

For a broker described by host, port and credentials, `AMQPConfig` is the most readable option. `AMQPConfig.default` points at `localhost:5672` with the `guest` user, so a development program only overrides what differs:

```scala
val connection: ZIO[Scope, Throwable, Connection] =
  Amqp.connect(AMQPConfig.default.copy(host = "rabbit.internal", user = "app", password = "secret"))
```

The connection is a scoped resource: it is closed when the surrounding scope ends. See [`Amqp` and `AMQPConfig`](./reference/amqp.md) for every field.

## Connect with a URI

When the broker address is a single setting, such as a `RABBITMQ_URL` environment variable or a secret from a platform, pass it as a `URI`. The URI carries the credentials, the host, the port and the URL-encoded virtual host (`%2F` is `/`):

```scala
val connection: ZIO[Scope, Throwable, Connection] =
  Amqp.connect(URI.create("amqp://guest:guest@localhost:5672/%2F"))
```

Use the `amqps` scheme for TLS.

## Connect with a ConnectionFactory

For anything the other two overloads cannot express, such as a custom SSL context, automatic recovery settings, a client-provided name or a custom executor, configure a RabbitMQ `ConnectionFactory` yourself and pass it in. `Amqp.connect(factory)` is the overload the other two call:

```scala
val connection: ZIO[Scope, Throwable, Connection] = {
  val factory = new ConnectionFactory()
  factory.setHost("rabbit.internal")
  factory.setPort(5672)
  factory.setUsername("app")
  factory.setPassword("secret")
  factory.setRequestedHeartbeat(30)
  Amqp.connect(factory)
}
```

Prefer the factory when a password contains characters that are special in URIs (such as `@`, `:` or `/`): `AMQPConfig#toUri` does not percent-encode them.

## Read the Settings from the Environment

To keep credentials out of the code, build the `AMQPConfig` from environment variables with `System.env`, falling back to the defaults:

```scala
val config: ZIO[Any, SecurityException, AMQPConfig] =
  for {
    host     <- System.env("AMQP_HOST").map(_.getOrElse(AMQPConfig.default.host))
    user     <- System.env("AMQP_USER").map(_.getOrElse(AMQPConfig.default.user))
    password <- System.env("AMQP_PASSWORD").map(_.getOrElse(AMQPConfig.default.password))
  } yield AMQPConfig.default.copy(host = host, user = user, password = password)

val connection: ZIO[Scope, Throwable, Connection] =
  config.flatMap(Amqp.connect(_))
```

If you already use a configuration library, construct the same case class from its output. The library itself has no configuration integration.

## Retry Until the Broker Is Available

A container orchestrator often starts the application before the broker accepts connections. `Amqp.connect` fails with the RabbitMQ client's exception in the error channel, so any ZIO `Schedule` can retry it. To retry every two seconds, up to ten times:

```scala
val connection: ZIO[Scope, Throwable, Connection] =
  Amqp.connect(AMQPConfig.default).retry(Schedule.spaced(2.seconds) && Schedule.recurs(10))
```

Retrying a scoped acquisition is safe: a failed attempt acquired nothing, so nothing is left to release.

## Use More than One Channel

One connection can serve many channels, and each channel serializes its own operations. To avoid a slow call on one channel delaying another, give independent jobs their own channels on a shared connection:

```scala
val program: ZIO[Any, Throwable, Unit] =
  ZIO.scoped {
    for {
      connection <- Amqp.connect(AMQPConfig.default)
      publisher  <- Amqp.createChannel(connection)
      consumer   <- Amqp.createChannel(connection)
      _          <- publisher.queueDeclare(QueueName("jobs"))
    } yield ()
  }
```

The channels are closed first, in reverse order, and then the connection.

## Running the Example

The file [`ConnectExample.scala`](https://github.com/zio/zio-amqp/blob/master/zio-amqp-examples/src/main/scala/zio/amqp/examples/howto/ConnectExample.scala) opens a connection with each of the three overloads against a local broker and prints whether each is open. Start RabbitMQ with `docker compose up -d`, then run `sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.ConnectExample"` from the repository root. The output is:

```text
config open: true, uri open: true, factory open: true
```
