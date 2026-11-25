# Metric Type Mapping

> Each ZIO metric type has a different shape, and each backend has its own shape for the same data. This page shows how
> every connector encodes each type, so that you can build dashboards and queries against the output. For the definition of
> each type and how to create it, see [Metric Reference](./metric-reference.md).

The table below gives the output of each connector for each metric type. The Prometheus column shows the metric family
name and the suffixes that the connector adds. The StatsD and Datadog columns show the datagram type code (`c`, `g`, or `d`).

| Metric Type | Prometheus                                              | StatsD and Datadog                                      | New Relic                                       | Micrometer                                       |
| ----------- | ------------------------------------------------------- | ------------------------------------------------------- | ----------------------------------------------- | ------------------------------------------------ |
| Counter     | `counter`, with the total count as the value            | `c`, with the change since the last poll                | `count`, with the change and `interval.ms`      | `Counter`                                        |
| Gauge       | `gauge`, with the current value                         | `g`, with the current value                             | `gauge`, with the current value                 | `Gauge`, backed by an atomic double              |
| Histogram   | `histogram`: `_bucket{le}`, `_sum`, `_count`, `_min`, `_max` | StatsD: one `g` per bucket with an `le` tag. Datadog: `d` distribution of raw values | `summary`, with count, sum, min, and max | `DistributionSummary`, with the bucket boundaries as service level objectives |
| Summary     | `summary`: `{quantile,error}`, `_sum`, `_count`, `_min`, `_max` | `g` per quantile, with `quantile` and `error` tags | `summary`, with count, sum, min, max, and `zio.error.margin` | `DistributionSummary`, with the quantiles as percentiles and an `error` tag |
| Frequency   | `counter`, one series per value with a `bucket` label   | `g` per value, with a `bucket` tag                      | `count` per value, with a `zio.frequency.name` attribute | `Counter` per value, with a `bucket` tag     |

## Prometheus

Prometheus output has a `# TYPE` and a `# HELP` line for each metric family. Metric names are changed so that they are
valid in Prometheus: hyphens, spaces, and dots become underscores. Counters do not get a `_total` suffix.

Histograms produce one `_bucket` series for each boundary, with an `le` label, and the `+Inf` bucket is last. Summaries
produce one series for each quantile, with `quantile` and `error` labels, and no buckets. Both types also produce `_sum`,
`_count`, `_min`, and `_max` series.

Frequencies produce a `counter` family. Each observed value becomes one series, with the value in the `bucket` label.

## StatsD and Datadog

StatsD and Datadog share the same datagram format. Each metric becomes one or more datagrams of the form
`name:value|type|#tags`. Constant tags come first in the tags section, followed by the metric's own labels.

Counters send the change since the last poll, so the backend must sum them. Gauges send the current value. Histograms
and summaries send one gauge per bucket or quantile, distinguished by the `le` or `quantile` tag. Frequencies send one
gauge per observed value, distinguished by the `bucket` tag.

The Datadog connector differs in one case. Histograms become `d` distributions, which carry raw values. Datadog then
computes the percentiles itself, so the bucket boundaries are not sent. See the [Datadog page](./datadog-client.md).

## New Relic

The New Relic connector sends JSON events to the Metric API. Counters become `count` events with an `interval.ms`
field, which is the time from the start of the application to the latest event. Gauges become `gauge` events.

Histograms and summaries become `summary` events with count, sum, min, and max. Quantiles are not sent. The error margin is
sent as the `zio.error.margin` attribute. Frequencies become `count` events, one for each observed value, with the value in the
`zio.frequency.name` attribute. Metric labels become event attributes, with their names sanitized.

## Micrometer

The Micrometer connector creates Micrometer meters directly, so the registry decides the final format. The connector sets
the following properties:

- Histograms use the boundaries as service level objectives. Boundaries of zero or less are dropped, because Micrometer
  does not accept them.
- A histogram's base unit comes from a `time_unit` label. The connector removes that label from the tags.
- Summaries publish their quantiles as percentiles. The number of digits comes from `MicrometerConfig`.
- Frequencies are counters, with one meter for each observed value.
