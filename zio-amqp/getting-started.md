# Getting Started with ZIO AMQP

> This page takes you from an empty project to a program that publishes a message to RabbitMQ and reads it back.

## Prerequisites

You need a RabbitMQ broker listening on `localhost:5672` with the default `guest`/`guest` account, which RabbitMQ only accepts from the local machine. The repository's `docker-compose.yml` starts one:

```bash
docker compose up -d
```

Add ZIO AMQP to your project as described in [Installation](./index.md#installation).

## A First Program

The program below connects to the broker, declares a queue, publishes one message to it, and consumes that message back. Both the connection and the channel are scoped resources, so `ZIO.scoped` closes them when the program ends:

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

## Next Steps

- Learn the ideas behind the API in [AMQP Basics](./explanation/amqp-basics.md) and [Delivery and Failure](./explanation/delivery-and-failure.md).
- Build a producer and a pool of consumers in the [tutorial](./tutorial-work-queue.md).
- Solve a specific problem with the how-to guides, starting with [How to Connect to a Broker](./how-to-connect.md).
- Look up a type or method in the reference: [`Amqp` and `AMQPConfig`](./reference/amqp.md), [`Channel`](./reference/channel.md) and [Model Types](./reference/model.md).
