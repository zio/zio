# How to Process Messages in Parallel

> A consumer made from `Channel#consume` handles one message at a time unless you ask for more. This guide shows how to process several messages at once with one channel, how to set the prefetch count to match, and why batch acknowledgement is unsafe when messages finish out of order.

All snippets assume these imports:

```scala
import com.rabbitmq.client.Delivery
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("jobs")

def process(delivery: Delivery): ZIO[Any, Throwable, Unit] =
  ZIO.sleep(100.millis) *> Console.printLine(new String(delivery.getBody, "UTF-8"))
```

## Process Several Messages at Once

`ZStream#mapZIOPar` and `ZStream#mapZIOParUnordered` run the processing stage for up to `n` messages at the same time. The unordered variant emits each result as soon as it is ready, which is what a consumer wants because it only acknowledges. This worker processes four messages concurrently and acknowledges each one when it is done:

```scala
def worker(channel: Channel, parallelism: Int): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("parallel-worker"))
    .mapZIOParUnordered(parallelism) { delivery =>
      process(delivery) *> channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
    }
    .runDrain
```

The processing runs on many fibers, while the `Channel` takes turns between them: each `Channel#ack` waits for the channel's one permit, which is held only for the short call to the broker.

## Match the Prefetch Count

The broker sends at most as many unacknowledged messages as the prefetch count allows, so a count below the parallelism leaves some of the parallel slots idle. Set it to at least the parallelism before consuming:

```scala
def run(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel.basicQos(4) *> worker(channel, parallelism = 4)
```

A count equal to the parallelism keeps every slot busy and holds at most that many messages away from other consumers. See [How to Limit Prefetch](./how-to-limit-prefetch.md) for choosing it.

## Settle Failures Individually

With `mapZIOParUnordered`, a failure in one message fails the whole stream and stops the consumer. To keep the other messages running, handle the failure inside the stage and reject only that message:

```scala
def resilientWorker(channel: Channel, parallelism: Int): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("resilient-worker"))
    .mapZIOParUnordered(parallelism) { delivery =>
      val tag = DeliveryTag(delivery.getEnvelope.getDeliveryTag)
      process(delivery).foldZIO(
        _ => channel.nack(tag, requeue = true),
        _ => channel.ack(tag)
      )
    }
    .runDrain
```

## Do Not Use Batch Acknowledgement Here

`Channel#ackMany` and `Channel#nackMany` act on the highest tag in the sequence and settle every lower unacknowledged tag with it. When messages finish out of order, a batch that ends at tag 7 also acknowledges tag 5, even if tag 5 is still being processed, and the broker forgets it. If that worker then crashes, message 5 is lost. Acknowledge each message on its own, as above, unless the processing order is guaranteed. See [How to Acknowledge Messages](./how-to-acknowledge-messages.md).
