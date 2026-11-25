# How to Dead-Letter and Expire Messages

> A *dead-letter exchange* receives messages that a queue could not keep: a consumer rejected them with `requeue = false`, they sat in the queue longer than their time-to-live, or the queue was full. This guide shows how to declare the queues for that, how to set a time-to-live, and how to read the dead letters. ZIO AMQP has no dedicated API for it: you pass the broker's `x-` queue arguments through the `arguments` parameter of `Channel#queueDeclare`. The meaning of each argument is in the RabbitMQ guides on [dead letter exchanges](https://www.rabbitmq.com/docs/dlx) and [time-to-live](https://www.rabbitmq.com/docs/ttl).

All snippets assume these imports:

```scala
import com.rabbitmq.client.AMQP
import zio._
import zio.amqp._
import zio.amqp.model._

val workQueue     = QueueName("orders")
val deadLetters   = QueueName("orders.dead")
val deadExchange  = ExchangeName("orders.dlx")
```

## Declare the Dead-Letter Topology

First create the exchange that collects dead letters and a queue bound to it. A fanout exchange sends everything to the bound queue whatever the routing key. Then declare the work queue with `x-dead-letter-exchange` naming that exchange:

```scala
def declareTopology(channel: Channel): ZIO[Any, Throwable, Unit] =
  for {
    _ <- channel.exchangeDeclare(deadExchange, ExchangeType.Fanout, durable = true)
    _ <- channel.queueDeclare(deadLetters, durable = true)
    _ <- channel.queueBind(deadLetters, deadExchange, RoutingKey(""))
    _ <- channel.queueDeclare(
           workQueue,
           durable = true,
           arguments = Map[String, AnyRef]("x-dead-letter-exchange" -> ExchangeName.unwrap(deadExchange))
         )
  } yield ()
```

`ExchangeName.unwrap` turns the newtype into the plain name the argument needs. The dead-letter queue must exist and be bound before messages die, or they are dropped.

A queue's arguments are part of its identity: declaring an existing queue again with different arguments is an error that closes the channel. Delete the queue first when you change them.

## Dead-Letter a Rejected Message

Rejecting a message without requeueing it sends it to the dead-letter exchange:

```scala
def handle(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(workQueue, ConsumerTag("orders-worker"))
    .mapZIO { delivery =>
      val tag = DeliveryTag(delivery.getEnvelope.getDeliveryTag)
      ZIO
        .attempt(new String(delivery.getBody, "UTF-8").toInt)
        .foldZIO(_ => channel.nack(tag, requeue = false), _ => channel.ack(tag))
    }
    .runDrain
```

## Expire Messages After a Time

To drop or dead-letter every message of a queue that stays unconsumed for a minute, declare the queue with `x-message-ttl` in milliseconds. The value must be a number, so box it as a `java.lang.Long`:

```scala
def declareExpiring(channel: Channel): ZIO[Any, Throwable, String] =
  channel.queueDeclare(
    QueueName("orders.expiring"),
    durable = true,
    arguments = Map[String, AnyRef](
      "x-dead-letter-exchange" -> ExchangeName.unwrap(deadExchange),
      "x-message-ttl"          -> Long.box(60000L)
    )
  )
```

To give a single message its own time-to-live instead, set the `expiration` property, a string of milliseconds, when publishing:

```scala
def publishExpiring(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel.publish(
    ExchangeName(""),
    "short-lived".getBytes("UTF-8"),
    RoutingKey("orders"),
    props = new AMQP.BasicProperties.Builder().expiration("30000").build()
  )
```

## Read the Dead Letters

The dead-letter queue is an ordinary queue. Consume it like any other, for example to inspect or republish failures:

```scala
def inspect(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(deadLetters, ConsumerTag("dead-letter-reader"))
    .mapZIO { delivery =>
      Console.printLine(new String(delivery.getBody, "UTF-8")) *>
        channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
    }
    .runDrain
```

The broker adds an `x-death` header to each dead letter, with the reason and the original queue. It is available through `delivery.getProperties.getHeaders`.
