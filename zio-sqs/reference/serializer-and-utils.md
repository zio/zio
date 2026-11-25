# Serializer and Utils

> `Serializer[A]` converts events into message bodies, and the `Utils` object looks up and creates queues by name.

## Serializer

`Serializer[A]` is a trait with one abstract method, so a lambda of type `A => String` converts into a `Serializer[A]` directly. The library has only the producing side: there is no deserializer, and the consumer reads `Message.ReadOnly#body` itself.

| Member                       | Signature                       | Description                                                   |
| ---------------------------- | ------------------------------- | ------------------------------------------------------------- |
| `Serializer#apply`           | `(a: A): String`                | Converts a value to a message body. The only abstract method. |
| `Serializer#contramap`       | `[B](f: B => A): Serializer[B]` | Builds a serializer for `B` by applying `f` first.            |
| `Serializer.serializeString` | `Serializer[String]`            | The identity serializer for strings.                          |

To write a serializer for a case class, see [Use a Custom Serializer](../how-to/use-a-custom-serializer.md).

## Utils

`Utils` has two methods for working with queues by name. Both need the `Sqs` client:

| Method              | Signature                                                                             | Description                                               |
| ------------------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| `Utils.createQueue` | `(name: String, attributes: Map[QueueAttributeName, String] = Map()): RIO[Sqs, Unit]` | Creates a queue with the given name and attributes.       |
| `Utils.getQueueUrl` | `(name: String): RIO[Sqs, String]`                                                    | Returns the URL of an existing queue with the given name. |

`Utils.createQueue` creates the queue when it does not exist. The `attributes` map sets queue attributes, such as `QueueAttributeName.FifoQueue` for a FIFO queue, as shown in [Send Messages to a FIFO Queue](../how-to/send-messages-to-a-fifo-queue.md).
