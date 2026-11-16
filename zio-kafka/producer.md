# Producer

> `Producer` is the **zio-kafka client for writing records to Kafka**. It wraps the Java `KafkaProducer` in a ZIO-friendly API: every produce call is an effect, serialization uses zio-kafka's effectful [`Serializer`](serialization-and-deserialization.md), and the producer's lifetime is tied to a `Scope` so it is always closed. The fundamental operations are `Producer#produce`, `Producer#produceAsync` and their chunk-based variants `Producer#produceChunk` and `Producer#produceChunkAsync`.

`Producer`:

- Two-Layer Async — the async variants separate "accepted by the producer" from "acknowledged by the broker", so you can send many records and await all acknowledgements at once.
- Batch-Oriented — the producer handles records in chunks internally; a single record is a chunk of one.
- Backpressured — records wait in a bounded send queue, so a fast caller slows down instead of exhausting memory.
- Shareable — one `Producer` can serve many fibers.

The trait has the following shape (bodies and overloads omitted; see the sections below for every overload):

```scala
trait Producer {
  def produce[R, K, V](record: ProducerRecord[K, V], keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, RecordMetadata]
  def produceAsync[R, K, V](record: ProducerRecord[K, V], keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, Task[RecordMetadata]]
  def produceChunk[R, K, V](records: Chunk[ProducerRecord[K, V]], keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, Chunk[RecordMetadata]]
  def produceChunkAsync[R, K, V](records: Chunk[ProducerRecord[K, V]], keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, Task[Chunk[RecordMetadata]]]
  def produceChunkAsyncWithFailures(records: Chunk[ProducerRecord[Array[Byte], Array[Byte]]]): UIO[UIO[Chunk[Either[Throwable, RecordMetadata]]]]
  final def produceAll[R, K, V](keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): ZPipeline[R, Throwable, ProducerRecord[K, V], RecordMetadata]

  def partitionsFor(topic: String): Task[Chunk[PartitionInfo]]
  def flush: Task[Unit]
  def metrics: Task[Map[MetricName, Metric]]
}
```

`ProducerRecord`, `RecordMetadata`, `PartitionInfo`, `MetricName` and `Metric` are the Kafka client's own classes (`org.apache.kafka.clients.producer` and `org.apache.kafka.common`).

## Usage

To produce a record, we build `ProducerSettings`, provide them to the `Producer.live` layer, and call `Producer#produce` with a serializer for the key and one for the value:

```scala
import zio._
import zio.kafka.producer._
import zio.kafka.serde._

object ProducerApp extends ZIOAppDefault {
  val bootstrapServers = List("localhost:9092")

  val program: ZIO[Producer, Throwable, Unit] =
    for {
      producer <- ZIO.service[Producer]
      metadata <- producer.produce("orders", "order-1", "created", Serde.string, Serde.string)
      _        <- ZIO.logInfo(s"Written to partition ${metadata.partition()} at offset ${metadata.offset()}")
    } yield ()

  override def run =
    program.provide(
      ZLayer.succeed(ProducerSettings(bootstrapServers).withClientId("orders-producer")),
      Producer.live
    )
}
```

