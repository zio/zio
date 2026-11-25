# How to Check Metrics Locally and in Tests

> You can check which metrics an application records without running a monitoring backend. The Prometheus publisher keeps
> the encoded text in memory, so a program or a test can read that text directly and check it. This guide shows how to read
> the publisher in a local program and in a ZIO Test spec.

## Read the Publisher in a Local Program

The publisher refreshes on the `MetricsConfig` interval, so a program must wait longer than one interval before it reads
the text. The following program uses an interval of 100 milliseconds and waits 300 milliseconds before it reads:

```scala
import zio._
import zio.metrics.Metric
import zio.metrics.connectors.{prometheus, MetricsConfig}
import zio.metrics.connectors.prometheus.PrometheusPublisher

object LocalCheckApp extends ZIOAppDefault {

  val requests: Metric.Counter[Any] =
    Metric.counter("local_requests", "Requests seen by the local check").fromConst(1L)

  val program: ZIO[PrometheusPublisher, Throwable, Unit] =
    for {
      _    <- ZIO.unit @@ requests
      _    <- ZIO.sleep(300.millis)
      body <- ZIO.serviceWithZIO[PrometheusPublisher](_.get)
      _    <- Console.printLine(body)
    } yield ()

  override def run =
    program.provide(
      ZLayer.succeed(MetricsConfig(100.millis)),
      prometheus.publisherLayer,
      prometheus.prometheusLayer,
    )
}
```

The program prints the text that a scrape would return. For a single request, the output is:

```
# TYPE local_requests counter
# HELP local_requests Requests seen by the local check
local_requests 1.0 1794253765088
```

## Check Values in a ZIO Test

A ZIO Test spec can use the same publisher. The spec reads the text and asserts that the expected series is present:

```scala
import zio._
import zio.metrics.Metric
import zio.metrics.connectors.{prometheus, MetricsConfig}
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.test._

object OrdersMetricsSpec extends ZIOSpecDefault {

  val orders: Metric.Counter[Any] =
    Metric.counter("test_orders_processed", "Orders processed in the test").fromConst(1L)

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Orders metrics")(
      test("record the processed order in the Prometheus text") {
        for {
          _    <- ZIO.unit @@ orders
          _    <- ZIO.sleep(300.millis)
          body <- ZIO.serviceWithZIO[PrometheusPublisher](_.get)
        } yield assertTrue(body.contains("test_orders_processed 1.0"))
      },
    ).provide(
      ZLayer.succeed(MetricsConfig(100.millis)),
      prometheus.publisherLayer,
      prometheus.prometheusLayer,
    ) @@ TestAspect.withLiveClock
}
```

The spec ends with `TestAspect.withLiveClock`. The connector starts its refresh fiber when the layers are built, and that
fiber waits on the clock. ZIO Test replaces the clock with a test clock by default, and that clock does not advance on its
own, so the publisher would never refresh. The aspect must cover the layers, which is why it is applied to the whole spec.

:::tip[Use unique metric names]
ZIO Metrics keeps one registry for the whole JVM. If two tests record the same metric name, their values add up. Give
each test its own metric name, or check only the series that the test records.
:::

For a unit test that does not need the Prometheus text, the Micrometer connector can use an in-memory
`SimpleMeterRegistry`, which records values without any output format. See [Micrometer Connector](../reference/micrometer-connector.md).
