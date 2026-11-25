# Datadog Client

> In a normal Datadog setup we will find a Datadog agent with an open UDP port where applications send their
> metrics to. The format of the metrics is defined by the [DogStatsD protocol](https://docs.datadoghq.com/developers/dogstatsd/datagram_shell/?tab=metrics).

The Datadog connector creates the relevant DogStatsD datagrams and sends them through a `StatsdClient`, over UDP or a Unix domain socket.

> The instrumented code is exactly the same as for the Prometheus instrumentation. The only difference is that
> another client is provided when the App is configured.

## ZIO Metrics in DogStatsD

DogStatsD normally has its own definition how histograms and summaries are configured. In the default setup this
is defined in the config file of the statsd agent. Furthermore, a StatsD Histogram is more or less the
equivalent of a Prometheus summary.

However, whenever the desired quantiles need to change, the config must be adjusted and the agent restarted.

Therefore, the ZIO Metrics Datadog connector maps summaries and frequencies to sets of related gauges, and histograms to distributions. This allows us to achieve
the same visualization without the need to adjust any of the agents config files.

### Counter

Counters are reported periodically within a configurable time interval, as in:

```
countAll:1|c
```

### Gauge

Gauges are reported periodically within a configurable time interval, as in:

```
adjustGauge:32.2787317766752|g
```

### Histogram

A histogram is reported as a datadog distribution. Each submitted value is queued and sent at the next flush, which runs every `histogramSendInterval`. Values for the same histogram that are queued together are sent in one datagram. The bucket configuration is 
ignored in this case, as Datadog expects raw values to be recorded. 

```
myHistogram:3|d
```

### Summary

A summary is periodically reported as a set of related gauges, within a configurable time interval. Each quantile will 
be reported within its own gauge. The additional labels are `quantile` to address the quantile and `error` to display 
the configured error margin.

```
mySummary:123|g|#quantile:0.1,error:0.03
mySummary:254|g|#quantile:0.5,error:0.03
mySummary:441|g|#quantile:0.9,error:0.03
```

### Frequencies

Frequencies are reported as sets of related gauges. An additional label is used to differentiate the distinct
values in the set. The label is named `bucket`.

```
mySet:6|g|#bucket:my_key_18
mySet:1|g|#bucket:my_key_19
mySet:2|g|#bucket:my_key_13
mySet:3|g|#bucket:my_key_14
mySet:1|g|#bucket:my_key_16
mySet:3|g|#bucket:my_key_10
mySet:2|g|#bucket:my_key_11
mySet:1|g|#bucket:my_key_12
```

## The ZIO Metrics Datadog Example

The Datadog connector sends its datagrams through a `StatsdClient`. We need a client that can send them to a specified
UDP destination or a unix domain socket, and we do not need to start a server in the application. The following
application counts a request every second. It provides the `datadog.live` layer together with the `statsd.statsdUDP`
layer that supplies its `StatsdClient`. Building these layers starts the reporting, so they are provided even though the
program does not use the client directly:

```scala
import zio._
import zio.metrics.Metric
import zio.metrics.connectors.{datadog, statsd, MetricsConfig}

object DatadogApp extends ZIOAppDefault {

  val requests: Metric.Counter[Any] =
    Metric.counter("dd_requests", "Requests handled").fromConst(1L)

  val program: UIO[Unit] =
    (ZIO.unit @@ requests *> ZIO.sleep(1.second)).forever

  override def run =
    program.provide(
      ZLayer.succeed(MetricsConfig(100.millis)),
      ZLayer.succeed(statsd.StatsdConfig("localhost", 8125)),
      ZLayer.succeed(datadog.DatadogPublisherConfig()),
      statsd.statsdUDP,
      datadog.live,
    )
}
```

:::warning[Each metric is sent twice with this layer combination]
The `datadog.live` layer needs a `StatsdClient`. The public layers that provide one, `statsd.statsdUDP` and `statsd.statsdUDS`, also start their own sender for every metric. With the combination above, counters, gauges, summaries, and frequencies are therefore sent once by each sender, and the Datadog agent receives two datagrams per update. With default settings the two datagrams are identical. Histograms are sent as `le` gauges by the StatsD sender and as `d` distributions by the Datadog sender.
:::

To send datagrams over a unix domain socket instead of UDP, use `statsd.statsdUDS` with a `statsd.DatagramSocketConfig`, as shown in [How to Send Metrics to Datadog](../how-to/send-metrics-to-datadog.md). This can be used with the Datadog agent starting with version 6.0.0 on the Linux platform.

## Configuration

The `datadog.live` layer reads a `DatadogPublisherConfig`, and it needs a `StatsdClient` to send the datagrams. That client comes from the `statsd.statsdUDP` or `statsd.statsdUDS` layer. The layer also reads `MetricsConfig`, which sets how often the metrics registry is polled.

Every field and default of `DatadogPublisherConfig` is listed in the [configuration reference](./configuration.md#datadog).

The older `DatadogConfig` is deprecated since version 2.4.0. It has no `prefix` or `constantTags` fields, so use `DatadogPublisherConfig` instead.

### Prefix and Constant Tags

The `prefix` is prepended to every metric name, and the `constantTags` are added to every datagram before the tags of the metric itself. Any separator has to be part of the prefix. To report under `myapp.` with the environment and service as tags:

```scala
import zio.metrics.MetricLabel
import zio.metrics.connectors.datadog.DatadogPublisherConfig

val publisherConfig = DatadogPublisherConfig(
  prefix = Some("myapp."),
  constantTags = List(MetricLabel("env", "prod"), MetricLabel("service", "checkout")),
)
```

With this configuration, a counter named `countAll` is sent as:

```
myapp.countAll:1|c|#env:prod,service:checkout
```

Histograms are sent as distributions with the same prefix and tags, so the same settings apply to them. A histogram named `myHistogram` with the value 3 is sent as `myapp.myHistogram:3|d|#env:prod,service:checkout`.

### Entity ID

When `entityId` is set, the client adds the internal `dd.internal.entity_id` tag after the constant tags. The tag lets Datadog track the client entity that sent the metric. To set an entity ID:

```scala
import zio.metrics.connectors.datadog.DatadogPublisherConfig

val publisherConfig = DatadogPublisherConfig(entityId = Some("pod-uid-123"))
```

With both `constantTags` and `entityId` set, the counter above is sent as `myapp.countAll:1|c|#env:prod,service:checkout,dd.internal.entity_id:pod-uid-123`.

The `containerId` is sent as a suffix after the tags. For example, a gauge named `m1` with the container ID `aaa` is sent as `m1:1|g|c:aaa`.

To run the connector against a local Datadog agent, see [How to Send Metrics to Datadog](../how-to/send-metrics-to-datadog.md).
