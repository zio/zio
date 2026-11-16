# Migrating to zio-kafka 4

> Zio-kafka 4.0.0 changes how offsets are committed. Before 4.0, zio-kafka committed the offset of the last consumed record plus 1. Since KIP-1094, the Kafka client reports the actual _next offset_ of a partition, which skips the invisible control records that transactional producers write. Zio-kafka 4.0 attaches this next offset to every record and commits it. As a result, consumer lag can now go down to 0, where before a consumer of a transactional topic always showed some lag.

This change is the only one in 4.0.0. The Scala, ZIO and Kafka client versions are the same as in zio-kafka 3.8.2, and 4.0.0 removes no deprecated APIs. Most applications only need to update the dependency. You need to change code if it:

- Reads `OffsetBatch.offsets`, for example to store offsets outside Kafka.
- Implements the `Offset` trait, for example as a fake in tests.
- Constructs a `CommittableRecord` directly, for example in tests.
- Reads the offsets of `DiagnosticEvent.Commit.Started` in a diagnostics callback.
- Assumes that a committed offset always equals the last consumed offset plus 1.

If you are still on zio-kafka 2.x, first follow [migrating to zio-kafka 3](migrating-to-zio-kafka-3.md).

## Update the Dependency

To upgrade, change the version of zio-kafka (and of the testkit, if you use it) in `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-kafka"         % "4.1.0"
libraryDependencies += "dev.zio" %% "zio-kafka-testkit" % "4.1.0" % Test
```

As with any major release, zio-kafka 4 is not binary compatible with zio-kafka 3. A library that was compiled against zio-kafka 3 and uses the APIs below needs a release built against zio-kafka 4.

## Replace `OffsetBatch.offsets` With `OffsetBatch#nextOffsets`

`OffsetBatch.offsets` is gone. Its replacement, `OffsetBatch#nextOffsets`, holds per partition the offset to commit, which is the next offset, not the consumed offset. This affects you if you combine offsets into a batch and then store them outside Kafka, as described in [preventing duplicates](preventing-duplicates.md).

In zio-kafka 3.x, the batch held consumed offsets, so code that stored offsets added 1:

```scala
// zio-kafka 3.x
def offsetsToStore(batch: OffsetBatch): Map[TopicPartition, Long] =
  batch.offsets.map { case (tp, offsetAndMetadata) => tp -> (offsetAndMetadata.offset() + 1) }
```

In zio-kafka 4, the values are already the next offsets, so we store them as they are:

```scala
import org.apache.kafka.common.TopicPartition
import zio.kafka.consumer.OffsetBatch

def offsetsToStore(batch: OffsetBatch): Map[TopicPartition, Long] =
  batch.nextOffsets.map { case (tp, offsetAndMetadata) => tp -> offsetAndMetadata.offset() }
```

:::warning
Do not only rename `OffsetBatch.offsets` to `OffsetBatch#nextOffsets`. If you keep adding 1, the code still compiles, but after a restart or rebalance each partition skips one record.
:::

The values of `OffsetBatch#nextOffsets` are `OffsetAndMetadata`s, so they also carry the leader epoch and any metadata added with `Offset#withMetadata`. Values that are not inside an `OffsetBatch`, such as `record.offset.offset`, keep their meaning: they are still consumed offsets.

## Prefer `CommittableRecord#nextOffset` When Storing Single Offsets

This step is optional; the 3.x code keeps compiling and working. Code that stores the offset of a single record by adding 1 to the consumed offset ignores the control records that follow it, so the stored offset can lag behind. Each record now carries its exact next offset in `CommittableRecord#nextOffset`, and each `Offset` in `Offset#nextOffset`.

In zio-kafka 3.x, we compute the next offset ourselves:

```scala
// zio-kafka 3.x
def offsetToStore(record: CommittableRecord[String, String]): (TopicPartition, Long) =
  record.offset.topicPartition -> (record.offset.offset + 1)
```

