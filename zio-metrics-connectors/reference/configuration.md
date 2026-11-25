# Configuration Reference

> This page lists every configuration type that the connectors use, with its fields, defaults, and the layers that need it.
> Each connector page describes how to wire these configurations into an application.

## MetricsConfig

`MetricsConfig` sets how often the connector reads the ZIO Metrics registry. The Prometheus, StatsD, Datadog, and New Relic
connectors need it. The Micrometer connector does not, because it receives updates as they happen.

| Field      | Type       | Default  | Description                                                            |
| ---------- | ---------- | -------- | ---------------------------------------------------------------------- |
| `interval` | `Duration` | Required | How often the connector reads the registry and sends or publishes it. |

`MetricsConfig` has no default instance. Create one with the interval you need:

```scala
import zio._
import zio.metrics.connectors.MetricsConfig

val metricsConfig: ZLayer[Any, Nothing, MetricsConfig] =
  ZLayer.succeed(MetricsConfig(5.seconds))
```

## Prometheus

The Prometheus connector has no configuration type. Its layers are `prometheus.publisherLayer`, which provides a `PrometheusPublisher`, and `prometheus.prometheusLayer`, which requires a `MetricsConfig` and a `PrometheusPublisher`.

`PrometheusPublisher` has two methods. `get` returns the current Prometheus text, and `set` replaces it. The application
reads `get` in an HTTP route. See [Serve Prometheus Metrics](../how-to/serve-prometheus-metrics.md).

## StatsD

`StatsdConfig` describes the StatsD agent that receives UDP datagrams:

| Field          | Type               | Default  | Description                                                                             |
| -------------- | ------------------ | -------- | --------------------------------------------------------------------------------------- |
| `host`         | `String`           | Required | Host name of the agent.                                                                 |
| `port`         | `Int`              | Required | UDP port of the agent.                                                                  |
| `prefix`       | `Option[String]`   | `None`   | Prepended to every metric name. Include any separator, such as `"orders."`.             |
| `constantTags` | `List[MetricLabel]` | `Nil`   | Added to every datagram.                                                                |
| `suffix`       | `Option[String]`   | `None`   | Appended to every datagram, after the tags.                                             |

`StatsdConfig.default` points at `localhost` on port 8125, and `StatsdConfig.defaultLayer` provides it as a layer.

`DatagramSocketConfig` describes a Unix domain socket instead of UDP. It has the same `prefix`, `constantTags`, and
`suffix` fields, and it adds one required field:

| Field  | Type     | Default  | Description                                |
| ------ | -------- | -------- | ------------------------------------------ |
| `path` | `String` | Required | Path of the socket that the agent listens on. |

The layers are `statsd.statsdUDP`, which requires a `StatsdConfig` and a `MetricsConfig`, and `statsd.statsdUDS`, which
requires a `DatagramSocketConfig` and a `MetricsConfig`. The `statsd.statsdLayer` and `DatadogConfig` APIs are deprecated since
version 2.4.0, so new code should not use them.

The following values set a UDP agent with a prefix and one constant tag, and a socket path:

```scala
import zio.metrics.MetricLabel
import zio.metrics.connectors.statsd.{DatagramSocketConfig, StatsdConfig}

val udpConfig: StatsdConfig =
  StatsdConfig(
    host = "localhost",
    port = 8125,
    prefix = Some("orders."),
    constantTags = List(MetricLabel("env", "prod")),
  )

val socketConfig: DatagramSocketConfig =
  DatagramSocketConfig(path = "/var/run/datadog/datadog.sock")
```

## Datadog

`DatadogPublisherConfig` configures the Datadog connector. Every field has a default, so `DatadogPublisherConfig()` is a
valid configuration:

