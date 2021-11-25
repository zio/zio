---
id: zio-pulsar
title: "ZIO Pulsar"
---

[ZIO Pulsar](https://github.com/apache/pulsar) is the _Apache Pulsar_ client for Scala with ZIO and ZIO Streams integration.

## Introduction

ZIO Pulsar is a purely functional Scala wrapper over the official Pulsar client. Some key features of this library:

- **Type-safe** — Utilizes Scala type system to reduce runtime exceptions present in the official Java client.
- **Streaming-enabled** — Naturally integrates with ZIO Streams.
- **ZIO integrated** — Uses common ZIO primitives like ZIO effect and `ZManaged` to reduce the boilerplate and increase expressiveness.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file for _Scala 3_:

```scala
libraryDependencies += "com.github.jczuchnowski" %% "zio-pulsar" % "0.1"
```

## Example

First of all we need to create an instance of _Apache Pulsar_ and run that:

```
docker run -it \
  -p 6650:6650 \
  -p 8080:8080 \
  --mount source=pulsardata,target=/pulsar/data \
  --mount source=pulsarconf,target=/pulsar/conf \
  --network pulsar \
  apachepulsar/pulsar:2.7.0 \
  bin/pulsar standalone
```

Now we can run the following example:

```scala
import org.apache.pulsar.client.api.{PulsarClientException, Schema}
import zio._
import zio.blocking._
import zio.clock._
import zio.console._
import zio.pulsar._
import zio.stream._

import java.nio.charset.StandardCharsets

object StreamingExample extends zio.App {
  val topic = "my-topic"

  val producer: ZManaged[Has[PulsarClient], PulsarClientException, Unit] =
    for {
      sink <- Producer.make(topic, Schema.STRING).map(_.asSink)
      _ <- Stream.fromIterable(0 to 100).map(i => s"Message $i").run(sink).toManaged_
    } yield ()

  val consumer: ZManaged[Has[PulsarClient] with Blocking with Console, PulsarClientException, Unit] =
    for {
      builder <- ConsumerBuilder.make(Schema.STRING).toManaged_
      consumer <- builder
        .subscription(Subscription("my-subscription", SubscriptionType.Exclusive))
        .topic(topic)
        .build
      _ <- consumer.receiveStream.take(10).foreach { e =>
        consumer.acknowledge(e.getMessageId) *>
          putStrLn(new String(e.getData, StandardCharsets.UTF_8)).orDie
      }.toManaged_
    } yield ()

  val myApp =
    for {
      f <- consumer.fork
      _ <- producer
      _ <- f.join.toManaged_
    } yield ()

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    myApp
      .provideCustom(
        (Console.live ++ Clock.live) >+>
          PulsarClient.live("localhost", 6650)
      ).useNow.exitCode
}
```

## Resources

- [ZIO World - ZIO PULSAR](https://www.youtube.com/watch?v=tpwydDqQBmk) by Jakub Czuchnowski (March 2020) — A new library that offers a native, first-class ZIO experience for Pulsar, the Kafka competitor gaining traction for some use cases.
