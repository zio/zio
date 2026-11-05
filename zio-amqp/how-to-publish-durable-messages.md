# How to Publish Durable Messages

> A message survives a broker restart only if two things hold: it sits in a durable queue, and it was published as persistent. This guide shows how to set both from ZIO AMQP, and how to attach other message properties such as a content type, a correlation id and headers.

All snippets assume these imports:

```scala
import com.rabbitmq.client.{ AMQP, MessageProperties }
import zio._
import zio.amqp._
import zio.amqp.model._
```

## Declare a Durable Queue

A durable queue is re-created by the broker after a restart, but it comes back empty unless its messages were persistent too. Declare it with `durable = true`:

```scala
def declare(channel: Channel): ZIO[Any, Throwable, String] =
  channel.queueDeclare(QueueName("invoices"), durable = true)
```

The same flag exists on `Channel#exchangeDeclare`. A durable queue bound to a transient exchange loses the binding on restart, so declare exchanges that feed durable queues as durable too.

## Publish a Persistent Message

`Channel#publish` takes the message properties as a RabbitMQ `AMQP.BasicProperties`. A `deliveryMode` of 2 marks the message as persistent, and the broker writes it to disk. The client provides ready-made constants, so for a plain-text payload:

```scala
def publish(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel.publish(
    exchange = ExchangeName(""),
    body = "invoice-1".getBytes("UTF-8"),
    routingKey = RoutingKey("invoices"),
    props = MessageProperties.PERSISTENT_TEXT_PLAIN
  )
```

The effect completes once the message has been handed to the client's connection. It does not wait for the broker to confirm that the message reached the disk, and ZIO AMQP has no publisher-confirm API, so a crash in that window can still lose a message.

## Attach a Content Type, an Id and Headers

To describe the payload to consumers, build the properties with the client's `AMQP.BasicProperties.Builder`. The content type tells consumers how to decode the body, the correlation id ties a message to a request, and headers carry free-form key-value pairs:

```scala
def publishJson(channel: Channel): ZIO[Any, Throwable, Unit] = {
  val props = new AMQP.BasicProperties.Builder()
    .contentType("application/json")
    .deliveryMode(2)
    .correlationId("order-42")
    .headers(java.util.Collections.singletonMap[String, AnyRef]("source", "checkout"))
    .build()

  channel.publish(ExchangeName(""), """{"id":42}""".getBytes("UTF-8"), RoutingKey("invoices"), props = props)
}
```

Publishing without `props` sends a message with no properties, which the broker treats as transient.

## Read the Properties on the Consumer

Each `Delivery` exposes the properties through `getProperties`. The getters return `null` for a property the publisher did not set, so wrap optional ones in `Option`:

```scala
def consume(channel: Channel): ZIO[Any, Throwable, Unit] =
  channel
    .consume(QueueName("invoices"), ConsumerTag("invoice-worker"))
    .mapZIO { delivery =>
      val props       = delivery.getProperties
      val contentType = Option(props.getContentType)
      val source      = Option(props.getHeaders).flatMap(h => Option(h.get("source")))
      Console.printLine(s"contentType=$contentType source=$source") *>
        channel.ack(DeliveryTag(delivery.getEnvelope.getDeliveryTag))
    }
    .runDrain
```

Header values come back as the RabbitMQ client's own types: a string header is a `LongString`, not a `String`. Call `toString` on it, or use `props.getHeaders.get("source").toString`.

## Running the Example

The file [`DurableMessagesExample.scala`](https://github.com/zio/zio-amqp/blob/master/zio-amqp-examples/src/main/scala/zio/amqp/examples/howto/DurableMessagesExample.scala) publishes a persistent JSON message to a durable queue and prints the properties that the consumer reads back. Start RabbitMQ with `docker compose up -d` and run `sbt "zio-amqp-examples/runMain zio.amqp.examples.howto.DurableMessagesExample"`. The output is:

```text
deliveryMode=2 contentType=application/json correlationId=order-42 source=howto
```
