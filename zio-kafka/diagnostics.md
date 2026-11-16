# Diagnostics

> Diagnostics is a callback API that lets your program observe key events inside a zio-kafka consumer or producer: polls, data requests, commits, rebalances, batches of produced records and shutdown. You plug a `Diagnostics` into `ConsumerSettings` or `ProducerSettings`, and zio-kafka calls it for every event. Use it to log rebalances, count failed records, or check in a test that the consumer did what you expect.

The API consists of a callback trait, two event hierarchies and a ready-made implementation for tests:

```scala
package zio.kafka.diagnostics

trait Diagnostics[-DiagnosticEvent] {
  def emit(event: => DiagnosticEvent): UIO[Unit]
}

object SlidingDiagnostics {
  def make[DiagnosticEvent](queueSize: Int = 16): ZIO[Scope, Nothing, QueuingDiagnostics[DiagnosticEvent]]
}
```

The consumer emits subtypes of `zio.kafka.consumer.diagnostics.DiagnosticEvent`, and the producer emits subtypes of `zio.kafka.producer.ProducerEvent`.

Diagnostics and [metrics](metrics.md) complement each other. Metrics are aggregated numbers (counters, gauges, histograms) meant for dashboards and alerting. Diagnostics hands you each individual event, with full details such as the exact topic-partitions of a rebalance or the indices of the records that failed to be produced.

## How They Work Together

You create a `Diagnostics` and put it in the settings. When the consumer or producer starts, zio-kafka wraps your callback so that it runs on its own fiber:

1. Create a `Diagnostics[DiagnosticEvent]` (consumer) or a `Diagnostics[ProducerEvent]` (producer), either by implementing the trait or with `SlidingDiagnostics.make`.
2. Pass it to `ConsumerSettings#withDiagnostics` or `ProducerSettings#withDiagnostics`.
3. While running, the consumer or producer places every event in an unbounded in-memory queue.
4. A separate fiber takes the events from that queue, in order, and calls your `Diagnostics#emit` for each of them, one at a time.
5. When the consumer or producer closes, it emits a final event (`DiagnosticEvent.ConsumerFinalized` or `ProducerEvent.ProducerFinalized`), waits until your `Diagnostics` has handled every queued event including the final one. The fiber ends after the final event.

The diagram shows the flow of events from the consumer or producer to your callback:

```
 Consumer / Producer                        separate fiber
 ───────────────────                        ──────────────
   Poll, Request,      ┌──────────────────┐
   Commit.*, ...  ───> │ unbounded queue  │ ───> your Diagnostics#emit(event)
   ...Finalized   ───> │ (in memory)      │      (one event at a time, in order)
                       └──────────────────┘
```

## The Diagnostics Trait

`Diagnostics[-DiagnosticEvent]` is a single-method trait. Its `emit` method receives one event and returns a `UIO[Unit]`, so it cannot fail:

```scala
trait Diagnostics[-DiagnosticEvent] {
  def emit(event: => DiagnosticEvent): UIO[Unit]
}

object Diagnostics {
  val NoOp: Diagnostics[Any]
}
```

The type parameter is contravariant: a `Diagnostics[Any]` is a `Diagnostics[DiagnosticEvent]` and a `Diagnostics[ProducerEvent]` at the same time, so one generic implementation can serve both a consumer and a producer. The event is passed by name, so when diagnostics are turned off, zio-kafka never builds the event objects at all.

The no-op implementation is available under three names: `Diagnostics.NoOp`, `Consumer.NoDiagnostics` and `Producer.NoDiagnostics`. It is the default in both `ConsumerSettings` and `ProducerSettings`. When the settings hold the no-op implementation, zio-kafka creates neither the queue nor the fiber.

Two type aliases name the consumer and producer variants:

```scala
object Consumer {
  type ConsumerDiagnostics = zio.kafka.diagnostics.Diagnostics[DiagnosticEvent]
  val NoDiagnostics: ConsumerDiagnostics
}

object Producer {
  type ProducerDiagnostics = zio.kafka.diagnostics.Diagnostics[ProducerEvent]
  val NoDiagnostics: ProducerDiagnostics
}
```

## Configuring Diagnostics

Diagnostics are part of the consumer and producer settings. The consumer takes a `Consumer.ConsumerDiagnostics` through `ConsumerSettings#withDiagnostics`, and the producer takes a `Producer.ProducerDiagnostics` through `ProducerSettings#withDiagnostics`. `TransactionalProducerSettings` has no diagnostics setting of its own. Set the diagnostics on the `ProducerSettings` you pass to `TransactionalProducerSettings.apply`, and the transactional producer uses them.

To log every consumer and producer event at debug level with one generic `Diagnostics[Any]`:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.diagnostics.Diagnostics
import zio.kafka.producer._

val bootstrapServers = List("localhost:9092")

