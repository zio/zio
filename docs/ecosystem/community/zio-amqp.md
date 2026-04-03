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
libraryDependencies += "nl.vroste" %% "zio-amqp" % "0.3.0"
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
import nl.vroste.zio.amqp.model._
import zio._

import java.net.URI

object ZIOAMQPExample extends ZIOAppDefault {

  val channel: ZIO[Scope, Throwable, Channel] = for {
    connection <- Amqp.connect(URI.create("amqp://localhost:5672"))
    channel    <- Amqp.createChannel(connection)
  } yield channel

  val myApp: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        channel                            <- channel
        producer: ZIO[Any, Throwable, Long] =
          Random.nextUUID
            .flatMap(uuid => channel.publish(ExchangeName("my_exchange"), uuid.toString.getBytes).unit)
            .schedule(Schedule.spaced(1.seconds))

        consumer: ZIO[Any, Throwable, Unit] =
          channel
            .consume(queue = QueueName("my_queue"), consumerTag = ConsumerTag("my_consumer"))
            .mapZIO { record =>
              val deliveryTag = record.getEnvelope.getDeliveryTag
              Console.printLine(s"Received $deliveryTag: ${new String(record.getBody)}") *>
                channel.ack(DeliveryTag(deliveryTag))
            }
            .runDrain
        p                                  <- producer.fork
        c                                  <- consumer.fork
        _                                  <- p.zip(c).join
      } yield ()
    }

  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] = myApp
}
```
