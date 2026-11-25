# ProducerSettings

> `ProducerSettings` controls batching, concurrency and retries of a [Producer](./producer.md). It is a case class with five fields, all of which have defaults, so `ProducerSettings()` is a valid value. The constructor fails with an `IllegalArgumentException` when `batchSize` is greater than 10.

## Fields

The fields and their defaults are:

| Field           | Type       | Default           | Meaning                                                     |
| --------------- | ---------- | ----------------- | ----------------------------------------------------------- |
| `batchSize`     | `Int`      | `10`              | Events per SQS request, at most 10.                         |
| `duration`      | `Duration` | `500.millisecond` | Longest time to wait for a batch to fill before it is sent. |
| `parallelism`   | `Int`      | `16`              | Batch requests in flight at once.                           |
| `retryDelay`    | `Duration` | `250.millisecond` | Time to wait before a recoverable failure is retried.       |
| `retryMaxCount` | `Int`      | `10`              | Number of retries for an event with a recoverable failure.  |

## Behavior

`batchSize` sets how many events go into one SQS request. SQS accepts at most 10 events per request, so a larger value makes the `ProducerSettings` constructor throw. Setting `batchSize` to `1` turns off batching, and each event is sent in its own request.

`duration` sets how long the producer waits for a batch to fill up. When the time passes first, the producer sends the events it has, even if the batch is not full. `parallelism` sets how many batch requests are in flight at once. The internal event queue holds `batchSize * parallelism` entries, rounded up to the next power of 2.

`retryDelay` and `retryMaxCount` control the retries of events that failed with a recoverable error code, as described in [Producer](./producer.md#retries).

To set these fields in a program, see [Send Messages to a Queue](../how-to/send-messages-to-a-queue.md#tune-batching-and-retries).
