# Metrics Observers

> Metrics observers are the extension point that decides where the zio-kafka consumer and producer metrics go. While it runs, the consumer reports every poll, commit, rebalance and a periodic snapshot of its internal queues to a `ConsumerMetricsObserver`. The producer reports every completed produce call and the state of its send queue to a `ProducerMetricsObserver`. By default, both observers record the measurements as ZIO metrics, so any ZIO metrics backend can export them. When you implement your own observer, you can send the same measurements to another metrics library, add logging, change histogram boundaries, or turn metrics off.

The module consists of two observer traits, their default implementations, and two case classes that describe every metric with a `MetricInfo`:

```scala
// package zio.kafka.consumer.metrics
trait ConsumerMetricsObserver { /* 6 callbacks, see below */ }
final class ZioMetricsConsumerMetricsObserver(consumerMetrics: ConsumerMetrics, metricLabels: Set[MetricLabel])
    extends ConsumerMetricsObserver
final case class ConsumerMetrics(pollCounter: MetricInfo.Counter = ???, /* 23 more descriptors */)

// package zio.kafka.producer.metrics
trait ProducerMetricsObserver { /* 4 callbacks, see below */ }
final class ZioMetricsProducerMetricsObserver(producerMetrics: ProducerMetrics, metricLabels: Set[MetricLabel])
    extends ProducerMetricsObserver
final case class ProducerMetrics(produceCallsCounter: MetricInfo.Counter = ???, /* 6 more descriptors */)

// package zio.kafka.metrics
sealed trait MetricInfo { def name: String; def description: String }
```

The metric names, their meaning and how to read them on a dashboard are on the [Metrics](metrics.md) page. This page covers the API behind them: when each callback runs, what it receives, and how to install your own observer.

:::warning[Unstable API]
The scaladoc of `ConsumerMetricsObserver`, `ProducerMetricsObserver`, `ConsumerMetrics`, `ProducerMetrics` and `MetricInfo` marks them as an UNSTABLE API: they may change in an incompatible way in any zio-kafka version, without notice. Expect to adapt a custom observer when you upgrade.
:::

## How They Work Together

When a consumer or producer starts, it picks one observer and calls it from its own code paths for the rest of its life:

1. You optionally pass an observer with `ConsumerSettings#withMetricsObserver` or `ProducerSettings#withMetricsObserver`.
2. When the consumer or producer is created, it uses that observer. When none is set, it creates a `ZioMetricsConsumerMetricsObserver` or `ZioMetricsProducerMetricsObserver` with the default descriptors and the labels from `withMetricsLabels`.
3. The consumer calls the observer after every poll, commit, aggregated commit and rebalance, and on a schedule for the runloop snapshot. The producer calls it when a produce call completes, when the send fiber takes a batch from the send queue, after authorization failures, and every 10 seconds for the send queue size.
4. The default observers turn each `MetricInfo` of `ConsumerMetrics` or `ProducerMetrics` into a ZIO metric (`Metric.counter`, `Metric.gauge` or `Metric.histogram`), tagged with the labels, and update it on every callback.

The following diagram shows where the callbacks come from:

```
ConsumerSettings#metricsObserver
  Some(observer) -> observer
  None           -> new ZioMetricsConsumerMetricsObserver(ConsumerMetrics(), metricLabels)
        │
        ▼
┌───────────────────────────┐     observePoll              after every poll
│  ConsumerMetricsObserver  │ ◄── observePollAuthError     each poll that fails with an auth error
└───────────────────────────┘     observeRebalance         after a poll during which a rebalance ran
                                  observeCommit            each successful Offset#commit
                                  observeAggregatedCommit  each successful commit to the broker
                                  observeRunloopMetrics    on runloopMetricsSchedule

ProducerSettings#metricsObserver
  Some(observer) -> observer
  None           -> new ZioMetricsProducerMetricsObserver(ProducerMetrics(), metricLabels)
        │
        ▼
┌───────────────────────────┐     observeProduce           each fully acknowledged produce call
│  ProducerMetricsObserver  │ ◄── observeSendAuthError     each send with auth errors
└───────────────────────────┘     observeSendQueueTake     each batch the send fiber takes
                                  observeSendQueueSize     every 10 seconds
```

Every callback returns `UIO[Unit]`. Most callbacks run inside the consumer's or producer's own processing (the poll loop, commit handling, or a produce call), so the scaladoc of both traits asks them to return quickly. `ConsumerMetricsObserver#observeRunloopMetrics` and `ProducerMetricsObserver#observeSendQueueSize` are the exception: they run on their own fibers, on a schedule.

