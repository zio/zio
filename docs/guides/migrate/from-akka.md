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
3. Event Sourcing
4. Distributed Computing

Let's see an example of each use-case in a simple application using Akka.

### Parallelism

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

## Modeling Actors Using ZIO

### Parallelism

### Concurrent State (Low Contention)

### Concurrent State (High Contention)
