# New Relic Client

> The New Relic connector sends metrics to the [New Relic Metric API](https://docs.newrelic.com/docs/data-apis/ingest-apis/metric-api/report-metrics-metric-api/)
> over HTTPS. The instrumented code is the same as for the other connectors. The only difference is the layer that you
> provide when the application starts. For a comparison of the connectors, see [Choose a Connector](../how-to/choose-a-connector.md).

## How Metrics Are Sent

The connector reads the ZIO Metrics registry on the `MetricsConfig` interval. It converts each changed metric into a JSON
event, as described in [Metric Type Mapping](./metric-type-mapping.md#new-relic). The events wait in a queue
until one of two conditions is met. Either the queue holds `maxMetricsPerRequest` events, or `maxPublishingDelay` elapses
after the last request. Then the connector sends the batch in one HTTPS request with the `Api-Key` header set to
your API key.

The queue holds up to twice `maxMetricsPerRequest` events. When the queue is full, the connector waits for room
before it queues more events.

The connector creates its own [zio-http](https://zio.dev/zio-http/) client with the default settings and uses `zio-json`
to build the request body. Both come with the `zio-metrics-connectors-newrelic` module as transitive dependencies. The
application cannot supply its own client, so the client's default TLS and proxy settings apply.

## Configure the Connector from the Environment

The `NewRelicConfig.fromEnvLayer` layer reads its settings from environment variables. The API key is required, and the
other settings have defaults. The [configuration reference](./configuration.md#new-relic) lists them all.

Set the API key in the shell before you start the application:

```bash
export NEW_RELIC_API_KEY="your-api-key"
```

Then provide the layers in your application. The following application records a counter every second:

```scala
import zio._
import zio.metrics.Metric
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.newrelic.{newRelicLayer, NewRelicConfig}

object NewRelicApp extends ZIOAppDefault {

  val requests: Metric.Counter[Any] =
    Metric.counter("nr_requests", "Requests handled").fromConst(1L)

  val program: UIO[Unit] =
    (ZIO.unit @@ requests *> ZIO.sleep(1.second)).forever

  override def run =
    program.provide(
      ZLayer.succeed(MetricsConfig(10.seconds)),
      NewRelicConfig.fromEnvLayer,
      newRelicLayer,
    )
}
```

The application fails to start if `NEW_RELIC_API_KEY` is not set, because `NewRelicConfig.fromEnvLayer` reads it as a required value.

## Use the EU Endpoint

New Relic accounts in the EU region use a different endpoint. The `NewRelicConfig.fromEnvEULayer` layer reads the same
variables but always sends to the EU endpoint, so it ignores `NEW_RELIC_URI`. Replace `NewRelicConfig.fromEnvLayer` with
`NewRelicConfig.fromEnvEULayer` in the `provide` call above to use it.

## Configure the Connector in Code

To set the values in code, construct a `NewRelicConfig` directly. The constructor takes the API key, the endpoint, the
batch size, and the maximum publishing delay:

```scala
import java.time.Duration

import zio.metrics.connectors.newrelic.{NewRelicConfig, NewRelicUri}

val config: NewRelicConfig =
  NewRelicConfig(
    apiKey = sys.env("NEW_RELIC_API_KEY"),
    newRelicURI = NewRelicUri.EU,
    maxMetricsPerRequest = 500,
    maxPublishingDelay = Duration.ofSeconds(5),
  )
```

Provide the config with `ZLayer.succeed(config)` in place of `NewRelicConfig.fromEnvLayer`. Use `NewRelicUri.Custom` with
your own endpoint URL when you need an endpoint that the connector does not list.

## Next Steps

The [configuration reference](./configuration.md) lists every New Relic setting and default. The
[metric type mapping](./metric-type-mapping.md#new-relic) shows the events that the connector sends for each
metric type.