## Installing an Observer

Each settings class has a `metricsObserver: Option[...]` field, `None` by default, and a method that sets it:

```scala
final case class ConsumerSettings(/* ... */) {
  def withMetricsLabels(metricLabels: Set[MetricLabel]): ConsumerSettings
  def withMetricsObserver(observer: ConsumerMetricsObserver): ConsumerSettings
  def withRunloopMetricsSchedule(runloopMetricsSchedule: Schedule[Any, Unit, Long]): ConsumerSettings
}

final case class ProducerSettings(/* ... */) {
  def withMetricsLabels(metricLabels: Set[MetricLabel]): ProducerSettings
  def withMetricsObserver(observer: ProducerMetricsObserver): ProducerSettings
}
```

`withMetricsLabels` only configures the default observer. As soon as an observer is set, the consumer and producer use it as is, and the labels from `withMetricsLabels` are ignored. When you install a `ZioMetricsConsumerMetricsObserver` or `ZioMetricsProducerMetricsObserver` yourself, pass the labels to its constructor instead.

The two settings below record the consumer metrics with the same `group-id` label, the first through the settings and the second through the constructor of the default observer:

```scala
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.consumer.metrics.{ ConsumerMetrics, ZioMetricsConsumerMetricsObserver }
import zio.metrics.MetricLabel

val bootstrapServers = List("localhost:9092")
val labels           = Set(MetricLabel("group-id", "orders-service"))

// The default observer, created by the consumer, gets the labels from the settings
val withLabels: ConsumerSettings =
  ConsumerSettings(bootstrapServers)
    .withGroupId("orders-service")
    .withMetricsLabels(labels)

// An explicit observer: labels set with withMetricsLabels would be ignored
val withObserver: ConsumerSettings =
  ConsumerSettings(bootstrapServers)
    .withGroupId("orders-service")
    .withMetricsObserver(new ZioMetricsConsumerMetricsObserver(ConsumerMetrics(), labels))
```

When no observer is set, every consumer and producer creates its own default observer when it starts, in `Consumer.make`, `Producer.make` and the other constructors, including those of `TransactionalProducer`. An observer is just a value, so you can share one instance between several consumers; they then report into the same metrics. When an application runs several consumers or producers with the default observer, give each one distinct labels, or their measurements land in the same ZIO metrics.

## ConsumerMetricsObserver

`ConsumerMetricsObserver` has six callbacks, each called from a different place in the consumer:

```scala
trait ConsumerMetricsObserver {
  def observePoll(resumedCount: Int, pausedCount: Int, latency: Duration, pollSize: Int): UIO[Unit]
  def observeCommit(latency: Duration): UIO[Unit]
  def observeAggregatedCommit(latency: Duration, commitSize: Long): UIO[Unit]
  def observeRebalance(currentlyAssignedCount: Int, assignedCount: Int, revokedCount: Int, lostCount: Int): UIO[Unit]
  def observeRunloopMetrics(state: ConsumerMetricsObserver.ConsumerState): UIO[Unit]
  def observePollAuthError: UIO[Unit]
}
```

The sections below describe when the consumer calls each one. See the [Consumer](consumer.md) page for the runloop, partition streams and commits these callbacks report on.

### Polling

The runloop calls `ConsumerMetricsObserver#observePoll` once after every successful call to the Java consumer's `poll`. Before polling, the runloop asks the fetch strategy which partitions to fetch: it resumes those and pauses the other assigned partitions, which applies backpressure to slow streams. The arguments are:

- `resumedCount` is the number of assigned partitions resumed for this poll.
- `pausedCount` is the number of assigned partitions paused for this poll.
- `latency` is the duration of the poll, including any retries after authentication or authorization errors.
- `pollSize` is the number of records the poll returned.

`ConsumerMetricsObserver#observePollAuthError` takes no arguments. The consumer calls it every time a poll fails with an `AuthorizationException` or `AuthenticationException`, while it decides whether to retry the poll according to `ConsumerSettings#authErrorRetrySchedule` (by default 5 retries, 500 ms apart). Only errors thrown by `poll` itself count; see [Detecting Authorization Failures](detecting-authorization-failures.md) for failures that `poll` does not report.

### Committing

Committing produces two kinds of measurements: one per commit request and one per commit sent to the broker.

