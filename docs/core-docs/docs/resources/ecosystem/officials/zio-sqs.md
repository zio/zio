---
id: zio-sqs
title: "ZIO SQS"
---

[ZIO SQS](https://github.com/zio/zio-sqs) is a ZIO-powered client for AWS SQS. It is built on top of the [AWS SDK for Java 2.0](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/basics.html) via the automatically generated wrappers from [zio-aws](https://github.com/zio/zio-aws).

## Introduction

ZIO SQS enables us to produce and consume elements to/from the Amazon SQS service. It is integrated with ZIO Streams, so we can produce and consume elements in a streaming fashion, element by element or micro-batching.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-sqs" % "0.4.2"
```

## Example

In this example we produce a stream of events to the `MyQueue` and then consume them from that queue:

```scala
import io.github.vigoo.zioaws
import io.github.vigoo.zioaws.core.config.CommonAwsConfig
import io.github.vigoo.zioaws.sqs.Sqs
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.regions.Region
import zio.clock.Clock
import zio.sqs.producer.{Producer, ProducerEvent}
import zio.sqs.serialization.Serializer
import zio.sqs.{SqsStream, SqsStreamSettings, Utils}
import zio.stream.ZStream
import zio.{ExitCode, RIO, URIO, ZLayer, _}

object ProducerConsumerExample extends zio.App {
  val queueName = "MyQueue"

  val client: ZLayer[Any, Throwable, Sqs] = zioaws.netty.default ++
    ZLayer.succeed(
      CommonAwsConfig(
        region = Some(Region.of("ap-northeast-2")),
        credentialsProvider = StaticCredentialsProvider.create(
          AwsBasicCredentials.create("key", "key")
        ),
        endpointOverride = None,
        commonClientConfig = None
      )
    ) >>>
    zioaws.core.config.configured() >>>
    zioaws.sqs.live

  val stream: ZStream[Any, Nothing, ProducerEvent[String]] =
    ZStream.iterate(0)(_ + 1).map(_.toString).map(ProducerEvent(_))

  val program: RIO[Sqs with Clock, Unit] = for {
    _        <- Utils.createQueue(queueName)
    queueUrl <- Utils.getQueueUrl(queueName)
    producer = Producer.make(queueUrl, Serializer.serializeString)
    _ <- producer.use { p =>
      p.sendStream(stream).runDrain
    }
    _ <- SqsStream(
      queueUrl,
      SqsStreamSettings(stopWhenQueueEmpty = true, waitTimeSeconds = Some(3))
    ).foreach(msg => UIO(println(msg.body)))
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.provideCustom(client).exitCode
}
```