In zio-kafka 4, we read it from the record:

```scala
import org.apache.kafka.common.TopicPartition
import zio.kafka.consumer.CommittableRecord

def offsetToStore(record: CommittableRecord[String, String]): (TopicPartition, Long) =
  record.offset.topicPartition -> record.nextOffset.offset()
```

To also store the leader epoch, use `record.nextOffset.leaderEpoch()`. [Offsets and Committing](offsets-and-committing.md) explains the difference between the consumed offset and the next offset.

## Implement `Offset#nextOffset`

The `Offset` trait has a new abstract member, `def nextOffset: OffsetAndMetadata`, and lost its package-private `metadata` and `asJavaOffsetAndMetadata` members. A custom implementation of `Offset` must now provide `Offset#nextOffset`. To match zio-kafka's own implementation, take `Offset#leaderEpoch` from the next offset, let `Offset#commit` commit the next offset, and let `Offset#withMetadata` store the metadata in the next offset.

In zio-kafka 3.x, a fake offset for tests had no next offset:

```scala
// zio-kafka 3.x
final case class FakeOffset(topic: String, partition: Int, offset: Long) extends Offset {
  def leaderEpoch: Optional[Integer] = Optional.empty()
  // ... commit, batch, consumerGroupMetadata, withMetadata, metadata
}
```

In zio-kafka 4, it takes the next offset as an extra field:

```scala
import java.util.Optional

import org.apache.kafka.clients.consumer.{ ConsumerGroupMetadata, OffsetAndMetadata }
import zio._
import zio.kafka.consumer.{ Offset, OffsetBatch }

final case class FakeOffset(topic: String, partition: Int, offset: Long, nextOffset: OffsetAndMetadata)
    extends Offset {
  def leaderEpoch: Optional[Integer]                       = nextOffset.leaderEpoch()
  def commit: Task[Unit]                                   = ZIO.unit
  def batch: OffsetBatch                                   = OffsetBatch.empty // this fake is never committed in a batch
  def consumerGroupMetadata: Option[ConsumerGroupMetadata] = None
  def withMetadata(metadata: String): Offset =
    copy(nextOffset = new OffsetAndMetadata(nextOffset.offset(), nextOffset.leaderEpoch(), metadata))
}
```

## Pass the Next Offset When Constructing a `CommittableRecord`

`CommittableRecord` has a new second parameter, `nextOffset: OffsetAndMetadata`, both in its constructor and in `CommittableRecord.apply`. Code that builds records by hand, typically tests, must pass it, and a pattern such as `case CommittableRecord(record, _, _)` needs an extra `_`.

In zio-kafka 3.x, a test record needed only the Kafka record, a commit handle and the group metadata:

```scala
// zio-kafka 3.x
val record: CommittableRecord[String, String] =
  CommittableRecord(
    record = consumerRecord,
    commitHandle = _ => ZIO.unit,
    consumerGroupMetadata = None
  )
```

In zio-kafka 4, we also pass the next offset. For a record that is not followed by control records, it is the record's offset plus 1:

```scala
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetAndMetadata }
import org.apache.kafka.common.TopicPartition
import zio._
import zio.kafka.consumer.CommittableRecord

val consumerRecord = new ConsumerRecord[String, String]("my-topic", 0, 41L, "key", "value")

val record: CommittableRecord[String, String] =
  CommittableRecord(
    record = consumerRecord,
    nextOffset = new OffsetAndMetadata(consumerRecord.offset() + 1),
    commitHandle = (_: Map[TopicPartition, OffsetAndMetadata]) => ZIO.unit,
    consumerGroupMetadata = None
  )
```

## Expect Commits to Skip Control Records

This change needs no code changes. `Offset#commit`, `OffsetBatch#commit`, `Consumer#consumeWith` and transactions of a `TransactionalProducer` now commit the actual next offset. When control records follow the last consumed record of a partition, the committed offset is larger than that record's offset plus 1.

