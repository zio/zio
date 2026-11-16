# Getting Started with ZIO Kafka

> This page gets you from an empty sbt project to a running program that produces records to Kafka and consumes them again. We add the zio-kafka dependencies, start a single Kafka broker on our machine with Docker, and then walk through a small application that runs a producer and a consumer side by side.

## Prerequisites

To follow along, you need:

- A JDK, version 17 or later (zio-kafka is tested on Java 17, 21 and 25).
- [sbt](https://www.scala-sbt.org), to build and run the example.
- [Docker](https://docs.docker.com/get-docker/) with Docker Compose, to run a local Kafka broker.

## Adding the Dependencies

Zio-kafka is published for Scala 2.13 and Scala 3. Add the library, and the test kit for your tests, to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-kafka"         % "4.2.0"
libraryDependencies += "dev.zio" %% "zio-kafka-testkit" % "4.2.0" % Test
```

The `zio-kafka` artifact brings in ZIO, zio-streams and the Apache Kafka Java client, so you don't need to add them separately. The `zio-kafka-testkit` artifact provides an embedded Kafka broker and helpers for tests, see [Writing Tests](writing-tests.md).

Two situations need extra build settings: using the test kit on Scala 3, and trying out unreleased versions.

### Using the Test Kit With Scala 3

The test kit depends on `embedded-kafka`, which pulls in the Scala 2.13 build of `scala-collection-compat`. On Scala 3 this clashes with the Scala 3 build of the same library, and sbt fails with "Modules were resolved with conflicting cross-version suffixes". If you use zio-kafka 3.x or later, Scala 3, `zio-kafka-testkit` and `embedded-kafka`, exclude the Scala 2.13 artifact in your `build.sbt` file:

```scala
excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_2.13"
```

### Using Snapshot Versions

Snapshots of zio-kafka are published to Sonatype's Central snapshot repository at https://central.sonatype.com/repository/maven-snapshots/. The repository's [metadata file for zio-kafka](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-kafka_3/maven-metadata.xml) lists the available versions. To let sbt resolve snapshot versions, add the repository as a resolver:

```scala
resolvers += "Sonatype Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"
```

## Running a Local Kafka Broker

Before we can produce or consume anything, we need a running Kafka broker. Save the following file as `docker-compose.yml`. It starts a single Kafka node in KRaft mode (Kafka's built-in cluster coordination, so no ZooKeeper is needed) that acts as both broker and controller:

```yaml
services:
  broker:
    image: apache/kafka:4.4.0
    container_name: broker
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
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

The `PLAINTEXT_HOST` listener is published on port 9092 of your machine, so programs running outside Docker connect to `localhost:9092`. The replication factors are set to 1 because there is only one broker.

Start the broker in the background:

```bash
docker compose up -d
```

When you are done, `docker compose down` stops and removes the container.

## Writing a Producer and a Consumer

The following application, one of the examples in the zio-kafka repository, produces a record with a random number every two seconds, and at the same time consumes those records, prints them and commits their offsets:

```scala title="zio-kafka-example/src/main/scala/zio/kafka/example/ReadmeExample.scala"
// format: off
// Manually formatted for the small width of readme
package zio.kafka.example

import zio._
import zio.kafka.consumer._
import zio.kafka.producer.{ Producer, ProducerSettings }
import zio.kafka.serde._
import zio.stream.ZStream

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
          .plainStream(Subscription.topics("random-topic"), Serde.long, Serde.string)
          .tap(r => Console.printLine(r.value))
          .map(_.offset)
          .aggregateAsyncWithin(Consumer.collectOffsets, Schedule.fixed(100.millis))
          .mapZIO(_.commit)
          .runDrain
      } yield ()
    }

  override def run: ZIO[Any, Throwable, Unit] =
    ZIO.raceFirst(producerRun, List(consumerRun))
}
```

Run it with `sbt run`. After a few seconds, the consumer prints a new random number every two seconds. Stop the program with `Ctrl+C`.

## How the Example Works

The application has two independent parts, a producer and a consumer, that both connect to the broker at `localhost:9092`.

### The Producer

`Producer.make` creates a `Producer` from a `ProducerSettings`. It needs a `Scope`: when the scope closes, the producer is closed and disconnects from Kafka. `ZIO.scoped` provides that scope and closes it when `producerRun` ends.

`ZStream.fromSchedule` emits an element every two seconds. For each element, we draw a random number and call `Producer#produce` to send a record to the `random-topic` topic. Kafka stores keys and values as bytes, so `Producer#produce` takes a serializer for the key and one for the value. Here the key is a `Long` (the random number modulo 4) and the value is a `String`, so we use the built-in `Serde.long` and `Serde.string`. `Producer#produce` completes when the broker has acknowledged the record. The broker creates the `random-topic` topic automatically the first time a record is sent to it, because Kafka brokers allow automatic topic creation by default.

### The Consumer

`Consumer.make` creates a `Consumer` from a `ConsumerSettings`. Like the producer, it lives as long as its `Scope`. `ConsumerSettings#withGroupId` sets the consumer group: consumers in the same group share the partitions of a topic, and Kafka remembers per group which records were already processed.

`Consumer#plainStream` subscribes to the `random-topic` topic and returns a `ZStream` of `CommittableRecord`s, with keys and values deserialized by `Serde.long` and `Serde.string`. For each record we print the value, then keep only its offset (`CommittableRecord#offset`).

Committing an offset tells Kafka that the group has processed the record, so it is not delivered to this group again after a restart. Committing every record separately is slow, so `ZStream#aggregateAsyncWithin` with the `Consumer.collectOffsets` sink merges the offsets into an `OffsetBatch` every 100 milliseconds, and `OffsetBatch#commit` commits each batch in one call.

By default a new consumer group starts reading at the end of the topic. Records produced before the consumer joined are skipped, but because the producer keeps producing, new records soon arrive. See [Partition Assignment and Offset Retrieval](partition-assignment-and-offset-retrieval.md) to start from the beginning of the topic instead.

:::note
`ZStream#tap` is used here to keep the example short. It breaks the chunking structure of the stream and lowers throughput. See [Avoiding Chunk Breakers](avoiding-chunk-breakers.md) for alternatives.
:::

### Running Both

`ZIO.raceFirst` runs `producerRun` and `consumerRun` concurrently and completes as soon as one of them completes, interrupting the other. Both streams run until they are interrupted or fail, so the application runs until you stop it or one side fails.

## Next Steps

From here, you can continue with:

- The [tutorial](tutorial.md), a longer guided tour of producing and consuming with ZIO workflows, zio-streams and JSON data.
- [Consumer](consumer.md), for the consumer APIs in depth.
- [Producer](producer.md), for all the ways to produce records.
- [Offsets and Committing](offsets-and-committing.md), for how offsets work and how to commit them safely.
- [Writing Tests](writing-tests.md), for testing your zio-kafka code with the embedded Kafka broker from the test kit.
