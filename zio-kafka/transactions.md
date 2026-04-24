# Transactions

> > This document describes Kafka transactions for zio-kafka version 3.0.0 and later.

> This document describes Kafka transactions for zio-kafka version 3.0.0 and later.

## What are Kafka Transactions?

[Kafka transactions](https://www.confluent.io/blog/transactions-apache-kafka/) are different from what you may know from
relational databases. In Kafka a transaction means that your program consumes some records, then produces some other
records (to the same broker) and then by committing the _consumed_ records the transaction is committed, and the
produced records become available to other consumers.

I can image that went a bit fast, here is a more gentle introduction with more background:
[Kafka Transactions Explained (Twice!)](https://www.warpstream.com/blog/kafka-transactions-explained-twice).

## A warning about consuming transactional records

By default, produced records become immediately visible for consumers, even if they are produced in a not yet committed
Kafka transaction! To prevent reading transactional records before they are committed, you need to configure every
consumer with the `isolation.level` property set to `read_committed`. For example as follows:

```scala
val settings = ConsumerSettings(bootstrapServers)
  .withGroupId(groupId)
  .withReadCommitted(true) // Only read committed records
.... etc.
```

## Producing transactional records

In order to produce records transactionally, we need a `Consumer` and a `TransactionalProducer` that will work together
to safely commit transactions.

On this page we assume you're using [ZIO service pattern](https://zio.dev/reference/service-pattern/) and that the
`Consumer` and `TransactionalProducer` are created as a layer.

First we create the `Consumer` layer.
Note that rebalance-safe-commits must be enabled (see the background section below for more information).
Since the default `maxRebalanceDuration` is quite high, you may also consider setting it to a lower duration, one in
which you are sure all records of a poll can be processed.

```scala
val bootstrapBrokers = List("localhost:9092")

val consumerLayer: ZLayer[Any, Throwable, Consumer] = ZLayer {
  val consumerSettings = ConsumerSettings(bootstrapBrokers)
    .withGroupId("somegroupid")
    .withRebalanceSafeCommits(true) // required!
    .withMaxRebalanceDuration(30.seconds)
  Consumer.make(consumerSettings)
}
```

Now we can create the `TransactionalProducer` layer.
Note that the producer connects (and must connect) to the same brokers as the consumer.

```scala
val producerLayer: ZLayer[Consumer, Throwable, TransactionalProducer] =
  ZLayer {
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
`plainStream` method to consume records with a `Long` value and an `Int` key.

```scala
val consumedRecordsStream = consumer
  .plainStream(Subscription.topics("my-input-topic"), Serde.int, Serde.long)
```

For each consumed records, we then create a `ProducerRecord` with a `String` value containing `Copy of <input value>`.
Note, that we also keep the offset of the consumed record so that we can commit it later.

```scala
val producedRecordsStream = consumedRecordsStream
  .mapChunks { records: Chunk[CommittableRecord[Int, Long]] =>
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
should not use `.mapZIO` because it results in a stream where each chunk contains only a single item. Therefore, we use
`.mapChunksZIO` instead (see also [avoiding chunk-breakers](avoiding-chunk-breakers.md)).

These new records can now be produced. Let's build it up slowly.

```scala
producedRecordsStream
  .mapChunksZIO { recordsAndOffsets: Chunk[(ProducerRecord[Int, String], Offset)] =>
    ???
  }
```

Let's work on those question marks. We need to create a transaction.
Each zio-kafka transaction runs in a ZIO `Scope`; the transaction commits when the scope closes.

```scala
ZIO.scoped {
  ??? // transaction stuff
}
```

We also want to try to complete the transaction, even when the program is shutting down.
Therefore, we add `.uninterruptible` and get:

```scala
ZIO
  .scoped {
    ??? // transaction stuff
  }
  .uninterruptible
```

Now we can fill in the 'transaction stuff': we create the transaction and use it to produce some records.

```scala
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

Notice how we pass the offsets of the consumed records. Once the transaction completes, these are the offsets that will
be committed.

Putting it all together we get:

```scala

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
        .mapChunks { records: Chunk[CommittableRecord[Int, Long]] =>
          records.map { record =>
            val key: Int         = record.record.key()
            val value: Long      = record.record.value()
            val newValue: String = "Copy of " + value.toString

            val producerRecord: ProducerRecord[Int, String] =
              new ProducerRecord("my-output-topic", key, newValue)
            (producerRecord, record.offset)
          }
        }
        .mapChunksZIO { recordsAndOffsets: Chunk[(ProducerRecord[Int, String], Offset)] =>
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
        ZLayer.succeed(Consumer.NoDiagnostics),  // No longer needed in zio-kafka 3.x
        Consumer.live,
        producerSettings,
        TransactionalProducer.live
      )

}
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
rebalance, consumers polling within the wrong group epoch are 'fenced'. A fenced consumer dies with a
`FencedInstanceIdException`.

### How zio-kafka helps

With zio-kafka you can use ZIO scopes to define the transaction boundaries. Within the scope you can produce records one
by one, all in 1 batch or in multiple batches. Once the scope closes, all collected offsets are committed.

In addition, zio-kafka prevents failed commits. The `TransactionalProducer` informs the `Consumer` which offsets were
committed. When a rebalance starts, the consumer holds it up until all records consumed so far are committed. (This
feature is called rebalance-safe-commits.) Because of this, every transaction commits in the same group epoch as the
epoch in which the records were consumed, and the brokers can accept the transaction.

When a zio-kafka consumer misses a rebalance and continues polling, there is nothing that can be done. It will die with
a `FencedInstanceIdException`. In these cases the consumer (or perhaps simpler, the whole program) should restart.
