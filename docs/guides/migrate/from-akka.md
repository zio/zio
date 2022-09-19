---
id: from-akka
title: "How to Migrate from Akka to ZIO?"
sidebar_label: "Migration from Akka"
---

## Overview

Here, we summarized alternative ZIO solutions for Akka Actor features. So before starting the migration, let's see an overview of corresponding features in ZIO:

| Concerns                | Akka                                        | ZIO                                   |
|-------------------------|---------------------------------------------|---------------------------------------|
| Concurrency             | [Akka Actor][1]                             | [ZIO][2] + [Concurrent Data Types][3] |
| Streaming               | [Akka Streams][4]                           | [ZIO Streams][5]                      |
| Event Sourcing and CQRS | [Lagom Framework][6], [Akka Persistence][8] | [ZIO Entity][7], [Edomata][9]         |
| Scheduling              | [Akka Scheduler][10]                        | [Schedule data type][11]              |
| Cron-like Scheduling    | [Akka Quartz Scheduler][12]                 | [Schedule data type][11]              |
| Resiliency              | [Akka CircuitBreaker][13]                   | [Rezilience][14]                      |
| Logging                 | [Built-in Support][15]                      | [Built-in Support (ZLogger)][16]      |
| Testing                 | [Akka Testkit][17]                          | [ZIO Test][18]                        |
| Testing Streams         | [Akka Stream Testkit][19]                   | [ZIO Test][18]                        |
| Metrics                 | [Cluster Metric Extension][20]              | [Metrics][21]                         |
| Supervision             | [Yes][22]                                   | Yes                                   |
| Monitoring              | [Yes][22]                                   | Yes                                   |

There are also several integration libraries for Akka that cover a wide range of technologies. If you use any of these technologies, you have a chance to use the equivalent of them in the ZIO ecosystem:

| Tools                | Alpakka                            | ZIO Connect                            |
|----------------------|------------------------------------|----------------------------------------|
| gRPC                 | [Akka gRPC][23]                    | [ZIO gRPC][24]                         |
| GraphQL              | [Sangria][25]                      | [Caliban][26]                          |
| Apache Kafka         | [Alpakka Kafka][27]                | [ZIO Kafka][28]                        |
| AWS                  |                                    | [ZIO AWS][30]                          |
| AWS S3               | [Alpakka S3][29]                   | [ZIO S3][32]                           |
| AWS SNS              | [Alpakka SNS][31]                  | [ZIO AWS SNS][30]                      |
| AWS SQS              | [Alpakka SQS][33]                  | [ZIO SQS][34], [ZIO AWS SQS][30]       |
| AMQP                 | [Alpakka AMQP][35]                 | [ZIO AMQP][36]                         |
| AWS Kinesis          | [Alpakka Kinesis][37]              | [ZIO Kinesis][38]                      |
| AWS DynamoDB         | [Alpakka DynamoDB][39]             | [ZIO DynamoDB][40]                     |
| Pulsar               | [Pulsar4s][41]                     | [ZIO Pulsar][42]                       |
| AWS Lambda           | [Alpakka AWS Lambda][43]           | [ZIO Lambda][44], [ZIO AWS Lambda][30] |
|                      | [Alpakka Cassandra][45]            | [ZIO Cassandra][46]                    |
| Elasticsearch        | [Alpakka Elasticsearch][47]        | [ZIO Elasticsearch][48]                |
| FTP                  | [Alpakka FTP][49]                  | [ZIO FTP][50]                          |
|                      |                                    | [ZIO MongoDB][51]                      |
| Redis                |                                    | [ZIO Redis][52]                        |
| Data Codecs          | [Alpakka Avro Parquet][53]         | [ZIO Schema][54]                       |
| HTTP                 | [Akka Http][55]                    | [ZIO HTTP][56]                         |
|                      |                                    | [ZIO NIO][57]                          |
| Slick                | [Alpakka Slick][58]                | [ZIO Slick Interop][59]                |
| Streaming TCP        | [Akka TCP][60]                     | [ZIO TCP][61]                          |
| Google Cloud Pub/Sub | [Alpakka Google Cloud Pub/Sub][62] | [ZIO GCP Pub/Sub][63]                  |
| Google Cloud Storage | [Alpakka Google Cloud Storage][64] | [ZIO GCP Storage][63]                  |
| Json                 | [Alpakka JSON Streaming][65]       | [ZIO JSON][66]                         |
| OrientDB             | [Alpakka OrientDB][67]             | [ZIO Quill OrientDB][68]               |
| Logging              | [Akka SL4J][69]                    | [ZIO Logging SLF4J][70]                |
| Caching              | [Akka HTTP Caching][71]            | [ZIO Cache][72]                        |

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
6. Streaming

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

