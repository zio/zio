# Introduction to ZIO Kafka

> [ZIO Kafka](https://github.com/zio/zio-kafka) is a Kafka client for ZIO. It provides a purely functional, streams-based interface to the Kafka
> client and integrates effortlessly with ZIO and zio-streams. Often zio-kafka programs have a _higher_ throughput than
> programs that use the Java Kafka client directly (see section [Performance](#performance) below).

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-kafka/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-kafka_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-kafka_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-kafka_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-kafka_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-kafka_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-kafka_2.13) [![ZIO Kafka](https://img.shields.io/github/stars/zio/zio-kafka?style=social)](https://github.com/zio/zio-kafka) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## Introduction

Apache Kafka is a distributed event streaming platform that acts as a distributed publish-subscribe messaging system. It enables us to build distributed streaming data pipelines and event-driven applications.

Kafka has a mature Java client for producing and consuming events, but it has a low-level API. Zio-kafka is a ZIO native client for Apache Kafka. It has a high-level streaming API on top of the Java client. So we can produce and consume events using the declarative concurrency model of zio-streams. In addition, zio-kafka supports an even higher level API where you only write the processing part and the rest is handled by zio-kafka.

## Features

- Exposes the Java Kafka consumer, producer and admin clients with a ZIO based interface.
- Consuming (see [Consumer](consumer.md)):
  - 2 APIs: streaming and ZIO workflow based
  - supports [custom deserialization](serialization-and-deserialization.md)
  - can process each partition in parallel for highest throughput
  - allows batched processing for highest throughput
  - configurable per partition pre-fetching (with back-pressure)
  - the only async Kafka consumer [without duplicates](preventing-duplicates.md) after a rebalance (as far as we know)
  - very [configurable](consumer-tuning.md)
  - automatic or manual [starting offset](partition-assignment-and-offset-retrieval.md)
  - supports [external commits](offsets-and-committing.md)
  - retries after authentication/authorization errors
  - detects [silent authorization failures](detecting-authorization-failures.md) when a READ ACL is revoked at runtime
  - exposes [metrics](metrics.md)
  - [diagnostics](diagnostics.md) API
- Producing (see [Producer](producer.md)):
  - 2 APIs: streaming and ZIO workflow based
  - supports [custom serialization](serialization-and-deserialization.md)
  - allows for batches for highest throughput
  - optionally await broker acknowledgements
  - supports [transactions](transactions.md)
  - optional retries after authentication/authorization errors
  - exposes [metrics](metrics.md)
  - [diagnostics](diagnostics.md) API
- [Admin API](admin-client.md):
  - exposes most of the admin client methods with a ZIO based interface
  - safer than the wrapped java client by waiting for the operation to be done
- Proper errors when broker expects SSL (no [OOM crashes](https://issues.apache.org/jira/browse/KAFKA-4090)), see [Security](security.md)
- [Test kit](writing-tests.md) with embedded kafka broker
- Well documented
- Community support via [Discord](https://discord.com/channels/629491597070827530/629497941719121960)
- Commercial support via [Ziverge](https://www.ziverge.com)

## Installation

To use zio-kafka, add the following lines to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-kafka"         % "4.2.0"
libraryDependencies += "dev.zio" %% "zio-kafka-testkit" % "4.2.0" % Test
```

The [Getting Started](getting-started.md) page walks through running a local Kafka broker and writing a first producer and consumer. For a longer tour of the different ways you can use zio-kafka, read the [zio-kafka tutorial](tutorial.md).

## Documentation

If you are reading this page from https://zio.dev/zio-kafka/, the documentation is in the sub-pages and can be found by
scrolling the left panel of this page.

If you are reading this from GitHub, the documentation can be found in
[the docs folder](https://github.com/zio/zio-kafka/tree/master/docs).

## Resources

The following articles, videos and example projects show zio-kafka in practice.

### Articles

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

### Example Projects

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

Zio-kafka makes it easy to process partitions in parallel: `Consumer.consumeWith` does so by default, and with the streaming API you can use `Consumer#partitionedStream` with `ZStream#flatMapPar`. The default java-kafka client does not provide parallel processing. Of course, there is some overhead in buffering records and distributing them to the fibers that need them. On 2024-11-23, we estimated that zio-kafka consumes faster than the java-kafka client when processing takes more than ~1.2ms per 1000 records. The precise time depends on many factors. Please see [this article](https://day-to-day-stuff.blogspot.com/2024/12/zio-kafka-faster-than-java-kafka.html) for more details.

If you do not care for the convenient ZStream based API that zio-kafka brings, and latency is of absolute importance,
using the java based Kafka client directly is still the better choice.

## Developers

* [Benchmarks and Flame graphs](https://github.com/zio/zio-kafka/blob/master/zio-kafka-bench/README.md)