The consumer calls `ConsumerMetricsObserver#observeCommit` once for every commit request made through the zio-kafka API, such as `Offset#commit` or `OffsetBatch#commit`, when the request completes successfully. `latency` runs from the moment the request is queued until the broker acknowledges it. A commit that fails or exceeds `ConsumerSettings#commitTimeout` is not observed, and neither are offsets that a `TransactionalProducer` commits as part of a transaction.

In each runloop cycle, the consumer merges all queued commit requests into one aggregated commit and sends it to the broker with the Java consumer's `commitAsync`. When the broker acknowledges that commit, the consumer calls `ConsumerMetricsObserver#observeAggregatedCommit`, provided the aggregated commit contained at least one request:

- `latency` runs from the `commitAsync` call until the acknowledgement arrives.
- `commitSize` is the sum, over all partitions in the commit, of how far the committed offset moved since the previous commit of this consumer. A partition this consumer has not committed to before counts as 0, so the value is an approximation of the number of committed records.

### Rebalancing

When the rebalance listener runs during a poll, the runloop updates its partition streams after that poll and then calls `ConsumerMetricsObserver#observeRebalance` once. When the listener runs more than once during the same poll, the consumer merges those events into one call. The arguments are:

- `currentlyAssignedCount` is the number of partitions assigned to the consumer after the rebalance.
- `assignedCount` is the number of newly assigned partitions.
- `revokedCount` is the number of revoked partitions.
- `lostCount` is the number of lost partitions.

### Sampling Runloop State

The other callbacks report events, but `ConsumerMetricsObserver#observeRunloopMetrics` reports a snapshot. When the consumer starts, it forks a fiber that takes the snapshot immediately and then repeats it according to `ConsumerSettings#runloopMetricsSchedule`, `Schedule.fixed(500.millis)` by default. The snapshot is a `ConsumerState`:

```scala
object ConsumerMetricsObserver {
  final case class ConsumerState(
    pendingRequestCount: Int,
    assignedPartitionCount: Int,
    perPartitionQueueSizes: Chunk[Int],
    perPartitionOutstandingPolls: Chunk[Int],
    isSubscribed: Boolean,
    commandQueueSize: Int,
    commitQueueSize: Int,
    pendingCommitCount: Int
  ) {
    val totalQueueSize: Int = perPartitionQueueSizes.sum
  }
}
```

The pending requests, the partition streams and the subscription come from the runloop state as of its most recent cycle; the queue sizes and commit counts are read when the snapshot is taken. The field names mostly speak for themselves, but a few need a word of explanation: `pendingRequestCount` is the number of partition streams that ran out of records and requested more, `perPartitionOutstandingPolls` counts, per partition stream, the polls during which records idled in its queue, `commitQueueSize` is the number of commit requests waiting to be merged into the next aggregated commit, and `pendingCommitCount` is the number of commits sent to the broker and awaiting completion.

To sample the runloop less often, for example every 5 seconds, change the schedule in the consumer settings:

```scala
import zio._
import zio.kafka.consumer.ConsumerSettings

val settings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("orders-service")
    .withRunloopMetricsSchedule(Schedule.fixed(5.seconds))
```

## ProducerMetricsObserver

`ProducerMetricsObserver` has four callbacks:

```scala
trait ProducerMetricsObserver {
  def observeProduce(latency: Duration, batchSize: Int): UIO[Unit]
  def observeSendQueueSize(size: Int): UIO[Unit]
  def observeSendQueueTake(latency: Duration): UIO[Unit]
  def observeSendAuthError(errorCount: Int): UIO[Unit]
}
```

To read them, it helps to know how the producer works (see [Producer](producer.md)): every produce call serializes its records and offers them as one batch to an internal send queue; a background fiber takes the batches from the queue and sends their records to Kafka. `Producer` and `TransactionalProducer` call the observer in the same places.

The producer calls `ProducerMetricsObserver#observeProduce` once per produce call, after the broker has acknowledged every record of the call, including records sent again after an authorization error. `latency` runs from the moment the producer starts to enqueue the serialized batch until the last acknowledgement. `batchSize` is 1 for `Producer#produce` and `Producer#produceAsync`, and the number of records for the chunk variants such as `Producer#produceChunk`, `Producer#produceChunkAsync` and `Producer#produceChunkAsyncWithFailures`. A produce call with at least one failed record is not observed.

`ProducerMetricsObserver#observeSendQueueSize` receives the number of batches (produce calls, not records) in the send queue. A background fiber samples it every 10 seconds; this interval is not configurable. The capacity of the queue is set with `ProducerSettings#withSendBufferSize`.

