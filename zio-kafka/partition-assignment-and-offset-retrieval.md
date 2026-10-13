# Partition Assignment And Offset Retrieval

> `zio-kafka` offers several ways to control which Kafka topics and partitions are assigned to your application.

# Consumer partition assignment

| Use case                                           | Method                                                  |
|----------------------------------------------------|---------------------------------------------------------|
| One or more topics, automatic partition assignment | `Subscription.topics("my_topic", "other_topic")`        |
| Topics matching a pattern                          | `Subscription.pattern("topic.*")`                       |
| Manual partition assignment                        | `Subscription.manual("my_topic" -> 1, "my_topic" -> 2)` |

The example `Subscription.manual("my_topic" -> 1, "my_topic" -> 2)` subscribes to partitions `1` and `2` of topic `my_topic`.

# Consumer starting offsets / offset retrieval

By default `zio-kafka` starts streaming a partition from the last committed offset for the active consumer group, or
else the latest offset on the partition in case no offset has yet been committed.

You can also choose to store offsets externally, outside of Kafka. This is useful when consistency between external
data and the consumer offset is required. For example, you can store the offset in a transactional database together
with the data that is derived from the record of that offset. Although it is optional, Kafka recommends you store a
`leaderEpoch` together with the offset as it prevents consuming from a broker that is unaware it is no longer a leader.
For more details about why leaderEpoch is important
see https://www.confluent.io/blog/guide-to-consumer-offsets/#part-ii-in-depth-analysis-and-insights.

| Use case                                                          | `OffsetRetrieval` method                                                              |
|-------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| Offsets in Kafka, start at latest record if no offset committed   | `Auto()`                                                                              |
| Offsets in Kafka, start at earliest record if no offset committed | `Auto(AutoOffsetStrategy.Earliest)`                                                   |
| Offsets in Kafka, fail if no offset committed                     | `Auto(AutoOffsetStrategy.None)`                                                       |
| External offset storage                                           | `External(getOffsets: Set[TopicPartition] => Task[Map[TopicPartition, OffsetEpoch]])` |

For external offset retrieval, the `getOffsets` function is called for each topic-partition that is assigned to the
consumer, either via Kafka's rebalancing or via a manual assignment.

Offset retrieval is configured via `ConsumerSettings.withOffsetRetrieval()`. You can find more details in the scaladocs
of that method.

:::caution
To prevent off-by-one bugs you should clearly decide to store either the consumed offset, or the next-offset. Read more
about this on
the [preventing duplicates](preventing-duplicates.md#be-clear-about-what-offset-you-persist-to-prevent-off-by-one-bugs)
page.
:::
