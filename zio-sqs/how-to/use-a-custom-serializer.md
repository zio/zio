# Use a Custom Serializer

> SQS message bodies are strings, so every `Producer` needs a way to turn its events into strings. The `Serializer[A]` type class does this conversion. The library provides only the producing side: when the body arrives on the consumer side, the program reads `Message.ReadOnly#body` and decodes it itself.

## The Serializer Type

`Serializer[A]` has one abstract method, `apply`, which converts a value of type `A` into a `String`. A `Serializer` is given to `Producer.make` along with the queue URL. Every event that the producer sends then goes through it:

```scala
import zio.sqs.serialization.Serializer

val numbers: Serializer[Int] = (n: Int) => s"""{"value":$n}"""
```

The lambda above is the whole serializer. Because `Serializer` has a single abstract method, a lambda of type `A => String` converts into a `Serializer[A]` directly.

## Serializing Strings

When the events are already strings, `Serializer.serializeString` is the identity serializer. It returns each string unchanged, so the message body is exactly the text that the program supplied:

```scala
import zio.sqs.serialization.Serializer

val text: String = Serializer.serializeString("plain text body")
```

## Adapting a Serializer with contramap

`Serializer#contramap` builds a new serializer for another type by applying a function to the input before the existing serializer runs. A producer of case classes can therefore reuse a string serializer. The examples in the rest of this page share the `Order` type and its serializer, which this section defines:

```scala
import zio.sqs.serialization.Serializer

case class Order(id: Int, item: String)

val orderSerializer: Serializer[Order] =
  Serializer.serializeString.contramap[Order](order => s"""{"id":${order.id},"item":"${order.item}"}""")
```

The hand-written JSON above is enough for a short example. A real program should use a JSON library, such as ZIO JSON, to encode its types. Its encoder can then be wrapped with `Serializer#contramap` in the same way.

## Using a Serializer with a Producer

To use a serializer, pass it to `Producer.make`. The following program sends two orders, with `orderSerializer` converting each one into a message body:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.sqs.Utils
import zio.sqs.producer.{Producer, ProducerEvent}

val publishOrders: ZIO[Sqs, Throwable, Unit] = for {
  queueUrl <- Utils.getQueueUrl("OrdersQueue")
  _        <- ZIO.scoped {
                Producer.make(queueUrl, orderSerializer).flatMap { producer =>
                  producer.produceBatch(
                    List(Order(1, "book"), Order(2, "pen")).map { order =>
                      ProducerEvent(data = order, attributes = Map.empty, groupId = None, deduplicationId = None)
                    }
                  )
                }
              }
} yield ()
```

The shortcut `ProducerEvent(body: String)` only accepts strings, so the typed events are built with the full `ProducerEvent` constructor, leaving the attributes, group and deduplication settings empty. The producer passes each `Order` to the serializer and never sees its string form.

## Reading Message Bodies

The consumer side has no deserializer. `SqsStream` emits `Message.ReadOnly` values, and `Message.ReadOnly#body` has the type `zio.prelude.data.Optional[String]`. Its `toOption` method converts it to a Scala `Option`. The program then turns the body back into a value with its own decoder. The decoder below reverses the JSON format from the `Order` serializer with a regular expression:

```scala
import zio.aws.sqs.model.Message

val orderPattern = """\{"id":(\d+),"item":"([^"]*)"\}""".r

def decodeOrder(message: Message.ReadOnly): Option[Order] =
  message.body.toOption.flatMap {
    case orderPattern(id, item) => Some(Order(id.toInt, item))
    case _                      => None
  }
```

A body that does not match the pattern decodes to `None`, so the caller decides how to handle it. A regular expression is fine for this short format. Real bodies should go through a JSON decoder.

For the members of `Serializer`, see [Serializer and Utils](../reference/serializer-and-utils.md).