`ProducerMetricsObserver#observeSendQueueTake` runs each time the send fiber takes a batch from the queue. `latency` is the time the batch spent in the queue, including waiting for queue capacity.

`ProducerMetricsObserver#observeSendAuthError` runs when a send attempt gets authentication or authorization errors. For a single-record produce call (`Producer#produce`, `Producer#produceAsync`), it runs with `errorCount` 1 each time the record fails with such an error. For a chunk produce call (`Producer#produceChunk` and its variants, including `Producer#produceAll`), it runs when some records fail with such an error and no record fails with another error; `errorCount` is the number of records that failed with an authentication or authorization error. In both cases the producer calls it before it asks `ProducerSettings#authErrorRetrySchedule` whether to retry, so it also runs with the default schedule, `Schedule.stop`, which never retries.

## The Default Observers

`ZioMetricsConsumerMetricsObserver` and `ZioMetricsProducerMetricsObserver` record every callback as ZIO metrics. Each has a primary constructor that takes the descriptors and the labels, and an auxiliary constructor that takes only the labels and uses the default descriptors:

```scala
final class ZioMetricsConsumerMetricsObserver(consumerMetrics: ConsumerMetrics, metricLabels: Set[MetricLabel])
    extends ConsumerMetricsObserver {
  def this(metricLabels: Set[MetricLabel]) = this(ConsumerMetrics(), metricLabels)
}

final class ZioMetricsProducerMetricsObserver(producerMetrics: ProducerMetrics, metricLabels: Set[MetricLabel])
    extends ProducerMetricsObserver {
  def this(metricLabels: Set[MetricLabel]) = this(ProducerMetrics(), metricLabels)
}
```

The default observers convert every descriptor into a ZIO metric with the same name and description, tagged with `metricLabels`:

- A `MetricInfo.Counter` becomes a `Metric.counter` that counts in `Long`.
- A `MetricInfo.Gauge[Int]` becomes a `Metric.gauge`; the `Int` value is converted to `Double`.
- A `MetricInfo.Histogram[Duration]` becomes a `Metric.histogram` with the descriptor's boundaries, and records the duration in seconds.
- A `MetricInfo.Histogram[Int]` or `MetricInfo.Histogram[Long]` becomes a `Metric.histogram` with the descriptor's boundaries.

Each callback updates the descriptors named after it, for example `ConsumerMetricsObserver#observePoll` updates `pollCounter`, `pollLatencyHistogram` and the other poll descriptors, and `ProducerMetricsObserver#observeProduce` updates the `produce*` descriptors. Counters such as `partitionsAssignedCounter`, `produceRecordsCounter` (by `batchSize`) and `sendAuthErrorCounter` (by `errorCount`) grow by the matching count, and the gauges are set to the latest value. `ConsumerMetricsObserver#observeRunloopMetrics` adds one observation per partition stream to `queueSizeHistogram` and `queuePollsHistogram`, sets `subscriptionStateGauge` to 1 or 0, and does not record `assignedPartitionCount`.

## Metric Descriptors

`ConsumerMetrics` and `ProducerMetrics` are repositories of metric names, descriptions and histogram boundaries. Every field has a default, so `ConsumerMetrics()` describes the standard metrics, and `ConsumerMetrics#copy` changes only the fields you name. A custom observer for another metrics library can read the same descriptors to keep the standard names.

Each field is a `MetricInfo`, a sealed trait with one case class per metric kind:

```scala
sealed trait MetricInfo {
  def name: String
  def description: String
}

object MetricInfo {
  final case class Counter(name: String, description: String)  extends MetricInfo
  final case class Gauge[A](name: String, description: String) extends MetricInfo
  final case class Histogram[A](
    name: String,
    description: String,
    boundaries: zio.metrics.MetricKeyType.Histogram.Boundaries
  ) extends MetricInfo
}
```

The type parameter of `Gauge` and `Histogram` is the type of the measured value (`Int`, `Long` or `Duration`); `MetricInfo` stores no value itself. The boundaries are ZIO's `Histogram.Boundaries`, usually built with `Histogram.Boundaries.fromChunk`, `Histogram.Boundaries.linear` or `Histogram.Boundaries.exponential`. Latency boundaries are in seconds, because the default observers record durations in seconds.

The companion objects define the default boundaries as methods, such as `ConsumerMetrics.defaultLatencyBoundaries`, so you can reuse them. Most follow an exponential series based on 𝑒. For every default and the histograms that use it, see the [`ConsumerMetrics`](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/consumer/metrics/ConsumerMetrics$.html) and [`ProducerMetrics`](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/producer/metrics/ProducerMetrics$.html) API docs.

