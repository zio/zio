# Consume Messages from a Queue

> `SqsStream.apply` reads messages from a queue as a ZIO stream. Each element is a `Message.ReadOnly`, and the stream keeps long-polling SQS until the program stops reading from it. Two delivery models are available. With at-most-once delivery, the library deletes each message when it is received. With at-least-once delivery, the message is deleted only after the program has finished processing it.

## Reading a Stream of Messages

`SqsStream.apply` takes a queue URL and a `SqsStreamSettings` value. The default settings enable long polling, which means each receive call waits up to 20 seconds for a message to arrive:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.sqs.{SqsStream, SqsStreamSettings}

val queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue"

val printBodies: ZIO[Sqs, Throwable, Unit] =
  SqsStream(queueUrl, SqsStreamSettings.default).foreach(msg => Console.printLine(msg.body.getOrElse("")))
```

The stream never ends on its own unless `stopWhenQueueEmpty` is enabled, as described below. Any operator from ZIO Streams can transform the messages before they reach the final step.

## Choosing When Messages Are Deleted

A message that SQS delivers stays hidden from other consumers until its visibility timeout expires. Deleting the message removes it for good. The `autoDelete` setting controls when the library deletes messages. It defaults to `false`.

With `autoDelete` set to `false`, the program deletes each message itself after it has finished processing it. `SqsStream.deleteMessage` deletes one message:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.sqs.{SqsStream, SqsStreamSettings}

val queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue"

val processThenDelete: ZIO[Sqs, Throwable, Unit] =
  SqsStream(queueUrl, SqsStreamSettings.default.withAutoDelete(false)).foreach { msg =>
    Console.printLine(msg.body.getOrElse("")) *> SqsStream.deleteMessage(queueUrl, msg)
  }
```

Deleting messages one at a time makes one request per message. `SqsStream.deleteMessageBatchSink` deletes the messages of each chunk in batches of up to 10 instead. Chunks reach the sink only after the stages before it have processed them, so this sink gives at-least-once delivery when `autoDelete` is `false`:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.sqs.{SqsStream, SqsStreamSettings}

val queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue"

val processInBatches: ZIO[Sqs, Throwable, Unit] =
  SqsStream(queueUrl, SqsStreamSettings.default.withAutoDelete(false))
    .tap(msg => Console.printLine(msg.body.getOrElse("")))
    .run(SqsStream.deleteMessageBatchSink(queueUrl))
```

With `autoDelete` set to `true`, the library deletes each chunk in a batch before the chunk reaches the program. A message that fails during processing is already gone, so this mode gives at-most-once delivery.

## Stopping When the Queue Is Empty

By default, the stream waits for new messages forever. With `stopWhenQueueEmpty` set to `true`, the stream ends as soon as a receive call returns no messages. This makes it possible to drain a queue into a list:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.aws.sqs.model.Message
import zio.sqs.{SqsStream, SqsStreamSettings}

val queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue"

val drain: ZIO[Sqs, Throwable, Chunk[Message.ReadOnly]] =
  SqsStream(queueUrl, SqsStreamSettings.default.withStopWhenQueueEmpty(true).withWaitTimeSeconds(3)).runCollect
```

Setting `waitTimeSeconds` to a small value, such as 3, makes the empty check return sooner. Long polling still applies, so the stream waits up to that time before it decides that the queue is empty.

To delete messages only after the program has processed a whole chunk, and to keep long-running chunks hidden, see [Process Messages at Least Once](./process-messages-at-least-once.md).

## Handling Errors

Errors from SQS fail the stream with a `Throwable`. The stream stops at that point, so a program that must keep running should restart it or handle the error with the usual ZIO operators, such as `retry` or `catchAll`.

Batch deletions retry on their own. When SQS reports a message as failed in a batch delete, the library retries that message up to 8 times. If it still fails, the deletion fails with `Failed to delete messages after retrying`.
