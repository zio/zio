# Handle Send Failures

> To publish events and decide what happens to the ones that SQS rejects, use the `E` variants of the `Producer` methods. The examples below use this shared setup. The queue URL is a placeholder, and every example needs a real AWS account, so they are compiled but not run:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.sqs.producer.{ErrorOrEvent, Producer, ProducerEvent}
import zio.sqs.serialization.Serializer

val queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue"

val events: List[ProducerEvent[String]] = List("order created", "order paid", "order shipped").map(ProducerEvent(_))
```

## Get a Result for Each Event

The other `Producer` methods fail as soon as SQS rejects an event. The `E` variants, `Producer#produceBatchE` and `Producer#sendStreamE`, do not fail for that. Each event produces an `ErrorOrEvent[T]`, which is an `Either` of a `ProducerError` on the left or the published event on the right. The program can then decide what to do with the failures:

```scala
val retryFailed: ZIO[Sqs, Throwable, Iterable[ProducerEvent[String]]] =
  ZIO.scoped {
    Producer.make(queueUrl, Serializer.serializeString).flatMap { producer =>
      producer.produceBatchE(events).flatMap { results: Iterable[ErrorOrEvent[String]] =>
        val failed = results.collect { case Left(error) => error.event }
        producer.produceBatch(failed)
      }
    }
  }
```

The example above publishes the failed events again with `Producer#produceBatch`. A real program would usually limit the number of attempts and log the codes of the errors first.

`ProducerError` is a `RuntimeException` with four fields. `senderFault` tells whether the caller caused the error. `code` is the SQS error code, and `message` is the optional message from SQS. `event` is the event that failed, so it can be retried or stored.

## Understand Retries

Some SQS errors are temporary. The producer treats `ServiceUnavailable` and `ThrottlingException` as recoverable and resends those events after `retryDelay`. Each event is retried at most `retryMaxCount` times. When the limit is reached, the event fails with its last `ProducerError`. Every other error code fails the event immediately, without a retry.

If the request itself throws an exception, every event in that batch fails with the exception.
