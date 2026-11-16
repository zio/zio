# Transactions

> > This document describes Kafka transactions for zio-kafka version 3.0.0 and later.

## What are Kafka Transactions?

[Kafka transactions](https://www.confluent.io/blog/transactions-apache-kafka/) are different from what you may know from
relational databases. In Kafka a transaction means that your program consumes some records, then produces some other
records (to the same broker) and then by committing the _consumed_ records the transaction is committed, and the
produced records become available to other consumers.

I can imagine that went a bit fast, here is a more gentle introduction with more background:
[Kafka Transactions Explained (Twice!)](https://www.warpstream.com/blog/kafka-transactions-explained-twice).

## A warning about consuming transactional records

By default, produced records become immediately visible for consumers, even if they are produced in a not yet committed
Kafka transaction! To prevent reading transactional records before they are committed, you need to configure every
consumer with the `isolation.level` property set to `read_committed`. For example as follows:

```scala
import zio.kafka.consumer.ConsumerSettings

val bootstrapServers = List("localhost:9092")
val groupId          = "my-group"

val settings = ConsumerSettings(bootstrapServers)
  .withGroupId(groupId)
  .withReadCommitted(true) // Only read committed records
```

## Producing transactional records

In order to produce records transactionally, we need a `Consumer` and a `TransactionalProducer` that will work together
to safely commit transactions.

On this page we assume you're using [ZIO service pattern](https://zio.dev/reference/service-pattern/) and that the
`Consumer` and `TransactionalProducer` are created as a layer.

First we create the `Consumer` layer.
Note that rebalance-safe-commits must be enabled (see the background section below for more information). The consumer must also use a partition assignor that revokes all partitions during a rebalance, so the `CooperativeStickyAssignor` is not supported. When rebalance-safe-commits is enabled, zio-kafka uses the `RangeAssignor` unless you configure another assignor. `TransactionalProducer.make` fails with an `IllegalArgumentException` when rebalance-safe-commits is off or the `CooperativeStickyAssignor` is configured. It cannot detect other incremental rebalance protocols, such as a custom cooperative assignor or the `consumer` group protocol (`group.protocol=consumer`), so do not use those with a transactional producer either.
Since the default `maxRebalanceDuration` is quite high, you may also consider setting it to a lower duration, one in
which you are sure all records of a poll can be processed:

```scala
import zio._
import zio.kafka.consumer.{ Consumer, ConsumerSettings }

val bootstrapBrokers = List("localhost:9092")

val consumerLayer: ZLayer[Any, Throwable, Consumer] = ZLayer.scoped {
  val consumerSettings = ConsumerSettings(bootstrapBrokers)
    .withGroupId("somegroupid")
    .withRebalanceSafeCommits(true) // required!
    .withMaxRebalanceDuration(30.seconds)
  Consumer.make(consumerSettings)
}
```

Now we can create the `TransactionalProducer` layer.
Note that the producer connects (and must connect) to the same brokers as the consumer:

```scala
import zio._
import zio.kafka.consumer.Consumer
import zio.kafka.producer.{ ProducerSettings, TransactionalProducer, TransactionalProducerSettings }

import java.util.UUID

val bootstrapBrokers = List("localhost:9092")

val producerLayer: ZLayer[Consumer, Throwable, TransactionalProducer] =
  ZLayer.scoped {
    // See text below for a better transactionalId
    val transactionalId = UUID.randomUUID().toString
    val producerSettings = ProducerSettings(bootstrapBrokers)
    val transactionalProducerSettings = TransactionalProducerSettings(producerSettings, transactionalId)

    for {
      consumer <- ZIO.service[Consumer]
      tp       <- TransactionalProducer.make(transactionalProducerSettings, consumer)
    } yield tp
  }
```

Notice that the `producerLayer` requires a `Consumer`. The producer coordinates with the consumer to make sure
that transactions are accepted by the brokers. The consumer does this by holding up rebalances until all records
consumed before the rebalance are committed by the producer. (Again, see the background section below for more
information.)

The `transactionalId` uniquely identifies a producer and is (ideally) retained across restarts. It allows the Kafka
brokers to identify zombies, producers that are accidentally still running. Records produced by a zombie are rejected.
For a more complete description see [Kafka’s transactional.id – your first guess is probably wrong. It’s actually all about zombies](https://community.ibm.com/community/user/integration/blogs/kim-clark1/2024/06/19/kafka-transactionalid).

Ideally, the `transactionalId` is stable and is provided by the environment. For example, the first producer uses a
`transactionalId` that ends with `0`, the second producer uses a `transactionalId` that ends with `1` and so on.

With these in place we can look at the application logic of consuming and producing. In this example we use the
`plainStream` method to consume records with a `Long` value and an `Int` key:

```scala
import zio.kafka.consumer.{ Consumer, Subscription }
import zio.kafka.serde.Serde

val consumer: Consumer = ??? // the consumer from the layer above

val consumedRecordsStream = consumer
  .plainStream(Subscription.topics("my-input-topic"), Serde.int, Serde.long)
```

For each consumed records, we then create a `ProducerRecord` with a `String` value containing `Copy of <input value>`.
Note, that we also keep the offset of the consumed record so that we can commit it later:

```scala
import org.apache.kafka.clients.producer.ProducerRecord
import zio._
import zio.kafka.consumer.CommittableRecord
import zio.stream.ZStream

val consumedRecordsStream: ZStream[Any, Throwable, CommittableRecord[Int, Long]] = ??? // from the previous step

val producedRecordsStream = consumedRecordsStream
  .mapChunks { (records: Chunk[CommittableRecord[Int, Long]]) =>
    records.map { record =>
      val key: Int    = record.record.key()
      val value: Long = record.record.value()
      val newValue: String = "Copy of " + value.toString

      val producerRecord: ProducerRecord[Int, String] =
        new ProducerRecord("my-output-topic", key, newValue)
      (producerRecord, record.offset)
    }
  }
```

Typically, to optimize throughput, we want to produce records in batches. The underlying chunking structure of the
consumer stream is ideal for that because zio-kafka guarantees that each chunk in the stream contains records that
were fetched together from the broker. However, we need to be careful to retain the chunking structure. For example, we
should not use `ZStream#mapZIO` because it results in a stream where each chunk contains only a single item. Therefore, we use
`ZStream#mapChunksZIO` instead (see also [avoiding chunk-breakers](avoiding-chunk-breakers.md)).

These new records can now be produced. Let's build it up slowly:

```scala
producedRecordsStream
  .mapChunksZIO { (recordsAndOffsets: Chunk[(ProducerRecord[Int, String], Offset)]) =>
    ???
  }
```

Let's work on those question marks. We need to create a transaction.
Each zio-kafka transaction runs in a ZIO `Scope`; the transaction commits when the scope closes successfully, and is aborted when the scope closes with a failure or interruption (for example after `Transaction#abort`, as long as its failure is not caught inside the scope). We start with the scope:

```scala
ZIO.scoped {
  ??? // transaction stuff
}
```

We also want to try to complete the transaction, even when the program is shutting down.
Therefore, we add `ZIO#uninterruptible` and get:

```scala
ZIO
  .scoped {
    ??? // transaction stuff
  }
  .uninterruptible
```

Now we can fill in the 'transaction stuff': we create the transaction and use it to produce some records:

```scala
import org.apache.kafka.clients.producer.ProducerRecord
import zio._
import zio.kafka.consumer.{ Offset, OffsetBatch }
import zio.kafka.producer.TransactionalProducer
import zio.kafka.serde.Serde

val transactionalProducer: TransactionalProducer                    = ??? // the producer from the layer above
val recordsAndOffsets: Chunk[(ProducerRecord[Int, String], Offset)] = ??? // the input of `mapChunksZIO`

val transactionStuff =
  for {
    tx <- transactionalProducer.createTransaction
    _  <- {
      val (records, offsets) = recordsAndOffsets.unzip
      tx.produceChunkBatch(
        records,
        Serde.int,
        Serde.string,
        OffsetBatch(offsets)
      )
    }
    // running inside `mapChunksZIO`, we need to return a Chunk.
    // Since we're not using the result, the empty chunk will do.
  } yield Chunk.empty
```

Notice how we pass the offsets of the consumed records. When the transaction commits, these offsets are committed with it.
`Transaction#produce` and `Transaction#produceChunk` work the same way, but take an `Option[Offset]` instead of an `OffsetBatch`.

Putting it all together we get:

```scala
import org.apache.kafka.clients.producer.ProducerRecord
import zio._
import zio.kafka.consumer._
import zio.kafka.producer._
import zio.kafka.serde.Serde

import java.util.UUID

object Transactional extends ZIOAppDefault {

  private val topic = "transactional-test-topic"

  val bootstrapBrokers = List("localhost:9092")

  val consumerSettings = ZLayer.succeed {
    ConsumerSettings(bootstrapBrokers)
      .withGroupId("transactional-example-app")
      .withRebalanceSafeCommits(true) // required for transactional producing
      .withMaxRebalanceDuration(30.seconds)
  }

  val producerSettings = ZLayer.succeed {
    TransactionalProducerSettings(
      bootstrapServers = bootstrapBrokers,
      transactionalId = UUID.randomUUID().toString
    )
  }

  private val runConsumerStream: ZIO[Consumer & TransactionalProducer, Throwable, Unit] =
    for {
      consumer              <- ZIO.service[Consumer]
      transactionalProducer <- ZIO.service[TransactionalProducer]
      _                     <- ZIO.logInfo(s"Consuming messages from topic $topic...")
      _ <- consumer
        .plainStream(Subscription.topics(topic), Serde.int, Serde.long)
        .mapChunks { (records: Chunk[CommittableRecord[Int, Long]]) =>
          records.map { record =>
            val key: Int         = record.record.key()
            val value: Long      = record.record.value()
            val newValue: String = "Copy of " + value.toString

            val producerRecord: ProducerRecord[Int, String] =
              new ProducerRecord("my-output-topic", key, newValue)
            (producerRecord, record.offset)
          }
        }
        .mapChunksZIO { (recordsAndOffsets: Chunk[(ProducerRecord[Int, String], Offset)]) =>
          ZIO.scoped {
            for {
              _  <- ZIO.addFinalizer(ZIO.logInfo("Completing transaction"))
              tx <- transactionalProducer.createTransaction
              _ <- {
                val (records, offsets) = recordsAndOffsets.unzip
                tx.produceChunkBatch(
                  records,
                  Serde.int,
                  Serde.string,
                  OffsetBatch(offsets)
                )
              }
            } yield Chunk.empty
          }.uninterruptible
        }
        .runDrain
    } yield ()

  override def run: ZIO[Scope, Any, Any] =
    runConsumerStream
      .provide(
        consumerSettings,
        Consumer.live,
        producerSettings,
        TransactionalProducer.live
      )

}
```

## How a Transaction Ends

A transaction ends when its scope closes. When the scope closes successfully, zio-kafka commits the transaction, including the collected offsets. When the scope closes with a failure or an interruption, zio-kafka aborts the transaction. Committing and aborting are each retried up to 5 times; when they keep failing, the effect dies.

A `TransactionalProducer` runs one transaction at a time: `TransactionalProducer#createTransaction` waits until the scope of the previous transaction has closed. Do not keep a `Transaction` after its scope closes; producing with it then fails with `TransactionalProducer.TransactionLeaked`.

To abort a transaction on purpose, call `Transaction#abort`. It fails the effect with `TransactionalProducer.UserInitiatedAbort` (not a `Throwable`). The transaction is aborted only if this failure reaches the end of the scope: if you catch it inside the scope, the scope closes successfully and the transaction commits. So we catch it outside the scope when the abort is expected:

```scala
import org.apache.kafka.clients.producer.ProducerRecord
import zio._
import zio.kafka.producer.TransactionalProducer
import zio.kafka.producer.TransactionalProducer.UserInitiatedAbort
import zio.kafka.serde.Serde

val transactionalProducer: TransactionalProducer = ???

val abortedTransaction =
  ZIO.scoped {
    for {
      tx <- transactionalProducer.createTransaction
      _  <- tx.produce(new ProducerRecord("my-output-topic", "key", "value"), Serde.string, Serde.string, None)
      _  <- tx.abort // the record above is never visible to read_committed consumers
    } yield ()
  }.catchSome { case UserInitiatedAbort => ZIO.unit }
```

## Technical background - How does it all work?

Kafka must ensure that each record is only processed once, even though a partition can be revoked, assigned to another
consumer, all while the original consumer knows nothing about this. In this situation the original consumer will happily
continue producing records even though another consumer is doing the same thing with the same records.

Kafka does this by validating the group epoch. The group epoch is a number that gets increased after every rebalance.
When the transactional producer commits a transaction with the offsets of consumed records, it also includes the group
epoch of when the records were consumed. If the broker detects a commit with a group epoch that is not equal to the
current epoch, it will reject the commit.

In addition, to prevent an old consumer from continuing to poll into the next epoch without participating in the
rebalance, consumers polling within the wrong group epoch are 'fenced'. The broker rejects the commits of a fenced
consumer.

### How zio-kafka helps

With zio-kafka you can use ZIO scopes to define the transaction boundaries. Within the scope you can produce records one
by one, all in 1 batch or in multiple batches. Once the scope closes successfully, the records are committed together with all
collected offsets. If the scope fails, the transaction is aborted.

In addition, zio-kafka prevents failed commits. The `TransactionalProducer` informs the `Consumer` which offsets were
committed. When a rebalance starts, the consumer holds it up until all records consumed so far are committed. (This
feature is called rebalance-safe-commits.) Because of this, every transaction commits in the same group epoch as the
epoch in which the records were consumed, and the brokers can accept the transaction.

When a zio-kafka consumer misses a rebalance and continues polling, there is nothing that can be done. The broker rejects
its transactions; zio-kafka retries the commit 5 times and then the effect dies. In these cases the consumer (or perhaps
simpler, the whole program) should restart.
