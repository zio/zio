# How to Add Labels to Metrics

> Labels split one metric into several series, so that a dashboard can group or filter the values by a dimension such as
> the service or the route. This guide shows how to attach a fixed label to a metric and how to vary a label per call.

## Attach a Fixed Label

`Metric#tagged` returns a copy of the metric with one more label. Every series of that metric carries the label, so
the label is set once where the metric is defined:

```scala
import zio._
import zio.metrics.Metric

val requests: Metric.Counter[Any] =
  Metric.counter("http_requests", "Number of HTTP requests")
    .tagged("service", "orders")
    .fromConst(1L)
```

Call `Metric#tagged` again to add another label. Each call returns a new metric and leaves the original unchanged.

## Vary a Label per Call

To set a label that depends on the work being done, apply a tagged copy of the metric at the call site. The next
block records one request for each route it handles:

```scala
import zio._
import zio.metrics.Metric

def handle(route: String): UIO[Unit] =
  ZIO.unit @@ requests.tagged("route", route)
```

When the application calls `handle("/orders")` twice and `handle("/health")` once, the Prometheus output contains one
series per distinct combination of labels:

```
# TYPE http_requests counter
# HELP http_requests Number of HTTP requests
http_requests{service="orders",route="/orders"} 2.0 1794253765088
http_requests{service="orders",route="/health"} 1.0 1794253765088
```

The order of labels inside the braces is not guaranteed, because the connector stores them in a set.

:::warning[Keep label values bounded]
Each distinct label value creates a new series. Label values taken from user input, such as customer IDs, email
addresses, or raw URL paths that contain IDs, can create an unbounded number of series. Use a route template such as
`/orders/:id` instead of the actual path.
:::

For the full list of labels that each connector forwards, see [Metric Type Mapping](../reference/metric-type-mapping.md).
