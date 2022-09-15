---
id: from-akka
title: "How to Migrate from Akka to ZIO?"
sidebar_label: "Migration from Akka"
---

## Overview

Here, we summarized alternative ZIO solutions for Akka Actor features. So before starting the migration, let's see an overview of corresponding features in ZIO:

| Concerns                | Akka                  | ZIO                                   |
|-------------------------|-----------------------|---------------------------------------|
| Concurrency             | Akka Actor            | ZIO + Concurrent Data Types           |
| Streaming               | Akka Streams          | ZIO Streams                           |
| Event Sourcing and CQRS | Lagom Framework       | ZIO Entity                            |
| Scheduling              | Akka Scheduler        | Built-in Support (Schedule data type) |
| Cron-like Scheduling    | Akka Quartz Scheduler | Built-in Support (Schedule data type) |
| Resiliency              | Akka CircuitBreaker   | Rezilience                            |
| Logging                 | Built-in Support      | Built-in Support (ZLogger)            |
| Testing                 | Akka Testkit          | ZIO Test                              |
| Testing Streams         | Akka Stream Testkit   | ZIO Test                              |
| Logging                 | Akka SL4J             | ZIO Logging SLF4J                     |
| Caching                 | Akka HTTP Caching     | ZIO Cache                             |
| Metrics                 | Yes                   | Yes                                   |
| Supervison              | Yes                   | Yes                                   |
| Monitoring              | Yes                   | Yes                                   |

There are also several integration libraries for Akka that cover a wide range of technologies. If you use any of these technologies, you have a chance to use the equivalent of them in the ZIO ecosystem:

| Tools                | Alpakka                      | ZIO Connect        |
|----------------------|------------------------------|--------------------|
| gRPC                 | Akka gRPC                    | ZIO gRPC           |
| GraphQL              | Sangria                      | Caliban            |
| Apache Kafka         | Akka Stream Kafka            | ZIO Kafka          |
| AWS S3               | Alpakka S3                   | ZIO AWS S3         |
| AWS SNS              | Alpakka SNS                  | ZIO AWS SNS        |
| AWS SQS              | Alpakka SQA                  | ZIO SQS            |
| AMQP                 | Alpakka AMQP                 | ZIO AMQP           |
| AWS Kinesis          | Alpakka Kinesis              | ZIO Kinesis        |
| AWS DynamoDB         | Alpakka DynamoDB             | ZIO DynamoDB       |
| Pulsar               | Pulsar4s                     | ZIO Pulsar         |
| AWS Lambda           | Alpakka AWS Lambda           | ZIO AWS Lambda     |
|                      | Alpakka Cassandra            | ZIO Cassandra      |
| Elasticsearch        | Alpakka Elasticsearch        | ZIO Elasticsearch  |
| FTP                  | Alpakka  FTP                 | ZIO FTP            |
|                      |                              | ZIO MongoDB        |
| Redis                |                              | ZIO Redis          |
| Data Codecs          | Alpakka Avro Parquet         | ZIO Schema         |
| HTTP                 | Akka Http                    | ZIO HTTP           |
|                      |                              | ZIO NIO            |
| Slick                | Alpakka Slick                | ZIO Slick Interop  |
| Streaming TCP        | Akka TCP                     | ZIO TCP            |
|                      |                              | ZIO GCP Firestore  |
| Google Cloud Pub/Sub | Alpakka Google Cloud Pub/Sub | ZIO GCP Pub/Sub    |
|                      |                              | ZIO GCP Redis      |
| Google Cloud Storage | Alpakka Google Cloud Storage | ZIO GCP Storage    |
|                      | Alpakka Influxdb             | anakos/influx      |
| Json                 | Alpakka JSON Streaming       | ZIO JSON           |
| OrientDB             | Alpakka OrientDB             | ZIO Quill OrientDB |

We also have several other libraries that may not be covered by the Akka ecosystem, but you can still use them. So we encourage you to check the ecosystem section of the ZIO website; take a look at the libraries, and see if they are suitable for your requirements for the migration process.

## Akka Actors Are Not Composable

Akka actors are modeled as a partial function from `Any` to `Unit`:

```scala
type Actor = PartialFunction[Any, Unit]
```

