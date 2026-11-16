# Consumer

> `Consumer` is the **zio-kafka service for reading records from Kafka**. It wraps a single Apache Kafka `KafkaConsumer` and exposes its records as ZIO streams of `CommittableRecord`s, so that each record carries an `Offset` you can commit once you have processed it. One `Consumer` can serve several streams at once, each with its own subscription and deserializers.

The `Consumer` service:

- Polls for you — a background loop owns the Kafka consumer, fetches records ahead of demand, and pauses partitions whose streams are not pulling.
- Streams per partition — records of one partition arrive in order, and you choose between one merged stream or one stream per partition.
- Never auto-commits — offsets are committed explicitly after processing (by your code or by `Consumer#consumeWith`), which gives at-least-once delivery.
- Is scoped — the Kafka consumer is closed when the `Scope` that created it closes.

The shape of the service, with representative members:

```scala
trait Consumer {
  def plainStream[R, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V], bufferSize: Int = 4): ZStream[R, Throwable, CommittableRecord[K, V]]
  def partitionedStream[R, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V]): Stream[Throwable, (TopicPartition, ZStream[R, Throwable, CommittableRecord[K, V]])]
  def consumeWith[R: EnvironmentTag, R1: EnvironmentTag, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V], commitRetryPolicy: Schedule[Any, Any, Any] = Schedule.exponential(1.second) && Schedule.recurs(3))(f: ConsumerRecord[K, V] => URIO[R1, Unit]): ZIO[R & R1, Throwable, Unit]
  def stopConsumption: UIO[Unit]
  def assignment: Task[Set[TopicPartition]]
  def committed(partitions: Set[TopicPartition], timeout: Duration = Duration.Infinity): Task[Map[TopicPartition, Option[OffsetAndMetadata]]]
  val consumerSettings: ConsumerSettings
  // ... more streams and metadata queries, see below
}

object Consumer {
  def make(settings: ConsumerSettings): ZIO[Scope, Throwable, Consumer]
  def live: RLayer[ConsumerSettings, Consumer]
  // ...
}
```

The how-to guides walk through common tasks step by step: [creating a consumer](creating-a-consumer.md), [consuming Kafka topics using ZIO Streams](consuming-kafka-topics-using-zio-streams.md) and [sharing a consumer between streams](sharing-consumer.md). This page is the reference for the whole API.

## Usage

To consume a topic, we create a consumer from `ConsumerSettings`, stream its records, and commit their offsets in batches:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.serde.Serde

val settings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("my-group")
    .withClientId("my-client")

val program: Task[Unit] =
  ZIO.scoped {
    for {
      consumer <- Consumer.make(settings)
      _ <- consumer
             .plainStream(Subscription.topics("my-topic"), Serde.string, Serde.string)
             .tap(record => ZIO.logInfo(s"${record.key}: ${record.value}"))
             .map(_.offset)
             .aggregateAsync(Consumer.collectOffsets)
             .mapZIO(_.commit)
             .runDrain
    } yield ()
  }
```

`Consumer.collectOffsets` merges the offsets that arrive while a commit is running into one `OffsetBatch`, so committing does not slow down the stream. See [Offsets and Committing](offsets-and-committing.md) for the details.

## Creating a Consumer

A `Consumer` needs a `Scope`, because it owns a connection to the Kafka cluster that must be closed. There are three ways to create one.

### Consumer.make

`Consumer.make` creates a consumer from settings. It validates the settings first and fails with an `IllegalArgumentException` when they are invalid (see [Settings Validation](#settings-validation)):

```scala
object Consumer {
  def make(settings: ConsumerSettings): ZIO[Scope, Throwable, Consumer]
}
```

When the scope closes, zio-kafka stops consuming and closes the Kafka consumer, giving it at most `closeTimeout` (30 seconds by default) to close. The [creating a consumer](creating-a-consumer.md) guide shows how to use it with `ZIO.scoped` and `ZLayer.scoped`.

### Consumer.live

`Consumer.live` is a layer that reads `ConsumerSettings` from the environment and produces a `Consumer`. It is the natural choice when the settings come from another layer, for example from configuration:

```scala
import zio._
import zio.kafka.consumer._

