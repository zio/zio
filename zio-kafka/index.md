# Getting Started with ZIO Kafka

> [ZIO Kafka](https://github.com/zio/zio-kafka) is a Kafka client for ZIO. It provides a purely functional, streams-based interface to the Kafka
client and integrates effortlessly with ZIO and zio-streams. Often zio-kafka programs have a _higher_ throughput than
programs that use the Java Kafka client directly (see section [Performance](#performance) below).

[ZIO Kafka](https://github.com/zio/zio-kafka) is a Kafka client for ZIO. It provides a purely functional, streams-based interface to the Kafka
client and integrates effortlessly with ZIO and zio-streams. Often zio-kafka programs have a _higher_ throughput than
programs that use the Java Kafka client directly (see section [Performance](#performance) below).

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-kafka/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-kafka_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-kafka_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-kafka_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-kafka_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-kafka-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-kafka-docs_2.13) [![ZIO Kafka](https://img.shields.io/github/stars/zio/zio-kafka?style=social)](https://github.com/zio/zio-kafka) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## Introduction

Apache Kafka is a distributed event streaming platform that acts as a distributed publish-subscribe messaging system. It enables us to build distributed streaming data pipelines and event-driven applications.

Kafka has a mature Java client for producing and consuming events, but it has a low-level API. Zio-kafka is a ZIO native client for Apache Kafka. It has a high-level streaming API on top of the Java client. So we can produce and consume events using the declarative concurrency model of zio-streams. In addition, zio-kafka supports an even higher level API where you only write the processing part and the rest is handled by zio-kafka.

## Features

- Exposes the Java Kafka consumer, producer and admin clients with a ZIO based interface.
- Consuming:
  - 2 APIs: streaming and ZIO workflow based
  - supports custom deserialization
  - process each partition in parallel for highest throughput
  - allows batched processing for highest throughput
  - configurable per partition pre-fetching (with back-pressure)
  - the only async Kafka consumer [without duplicates](https://zio.dev/zio-kafka/preventing-duplicates) after a rebalance (as far as we know)
  - very configurable
  - automatic or manual starting offset
  - supports external commits
  - retries after authentication/authorization errors
  - exposes metrics
  - diagnostics API
- Producing:
  - 2 APIs: streaming and ZIO workflow based
  - supports custom serialization
  - allows for batches for highest throughput
  - optionally await broker acknowledgements
  - optional retries after authentication/authorization errors
- Admin API:
  - exposes all the admin client methods with a ZIO based interface
- Proper errors when broker expects SSL (no [OOM crashes](https://issues.apache.org/jira/browse/KAFKA-4090))
- Test kit with embedded kafka broker
- Well documented
- Community support via [Discord](https://discord.com/channels/629491597070827530/629497941719121960)
- Commercial support via [Ziverge](https://www.ziverge.com)

## Getting started

See the [zio-kafka tutorial](https://zio.dev/guides/tutorials/producing-consuming-data-from-kafka-topics/) for a grand tour of the different ways you can use zio-kafka.

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-kafka"         % "3.2.0"
libraryDependencies += "dev.zio" %% "zio-kafka-testkit" % "3.2.0" % Test
```

Snapshots are available on Sonatype's snapshot repository https://oss.sonatype.org/content/repositories/snapshots.
[Browse here](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-kafka_3/) to find available versions.

If you use `zio-kafka` 3.x, scala 3, `zio-kafka-testkit` and `embedded-kafka`, you also need to add the following to
your `build.sbt` file:

```scala
excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13"
```

## Example

Let's write a simple Kafka producer and consumer using zio-kafka with zio-streams. Before everything, we need a running instance of Kafka. We can do that by saving the following docker-compose script in the `docker-compose.yml` file and run `docker compose up`:

```docker-compose
services:
  broker:
    image: apache/kafka:3.9.0
    container_name: broker
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_BROKER_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://broker:29092,CONTROLLER://broker:29093,PLAINTEXT_HOST://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://broker:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@broker:29093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
```

Now, we can run our ZIO Kafka Streaming application:

```scala

object ReadmeExample extends ZIOAppDefault {

  private val producerRun: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        producer <-
          Producer.make(
            ProducerSettings(List("localhost:9092"))
          )
        _ <- ZStream
          .fromSchedule(Schedule.fixed(2.seconds))
          .mapZIO(_ => Random.nextIntBetween(0, Int.MaxValue))
          .mapZIO { random =>
            producer.produce[Any, Long, String](
              topic = "random-topic",
              key = (random % 4).toLong,
              value = random.toString,
              keySerializer = Serde.long,
              valueSerializer = Serde.string
            )
          }
          .runDrain
      } yield ()
    }

  private val consumerRun: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        consumer <-
          Consumer.make(
            ConsumerSettings(List("localhost:9092"))
              .withGroupId("group")
          )
        _ <- consumer
          .plainStream(Subscription.topics("random"), Serde.long, Serde.string)
          .tap(r => Console.printLine(r.value))
          .map(_.offset)
          .aggregateAsync(Consumer.offsetBatches)
          .mapZIO(_.commit)
          .runDrain
      } yield ()
    }

  override def run: ZIO[Any, Throwable, Unit] =
    ZIO.raceFirst(producerRun, List(consumerRun))
}
```

## Documentation

If you are reading this page from https://zio.dev/zio-kafka/, the documentation is in the sub-pages and can be found by
scrolling the left panel of this page.

If you are reading this from GitHub, the documentation can be found in
[the docs folder](https://github.com/zio/zio-kafka/tree/master/docs).

## Resources

### Articles

- [ZIO Kafka tutorial](https://zio.dev/guides/tutorials/producing-consuming-data-from-kafka-topics/) by the zio-kafka team
- [ZIO Kafka faster than Java Kafka](https://day-to-day-stuff.blogspot.com/2024/12/zio-kafka-faster-than-java-kafka.html) by Erik van Oosten (December 2024)
- [Introduction to zio-kafka](https://www.baeldung.com/scala/zio-kafka-intro) by Stefanos Georgakis, Baeldung (January 2023)
- [How to implement streaming microservices with ZIO 2 and Kafka](https://scalac.io/blog/streaming-microservices-with-zio-and-kafka/) by Jorge Vasquez, Scalac (June 2023)
- [Zio Kafka](https://medium.com/@knoldus/zio-kafka-d865fc20174a) by Knoldus Inc (January 2022)
- [Writing a Simple Producer and Consumer Using ZIO Workflows](https://pramodshehan.medium.com/writing-a-simple-producer-and-consumer-using-zio-workflows-a57def08210c) by Pramod Shehan (December 2022)
- [ZIO Kafka with transactions - a debugging story](https://www.ziverge.com/post/zio-kafka-with-transactions---a-debugging-story/) by Daniel Vigovszky, Ziverge (June 2022)
- [Introduction to Zio-Kafka](https://blog.knoldus.com/introduction-to-zio-kafka/) by Akash Kumar (March 2022)
- [Introduction to Zio-Kafka](https://blog.nashtechglobal.com/introduction-to-zio-kafka/) by Khalid Ahmed, Nash Tech (March 2022)
- [ZIO Kafka: A Practical Streaming Tutorial](https://rockthejvm.com/articles/zio-kafka/) by Riccardo Cardin, Rock the JVM (August 2021)
- [Using ZIO Kafka with offset storage in Postgres for transactional processing](https://functional.works-hub.com/learn/using-zio-kafka-with-offset-storage-in-postgres-for-transactional-processing-be4a2) by Marek Kadek (March 2021)
- [Streaming microservices with ZIO and Kafka](https://scalac.io/streaming-microservices-with-zio-and-kafka/) by Aleksandar Skrbic (February 2021)
- [An Introduction to ZIO Kafka](https://www.ziverge.com/post/an-introduction-to-zio-kafka/) by Ziverge (April 2020)

### Video

- [Free ZIO Kafka course](https://www.learnscala.dev/) Free video training courses by Alvin Alexander, sponsored by Ziverge (2025) to learn Scala 3, Functional Programming, and ZIO 2. The zio-kafka module can be followed separately.
- [Optimizing Data Transfer Kafka to BQ: let's use Scala to make it custom](https://www.youtube.com/watch?v=McnC2UU-RIE) by Dario Amorosi, Adevinta (November 2024)
- [Making ZIO-Kafka Safer and Faster in 2023](https://www.youtube.com/watch?v=MJoRwEyyVxM) by Erik van Oosten (November 2023)
- [ZIO Kafka with Scala: A Tutorial](https://www.youtube.com/watch?v=ExFjjczwwHs) by Rock the JVM (August 2021)
- [ZIO WORLD - ZIO Kafka](https://www.youtube.com/watch?v=GECv1ONieLw) by Aleksandar Skrbic (March 2020) — Aleksandar Skrbic presented ZIO Kafka, a critical library for the modern Scala developer, which hides some of the complexities of Kafka.

### Example projects

- [Kafka BigQuery Express](https://github.com/adevinta/kafka-bigquery-express/) by Adevinta (November 2024) A production system to copy data from Kafka to BigQuery, safely and cost-effectively. (See also the video "Optimizing Data Transfer...".)
- [zio-kafka-showcase](https://github.com/ScalaConsultants/zio-kafka-showcase) by Jorge Vásquez, Example project that demonstrates how to build Kafka based microservices with Scala and ZIO
- [zio-kafka-demo1](https://github.com/pramodShehan5/zio-kafka-demo1) (December 2022), example consumer and producer using zio-kafka 2.0.5
- [zio-kafka-example-app](https://github.com/zivergetech/zio-kafka-example-app) by Ziverge (December 2020), example application using zio-kafka 0.8.0

## Adopters

Here is a partial list of companies using zio-kafka in production.

Want to see your company here? [Submit a PR](https://github.com/zio/zio-kafka/edit/master/docs/index.md)!

* [Conduktor](https://www.conduktor.io)
* [KelkooGroup](https://www.kelkoogroup.com)
* [Rocker](https://rocker.com)

## Performance

Often, _zio-kafka programs consume with a higher throughput_ than programs that use the java-kafka client directly.
Read on for the details.

By default, zio-kafka programs process partitions in parallel. The default java-kafka client does not provide parallel
processing. Of course, there is some overhead in buffering records and distributing them to the fibers that need them.
On 2024-11-23, we estimated that zio-kafka consumes faster than the java-kafka client when processing takes more than
~1.2ms per 1000 records. The precise time depends on many factors. Please
see [this article](https://day-to-day-stuff.blogspot.com/2024/12/zio-kafka-faster-than-java-kafka.html) for more
details.

If you do not care for the convenient ZStream based API that zio-kafka brings, and latency is of absolute importance,
using the java based Kafka client directly is still the better choice.

## Developers

* [Benchmarks and Flame graphs](https://github.com/zio/zio-kafka/blob/master/zio-kafka-bench/README.md)
