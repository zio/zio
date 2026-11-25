# How to Serve Prometheus Metrics over HTTP

> The Prometheus connector does not run an HTTP server. It stores the latest encoded metrics in a `PrometheusPublisher`, and
> your application decides how to expose that text. This guide shows how to serve the text with zio-http, how to change the
> path and port, and how to control how often the text is refreshed.

## Read the Text from the Publisher

`PrometheusPublisher#get` returns the current text as a `UIO[String]`, so any route can call it. The route must set the
content type to `text/plain; version=0.0.4` so that Prometheus reads the body as the text format. The following route serves
the metrics at `/internal/prometheus`:

```scala
import java.nio.charset.StandardCharsets

import zio._
import zio.http._
import zio.metrics.connectors.prometheus.PrometheusPublisher

val prometheusRoute: Route[PrometheusPublisher, Nothing] =
  Method.GET / "internal" / "prometheus" -> handler {
    ZIO.serviceWithZIO[PrometheusPublisher](_.get).map { body =>
      Response(
        status = Status.Ok,
        headers = Headers(Header.Custom(Header.ContentType.name, "text/plain; version=0.0.4")),
        body = Body.fromString(body, StandardCharsets.UTF_8),
      )
    }
  }
```

To change the path, change the segments after `Method.GET`. Keep the path in your Prometheus scrape configuration in sync
with the route.

## Choose the Port

The server port comes from `Server.Config`. The following layer makes the server listen on port 9090 instead of the
default 8080:

```scala
import zio._
import zio.http._

val serverConfig: ZLayer[Any, Nothing, Server.Config] =
  ZLayer.succeed(Server.Config.default.port(9090))
```

## Set the Refresh Interval

The connector refreshes the publisher text on the interval in `MetricsConfig`. A scrape returns the text from the most
recent refresh, not a new snapshot. A shorter interval gives fresher values and more work for the connector. A longer
interval does less work but returns older values. A reasonable starting point is the same interval as your Prometheus
scrape interval:

```scala
import zio._
import zio.metrics.connectors.MetricsConfig

val metricsConfig: ZLayer[Any, Nothing, MetricsConfig] =
  ZLayer.succeed(MetricsConfig(15.seconds))
```

The first refresh runs about 10 milliseconds after the layer starts. Until then, the publisher text is empty, so a scrape
that arrives very early returns an empty body.

## Wire the Route into a Server

The application provides the server, the metrics layers, and the publisher. The routes need the `PrometheusPublisher`
in their environment, which `prometheus.publisherLayer` provides:

```scala
import zio._
import zio.http._
import zio.metrics.connectors.prometheus

object MetricsServer extends ZIOAppDefault {

  override def run =
    Server.serve(Routes(prometheusRoute))
      .provide(
        serverConfig,
        Server.live,
        metricsConfig,
        prometheus.publisherLayer,
        prometheus.prometheusLayer,
      )
}
```

## Test the Endpoint

To check the response headers as well as the body, request the endpoint with `curl -i`:

```
curl -i http://localhost:9090/internal/prometheus
```

The response status is 200, the `Content-Type` header is `text/plain; version=0.0.4`, and once the first refresh has run,
the body lists the metrics in the Prometheus text format, as shown in the [tutorial](../tutorial/instrument-and-scrape.md).
