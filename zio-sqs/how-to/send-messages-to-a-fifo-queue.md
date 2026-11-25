# Send Messages to a FIFO Queue

> To keep the order of related messages and to drop repeated ones, send the messages to a FIFO queue. SQS requires a message group on every message sent to a FIFO queue, and it requires a deduplication token unless the queue has the `ContentBasedDeduplication` attribute enabled, in which case SQS derives the token from the message body. SQS uses the token to ignore repeats. A `ProducerEvent` carries both, together with message attributes and an optional delivery delay.

## Create a FIFO Queue

The name of a FIFO queue has the form `name.fifo`, and the queue needs the `FifoQueue` attribute. Without `ContentBasedDeduplication`, every event must carry a `deduplicationId`. `Utils.createQueue` takes the attributes as a map:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.aws.sqs.model.QueueAttributeName
import zio.sqs.Utils

val createFifoQueue: RIO[Sqs, String] =
  for {
    _        <- Utils.createQueue("orders.fifo", Map(QueueAttributeName.FifoQueue -> "true"))
    queueUrl <- Utils.getQueueUrl("orders.fifo")
  } yield queueUrl
```

## Set Attributes, Group, Deduplication and Delay

`ProducerEvent` carries the payload and the SQS settings that go with it. Besides `data`, it has four settings, which [ProducerEvent and ProducerError](../reference/producer-event.md) describes in full. The following event sets the attributes, group, deduplication token and delay at once:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.aws.sqs.model.MessageAttributeValue
import zio.sqs.SqsStream
import zio.sqs.SqsStreamSettings
import zio.sqs.producer.{Producer, ProducerEvent}
import zio.sqs.serialization.Serializer

val queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/orders.fifo"

val paid: ProducerEvent[String] = ProducerEvent[String](
  data = "order 42 paid",
  attributes = Map("eventType" -> MessageAttributeValue(dataType = "String", stringValue = Some("payment"))),
  groupId = Some("orders"),
  deduplicationId = Some("order-42-paid"),
  delay = None
)
```

A FIFO queue does not support a per-message `delay`, so the event leaves it at `None`. Delays apply to standard queues, where `delay = Some(5.seconds)` postpones delivery of the message by 5 seconds.

## Publish the Events

SQS delivers the messages of one group in the order it receives them. The producer batches events and sends batches concurrently, so it does not guarantee the order in which SQS receives events that are published in parallel. To send the event to the queue:

```scala
val publish: ZIO[Sqs, Throwable, ProducerEvent[String]] =
  ZIO.scoped {
    Producer.make(queueUrl, Serializer.serializeString).flatMap(_.produce(paid))
  }
```

The producer sends whatever the event holds. An event without a group that is published to a FIFO queue fails with the `ProducerError` that SQS returns.

## Read the Attributes on the Consumer Side

Consumers receive message attributes only when they ask for them by name in the stream settings:

```scala
val eventTypes: ZIO[Sqs, Throwable, Unit] =
  SqsStream(queueUrl, SqsStreamSettings.default.withMessageAttributeName("eventType"))
    .foreach(msg => Console.printLine(msg.messageAttributes.toOption.flatMap(_.get("eventType"))))
```
