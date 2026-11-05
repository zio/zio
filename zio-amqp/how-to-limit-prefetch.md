# How to Limit Prefetch to Apply Back-Pressure

> By default, a RabbitMQ broker pushes every message in a queue to a consumer as fast as the network allows. A slow consumer then accumulates an ever-growing backlog in memory, and a fast consumer on the same queue sits idle. This guide shows how to use `Channel#basicQos` to cap the number of unacknowledged messages a consumer holds, and how to pick the cap.

All snippets assume these imports:

```scala
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("jobs")
```

## Set a Prefetch Count Before Consuming

The prefetch count is the number of deliveries the broker sends before it waits for acknowledgements. Call `Channel#basicQos` before `Channel#consume`, because the setting applies to consumers that are started afterwards. With a prefetch of 10, the broker sends ten messages, then one more for each acknowledgement it receives:

```scala
def worker(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel.basicQos(10) *>
    channel
      .consume(queue, ConsumerTag("worker"))
      .mapZIO { delivery =>
        Console.printLine(new String(delivery.getBody, "UTF-8")) *>
          channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
      }
      .runDrain
```

The limit only works with manual acknowledgements. A consumer started with `autoAck = true` is never limited, because its deliveries count as acknowledged when they are sent.

## Choose the Count

A prefetch of 1 gives the fairest distribution between workers: a worker receives its next message only when it is free, so one slow task never holds up messages that another worker could take. The price is a network round trip between every two messages, which limits the throughput of workers with very fast tasks.

For tasks that take milliseconds, raise the count until the consumer is no longer waiting for the broker. A count of a few tens to a few hundred is common. For tasks that take seconds or longer, a count of 1 costs nothing. The prefetch should also be at least the size of any batch you [acknowledge in one call](./how-to-acknowledge-messages.md#settle-a-batch-with-one-call), otherwise the batch can never fill.

A count of `0` removes the limit, which is the default behavior.

## Apply the Limit to a Whole Channel

With the default `global = false`, the count applies to each consumer that is started on the channel afterwards: two consumers with a prefetch of 5 can hold ten messages together. To share one budget across all consumers of the channel, pass `global = true`:

```scala
def sharedBudget(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel.basicQos(10, global = true)
```

Because `Amqp.createChannel` gives each channel its own prefetch setting, the simplest way to give every consumer its own limit is to give every consumer its own channel.

## See the Limit at Work

The count of ready messages on a queue excludes the ones that are delivered but unacknowledged, which makes the effect of the prefetch visible. If a consumer with a prefetch of 5 never acknowledges, a queue of 20 messages shows only 15 as ready:

```scala
def demonstrate(channel: Channel): ZIO[Any, Throwable, Long] =
  for {
    _     <- channel.basicQos(5)
    fiber <- channel.consume(queue, ConsumerTag("holder")).runDrain.fork
    _     <- ZIO.sleep(1.second)
    ready <- channel.messageCount(queue)
    _     <- fiber.interrupt
  } yield ready
```

## Running the Example

The file [`PrefetchExample.scala`](https://github.com/zio/zio-amqp/blob/master/zio-amqp-examples/src/main/scala/zio/amqp/examples/howto/PrefetchExample.scala) publishes 20 messages, starts a consumer with a prefetch of 5 that never acknowledges, and prints the number of ready messages. Start RabbitMQ with `docker compose up -d` and run `sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.PrefetchExample"`. The output is:

```text
Ready messages with prefetch 5 and no acks: 15
```