val settingsLayer: ULayer[ConsumerSettings] =
  ZLayer.succeed(ConsumerSettings(List("localhost:9092")).withGroupId("my-group"))

val consumerLayer: TaskLayer[Consumer] =
  settingsLayer >>> Consumer.live
```

### Consumer.fromJavaConsumerWithPermit

`Consumer.fromJavaConsumerWithPermit` wraps a `KafkaConsumer` that you create yourself. You rarely need this; it is an advanced API for when your application also needs direct access to the Java consumer. Prefer `Consumer.make` otherwise. Its signature:

```scala
object Consumer {
  def fromJavaConsumerWithPermit(
    javaConsumer: org.apache.kafka.clients.consumer.Consumer[Array[Byte], Array[Byte]],
    settings: ConsumerSettings,
    access: Semaphore
  ): ZIO[Scope, Throwable, Consumer]
}
```

The Java consumer and zio-kafka share the semaphore `access`. While you hold its permit, zio-kafka cannot poll, so hold it as briefly as possible. Unlike `Consumer.make`, this method does not validate `settings`. You are responsible for all of the following:

- Create the `KafkaConsumer` and close it after the zio-kafka consumer.
- Disable auto commit (`enable.auto.commit=false`).
- Create `access` as a semaphore with exactly one permit.
- Acquire a permit from `access` before every use of the Java consumer, and release it afterwards.
- Never call `subscribe`, `unsubscribe`, `assign`, `poll`, `commit*`, `seek`, `pause`, `resume` or `enforceRebalance` on the Java consumer.
- Keep the configuration of the Java consumer in sync with `settings`, for example by building `settings` with `ConsumerSettings(bootstrapServers).withProperties(config)`.

Breaking any of these rules is likely to cause errors that are hard to track down. To create the Java consumer in a scope and hand it to zio-kafka:

```scala
import org.apache.kafka.clients.consumer.{ ConsumerConfig, KafkaConsumer }
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import zio._
import zio.kafka.consumer._

import scala.jdk.CollectionConverters._

val config: Map[String, AnyRef] = Map(
  ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG  -> "localhost:9092",
  ConsumerConfig.GROUP_ID_CONFIG           -> "my-group",
  ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> "false"
)

val consumerWithJavaAccess: ZIO[Scope, Throwable, (Consumer, Task[Set[String]])] =
  for {
    javaConsumer <- ZIO.fromAutoCloseable(
                      ZIO.attempt(
                        new KafkaConsumer[Array[Byte], Array[Byte]](
                          config.asJava,
                          new ByteArrayDeserializer(),
                          new ByteArrayDeserializer()
                        )
                      )
                    )
    access   <- Semaphore.make(1)
    settings  = ConsumerSettings(List("localhost:9092")).withProperties(config)
    consumer <- Consumer.fromJavaConsumerWithPermit(javaConsumer, settings, access)
    // Every direct use of the Java consumer holds the permit:
    listTopicNames = access.withPermit(ZIO.attemptBlocking(javaConsumer.listTopics().keySet().asScala.toSet))
  } yield (consumer, listTopicNames)
```

Because finalizers run in reverse order, the scope closes the zio-kafka consumer before the Java consumer.

## Consumer Settings

`ConsumerSettings` is an immutable case class holding the Kafka client properties plus zio-kafka's own settings. Build it with `ConsumerSettings(bootstrapServers)` followed by `with*` methods; the Scaladoc recommends this style to stay source compatible with future releases:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.consumer.Consumer.{ AutoOffsetStrategy, OffsetRetrieval }

val settings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("orders-service")
    .withClientId("orders-service-1")
    .withOffsetRetrieval(OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
    .withRebalanceSafeCommits(true)
    .withMaxPollInterval(10.minutes)
    .withProperty("fetch.min.bytes", "1024")
```

