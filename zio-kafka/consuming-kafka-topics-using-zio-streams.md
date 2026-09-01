# Consuming Kafka topics using ZIO Streams

> You can stream data from Kafka using the `plainStream` method:

```scala
import zio._
import zio.Console.printLine
import zio.kafka.consumer._

consumer
  .plainStream(Subscription.topics("topic150"), Serde.string, Serde.string) // (1)
  .tap(cr => printLine(s"key: ${cr.record.key}, value: ${cr.record.value}")) // (2)
  .map(_.offset) // (3)
  .aggregateAsyncWithin(Consumer.collectOffsets, Schedule.fixed(100.millis)) // (4)
  .mapZIO(_.commit) // (5)
  .runDrain
```

(1) There are several types of subscriptions: a set of topics, a topic name pattern, or fully specified set of
topic-partitions (see [subscriptions](partition-assignment-and-offset-retrieval.md)).

(2) The `plainStream` method returns a stream of `CommittableRecord` objects. In this line, we access the key
and value from the `CommittableRecord` and print them to the console.

:::caution
Some stream operators (like `tap` and `mapZIO`) break the chunking structure of the stream which heavily reduces
throughput. See [avoiding chunk-breakers](avoiding-chunk-breakers.md) for more information and alternatives.
:::

(3) Here we get the offset of the record, so we now have a stream of offsets.

(4) The offsets are aggregated into an `OffsetBatch`. The offset batch keeps the highest seen offset per partition.
ZStream operator `aggregateAsyncWithin` runs upstream in a separate fiber, allowing consuming and committing to run in
parallel.

The offset batches are emitted every 100 milliseconds. The delay should be longer than the typical commit-duration in
your system. For example, lets assume the delay is set to 2ms and a commit operation takes 10ms. Then, when some records
are consumed, 2ms later `aggregateAsyncWithin` emits an offset batch and starts collecting offsets for the next batch.
In addition, the commit operation starts. Again 2ms later `aggregateAsyncWithin` tries to emit the next batch. However,
this will block because the commit is still ongoing. Since `aggregateAsyncWithin` is now blocked, it stops accepting
offsets from upstream, halting the consumer until the commit completes.

For applications that only see a few records per second or less, you can remove line (4) and commit the offset for each
record separately. In this approach the commit executes on the same fiber as the rest of the stream. This lowers
throughput. Explicitly committing every record is therefore almost never the best approach.

To get the lowest commit latency you can commit continuously; a new commit is started as soon as the previous completes.
This is done by replacing line (4) with `.aggregateAsync(Consumer.collectOffsets)`. Be aware that registering commits is
an intensive operation for the kafka brokers. With dozens of consumers continuously committing, the high load may slow
down everything.

(5) All offsets in the `OffsetBatch` are committed. As soon as the commit is done, the next offset batch is pulled
from upstream.

## Consuming with more parallelism

To process partitions (assigned to the consumer) in parallel, you may use the `partitionedStream` method, which creates
a nested stream of partitions:

```scala
import zio._
import zio.Console.printLine
import zio.kafka.consumer._

consumer
  .partitionedStream(Subscription.topics("topic150"), Serde.string, Serde.string)
  .flatMapPar(Int.MaxValue) { case (topicPartition, partitionStream) => // (1)
    ZStream.fromZIO(
      printLine(s"Starting stream for topic '${topicPartition.topic}' partition ${topicPartition.partition}")
    ) *>
      partitionStream
        .tap(record => printLine(s"key: ${record.key}, value: ${record.value}")) // (2)
        .map(_.offset) // (3)
  }
  .aggregateAsyncWithin(Consumer.collectOffsets, Schedule.fixed(100.millis)) // (4)
  .mapZIO(_.commit)  // (5)
  .runDrain
```

In this example, each partition is processed in parallel on separate fibers, while a single fiber is doing commits.

(1) When using partitionedStream with `flatMapPar(n)`, it is recommended to set n to `Int.MaxValue`. N must be equal to
or greater than the number of partitions your consumer subscribes to otherwise there will be unhandled partitions and
Kafka eventually evicts your consumer.

Each time a new partition is assigned to the consumer, a new partition stream is created and the body of the
`flatMapPar` is executed.

(2) As before, here we process the received `CommittableRecord`.

(3) Note how the offsets from all the sub-streams are merged into a single stream of `Offset`s. This ensures only a
single stream is doing commits.

(4) and (5) The offsets are aggregated into an `OffsetBatch` and committed as in the previous section.

## Controlled shutdown (experimental)

The examples above will keep processing records forever, or until the fiber is interrupted, typically at application
shutdown. When interrupted, some records may be 'in-flight', e.g. being processed by one of the stages of your consumer
stream user code. Those records will be processed partly and their offsets may not be committed. For fast shutdown in an
at-least-once processing scenario this is fine.

zio-kafka also supports a _graceful shutdown_, where the fetching of records for the subscribed topics/partitions is
stopped, the streams are ended and all downstream stages are completed, allowing in-flight records to be fully
processed.

Use the `*StreamWithControl` variants of `plainStream`, `partitionedStream` and `partitionedAssignmentStream` for this
purpose. These methods return a `StreamControl` object allowing you to stop fetching records and terminate the execution
of the stream gracefully.

The `StreamControl` can be used with `Consumer.runWithGracefulShutdown`, which can gracefully terminate the stream upon
fiber interruption. This is useful for a controlled shutdown when your application is terminated:

```scala
import zio.Console.printLine
import zio.kafka.consumer._
import zio._

ZIO.scoped {
  for {
    control <- consumer.partitionedStreamWithControl(
      Subscription.topics("topic150"),
      Serde.string,
      Serde.string
    )
    _ <- consumer.runWithGracefulShutdown(control, shutdownTimeout = 30.seconds) { stream =>
        stream.flatMapPar(Int.MaxValue) { case (topicPartition, partitionStream) =>
          partitionStream
            .tap(record => printLine(s"key: ${record.key}, value: ${record.value}"))
            .map(_.offset)
        }
        .aggregateAsyncWithin(Consumer.collectOffsets, Schedule.fixed(100.millis))
        .mapZIO(_.commit)
        .runDrain
      }
  } yield ()
}
```

For more control over when to end the stream, use the `StreamControl` construct like this:

```scala
import zio.Console.printLine
import zio.kafka.consumer._

ZIO.scoped {
    for {
      control <- consumer.partitionedStreamWithControl(
          Subscription.topics("topic150"),
          Serde.string,
          Serde.string
        )
      fiber <- control.stream.flatMapPar(Int.MaxValue) { case (topicPartition, partitionStream) =>
            partitionStream
              .tap(record => printLine(s"key: ${record.key}, value: ${record.value}"))
              .map(_.offset)
          }
          .aggregateAsyncWithin(Consumer.collectOffsets, Schedule.fixed(100.millis))
          .mapZIO(_.commit)
          .runDrain
          .forkScoped
      // At some point in your code, when some condition is met
      _ <- control.end // Stop fetching more records
      _ <- fiber.join // Wait for graceful completion of the stream
    } yield ()
}
```

As of zio-kafka 3.0, this functionality is experimental. If no issues are reported and the API has good usability, it
will eventually be marked as stable.
