# SqsStreamSettings and SqsMessageLifetimeExtensionSettings

> `SqsStreamSettings` configures how [SqsStream](./sqs-stream.md) receives messages. `SqsMessageLifetimeExtensionSettings` configures the background visibility extension of `SqsStream.consumeChunkAtLeastOnce`. Both are case classes with a `default` value and builder methods that return a copy with one field changed.

## SqsStreamSettings

`SqsStreamSettings.default` has the values in the Default column:

| Field                   | Type                       | Default    | Meaning                                                                                         |
| ----------------------- | -------------------------- | ---------- | ----------------------------------------------------------------------------------------------- |
| `attributeNames`        | `List[QueueAttributeName]` | `Nil`      | Queue attributes to return with each message.                                                   |
| `maxNumberOfMessages`   | `Option[Int]`              | `None`     | Messages per receive call, from 1 to 10. `None` uses the SQS default of 1.                      |
| `messageAttributeNames` | `List[String]`             | `Nil`      | Message attributes to return with each message.                                                 |
| `visibilityTimeout`     | `Option[Int]`              | `None`     | Seconds that a received message stays hidden. `None` uses the queue's setting.                  |
| `waitTimeSeconds`       | `Option[Int]`              | `Some(20)` | Seconds that a receive call waits for messages (long polling). `None` uses the queue's setting. |
| `autoDelete`            | `Boolean`                  | `false`    | Deletes each received chunk before it is emitted.                                               |
| `stopWhenQueueEmpty`    | `Boolean`                  | `false`    | Ends the stream when a receive call returns no messages.                                        |

The builder methods are `withAttributeName`, `withAttributeNames`, `withMaxNumberOfMessages`, `withMessageAttributeName`, `withMessageAttributeNames`, `withVisibilityTimeout`, `withWaitTimeSeconds`, `withAutoDelete` and `withStopWhenQueueEmpty`. The name builders differ in how they change the list. `withAttributeName` and `withMessageAttributeName` add one name to the front of the list, `withAttributeNames` replaces the whole list, and `withMessageAttributeNames` appends to the end of it.

To choose between `autoDelete` values, see [Consume Messages from a Queue](../how-to/consume-messages-from-a-queue.md).

## SqsMessageLifetimeExtensionSettings

`SqsMessageLifetimeExtensionSettings.default` enables the extension and sets `maximumRetries` to 16:

| Field                    | Type                              | Default | Meaning                                                                                                 |
| ------------------------ | --------------------------------- | ------- | ------------------------------------------------------------------------------------------------------- |
| `automaticExtension`     | `Boolean`                         | `true`  | Turns the background extension on or off.                                                               |
| `maximumRetries`         | `Int`                             | `16`    | An integer that `SqsStream.consumeChunkAtLeastOnce` passes to the extension call. See the note below.   |
| `overrideInitialDelay`   | `Option[Duration]`                | `None`  | Time before the first extension. `None` uses half the visibility timeout, or 15 seconds.                |
| `overrideRepeatSchedule` | `Option[Schedule[Any, Any, Any]]` | `None`  | Schedule that repeats the extension. `None` repeats every half visibility timeout, or every 15 seconds. |

The builder methods are `withAutomaticExtension`, `withMaximumRetries` and `withOverrideInitialDelay`. There is no builder for `overrideRepeatSchedule`, so set it through the constructor.

:::note[maximumRetries and the extension length]
`SqsStream.consumeChunkAtLeastOnce` passes `maximumRetries` as the third argument of `SqsStream.extendMessageLifetimeBatch`, which is the number of seconds to set the visibility timeout to. With the default settings, each automatic extension therefore sets the visibility timeout of the chunk to 16 seconds. Set the `visibilityTimeout` in `SqsStreamSettings` so that the extension interval and this value suit the processing time.
:::

### Methods

The two methods compute the defaults from the stream settings. Both take the `SqsStreamSettings` of the stream:

| Method         | Signature                                                | Description                                                                                                  |
| -------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `schedule`     | `(settings: SqsStreamSettings): Schedule[Any, Any, Any]` | Returns `overrideRepeatSchedule`, or a schedule spaced by half the visibility timeout (15 seconds if unset). |
| `initialDelay` | `(settings: SqsStreamSettings): Duration`                | Returns `overrideInitialDelay`, or half the visibility timeout (15 seconds if unset).                        |