Any Kafka consumer property can be set with `ConsumerSettings#withProperty` or `ConsumerSettings#withProperties`; the common ones, such as `group.id`, `client.id`, `max.poll.records` and `isolation.level`, also have their own `with*` method. A group id is needed for `Topics` and `Pattern` subscriptions and for committing to Kafka; a `Manual` subscription works without it. The other settings control zio-kafka itself, for example the poll and commit timeouts, the offset retrieval strategy (by default `OffsetRetrieval.Auto(AutoOffsetStrategy.Latest)`, so a partition without a committed offset starts at its end), rebalance-safe commits, pre-fetching, metrics and diagnostics. For every setting and its default, see the [`ConsumerSettings` API docs](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/consumer/ConsumerSettings.html).

Related pages explain when to change these settings: [consumer tuning](consumer-tuning.md) for poll, pre-fetch and slow-consumer settings, [partition assignment and offset retrieval](partition-assignment-and-offset-retrieval.md) for `OffsetRetrieval`, [preventing duplicates](preventing-duplicates.md) for rebalance-safe commits, [detecting authorization failures](detecting-authorization-failures.md) for the authorization settings, and [metrics](metrics.md) for the metrics settings.

:::note
When rebalance-safe commits are enabled and you do not set `partition.assignment.strategy` yourself, zio-kafka configures Kafka's `RangeAssignor`, which revokes all partitions during a rebalance.
:::

### Settings Validation

`Consumer.make` checks the settings before it connects. It fails with an `IllegalArgumentException` listing every problem when:

- `enable.auto.commit` is set to `true` (zio-kafka pre-fetches records, so auto commit could commit records you have not processed; zio-kafka always disables it).
- `commitTimeout` is not larger than twice `pollTimeout`.
- The settings contain the deprecated `OffsetRetrieval.Manual` directly instead of `OffsetRetrieval.External`. `ConsumerSettings#withOffsetRetrieval` converts `Manual` to `External` for you.

## Consuming Records

Every way to consume takes a `Subscription` and a key and a value `Deserializer`. A subscription names topics (`Subscription.topics("a", "b")`), a topic pattern (`Subscription.pattern("orders-.*".r)`), or a fixed set of partitions (`Subscription.manual("a" -> 0)`); [partition assignment and offset retrieval](partition-assignment-and-offset-retrieval.md) covers them in detail. Deserializers are described in [serialization and deserialization](serialization-and-deserialization.md).

All streams emit records of one partition in order, and the records fetched for a partition in one poll land in the same chunk. Use chunk-preserving operators to keep processing fast, as explained in [avoiding chunk breakers](avoiding-chunk-breakers.md).

### Plain Stream

`Consumer#plainStream` emits the records of all assigned partitions in a single stream. The order within a partition is preserved; how partitions interleave is not deterministic. Internally it merges the partition streams, buffering up to `bufferSize` chunks:

```scala
trait Consumer {
  def plainStream[R, K, V](
    subscription: Subscription,
    keyDeserializer: Deserializer[R, K],
    valueDeserializer: Deserializer[R, V],
    bufferSize: Int = 4
  ): ZStream[R, Throwable, CommittableRecord[K, V]]
}
```