ZIO has a data type called `Queue` which is useful for buffering workloads:

```scala mdoc:silent
import zio._
import zio.stream._

trait Actor[-In] {
  def tell(i: In): UIO[Boolean]
}

object Actor {
  def make[In](receive: In => UIO[Unit]): ZIO[Scope, Nothing, Actor[In]] =
    ZIO.acquireRelease {
      for {
        queue <- Queue.unbounded[In]
        fiber <- queue.take.flatMap(receive).forever.fork
        actor = new Actor[In] {
                  override def tell(i: In): UIO[Boolean] =
                    queue.offer(i)
                }
      } yield (actor, fiber)
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
      _     <- ZIO.foreachParDiscard(1 to 1000)(actor.tell)
      _     <- ZIO.debug("All messages were sent to the actor!")
    } yield ()
  }
}
```

## Event Sourcing

### Event Sourcing in Akka

In event sourcing, we store the events that happened in the past and use them to reconstruct the current state of the application. Akka has a built-in solution called Akka Persistence.

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

### Event Sourcing in ZIO

In the ZIO ecosystem, we have a community library called [ZIO Entity](https://github.com/thehonesttech/zio-entity) which aims to provide a distributed event-sourcing solution. Although it is not production-ready yet, there is nothing to worry about. We can write our toolbox or use other libraries from other functional effect ecosystems using ZIO Interop.

Note that based on your use case, you may don't need any event-sourcing framework or library. So before using any library, you should consider if you really need it or not. In many cases, this is very dependent on our domain and business requirements, and using a library may mislead you in the wrong direction.

Anyway, there is a purely functional library called [Edomata](https://edomata.ir/) which provides a simple solution for event sourcing. It is written in a tagless final style using [Cats Effect](https://typelevel.org/cats-effect/) and [Fs2](https://fs2.io/). We can use ZIO Interop to use the ZIO effect to run an Edomaton.

In the following example, we are going to use Edomata to implement a simple counter which is event sourced. To do so, we need to define Events, Commands, State, Rejections, and Transitions:

After finding out domain events, we can define them using enum or sealed traits. In this example, we have two `Increased` and `Decreased` events:

```scala
enum Event {
  case Increased, Decreased
}
```

Then we should define the commands our domain can handle:

```scala
enum Command {
  case Inc, Dec
}
```

To notify some information data to the outside world, we can use `Notification`s, let's define a simple notification data type:

```scala
case class Notification(message: String)
```

Now it's time to define the domain model and the state of the counter:

```scala
import cats.data.ValidatedNec
import edomata.core.{Decision, DomainModel}
import cats.implicits.*
import edomata.syntax.all.*

case class Counter(state: Int) {
  def inc = this.perform { Decision.accept(Event.Increased) }
  def dec = this.perform {
    if (state > 0) Decision.accept(Event.Decreased) else "decision rejected".reject
  }
}

object Counter extends DomainModel[Counter, Event, String] {
  override def initial: Counter = Counter(0)

  override def transition = {
    case Event.Increased => state => state.copy(state = state.state + 1).validNec
    case Event.Decreased => state => state.copy(state = state.state - 1).validNec
  }
}
```

We are ready to define the `CounterService`:

```scala
object CounterService extends Counter.Service[Command, Notification] {
  import App._
  def apply(): PureApp[Unit] = router {
    case Command.Inc =>
      for {
        counter <- state.decide(_.inc)
        _ <- publish(Notification(s"state is going to become ${counter.state}"))
      } yield ()
    case Command.Dec => state.decide(_.dec).void
  }
}
```

So far, we have defined an edomaton called `CounterService`. To run it, we need a backend:

```scala
import scala.concurrent.duration._
import cats.effect.std.Console
import cats.effect.{Async, Concurrent, Resource}
import edomata.core.{CommandMessage, DomainService}
import edomata.skunk.{BackendCodec, CirceCodec, SkunkBackend}
import edomata.backend.Backend
import edomata.syntax.all.liftTo
import fs2.io.net.Network
import skunk.Session
import io.circe.generic.auto.*
import natchez.Trace
import natchez.Trace.Implicits.noop

object BackendService {
  given BackendCodec[Event] = CirceCodec.jsonb // or .json
  given BackendCodec[Notification] = CirceCodec.jsonb
  given BackendCodec[Counter] = CirceCodec.jsonb

  def backend[F[_]: Async: Concurrent: Trace: Console] =
    SkunkBackend(
      Session
        .single[F]("localhost", 5432, "postgres", "postgres", Some("postgres"))
    )

  def buildBackend[F[_]: Async: Concurrent: Network: Console]
      : Resource[F, Backend[F, Counter, Event, String, Notification]] =
    backend
      .builder(CounterService, "counter")
      .withRetryConfig(retryInitialDelay = 2.seconds)
      .persistedSnapshot(200)
      .build

  def service[F[_]: Async: Concurrent: Network: Console]
      : Resource[F, DomainService[F, CommandMessage[Command], String]] =
    buildBackend
      .map(_.compile(CounterService().liftTo[F]))
}
```

To demonstrate how the backend works, we can write a simple web service that accepts `Inc` and `Dec` commands:

```scala
import zio.*
import zhttp.http.*

import cats.data.EitherNec
import edomata.core.CommandMessage

import java.time.Instant

type Service = CommandMessage[Command] => RIO[Scope, EitherNec[String, Unit]]

object ZIOCounterHttpApp {

  def apply(service: Service) =
    Http.collectZIO[Request] {
      // command: inc or dec
      // GET /{edomaton address}/{command}/{command id}
      case Method.GET -> !! / address / command / commandId =>
        val cmd = command match {
          case "inc" => Command.Inc
          case "dec" => Command.Dec
        }

        service(CommandMessage(commandId, Instant.now, address, cmd))
          .map(r => Response.text(r.toString))
          .orDie
    }
}
```

Now we can wire everything and run the application:

```scala
import zio.*
import zio.interop.catz.*
import zhttp.http.*
import zhttp.service.Server
import cats.effect.std.Console

object MainApp extends ZIOAppDefault {
  given Console[Task] = Console.make[Task]

  def run =
    ZIO.scoped {
      for {
        backendService <- BackendService.service.toScopedZIO
        _ <- Server.start(8090, ZIOCounterHttpApp(backendService))
      } yield ()
    }
}
```

To test the application, we can send the following requests:

```http
GET /FooCounter/inc/cf2209c9-6b41-44da-8c52-7e0dce109dc3
GET /FooCounter/inc/11aa920d-254e-4aa7-86f2-8002df80533b
GET /FooCounter/dec/b2e8a02c-77db-463d-8213-d462fc5a9439
GET /FooCounter/inc/9ac51e44-36d0-4daa-ac23-28e624bec174
```

If multiple commands with the same command id are sent, the backend only processes the first one and ignores the rest. If the command is rejected, the backend will not accept any subsequent commands.

To see all the events or the current state associated with the `FooCounter` adomaton, we can use the `Backend#repository` to query the database:

```scala
import zio.*
import zio.interop.catz.*
import zio.stream.interop.fs2z._
import cats.effect.std.Console

object ZIOStateAndHistory extends ZIOAppDefault {
  given Console[Task] = Console.make[Task]

  def run =
    ZIO.scoped {
      for {
        backendService <- BackendService.buildBackend.toScopedZIO.orDie
        history <- backendService.repository
          .history("FooCounter")
          .toZStream()
          .runCollect
        state <- backendService.repository.get("FooCounter")
        _ <- ZIO.debug(s"FooCounter History: $history")
        _ <- ZIO.debug(s"FooCounter State: $state")
      } yield ()
    }
}

// Output:
// FooCounter History: Chunk(Valid(Counter(0),0),Valid(Counter(1),1),Valid(Counter(2),2),Valid(Counter(1),3),Valid(Counter(2),4))
// FooCounter State: Valid(Counter(2),4)
```

That's it! By using functional programming instead of Akka actors, we implemented a simple event sourced counter.


## Streaming

### Streaming with Akka

Akka stream is developed on top of Akka actors with backpressure support. There are three main components in Akka streams:

1. Source
2. Sink
3. Flow

Here is a simple example of how to have a streaming app in Akka:

```scala mdoc:compile-only
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.util.ByteString

import java.nio.file.Paths
import scala.concurrent._

object AkkaStreamApp extends App {
  implicit val system: ActorSystem = ActorSystem("stream")
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val source = Source(1 to 100)
  val factorial = Flow[Int].scan(BigInt(1))((acc, next) => acc * next)
  val serialize = Flow[BigInt].map(num => ByteString(s"$num\n"))
  val sink = FileIO.toPath(Paths.get("factorials.txt"))

  source
    .via(factorial)
    .via(serialize)
    .runWith(sink)
    .onComplete(_ => system.terminate())
}
```

### Streaming in ZIO

ZIO stream is a purely functional, composable, effectful, and resourceful streaming data type for Scala. It provides a way to model streaming data processing as a pure function. It is built on top of ZIO and provides a way to model streaming data processing as a pure function.

Like the Akka terminology, ZIO streams have three main components:

1. [ZStream](../../reference/stream/zstream/index.md)
2. [ZPipeline](../../reference/stream/zpipeline.md)
3. [ZSink](../../reference/stream/zsink/index.md)

Let's see how to implement the same example in ZIO:

```scala mdoc:compile-only
import zio._
import zio.stream._

object ZIOStreamApp extends ZIOAppDefault {
  val source    = ZStream.fromIterable(1 to 100)
  val factorial = ZPipeline.scan(BigInt(1))((acc, next: Int) => acc * next)
  val serialize = ZPipeline.map((num: BigInt) => Chunk.fromArray(s"$num".getBytes))
  val sink      = ZSink.fromFileName("factorials.txt")

  def run = 
    source
      .via(factorial)
      .via(serialize).flattenChunks
      .run(sink)
}
```

## Modeling Actors Using ZIO

[1]: https://doc.akka.io/docs/akka/current/index.html
[2]: https://zio.dev/reference/core/zio/
[3]: https://zio.dev/reference/concurrency/
[4]: https://doc.akka.io/docs/akka/current/stream/index.html
[5]: ../../reference/stream
[6]: https://www.lagomframework.com/
[7]: https://github.com/thehonesttech/zio-entity
[8]: https://doc.akka.io/docs/akka/current/persistence.html
[9]: https://edomata.ir/
[10]: https://doc.akka.io/docs/akka/2.5.32/scheduler.html
[11]: ../../reference/schedule
[12]: https://github.com/enragedginger/akka-quartz-scheduler
[13]: https://doc.akka.io/docs/akka/current/common/circuitbreaker.html
[14]: https://www.vroste.nl/rezilience/
[15]: https://doc.akka.io/docs/akka/current/logging.html
[16]: ../../guides/tutorials/enable-logging-in-a-zio-application.md
[17]: https://doc.akka.io/docs/akka/current/typed/testing.html
[18]: ../../reference/test
[19]: https://doc.akka.io/docs/akka/current/stream/stream-testkit.html
[20]: https://doc.akka.io/docs/akka/current/cluster-metrics.html
[21]: ../../reference/metrics
[22]: https://doc.akka.io/docs/akka/current/general/supervision.html
[23]: https://doc.akka.io/docs/akka-grpc/current/index.html
[24]: ../../ecosystem/community/zio-grpc.md
[25]: https://github.com/sangria-graphql/sangria-akka-http-example
[26]: ../../ecosystem/community/caliban.md
[27]: https://doc.akka.io/docs/alpakka-kafka/current/home.html
[28]: ../../ecosystem/officials/zio-kafka.md
[29]: https://doc.akka.io/docs/alpakka/current/s3.html
[30]: ../../ecosystem/officials/zio-aws.md
[31]: https://doc.akka.io/docs/alpakka/current/sns.html
[32]: https://github.com/zio/zio-s3
[33]: https://doc.akka.io/docs/alpakka/current/sqs.html
[34]: ../../ecosystem/officials/zio-sqs.md
[35]: https://doc.akka.io/docs/alpakka/current/amqp.html
[36]: ../../ecosystem/community/zio-amqp.md
[37]: https://doc.akka.io/docs/alpakka/current/kinesis.html
[38]: ../../ecosystem/community/zio-kinesis.md
[39]: https://doc.akka.io/docs/alpakka/current/dynamodb.html
[40]: https://github.com/zio/zio-dynamodb
[41]: https://doc.akka.io/docs/alpakka/current/external/pulsar.html
[42]: ../../ecosystem/community/zio-pulsar.md
[43]: https://doc.akka.io/docs/alpakka/current/awslambda.html
[44]: https://github.com/zio/zio-lambda
[45]: https://doc.akka.io/docs/alpakka/current/cassandra.html
[46]: https://github.com/palanga/zio-cassandra
[47]: https://doc.akka.io/docs/alpakka/current/elasticsearch.html 
[48]: https://github.com/aparo/zio-elasticsearch
[49]: https://doc.akka.io/docs/alpakka/current/ftp.html
[50]: ../../ecosystem/officials/zio-ftp.md
[51]: https://github.com/mbannour/zio-mongodb
[52]: ../../ecosystem/officials/zio-redis.md
[53]: https://doc.akka.io/docs/alpakka/current/avroparquet.html
[54]: ../../ecosystem/officials/zio-schema.md
[55]: https://doc.akka.io/docs/akka-http/current/index.html
[56]: ../../ecosystem/community/zio-http.md
[57]: ../../ecosystem/officials/zio-nio.md
[58]: https://doc.akka.io/docs/alpakka/current/slick.html
[59]: ../../ecosystem/community/zio-slick-interop.md
[60]: https://doc.akka.io/docs/alpakka/current/external/tcp.html
[61]: https://github.com/searler/zio-tcp
[62]: https://doc.akka.io/docs/alpakka/current/google-cloud-pub-sub.html
[63]: https://github.com/zio/zio-gcp 
[64]: https://doc.akka.io/docs/alpakka/current/google-cloud-storage.html
[65]: https://doc.akka.io/docs/alpakka/current/data-transformations/json.html
[66]: ../../ecosystem/officials/zio-json.md
[67]: https://doc.akka.io/docs/alpakka/current/orientdb.html
[68]: ../../ecosystem/community/quill.md
[69]: https://doc.akka.io/docs/akka/current/typed/logging.html
[70]: ../../ecosystem/officials/zio-logging.md
[71]: https://doc.akka.io/docs/akka-http/current/common/caching.html
[72]: ../../ecosystem/officials/zio-cache.md