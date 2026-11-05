# Tutorial: Build a Work Queue

> Welcome! In this tutorial we build a small work queue with ZIO AMQP: a producer puts tasks on a queue, and a pool of workers takes them one at a time and confirms each one when it is done. By the end of this tutorial, you will know how to connect to RabbitMQ, declare a queue, publish messages, consume them as a stream, acknowledge them, and spread work fairly over several consumers.

We assume you know the basics of ZIO (effects, `for` comprehensions and `ZIO.scoped`) but nothing about AMQP. The ideas we need are small:

- A broker (here RabbitMQ) accepts messages from producers and hands them to consumers.
- A queue is a named buffer inside the broker that holds messages until a consumer takes them.
- A consumer acknowledges a message when it has finished processing it. A message that is never acknowledged is delivered again.

## 1. Start RabbitMQ and Connect

We need a running broker. The repository ships a `docker-compose.yml` that starts RabbitMQ 3.13 on port 5672, the default AMQP port. From the root of the repository, run:

```bash
docker compose up -d
```

We also add the library to our project:

```scala
libraryDependencies += "dev.zio" %% "zio-amqp" % "1.0.1"
```

Now we can connect. `Amqp.connect` opens a connection, and `Amqp.createChannel` opens a channel on it. A channel is the object we use for every operation: declaring queues, publishing and consuming. Both are scoped resources, so `ZIO.scoped` closes them for us when the program ends, even if it fails.

The first program connects with the default settings (`localhost:5672`, user `guest`, password `guest`) and declares a queue named `tasks`. `durable = true` makes the queue survive a broker restart:

```scala
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("tasks")

val program: ZIO[Any, Throwable, Unit] =
  ZIO.scoped {
    for {
      connection <- Amqp.connect(AMQPConfig.default)
      channel    <- Amqp.createChannel(connection)
      name       <- channel.queueDeclare(queue, durable = true)
      _          <- Console.printLine(s"Declared queue: $name")
    } yield ()
  }
```

Running it prints:

```text
Declared queue: tasks
```

Declaring a queue is idempotent: if `tasks` already exists with the same settings, nothing changes. That is why every program in this tutorial can declare the queue it uses.

Notice that the names `QueueName("tasks")`, `ConsumerTag("worker")` and so on are wrapped in small types. They keep us from passing a queue name where an exchange name is expected. See [Model Types](./reference/model.md) for the full list.

## 2. Publish Tasks

Publishers do not send messages to a queue directly: they send them to an exchange, which routes them to queues. RabbitMQ provides a default exchange with the empty name. It routes a message to the queue whose name equals the message's routing key, which is exactly what a work queue needs.

To publish five tasks, we call `Channel#publish` once per task. The body is a byte array, so we encode our text as UTF-8. The `MessageProperties.PERSISTENT_TEXT_PLAIN` constant from the RabbitMQ client marks each message as persistent, so the broker writes it to disk and it survives a restart together with the durable queue:

```scala
import com.rabbitmq.client.MessageProperties
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("tasks")

def publishTasks(channel: Channel): ZIO[Any, Throwable, Unit] =
  ZIO.foreachDiscard(1 to 5) { i =>
    channel.publish(
      exchange = ExchangeName(""),
      body = s"task-$i".getBytes("UTF-8"),
      routingKey = RoutingKey(QueueName.unwrap(queue)),
      props = MessageProperties.PERSISTENT_TEXT_PLAIN
    )
  }
```

The `publishTasks` function needs a `Channel`, so the complete program wraps it in the same connect-and-declare scaffolding as before. The runnable version also asks the broker how many messages are waiting, using `Channel#messageCount`:

<details>
  <summary>Runnable version, with the connection and the queue from section 1: <code>Concept2PublishExample.scala</code></summary>

```scala
//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.0
// Run from this folder:  scala-cli run Concept2PublishExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.tutorial.Concept2PublishExample"

package zio.amqp.examples.tutorial

import com.rabbitmq.client.MessageProperties
import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Work Queue Tutorial — 2. Publish Tasks
 *
 * Publishes five persistent task messages through the default exchange. Setup from section 1: the connection, the
 * channel and the durable queue.
 */
object Concept2PublishExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("tasks")

  val program: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel.queueDeclare(queue, durable = true)
        _          <- ZIO.foreachDiscard(1 to 5) { i =>
                        channel.publish(
                          exchange = ExchangeName(""),
                          body = s"task-$i".getBytes("UTF-8"),
                          routingKey = RoutingKey(QueueName.unwrap(queue)),
                          props = MessageProperties.PERSISTENT_TEXT_PLAIN
                        )
                      }
        waiting    <- channel.messageCount(queue)
        _          <- Console.printLine(s"Messages waiting in the queue: $waiting")
      } yield ()
    }

  def run: ZIO[Any, Throwable, Unit] = program
}
```