The [Usage](#usage) section shows a complete plain stream.

### Partitioned Streams

`Consumer#partitionedStream` emits one inner stream per assigned partition, paired with its `TopicPartition`. Processing each partition stream in its own fiber gives parallelism across partitions while keeping each partition sequential. `Consumer#partitionedAssignmentStream` emits the same partition streams, but grouped in one `Chunk` per rebalance, so you can react to the set of newly assigned partitions as a whole:

```scala
trait Consumer {
  def partitionedStream[R, K, V](
    subscription: Subscription,
    keyDeserializer: Deserializer[R, K],
    valueDeserializer: Deserializer[R, V]
  ): Stream[Throwable, (TopicPartition, ZStream[R, Throwable, CommittableRecord[K, V]])]

  def partitionedAssignmentStream[R, K, V](
    subscription: Subscription,
    keyDeserializer: Deserializer[R, K],
    valueDeserializer: Deserializer[R, V]
  ): Stream[Throwable, Chunk[(TopicPartition, ZStream[R, Throwable, CommittableRecord[K, V]])]]
}
```

With a `Topics` or `Pattern` subscription the outer stream emits again on every rebalance: new partition streams start, and the streams of revoked partitions complete. With a `Manual` subscription there are no rebalances. To process every partition in parallel and commit from a single place:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.serde.Serde

def processPartitions(consumer: Consumer): Task[Unit] =
  consumer
    .partitionedStream(Subscription.topics("my-topic"), Serde.string, Serde.string)
    .flatMapPar(Int.MaxValue) { case (topicPartition, partitionStream) =>
      partitionStream
        .tap(record => ZIO.logInfo(s"$topicPartition: ${record.value}"))
        .map(_.offset)
    }
    .aggregateAsync(Consumer.collectOffsets)
    .mapZIO(_.commit)
    .runDrain
```

Use `Int.MaxValue` (or at least the number of partitions you can be assigned) as the parallelism of `flatMapPar`. A partition stream that is never pulled stops making progress, and after `maxStreamPullInterval` zio-kafka fails it and shuts down the consumer. The [consuming Kafka topics using ZIO Streams](consuming-kafka-topics-using-zio-streams.md) guide explains these patterns further.

### consumeWith

`Consumer#consumeWith` is the simplest way to process a topic. It runs your effect for each record and commits offsets for you:

```scala
trait Consumer {
  def consumeWith[R: EnvironmentTag, R1: EnvironmentTag, K, V](
    subscription: Subscription,
    keyDeserializer: Deserializer[R, K],
    valueDeserializer: Deserializer[R, V],
    commitRetryPolicy: Schedule[Any, Any, Any] = Schedule.exponential(1.second) && Schedule.recurs(3)
  )(f: ConsumerRecord[K, V] => URIO[R1, Unit]): ZIO[R & R1, Throwable, Unit]
}

object Consumer {
  def consumeWith[R: EnvironmentTag, R1: EnvironmentTag, K, V](
    settings: ConsumerSettings,
    subscription: Subscription,
    keyDeserializer: Deserializer[R, K],
    valueDeserializer: Deserializer[R, V],
    commitRetryPolicy: Schedule[Any, Any, Any] = Schedule.exponential(1.second) && Schedule.recurs(3)
  )(f: ConsumerRecord[K, V] => URIO[R1, Unit]): RIO[R & R1, Unit]
}
```

`R` is the environment of the deserializers and `R1` the environment of your effect. The effect receives Kafka's `ConsumerRecord`, not a `CommittableRecord`. Records of one partition are processed one after another, while different partitions are processed in parallel. Offsets are committed after the effect completes, batched while a commit is in progress; commits that fail with a `RetriableCommitFailedException` or `Consumer.CommitTimeout` are retried according to `commitRetryPolicy` (by default up to 3 retries with exponential backoff starting at 1 second). The returned effect only completes when it is interrupted, or fails when the consumer fails.

The effect's error type is `Nothing`, so it must handle its own failures, by retrying or by ignoring the error and skipping the record. The companion variant creates and closes its own consumer from `settings`:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.serde.Serde

val settings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092")).withGroupId("my-group")

val program: Task[Unit] =
  Consumer.consumeWith(settings, Subscription.topics("my-topic"), Serde.string, Serde.string) { record =>
    ZIO.logInfo(s"${record.key()}: ${record.value()}")
  }
```

Delivery is at least once: a record whose effect has run may not have its offset committed yet when the stream stops. See [committing with consumeWith](offsets-and-committing.md#committing-with-consumewith).

### Streams With Control

Each stream method has a `*WithControl` variant that returns a `StreamControl` instead of a bare stream. A `StreamControl` lets you stop fetching records for one subscription and let the stream finish its in-flight records, which makes a graceful shutdown possible. The `*WithControl` methods and `Consumer.runWithGracefulShutdown` are experimental: they may change or disappear in any zio-kafka release. Their signatures:

```scala
trait Consumer {
  def plainStreamWithControl[R, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V], bufferSize: Int = 4): ZIO[Scope with R, Throwable, StreamControl[R, Throwable, CommittableRecord[K, V]]]
  def partitionedStreamWithControl[R, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V]): ZIO[Scope with R, Throwable, StreamControl[R, Throwable, (TopicPartition, ZStream[R, Throwable, CommittableRecord[K, V]])]]
  def partitionedAssignmentStreamWithControl[R, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V]): ZIO[Scope, Throwable, StreamControl[R, Throwable, Chunk[(TopicPartition, ZStream[R, Throwable, CommittableRecord[K, V]])]]]
}

