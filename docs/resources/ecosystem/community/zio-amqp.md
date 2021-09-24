---
id: zio-amqp
title: "ZIO AMQP"
---

[ZIO AMQP](https://github.com/svroonland/zio-amqp) is a ZIO-based AMQP client for Scala.

## Introduction

ZIO AMQP is a ZIO-based wrapper around the RabbitMQ client. It provides a streaming interface to AMQP queues and helps to prevent us from shooting ourselves in the foot with thread-safety issues.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "nl.vroste" %% "zio-amqp" % "0.2.0"
```

## Example

First, let's create an instance of RabbitMQ:

```
docker run -d --name some-rabbit -p 5672:5672 -p 5673:5673 -p 15672:15672 rabbitmq:3-management
```

Then we need to create `my_exchange` and `my_queue` and bind the queue to the exchange via the RabbitMQ management dashboard (`localhost:15672`).

Now we can run the example below:

```scala
import nl.vroste.zio.amqp._
import zio._
import zio.blocking._
import zio.clock.Clock
import zio.console._
import zio.duration.durationInt
import zio.random.Random

import java.net.URI

object ZIOAMQPExample extends zio.App {

  val channelM: ZManaged[Blocking, Throwable, Channel] = for {
    connection <- Amqp.connect(URI.create("amqp://localhost:5672"))
    channel <- Amqp.createChannel(connection)
  } yield channel

  val myApp: ZIO[Blocking with Console with Clock with Random, Throwable, Unit] =
    channelM.use { channel =>
      val producer: ZIO[Blocking with Random with Clock, Throwable, Long] =
        zio.random.nextUUID
          .flatMap(uuid =>
            channel.publish("my_exchange", uuid.toString.getBytes)
              .map(_ => ())
          ).schedule(Schedule.spaced(1.seconds))

      val consumer: ZIO[Blocking with Console, Throwable, Unit] = channel
        .consume(queue = "my_queue", consumerTag = "my_consumer")
        .mapM { record =>
          val deliveryTag = record.getEnvelope.getDeliveryTag
          putStrLn(s"Received $deliveryTag: ${new String(record.getBody)}") *>
            channel.ack(deliveryTag)
        }
        .runDrain

      for {
        p <- producer.fork
        c <- consumer.fork
        _ <- p.zip(c).join
      } yield ()
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    myApp.exitCode
}
```
