# Amqp and AMQPConfig

> `Amqp` is the entry point of the library. Its four methods acquire the two resources every program needs, a RabbitMQ `Connection` and a ZIO AMQP [`Channel`](./channel.md), and release them through the surrounding `Scope`. `AMQPConfig` describes how to reach the broker. The signatures are:

```scala
object Amqp {
  def connect(factory: ConnectionFactory): ZIO[Scope, Throwable, Connection]
  def connect(uri: URI): ZIO[Scope, Throwable, Connection]
  def connect(amqpConfig: AMQPConfig): ZIO[Scope, Throwable, Connection]
  def createChannel(connection: Connection): ZIO[Scope, Throwable, Channel]
}
```

`Connection` and `ConnectionFactory` are the types of the RabbitMQ Java client (`com.rabbitmq.client`). ZIO AMQP does not wrap them.

## Creating a Connection

All three overloads of `Amqp.connect` open a connection on a blocking thread and register a finalizer that closes it when the `Scope` ends. If closing the connection fails, the error is not reported through the error channel: the finalizer dies with it (`orDie`).

The overloads differ in how the broker is described. To connect with a fully configured RabbitMQ `ConnectionFactory`, for example to set TLS options or a custom executor:

```scala
import com.rabbitmq.client.ConnectionFactory
import zio._
import zio.amqp._

val factory = new ConnectionFactory()
factory.setHost("rabbit.internal")
factory.setPort(5672)

val connection: ZIO[Scope, Throwable, com.rabbitmq.client.Connection] =
  Amqp.connect(factory)
```

To connect with an AMQP URI, which carries the host, port, credentials and virtual host in one string:

```scala
import java.net.URI
import zio._
import zio.amqp._

val connection: ZIO[Scope, Throwable, com.rabbitmq.client.Connection] =
  Amqp.connect(URI.create("amqp://guest:guest@localhost:5672/%2F"))
```

`Amqp.connect(uri)` creates a `ConnectionFactory` and calls `ConnectionFactory#setUri`, so any URI the RabbitMQ client accepts works, including the `amqps` scheme for TLS.

To connect from an [`AMQPConfig`](#amqpconfig), which is the typed form of the same information:

```scala
import zio._
import zio.amqp._
import zio.amqp.model._

val connection: ZIO[Scope, Throwable, com.rabbitmq.client.Connection] =
  Amqp.connect(AMQPConfig.default.copy(host = "rabbit.internal"))
```

`Amqp.connect(amqpConfig)` converts the configuration with `AMQPConfig#toUri` and then behaves like the URI overload. See [Connect to a Broker](../how-to-connect.md) for a task-oriented walkthrough, including loading the configuration from the environment.

## Creating a Channel

`Amqp.createChannel` opens a channel on an existing connection and wraps it in a [`Channel`](./channel.md). The wrapper owns a one-permit `Semaphore`, and every operation on it takes that permit, so the underlying RabbitMQ channel, which is not thread-safe, is used by one fiber at a time.

The channel is closed when the `Scope` ends. The finalizer also takes the permit, so it waits for an operation that is in flight before closing. Like the connection finalizer, it dies if closing fails.

To open a channel and use it in one scoped program:

```scala
import zio._
import zio.amqp._
import zio.amqp.model._

val program: ZIO[Any, Throwable, Unit] =
  ZIO.scoped {
    for {
      connection <- Amqp.connect(AMQPConfig.default)
      channel    <- Amqp.createChannel(connection)
      _          <- channel.queueDeclare(QueueName("example"))
    } yield ()
  }
```

Because the finalizers run in reverse order of acquisition, the channel is closed before its connection.

A single connection supports many channels. Create one channel per independent unit of work, for example one for a consumer and another for a publisher, so a slow operation on one does not hold the other's permit.

## AMQPConfig

`AMQPConfig` is a case class in `zio.amqp.model` that holds everything needed to build an AMQP URI. It has no ZIO Config or JSON codec of its own, so load the values with the mechanism your application already uses and construct it by hand. Its definition is:

```scala
case class AMQPConfig(
  user: String,
  password: String,
  vhost: String,
  heartbeatInterval: Duration,
  ssl: Boolean,
  host: String,
  port: Short,
  connectionTimeout: Duration
) {
  val encodedVhost: String
  def toUri: AmqpUri
}
```

The fields are:

| Field               | Type       | Meaning                                                                    |
| ------------------- | ---------- | -------------------------------------------------------------------------- |
| `user`              | `String`   | User name.                                                                 |
| `password`          | `String`   | Password.                                                                  |
| `vhost`             | `String`   | Virtual host. `"/"` is the default virtual host.                           |
| `heartbeatInterval` | `Duration` | Heartbeat interval. Written to the URI in whole seconds.                   |
| `ssl`               | `Boolean`  | When `true`, the URI scheme is `amqps` instead of `amqp`.                  |
| `host`              | `String`   | Broker host name.                                                          |
| `port`              | `Short`    | Broker port.                                                               |
| `connectionTimeout` | `Duration` | Connection timeout. Written to the URI in milliseconds.                    |

### Default Configuration

`AMQPConfig.default` points at a local RabbitMQ with its stock credentials. To inspect its values:

```scala
import zio.amqp.model._

AMQPConfig.default
// res4: AMQPConfig = AMQPConfig(
//   user = "guest",
//   password = "guest",
//   vhost = "/",
//   heartbeatInterval = PT10S,
//   ssl = false,
//   host = "localhost",
//   port = 5672,
//   connectionTimeout = PT10S
// )
```

Use `AMQPConfig#copy` to override individual fields. To target a TLS broker on the conventional TLS port:

```scala
import zio._
import zio.amqp.model._

val secure: AMQPConfig =
  AMQPConfig.default.copy(host = "rabbit.example.com", port = 5671, ssl = true, user = "app")
```

### Converting to a URI

`AMQPConfig#encodedVhost` is the virtual host URL-encoded with UTF-8, so `"/"` becomes `%2F`. `AMQPConfig#toUri` assembles the URI from the other fields. To see the URI for the default configuration:

```scala
println(AMQPConfig.default.encodedVhost)
// %2F
println(AMQPConfig.default.toUri)
// amqp://guest:guest@localhost:5672/%2F?heartbeat=10&connection_timeout=10000
```

The result has the type `AmqpUri`, a subtype of `java.net.URI`, so it can be passed anywhere a `URI` is expected. See [Model Types](./model.md#amqpuri).

:::warning[Credentials are inserted into the URI as they are]
`AMQPConfig#toUri` writes `user` and `password` into the URI without percent-encoding, and `port` is a `Short`, so it cannot hold values above 32767. If a password contains characters such as `@`, `:` or `/`, or the broker listens on a higher port, build a `ConnectionFactory` and use `Amqp.connect(factory)` instead.
:::
