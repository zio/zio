---
id: zio-architectural-patterns
title: "ZIO Architectural Patterns"
---

In this section, we are going to talk about the design elements of a ZIO application and the ZIO idiomatic way of structuring codes to write ZIO applications.

## Onion Architecture

Onion architecture is a software architecture pattern that is used to create loosely coupled, maintainable, and testable applications by layering the application into a set of concentric circles.

- The innermost layer contains the domain model. Its language has the highest level of abstraction.
- From the very center, the language domain model is surrounded successively by other layers, each of which is more technical and has a lower level of abstraction than the previous one.
- The outermost layer contains the final language is the one that is closest to the environment in which the application is running. For example, the outermost layer could be the user interface, a web API, etc.

Onion architecture is based on the _inversion of control_ principle. So each layer is dependent on the underlying layer, but not on the layers above it. This means that the innermost layer is independent of any other layers.

In ZIO by taking advantage of both functional and object-oriented programming, we can implement onion architecture in a very simple and elegant way. To implement this architecture, please refer to the [Writing ZIO Services](../architecture/index.md) section which empowers you to create layers (services) in the onion architecture. In order to assemble all layers and make the whole application work, please refer to the [Dependency Injection In ZIO](../di/index.md) section.

## Sidecar Pattern

The sidecar pattern is a microservice architecture pattern that is used to separate cross-cutting concerns from the main business logic. It is a very useful pattern when we have to deal with concerns like logging, metrics, profiling, monitoring, etc. These concerns are not part of the main service logic, but they are important for the service to work correctly.

In ZIO, we can implement the sidecar pattern by using compositional apps, or by using the `bootstrap` layer.

### Compositional Apps

In the following example, as we have multiple applications (`UserApp` and `DocumentApp`), we use compositional apps to implement this pattern:

```scala mdoc:invisible
import zio._
import zhttp.http._

val userHttpApp: Http[Any, Nothing, Request, Response]     = Http.empty
val documentHttpApp: Http[Any, Nothing, Request, Response] = Http.empty
```

```scala mdoc:compile-only
import zio._
import zhttp.http._
import zhttp.service.Server
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{MetricsConfig, prometheus}

object UserApp extends ZIOAppDefault {
  def run = Server.start(port = 8080, http = userHttpApp)
}

object DocumentApp extends ZIOAppDefault {
  def run = Server.start(port = 8081, http = documentHttpApp)
}

object Metrics extends ZIOAppDefault {
  private val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))

  def run =
    Server
      .start(
        port = 8082,
        http = Http.collectZIO[Request] { case Method.GET -> !! / "metrics" =>
          ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
        }
      )
      .provide(
        metricsConfig,
        prometheus.publisherLayer,
        prometheus.prometheusLayer
      )
}

object MainApp extends ZIOApp.Proxy(UserApp <> DocumentApp <> Metrics)
```

### Bootstrap Layer

If we had only one application, we could use the `bootstrap` layer to implement this pattern:

```scala mdoc:compile-only
import zio._
import zhttp.http._
import zhttp.service.Server
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{MetricsConfig, prometheus}

object MetricsService {
  private val metricsConfig = ZLayer.succeed(MetricsConfig(5.seconds))

  private val exporter: ZLayer[PrometheusPublisher, Nothing, Unit] =
    ZLayer.fromZIO {
      Server
        .start(
          port = 8081,
          http = Http.collectZIO[Request] { case Method.GET -> !! / "metrics" =>
            ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
          }
        )
        .forkDaemon
        .unit
    }

  val layer: ZLayer[Any, Nothing, Unit] =
    ZLayer.make[Unit](
      exporter,
      metricsConfig,
      prometheus.publisherLayer,
      prometheus.prometheusLayer
    )

}

object UserAoo extends ZIOAppDefault {
  override val bootstrap = MetricsService.layer

  def run = Server.start(port = 8080, http = userHttpApp)
}
```
