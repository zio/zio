# StatsD Client

> In a normal StatsD setup we will find a StatsD agent with an open UDP port where applications send their 
> metrics to. The format of the metrics is defined in simple [datagrams](https://docs.datadoghq.com/developers/dogstatsd/datagram_shell/?tab=metrics).

The StatsD client creates the relevant StatsD datagrams and sends them via UDP.

> The instrumented code is exactly the same as for the Prometheus instrumentation. The only difference is that 
> another client is provided when the App is configured. 

## ZIO Metrics in StatsD

StatsD normally has its own definition how histograms and summaries are configured. In the default setup this 
is defined in the config file of the statsd agent. Furthermore, a StatsD Histogram is more or less the
equivalent of a Prometheus summary. 

However, whenever the desired quantiles need to change, the config must be adjusted and the agent restarted. 

Therefore, the client maps the more complex metrics to a set of related gauges. This allows us to achieve 
the same visualization without the need to adjust any of the agents config files. 

### Counter 

On each poll, a datagram is sent with the change since the previous poll, as in:

```
countAll:1|c
```

### Gauge 

Whenever a gauge changes, a datagram with the current absolute value of the gauge is reported.
```
adjustGauge:32.2787317766752|g
```

### Histogram 

A histogram is reported in a datagram built from related gauges. The individual records share the same name
and the buckets are encoded in an extra label `le`. 

```
myHistogram:0|g|#le:0.0
myHistogram:3|g|#le:10.0
myHistogram:6|g|#le:20.0
myHistogram:10|g|#le:30.0
myHistogram:13|g|#le:40.0
myHistogram:16|g|#le:50.0
myHistogram:19|g|#le:60.0
myHistogram:22|g|#le:70.0
myHistogram:24|g|#le:80.0
myHistogram:27|g|#le:90.0
myHistogram:29|g|#le:100.0
myHistogram:37|g|#le:Inf
```

### Summary 

A summary is also reported as a set of related gauges. Each quantile will be reported within its own gauge. 
The additional labels are `quantile` to address the quantile and `error` to display the configured error margin. 

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

## The ZIO Metrics StatsD example

For StatsD we do not need to start a server in the application. We provide a client that sends datagrams to a specified
UDP destination. The following application counts a request every second. It provides the `statsdUDP` layer. Building
this layer starts the reporting, so it is provided even though the program does not use the `StatsdClient` directly:

```scala
import zio._
import zio.metrics.Metric
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.statsd.{statsdUDP, StatsdConfig}

object StatsdApp extends ZIOAppDefault {

  val requests: Metric.Counter[Any] =
    Metric.counter("countAll", "Requests handled").fromConst(1L)

  val program: UIO[Unit] =
    (ZIO.unit @@ requests *> ZIO.sleep(1.second)).forever

  override def run =
    program.provideLayer(
      (ZLayer.succeed(MetricsConfig(100.millis)) ++ ZLayer.succeed(StatsdConfig("localhost", 8125))) >>> statsdUDP
    )
}
```

The layer polls the ZIO Metrics registry on the `MetricsConfig` interval. On each poll it sends the metrics that changed since the previous poll to StatsD as datagrams.

## Configuration

The StatsD client takes a `StatsdConfig` when it sends datagrams over UDP, and a `DatagramSocketConfig` when it sends them to a unix domain socket. The `statsdUDP` and `statsdUDS` layers read these settings. They also read `MetricsConfig`, which sets how often the metrics registry is polled.

Every field and default of `StatsdConfig` and `DatagramSocketConfig` is listed in the [configuration reference](./configuration.md#statsd).

`StatsdConfig.default` points to `localhost` on port `8125`. `DatagramSocketConfig` has a `path` field instead of `host` and `port`, and it accepts the same `prefix`, `constantTags` and `suffix` fields. If a datagram cannot be sent, the client drops it without raising an error.

### Prefix

The `prefix` is prepended to every metric name, including each bucket line of a histogram. Any separator has to be part of the prefix. To report all metrics under `myapp.`:

```scala
import zio.metrics.connectors.statsd.StatsdConfig

val config = StatsdConfig("localhost", 8125, prefix = Some("myapp."))
```

With this configuration, a counter named `countAll` that reaches 1 is sent as:

```
myapp.countAll:1|c
```

### Constant Tags

The `constantTags` are added to every datagram, before the tags of the metric itself. Use them for values that are the same across the whole application, such as the environment:

```scala
import zio.metrics.MetricLabel
import zio.metrics.connectors.statsd.StatsdConfig

val config = StatsdConfig(
  host = "localhost",
  port = 8125,
  constantTags = List(MetricLabel("env", "prod"), MetricLabel("service", "checkout")),
)
```

With these tags, the same counter is sent as:

```
countAll:1|c|#env:prod,service:checkout
```

A metric with its own tags, such as `region:eu`, appends them after the constant tags: `countAll:1|c|#env:prod,service:checkout,region:eu`.

The `suffix` is appended to the end of every datagram, after the tags. The client adds it as written, so include any separator in the value yourself.

To send these datagrams to a Datadog agent, see [How to Send Metrics to Datadog](../how-to/send-metrics-to-datadog.md).