In other words, an actor accepts something and does something with it. Both its input and output are not well typed. It is just like a blackbox:

We know that having these types of functions is not composable. So it is hard to write small pieces of actors and compose them together to build large applications.

## Akka Use-cases

Before starting the migration, we need to understand what types of use-cases developers use Akka for.

Akka is a toolkit for building highly concurrent, distributed, and resilient message-driven applications. Here are the most common use-cases for Akka among developers:

1. Parallelism
2. Concurrent State Management
3. Managing Highly Congestion Workflows
4. Event Sourcing
5. Distributed Computing

Let's see an example of each use-case in a simple application using Akka.

## Parallelism

### Parallelism in Akka

We can achieve parallelism in Akka by creating multiple instances of an actor and sending messages to them. Akka takes care of how to route messages to these actors. In the following example, we have a simple `JobRunner` actor which accepts `Job` messages and runs them one by one:

```scala mdoc:silent
import akka.actor._

case class Job(n: Int)

class JobRunner extends Actor {
  override def receive = { case Job(n) =>
    println(s"job$n — started")
    Thread.sleep(1000)
    println(s"job$n — finished")
  }
}
```

If we have plenty of jobs to run, we can create a pool of `JobRunner` actors and send them jobs to make them run in parallel:

```scala mdoc:compile-only
import akka.actor._
import akka.routing.RoundRobinPool

object MainApp extends scala.App {
  val actorSystem = ActorSystem("parallel-app")
  val jobRunner = actorSystem.actorOf(
    Props[JobRunner].withRouter(RoundRobinPool(4)),
    "job-runner"
  )

  for (job <- (1 to 10).map(Job)) {
    jobRunner ! job
  }
}
```

### Parallelism in ZIO

In ZIO we can achieve the same functionality easily by using `ZIO.foreachPar` operators:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {

  def jobRunner(n: Int) =
    for {
      _ <- Console.printLine(s"job$n - started")
      _ <- ZIO.sleep(1.second)
      _ <- Console.printLine(s"job$n - finished")
    } yield ()

  val jobs = (1 to 10)
  
  def run = ZIO.foreachParDiscard(jobs)(jobRunner)
}
```

We use `ZIO.withParallelism` operator to change the default parallelism factor:

```scala
ZIO.withParallelism(4) {
  ZIO.foreachParDiscard(jobs)(jobRunner)
} 
```

## Concurrent State Management

### State Management in Akka

The main purpose of Akka actors is to write concurrent stateful applications. Using Akka actors, we can have stateful actors without worrying about concurrent access to the shared state. In the following example, we have a simple `Counter` actor which accepts `inc` and `dec` messages and increments or decrements its internal state:

```scala mdoc:silent
import akka.actor.Actor

class Counter extends Actor {
  private var state = 0

  override def receive: Receive = {
    case "inc" =>
      state += 1
    case "dec" =>
      state -= 1
    case "get" =>
      sender() ! state
  }
}
```

Now we can create an instance of the `Counter` actor and send it `inc` and `dec` messages to increment and decrement its internal state:

```scala mdoc:compile-only
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.{Failure, Success}

object MainApp extends App {
  val system = ActorSystem("counter-app")
  val counterActor = system.actorOf(Props[Counter], "counter")

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val timeout: Timeout = Timeout(1.second)

  counterActor ! "inc"
  counterActor ! "inc"
  counterActor ! "inc"
  counterActor ! "dec"

  (counterActor ? "get").onComplete {
    case Success(v) =>
      println(s"The current value of counter: $v")
    case Failure(e) =>
      println(s"Failed to receive the result from the counter: ${e.getMessage}")
  }

}
```

Outside the actor, we haven't access to its internal states, so we can't modify it directly. We can only send messages to the actor and let it handle the state management on its own. Using this approach, we can have safe concurrent state management. If multiple actors send messages to this actor concurrently, they can't make the state inconsistent.

### State Management in ZIO

State management is very easy in ZIO in presence of the `Ref` data type. `Ref` models a mutable state which is safe for concurrent access:

```scala mdoc:silent:nest
import zio._

case class Counter(state: Ref[Int]) {
  def inc = state.update(_ + 1)
  def dec = state.update(_ - 1)
  def get = state.get
}

