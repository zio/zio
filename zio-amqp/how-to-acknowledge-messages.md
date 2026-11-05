# How to Acknowledge Messages

> A delivery that a consumer has not acknowledged stays on the broker as "unacknowledged": the broker redelivers it if the channel closes first. This guide shows how to acknowledge a message after it has been handled, how to reject one and requeue it, how to settle many deliveries at once, and when to skip acknowledgements entirely.

All snippets assume these imports and a channel `channel`, obtained from [`Amqp.createChannel`](./reference/amqp.md#creating-a-channel):

```scala
import com.rabbitmq.client.Delivery
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("jobs")

def tagOf(delivery: Delivery): DeliveryTag =
  DeliveryTag(delivery.getEnvelope.getDeliveryTag)

def handle(delivery: Delivery): ZIO[Any, Throwable, Unit] =
  Console.printLine(new String(delivery.getBody, "UTF-8"))
```

## Acknowledge After Processing

A consumer started with `Channel#consume` has `autoAck = false` by default, so every delivery must be settled. To acknowledge a message only after `handle` has succeeded, call `Channel#ack` as the last step of the stream stage. If `handle` fails, the stream fails, the channel is eventually closed, and the broker redelivers the unacknowledged messages:

```scala
def worker(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("worker"))
    .mapZIO(delivery => handle(delivery) *> channel.ack(tagOf(delivery)))
    .runDrain
```

## Reject and Requeue a Failed Message

When processing fails and the next attempt may succeed, for example after a timeout, reject the message with `Channel#nack` and `requeue = true`. The broker puts it back on the queue, and it is delivered again with the `redelivered` flag set on its envelope. To keep the consumer alive when a single message fails:

```scala
def resilientWorker(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("resilient-worker"))
    .mapZIO { delivery =>
      handle(delivery).foldZIO(
        _ => channel.nack(tagOf(delivery), requeue = true),
        _ => channel.ack(tagOf(delivery))
      )
    }
    .runDrain
```

A requeued message returns to its original position in the queue, so a message that always fails is retried again and again. Check `delivery.getEnvelope.isRedeliver` and reject with `requeue = false` the second time to stop it.

## Discard or Dead-Letter a Message

`Channel#nack` with `requeue = false`, the default, removes the message from the queue. If the queue was declared with a dead-letter exchange, the broker republishes the message to that exchange instead of dropping it. To set that up, pass the RabbitMQ queue arguments to `Channel#queueDeclare`:

```scala
def declareWithDeadLetters(channel: Channel): ZIO[Any, Throwable, String] =
  channel.queueDeclare(
    queue,
    durable = true,
    arguments = Map("x-dead-letter-exchange" -> "jobs.dead")
  )
```

The exchange `jobs.dead` and a queue bound to it must exist for the dead-lettered messages to be kept. See [Route Messages with Exchanges](./how-to-route-with-exchanges.md) for declaring them.

## Settle a Batch with One Call

Acknowledging each message costs one network round trip. To settle a whole batch with one, collect the tags and call `Channel#ackMany`, which acknowledges the highest tag with `multiple = true`. That also acknowledges every lower unacknowledged tag on the channel, so call it only when the whole group, and everything before it, is done:

```scala
def batchWorker(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("batch-worker"))
    .groupedWithin(100, 1.second)
    .mapZIO { batch =>
      (ZIO.foreachDiscard(batch)(handle) *> channel.ackMany(batch.map(tagOf).toSeq)).when(batch.nonEmpty)
    }
    .runDrain
```

`Channel#nackMany` is the matching call for rejecting a group. Both methods throw `UnsupportedOperationException` when called with an empty sequence, so the example checks `batch.nonEmpty` before calling it.

Because a batch is settled only after all its messages succeed, set a [prefetch limit](./how-to-limit-prefetch.md) at least as large as the batch size, or the broker will stop sending before the batch fills.

## Skip Acknowledgements with autoAck

Pass `autoAck = true` to `Channel#consume` to let the broker consider every message acknowledged the moment it sends it. This is the fastest mode, but a message in flight when the consumer or the channel dies is lost, and prefetch limits do not apply. Use it only for data you can afford to lose, such as metrics samples:

```scala
def firehose(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("firehose"), autoAck = true)
    .mapZIO(handle)
    .runDrain
```

Never call `Channel#ack` for a delivery received with `autoAck = true`: the broker has already forgotten the tag, and acknowledging it is a protocol error that closes the channel.

## Running the Example

The file [`AcknowledgeExample.scala`](https://github.com/zio/zio-amqp/blob/master/zio-amqp-examples/src/main/scala/zio/amqp/examples/howto/AcknowledgeExample.scala) publishes a good and a flaky message, acknowledges the first, rejects the second once with requeue, and acknowledges it on redelivery. Start RabbitMQ with `docker compose up -d` and run `sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.AcknowledgeExample"`. The output is:

```text
good (redelivered = false): acked
flaky (redelivered = false): nacked and requeued
flaky (redelivered = true): acked
```
