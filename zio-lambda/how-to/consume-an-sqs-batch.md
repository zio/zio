# How to Consume an SQS Batch

> An SQS trigger sends your function a batch of messages in one `SQSEvent`. This guide shows how to process each message and how the outcome of the invocation affects the batch.

## Process Each Message

Use `SQSEvent` from the `zio-lambda-event` module as the event type. Its `records` field is the list of messages, and each `SQSRecord` has the message text in `body`:

```scala
import zio._
import zio.lambda._
import zio.lambda.event._

object QueueConsumer extends ZIOAppDefault {

  def process(record: SQSRecord): Task[Unit] =
    ZIO.logInfo(s"Processing ${record.messageId}: ${record.body}")

  val handler = (event: SQSEvent, _: Context) =>
    ZIO.foreachDiscard(event.records)(process).as("done")

  override val run = ZLambdaRunner.serve(handler)
}
```

The function returns a `String`, which the runner encodes as a JSON string. The function must return a value that has a `JsonEncoder`, so the runner has something to send back.

## Fail the Invocation When a Message Fails

`ZIO.foreachDiscard` stops at the first failure. When the effect fails, the runner reports an invocation error to Lambda, and Lambda treats the whole batch as failed. SQS then makes the messages visible again after the visibility timeout, according to your queue settings. Make `process` safe to run twice, because messages that were already processed in the failed batch are delivered again.

## Read Message Attributes

`messageAttributes` is a `Map[String, SQSMessageAttribute]`. Each attribute has a `stringValue` and a `dataType` of `SQSMessageAttributeDataType.String`, `Number` or `Binary`:

```scala
import zio.lambda.event._

def priority(record: SQSRecord): Option[String] =
  record.messageAttributes.get("priority").map(_.stringValue)
```

For all fields of the event, see [Messaging Events](../reference/events-messaging.md).
