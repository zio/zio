---
id: from-akka
title: "How to Migrate from Akka to ZIO?"
sidebar_label: "Migration from Akka"
---

| Concerns                | Akka                  | ZIO                                   |
|-------------------------+-----------------------+---------------------------------------|
| Concurrenty             | Akka Actor            | ZIO + Concurrent Data Types           |
| Streaming               | Akka Streams          | ZIO‌ Streams                           |
| Event Sourcing and CQRS | Lagom Framework       | ZIO Entity                            |
| Scheduling              | Akka Scheduler        | Built-in Support (Schedule data type) |
| Cron-like Scheduling    | Akka Quartz Scheduler | Built-in Support (Schedule data type) |
| Resiliency              | Akka CircutBreaker    | Rezilience                            |
| Logging                 | Built-in Support      | Built-in Support (ZLogger)            |
| Testing                 | Akka Testkit          | ZIO Test                              |
| Testing Streams         | Akka Stream Testkit   | ZIO Test                              |
| Logging                 | Akka SL4J             | ZIO Logging SLF4J                     |
| Caching                 | Akka HTTP Caching     | ZIO Cache                             |
| Metrics                 | Yes                   | Yes                                   |
| Supervison              | Yes                   | Yes                                   |
| Monitoring              | Yes                   | Yes                                   |

|                            | Akka Actor            | ZIO                  |
|----------------------------+-----------------------+----------------------|
| Structured Concurrency     | No                    | Yes                  |
| Concurrency Model          | Actor Model           | Fibers               |
| Composability              | Weak                  | Yes                  |
| Type-safe Error Management | No                    | Yes                  |

## Distributed Systems

| Distributed Systems | Akka | ZIO |
|---------------------+------+-----|
| Remoting            |      |     |
| Clustering          | Yes  | No  |
| Data Distributation | Yes  | No  |
| Discovery           | Yes  | No  |

ZIO solutions:
- ZIO Keeper
- ZIO Flow
- ZIO Shardcake

| Tools                | Alpakka                      | ZIO Connect        |
|----------------------+------------------------------+--------------------|
| gRPC                 | Akka gRPC                    | ZIO gRPC           |
| GraphQL              | Sangria                      | Caliban            |
| Apache Kafka         | Akka Stream Kafka            | ZIO Kafka          |
| AWS S3               | Alpakka S3                   | ZIO AWS S3         |
| AWS SNS              | Alpakka SNS                  | ZIO AWS SNS        |
| AWS SQS              | Alpakka SQA                  | ZIO SQS            |
| AMQP                 | Alpakka AMQP                 | ZIO AMQP           |
| AWS Kinesis          | Alpakka Kinesis              | ZIO AWS Kinesis    |
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
|                      | Alpakka Google Cloud Pub/Sub | ZIO GCP Pub/Sub    |
|                      |                              | ZIO GCP Redis      |
| Google Cloud Storage | Alpakka Google Cloud Storage | ZIO GCP Storage    |
|                      | Alpakka Influxdb             | anakos/influx      |
| Json                 | Alpakka JSON Streaming       | ZIO JSON           |
| OrientDB             | Alpakka OrientDB             | ZIO Quill OrientDB |



|            | Structured Concurrency | Model       | Composable | Type-safe Error Management | Scheduling |
|------------+------------------------+-------------+------------+----------------------------+------------|
| Akka Actor | No                     | Actor Model | No         | No                         | Yes        |
| ZIO Core   | Yes                    | Fibers      | Yes        | Yes                        | Yes        |


## Akka vs. ZIO - Concurrency

## Actor Model

Akka is a toolkit of libraries for building concurrent, distributed, and fault-tolerant applications. It has wide-ranging capabilities. In this section, we will discuss how we can migrate from Akka to ZIO in order to write concurrent applications.

The concurrency model in Akka is based on actor model. Actor model is a model of concurrency in which embraces the object-oriented programming paradigm where encapsulate the state and behavior in a single object. Each actor has a state and behavior and also a mailbox for storing incoming events. The state is represented by a private data structure. The behavior is represented by a partial function that maps received messages from the mailbox to the next state, or sends a message to another actor. So it each actor works like a state machine using message passing style for transitioning between states.

Each actor has a parent and all actors are children of the root (guardian) actor. Each parent actor has reference to all its children. They can supervise their child actors.

## Application Architecture Using Akka Actors

Actors communicate with each other via one of the following patterns:

- tell - By sending a message to an actor using the tell with the `Unit` return type.
- ask - By sending a message to an actor and receiving a `Future` containing the reply message.
- pipeTo - By feeding a future to next actor.

So our final application, comparises of several actors communicating each other.

## Actors and Futures

Akka uses the `Future` as the building block for its concurrency model. The `Future` is a data structure that represents a value that may not yet be available. Both `Future` and `Actor` are asynchronous. In Akka, the `Future` is used mainly when we use the `ask` and `pipeTo` methods.

