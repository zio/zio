# ZIO AMQP

> ZIO AMQP is a ZIO-based wrapper around the [RabbitMQ Java client](https://www.rabbitmq.com/client-libraries/java-api-guide). It exposes queues as `ZStream`s of deliveries and serializes access to the underlying RabbitMQ channel, so concurrent fibers cannot corrupt it. Connections and channels are scoped resources: they are closed automatically when the `Scope` that owns them ends, even if the program fails or is interrupted.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-amqp/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-amqp_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-amqp_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-amqp_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-amqp_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-amqp_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-amqp_2.13) [![ZIO AMQP](https://img.shields.io/github/stars/zio/zio-amqp?style=social)](https://github.com/zio/zio-amqp)

## Installation

Add the following line to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-amqp" % "1.0.1"
```

ZIO AMQP depends on ZIO 2 and the RabbitMQ Java client, and is published for Scala 2.12, 2.13 and 3.

## A First Program

The program below connects to a broker on `localhost:5672` with the default `guest`/`guest` credentials, declares a queue, publishes one message to it, and consumes that message back. Both the connection and the channel are scoped resources, so `ZIO.scoped` closes them when the program ends:

```scala
import zio._
import zio.amqp._
import zio.amqp.model._

val queue = QueueName("hello")

val program: ZIO[Any, Throwable, Unit] =
  ZIO.scoped {
    for {
      connection <- Amqp.connect(AMQPConfig.default)
      channel    <- Amqp.createChannel(connection)
      _          <- channel.queueDeclare(queue)
      _          <- channel.publish(ExchangeName(""), "Hello, AMQP!".getBytes, RoutingKey("hello"))
      _          <- channel
                      .consume(queue, ConsumerTag("first-program"))
                      .mapZIO { delivery =>
                        Console.printLine(new String(delivery.getBody)) *>
                          channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
                      }
                      .take(1)
                      .runDrain
    } yield ()
  }
```

Publishing to the default exchange (the empty name `ExchangeName("")`) with the queue's name as the routing key delivers the message straight to that queue. The `consume` stream stays open until it is completed, here by `take(1)`, which also cancels the consumer on the broker.

## Where to Go Next

If you are new to AMQP, start with the [tutorial](./tutorial-work-queue.md), which builds a producer and a pool of competing consumers step by step.

For a specific task, pick a how-to guide:

- [Connect to a Broker](./how-to-connect.md)
- [Acknowledge Messages](./how-to-acknowledge-messages.md)
- [Limit Prefetch to Apply Back-Pressure](./how-to-limit-prefetch.md)
- [Route Messages with Exchanges](./how-to-route-with-exchanges.md)
- [Publish Durable Messages](./how-to-publish-durable-messages.md)
- [Stop a Consumer](./how-to-stop-a-consumer.md)
- [Test with Testcontainers](./how-to-test-with-testcontainers.md)

To look up a type or method, use the reference pages:

- [`Amqp` and `AMQPConfig`](./reference/amqp.md): create connections and channels.
- [`Channel`](./reference/channel.md): declare queues and exchanges, publish, consume and acknowledge.
- [Model Types](./reference/model.md): `QueueName`, `ExchangeName`, `RoutingKey`, `ConsumerTag`, `DeliveryTag`, `AmqpUri` and `ExchangeType`.
