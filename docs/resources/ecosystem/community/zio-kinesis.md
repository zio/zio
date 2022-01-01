---
id: zio-kinesis
title: "ZIO Kinesis"
---

[ZIO Kinesis](https://github.com/svroonland/zio-kinesis) is a ZIO-based AWS Kinesis client for Scala.

## Introduction

ZIO Kinesis is an interface to Amazon Kinesis Data Streams for consuming and producing data. This library is built on top of [ZIO AWS](https://github.com/zio/zio-aws), a library of automatically generated ZIO wrappers around AWS SDK methods.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

  ```scala
libraryDependencies += "nl.vroste" %% "zio-kinesis" % "0.20.0"
```

## Example

This is an example of consuming a stream from Amazon Kinesis:

```scala
import nl.vroste.zio.kinesis.client.serde.Serde
import nl.vroste.zio.kinesis.client.zionative.Consumer
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.duration._
import zio.logging.Logging
import zio.{ExitCode, URIO, _}

object ZIOKinesisConsumerExample extends zio.App {
  val loggingLayer: ZLayer[Any, Nothing, Logging] =
    (Console.live ++ Clock.live) >>>
      Logging.console() >>>
      Logging.withRootLoggerName(getClass.getName)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Consumer
      .consumeWith(
        streamName = "my-stream",
        applicationName = "my-application",
        deserializer = Serde.asciiString,
        workerIdentifier = "worker1",
        checkpointBatchSize = 1000L,
        checkpointDuration = 5.minutes
      )(record => putStrLn(s"Processing record $record"))
      .provideCustom(Consumer.defaultEnvironment ++ loggingLayer)
      .exitCode
}
```
