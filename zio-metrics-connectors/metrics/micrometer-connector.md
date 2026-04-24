# Micrometer Connector

> ZIO Metrics has an integration with [Micrometer](https://micrometer.io/), a powerful metrics instrumentation library.
By combining these two frameworks, developers can benefit from comprehensive and flexible metrics monitoring capabilities
within their ZIO applications. This integration allows for efficient monitoring, gathering, and reporting of key
performance indicators (KPIs) and other metrics for enhanced observability.

ZIO Metrics has an integration with [Micrometer](https://micrometer.io/), a powerful metrics instrumentation library.
By combining these two frameworks, developers can benefit from comprehensive and flexible metrics monitoring capabilities
within their ZIO applications. This integration allows for efficient monitoring, gathering, and reporting of key
performance indicators (KPIs) and other metrics for enhanced observability.

## Benefits

Micrometer integration offers a range of benefits that make it an excellent choice for integrating with ZIO Metrics.
Here are some key advantages:

### 1. Comprehensive Metrics Support

Micrometer provides a vendor-neutral facade for various monitoring systems, including Prometheus, Datadog, Graphite,
and more. The same like slf4j for logs but for observability. It offers a unified API for recording metrics, allowing
developers to easily integrate with multiple backend systems and switch between different monitoring systems without
major code changes for its ZIO applications. You can choose whatever you want as monitoring tool - Grafana,
Prometheus, and Jaeger and so on.

### 2. Real-time Metrics Updates

By leveraging the low-level integration with the ZIO Metrics core client listener, the module provides immediate
updates to Micrometer whenever metric changes occur within the ZIO application. This real-time synchronization
ensures that the metrics reported by Micrometer accurately reflect the current state of the application, enabling
timely monitoring and analysis.

### 3. Always on trend of standards

Integration with Micrometer ensures that developers are always on trend of standards with metrics format changes and
can take advantage of the latest updates and improvements in the monitoring ecosystem. By relying on Micrometer as
the integration layer, developers gain compatibility with the latest monitoring systems, future-proof their
monitoring infrastructure, benefit from community-driven updates and support, and simplify maintenance and upgrades.
This integration provides a reliable and scalable solution for metrics monitoring in ZIO-based applications, allowing
developers to focus on their core business logic while staying at the forefront of monitoring technology.

## Example of usage

1. Import Dependencies

   In your project's build configuration, add the following dependency to import the ZIO Metrics Micrometer module:

```
   libraryDependencies += "dev.zio" %% "zio-metrics-connectors-micrometer" % latest
```

2. Choose Micrometer Backend

   Decide on the backend for the Micrometer registry. You can use the built-in
   'SimpleMeterRegistry', which stores all metrics in memory (commonly used for testing), or select another backend for
   external integration. In this example, we'll use Prometheus as the backend.

```
   "io.micrometer" % "micrometer-registry-prometheus" % latest
```

3. Provide Micrometer and its backend Layer to your main ZIO effect

   `zio-metrics-connectors-micrometer` gives you `micrometer.micrometerLayer` which initializes a bridge between ZIO Metrics and
   Micrometer.
   You should also provide a layer with a backend for micrometer.

4. Optionally enable Core ZIO Metrics and Default JVM Metrics

   Enable core ZIO Metrics by providing `Runtime.enableRuntimeMetrics` to your main application's ZIO effect. This
   ensures that basic ZIO metrics, such as fiber counts and execution times, are collected. Additionally, include
   `DefaultJvmMetrics.live` in your layer composition to enable default JVM metrics collection.

Here's an example of how example may look like:

```scala

object SampleApp extends ZIOAppDefault {

  def program[R, E, T]: ZIO[R, E, T] = ???

  override def run: ZIO[Environment & ZIOAppArgs & Scope, Any, Any] = (for {
    _ <- program
  } yield ())
    .provide(
      micrometer.micrometerLayer,
      ZLayer.succeed(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT)),
      Runtime.enableRuntimeMetrics,
      DefaultJvmMetrics.live.unit,
    )
}

```
