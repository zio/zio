# Tuning the Consumer

> Zio-kafka's consumer can be tuned for:

* throughput
* latency
* memory usage
* long-running processes / slow consumers

All tuning is done via `ConsumerSettings` (see [consumer](consumer.md)):

```scala
import zio.kafka.consumer.ConsumerSettings

val bootstrapServers = List("localhost:9092")
val groupId          = "my-group"
val properties       = Map[String, AnyRef]("fetch.min.bytes" -> "1")

val settings = ConsumerSettings(bootstrapServers)
  .withGroupId(groupId)
  .withProperties(properties)
```

Read on for more details.

## Names

Two very similarly named settings are relevant. Don't mix them up:

* `pollTimeout` — how long a poll may take
* `max.poll.interval.ms` — the maximum time between polls

## Throughput and latency

The kafka client can be tuned for either high throughput or low latency, unfortunately not both.
The most important settings for tuning throughput and latency are:

* zio-kafka's `pollTimeout` — This is the maximum time to block while polling the Kafka consumer. Zio-kafka's default
  is `50ms` which is good for low latency applications. Set this higher, e.g. `500ms` for better throughput.
* kafka's [configuration `max.poll.records`](https://kafka.apache.org/documentation/#consumerconfigs_max.poll.records) — The maximum number of records a poll will return. Kafka defaults
  this to `500`. You can set this higher for more throughput, or lower for lower latency.
* zio-kafka's fetch-strategy `partitionPreFetchBufferLimit` — when the number of records in a partition queue is
  at or below this value, zio-kafka will start to pre-fetch and buffer more records from Kafka. The default value for
  this parameter is `1024`; 2 * the default `max.poll.records` of 500, rounded to the nearest power of 2.

Zio-kafka provides 2 methods that set these settings for 2 common use cases: `ConsumerSettings.tuneForHighThroughput`
and `ConsumerSettings.tuneForLowLatency`.
Note that their implementation may vary over time. You can use them as follows:

```scala
import zio.kafka.consumer.ConsumerSettings

val bootstrapServers = List("localhost:9092")

val highThroughputSettings = ConsumerSettings(bootstrapServers).tuneForHighThroughput
val lowLatencySettings = ConsumerSettings(bootstrapServers).tuneForLowLatency
```

The scaladoc of `ConsumerSettings#tuneForHighThroughput` and `ConsumerSettings#tuneForLowLatency` lists the exact values they set. Both methods replace the configured `FetchStrategy` with a `QueueSizeBasedFetchStrategy`, and so does `ConsumerSettings#withPartitionPreFetchBufferLimit`. Call them before `ConsumerSettings#withFetchStrategy`, otherwise a custom fetch strategy is silently discarded.

## Small and large records

Kafka’s performance is not very sensitive to record size. However, when records become very small (< 100 bytes) it
might be beneficial to increase `max.poll.records` and `partitionPreFetchBufferLimit`. Similarly, when records are
very large (> 100Kb), `max.poll.records` can be decreased. Also, pre-fetching can be limited by decreasing
`partitionPreFetchBufferLimit` or even disabled by using `ConsumerSettings#withoutPartitionPreFetching`.

## High number of partitions

When a lot of partitions need to be consumed, we need to take into account that heap is needed to store the records in
the partition queues. A very rough estimate for the maximum amount of heap needed is given by: `average record size` *
`number of partitions` * max(`partitionPreFetchBufferLimit`, `max.poll.records`).

The total can be tuned by changing the `partitionPreFetchBufferLimit`, `max.poll.records` settings.

Another option is to use a different `FetchStrategy`, for example the built-in
`zio.kafka.consumer.fetch.ManyPartitionsQueueSizeBasedFetchStrategy` (since zio-kafka 2.8.1), which also caps the total
queue size over all partitions:

```scala
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.consumer.fetch.ManyPartitionsQueueSizeBasedFetchStrategy

val settings = ConsumerSettings(List("localhost:9092"))
  .withFetchStrategy(
    ManyPartitionsQueueSizeBasedFetchStrategy(maxPartitionQueueSize = 1024, maxTotalQueueSize = 20480)
  )
```

