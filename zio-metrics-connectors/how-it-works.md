# How ZIO Metrics Connectors Work

> A connector reads the metric state that ZIO keeps in its metrics registry. The state is kept in an internal data structure that is optimized to update as efficiently as possible, and the data that a backend needs is generated only when it is required. To define and apply metrics, see [Metric Reference](reference/metric-reference.md).

> Changing which platform the metrics are reported to has no impact on the application at all. Once instrumented
> properly, the decision where to report to happens 'at the end of the world', that is, in the ZIO application's main
> entry point, by providing one or more of the available reporting clients.

## Polling and Listening

The Prometheus, StatsD, Datadog, and New Relic connectors poll the registry on the interval of a `MetricsConfig`. The Prometheus connector republishes every metric on each poll. The StatsD, Datadog, and New Relic connectors skip metrics that did not change since the last poll, and Datadog sends them anyway when `sendUnchanged` is `true`. The Micrometer connector registers a listener, so every change reaches the Micrometer registry as it happens, and it does not need a `MetricsConfig`.

The Datadog connector also listens, for histograms only: it queues each recorded histogram value and sends it as a
distribution, while it polls for every other metric type.

## Push and Pull

Prometheus is a pull backend: your application serves the encoded text and the Prometheus server scrapes it. The
other connectors push to the backend. For a comparison of the connectors and how to pick one, see
[How to Choose a Connector](how-to/choose-a-connector.md).

## Where to Go Next

To add a connector to your build, see [Installation](index.md#installation). Then:

* [Metric Reference](reference/metric-reference.md) lists the metric types that ZIO Metrics supports.
* [Prometheus Client](reference/prometheus-client.md), [StatsD Client](reference/statsd-client.md),
  [Datadog Client](reference/datadog-client.md), [New Relic Client](reference/new-relic-client.md), and
  [Micrometer Connector](reference/micrometer-connector.md) describe each connector.
* [Configuration](reference/configuration.md) and [Metric Type Mapping](reference/metric-type-mapping.md) list every
  setting and how each metric type is encoded.