</details>

Running it prints the number of messages in the queue:

```text
Messages waiting in the queue: 5
```

The broker updates its message counts asynchronously, so a count read immediately after publishing can occasionally lag behind.

## 3. Consume and Acknowledge

Now we need a worker. `Channel#consume` registers a consumer on a queue and returns a `ZStream` of deliveries. Each element is a RabbitMQ `Delivery` that carries the message body and an envelope with the delivery tag, the number that identifies this delivery on the channel.

After a worker finishes a task, it calls `Channel#ack` with the delivery tag. Only then does the broker forget the message. If the worker crashes before acknowledging, the channel closes and the broker delivers the message to another consumer, so no task is lost.

In the following program, `mapZIO` handles each delivery in order and `take(5)` ends the stream after five tasks. When the stream ends, the consumer is cancelled automatically:

```scala
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("tasks")

def worker(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(queue, ConsumerTag("worker"))
    .mapZIO { delivery =>
      val task = new String(delivery.getBody, "UTF-8")
      Console.printLine(s"Working on $task") *>
        channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
    }
    .take(5)
    .runDrain
```

The envelope returns the tag as a plain `Long`, so we wrap it in `DeliveryTag` for `Channel#ack`.

<details>
  <summary>Runnable version, with the connection and the queue from section 1: <code>Concept3ConsumeExample.scala</code></summary>

```scala
//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.0
// Run from this folder:  scala-cli run Concept3ConsumeExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.tutorial.Concept3ConsumeExample"

package zio.amqp.examples.tutorial

import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Work Queue Tutorial — 3. Consume and Acknowledge
 *
 * Consumes up to five tasks from the queue and acknowledges each one after it is handled. Run Concept2PublishExample
 * first so the queue has messages.
 */
object Concept3ConsumeExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("tasks")

  val program: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel
                        .consume(queue, ConsumerTag("worker"))
                        .mapZIO { delivery =>
                          val task = new String(delivery.getBody, "UTF-8")
                          Console.printLine(s"Working on $task") *>
                            channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
                        }
                        .take(5)
                        .runDrain
      } yield ()
    }

  def run: ZIO[Any, Throwable, Unit] = program
}
```

</details>

If we run the publisher from section 2 and then this worker, the worker prints each task in the order it was published:

```text
Working on task-1
Working on task-2
Working on task-3
Working on task-4
Working on task-5
```

A consumer in ZIO AMQP is just a stream, so everything we know about `ZStream` applies: we can filter, batch with `grouped`, run stages in parallel with `mapZIOPar`, or stop with `take`, `takeUntil` and interruption.

## 4. Share Work Between Workers

A work queue earns its name when several workers share the load. If we start two consumers on the same queue, the broker hands each message to exactly one of them. By default, though, the broker pushes messages to consumers as fast as it can, regardless of how busy they are, so a slow worker can end up with a long backlog while a fast one is idle.

`Channel#basicQos` fixes that. It sets the prefetch count: the maximum number of unacknowledged messages the broker sends to a consumer. With a prefetch of 1, a worker gets its next task only after it has acknowledged the previous one, so work flows to whichever worker is free.

Each worker also gets its own channel, because a channel serializes its operations. The worker below creates a channel, limits its prefetch, and then consumes forever as a background fiber that is tied to the surrounding scope:

```scala
import com.rabbitmq.client.Connection
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("tutorial-tasks")

def worker(connection: Connection, name: String): ZIO[Scope, Throwable, Unit] =
  for {
    channel <- Amqp.createChannel(connection)
    _       <- channel.basicQos(1)
    _       <- channel
                 .consume(queue, ConsumerTag(name))
                 .mapZIO { delivery =>
                   val task = new String(delivery.getBody, "UTF-8")
                   Console.printLine(s"[$name] working on $task") *>
                     ZIO.sleep(200.millis) *>
                     channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
                 }
                 .runDrain
                 .forkScoped
  } yield ()
```

`forkScoped` starts the stream in a new fiber and interrupts it when the scope closes. Interrupting the stream cancels the consumer, and the scope then closes the channel. The `ZIO.sleep` stands in for real work.

## 5. Putting It Together

We now have all the pieces. The complete program declares a queue, starts two workers, publishes ten tasks, and waits until the workers have acknowledged all of them. It differs from the earlier sections in three ways. It uses a separate queue, `tutorial-tasks`, so it cannot collide with leftover messages from the earlier runs. It purges that queue at the start with `Channel#purgeQueue` so that it can be run repeatedly. And the workers share a `Ref` counter and a `Promise` so that the main fiber knows when the last task is done.

