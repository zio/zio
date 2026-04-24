# Example of Consuming, Producing and Committing Offsets

> This example shows how to consume messages from topic `topic_a` and produce transformed messages to `topic_b`, after
which consumer offsets are committed.

This example shows how to consume messages from topic `topic_a` and produce transformed messages to `topic_b`, after
which consumer offsets are committed.

Processing is done in chunks using `mapChunksZIO` for more efficiency.

Please note: the ZIO consumer does not support automatic offset committing. As a result, it ignores the Kafka consumer
setting `enable.auto.commit=true`. Developers should manually commit offsets using the provided commit methods,
typically after processing messages or at appropriate points in their application logic.

```scala

val consumerSettings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092")).withGroupId("group")
val producerSettings: ProducerSettings =
  ProducerSettings(List("localhost:9092"))

for {
  consumer <- Consumer.make(consumerSettings)
  producer <- Producer.make(producerSettings, Serde.int, Serde.string)
  _ <-
    consumer
      .plainStream(Subscription.topics("my-input-topic"), Serde.int, Serde.long)
      .map { record =>
        val key: Int = record.record.key()
        val value: Long = record.record.value()
        val newValue: String = value.toString

        val producerRecord: ProducerRecord[Int, String] =
          new ProducerRecord("my-output-topic", key, newValue)
        (producerRecord, record.offset)
      }
      .mapChunksZIO { chunk =>
        val records = chunk.map(_._1)
        val offsetBatch = OffsetBatch(chunk.map(_._2).toSeq)

        producer.produceChunk[Any, Int, String](records) *>
          offsetBatch.commit.as(Chunk(()))
      }
      .runDrain
} yield ()
```
