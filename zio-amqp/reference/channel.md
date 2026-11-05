# Channel

> `Channel` is the handle through which a program talks to the broker: it declares queues and exchanges, publishes messages, consumes them as a `ZStream`, and acknowledges them. It wraps a RabbitMQ Java client channel together with a one-permit `Semaphore`, and every method takes that permit for the duration of its call. The underlying client channel is not thread-safe, so this makes a `Channel` safe to share between fibers.

A `Channel` has no public constructor. Obtain one from [`Amqp.createChannel`](./amqp.md#creating-a-channel), which closes it when the enclosing `Scope` ends.

All the examples on this page assume the following setup, which opens a channel against a local broker and names a queue and an exchange:

```scala
import com.rabbitmq.client.AMQP
import zio._
import zio.amqp._
import zio.amqp.model._

val queue    = QueueName("reference-queue")
val exchange = ExchangeName("reference-exchange")

def withChannel[A](use: Channel => ZIO[Any, Throwable, A]): ZIO[Any, Throwable, A] =
  ZIO.scoped {
    Amqp.connect(AMQPConfig.default).flatMap(Amqp.createChannel).flatMap(use)
  }
```

Every method returns a `ZIO[Any, Throwable, _]` (or a `ZStream[Any, Throwable, _]`): the effects need no environment, and any failure of the RabbitMQ client, including an `IOException` from a closed channel or a protocol error from the broker, appears in the error channel. Methods that call the broker synchronously run on ZIO's blocking thread pool.

:::note[Broker errors close the channel]
In AMQP, most broker-side errors, such as operating on a queue that does not exist or re-declaring a queue with different arguments, close the channel they happened on. After that, every call on that `Channel` fails. Create a new channel with `Amqp.createChannel` to continue.
:::

## Queues

A queue stores messages until a consumer takes them. These methods create, inspect and remove queues.

### Channel#queueDeclare

`Channel#queueDeclare` creates a queue, or does nothing if a queue with the same name and the same properties already exists. It returns the name of the queue as a `String`:

```scala
class Channel {
  def queueDeclare(
    queue: QueueName,
    durable: Boolean = false,
    exclusive: Boolean = false,
    autoDelete: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
  ): ZIO[Any, Throwable, String]
}
```

The parameters are:

| Parameter    | Meaning                                                                                              |
| ------------ | ---------------------------------------------------------------------------------------------------- |
| `queue`      | Name of the queue. An empty name asks the broker to generate one.                                    |
| `durable`    | The queue survives a broker restart. This does not make its messages persistent; see [Publish Durable Messages](../how-to-publish-durable-messages.md). |
| `exclusive`  | The queue is used by this connection only and is deleted when the connection closes.                 |
| `autoDelete` | The broker deletes the queue once its last consumer has gone.                                        |
| `arguments`  | Optional queue arguments such as `x-message-ttl` or `x-dead-letter-exchange`.                        |

All flags default to `false`. To declare a durable queue with a message time-to-live of one minute:

```scala
withChannel { channel =>
  channel.queueDeclare(queue, durable = true, arguments = Map("x-message-ttl" -> Int.box(60000)))
}
```

To let the broker choose a unique name, pass an empty `QueueName` and use the returned `String`. A server-named queue is usually also `exclusive`:

```scala
withChannel { channel =>
  for {
    name <- channel.queueDeclare(QueueName(""), exclusive = true, autoDelete = true)
    _    <- Console.printLine(s"declared $name")
  } yield ()
}
```

### Channel#queueDeclarePassive

`Channel#queueDeclarePassive` checks that a queue exists without creating it. It returns the RabbitMQ client's `AMQP.Queue.DeclareOk`, which carries the queue name, the number of ready messages and the number of consumers. If the queue does not exist, the broker answers with an error and closes the channel:

```scala
class Channel {
  def queueDeclarePassive(queue: QueueName): ZIO[Any, Throwable, AMQP.Queue.DeclareOk]
}
```

To read the message count of an existing queue:

```scala
withChannel { channel =>
  for {
    _  <- channel.queueDeclare(queue)
    ok <- channel.queueDeclarePassive(queue)
    _  <- Console.printLine(s"${ok.getQueue}: ${ok.getMessageCount} ready, ${ok.getConsumerCount} consumers")
  } yield ()
}
```

### Channel#queueDelete

`Channel#queueDelete` removes a queue and everything in it. With `ifUnused` or `ifEmpty` set, the broker deletes the queue only when it has no consumers or no messages respectively; otherwise it replies with an error that closes the channel:

```scala
class Channel {
  def queueDelete(
    queue: QueueName,
    ifUnused: Boolean = false,
    ifEmpty: Boolean = false
  ): ZIO[Any, Throwable, Unit]
}
```

To delete a queue unconditionally:

```scala
withChannel(_.queueDelete(queue))
```

### Channel#purgeQueue

`Channel#purgeQueue` removes all messages that are ready to be delivered from a queue and keeps the queue. It returns the client's `AMQP.Queue.PurgeOk`. Messages that were delivered but are still unacknowledged are not removed:

```scala
class Channel {
  def purgeQueue(queue: QueueName): ZIO[Any, Throwable, AMQP.Queue.PurgeOk]
}
```

To empty a queue and report how many messages were dropped:

```scala
withChannel { channel =>
  channel.purgeQueue(queue).flatMap(ok => Console.printLine(s"purged ${ok.getMessageCount} messages"))
}
```

### Channel#messageCount

`Channel#messageCount` returns the number of messages that are ready to be delivered to consumers. The queue must exist: otherwise the broker closes the channel with an error. Messages that have been delivered but not yet acknowledged are not counted:

```scala
class Channel {
  def messageCount(queue: QueueName): ZIO[Any, Throwable, Long]
}
```

### Channel#consumerCount

`Channel#consumerCount` returns the number of consumers on a queue, with the same requirement that the queue exists:

```scala
class Channel {
  def consumerCount(queue: QueueName): ZIO[Any, Throwable, Long]
}
```

To read both counts of a queue:

```scala
withChannel { channel =>
  for {
    _         <- channel.queueDeclare(queue)
    messages  <- channel.messageCount(queue)
    consumers <- channel.consumerCount(queue)
    _         <- Console.printLine(s"$messages messages, $consumers consumers")
  } yield ()
}
```

The counts come from the broker at the moment of the call and can be stale as soon as they arrive. Use them for monitoring and tests, not for coordination.

## Exchanges and Bindings

Publishers send messages to an exchange, and the exchange routes them to queues according to its [`ExchangeType`](./model.md#exchangetype) and the bindings between the exchange and the queues.

### Channel#exchangeDeclare

`Channel#exchangeDeclare` creates an exchange, or does nothing if one with the same name and properties already exists. The `type` parameter is a Scala keyword-quoted name; pass it by position or as `` `type` = ... ``:

```scala
class Channel {
  def exchangeDeclare(
    exchange: ExchangeName,
    `type`: ExchangeType,
    durable: Boolean = false,
    autoDelete: Boolean = false,
    internal: Boolean = false,
    arguments: Map[String, AnyRef] = Map.empty
  ): ZIO[Any, Throwable, Unit]
}
```

The flags mean:

| Parameter    | Meaning                                                                                        |
| ------------ | ---------------------------------------------------------------------------------------------- |
| `durable`    | The exchange survives a broker restart.                                                        |
| `autoDelete` | The broker deletes the exchange once its last binding is removed.                              |
| `internal`   | Publishers cannot send to the exchange directly; only other exchanges can route messages to it. |
| `arguments`  | Optional exchange arguments, such as `alternate-exchange`.                                     |

To declare a durable topic exchange:

```scala
withChannel(_.exchangeDeclare(exchange, ExchangeType.Topic, durable = true))
```

### Channel#exchangeDelete

`Channel#exchangeDelete` removes an exchange. With `ifUnused = true`, the broker refuses to delete an exchange that still has bindings:

```scala
class Channel {
  def exchangeDelete(
    exchange: ExchangeName,
    ifUnused: Boolean = false
  ): ZIO[Any, Throwable, Unit]
}
```

### Channel#queueBind

`Channel#queueBind` binds a queue to an exchange, so messages that the exchange routes by this binding's `routingKey` end up in the queue. What the key means depends on the exchange type: a direct exchange compares it for equality, a topic exchange treats it as a pattern, and a fanout exchange ignores it. A headers exchange ignores it too and matches on `arguments` instead:

```scala
class Channel {
  def queueBind(
    queue: QueueName,
    exchange: ExchangeName,
    routingKey: RoutingKey,
    arguments: Map[String, AnyRef] = Map.empty
  ): ZIO[Any, Throwable, Unit]
}
```

To route every message with a key that starts with `orders.` to a queue:

```scala
withChannel { channel =>
  for {
    _ <- channel.exchangeDeclare(exchange, ExchangeType.Topic)
    _ <- channel.queueDeclare(queue)
    _ <- channel.queueBind(queue, exchange, RoutingKey("orders.#"))
  } yield ()
}
```

The library has no method to remove a binding. Deleting the queue or the exchange removes the bindings that refer to it.

## Publishing

Messages enter the broker through an exchange. One method sends them.

### Channel#publish

`Channel#publish` sends one message to an exchange. The body is a byte array, so the encoding is up to the caller:

```scala
class Channel {
  def publish(
    exchange: ExchangeName,
    body: Array[Byte],
    routingKey: RoutingKey = RoutingKey(""),
    mandatory: Boolean = false,
    immediate: Boolean = false,
    props: AMQP.BasicProperties = new AMQP.BasicProperties()
  ): ZIO[Any, Throwable, Unit]
}
```

The parameters are:

| Parameter    | Meaning                                                                                                                                  |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `exchange`   | Target exchange. `ExchangeName("")` is the default exchange, which routes to the queue named by the routing key.                         |
| `body`       | Message payload.                                                                                                                         |
| `routingKey` | Routing key, matched by the exchange's bindings. Defaults to the empty key.                                                              |
| `mandatory`  | Ask the broker to return the message if it cannot be routed to any queue. ZIO AMQP has no API to receive returned messages.              |
| `immediate`  | Ask the broker to return the message if no consumer can take it immediately. RabbitMQ 3.0 and later reject this flag, so leave it `false`. |
| `props`      | Message properties such as `deliveryMode`, `contentType`, `headers` or `correlationId`, from the RabbitMQ client's `AMQP.BasicProperties`. |

The effect completes when the message has been handed to the client's connection. It does not wait for a broker confirmation, so success does not prove that the broker stored the message. A message that no binding matches is silently dropped.

To publish a UTF-8 text message through the default exchange directly to a queue:

```scala
withChannel { channel =>
  channel.publish(ExchangeName(""), "hello".getBytes("UTF-8"), RoutingKey(QueueName.unwrap(queue)))
}
```

To publish a persistent JSON message with a content type, building the properties with the client's builder:

```scala
withChannel { channel =>
  val props = new AMQP.BasicProperties.Builder()
    .contentType("application/json")
    .deliveryMode(2)
    .build()

  channel.publish(exchange, """{"id":1}""".getBytes("UTF-8"), RoutingKey("orders.created"), props = props)
}
```

Publishing is safe to call from many fibers at once: calls queue up on the channel's permit and run one at a time.

## Consuming

A consumer receives messages from a queue as a stream. Two methods start it and tune how much the broker sends ahead.

### Channel#consume

`Channel#consume` registers a consumer on a queue and exposes the deliveries as a `ZStream` of the RabbitMQ client's `com.rabbitmq.client.Delivery`:

```scala
class Channel {
  def consume(
    queue: QueueName,
    consumerTag: ConsumerTag,
    autoAck: Boolean = false
  ): ZStream[Any, Throwable, Delivery]
}
```

The parameters are:

| Parameter     | Meaning                                                                                                                      |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `queue`       | Queue to consume from. It must exist.                                                                                        |
| `consumerTag` | Identifier of this consumer on the channel. It must be unique among the channel's active consumers.                          |
| `autoAck`     | When `true`, the broker treats a message as acknowledged as soon as it sends it. When `false` (the default), the program must call `Channel#ack` or `Channel#nack`. |

The consumer is registered when the stream starts running, not when `Channel#consume` is called. A `Delivery` has `getBody`, `getEnvelope` and `getProperties`: the envelope holds the delivery tag, the exchange, the routing key and the redelivery flag.

The stream behaves as follows:

- It emits one element per delivery and has no end of its own: it keeps waiting for the next message.
- When the stream stops, whether it was completed by an operator such as `take`, failed, or interrupted, the consumer is cancelled on the broker. Errors from the cancellation itself are ignored.
- If the broker cancels the consumer, for example because the queue was deleted, the stream completes.
- If the channel or connection shuts down, the stream fails with the client's `ShutdownSignalException`.

To print five messages from a queue and acknowledge each one after it has been handled:

```scala
withChannel { channel =>
  channel
    .consume(queue, ConsumerTag("reference-consumer"))
    .mapZIO { delivery =>
      Console.printLine(new String(delivery.getBody, "UTF-8")) *>
        channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
    }
    .take(5)
    .runDrain
}
```

The delivery tag is a `Long` in the envelope, so it is wrapped in `DeliveryTag` before it is acknowledged. See [Acknowledge Messages](../how-to-acknowledge-messages.md) for the full picture and [Stop a Consumer](../how-to-stop-a-consumer.md) for shutdown patterns.

### Channel#basicQos

`Channel#basicQos` sets the prefetch count: the maximum number of unacknowledged deliveries the broker sends before it waits for acknowledgements. It is the main tool for bounding how much work a consumer holds at once:

```scala
class Channel {
  def basicQos(count: Int, global: Boolean = false): ZIO[Any, Throwable, Unit]
}
```

With `global = false`, the limit applies to each consumer started on the channel afterwards. With `global = true`, it is shared by all consumers on the channel. A `count` of `0` means no limit. The setting has no effect on consumers with `autoAck = true`. Call it before `Channel#consume`:

```scala
withChannel { channel =>
  channel.basicQos(10) *>
    channel
      .consume(queue, ConsumerTag("limited"))
      .mapZIO(delivery => channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag)))
      .take(100)
      .runDrain
}
```

See [Limit Prefetch to Apply Back-Pressure](../how-to-limit-prefetch.md).

## Acknowledging

A delivery stays "unacknowledged" on the broker until the consumer settles it. If the channel closes before that, the broker puts the delivery back on the queue and delivers it again. These four methods settle deliveries.

### Channel#ack

`Channel#ack` acknowledges one delivery, or, with `multiple = true`, every unacknowledged delivery up to and including the given tag on this channel:

```scala
class Channel {
  def ack(deliveryTag: DeliveryTag, multiple: Boolean = false): ZIO[Any, Throwable, Unit]
}
```

Acknowledging an unknown or already settled tag is a protocol error that closes the channel.

### Channel#ackMany

`Channel#ackMany` acknowledges several deliveries with a single call to the broker. It sends `Channel#ack` for the highest tag in the sequence with `multiple = true`, so it also acknowledges every lower unacknowledged tag on the channel, including tags that are not in the sequence:

```scala
class Channel {
  def ackMany(deliveryTags: Seq[DeliveryTag]): ZIO[Any, Throwable, Unit]
}
```

The sequence must not be empty: `Channel#ackMany(Seq.empty)` throws `UnsupportedOperationException` when called, rather than failing the returned effect. To acknowledge a batch of five deliveries at once:

```scala
withChannel { channel =>
  channel
    .consume(queue, ConsumerTag("batch"))
    .grouped(5)
    .mapZIO { batch =>
      val tags = batch.map(d => DeliveryTag(d.getEnvelope.getDeliveryTag))
      channel.ackMany(tags)
    }
    .take(1)
    .runDrain
}
```

### Channel#nack

`Channel#nack` rejects one delivery, or with `multiple = true` every unacknowledged delivery up to and including the tag. With `requeue = true` the broker puts the message back on its queue; with `requeue = false` (the default) it discards the message, or dead-letters it if the queue has a dead-letter exchange:

```scala
class Channel {
  def nack(
    deliveryTag: DeliveryTag,
    requeue: Boolean = false,
    multiple: Boolean = false
  ): ZIO[Any, Throwable, Unit]
}
```

To put a message back on the queue after a failed attempt:

```scala
withChannel { channel =>
  channel
    .consume(queue, ConsumerTag("retrying"))
    .mapZIO(delivery => channel.nack(DeliveryTag(delivery.getEnvelope.getDeliveryTag), requeue = true))
    .take(1)
    .runDrain
}
```

`Channel#nack` is a RabbitMQ extension to AMQP 0-9-1, and it can reject several deliveries at once, which the standard `basic.reject` cannot.

### Channel#nackMany

`Channel#nackMany` rejects several deliveries with a single call. Like `Channel#ackMany`, it uses the highest tag with `multiple = true`, so it affects every lower unacknowledged tag as well, and it throws for an empty sequence:

```scala
class Channel {
  def nackMany(deliveryTags: Seq[DeliveryTag], requeue: Boolean = false): ZIO[Any, Throwable, Unit]
}
```