trait StreamControl[-R, +E, +A] {
  def stream: ZStream[R, E, A]
  def end: UIO[Unit]
}

object Consumer {
  def runWithGracefulShutdown[R1, R2, E, A](control: StreamControl[R1, E, A], shutdownTimeout: Duration)(
    withStream: ZStream[R1, E, A] => ZIO[R2 & Scope, E, Any]
  ): ZIO[R1 & R2, E, Any]
}
```

The `Scope` controls the subscription: zio-kafka unsubscribes when the scope ends. `StreamControl#end` stops fetching records for the subscription's partitions and ends the stream, but does not unsubscribe until the scope ends. Run `StreamControl#stream` at most once; running it twice divides the chunks over both runs. `StreamControl#map` transforms the stream while keeping `StreamControl#end`.

`Consumer.runWithGracefulShutdown` runs `withStream` on the controlled stream. When the surrounding fiber is interrupted, for example at application shutdown, it calls `StreamControl#end` and waits up to `shutdownTimeout` for `withStream` to finish processing and committing:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.serde.Serde

def runGracefully(consumer: Consumer): Task[Any] =
  ZIO.scoped {
    for {
      control <- consumer.plainStreamWithControl(Subscription.topics("my-topic"), Serde.string, Serde.string)
      result <- Consumer.runWithGracefulShutdown(control, shutdownTimeout = 30.seconds) { stream =>
                  stream
                    .tap(record => ZIO.logInfo(record.value))
                    .map(_.offset)
                    .aggregateAsync(Consumer.collectOffsets)
                    .mapZIO(_.commit)
                    .runDrain
                }
    } yield result
  }
```

If `withStream` does not finish within `shutdownTimeout`, zio-kafka logs an error and gives up waiting; some in-flight records may not have been processed. The [controlled shutdown](consuming-kafka-topics-using-zio-streams.md#controlled-shutdown-experimental) section of the streams guide shows how to call `StreamControl#end` yourself.

### Multiple Subscriptions on One Consumer

You can run several streams on one `Consumer`, each with its own subscription and deserializers. Zio-kafka subscribes the Kafka consumer to the union of all active subscriptions, and each stream receives only the partitions that match its own subscription. When a stream completes, its subscription is removed from the union.

All subscriptions on one consumer must be of the same kind: all `Topics`, all `Pattern`, or all `Manual`. Two `Pattern` subscriptions are combined into one regular expression. Starting a stream whose subscription is of a different kind fails that stream with `InvalidSubscriptionUnion`, which lists the subscriptions that could not be combined. When subscriptions overlap, for example `Subscription.topics("a", "b")` and `Subscription.topics("b", "c")`, the records of topic `b` are divided over both streams in a non-deterministic way.

Settings such as `max.poll.records` and the offset retrieval strategy apply to all subscriptions together. The [sharing a consumer](sharing-consumer.md) guide has a full example.

### Stopping Consumption

`Consumer#stopConsumption` stops fetching for every subscription, drains the buffered records, and ends all streams of this consumer. Commits are still processed while the streams finish, so a stream can stop the consumer and then commit the offsets of the records it has processed:

```scala
trait Consumer {
  def stopConsumption: UIO[Unit]
}
```

To end a single subscription and leave the others running, use the `StreamControl` of that subscription instead.

## Querying Metadata and Offsets

