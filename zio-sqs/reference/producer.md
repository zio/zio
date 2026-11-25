# Producer

> `Producer[T]` is a trait for publishing events of type `T` to a queue. It collects events into batches and sends them to SQS in a background fiber, so callers can publish one event at a time and still send up to 10 events per request. The `T` type parameter is the payload type given to `Producer.make`.

## Creating a Producer

`Producer.make` creates a producer. Its signature is:

```scala
object Producer {
  def make[T](
    queueUrl: String,
    serializer: Serializer[T],
    settings: ProducerSettings = ProducerSettings()
  ): ZIO[Sqs & Scope, Throwable, Producer[T]]
}
```

The producer runs in the `Scope`, so `ZIO.scoped` must wrap its use. When the scope closes, the producer stops its background fiber and shuts down its internal queues. The `serializer` converts each event payload into the message body. See [Serializer and Utils](./serializer-and-utils.md) and [ProducerSettings](./producer-settings.md) for the other parameters.

## Methods

The methods that fail when SQS rejects an event are `produce`, `produceBatch`, `sendStream` and `sendSink`. The `E` variants, `produceBatchE` and `sendStreamE`, return an `ErrorOrEvent` for each event instead:

| Method          | Signature                                                                          | Description                                                                                             |
| --------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `produce`       | `(e: ProducerEvent[T]): Task[ProducerEvent[T]]`                                    | Publishes one event. Fails if SQS rejects it.                                                           |
| `produceBatch`  | `(es: Iterable[ProducerEvent[T]]): Task[Iterable[ProducerEvent[T]]]`               | Publishes the events. Fails if SQS rejects any of them.                                                 |
| `produceBatchE` | `(es: Iterable[ProducerEvent[T]]): Task[Iterable[ErrorOrEvent[T]]]`                | Publishes the events and returns one `ErrorOrEvent` per input. Does not fail for SQS errors.            |
| `sendStream`    | `Stream[Throwable, ProducerEvent[T]] => ZStream[Any, Throwable, ProducerEvent[T]]` | Publishes each event of the input stream and emits the published events. Fails if SQS rejects an event. |
| `sendStreamE`   | `Stream[Throwable, ProducerEvent[T]] => ZStream[Any, Throwable, ErrorOrEvent[T]]`  | Publishes each event of the input stream and emits one `ErrorOrEvent` per event.                        |
| `sendSink`      | `ZSink[Any, Throwable, Iterable[ProducerEvent[T]], Nothing, Unit]`                 | A sink that publishes each batch of events it receives. Fails if SQS rejects an event.                  |

`sendStream` and `sendStreamE` publish up to `batchSize` events at a time, so their concurrency follows `batchSize`, not `parallelism`. If the request itself throws an exception, every event in that batch fails with the exception.

## Retries

The producer treats the error codes `ServiceUnavailable` and `ThrottlingException` as recoverable and resends those events after `retryDelay`. It retries each event at most `retryMaxCount` times, and then the event fails with its last `ProducerError`. Every other error code fails the event immediately.

For the event and error types, see [ProducerEvent and ProducerError](./producer-event.md). To publish events in practice, see [Send Messages to a Queue](../how-to/send-messages-to-a-queue.md) and [Handle Send Failures](../how-to/handle-send-failures.md).
