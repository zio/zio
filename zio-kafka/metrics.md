# Metrics

> Zio-kafka exposes all the metrics of the wrapped Java based consumer and producer, plus some more metrics about the
> zio-kafka consumer and producer themselves.

## Java client metrics

The metrics from the Java metrics can be obtained via the `Consumer#metrics` and `Producer#metrics` methods. Both
return a live view on the internal metrics of the consumer/producer. We currently do not expose these metrics elsewhere,
a PR to copy them to the zio-metrics API is welcome.

## Zio-kafka Metrics

The zio-kafka consumer and producer collect some additional metrics using the zio-metrics API. This allows any
zio-metrics backend to access and process the observed values.

By default, no tags are added. Tags can be configured via `ConsumerSettings.withMetricsLabels` / `ProducerSettings.withMetricsLabels`.

To send these metrics to another backend, change histogram boundaries, or turn metrics off, see [Metrics Observers](metrics-observers.md).

Like the zio-metrics we follow Prometheus conventions. This means that:

- durations are expressed in seconds,
- counters can only increase,
- metric names use snake_case and end with the unit name where possible.

Each histogram defines 10 bucket boundaries (zio-metrics ignores a boundary of `0` and adds a `+Inf` bucket). To reach a decent range while keeping sufficient accuracy at the low end, most
bucket boundaries use an exponential series based on 𝑒.

## Zio-kafka Consumer Metrics

To interpret the metrics you need to know how zio-kafka works internally.

![](consumer-internals.svg)

