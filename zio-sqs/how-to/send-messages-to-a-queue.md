# Send Messages to a Queue

> `Producer` publishes events of type `T` to a queue. Each event is turned into a string by a `Serializer`, collected into batches, and sent to SQS in the background. The producer does the batching, so the program can publish one event at a time and still send 10 events per request.

## Create a Producer

`Producer.make` takes a queue URL, a serializer, and optional `ProducerSettings`. It returns the producer inside a `Scope`, because the producer starts a background fiber that must be stopped when the scope closes. `ZIO.scoped` provides the scope and closes it at the end of the program.

The examples below use this shared setup. The queue URL is a placeholder, and every example needs a real AWS account, so they are compiled but not run:

```scala
import zio._
import zio.aws.sqs.Sqs
import zio.stream.ZStream
import zio.sqs.Utils
import zio.sqs.producer.{ErrorOrEvent, Producer, ProducerEvent, ProducerSettings}
import zio.sqs.serialization.Serializer

val queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/MyQueue"

val events: List[ProducerEvent[String]] = List("order created", "order paid", "order shipped").map(ProducerEvent(_))
```

To publish a single event with `Producer#produce`, create the producer and call the method inside the same scope:

```scala
val publishOne: ZIO[Sqs, Throwable, ProducerEvent[String]] =
  ZIO.scoped {
    Producer.make(queueUrl, Serializer.serializeString).flatMap(_.produce(ProducerEvent("first event")))
  }
```

## Publish a Batch

`Producer#produceBatch` publishes a collection of events and waits for SQS to answer each event. The collection it gives back contains each event from the input, in the same order. The task fails if SQS rejects any of the events:

```scala
val publishBatch: ZIO[Sqs, Throwable, Iterable[ProducerEvent[String]]] =
  ZIO.scoped {
    Producer.make(queueUrl, Serializer.serializeString).flatMap(_.produceBatch(events))
  }
```

## Stream Events to a Queue

`Producer#sendStream` takes a stream of events and returns a stream of the published events. Each event is sent as it flows through, and the stream fails if SQS rejects an event. To publish a stream of events and wait for the end of it:

```scala
val publishStream: ZIO[Sqs, Throwable, Unit] =
  ZIO.scoped {
    Producer.make(queueUrl, Serializer.serializeString).flatMap { producer =>
      producer.sendStream(ZStream.fromIterable(events)).runDrain
    }
  }
```

`Producer#sendSink` is a sink for the same purpose. It accepts a stream of batches, where each element is an `Iterable` of events, and publishes every batch it receives:

```scala
val publishSink: ZIO[Sqs, Throwable, Unit] =
  ZIO.scoped {
    Producer.make(queueUrl, Serializer.serializeString).flatMap { producer =>
      ZStream.succeed(events).run(producer.sendSink)
    }
  }
```

## Tune Batching and Retries

`ProducerSettings` controls batching, concurrency, and retries. All fields have defaults, so the producer works with `ProducerSettings()`:

```scala
val tunedSettings: ProducerSettings = ProducerSettings(
  batchSize = 5,
  duration = 200.millis,
  parallelism = 4,
  retryDelay = 1.second,
  retryMaxCount = 3
)

val tunedProducer: ZIO[Sqs & Scope, Throwable, Producer[String]] =
  Producer.make(queueUrl, Serializer.serializeString, tunedSettings)
```

To choose the values, see [ProducerSettings](../reference/producer-settings.md). `Producer#sendStream` publishes `batchSize` events at a time, so its concurrency follows `batchSize`, not `parallelism`. Setting `batchSize` to `1` turns off batching: each event is sent in its own request.

To react to events that SQS rejects, see [Handle Send Failures](./handle-send-failures.md). To send events that need attributes, groups or a delay, see [Send Messages to a FIFO Queue](./send-messages-to-a-fifo-queue.md).