You can also write a custom `FetchStrategy`, see [Writing a Custom Fetch Strategy](#writing-a-custom-fetch-strategy).

## Writing a Custom Fetch Strategy

A fetch strategy decides, before every poll, which partitions may receive records. It implements a single method:

```scala
package zio.kafka.consumer.fetch

trait FetchStrategy {
  def selectPartitionsToFetch(streams: Chunk[PartitionStream]): ZIO[Any, Nothing, Set[TopicPartition]]
}

abstract class PartitionStream {
  def tp: TopicPartition
  def queueSize: UIO[Int]
}
```

The consumer passes one `PartitionStream` for every partition stream that has not ended. `PartitionStream#tp` is the Kafka `TopicPartition` of the stream, and `PartitionStream#queueSize` is the number of records that are buffered for it and not yet pulled by your program. The consumer resumes the partitions in the returned set and pauses all other assigned partitions for the next poll.

A paused partition receives no records, even when its stream is waiting for them, so make sure every partition is selected again eventually. The built-in strategies select a partition whenever its queue size is at or below a limit, which always includes partitions with an empty queue. The method runs before every poll, so keep it fast.

The following strategy gives one topic a larger buffer than the others, so that its partitions keep more records ready:

```scala
import org.apache.kafka.common.TopicPartition
import zio._
import zio.kafka.consumer.ConsumerSettings
import zio.kafka.consumer.fetch.{ FetchStrategy, PartitionStream }

final case class PriorityTopicFetchStrategy(
  priorityTopic: String,
  priorityLimit: Int = 4096,
  defaultLimit: Int = 256
) extends FetchStrategy {
  override def selectPartitionsToFetch(streams: Chunk[PartitionStream]): ZIO[Any, Nothing, Set[TopicPartition]] =
    ZIO
      .filter(streams) { stream =>
        val limit = if (stream.tp.topic() == priorityTopic) priorityLimit else defaultLimit
        stream.queueSize.map(_ <= limit)
      }
      .map(_.map(_.tp).toSet)
}

val settings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("orders-service")
    .withFetchStrategy(PriorityTopicFetchStrategy(priorityTopic = "payments"))
```

`PartitionStream` is part of the public API since zio-kafka 4.1.0; in earlier versions, a custom fetch strategy had to depend on an internal type.

## Long-running processes / slow consumers

To detect stalled consumers, Kafka revokes a partition when a consumer does not poll within the max poll interval (see
[configuration `max.poll.interval.ms`](https://kafka.apache.org/documentation/#consumerconfigs_max.poll.interval.ms)). The default max poll interval is 5 minutes. After a partition is revoked,
it will be assigned to another consumer.

In zio-kafka a stream needs to pull data within the max stream pull interval (`ConsumerSettings#withMaxStreamPullInterval`,
since zio-kafka 2.9.0), which defaults to `max.poll.interval.ms` (in versions 2.6.0 up to 2.8.x it is always the max poll
interval). If this doesn't happen, the stream is interrupted with a failure and the whole consumer shuts down.

To prevent the above failure states, either decrease `max.poll.records` (so that time between polling gets shorter),
and/or increase `max.poll.interval.ms` (so that the time between polls may be longer).

To see if your application must be configured with a lower `max.poll.records` and/or a higher `max.poll.interval.ms`
value we need to consider the maximum duration between polls. If processing is sequential, we can obtain this maximum by
multiplying `max.poll.records` with the maximum duration to process a single record. To also accommodate things like
long garbage collections and buffering, configuration `max.poll.interval.ms` should be substantially higher than the
maximum processing time.

`max.poll.records`, `max.poll.interval.ms` and the max stream pull interval can be set with:

```scala
import zio._
import zio.kafka.consumer.ConsumerSettings

val settings = ConsumerSettings(List("localhost:9092"))
  .withMaxPollRecords(1)
  .withMaxPollInterval(15.minutes)
  .withMaxStreamPullInterval(15.minutes)
```

On older zio-kafka versions `ConsumerSettings#withMaxPollInterval` is not available. Use the following instead:

```scala
import zio._
import zio.kafka.consumer.ConsumerSettings

val settings = ConsumerSettings(List("localhost:9092"))
  .withProperty("max.poll.interval.ms", 15.minutes.toMillis.toString)
```

### Long-running processes and `rebalance-safe-commits`

When `rebalance-safe-commits` is enabled, additional settings are needed for long-running processes. See
[preventing duplicates](preventing-duplicates.md) for more details.

### Long-running processes and silent authorization failures

When topics are quiet or experience long idle periods, configuring a low threshold for silent authorization failure
detection (`emptyPollCountToMetaRefresh`) can trigger excessive offset-fetch requests.
See [detecting authorization failures](detecting-authorization-failures.md) for how to tune this setting for low-volume
topics.

## Using metrics to tune the consumer

Zio-kafka exposes [metrics](metrics.md) that can be used to further tune the consumer. To interpret these metrics you need to know how zio-kafka works internally.

![](consumer-internals.svg)

The runloop is at the heart of every zio-kafka consumer.
It creates a zstream for each partition, eventually this is the zstream your applications consumes from.
When the zstream starts, and every time the records queue is empty, it sends a request for data to the runloop.
The request makes the runloop poll. Partitions whose queue size is at or below the `FetchStrategy`'s limit are resumed, so the next poll may receive records for them.
Any received records are put in the records queue.
When the queue reaches a certain size (as determined by the configured `FetchStrategy`), the partition is paused.
Meanwhile, the zstream reads from the queue and emits the records to your application.

An optimally configured consumer has the following properties:

- the zstreams never have to wait for new records (to get high throughput),
- most of the time, the record queues are empty (to get low latency and low heap usage).

The following strategy can help you get to this state:

1. First make sure that `pollTimeout` and `max.poll.records` make sense for the latency and throughput requirements
   of your application.
2. Configure `partitionPreFetchBufferLimit` to `0` (`ConsumerSettings#withPartitionPreFetchBufferLimit(0)`).
3. Observe metric `ziokafka_consumer_queue_polls` which gives the number of polls during which records are idling in
   the queue.
4. Increase `partitionPreFetchBufferLimit` in steps until most measurements of the `ziokafka_consumer_queue_polls`
   histogram are in the lowest bucket (`le="1.0"`; zio-metrics ignores the `0` bucket boundary, so this bucket counts both 0 and 1 idle polls).

During this process, it is useful to observe metric `ziokafka_consumer_queue_size` (number of records in the queues) to
see if the queues are indeed increasing in size.

When many (hundreds of) partitions need to be consumed, the metric `ziokafka_consumer_all_queue_size` should also be
observed as increasing `partitionPreFetchBufferLimit` can lead to high heap usage. (See 'High number of partitions'
above.)
