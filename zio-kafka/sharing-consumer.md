# Sharing a Consumer between multiple streams

> Often in a single application, you want to consume from multiple Kafka topics and process each topic in a distinct way. With `zio-kafka` you can use a single `Consumer` instance for multiple streams from different topics. It is not only easier to create a single `Consumer` layer instead of one for each topic, but it may be more resource efficient as well. The underlying Apache Kafka consumer, its thread pool and communication with the Kafka brokers will be shared, resulting in less resource consumption compared to when you create a Consumer instance for every topic.

Often in a single application, you want to consume from multiple Kafka topics and process each topic in a distinct way. With `zio-kafka` you can use a single `Consumer` instance for multiple streams from different topics. It is not only easier to create a single `Consumer` layer instead of one for each topic, but it may be more resource efficient as well. The underlying Apache Kafka consumer, its thread pool and communication with the Kafka brokers will be shared, resulting in less resource consumption compared to when you create a Consumer instance for every topic.

For each of the topics/patterns you subscribe to, you can define a dedicated stream to process the records, a dedicated `Deserializer` for the keys, and a dedicated `Deserializer` for the values. Other settings like poll interval and offset strategy are common to all subscriptions. For example, the value of the `max.poll.records` setting is the maximum number of records returned in each poll for all the subscriptions combined. If you need different settings for each topic/pattern, you need to create a `Consumer` instance per topic/pattern.

```scala

// Create a single Consumer instance
val consumerSettings: ConsumerSettings = ConsumerSettings(List("localhost:9092")).withGroupId("group")
val consumer: ZLayer[Any, Throwable, Consumer] = ZLayer.scoped(Consumer.make(consumerSettings))

// Define two streams, each for a different topic and key-value types
val stream1 = ZIO.serviceWithZIO[Consumer] { consumer =>
  consumer.plainStream(Subscription.topics("topic1"), Serde.string, Serde.string)
    .tap(cr => printLine(s"For topic1, got key: ${cr.record.key}, value: ${cr.record.value}"))
    .map(_.offset)
    .aggregateAsync(Consumer.offsetBatches)
    .mapZIO(_.commit)
    .runDrain
}

val stream2 = ZIO.serviceWithZIO[Consumer] { consumer =>
  consumer.plainStream(Subscription.topics("topic2"), Serde.uuid, Serde.int)
    .tap(cr => printLine(s"For topic2, got key: ${cr.record.key}, value: ${cr.record.value}"))
    .map(_.offset)
    .aggregateAsync(Consumer.offsetBatches)
    .mapZIO(_.commit)
    .runDrain
}

// Run both streams with the same consumer instance
(stream1 zipPar stream2).provideSomeLayer(consumer)
```

Each Stream can have a different lifetime. Subscriptions are tied to their Stream's scope, so when a Stream completes (successfully, via failure or interruption), the corresponding subscription is removed. 

Consumer sharing is only possible when using the same type of `Subscription`: `Topics`, `Pattern` or `Manual`. Mixing different types will result in a failed stream. Note that every `Topic` subscription can also be written as a `Pattern` subscription, so if you want to mix `Topic` and `Pattern` subscriptions, you can just use `Pattern`. 

If your subscriptions overlap, e.g. `Subscription.topics("topic1", "topic2")` and `Subscription.topics("topic2", "topic3")`, which stream will get the partitions of `topic2` is not deterministic.

:::note
Upon starting or ending subscriptions, Kafka will also reassign all partitions of the _other_ subscriptions. Record fetching will resume from the last committed offset, so records may be processed twice when not all offsets are committed yet. This works the same as for regular rebalancing. To minimize this issue, commit frequently or enable [rebalance safe commits](preventing-duplicates.md).
:::
