# How to Route Messages with Exchanges

> A publisher sends every message to an exchange, never directly to a queue, and the exchange decides which queues receive a copy. This guide shows how to use a direct, a fanout and a topic exchange from ZIO AMQP to deliver one message to the right set of consumers. The semantics of the exchange types are those of RabbitMQ; see [Model Types](./reference/model.md#exchangetype) for the list.

All snippets assume these imports:

```scala
import zio._
import zio.amqp._
import zio.amqp.model._
```

## Send to a Queue by Name

When a message has exactly one destination, use the default exchange, `ExchangeName("")`. RabbitMQ declares it for you, and it routes a message to the queue whose name equals the routing key, without any binding:

```scala
def sendToQueue(channel: Channel, queue: QueueName, body: String): ZIO[Any, Throwable, Unit] =
  channel.publish(ExchangeName(""), body.getBytes("UTF-8"), RoutingKey(QueueName.unwrap(queue)))
```

## Route by Exact Key with a Direct Exchange

To send messages to different queues depending on a label, declare a direct exchange and bind each queue with the label as its routing key. A message goes to every queue whose binding key equals the message's routing key. In the example, only the `errors` queue receives the second message:

```scala
def directRouting(channel: Channel): ZIO[Any, Throwable, Unit] = {
  val logs   = ExchangeName("logs")
  val infos  = QueueName("logs.info")
  val errors = QueueName("logs.error")

  for {
    _ <- channel.exchangeDeclare(logs, ExchangeType.Direct)
    _ <- channel.queueDeclare(infos)
    _ <- channel.queueDeclare(errors)
    _ <- channel.queueBind(infos, logs, RoutingKey("info"))
    _ <- channel.queueBind(errors, logs, RoutingKey("error"))
    _ <- channel.publish(logs, "disk is 80% full".getBytes("UTF-8"), RoutingKey("info"))
    _ <- channel.publish(logs, "disk is full".getBytes("UTF-8"), RoutingKey("error"))
  } yield ()
}
```

Binding one queue several times, once per key, delivers to it every message that matches any of them.

## Broadcast with a Fanout Exchange

To deliver every message to every interested consumer, such as a cache-invalidation notice, use a fanout exchange. It ignores routing keys, so the key given to `Channel#queueBind` and `Channel#publish` is arbitrary. Each subscriber declares its own queue, and a server-named exclusive queue disappears with the subscriber:

```scala
def subscribe(channel: Channel, exchange: ExchangeName): ZIO[Scope, Throwable, Unit] =
  for {
    queue <- channel.queueDeclare(QueueName(""), exclusive = true, autoDelete = true).map(QueueName(_))
    _     <- channel.queueBind(queue, exchange, RoutingKey(""))
    _     <- channel
               .consume(queue, ConsumerTag("subscriber"), autoAck = true)
               .mapZIO(delivery => Console.printLine(new String(delivery.getBody, "UTF-8")))
               .runDrain
               .forkScoped
  } yield ()

def broadcast(channel: Channel, exchange: ExchangeName): ZIO[Any, Throwable, Unit] =
  channel.exchangeDeclare(exchange, ExchangeType.Fanout) *>
    channel.publish(exchange, "cache cleared".getBytes("UTF-8"))
```

A queue only receives messages published after it was bound, so declare and bind the subscriber queues before the first publish.

## Route by Pattern with a Topic Exchange

A topic exchange matches the routing key against patterns made of dot-separated words. In a binding key, `*` stands for exactly one word and `#` for zero or more words. With the keys `orders.created`, `payments.error` and `orders.error`, the queue bound with `orders.*` receives the first and the third, and the queue bound with `*.error` receives the second and the third:

```scala
def topicRouting(channel: Channel): ZIO[Any, Throwable, Unit] = {
  val events = ExchangeName("events")
  val orders = QueueName("orders")
  val errors = QueueName("errors")

  for {
    _ <- channel.exchangeDeclare(events, ExchangeType.Topic)
    _ <- channel.queueDeclare(orders)
    _ <- channel.queueDeclare(errors)
    _ <- channel.queueBind(orders, events, RoutingKey("orders.*"))
    _ <- channel.queueBind(errors, events, RoutingKey("*.error"))
    _ <- ZIO.foreachDiscard(List("orders.created", "payments.error", "orders.error")) { key =>
           channel.publish(events, key.getBytes("UTF-8"), RoutingKey(key))
         }
  } yield ()
}
```

A message that matches both bindings, here `orders.error`, is delivered once to each queue.

## Clean Up

Exchanges and queues declared without `durable` or `autoDelete` stay on the broker until they are deleted, or until the broker restarts. Delete the ones that a program or a test created with `Channel#queueDelete` and `Channel#exchangeDelete`. Deleting a queue or an exchange also removes its bindings:

```scala
def cleanUp(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel.queueDelete(QueueName("orders")) *>
    channel.queueDelete(QueueName("errors")) *>
    channel.exchangeDelete(ExchangeName("events"))
```

## Running the Example

The file [`RoutingExample.scala`](https://github.com/zio/zio-amqp/blob/master/zio-amqp-examples/src/main/scala/zio/amqp/examples/howto/RoutingExample.scala) runs the topic exchange scenario above and prints how many messages each queue holds. Start RabbitMQ with `docker compose up -d` and run `sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.RoutingExample"`. The output is:

```text
orders.* queue: 2 messages, *.error queue: 2 messages
```
