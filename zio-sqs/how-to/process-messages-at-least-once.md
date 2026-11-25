# Process Messages at Least Once

> To process messages so that none is lost when processing fails, `SqsStream.consumeChunkAtLeastOnce` receives a chunk of messages, passes the whole chunk to a `process` function, and deletes the chunk only after `process` succeeds. If `process` fails, the messages stay in the queue. They become visible again when the visibility timeout expires, and another receive call picks them up.

## Process Chunks of Messages

`SqsStream.consumeChunkAtLeastOnce` takes the queue URL, the stream settings, the extension settings, the number of parallel consumers, and the processing function:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.aws.sqs.model.Message
import zio.sqs.{SqsMessageLifetimeExtensionSettings, SqsStream, SqsStreamSettings}

val queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue"

def process(messages: Chunk[Message.ReadOnly]): Task[Unit] =
  ZIO.debug(messages.map(_.body.getOrElse(""))) *> ZIO.sleep(14.seconds)

val atLeastOnce: RIO[Sqs, Unit] =
  SqsStream.consumeChunkAtLeastOnce(
    queueUrl = queueUrl,
    settings = SqsStreamSettings.default
      .withMaxNumberOfMessages(10)
      .withVisibilityTimeout(30)
      .withWaitTimeSeconds(20),
    extensionSettings = SqsMessageLifetimeExtensionSettings.default,
    consumerParallelism = 2
  )(process)
```

The `consumerParallelism` argument starts that many independent receive-and-process loops, each one working on its own chunk. The default is `1`. The `maxNumberOfMessages` setting controls how many messages each receive call returns, up to 10.

The `autoDelete` setting has no effect on `SqsStream.consumeChunkAtLeastOnce`, because this function always deletes after processing.

## Extending the Visibility Timeout

Processing a chunk can take longer than the visibility timeout. When that happens, SQS makes the messages visible to other consumers while the first consumer is still working on them. To prevent this, `SqsStream.consumeChunkAtLeastOnce` runs a background fiber while `process` runs. The fiber changes the visibility timeout of the chunk, first after an initial delay and then on a repeating schedule. By default, both the delay and the spacing are half of the `visibilityTimeout` setting, or 15 seconds when it is not set. The fiber stops as soon as `process` finishes, whether it succeeded or failed.

`SqsMessageLifetimeExtensionSettings` controls the fiber. `automaticExtension` turns it on or off, `overrideInitialDelay` sets the time before the first change, and `overrideRepeatSchedule` replaces the repeating schedule. To disable the fiber when processing is always shorter than the visibility timeout:

```scala
import zio.sqs.SqsMessageLifetimeExtensionSettings

val noExtension: SqsMessageLifetimeExtensionSettings =
  SqsMessageLifetimeExtensionSettings.default.withAutomaticExtension(false)
```

Each change sets the visibility timeout to the value of `maximumRetries`, which is 16 seconds with the default settings, so the chunk stays hidden for 16 seconds after each change. Choose a `visibilityTimeout` and an extension schedule that keep the changes closer together than this value. See [SqsStreamSettings and SqsMessageLifetimeExtensionSettings](../reference/sqs-stream-settings.md) for the fields.

For a single long-running message, `SqsStream.extendMessageLifetime` sets the visibility timeout of that message to a value chosen by the program. The following example processes each message for 90 seconds, which is longer than the visibility timeout of 30 seconds, so the message is extended to 120 seconds before the work starts:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.stream.ZStream
import zio.sqs.{SqsStream, SqsStreamSettings}

val queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue"

val longRunning: ZStream[Sqs, Throwable, Unit] =
  SqsStream(queueUrl, SqsStreamSettings.default.withVisibilityTimeout(30)).mapZIO { msg =>
    SqsStream.extendMessageLifetime(queueUrl, msg, 120) *>
      ZIO.sleep(90.seconds) *>
      SqsStream.deleteMessage(queueUrl, msg)
  }
```