:::note
`Histogram.Boundaries.fromChunk` drops every boundary that is not greater than 0 and appends `Double.MaxValue` as the last boundary (the `+Inf` bucket). The `0` that several default series start with therefore never reaches `Boundaries#values`, for the default observer and for a custom observer that reads the descriptor alike. The lowest bucket of those histograms is the first positive boundary, and it also counts the value 0.
:::

## Common Patterns

The examples below cover the usual reasons to touch this API. All of them only build settings and observers; nothing connects to Kafka until the settings are passed to `Consumer.make` or `Producer.make`.

### Customizing Histogram Boundaries

When the default buckets are too coarse for your workload, copy the default descriptors, replace the boundaries you need, and pass the result to the default observer. Here, polls usually take a few milliseconds, so the poll latency histogram gets boundaries from 1 ms to 512 ms, doubling each step:

```scala
import zio._
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.consumer.metrics.{ ConsumerMetrics, ZioMetricsConsumerMetricsObserver }
import zio.kafka.metrics.MetricInfo
import zio.metrics.MetricKeyType.Histogram
import zio.metrics.MetricLabel

val defaults = ConsumerMetrics()

// 0.001, 0.002, 0.004, ..., 0.512 seconds
val fastPollBoundaries: Histogram.Boundaries = Histogram.Boundaries.exponential(0.001, 2.0, 10)

val pollLatency: MetricInfo.Histogram[Duration] =
  defaults.pollLatencyHistogram.copy(boundaries = fastPollBoundaries)

val consumerMetrics: ConsumerMetrics = defaults.copy(pollLatencyHistogram = pollLatency)

val settings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("orders-service")
    .withMetricsObserver(
      new ZioMetricsConsumerMetricsObserver(consumerMetrics, Set(MetricLabel("group-id", "orders-service")))
    )
```

The same approach renames a metric: copy the `MetricInfo` with a different `name`. For the producer, copy `ProducerMetrics()` and pass it to `ZioMetricsProducerMetricsObserver`.

### Adding Logging to the Default Observer

An observer can delegate to another observer and do extra work around it. This one keeps all the default ZIO metrics, and also logs slow polls and every rebalance:

```scala
import zio._
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.consumer.metrics.{ ConsumerMetricsObserver, ZioMetricsConsumerMetricsObserver }
import zio.kafka.consumer.metrics.ConsumerMetricsObserver.ConsumerState
import zio.metrics.MetricLabel

final class LoggingConsumerMetricsObserver(underlying: ConsumerMetricsObserver, slowPoll: Duration)
    extends ConsumerMetricsObserver {

  override def observePoll(resumedCount: Int, pausedCount: Int, latency: Duration, pollSize: Int): UIO[Unit] =
    underlying.observePoll(resumedCount, pausedCount, latency, pollSize) *>
      ZIO
        .logWarning(s"Slow poll: ${latency.toMillis} ms for $pollSize records")
        .when(latency > slowPoll)
        .unit

  override def observeRebalance(
    currentlyAssignedCount: Int,
    assignedCount: Int,
    revokedCount: Int,
    lostCount: Int
  ): UIO[Unit] =
    underlying.observeRebalance(currentlyAssignedCount, assignedCount, revokedCount, lostCount) *>
      ZIO.logInfo(
        s"Rebalance: $assignedCount assigned, $revokedCount revoked, $lostCount lost, $currentlyAssignedCount now assigned"
      )

  override def observeCommit(latency: Duration): UIO[Unit] =
    underlying.observeCommit(latency)

  override def observeAggregatedCommit(latency: Duration, commitSize: Long): UIO[Unit] =
    underlying.observeAggregatedCommit(latency, commitSize)

  override def observeRunloopMetrics(state: ConsumerState): UIO[Unit] =
    underlying.observeRunloopMetrics(state)

  override def observePollAuthError: UIO[Unit] =
    underlying.observePollAuthError
}

val labels = Set(MetricLabel("group-id", "orders-service"))

val settings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("orders-service")
    .withMetricsObserver(
      new LoggingConsumerMetricsObserver(new ZioMetricsConsumerMetricsObserver(labels), slowPoll = 1.second)
    )
```

Because a custom observer replaces the default one, the labels go into the `ZioMetricsConsumerMetricsObserver` constructor, not into `withMetricsLabels`. `ZIO.logInfo` and `ZIO.logWarning` are cheap with ZIO's default logger; with a logging backend that writes synchronously, keep the extra work in the callback small.