The `Consumer` forwards a set of read-only queries to the Kafka consumer, converting Java collections to Scala ones: `Consumer#assignment`, `Consumer#subscription`, `Consumer#committed`, `Consumer#beginningOffsets`, `Consumer#endOffsets`, `Consumer#offsetsForTimes`, `Consumer#position`, `Consumer#listTopics`, `Consumer#partitionsFor` and `Consumer#metrics` (the Kafka client's own metrics; for zio-kafka's metrics, see [metrics](metrics.md)). Every query that takes a `timeout` defaults to `Duration.Infinity`. The queries share the Kafka consumer with the polling loop, so each one waits until the current poll completes. `Consumer#committed` returns `None` for a partition the group has not committed, and `Consumer#offsetsForTimes` leaves out partitions that have no record at or after the given timestamp. For the signatures, see the [`Consumer` API docs](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/consumer/Consumer.html).

Except for `Consumer#position`, the queries do not need the partitions to be assigned to this consumer, so they also work for monitoring. To compute the lag of a consumer group for the partitions it has committed:

```scala
import org.apache.kafka.common.TopicPartition
import zio._
import zio.kafka.consumer._

def lag(consumer: Consumer, topic: String): Task[Map[TopicPartition, Long]] =
  for {
    partitions <- consumer.partitionsFor(topic, timeout = 10.seconds)
    tps         = partitions.map(p => new TopicPartition(p.topic(), p.partition())).toSet
    ends       <- consumer.endOffsets(tps, timeout = 10.seconds)
    committed  <- consumer.committed(tps, timeout = 10.seconds)
  } yield ends.flatMap { case (tp, end) =>
    committed.get(tp).flatten.map(c => tp -> (end - c.offset()))
  }
```

