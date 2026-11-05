# Model Types

> The `zio.amqp.model` package holds the small types that appear in the signatures of [`Amqp`](./amqp.md) and [`Channel`](./channel.md): five newtypes that keep queue names, exchange names, routing keys, consumer tags and delivery tags from being mixed up, a typed URI, and the `ExchangeType` of an exchange. [`AMQPConfig`](./amqp.md#amqpconfig) lives in the same package. The examples on this page use the following import:

```scala
import zio.amqp.model._
```

## Newtypes

Queue names, exchange names, routing keys and consumer tags are all strings, so passing one where another is expected compiles with plain `String` parameters and fails at run time on the broker. The model package wraps each of them in a [ZIO Prelude](https://zio.dev/zio-prelude/) newtype, which the compiler treats as a distinct type and which has no run-time cost:

```scala
package object model {
  object QueueName    extends Newtype[String]
  object ExchangeName extends Newtype[String]
  object RoutingKey   extends Newtype[String]
  object ConsumerTag  extends Newtype[String]
  object DeliveryTag  extends Subtype[Long]
  object AmqpUri      extends Subtype[URI]
}
```

Each object is both the companion and the type: `QueueName` is the type `QueueName.Type` and the constructor `QueueName.apply`. To create the values and get the underlying strings back with `QueueName.unwrap`:

```scala
val queue    = QueueName("orders")
// queue: QueueName.Type = "orders"
val exchange = ExchangeName("orders-exchange")
// exchange: ExchangeName.Type = "orders-exchange"
val key      = RoutingKey("orders.created")
// key: RoutingKey.Type = "orders.created"
val tag      = ConsumerTag("billing-service")
// tag: ConsumerTag.Type = "billing-service"

QueueName.unwrap(queue)
// res0: String = "orders"
```

Because the newtypes are distinct, a call such as `channel.queueBind(exchange, queue, key)` with the first two arguments swapped does not compile.

### QueueName

`QueueName` names a queue. [`Channel#queueDeclare`](./channel.md#channelqueuedeclare) accepts an empty name, which asks the broker to generate one, and returns the name it chose as a `String`:

```scala
val serverNamed = QueueName("")
```

### ExchangeName

`ExchangeName` names an exchange. The empty name, `ExchangeName("")`, is the broker's predefined default exchange: it is a direct exchange that routes a message to the queue whose name equals the routing key.

### RoutingKey

`RoutingKey` is the label a publisher attaches to a message and a binding matches against. [`Channel#publish`](./channel.md#channelpublish) defaults it to `RoutingKey("")`.

### ConsumerTag

`ConsumerTag` identifies one consumer on a channel. [`Channel#consume`](./channel.md#channelconsume) requires one and uses it again to cancel the consumer when the stream ends, so two live consumers on the same channel must not share a tag.

### DeliveryTag

`DeliveryTag` identifies a delivery on a channel so it can be acknowledged. It is declared with `Subtype[Long]` rather than `Newtype[Long]`, so a `DeliveryTag` is also a `Long`: it can be passed where a `Long` is expected and compared with `<`. The broker assigns tags in increasing order, which is what makes [`Channel#ackMany`](./channel.md#channelackmany) possible.

The consumer hands out the raw tag as a `Long` through `Delivery#getEnvelope`, so it has to be wrapped before it is acknowledged:

```scala
import com.rabbitmq.client.Delivery

def tagOf(delivery: Delivery): DeliveryTag =
  DeliveryTag(delivery.getEnvelope.getDeliveryTag)

val asLong: Long = DeliveryTag(42L)
```

A delivery tag is only meaningful on the channel that received the delivery. Acknowledging it on another channel is a protocol error.

### AmqpUri

`AmqpUri` is a `java.net.URI` that has been produced by [`AMQPConfig#toUri`](./amqp.md#converting-to-a-uri). Because it is a subtype, it can be passed to `Amqp.connect(uri: URI)` directly. To build one by hand:

```scala
import java.net.URI

val uri: AmqpUri = AmqpUri(new URI("amqp://guest:guest@localhost:5672/%2F"))
```

## ExchangeType

An exchange decides how a published message is routed to queues, and its type fixes the rule. `ExchangeType` is a sealed trait with four cases for the types every RabbitMQ broker provides and one case for types added by plugins:

```scala
sealed trait ExchangeType extends Product with Serializable {
  def name: String
}

object ExchangeType {
  case object Direct                extends ExchangeType
  case object Fanout                extends ExchangeType
  case object Topic                 extends ExchangeType
  case object Headers               extends ExchangeType
  case class Custom(`type`: String) extends ExchangeType

  implicit def represent(`type`: ExchangeType): String
}
```

The `name` of each case is the string the broker expects. To list them:

```scala
List(ExchangeType.Direct, ExchangeType.Fanout, ExchangeType.Topic, ExchangeType.Headers).map(_.name)
// res4: List[String] = List("direct", "fanout", "topic", "headers")
```

The cases behave as follows:

| Case                   | Routes a message to                                                          |
| ---------------------- | ---------------------------------------------------------------------------- |
| `ExchangeType.Direct`  | Queues bound with a routing key equal to the message's routing key.          |
| `ExchangeType.Fanout`  | Every bound queue; the routing key is ignored.                               |
| `ExchangeType.Topic`   | Queues whose binding pattern matches the routing key (`*` and `#` wildcards). |
| `ExchangeType.Headers` | Queues whose binding arguments match the message headers.                    |
| `ExchangeType.Custom`  | Whatever the plugin that provides the exchange type decides.                 |

`ExchangeType.Custom` carries the type name as given. To refer to an exchange type from a RabbitMQ plugin, such as the delayed-message plugin:

```scala
ExchangeType.Custom("x-delayed-message").name
// res5: String = "x-delayed-message"
```

The implicit conversion `ExchangeType.represent` turns an `ExchangeType` into its name where a `String` is expected, which is how [`Channel#exchangeDeclare`](./channel.md#channelexchangedeclare) passes it to the RabbitMQ client. You rarely need it yourself.

See [Route Messages with Exchanges](../how-to-route-with-exchanges.md) for a worked example of the first three types.
