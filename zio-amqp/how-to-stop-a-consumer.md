# How to Stop a Consumer

> A stream returned by `Channel#consume` has no end of its own: it waits for the next message until something stops it. This guide shows how to stop a consumer after a number of messages, on a shutdown signal, on a timeout and when the application ends, and what happens to the message that is being processed at that moment.

All snippets assume these imports:

```scala
import com.rabbitmq.client.Delivery
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("jobs")

def process(delivery: Delivery): ZIO[Any, Throwable, Unit] =
  Console.printLine(new String(delivery.getBody, "UTF-8"))
```

## What Happens When the Stream Ends

However a consumer stream ends, whether it is completed, fails or is interrupted, `Channel#consume` cancels the consumer on the broker. The broker then stops sending messages to it. Messages that were already delivered but not acknowledged are not returned to the queue by the cancellation: they stay unacknowledged until the channel closes, and the broker then redelivers them.

For that reason, stop consumers with the techniques below and close the channel promptly. Closing happens automatically when the `Scope` that created the channel ends.

## Stop After a Number of Messages

To process a fixed number of messages and stop, use the ordinary stream operators `take` and `takeUntil`. Here the consumer stops after the first ten messages:

```scala
def firstTen(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("first-ten"))
    .take(10)
    .mapZIO(d => process(d) *> channel.ack(DeliveryTag(d.getEnvelope.getDeliveryTag)))
    .runDrain
```

## Stop When a Signal Arrives

To stop a long-running worker from another fiber, for example when the application receives a termination signal, complete a `Promise` and halt the stream with `ZStream#haltWhen`. Put `ZStream#haltWhen` before the processing stage: the stream then stops asking for new messages, but the message that is already being processed runs to completion and is acknowledged:

```scala
def gracefulWorker(channel: Channel, shutdown: Promise[Throwable, Unit]): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("graceful"))
    .haltWhen(shutdown)
    .mapZIO(d => process(d) *> channel.ack(DeliveryTag(d.getEnvelope.getDeliveryTag)))
    .runDrain
```

To trigger it, run the worker on a fiber, and later complete the promise and wait for the worker:

```scala
def run(channel: Channel): ZIO[Any, Throwable, Unit] =
  for {
    shutdown <- Promise.make[Throwable, Unit]
    worker   <- gracefulWorker(channel, shutdown).fork
    _        <- ZIO.sleep(1.minute)
    _        <- shutdown.succeed(())
    _        <- worker.join
  } yield ()
```

Combine this with `channel.basicQos(1)` set before consuming to bound how many messages the broker has already delivered when the signal arrives. With a prefetch of 1, at most one further message, which the broker sends right after the current acknowledgement, is left unacknowledged and returns to the queue when the channel closes. See [Limit Prefetch to Apply Back-Pressure](./how-to-limit-prefetch.md).

:::note[Interrupting stops mid-message]
Interrupting the worker fiber, or using `ZStream#interruptWhen`, cancels the processing stage as well. A message that was being processed is then never acknowledged and is redelivered after the channel closes. Choose that when processing is idempotent and fast shutdown matters more than finishing the current message.
:::

## Stop After a Period of Silence

To stop a consumer that has been idle for too long, use `ZStream#timeout`, which ends the stream if the next element does not arrive in time:

```scala
def untilIdle(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("until-idle"))
    .timeout(30.seconds)
    .mapZIO(d => process(d) *> channel.ack(DeliveryTag(d.getEnvelope.getDeliveryTag)))
    .runDrain
```

## Stop When the Application Ends

A worker that should live as long as the application can run on a fiber that is tied to a scope. `forkScoped` interrupts the fiber when the scope closes, which cancels the consumer, and the scope then closes the channel and the connection in reverse order of acquisition:

```scala
val program: ZIO[Any, Throwable, Unit] =
  ZIO.scoped {
    for {
      connection <- Amqp.connect(AMQPConfig.default)
      channel    <- Amqp.createChannel(connection)
      _          <- channel
                      .consume(queue, ConsumerTag("app-worker"))
                      .mapZIO(d => process(d) *> channel.ack(DeliveryTag(d.getEnvelope.getDeliveryTag)))
                      .runDrain
                      .forkScoped
      _          <- ZIO.never
    } yield ()
  }
```

`ZIO.never` keeps the program alive until it is interrupted, for example by `ZIOAppDefault` when the process receives SIGTERM. The scope then runs the finalizers.

## React When the Broker Stops the Consumer

Two events end the stream without any action by the program. If the broker cancels the consumer, for example because the queue was deleted, the stream completes normally. If the channel or connection is shut down, the stream fails with the RabbitMQ client's `ShutdownSignalException`. To reconnect after a shutdown, wrap the whole acquire-and-consume program in a retry, because a closed channel cannot be reused:

```scala
def resilient(program: ZIO[Any, Throwable, Unit]): ZIO[Any, Throwable, Unit] =
  program.retry(Schedule.exponential(1.second) && Schedule.recurs(5))
```

## Running the Example

The file [`StopConsumerExample.scala`](https://github.com/zio/zio-amqp/blob/master/zio-amqp-examples/src/main/scala/zio/amqp/examples/howto/StopConsumerExample.scala) publishes three tasks of one second each, requests shutdown while the first is being processed, and prints what is left. Start RabbitMQ with `docker compose up -d` and run `sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.StopConsumerExample"`. The output is:

```text
started task-1
shutdown requested
finished task-1
consumers: 0, messages left: 1
```

The consumer finished `task-1` after the shutdown request, started nothing new, and was cancelled. One message is still ready in the queue. The second task was already delivered when the first was acknowledged, so it is unacknowledged on the channel and returns to the queue when the channel closes.