object Counter {
  def make = Ref.make(0).map(Counter(_))
}
```

That's it! Very simple! We now we can use the counter in a program:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      c <- Counter.make
      _ <- c.inc <&> c.inc <&> c.inc <&> c.dec
      _ <- c.get.debug("The current value of the counter")
    } yield ()
}
```

## Buffering in Highly Congestion Workloads

Sometimes we have to deal with a high volume of incoming requests. In spite of parallelism and concurrency, we may not be able to handle all incoming requests within a short period of time. In such cases, we can use a buffer to store incoming requests temporarily and process them later.

### Buffering Workloads in Akka

Each actor in Akka has a mailbox that is used to store incoming messages. The mailbox is a FIFO queue. Actors only process one message at a time. If an actor receives more messages than it can process, the messages will be pending in the mailbox:

```scala
object MainApp extends scala.App {
  val actorSystem = ActorSystem("parallel-app")
  val worker = actorSystem.actorOf(Props[JobRunner], "worker")

  val jobs = (1 to 1000).map(Job)

  for (job <- jobs) {
    worker ! job
  }

  println("All messages were sent to the actor!")
}
```

If we run the above example, we can see that all messages are sent to the actor before, and the actor still processing messages from the mailbox one by one. By default, the mailbox is an unbounded FIFO queue. But we can also use a bounded mailbox or a custom priority mailbox.

### Buffering Workloads in ZIO

ZIO has a built-in data type called `Hub` which is useful for buffering workloads:

```scala mdoc:silent
import zio._
import zio.stream._

class Actor[I] private (private val hub: Hub[I]) {
  def tell(i: I): UIO[Boolean] = hub.publish(i)
}

object Actor {
  def make[I](receive: I => UIO[Unit]): ZIO[Scope, Nothing, Actor[I]] =
    ZIO.acquireRelease {
      for {
        hub     <- Hub.unbounded[I]
        promise <- Promise.make[Nothing, Unit]
        startActor =
          ZStream
            .unwrapScoped(
              ZStream.fromHubScoped(hub) <* promise.succeed(())
            )
            .mapZIO(receive)
            .runDrain
            .forever
        fiber <- startActor.forkScoped
        _ <- promise.await
      } yield (new Actor(hub), fiber)
    }(_._2.join).map(_._1)
}
```

Now we can send a high load of messages to the actor and the actor will process them one by one:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      actor <- Actor.make[Int](n => ZIO.debug(s"processing job-$n").delay(1.second))
      _     <- ZIO.foreachParDiscard(1 to 100)(actor.tell)
      _     <- ZIO.debug("All messages were sent to the actor!")
    } yield ()
  }
}
```

## Event Sourcing

Actors are a good fit for event sourcing. In event sourcing, we store the events that happened in the past and use them to reconstruct the current state of the application. Akka has a built-in solution called Akka Persistence.

In the following example, we have a simple `PersistentCounter` actor which accepts `inc` and `dec` messages and increments or decrements in its internal state and also sores incoming events in persistent storage. When the actor is restarted, it will recover its state from the persistent storage:

```scala mdoc:invisible:reset

```

```scala mdoc:compile-only
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.persistence._
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.{Failure, Success}

class PersistentCounter extends PersistentActor {
  private var state: Int = 0

  override def receive = {
    case "inc" => persist("inc")(_ => state += 1)
    case "dec" => persist("dec")(_ => state -= 1)
    case "get" => sender() ! state
  }

  override def receiveRecover = {
    case "inc" => state += 1
    case "dec" => state -= 1
  }

  override def receiveCommand: Receive = _ => ()

  override def persistenceId: String = "my-persistence-id"
}

object MainApp extends App {
  val system = ActorSystem("counter-app")
  val counter = system.actorOf(Props[PersistentCounter], "counter")

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val timeout: Timeout = Timeout(1.second)

  counter ! "inc"
  counter ! "inc"
  counter ! "inc"
  counter ! "dec"

  (counter ? "get").onComplete {
    case Success(v) =>
      println(s"Current value of the counter: $v")
    case Failure(e) =>
      println(s"Failed to receive the result from the counter: ${e.getMessage}")
  }

}
```

### Clustering

## Modeling Actors Using ZIO