val logEverything: Diagnostics[Any] = new Diagnostics[Any] {
  override def emit(event: => Any): UIO[Unit] = ZIO.logDebug(s"zio-kafka event: $event")
}

val consumerSettings: ConsumerSettings =
  ConsumerSettings(bootstrapServers)
    .withGroupId("my-group")
    .withDiagnostics(logEverything)

val producerSettings: ProducerSettings =
  ProducerSettings(bootstrapServers)
    .withDiagnostics(logEverything)

val transactionalProducerSettings: TransactionalProducerSettings =
  TransactionalProducerSettings(producerSettings, "my-transactional-id")
```

:::warning[Keep Up With the Events]
The queue between zio-kafka and your `Diagnostics` is unbounded. Because `Diagnostics#emit` runs on a separate fiber, a slow implementation does not slow down consuming or producing, but events pile up in memory when it cannot keep up. A busy consumer emits a `Poll` event for every poll and a `Request` event for every data request, so a slow implementation eventually leads to out-of-memory errors. Keep `Diagnostics#emit` fast: filter out the events you don't need, and hand expensive work to another fiber or a bounded queue. `SlidingDiagnostics` (below) is bounded by design.
:::

A slow `Diagnostics#emit` also delays shutdown, because closing the consumer or producer waits until every queued event has been handled.

:::warning[Never Throw or Die in Emit]
When `Diagnostics#emit` throws an exception or dies with a defect, the fiber that delivers the events stops. From then on, every attempt of the consumer or producer to emit an event dies with that same defect, so the problem surfaces instead of events piling up in memory unnoticed. Consumer streams fail with the defect, and so do commits. New produce calls die, and a produce call that is already waiting for its acknowledgement may never complete. Events emitted between the defect and the moment zio-kafka notices it are lost. Keep the body of `Diagnostics#emit` inside a ZIO effect (for example `ZIO.suspend { ... }`) and recover from every failure and defect with `catchAllCause`.
:::

## Consumer Events

The consumer emits subtypes of the sealed trait `zio.kafka.consumer.diagnostics.DiagnosticEvent`. Each event carries the Kafka `TopicPartition`s it is about:

```scala
sealed trait DiagnosticEvent

object DiagnosticEvent {
  final case class Poll(
    tpRequested: Set[TopicPartition],
    tpWithData: Set[TopicPartition],
    tpWithoutData: Set[TopicPartition]
  ) extends DiagnosticEvent

  final case class Request(partition: TopicPartition) extends DiagnosticEvent

  sealed trait Commit extends DiagnosticEvent
  object Commit {
    final case class Started(offsets: Map[TopicPartition, OffsetAndMetadata])                   extends Commit
    final case class Success(offsets: Map[TopicPartition, OffsetAndMetadata])                   extends Commit
    final case class Failure(offsets: Map[TopicPartition, OffsetAndMetadata], cause: Throwable) extends Commit
  }

  final case class Rebalance(
    revoked: Set[TopicPartition],
    assigned: Set[TopicPartition],
    lost: Set[TopicPartition],
    ended: Set[TopicPartition]
  ) extends DiagnosticEvent

  case object SubscriptionFinalized extends DiagnosticEvent
  case object RunloopFinalized      extends DiagnosticEvent
  case object ConsumerFinalized     extends DiagnosticEvent
}
```

The events fall into four groups: fetching data, committing, rebalancing and shutdown.

### Fetching Data

Partition streams ask the consumer for records, and the consumer polls the Kafka brokers to fulfill those requests. Two events describe this:

| Event                | Emitted when                                                                                                |
| -------------------- | ----------------------------------------------------------------------------------------------------------- |
| `Request(partition)` | A partition stream requests more records, which happens each time the stream pulls and its buffer is empty. |
| `Poll(...)`          | After every poll of the underlying Kafka consumer.                                                          |

`Poll` describes the outcome of one poll. `tpRequested` holds the partitions whose streams had an outstanding request at the time of the poll. `tpWithData` holds the partitions for which the poll returned records, which can include partitions that did not request data (zio-kafka pre-fetches). `tpWithoutData` holds the requested partitions for which the poll returned nothing, that is `tpRequested -- tpWithData`.

### Committing

Committing produces a `Started` event when a commit is requested, and a `Success` or a `Failure` event when it completes. `Started` is emitted just after the commit is queued, so occasionally it arrives after its own `Success` or `Failure`:

| Event                            | Emitted when                                                                   |
| -------------------------------- | ------------------------------------------------------------------------------ |
| `Commit.Started(offsets)`        | A commit is requested, for example by `Offset#commit` or `OffsetBatch#commit`. |
| `Commit.Success(offsets)`        | The Kafka broker confirmed a commit.                                           |
| `Commit.Failure(offsets, cause)` | A commit to the Kafka broker failed.                                           |

