# How to Recover from a Closed Channel

> When a broker-side error closes a channel, or the connection drops, a running consumer stream fails and every later call on that `Channel` fails too. This guide shows how to see that happen, how to restart a consumer on a fresh channel, and how to keep the retries bounded. The background is in [Delivery and Failure](./explanation/delivery-and-failure.md#failures-close-channels).

All snippets assume these imports:

```scala
import com.rabbitmq.client.{ Delivery, ShutdownSignalException }
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("jobs")

def process(delivery: Delivery): ZIO[Any, Throwable, Unit] =
  Console.printLine(new String(delivery.getBody, "UTF-8"))
```

## See the Failure

`Channel#queueDeclarePassive` fails if the queue does not exist, and the broker closes the channel as a result. Any call after that fails as well, although the connection is still open:

```scala
val demo: ZIO[Scope, Throwable, Unit] =
  for {
    connection <- Amqp.connect(AMQPConfig.default)
    channel    <- Amqp.createChannel(connection)
    first      <- channel.queueDeclarePassive(QueueName("does-not-exist")).either
    second     <- channel.queueDeclare(QueueName("other")).either
    _          <- Console.printLine(s"first: ${first.isLeft}, second: ${second.isLeft}")
  } yield ()
```

Both calls end in `Left`: the first with the broker's error, the second because the channel is closed. A consumer stream on that channel fails with the client's `ShutdownSignalException`.

## Restart the Consumer on a New Channel

Put everything that depends on the channel, from opening it to the end of the stream, in one scoped effect. If it fails, its scope closes the dead channel, and running the effect again opens a new one:

```scala
def consumeOnce(connection: com.rabbitmq.client.Connection): ZIO[Any, Throwable, Unit] =
  ZIO.scoped {
    Amqp.createChannel(connection).flatMap { channel =>
      channel.queueDeclare(queue, durable = true) *>
        channel
          .consume(queue, ConsumerTag("recovering-worker"))
          .mapZIO(d => process(d) *> channel.ack(DeliveryTag(d.getEnvelope.getDeliveryTag)))
          .runDrain
    }
  }
```

Messages that were delivered but not acknowledged when the channel died are redelivered by the broker to the new consumer, so processing must tolerate duplicates.

## Retry with a Bound

Retry on failure with a pause between attempts, and stop after a limit so a permanent fault, such as a wrong password, is reported:

```scala
def resilient(connection: com.rabbitmq.client.Connection): ZIO[Any, Throwable, Unit] =
  consumeOnce(connection).retry(Schedule.spaced(2.seconds) && Schedule.recurs(10))
```

To also reopen the consumer when the broker cancels it, which completes the stream without an error, repeat the effect with `forever` before the retry: `consumeOnce(connection).forever.retry(...)`.

## Recover from a Lost Connection

A lost connection fails every channel on it, so retrying on the same `Connection` cannot help. Move the connection inside the retried effect as well:

```scala
val fullyResilient: ZIO[Any, Throwable, Unit] =
  ZIO
    .scoped {
      Amqp.connect(AMQPConfig.default).flatMap(consumeOnce)
    }
    .retry(Schedule.spaced(5.seconds) && Schedule.recurs(10))
```

Retry only errors you expect, for example with `.retry(...)` after `ZIO#refineToOrDie[ShutdownSignalException]`, so that a bug in `process` is not retried ten times.
