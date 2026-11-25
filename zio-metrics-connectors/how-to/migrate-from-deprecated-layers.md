# How to Migrate from the Deprecated StatsD and Datadog Layers

> Version 2.4.0 deprecated `statsd.statsdLayer`, `datadog.datadogLayer`, and the `DatadogConfig` type. This guide shows
> the replacement for each and how the settings move.

## Replace `statsd.statsdLayer`

`statsd.statsdLayer` needed a `StatsdConfig` and a `MetricsConfig`, and it produced no output. Replace it with
`statsd.statsdUDP`. It needs the same two configurations and works the same way, and it also provides a `StatsdClient`.
To send to a Unix domain socket instead, use `statsd.statsdUDS` with a `DatagramSocketConfig`.

## Replace `datadog.datadogLayer` and `DatadogConfig`

`datadog.datadogLayer` read a `DatadogConfig`, which held both the agent address and the Datadog settings. The
replacement splits them:

- The agent address, `host` and `port`, moves to a `StatsdConfig`, or to a `DatagramSocketConfig` with a `path`. A
  StatsD layer, `statsd.statsdUDP` or `statsd.statsdUDS`, uses it to provide the `StatsdClient`.
- The Datadog settings move to a `DatadogPublisherConfig`. It keeps the field names `histogramSendInterval`,
  `maxBatchedMetrics`, `maxQueueSize`, `containerId`, `entityId` and `sendUnchanged` with the same defaults, and it adds
  `constantTags` and `prefix`.
- The `datadog.live` layer replaces `datadog.datadogLayer`. It needs the `StatsdClient`, the
  `DatadogPublisherConfig` and a `MetricsConfig`.

The following application uses the new layers with the values that `DatadogConfig.default` had:

```scala
import zio._
import zio.metrics.Metric
import zio.metrics.connectors.{datadog, statsd, MetricsConfig}

object MigratedDatadogApp extends ZIOAppDefault {

  val requests: Metric.Counter[Any] =
    Metric.counter("dd_requests", "Requests handled").fromConst(1L)

  val program: UIO[Unit] =
    (ZIO.unit @@ requests *> ZIO.sleep(1.second)).forever

  override def run =
    program.provide(
      ZLayer.succeed(MetricsConfig(10.seconds)),
      ZLayer.succeed(statsd.StatsdConfig("localhost", 8125)),
      ZLayer.succeed(datadog.DatadogPublisherConfig()),
      statsd.statsdUDP,
      datadog.live,
    )
}
```

Read the [Datadog connector page](../reference/datadog-client.md) about the datagrams that this layer combination sends.
For every field, see the [configuration reference](../reference/configuration.md#datadog).