Review code, tests and dashboards that assume otherwise. For example, a test that checks `committed == lastConsumedOffset + 1` on a transactional topic can now fail, and a dashboard that showed a lag of 1 on such a topic now shows 0.

## Expect Next Offsets in `Commit.Started` Diagnostic Events

In zio-kafka 3.x, the `DiagnosticEvent.Commit.Started` event carried the consumed offsets, while `Commit.Success` and `Commit.Failure` carried the next offsets. In zio-kafka 4, `Commit.Started` also carries the next offsets, the same values that are committed. If a [diagnostics](diagnostics.md) callback compares or logs the offsets of `Commit.Started`, remove any `+ 1` it applies to them.

## Checklist

To migrate an application from zio-kafka 3.x to zio-kafka 4:

- [ ] Update `zio-kafka` and `zio-kafka-testkit` to the latest 4.x release (4.1.0 at the time of writing).
- [ ] Replace every use of `OffsetBatch.offsets` with `OffsetBatch#nextOffsets`, and remove any `+ 1` applied to its values.
- [ ] Optionally, replace `record.offset.offset + 1` with `record.nextOffset.offset()` where you store offsets.
- [ ] Add `Offset#nextOffset` to custom implementations of `Offset`.
- [ ] Pass the `nextOffset` parameter wherever you construct a `CommittableRecord`, and add a field to positional patterns on it.
- [ ] Remove any `+ 1` applied to the offsets of `DiagnosticEvent.Commit.Started` in diagnostics callbacks.
- [ ] Check tests and monitoring that compare committed offsets with consumed offsets.

## Upgrading From zio-kafka 4.0 to 4.1

Zio-kafka 4.1.0 fixes a number of small problems and adds a few APIs; most applications only need to update the dependency. Check the following points:

- **Producer diagnostics:** `ProducerEvent.ProducedRecord#partition` is now `ProducerEvent.NoPartition` (`-1`) for a record that leaves the partition choice to the Kafka partitioner. In 4.0 it was `0`, which could not be told apart from a record sent to partition `0`. Update [diagnostics](diagnostics.md) callbacks that group or filter by this field.
- **Custom fetch strategies:** `PartitionStream`, the type a `FetchStrategy` receives, moved from an internal package to `zio.kafka.consumer.fetch.PartitionStream`. Change the import of a custom `FetchStrategy`, see [Writing a Custom Fetch Strategy](consumer-tuning.md#writing-a-custom-fetch-strategy).
- **SSL endpoint check:** `TransactionalProducer.make` and `TransactionalProducer.live` now run the same SSL endpoint check as the other clients, so with a plain-text protocol they fail when no bootstrap server is reachable at startup. The `KafkaException` of a failed check now has the message `"Failed to connect to Kafka"` instead of `"Failed to create new KafkaAdminClient"`; update code or alerts that match on the old message. See [Security](security.md#ssl-endpoint-check).
- **Failing diagnostics:** when a `Diagnostics` implementation throws or dies, the consumer or producer now fails with that defect, instead of queueing events in memory without bound. See [Diagnostics](diagnostics.md).
- **Metrics:** `ziokafka_producer_send_queue_latency_seconds` now records the actual (positive) time a batch spends in the send queue; in 4.0 the values had the wrong sign. `ziokafka_producer_auth_errors` now also counts authentication and authorization errors of single-record produce calls. Review dashboards and alerts built on these metrics, see [Metrics](metrics.md).
- **Admin client:** `createTopic`, `createTopics`, `createPartitions` and `incrementalAlterConfigs` with `validateOnly = true` no longer wait for the change to become visible, so they return sooner. See [Admin Client](admin-client.md).

Zio-kafka 4.1.0 also adds the serdes `Serde.intOption`, `Serde.longOption`, `Serde.shortOption`, `Serde.floatOption` and `Serde.doubleOption`, which turn a `null` key or value into `None` (see [Handling Missing Data](serialization-and-deserialization.md#handling-missing-data)).