All offsets in these events are the committed *next* offsets, see [Offsets and Committing](offsets-and-committing.md). A `Started` event is emitted for every commit attempt, with the offsets of that attempt. Each retry of `Offset#commitOrRetry` or `OffsetBatch#commitOrRetry` (which `Consumer.consumeWith` uses) is a new attempt. The consumer combines commits that are waiting to be sent into a single Kafka commit, so one `Success` or `Failure` event can cover several `Started` events. Its `offsets` field holds the combined offsets.

Some commits produce no `Success` or `Failure` event:

- When the broker rejects a commit because a rebalance is in progress, there is no event for the rejection. The consumer silently retries the commit later, without a new `Started` event, and the outcome of that retry produces the `Success` or `Failure` event.
- When a commit takes longer than the commit timeout, the caller fails with `Consumer.CommitTimeout`, but there is no event for the timeout itself.
- Commits registered with `Consumer#registerExternalCommits` and offsets committed by a `TransactionalProducer` do not emit commit events at all.

### Rebalancing

The `Rebalance` event is emitted after the consumer has handled a poll during which Kafka changed the partition assignment. Its fields hold the partitions that were revoked, newly assigned, and lost, plus the partitions whose streams were ended. A partition stream ends when its partition is revoked or lost, so `ended` lists the revoked and lost partitions that had a running stream.

Manual subscriptions (`Subscription.manual`) assign partitions directly, without the Kafka group protocol, so they never produce `Rebalance` events.

### Shutdown

Three events mark the end of the consumer's resources. When a consumer shuts down, they arrive in this order:

1. `SubscriptionFinalized` is emitted when the scope of a subscription ends, for example when a stream created by `Consumer#plainStream` completes. It is emitted once for every subscription.
2. `RunloopFinalized` is emitted when the consumer's internal run loop shuts down. The run loop starts when it is first needed, usually with the first subscription, and stops when the consumer closes. A consumer whose run loop never started does not emit this event.
3. `ConsumerFinalized` is emitted when the consumer closes. It is always the last event.

## Producer Events

The producer emits subtypes of the sealed trait `zio.kafka.producer.ProducerEvent`. The producer always handles records in batches, and a single record is a batch of one, so every event is about a batch:

```scala
sealed trait ProducerEvent

object ProducerEvent {
  val NoPartition = -1
  val NoValue     = -1

  final case class ProducedRecord(topic: String, partition: Int, size: Int)

  final case class RecordsOffered(batchId: Long, records: Chunk[ProducedRecord]) extends ProducerEvent

  final case class RecordsSent(batchId: Long, records: Chunk[ProducedRecord], failed: Set[Int]) extends ProducerEvent

  case object ProducerFinalized extends ProducerEvent
}
```

The events do not contain the records themselves. Each record is summarized by a `ProducedRecord` that holds the topic, the partition from the `ProducerRecord`, and the size of the serialized value in bytes. For a record without a value (a tombstone), `size` is `ProducerEvent.NoValue` (`-1`). The partition is the one set on the `ProducerRecord`, not the partition the Kafka partitioner picks. For records that leave the partition choice to the partitioner, it is `ProducerEvent.NoPartition` (`-1`), so you can tell them apart from records sent to partition `0`.

The producer emits three kinds of events:

| Event                                   | Emitted when                                                                                                                                                             |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `RecordsOffered(batchId, records)`      | A batch of records is about to be offered to the producer's internal send queue. The offer can still wait for space, or never happen if the produce call is interrupted. |
| `RecordsSent(batchId, records, failed)` | Sending all records of the batch completed, successfully or not.                                                                                                         |
| `ProducerFinalized`                     | The producer closes. It is always the last event.                                                                                                                        |

The moment of `RecordsSent` depends on the `acks` producer configuration. With `acks=0` it is emitted as soon as all records are added to the socket buffer, with any other value it is emitted after the broker acknowledged the records. When the producer retries records after an authentication or authorization error (see `ProducerSettings#withAuthErrorRetrySchedule`), `RecordsSent` is emitted once, after the last attempt.

The `failed` field of `RecordsSent` holds the indices (into `records`) of the records that failed, together with the records that were not sent because an earlier record failed. When every record succeeded, `failed` is empty.

The `batchId` links the events of one batch. It starts at `0` and increases by one for every batch, separately for each producer. The `RecordsOffered` and `RecordsSent` events of the same batch have the same `batchId` and the same `records`. Batches are sent concurrently, so the `RecordsSent` events of different batches can arrive in any order, and interleaved with the `RecordsOffered` events of other batches. The `RecordsOffered` event of a batch always arrives before its own `RecordsSent` event, When several fibers produce concurrently, the order of the `RecordsOffered` events (and of the `batchId`s) can differ from the order in which the batches enter the send queue. An empty batch produces no events.

## Writing Your Own Diagnostics

