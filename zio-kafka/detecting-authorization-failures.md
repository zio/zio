# Detecting Authorization Failures

> When a Kafka consumer's `READ` access control list (ACL) is revoked at runtime, the Apache Kafka broker stops sending
records to the consumer for the affected topic. However, the underlying Java Kafka consumer client's `poll()` method
silently returns zero records instead of throwing an error. In a standard stream setup, this causes ZIO-Kafka's consumer
stream (such as `consumeWith` or custom runloops) to run indefinitely in a silent, zombie-like state, without any
indication that authorization was lost.

When a Kafka consumer's `READ` access control list (ACL) is revoked at runtime, the Apache Kafka broker stops sending
records to the consumer for the affected topic. However, the underlying Java Kafka consumer client's `poll()` method
silently returns zero records instead of throwing an error. In a standard stream setup, this causes ZIO-Kafka's consumer
stream (such as `consumeWith` or custom runloops) to run indefinitely in a silent, zombie-like state, without any
indication that authorization was lost.

To detect these silent authorization losses, ZIO-Kafka provides an _opt-in_ metadata-refresh probe. By configuring
`ConsumerSettings.withEmptyPollCountToMetaRefresh(n)`, you instruct ZIO-Kafka to count consecutive empty polls for each
active topic. (We define 'empty poll' as a `poll()` operation resulting in zero records for a topic.) Once a topic
reaches `n` consecutive polls that return no records, the consumer issues a `committed()` request to the broker for the
assigned partitions of that topic. Unlike `poll()`, calling `committed()` forces a broker round-trip that immediately
throws a `TopicAuthorizationException` if access is denied, causing the consumer to fail.

Here is how you can configure the probe in your consumer settings:

```scala

val settings = ConsumerSettings(List("localhost:9092"))
  .withGroupId("my-group")
  .withEmptyPollCountToMetaRefresh(3) // Triggers a probe after 3 consecutive empty polls
```

### When to use this feature

You should use this feature if your Kafka cluster enforces dynamic topic-level ACLs that may be revoked at runtime, and
you want your consumer application to fail-fast, generate alerts, or restart to refresh credentials when authorization
is lost. Setting the threshold to a small value (such as `3` or `5`) ensures that your application detects the
permission loss within a few poll intervals.

For low-volume topics that are naturally quiet or experience long idle periods, setting a higher threshold for
`emptyPollCountToMetaRefresh` is prudent. If the threshold is set too low, the consumer will frequently hit the empty
poll threshold and execute `committed()` queries against the broker, creating unnecessary network traffic and metadata
request load on your Kafka cluster.

### When to avoid this feature

This feature is disabled by default (`None`) and should remain disabled if you do not use dynamic topic-level ACLs, as
keeping it turned off avoids all bookkeeping and potential broker overhead.
