# Introduction to ZIO SQS

> [ZIO SQS](https://zio.dev/zio-sqs) is a ZIO-powered client for AWS SQS. It is built on top of the [AWS SDK for Java 2.0](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/basics.html) via the automatically generated wrappers from [zio-aws](https://zio.dev/zio-aws).

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-sqs/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-sqs_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-sqs_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-sqs_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-sqs_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-sqs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-sqs_2.13) [![ZIO SQS](https://img.shields.io/github/stars/zio/zio-sqs?style=social)](https://github.com/zio/zio-sqs)

ZIO SQS enables us to produce and consume elements to/from the Amazon SQS service. It is integrated with ZIO Streams, so we can produce and consume elements in a streaming fashion, element by element or micro-batching.

## Installation

ZIO SQS is published for Scala 2.13 and Scala 3 on the JVM only. To use it, we add the following to the `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  // ZIO SQS client: producers, consumer streams and queue helpers (JVM only).
  // It brings the zio-aws SQS client and the Netty HTTP client as dependencies.
  "dev.zio" %% "zio-sqs" % "0.12.7"
)
```

## Example

In this example we produce a stream of ten events to the `MyQueue` and then consume them from that queue (at-most-once delivery semantics):

```scala
import zio._
import zio.aws.core.config.AwsConfig
import zio.aws.netty.NettyHttpClient
import zio.aws.sqs.Sqs
import zio.sqs.producer.{Producer, ProducerEvent}
import zio.sqs.serialization.Serializer
import zio.sqs.{SqsStream, SqsStreamSettings, Utils}
import zio.stream.ZStream

object ProducerConsumerExample extends ZIOAppDefault {
  val queueName = "MyQueue"

  val stream: ZStream[Any, Nothing, ProducerEvent[String]] =
    ZStream.iterate(0)(_ + 1).map(_.toString).map(ProducerEvent(_)).take(10)

  val program: ZIO[Sqs, Throwable, Unit] = for {
    _        <- Utils.createQueue(queueName)
    queueUrl <- Utils.getQueueUrl(queueName)
    producer  = Producer.make(queueUrl, Serializer.serializeString)
    _        <- ZIO.scoped(producer.flatMap(_.sendStream(stream).runDrain))
    _        <- SqsStream(
                  queueUrl,
                  SqsStreamSettings.default
                    .withAutoDelete(true) // at-most-once delivery semantics
                    .withStopWhenQueueEmpty(true)
                    .withWaitTimeSeconds(3)
                ).foreach(msg => Console.printLine(msg.body))
  } yield ()

  def run =
    program.provide(
      Sqs.live,
      AwsConfig.default,
      NettyHttpClient.default
    )
}
```

To learn the library step by step, start with [Getting Started](./getting-started.md). The examples folder in `zio-sqs/src/test/scala/examples` has additional examples for at-least-once and at-most-once delivery semantics.
