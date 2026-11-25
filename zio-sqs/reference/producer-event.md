# ProducerEvent and ProducerError

> `ProducerEvent[T]` is the event that a [Producer](./producer.md) publishes, and `ProducerError[T]` describes an event that SQS rejected. The `ErrorOrEvent[T]` type alias combines them as the result of the `E` methods of `Producer`.

## ProducerEvent

`ProducerEvent[T]` is a case class for one event. Its constructor takes these fields:

| Field             | Type                                 | Default | Meaning                                                                                                                                                                     |
| ----------------- | ------------------------------------ | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `data`            | `T`                                  | none    | Payload. The serializer of the producer converts it to the message body.                                                                                                    |
| `attributes`      | `Map[String, MessageAttributeValue]` | none    | SQS [message attributes](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-attributes.html).                                           |
| `groupId`         | `Option[String]`                     | none    | [Message group](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagegroupid-property.html). Required for FIFO queues.                   |
| `deduplicationId` | `Option[String]`                     | none    | [Deduplication token](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/using-messagededuplicationid-property.html) that SQS uses to drop repeats. |
| `delay`           | `Option[Duration]`                   | `None`  | [Delay](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-message-timers.html) before delivery, from 0 to 15 minutes. Sent as whole seconds.   |

The companion method `ProducerEvent.apply(body: String): ProducerEvent[String]` creates an event with empty attributes and no group, deduplication id or delay.

## ProducerError

`ProducerError[T]` is a case class that extends `RuntimeException`. Its fields mirror `BatchResultErrorEntry` of the AWS API:

| Field         | Type               | Meaning                                      |
| ------------- | ------------------ | -------------------------------------------- |
| `senderFault` | `Boolean`          | Whether the caller caused the error.         |
| `code`        | `String`           | The SQS error code.                          |
| `message`     | `Option[String]`   | The message from SQS, if any.                |
| `event`       | `ProducerEvent[T]` | The event that failed, so it can be retried. |

The companion object has two methods. `ProducerError.isRecoverable(code: String): Boolean` returns `true` for `ServiceUnavailable` and `ThrottlingException`, the codes that the producer retries. `ProducerError.apply[T](entry: BatchResultErrorEntry.ReadOnly, event: ProducerEvent[T]): ProducerError[T]` builds an error from the response entry of the AWS client.

## ErrorOrEvent

`ErrorOrEvent[T]` is a type alias in the `zio.sqs.producer` package object for `Either[ProducerError[T], ProducerEvent[T]]`. It is the element type of `Producer#produceBatchE` and `Producer#sendStreamE`.

To use these types, see [Send Messages to a FIFO Queue](../how-to/send-messages-to-a-fifo-queue.md) and [Handle Send Failures](../how-to/handle-send-failures.md).