To write your own diagnostics, implement `Diagnostics#emit` and pattern match on the events you are interested in. Return `ZIO.unit` for the others, so that the queue drains quickly.

### Logging Consumer Rebalances

The `Rebalance` event shows exactly which partitions moved, which helps when you investigate duplicate processing or lag after a deployment. To log every rebalance of a consumer:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.consumer.diagnostics.DiagnosticEvent
import zio.kafka.diagnostics.Diagnostics

val logRebalances: Consumer.ConsumerDiagnostics = new Diagnostics[DiagnosticEvent] {
  override def emit(event: => DiagnosticEvent): UIO[Unit] =
    event match {
      case DiagnosticEvent.Rebalance(revoked, assigned, lost, ended) =>
        ZIO.logInfo(
          s"Rebalance: assigned=${assigned.mkString(",")} revoked=${revoked.mkString(",")} " +
            s"lost=${lost.mkString(",")} ended=${ended.mkString(",")}"
        )
      case DiagnosticEvent.Commit.Failure(offsets, cause) =>
        ZIO.logWarningCause(s"Commit of $offsets failed", Cause.fail(cause))
      case _ =>
        ZIO.unit
    }
}

val settings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("my-group")
    .withDiagnostics(logRebalances)
```

The example also logs failed commits, because they often accompany a rebalance.

### Counting Failed Producer Records

The `failed` field of `RecordsSent` tells you how many records of a batch were not written. To keep a running count of failed records in a `Ref` that the rest of the program can read:

```scala
import zio._
import zio.kafka.diagnostics.Diagnostics
import zio.kafka.producer._

def countFailures(failedRecords: Ref[Long]): Producer.ProducerDiagnostics =
  new Diagnostics[ProducerEvent] {
    override def emit(event: => ProducerEvent): UIO[Unit] =
      event match {
        case ProducerEvent.RecordsSent(_, _, failed) if failed.nonEmpty =>
          failedRecords.update(_ + failed.size)
        case _ =>
          ZIO.unit
      }
  }

val producerWithFailureCount: ZIO[Scope, Throwable, (Producer, Ref[Long])] =
  for {
    failedRecords <- Ref.make(0L)
    settings = ProducerSettings(List("localhost:9092")).withDiagnostics(countFailures(failedRecords))
    producer <- Producer.make(settings)
  } yield (producer, failedRecords)
```

To feed such a count into your monitoring system, increment a ZIO metric instead of a `Ref`.

## SlidingDiagnostics

`SlidingDiagnostics.make` creates a `Diagnostics` that stores the events in a sliding queue of `queueSize` elements (default `16`). When the queue is full, the oldest event is dropped to make room for the new one, so memory use stays bounded no matter how many events arrive. This makes it a good fit for tests, and for any other situation where a slow consumer of the events must not cause high memory use:

```scala
object SlidingDiagnostics {
  def make[DiagnosticEvent](queueSize: Int = 16): ZIO[Scope, Nothing, QueuingDiagnostics[DiagnosticEvent]]
}

final case class QueuingDiagnostics[DiagnosticEvent] private[diagnostics] (queue: Queue[DiagnosticEvent])
  extends Diagnostics[DiagnosticEvent]
```

The result is a `QueuingDiagnostics`, which exposes the underlying ZIO `Queue` as `queue`. Read the collected events from it, for example with `Queue#takeAll`. The queue is shut down when the `Scope` of `SlidingDiagnostics.make` closes, so read the events before that.

In a test, create the diagnostics in an outer scope, run the consumer in an inner scope, and inspect the events once the consumer has closed. To collect the rebalances a consumer went through:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.consumer.diagnostics.DiagnosticEvent
import zio.kafka.diagnostics.SlidingDiagnostics
import zio.kafka.serde.Serde

val bootstrapServers = List("localhost:9092")

val rebalances: ZIO[Scope, Throwable, Chunk[DiagnosticEvent.Rebalance]] =
  for {
    diagnostics <- SlidingDiagnostics.make[DiagnosticEvent](queueSize = 1000)
    settings = ConsumerSettings(bootstrapServers)
                 .withGroupId("test-group")
                 .withDiagnostics(diagnostics)
    _ <- ZIO.scoped {
           Consumer.make(settings).flatMap { consumer =>
             consumer
               .plainStream(Subscription.topics("my-topic"), Serde.string, Serde.string)
               .take(10)
               .runDrain
           }
         }
    events <- diagnostics.queue.takeAll
  } yield events.collect { case rebalance: DiagnosticEvent.Rebalance => rebalance }
```

Because the consumer has closed when `Queue#takeAll` runs, `events` ends with `DiagnosticEvent.ConsumerFinalized`. Choose a `queueSize` that is large enough for the events you want to inspect: the consumer also emits `Poll` and `Request` events, and those push older events out of a small queue.
