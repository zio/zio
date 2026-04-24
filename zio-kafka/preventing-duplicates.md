# Preventing duplicates

> In zio-kafka processing of records runs asynchronously with partition management. This brings substantial performance
advantages but causes some records to be consumed and processed _twice_ when a rebalance occurs. To prevent this,
since version 2.7.1 zio-kafka supports a new mode in which we prevent duplicates due to rebalances. You can enable it
as follows:

In zio-kafka processing of records runs asynchronously with partition management. This brings substantial performance
advantages but causes some records to be consumed and processed _twice_ when a rebalance occurs. To prevent this,
since version 2.7.1 zio-kafka supports a new mode in which we prevent duplicates due to rebalances. You can enable it
as follows:

```scala

val consumerSettings: ConsumerSettings =
  ConsumerSettings(List("localhost:9092"))
    .withGroupId("group")
    .withRebalanceSafeCommits(true)       // enable rebalance-safe-commits mode
    .withMaxRebalanceDuration(30.seconds) // defaults to 3 minutes
    .withCommitTimeout(15.seconds)        // defaults to 15 seconds
```

With rebalance-safe-commits mode enabled, rebalances are held up for up to max-rebalance-duration to wait for pending
commits to be completed. Once pending commits are completed, it is safe for another consumer in the group to take over
a partition.

For this to work correctly, your program must process a chunk (of up to `max.poll.records` records) within the
configured max-rebalance-duration. The clock starts the moment the chunk is pushed into the stream and ends when the
commits for these records complete. Zio-kafka uses `commit-timeout` as the worse case time to commit. Therefore, with
the default settings, your program has 2 minutes and 45 seconds until the offsets should be committed.

It is probably clear now that your program should commit the offsets of consumed records. The most straightforward way
is to commit to the kafka brokers. This is done by calling `.commit` on the offset of consumed records (see the consumer
documentation). However, there are more options: external commits and transactional producing.

### Commit to an external system

When you commit to an external system (e.g. by writing to a relational database) the zio-kafka consumer needs to know
about those commits before it can work in rebalance-safe-commits mode. Inform zio-kafka about external commits by
invoking method `Consumer.registerExternalCommits(offsetBatch: OffsetBatch)` (available since zio-kafka 2.10.0).

Here is what this could look like:

```scala

consumer.plainStream(Subscription.topics("topic2000"), Serde.string, Serde.string)
  .mapZIO { record =>
    database.store(record.offset) *> // <-- the external commit
      consumer.registerExternalCommits(OffsetBatch(record.offset))
  }
  .runDrain
```

### Commit with a transactional producer

Transactional producing is described in [transactions](transactions.md).

## Long-running processes / slow consumers

When rebalance-safe-commits mode is enabled, consumers have tighter dead-lines. Besides a suitable `max.poll.records`
and `max.poll.interval.ms` (see [consumer tuning](consumer-tuning.md#long-running-processes--slow-consumers)), a
consumer must also be able to process `max.poll.records` within `maxRebalanceDuration`, with some margin to complete
commits.

With the default values of `500` for `max.poll.records`, 3 minutes for `maxRebalanceDuration` (which is 3/5 of 5
minutes for `max.poll.interval.ms`), and 10 seconds for `commitTimeout`, a consumer must be able to process and commit
`500` records in 2 minutes, 50 seconds (_3 records per second_). **This is a hard threshold.** Change the settings such
that there is sufficient head-room for busy services, network delays, garbage collections or reduced CPU capacity due to
other processes.

In case the consumer can handle less than 10 times the minimum records per second, it is important to use the Range
Assignor (see [PR 1576](https://github.com/zio/zio-kafka/pull/1576) for an extensive explanation).

Settings described in this section can be changed as follows:

```scala
val settings = ConsumerSettings(bootstrapServers)
  .withRebalanceSafeCommits(true)
  .withMaxPollRecords(1)
  .withMaxPollInterval(15.minutes)
  .withMaxRebalanceDuration(4.minutes)
  .withProperty(
    org.apache.kafka.clients.consumer.ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
    classOf[org.apache.kafka.clients.consumer.RangeAssignor].getName
  )
  .withCommitTimeout(10.seconds)
  .... etc.
```

## More information

There is more information in the scaladocs of `ConsumerSettings` and the description of
[pull request #1098](https://github.com/zio/zio-kafka/pull/1098) that introduced this feature.
You can also watch the presentation
[Making ZIO-Kafka Safer And Faster](https://www.youtube.com/watch?v=MJoRwEyyVxM). The relevant part starts at 10:24.