`Producer#produce` completes when the broker acknowledges the record. For higher throughput, see [Choosing a Produce Method](#choosing-a-produce-method).

## Creating a Producer

A producer holds network connections and a background fiber, so every constructor returns it inside a `Scope` (or a layer, which manages the scope for you). When the scope closes, the producer stops its background fibers. `Producer.make` and `Producer.live` also close the underlying Java producer; with `Producer.fromJavaProducer` you close it yourself.

### Producer.make

`Producer.make` creates a scoped producer from `ProducerSettings`. It validates the SSL endpoint configuration, creates the Java `KafkaProducer` on the blocking thread pool, and closes it with `ProducerSettings#closeTimeout` when the scope ends:

```scala
object Producer {
  def make(settings: ProducerSettings): ZIO[Scope, Throwable, Producer]
}
```

To use the producer inside a single scoped workflow:

```scala
import zio._
import zio.kafka.producer._
import zio.kafka.serde._

val bootstrapServers = List("localhost:9092")

val program: Task[Unit] =
  ZIO.scoped {
    for {
      producer <- Producer.make(ProducerSettings(bootstrapServers))
      _        <- producer.produce("events", "key", "value", Serde.string, Serde.string)
    } yield ()
  }
```

### Producer.live

`Producer.live` is a layer that reads `ProducerSettings` from the environment and calls `Producer.make`:

```scala
object Producer {
  val live: RLayer[ProducerSettings, Producer]
}
```

To build the layer and share one producer across the application:

```scala
import zio._
import zio.kafka.producer._

val bootstrapServers = List("localhost:9092")

val producerLayer: TaskLayer[Producer] =
  ZLayer.succeed(ProducerSettings(bootstrapServers)) >>> Producer.live
```

### Producer.fromJavaProducer

`Producer.fromJavaProducer` wraps a Java producer that you create yourself, for example to configure it in a way `ProducerSettings` does not cover. The Java producer must use byte-array keys and values, because zio-kafka serializes records itself:

```scala
object Producer {
  def fromJavaProducer(
    javaProducer: org.apache.kafka.clients.producer.Producer[Array[Byte], Array[Byte]],
    settings: ProducerSettings
  ): ZIO[Scope, Throwable, Producer]
}
```

You are responsible for creating and closing the Java producer. From `settings`, only the zio-kafka parts apply (send buffer size, auth error retry schedule, diagnostics and metrics); the Kafka properties and `ProducerSettings#closeTimeout` are not used. To create and close the Java producer in the same scope:

```scala
import zio._
import zio.kafka.producer._
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.ByteArraySerializer
import scala.jdk.CollectionConverters._

val config: Map[String, AnyRef] = Map("bootstrap.servers" -> "localhost:9092")

val producer: ZIO[Scope, Throwable, Producer] =
  for {
    javaProducer <- ZIO.acquireRelease(
                      ZIO.attemptBlocking(
                        new KafkaProducer[Array[Byte], Array[Byte]](
                          config.asJava,
                          new ByteArraySerializer(),
                          new ByteArraySerializer()
                        )
                      )
                    )(p => ZIO.attemptBlocking(p.close()).orDie)
    producer     <- Producer.fromJavaProducer(javaProducer, ProducerSettings())
  } yield producer
```

Because the scope releases resources in reverse order, the zio-kafka producer stops before the Java producer closes.

## Producing Records

The produce methods differ in two ways: whether they take one record or a chunk, and whether they wait for the broker's acknowledgement or hand it back as a separate effect. This section starts with a guide to choosing, then covers each method.

### Choosing a Produce Method

Waiting for an acknowledgement costs a network round trip. A method that waits for every record before sending the next one is easy to reason about but slow. The async variants let you send many records and wait for all their acknowledgements once, and the chunk variants put a whole batch into the send queue in one step. As a rule of thumb, use `Producer#produce` for low volumes or when each record must be confirmed before the next, `Producer#produceAsync` when records arrive one at a time and throughput matters, `Producer#produceChunk` or `Producer#produceChunkAsync` when you already have a batch (the async one gives the highest throughput, because sending one chunk overlaps with waiting for the acknowledgement of another), `Producer#produceChunkAsyncWithFailures` when you need to know which records of a chunk failed, and `Producer#produceAll` to produce every element of a `ZStream`.

For streaming applications that also commit consumer offsets, [Consuming, Producing and Committing Offsets](consuming-producing-and-committing-offsets.md) shows how to combine `Producer#produceChunkAsync` with `ZStream#buffer` to keep the producer busy while acknowledgements arrive.

### Byte Arrays or Serializers

Every produce method except `Producer#produceChunkAsyncWithFailures` and `Producer#produceAll` comes in two forms. One takes `ProducerRecord[Array[Byte], Array[Byte]]` values that are already serialized. The other takes a `ProducerRecord[K, V]` plus a key `Serializer[R, K]` and a value `Serializer[R, V]`, and serializes the record before sending it; serialization runs as part of the effect and can use the environment `R`. The single-record methods also accept `topic`, `key` and `value` directly instead of a `ProducerRecord`:

```scala
trait Producer {
  def produce(record: ProducerRecord[Array[Byte], Array[Byte]]): Task[RecordMetadata]
  def produce[R, K, V](record: ProducerRecord[K, V], keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, RecordMetadata]
  def produce[R, K, V](topic: String, key: K, value: V, keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, RecordMetadata]
}
```

Use a full `ProducerRecord` when you need to set the partition, the timestamp or [record headers](record-headers.md). The serializers receive the record's headers and may read or add to them. See [Serialization and Deserialization](serialization-and-deserialization.md) for the predefined serdes such as `Serde.string` and `Serde.long`.

:::note
Because `ProducerRecord` is a Java class, give its type parameters explicitly when the compiler cannot infer them, for example `new ProducerRecord[Array[Byte], Array[Byte]](topic, key, value)`. Build a new `ProducerRecord` for every send instead of reusing one: the Kafka client marks the headers of a sent byte-array record read-only, and a serializer may add headers to the record you pass in.
:::

### Producing One Record and Waiting

`Producer#produce` puts one record in the send queue and completes with its `RecordMetadata` (topic, partition, offset and timestamp) after the broker acknowledges it. It has the three overloads shown in [Byte Arrays or Serializers](#byte-arrays-or-serializers). To produce a record with a header and an explicit partition:

```scala
import zio._
import zio.kafka.producer._
import zio.kafka.serde._
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

def produceOrder(producer: Producer): Task[RecordMetadata] = {
  val record = new ProducerRecord[String, String]("orders", 0, "order-1", "created")
  record.headers().add("source", "web-shop".getBytes("UTF-8"))
  producer.produce(record, Serde.string, Serde.string)
}
```

### Producing Without Waiting

`Producer#produceAsync` returns an effect with two layers. The outer effect completes when the record is in the producer's send queue; it waits while the queue is full. Its result, the inner `Task`, completes when the broker acknowledges the record:

```scala
trait Producer {
  def produceAsync(record: ProducerRecord[Array[Byte], Array[Byte]]): Task[Task[RecordMetadata]]
  def produceAsync[R, K, V](record: ProducerRecord[K, V], keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, Task[RecordMetadata]]
  def produceAsync[R, K, V](topic: String, key: K, value: V, keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, Task[RecordMetadata]]
}
```

Running only the outer effect for many records and then awaiting all inner tasks lets the Kafka client batch the records into fewer requests. To enqueue 100 records and then wait for all acknowledgements:

```scala
import zio._
import zio.kafka.producer._
import zio.kafka.serde._
import org.apache.kafka.clients.producer.RecordMetadata

def produceMany(producer: Producer): Task[Chunk[RecordMetadata]] =
  for {
    acks     <- ZIO.foreach(Chunk.fromIterable(1 to 100)) { i =>
                  producer.produceAsync("events", i.toString, s"event $i", Serde.string, Serde.string)
                }
    metadata <- ZIO.collectAll(acks)
  } yield metadata
```

Every `Producer#produceAsync` call is one entry in the send queue, whose capacity is `ProducerSettings#sendBufferSize` (4096 by default). If you produce many records one by one, consider raising it, for example to `10240`, or switch to the chunk variants.

### Producing a Chunk

`Producer#produceChunk` sends a `Chunk` of records as one batch and completes with their metadata, in the same order as the input, after the broker acknowledges all of them:

```scala
trait Producer {
  def produceChunk(records: Chunk[ProducerRecord[Array[Byte], Array[Byte]]]): Task[Chunk[RecordMetadata]]
  def produceChunk[R, K, V](records: Chunk[ProducerRecord[K, V]], keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, Chunk[RecordMetadata]]
}
```

To produce three records in one call:

```scala
import zio._
import zio.kafka.producer._
import zio.kafka.serde._
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

def produceBatch(producer: Producer): Task[Chunk[RecordMetadata]] = {
  val records = Chunk(
    new ProducerRecord("orders", "order-1", "created"),
    new ProducerRecord("orders", "order-2", "created"),
    new ProducerRecord("orders", "order-1", "paid")
  )
  producer.produceChunk(records, Serde.string, Serde.string)
}
```

If any record fails, the whole effect fails, even though other records of the chunk may already be written. See [Error Handling](#error-handling).

### Producing a Chunk Without Waiting

`Producer#produceChunkAsync` is the two-layer form of `Producer#produceChunk`. The outer effect completes when the chunk is in the send queue, and the inner task completes when the broker acknowledges every record of the chunk:

```scala
trait Producer {
  def produceChunkAsync(records: Chunk[ProducerRecord[Array[Byte], Array[Byte]]]): Task[Task[Chunk[RecordMetadata]]]
  def produceChunkAsync[R, K, V](records: Chunk[ProducerRecord[K, V]], keySerializer: Serializer[R, K], valueSerializer: Serializer[R, V]): RIO[R, Task[Chunk[RecordMetadata]]]
}
```

This variant gives the highest throughput: while one chunk waits for acknowledgements, the next chunk is already on its way. To enqueue two chunks before awaiting either:

```scala
import zio._
import zio.kafka.producer._
import zio.kafka.serde._
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

def produceTwoBatches(
  producer: Producer,
  first: Chunk[ProducerRecord[String, String]],
  second: Chunk[ProducerRecord[String, String]]
): Task[Chunk[RecordMetadata]] =
  for {
    ackFirst  <- producer.produceChunkAsync(first, Serde.string, Serde.string)
    ackSecond <- producer.produceChunkAsync(second, Serde.string, Serde.string)
    metadata1 <- ackFirst
    metadata2 <- ackSecond
  } yield metadata1 ++ metadata2
```

### Per-Record Results

`Producer#produceChunkAsyncWithFailures` reports a result for each record instead of failing the whole chunk. It never fails: the inner effect completes with one `Either[Throwable, RecordMetadata]` per input record, in input order:

```scala
trait Producer {
  def produceChunkAsyncWithFailures(records: Chunk[ProducerRecord[Array[Byte], Array[Byte]]]): UIO[UIO[Chunk[Either[Throwable, RecordMetadata]]]]
}

object Producer {
  case object PublishOmittedException
      extends RuntimeException("Publish omitted due to a publish error for a previous record in the chunk")
      with NoStackTrace
}
```

When a record fails while the chunk is still being handed to the Java producer, the producer does not attempt the remaining records of the chunk; their result is `Left(Producer.PublishOmittedException)`. Records handed over before the failure was reported are still sent and get their own result. This variant accepts only byte-array records, because a failing serializer cannot be reported per record. Serialize the records first, for example with `Serializer#serialize`. To log which records of a chunk failed:

```scala
import zio._
import zio.kafka.producer._
import org.apache.kafka.clients.producer.ProducerRecord

def produceAndReport(producer: Producer, records: Chunk[ProducerRecord[Array[Byte], Array[Byte]]]): UIO[Unit] =
  for {
    await   <- producer.produceChunkAsyncWithFailures(records)
    results <- await
    _       <- ZIO.foreachDiscard(results.zipWithIndex) {
                 case (Right(_), _)                                  => ZIO.unit
                 case (Left(Producer.PublishOmittedException), index) => ZIO.logWarning(s"Record $index was not sent")
                 case (Left(error), index)                           => ZIO.logError(s"Record $index failed: $error")
               }
  } yield ()
```

### Producing a Stream

`Producer#produceAll` is a `ZPipeline` that produces every record of a stream and emits its `RecordMetadata`. It calls `Producer#produceChunk` once per chunk of the stream and waits for the acknowledgements before pulling the next chunk, so the stream's chunk size is the batch size:

```scala
trait Producer {
  final def produceAll[R, K, V](
    keySerializer: Serializer[R, K],
    valueSerializer: Serializer[R, V]
  ): ZPipeline[R, Throwable, ProducerRecord[K, V], RecordMetadata]
}
```

To produce a stream of 1000 records:

```scala
import zio._
import zio.stream._
import zio.kafka.producer._
import zio.kafka.serde._
import org.apache.kafka.clients.producer.ProducerRecord

def produceStream(producer: Producer): Task[Unit] =
  ZStream
    .fromIterable(1 to 1000)
    .map(i => new ProducerRecord("events", i.toString, s"event $i"))
    .via(producer.produceAll(Serde.string, Serde.string))
    .runDrain
```

Operators such as `ZStream#mapZIO` and `ZStream#tap` turn a stream into chunks of one record, which makes `Producer#produceAll` send one record per request. See [Avoiding Chunk Breakers](avoiding-chunk-breakers.md).

## Error Handling

A produce effect can fail at two moments. With the serializer overloads, serialization runs before anything is queued, so a serializer failure fails the outer effect and nothing of that call is sent. Send failures reported by Kafka fail the effect that waits for the acknowledgement: the inner task of the async variants, or the single effect of `Producer#produce` and `Producer#produceChunk`.

For chunks, `Producer#produceChunk`, `Producer#produceChunkAsync` and `Producer#produceAll` fail with the first error of the chunk. The other records of that chunk may or may not be written: records sent before the failure is noticed can succeed, and the remaining ones are not attempted. When you need to know which records were written, use `Producer#produceChunkAsyncWithFailures`.

The Java producer already retries transient errors according to Kafka's `retries` and `delivery.timeout.ms` properties. On top of that, zio-kafka can retry records that fail with an `AuthorizationException` or `AuthenticationException`, which some brokers report while access rights are still being set up. The retry is off by default (`Schedule.stop`); enable it with `ProducerSettings#withAuthErrorRetrySchedule`:

```scala
import zio._
import zio.kafka.producer._

val bootstrapServers = List("localhost:9092")

val settings: ProducerSettings =
  ProducerSettings(bootstrapServers)
    .withAuthErrorRetrySchedule(Schedule.recurs(5) && Schedule.spaced(500.millis))
```

For a chunk, only the failed and omitted records are sent again, so a retry can change the order in which records are written. For detecting revoked read access on the consumer side, see [Detecting Authorization Failures](detecting-authorization-failures.md).

## Other Operations

Besides producing, `Producer` exposes three operations that pass through to the Java producer. Each runs on the blocking thread pool:

```scala
trait Producer {
  def partitionsFor(topic: String): Task[Chunk[PartitionInfo]]
  def flush: Task[Unit]
  def metrics: Task[Map[MetricName, Metric]]
}
```

`Producer#partitionsFor` returns the partition metadata of a topic, which is useful for choosing a partition yourself. `Producer#flush` blocks until every record in the Java producer's buffer has been sent. `Producer#metrics` returns the Java client's metrics; zio-kafka's own producer metrics are covered in [Metrics](metrics.md). To count the partitions of a topic:

```scala
import zio._
import zio.kafka.producer._

def partitionCount(producer: Producer, topic: String): Task[Int] =
  producer.partitionsFor(topic).map(_.size)
```

## ProducerSettings

`ProducerSettings` is an immutable case class holding the Kafka client properties plus zio-kafka's own options. Build it with `ProducerSettings.apply(bootstrapServers)` and refine it with the `with*` methods; this stays source compatible when new fields are added. Any Kafka producer property can be set with `ProducerSettings#withProperty` or `ProducerSettings#withProperties`, and the common ones, such as `client.id`, `linger.ms`, `compression.type` and `enable.idempotence`, also have their own `with*` method. The zio-kafka options include the close timeout, the size of the send queue (`ProducerSettings#withSendBufferSize`, counted in chunks, one per produce call, not in records), the retry schedule for authorization errors (see [Error Handling](#error-handling)), diagnostics and metrics. For every setting and its default, see the [`ProducerSettings` API docs](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/producer/ProducerSettings.html).

`ProducerSettings#driverSettings` returns the properties passed to the Java producer. To see which Kafka properties the `with*` methods set:

```scala
import zio._
import zio.kafka.producer._

val exampleSettings: ProducerSettings =
  ProducerSettings(List("localhost:9092"))
    .withClientId("orders-producer")
    .withLinger(20.millis)
    .withCompression(ProducerCompression.Zstd(3))
```

The resulting driver properties are:

```scala
exampleSettings.driverSettings
// res12: Map[String, AnyRef] = Map(
//   "compression.type" -> "zstd",
//   "bootstrap.servers" -> "localhost:9092",
//   "client.id" -> "orders-producer",
//   "compression.zstd.level" -> 3,
//   "linger.ms" -> "20"
// )
```

Higher `linger.ms` values give larger batches, which also improves compression, at the cost of latency. With idempotence enabled (Kafka's default), the broker discards duplicates caused by the client's own retries. For transactional producing, wrap the settings in `TransactionalProducerSettings`, see [Transactions](transactions.md).

### Compression

`ProducerCompression` selects the codec for `ProducerSettings#withCompression`. Kafka compresses whole batches, so better batching (see `ProducerSettings#withLinger`) gives a better compression ratio. The codecs are `NoCompression`, `Gzip`, `Snappy`, `Lz4` and `Zstd`; `Gzip`, `Lz4` and `Zstd` accept a compression level as a constructor argument. For the valid levels and their defaults, see the [`ProducerCompression` API docs](https://javadoc.io/doc/dev.zio/zio-kafka_2.13/latest/zio/kafka/producer/ProducerCompression$.html).

To compress with LZ4 at its default level:

```scala
import zio.kafka.producer._

val bootstrapServers = List("localhost:9092")

val settings: ProducerSettings =
  ProducerSettings(bootstrapServers).withCompression(ProducerCompression.Lz4())
```

### Diagnostics and Metrics

`ProducerSettings#withDiagnostics` registers a callback for `ProducerEvent`s: `ProducerEvent.RecordsOffered` just before a batch is offered to the send queue, `ProducerEvent.RecordsSent` when the broker has handled it (with the indices of failed records), and `ProducerEvent.ProducerFinalized` when the producer closes. Events of the same batch share a `batchId`. The callback runs in a separate fiber, and unhandled events queue up in memory, so it must keep up. See [Diagnostics](diagnostics.md) for the full event model.

The producer records zio-kafka metrics such as produce latency, batch size and send queue size through ZIO metrics. When an application runs several producers, label their metrics with `ProducerSettings#withMetricsLabels`. To send metrics elsewhere, or to turn them off with `ProducerMetricsObserver.NoOp`, use `ProducerSettings#withMetricsObserver`; labels are then ignored. To label a producer's metrics:

```scala
import zio.kafka.producer._
import zio.metrics.MetricLabel

val bootstrapServers = List("localhost:9092")

val settings: ProducerSettings =
  ProducerSettings(bootstrapServers).withMetricsLabels(Set(MetricLabel("producer-id", "orders")))
```

See [Metrics](metrics.md) for the list of metrics.

## Integration

These pages build on `Producer`:

- [Record Headers](record-headers.md) explains how to attach metadata to produced records.
- [Transactions](transactions.md) covers `TransactionalProducer`, which produces records and commits consumer offsets atomically.
- [Consumer](consumer.md) and [Offsets and Committing](offsets-and-committing.md) cover the reading side of consume-transform-produce applications.
- [Writing Tests](writing-tests.md) shows how to create a producer against an embedded Kafka broker with the testkit.