Partitions without a committed offset are left out: where a consumer starts on them depends on its offset retrieval setting (by default the end of the partition, see [Consumer Settings](#consumer-settings)).

## Committing Offsets

Records are committed through their `Offset`, not through the `Consumer`. The `Consumer` companion provides the sinks that combine offsets into an `OffsetBatch`, and the service accepts commits made elsewhere:

```scala
object Consumer {
  val collectOffsets: ZSink[Any, Nothing, Offset, Nothing, OffsetBatch]
  val collectOffsetBatches: ZSink[Any, Nothing, OffsetBatch, Nothing, OffsetBatch]
  case object CommitTimeout extends RuntimeException
}

trait Consumer {
  def registerExternalCommits(offsetBatch: OffsetBatch): Task[Unit]
}
```

`Consumer.collectOffsets` folds `Offset`s into a batch, and `Consumer.collectOffsetBatches` merges batches (`Consumer.offsetBatches` is an alias of `Consumer.collectOffsets`). `Consumer.CommitTimeout` is the error of a commit that does not complete within `commitTimeout`. `Consumer#registerExternalCommits` tells the consumer about offsets you stored outside Kafka, so that rebalance-safe commits can wait for them; a `TransactionalProducer` calls it for you. [Offsets and Committing](offsets-and-committing.md) covers all of these, and [transactions](transactions.md) covers committing inside a transaction.

## Rebalance Listener

A `RebalanceListener` runs your effects when the consumer group changes the partitions assigned to this consumer. It is a ZIO wrapper around Kafka's `ConsumerRebalanceListener`:

```scala
final case class RebalanceListener(
  onAssigned: Set[TopicPartition] => Task[Unit],
  onRevoked: Set[TopicPartition] => Task[Unit],
  onLost: Set[TopicPartition] => Task[Unit]
) {
  def ++(that: RebalanceListener): RebalanceListener
  def runOnExecutor(executor: Executor): RebalanceListener
}

object RebalanceListener {
  def apply(onAssigned: Set[TopicPartition] => Task[Unit], onRevoked: Set[TopicPartition] => Task[Unit]): RebalanceListener
  val noop: RebalanceListener
}
```

Kafka calls `onAssigned` with the partitions newly given to this consumer, `onRevoked` with partitions taken away in an orderly rebalance, and `onLost` with partitions the consumer lost without a chance to commit, for example after its session timed out. The two-argument `RebalanceListener.apply` uses `onRevoked` for lost partitions too. `RebalanceListener#++` combines two listeners so that both run, one after the other, and `RebalanceListener#runOnExecutor` runs the callbacks on the given `Executor`. Register a listener with `ConsumerSettings#withRebalanceListener`:

```scala
import org.apache.kafka.common.TopicPartition
import zio._
import zio.kafka.consumer._

val listener: RebalanceListener =
  RebalanceListener(
    onAssigned = (assigned: Set[TopicPartition]) => ZIO.logInfo(s"Assigned: ${assigned.mkString(", ")}"),
    onRevoked = (revoked: Set[TopicPartition]) => ZIO.logInfo(s"Revoked: ${revoked.mkString(", ")}"),
    onLost = (lost: Set[TopicPartition]) => ZIO.logWarning(s"Lost: ${lost.mkString(", ")}")
  )

val settings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("my-group")
    .withRebalanceListener(listener)
```

Zio-kafka runs your listener after its own rebalance handling. By the time your `onRevoked` runs, zio-kafka has told the streams of the revoked partitions to end and, with rebalance-safe commits enabled, has waited (up to `maxRebalanceDuration`) for their offsets to be committed.

:::warning
The callbacks run inside the Kafka consumer's poll, while zio-kafka holds exclusive access to the Kafka consumer. Polling for all partitions waits until the callback completes, so keep callbacks short. Do not call `Consumer` methods or commit offsets from a callback: they need that same access and cannot complete until the callback returns. A failing callback makes the poll fail, so handle errors inside the callback.
:::

## CommittableRecord

A `CommittableRecord[K, V]` is what the consumer streams emit: a deserialized Kafka `ConsumerRecord` together with what is needed to commit it. Its commit handle and consumer group metadata are private:

```scala
final case class CommittableRecord[K, V](
  record: ConsumerRecord[K, V],
  nextOffset: OffsetAndMetadata,
  // private: commit handle and consumer group metadata
) {
  def key: K
  def value: V
  def partition: Int
  def timestamp: Long
  def offset: Offset
  def deserializeWith[R, K1, V1](keyDeserializer: Deserializer[R, K1], valueDeserializer: Deserializer[R, V1])(implicit
    ev1: K <:< Array[Byte],
    ev2: V <:< Array[Byte]
  ): RIO[R, CommittableRecord[K1, V1]]
}
```

Besides `key`, `value`, `partition` and `timestamp`, use `record`, the underlying Kafka `ConsumerRecord`, for everything else, such as `record.topic()` and `record.leaderEpoch()`. `offset.offset` is the consumed offset, and `offset.commit` commits it. `nextOffset` is the offset a consumer should resume from after this record: usually the record's offset + 1, but for the last record of a partition in a poll it is the next offset Kafka reports, which is larger when transaction control records follow the record. For every member, see the [`CommittableRecord` API docs](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/consumer/CommittableRecord.html).

The headers of a record are only available through `record.headers()`; see [record headers](record-headers.md). How `offset` and `nextOffset` relate to committing is explained in [Offsets and Committing](offsets-and-committing.md).

`CommittableRecord#deserializeWith` lets you defer deserialization, for example to handle a deserialization failure per record. When both deserializers of a stream are `Serde.byteArray`, zio-kafka skips deserialization entirely, so you can deserialize inside the stream instead:

```scala
import zio._
import zio.kafka.consumer._
import zio.kafka.serde.Serde

def consumeRaw(consumer: Consumer): Task[Unit] =
  consumer
    .plainStream(Subscription.topics("my-topic"), Serde.byteArray, Serde.byteArray)
    .mapChunksZIO(_.mapZIO(_.deserializeWith(Serde.string, Serde.string)))
    .tap(record => ZIO.logInfo(s"partition ${record.partition}, offset ${record.offset.offset}: ${record.value}"))
    .map(_.offset)
    .aggregateAsync(Consumer.collectOffsets)
    .mapZIO(_.commit)
    .runDrain
```
