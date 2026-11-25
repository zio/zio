# SqsStream

> `SqsStream` is the object that reads messages from a queue, deletes them, and changes their visibility timeout. Every method needs the `Sqs` client in its environment, which [Getting Started](../getting-started.md) shows how to build. The settings that configure the methods are described in [SqsStreamSettings and SqsMessageLifetimeExtensionSettings](./sqs-stream-settings.md).

To read messages as a stream and delete them one at a time or in batches, see [Consume Messages from a Queue](../how-to/consume-messages-from-a-queue.md). To process each chunk with automatic deletion, see [Process Messages at Least Once](../how-to/process-messages-at-least-once.md).

## Methods

Each method of `SqsStream` is listed with its signature and a short description:

| Method                                 | Signature                                                                                                                                                                                               | Description                                                                           |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `SqsStream.apply`                      | `(queueUrl: String, settings: SqsStreamSettings = SqsStreamSettings.default): ZStream[Sqs, Throwable, Message.ReadOnly]`                                                                                | Long-polls the queue and emits the received messages.                                 |
| `SqsStream.consumeChunkAtLeastOnce`    | `(queueUrl: String, settings: SqsStreamSettings, extensionSettings: SqsMessageLifetimeExtensionSettings, consumerParallelism: Int = 1)(process: Chunk[Message.ReadOnly] => Task[Unit]): RIO[Sqs, Unit]` | Processes each chunk and deletes it only after `process` succeeds.                    |
| `SqsStream.extendMessageLifetime`      | `(queueUrl: String, message: Message.ReadOnly, seconds: Int): RIO[Sqs, Unit]`                                                                                                                           | Sets the visibility timeout of one message to `seconds`.                              |
| `SqsStream.extendMessageLifetimeBatch` | `(queueUrl: String, messages: Chunk[Message.ReadOnly], seconds: Int, maximumRetries: Int = 8): RIO[Sqs, Chunk[Message.ReadOnly]]`                                                                       | Sets the visibility timeout of a list of messages, in batches of 10.                  |
| `SqsStream.deleteMessage`              | `(queueUrl: String, msg: Message.ReadOnly): RIO[Sqs, Unit]`                                                                                                                                             | Deletes one message.                                                                  |
| `SqsStream.deleteMessageBatch`         | `(queueUrl: String, msgs: Chunk[Message.ReadOnly], maximumRetries: Int = 8): RIO[Sqs, Chunk[Message.ReadOnly]]`                                                                                         | Deletes messages in batches of 10 and retries the entries that SQS reports as failed. |
| `SqsStream.deleteMessageBatchSink`     | `(queueUrl: String, maximumRetries: Int = 8): ZSink[Sqs, Throwable, Message.ReadOnly, Nothing, Unit]`                                                                                                   | A sink that calls `deleteMessageBatch` for each chunk it receives.                    |

## Behavior

`SqsStream.apply` never ends on its own unless `stopWhenQueueEmpty` is `true`. When `autoDelete` is `true`, it deletes each received chunk with `SqsStream.deleteMessageBatch` before it emits the chunk, which gives at-most-once delivery.

`SqsStream.consumeChunkAtLeastOnce` ignores `autoDelete` because it always deletes a chunk after `process` succeeds. It respects `stopWhenQueueEmpty`. It starts `consumerParallelism` independent receive-and-process loops, and while `process` runs, it extends the visibility timeout of the chunk in the background.

`SqsStream.extendMessageLifetimeBatch` and `SqsStream.deleteMessageBatch` split their input into batches of 10, the SQS limit. When SQS reports entries of a batch as failed, they retry those entries up to `maximumRetries` times, logging a warning on each attempt. When the retries run out, they fail with `Failed to change message visibility after retrying` or `Failed to delete messages after retrying`. Both return the messages that succeeded.

All methods fail with a `Throwable` when SQS returns an error.
