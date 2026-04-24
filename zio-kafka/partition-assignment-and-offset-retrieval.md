# Partition Assignment And Offset Retrieval

> `zio-kafka` offers several ways to control which Kafka topics and partitions are assigned to your application.

`zio-kafka` offers several ways to control which Kafka topics and partitions are assigned to your application.

| Use case                                           | Method                                                  |
|----------------------------------------------------|---------------------------------------------------------|
| One or more topics, automatic partition assignment | `Subscription.topics("my_topic", "other_topic")`        |
| Topics matching a pattern                          | `Subscription.pattern("topic.*")`                       |
| Manual partition assignment                        | `Subscription.manual("my_topic" -> 1, "my_topic" -> 2)` |

By default `zio-kafka` starts streaming a partition from the last committed offset for the consumer group, or the latest message on the topic if no offset has yet been committed. You can also choose to store offsets outside of Kafka. This can be useful in cases where consistency between data stores and consumer offset is required.

| Use case                                                           | Method                                                                       |
|--------------------------------------------------------------------|------------------------------------------------------------------------------|
| Offsets in Kafka, start at latest message if no offset committed   | `OffsetRetrieval.Auto()`                                                     |
| Offsets in Kafka, start at earliest message if no offset committed | `OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest)`                          |
| Manual/external offset storage                                     | `Manual(getOffsets: Set[TopicPartition] => Task[Map[TopicPartition, Long]])` |

For manual offset retrieval, the `getOffsets` function is called for each topic-partition that is assigned to the consumer, either via Kafka's rebalancing or via a manual assignment.
