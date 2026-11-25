# Prometheus Client

> In a normal prometheus setup we will find prometheus agents which query configured endpoints at regular intervals. The
> endpoints are HTTP endpoints serving the current metric state in an encoding defined by
> [prometheus](https://prometheus.io/docs/instrumenting/exposition_formats/#text-based-format).

ZIO Metrics Connectors provides the Prometheus encoding for the captured metrics out of the box. To avoid enforcing a
particular HTTP implementation, an instrumented application needs to expose the encoded format as an endpoint with the
HTTP server of its choice.

## ZIO Metrics in Prometheus

Most of the ZIO metrics have a direct representation in the Prometheus encoding.

### Counter

A counter is represented as a prometheus counter.

```scala
Metric.counter("count_all", "The counter description.")
```

```
# TYPE count_all counter
# HELP count_all The counter description.
count_all 460.0 1623586224730
```

### Gauge

A gauge is represented as a prometheus gauge.

```scala
Metric.gauge("adjust_gauge", "The gauge description.")
```

```
# TYPE adjust_gauge gauge
# HELP adjust_gauge The gauge description.
adjust_gauge -1.2485836762095701 1623586224730
```

### Histogram

A histogram is represented as a prometheus histogram.

```scala
Metric.histogram(
  "my_histogram",
  "My histogram description.",
  MetricKeyType.Histogram.Boundaries.linear(0.0, 10.0, 11)
)
```

```
# TYPE my_histogram histogram
# HELP my_histogram My histogram description.
my_histogram_bucket{le="0.0"} 0.0 1623586224730
my_histogram_bucket{le="10.0"} 8.0 1623586224730
my_histogram_bucket{le="20.0"} 18.0 1623586224730
my_histogram_bucket{le="30.0"} 30.0 1623586224730
my_histogram_bucket{le="40.0"} 44.0 1623586224730
my_histogram_bucket{le="50.0"} 51.0 1623586224730
my_histogram_bucket{le="60.0"} 59.0 1623586224730
my_histogram_bucket{le="70.0"} 65.0 1623586224730
my_histogram_bucket{le="80.0"} 76.0 1623586224730
my_histogram_bucket{le="90.0"} 88.0 1623586224730
my_histogram_bucket{le="100.0"} 95.0 1623586224730
my_histogram_bucket{le="+Inf"} 115.0 1623586224730
my_histogram_sum 6828.578655207023 1623586224730
my_histogram_count 115.0 1623586224730
my_histogram_min 0.2 1623586224730
my_histogram_max 118.4 1623586224730
```

### Summary

A summary is represented as a prometheus summary.

```scala
Metric.summary(
  "my_summary",
  "The summary description.",
  maxAge = ???,
  maxSize = ???,
  error = ???,
  quantiles = Chunk(0.1, 0.5, 0.9),
)
```

```
# TYPE my_summary summary
# HELP my_summary The summary description. 
my_summary{quantile="0.1",error="0.03"} 147.0 1623589839194
my_summary{quantile="0.5",error="0.03"} 286.0 1623589839194
my_summary{quantile="0.9",error="0.03"} 470.0 1623589839194
my_summary_sum 42582.0 1623589839194
my_summary_count 139.0 1623589839194
my_summary_min 3.0 1623589839194
my_summary_max 512.0 1623589839194
```

### Frequency

A frequency is represented by a set of prometheus counters, distinguished from each other with a label.

```scala
val aspSet = Metric.frequency("my_set", "My set description.")
ZIO.succeed("my_key_10") @@ aspSet
ZIO.succeed("my_key_11") @@ aspSet
// etc.
```

```
# TYPE my_set counter
# HELP my_set My set description.
my_set{bucket="my_key_17"} 7.0 1623589839194
my_set{bucket="my_key_18"} 9.0 1623589839194
my_set{bucket="my_key_19"} 12.0 1623589839194
my_set{bucket="my_key_13"} 6.0 1623589839194
my_set{bucket="my_key_14"} 4.0 1623589839194
my_set{bucket="my_key_15"} 6.0 1623589839194
my_set{bucket="my_key_16"} 5.0 1623589839194
my_set{bucket="my_key_10"} 10.0 1623589839194
my_set{bucket="my_key_11"} 1.0 1623589839194
my_set{bucket="my_key_12"} 10.0 1623589839194
```

## Serving the Encoded Metrics

The encoded state is stored in the `PrometheusPublisher`, and the application retrieves it with `PrometheusPublisher#get`. The resulting string can be used in an HTTP response that has a `Content-Type` header with value `text/plain; version=0.0.4`. For a complete route and server, see [How to Serve Prometheus Metrics over HTTP](../how-to/serve-prometheus-metrics.md). For a walkthrough from instrumentation to a scraped endpoint, follow the [tutorial](../tutorial/instrument-and-scrape.md). To chart the metrics, see [How to Visualize Prometheus Metrics in Grafana](../how-to/visualize-metrics-in-grafana.md).