### Bridging to Another Metrics Backend

To send zio-kafka metrics to a library other than ZIO metrics, implement the trait and translate each callback into calls on that library. The example below uses a small in-memory registry as a stand-in for that library, and reads the metric names from `ProducerMetrics` so the exported names match the standard ones. It records counters and keeps the latest value of each histogram as a gauge, to stay short:

```scala
import zio._
import zio.kafka.metrics.MetricInfo
import zio.kafka.producer.{ Producer, ProducerSettings }
import zio.kafka.producer.metrics.{ ProducerMetrics, ProducerMetricsObserver }

// Stand-in for the API of another metrics library
final class Registry(counters: Ref[Map[String, Long]], gauges: Ref[Map[String, Double]]) {
  def increment(metric: MetricInfo, by: Long): UIO[Unit] =
    counters.update(m => m.updated(metric.name, m.getOrElse(metric.name, 0L) + by))

  def set(metric: MetricInfo, value: Double): UIO[Unit] =
    gauges.update(_.updated(metric.name, value))
}

object Registry {
  val make: UIO[Registry] =
    for {
      counters <- Ref.make(Map.empty[String, Long])
      gauges   <- Ref.make(Map.empty[String, Double])
    } yield new Registry(counters, gauges)
}

final class RegistryProducerMetricsObserver(registry: Registry, metrics: ProducerMetrics)
    extends ProducerMetricsObserver {

  override def observeProduce(latency: Duration, batchSize: Int): UIO[Unit] =
    registry.increment(metrics.produceCallsCounter, 1L) *>
      registry.increment(metrics.produceRecordsCounter, batchSize.toLong) *>
      registry.set(metrics.produceLatencyHistogram, latency.toNanos.toDouble / 1e9)

  override def observeSendQueueSize(size: Int): UIO[Unit] =
    registry.set(metrics.sendQueueSizeHistogram, size.toDouble)

  override def observeSendQueueTake(latency: Duration): UIO[Unit] =
    ZIO.unit

  override def observeSendAuthError(errorCount: Int): UIO[Unit] =
    registry.increment(metrics.sendAuthErrorCounter, errorCount.toLong)
}

val producer: ZIO[Scope, Throwable, Producer] =
  for {
    registry <- Registry.make
    settings = ProducerSettings(List("localhost:9092"))
                 .withMetricsObserver(new RegistryProducerMetricsObserver(registry, ProducerMetrics()))
    producer <- Producer.make(settings)
  } yield producer
```

Each callback only updates a `Ref`, which keeps it fast. With a real library, prefer its non-blocking update calls; if an update can block, hand the measurement to a queue that another fiber drains, so the producer never waits for the metrics backend.

### Disabling Metrics

`ConsumerMetricsObserver.NoOp` and `ProducerMetricsObserver.NoOp` implement every callback as `ZIO.unit`. Installing them turns off zio-kafka's own metrics, which is useful in tests or when you do not export metrics at all:

```scala
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.consumer.metrics.ConsumerMetricsObserver
import zio.kafka.producer.ProducerSettings
import zio.kafka.producer.metrics.ProducerMetricsObserver

val bootstrapServers = List("localhost:9092")

val consumerSettings: ConsumerSettings =
  ConsumerSettings(bootstrapServers)
    .withGroupId("orders-service")
    .withMetricsObserver(ConsumerMetricsObserver.NoOp)

val producerSettings: ProducerSettings =
  ProducerSettings(bootstrapServers)
    .withMetricsObserver(ProducerMetricsObserver.NoOp)
```

`NoOp` only silences the zio-kafka metrics. The Java client metrics stay available through `Consumer#metrics` and `Producer#metrics`. With the consumer `NoOp`, the runloop still runs its sampling fiber on `ConsumerSettings#runloopMetricsSchedule`, but every snapshot is discarded.

## Related Pages

Metrics observers sit next to several other parts of zio-kafka:

- [Metrics](metrics.md) lists every metric name with its type and description, and explains how to read them.
- [Consumer](consumer.md) and [Producer](producer.md) describe `ConsumerSettings` and `ProducerSettings`, including their metrics settings, and the internals the callbacks report on.
- [Diagnostics](diagnostics.md) is the event-based counterpart: it hands you each individual event with full details on a separate fiber, while metrics observers receive numeric measurements inline, meant to become counters, gauges and histograms.
- [Consumer Tuning](consumer-tuning.md) shows how to use the consumer metrics to tune the consumer.