## Counter Example (Akka)

```scala
import akka.actor.{Actor, ActorSystem, Props}
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.{Failure, Success}

class Counter(var state: Int) extends Actor {
  private val log = Logging(context.system, this)
  
  def receive = {
    case "inc" =>
      log.info("inc message receive!")
      state = state + 1
    case "dec" =>
      log.info("dec message received!")
      state = state - 1
    case "get" =>
      log.info("dec message received!")
      sender() ! state
    case _ =>
      log.warning("received an unknown message!")
  }
}

object MainApp extends App {
  val system = ActorSystem("counter-app")
  val counterActor = system.actorOf(Props(new Counter(0)), "counter")

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val timeout: Timeout = Timeout(1.second)

  counterActor ! "inc" 
  counterActor ! "inc" 
  counterActor ! "inc" 
  counterActor ! "dec" 

  (counterActor ? "get").onComplete {
    case Success(v) =>
      println(s"The final value of counter: $v")
    case Failure(e) =>
      println(s"Failed to receive the result from the counter: ${e.getMessage}")
  }

}
```

## ZIO

ZIO is a library for building type-safe, concurrent, and fault-tolerant applications. The core abstraction in ZIO library for writing concurrent applications is the `ZIO` data type. It is a description of a computation that can be executed in parallel when it is composed with other ZIO workflows.


So in ZIO instead of using actors, we have `ZIO` data type. Each `ZIO` data type represent a 
It's a data type that represents a workflow that can be composed of several `ZIO` workflows. 


ZIO vs Future vs Akka Actor

ZIO has a growing ecosystem ....

With ZIO we can build type-safe composable workflows that finally can be a large application. It provides sequential and parallel composition. And more importantly, it is on top of ZIO which way better that `Future`.

## Counter Example (ZIO)

```scala
import zio._

case class Counter(state: Ref[Int]) {
  def inc = state.update(_ + 1) *> ZIO.log("increased by one")

  def dec = state.update(_ - 1) *> ZIO.log("decreased by one")

  def get = state.get <* ZIO.log("returning the current value of counter")
}

object Counter {
  def make = Ref.make(0).map(Counter(_))
}

object MainApp extends ZIOAppDefault {
  def run =
    for {
      c <- Counter.make
      _ <- c.inc
      _ <- c.inc
      _ <- c.inc
      _ <- c.dec
      _ <- c.get.debug("The final value of counter")
    } yield ()
}
```

ZIO values can be composed both sequentially and in parallel. The previous example is a sequential composition. We can also compose them in parallel like this:

```scala
for {
  c <- Counter.make
  _ <- c.inc <&> c.inc <&> c.inc <&> c.dec
  _ <- c.get.debug("The final value of counter")
} yield ()
```

This was a simple example of how we can achieve the same concurrent functionality with ZIO. But keep in mind that ZIO is even more powerful than Akka in terms of concurrency. Let's review some of the differences:

1. Akka actor is based on the actor model. The most important problem with Akka Actors is that they are not fundamentally composable. Why? Because they have side effects. So for every actor, we can't have a assumption that the actor doesn't have any side effects. Each actor have the ability to perform some side effect and change something outside of the actor. So we lose the ability to build a bigger application out of smaller components easily. Any changes to the input or output of an actor could cause an effect on other actors to be refactored.

When we design the whole application using Akka Actors, it is hard to write loosely coupled components. So by any change in the requirements, we have to make more effort to refactor the code.

On the other hand With ZIO, we use pure functional programming style to build the application. It has a powerful composition model. We can easily compose smaller components to build larger applications and refactor the code easily.

Akka Actor uses the actor for its concurrency model. They are not composable. So when our application grows, its hard to maintain, refactor or change acording new requirements.

## Eager vs. Lazy Evaluation

Another difference between Akka and ZIO is the way they handle the evaluation of the computation. In Akka, computations are evaluated when they are created. So it has an eager evaluation model. In ZIO, we have a lazy evaluation model. So a ZIO application, is composed of series of ZIO values that are description of the whole workflow, finally when we provide the whole application to the `run` method, the ZIO runtime will execute the application.


1. Actor Model
2. Akka Actors
3. Akka Streams
4. Akka Http
5. Akka Cluster
6. Cluster Sharding
7. Distributed Data
8. Akka Persistence
9. Akka Projections
10. Akka Management
11. Alpakka
12. Alpakka Kafka
13. Akka gRPC

14. cloudflow


Akka Insight
- Datadog
- New Relic
- Prometheus
- SL4J events
- Telegraf
- Coda Hale Metrics

OpenTracing Integration
- Jaeger
- Zipkin
- Datadog
- AWS X-Ray


## Usecases

### Concurrency

### Concurrent State

#### Low Contention

#### High Contention

### Persistence


### Distributed Computing