See the [tuning guide](consumer-tuning.md#using-metrics-to-tune-the-consumer) for more details on the internals.

### Poll metrics

| Type      | Name                                                  | Description                                                                        |
|-----------|-------------------------------------------------------|------------------------------------------------------------------------------------|
| counter   | `ziokafka_consumer_polls`                             | The number of polls.                                                               |
| histogram | `ziokafka_consumer_poll_latency_seconds`              | The duration of a single poll in seconds.                                          |
| histogram | `ziokafka_consumer_poll_size`                         | The number of records fetched by a single poll.                                    |
| gauge     | `ziokafka_consumer_partitions_resumed_in_latest_poll` | The number of partitions resumed in the latest poll call.                          |
| gauge     | `ziokafka_consumer_partitions_paused_in_latest_poll`  | The number of partitions paused in the latest poll call (because of backpressure). |
| counter   | `ziokafka_consumer_poll_auth_errors`                  | The number of polls that ended with an authentication or authorization error.      |

:::note
Note that `ziokafka_consumer_poll_auth_errors` only tracks errors surfaced directly during `poll()` calls. Silent
runtime authorization failures (such as a revoked `READ` ACL) are silently swallowed by `poll()` and will not increment
this metric. To detect and surface these failures as exceptions, configure the empty-poll metadata-refresh probe (
see [Detecting Authorization Failures](detecting-authorization-failures.md)).
:::

### Partition stream metrics

These metrics are sampled periodically, by default every 500ms (see `ConsumerSettings#withRunloopMetricsSchedule`).

| Type      | Name                                 | Description                                                            |
|-----------|--------------------------------------|------------------------------------------------------------------------|
| histogram | `ziokafka_consumer_pending_requests` | The number of partitions that ran out of records (the queue is empty). |
| histogram | `ziokafka_consumer_queue_size`       | The number of records queued for a partition.                          |
| histogram | `ziokafka_consumer_all_queue_size`   | The total number of records queued for all partitions.                 |
| histogram | `ziokafka_consumer_queue_polls`      | The number of polls during which records are idling in a queue.        |

### Commit metrics

These metrics measure the separate commit requests issued through zio-kafka's api. Metric
`ziokafka_consumer_pending_commits` is sampled periodically, like the partition stream metrics.

| Type      | Name                                       | Description                                         |
|-----------|--------------------------------------------|-----------------------------------------------------|
| histogram | `ziokafka_consumer_pending_commits`        | The number of commits that are awaiting completion. |
| counter   | `ziokafka_consumer_commits`                | The number of commits.                              |
| histogram | `ziokafka_consumer_commit_latency_seconds` | The duration of a commit in seconds.                |

### Aggregated commit metrics

In each runloop cycle zio-kafka combines all outstanding commit requests into 1 aggregated commit. These metrics are for the aggregated commits.

| Type      | Name                                                  | Description                                                                |
|-----------|-------------------------------------------------------|----------------------------------------------------------------------------|
| counter   | `ziokafka_consumer_aggregated_commits`                | The number of aggregated commits.                                          |
| histogram | `ziokafka_consumer_aggregated_commit_latency_seconds` | The duration of an aggregated commit in seconds.                           |
| histogram | `ziokafka_consumer_aggregated_commit_size`            | An approximation of the number of records (offsets) per aggregated commit. |

### Rebalance metrics

| Type    | Name                                              | Description                                                  |
|---------|---------------------------------------------------|--------------------------------------------------------------|
| counter | `ziokafka_consumer_rebalances`                    | The number of rebalances.                                    |
| gauge   | `ziokafka_consumer_partitions_currently_assigned` | The number of partitions currently assigned to the consumer. |
| counter | `ziokafka_consumer_partitions_assigned`           | The number of partitions assigned to the consumer.           |
| counter | `ziokafka_consumer_partitions_revoked`            | The number of partitions revoked to the consumer.            |
| counter | `ziokafka_consumer_partitions_lost`               | The number of partitions lost to the consumer.               |

### Runloop metrics

These metrics are sampled periodically, by default every 500ms (see `ConsumerSettings#withRunloopMetricsSchedule`).

| Type      | Name                                   | Description                                        |
|-----------|----------------------------------------|----------------------------------------------------|
| gauge     | `ziokafka_consumer_subscription_state` | Whether the consumer is subscribed (1) or not (0). |
| histogram | `ziokafka_consumer_command_queue_size` | The number of commands queued in the consumer.     |
| histogram | `ziokafka_consumer_commit_queue_size`  | The number of commits queued in the consumer.      |

See [ConsumerMetrics.scala](https://github.com/zio/zio-kafka/blob/master/zio-kafka/src/main/scala/zio/kafka/consumer/metrics/ConsumerMetrics.scala) for the exact details.

## Example

Here is an example dashboard that could be built with these metrics:

![metrics-dashboard.png](metrics-dashboard.png)

## Zio-kafka Producer Metrics

To understand the producer metrics it is useful to know how the producer works.

When you call the producer it first serializes the given batch of records (a single record is treated like a batch of
one). The batch is then offered to an internal send queue. Once the queue accepts the batch, the `produce*Async` methods
return immediately. The `produce` and `produceChunk` methods return later, when all acknowledgements are in. Once the fiber is ready
for it, it pulls a batch from the queue and sends the records to the Kafka broker one by one. Meanwhile, the broker
acknowledges records.

![](producer-internals.svg)

### Producer metrics

| Type      | Name                                | Description                                                                             |
|-----------|-------------------------------------|-----------------------------------------------------------------------------------------|
| counter   | `ziokafka_producer_calls`           | The number of produce calls that completed successfully.                                |
| histogram | `ziokafka_producer_latency_seconds` | The duration of a single produce, from after serialization to acknowledged, in seconds. |
| counter   | `ziokafka_producer_records`         | The number of records produced.                                                         |
| histogram | `ziokafka_producer_batch_size`      | The number of records per produce call.                                                 |

These metrics are only updated for produce calls in which every record was produced successfully.

### Queue metrics

| Type      | Name                                           | Description                                                                                  |
|-----------|------------------------------------------------|----------------------------------------------------------------------------------------------|
| histogram | `ziokafka_producer_send_queue_size`            | The number of batches (produce calls) in the zio-kafka send queue, sampled every 10 seconds. |
| histogram | `ziokafka_producer_send_queue_latency_seconds` | Time in send queue, including waiting for capacity, in seconds.                              |

The capacity of the send queue is set with `ProducerSettings#withSendBufferSize`.

### Send metrics

| Type    | Name                            | Description                                                                           |
|---------|---------------------------------|---------------------------------------------------------------------------------------|
| counter | `ziokafka_producer_auth_errors` | The number of record sends that resulted in an authentication or authorization error. |

See [ProducerMetrics.scala](https://github.com/zio/zio-kafka/blob/master/zio-kafka/src/main/scala/zio/kafka/producer/metrics/ProducerMetrics.scala) for the exact details.