<details>
  <summary>Complete program: <code>CompleteExample.scala</code></summary>

```scala
//> using scala 2.13
//> using dep dev.zio::zio-amqp:1.0.0
// Run from this folder:  scala-cli run CompleteExample.scala
// Or from the repository root:  sbt "zio-amqp-examples/runMain zio.amqp.examples.tutorial.CompleteExample"

package zio.amqp.examples.tutorial

import com.rabbitmq.client.{ Connection, MessageProperties }
import zio._
import zio.amqp._
import zio.amqp.model._

/**
 * Work Queue Tutorial — Putting It Together
 *
 * Publishes ten tasks and processes them with two competing workers, each on its own channel with a prefetch of one.
 */
object CompleteExample extends ZIOAppDefault {

  val queue: QueueName = QueueName("tutorial-tasks")
  val taskCount: Int   = 10

  def worker(
    connection: Connection,
    name: String,
    processed: Ref[Int],
    finished: Promise[Nothing, Unit]
  ): ZIO[Scope, Throwable, Unit] =
    for {
      channel <- Amqp.createChannel(connection)
      _       <- channel.basicQos(1)
      _       <- channel
                   .consume(queue, ConsumerTag(name))
                   .mapZIO { delivery =>
                     val task = new String(delivery.getBody, "UTF-8")
                     for {
                       _ <- Console.printLine(s"[$name] working on $task")
                       _ <- ZIO.sleep(200.millis)
                       _ <- channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
                       n <- processed.updateAndGet(_ + 1)
                       _ <- finished.succeed(()).when(n == taskCount)
                     } yield ()
                   }
                   .runDrain
                   .forkScoped
    } yield ()

  val program: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        connection <- Amqp.connect(AMQPConfig.default)
        channel    <- Amqp.createChannel(connection)
        _          <- channel.queueDeclare(queue, durable = true)
        _          <- channel.purgeQueue(queue)
        processed  <- Ref.make(0)
        finished   <- Promise.make[Nothing, Unit]
        _          <- worker(connection, "worker-1", processed, finished)
        _          <- worker(connection, "worker-2", processed, finished)
        _          <- ZIO.foreachDiscard(1 to taskCount) { i =>
                        channel.publish(
                          exchange = ExchangeName(""),
                          body = s"task-$i".getBytes("UTF-8"),
                          routingKey = RoutingKey(QueueName.unwrap(queue)),
                          props = MessageProperties.PERSISTENT_TEXT_PLAIN
                        )
                      }
        _          <- finished.await
        left       <- channel.messageCount(queue)
        _          <- Console.printLine(s"All $taskCount tasks done, $left messages left in the queue")
      } yield ()
    }

  def run: ZIO[Any, Throwable, Unit] = program
}
```

</details>

When we run it, the two workers alternate, because each takes a new task only when it has finished the last one. The assignment of tasks to workers can differ between runs:

```text
[worker-2] working on task-1
[worker-1] working on task-2
[worker-1] working on task-3
[worker-2] working on task-4
[worker-1] working on task-5
[worker-2] working on task-6
[worker-1] working on task-7
[worker-2] working on task-8
[worker-1] working on task-9
[worker-2] working on task-10
All 10 tasks done, 0 messages left in the queue
```

When the main fiber leaves `ZIO.scoped`, the scope interrupts both workers, which cancels their consumers, closes their channels, and then closes the connection.

## Running the Examples

All runnable code from this tutorial is in [`zio-amqp-examples/src/main/scala/zio/amqp/examples/tutorial/`](https://github.com/zio/zio-amqp/tree/master/zio-amqp-examples/src/main/scala/zio/amqp/examples/tutorial). Start RabbitMQ with `docker compose up -d`, then run any file from that folder with `scala-cli run <File>.scala`, or from the repository root with `sbt "zio-amqp-examples/runMain zio.amqp.examples.tutorial.<ObjectName>"`. Stop the broker with `docker compose down`.

## Where to Go Next

Several how-to guides take the work queue further:

- [Acknowledge Messages](./how-to-acknowledge-messages.md) covers rejecting and requeueing failed tasks.
- [Limit Prefetch to Apply Back-Pressure](./how-to-limit-prefetch.md) explains choosing the prefetch count.
- [Stop a Consumer](./how-to-stop-a-consumer.md) shows shutdown patterns for long-running workers.
- [Route Messages with Exchanges](./how-to-route-with-exchanges.md) goes beyond the default exchange.
