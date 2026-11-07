# Introduction to ZIO SQS

> [ZIO SQS](https://zio.dev/zio-sqs) is a ZIO-powered client for AWS SQS. It is built on top of the [AWS SDK for Java 2.0](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/basics.html) via the automatically generated wrappers from [zio-aws](https://zio.dev/zio-aws).

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-sqs/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-sqs_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-sqs_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-sqs_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-sqs_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-sqs-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-sqs-docs_2.13) [![ZIO SQS](https://img.shields.io/github/stars/zio/zio-sqs?style=social)](https://github.com/zio/zio-sqs)

## Introduction

ZIO SQS enables us to produce and consume elements to/from the Amazon SQS service. It is integrated with ZIO Streams, so we can produce and consume elements in a streaming fashion, element by element or micro-batching.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-sqs" % "0.12.0"
```

## Example

In this example we produce a stream of events to the `MyQueue` and then consume them from that queue (at-most-once delivery semantics):

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
    ZStream.iterate(0)(_ + 1).map(_.toString).map(ProducerEvent(_))

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

Check out the examples folder in `zio-sqs/src/test/scala/examples` for additional examples that cover at-least-once and at-most-once delivery semantics.