| Field                   | Type             | Default   | Description                                                                              |
| ----------------------- | ---------------- | --------- | ---------------------------------------------------------------------------------------- |
| `histogramSendInterval` | `Option[Duration]` | `None` | Sends distributions more often than the general interval.                                |
| `maxBatchedMetrics`     | `Int`            | `10`      | Maximum number of metrics per packet. Affects packet size.                               |
| `maxQueueSize`          | `Int`            | `100000`  | Maximum number of queued histogram values. Affects memory use.                           |
| `containerId`           | `Option[String]` | `None`    | Docker container ID, sent as a suffix on each datagram.                                  |
| `entityId`              | `Option[String]` | `None`    | Sent as the `dd.internal.entity_id` tag.                                                 |
| `sendUnchanged`         | `Boolean`        | `false`   | Sends metrics that did not change since the last poll.                                   |
| `constantTags`          | `List[MetricLabel]` | `Nil` | Added to every metric.                                                                   |
| `prefix`                | `Option[String]` | `None`    | Prepended to every metric name. Include any separator.                                   |

The `datadog.live` layer requires a `StatsdClient`, a `DatadogPublisherConfig`, and a `MetricsConfig`. Provide the
`StatsdClient` with `statsd.statsdUDP` or `statsd.statsdUDS`. The [Datadog page](./datadog-client.md) shows a
complete setup.

## New Relic

`NewRelicConfig` configures the New Relic connector. The constructor has no defaults. The `fromEnvLayer` and
`fromEnvEULayer` layers read these environment variables and apply the defaults listed in the table below:

| Environment Variable                 | Required | Default   | Description                                                             |
| ------------------------------------ | -------- | --------- | ----------------------------------------------------------------------- |
| `NEW_RELIC_API_KEY`                  | Yes      | None      | Your New Relic API key. The application fails to start without it.      |
| `NEW_RELIC_URI`                      | No       | US endpoint | Custom Metric API endpoint. Ignored by `fromEnvEULayer`.              |
| `NEW_RELIC_MAX_METRICS_PER_REQUEST`  | No       | `500`     | Maximum number of metrics in one HTTPS request.                          |
| `NEW_RELIC_MAX_PUBLISHING_DELAY`     | No       | `PT5S`    | Longest time to wait before sending a partial batch, as an ISO-8601 duration. |

The `NewRelicConfig` fields are `apiKey`, `newRelicURI`, `maxMetricsPerRequest`, and `maxPublishingDelay`. The
`newRelicURI` field takes one of three values from `NewRelicUri`:

- `NewRelicUri.NA` sends to `https://metric-api.newrelic.com/metric/v1`, the US endpoint.
- `NewRelicUri.EU` sends to `https://metric-api.eu.newrelic.com/metric/v1`, the EU endpoint.
- `NewRelicUri.Custom(endpoint)` sends to any other endpoint.

The `newRelicLayer` requires a `NewRelicConfig` and a `MetricsConfig`. It creates its own HTTP client.

## Micrometer

`MicrometerConfig` configures the Micrometer connector:

| Field                              | Type  | Default | Description                                                                       |
| ---------------------------------- | ----- | ------- | --------------------------------------------------------------------------------- |
| `summaryPercentileDigitsOfPrecision` | `Int` | `3`   | Digits of precision for the percentiles that summaries publish.                   |

`MicrometerConfig.default` uses the default value. The `micrometer.micrometerLayer` requires a `MeterRegistry` and a
`MicrometerConfig` in its environment. It does not require a `MetricsConfig`.

## Layers by Connector

The following table lists the layers to provide for each connector, and the configuration each one needs in its
environment:

| Connector  | Layers You Provide                                  | Environment Required                                        |
| ---------- | --------------------------------------------------- | ----------------------------------------------------------- |
| Prometheus | `prometheus.publisherLayer`, `prometheus.prometheusLayer` | `MetricsConfig`                                       |
| StatsD     | `statsd.statsdUDP` or `statsd.statsdUDS`            | `StatsdConfig` or `DatagramSocketConfig`, `MetricsConfig`  |
| Datadog    | `datadog.live`, plus a StatsD client layer          | `StatsdClient`, `DatadogPublisherConfig`, `MetricsConfig`  |
| New Relic  | `newRelicLayer`                                     | `NewRelicConfig`, `MetricsConfig`                          |
| Micrometer | `micrometer.micrometerLayer`                        | `MeterRegistry`, `MicrometerConfig`                        |
