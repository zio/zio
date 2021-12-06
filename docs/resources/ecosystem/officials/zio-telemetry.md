---
id: zio-telemetry
title: "ZIO Telemetry"
---

[ZIO telemetry](https://github.com/zio/zio-telemetry) is purely-functional and type-safe. It provides clients for OpenTracing and OpenTelemetry.

## Introduction

In monolithic architecture, everything is in one place, and we know when a request starts and then how it goes through the components and when it finishes. We can obviously see what is happening with our request and where is it going. But, in distributed systems like microservice architecture, we cannot find out the story of a request through various services easily. This is where distributed tracing comes into play.

ZIO Telemetry is a purely functional client which helps up propagate context between services in a distributed environment.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file if we want to use [OpenTelemetry](https://opentelemetry.io/) client:

```scala
libraryDependencies += "dev.zio" %% "zio-telemetry" % "0.8.1"
```

And for using [OpenTracing](https://opentracing.io/) client we should add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-opentracing" % "0.8.1"
```

## Example

In this example, we create two services, `ProxyServer` and `BackendServer`. When we call ProxyServer, the BackendServer will be called.

Note that we are going to use _OpenTracing_ client for this example.

Here is a simplified diagram of our services:

```
                               ┌────────────────┐
                               │                │
                        ┌─────►│ Jaeger Backend │◄────┐
                        │      │                │     │
           Tracing Data │      └────────────────┘     │ Tracing Data
                        │                             │
               ┌────────┴─────────┐         ┌─────────┴────────┐
               │                  │         │                  │
User Request──►│   Proxy Server   ├────────►|  Backend Server  │
               │                  │         │                  │
               └──────────────────┘         └──────────────────┘
```

First of all we should add following dependencies to our `build.sbt` file:

```scala
object Versions {
  val http4s         = "0.21.24"
  val jaeger         = "1.6.0"
  val sttp           = "2.2.9"
  val opentracing    = "0.33.0"
  val opentelemetry  = "1.4.1"
  val opencensus     = "0.28.3"
  val zipkin         = "2.16.3"
  val zio            = "1.0.9"
  val zioInteropCats = "2.5.1.0"
}

lazy val openTracingExample = Seq(
  "org.typelevel"                %% "cats-core"                     % "2.6.1",
  "io.circe"                     %% "circe-generic"                 % "0.14.1",
  "org.http4s"                   %% "http4s-core"                   % Versions.http4s,
  "org.http4s"                   %% "http4s-blaze-server"           % Versions.http4s,
  "org.http4s"                   %% "http4s-dsl"                    % Versions.http4s,
  "org.http4s"                   %% "http4s-circe"                  % Versions.http4s,
  "io.jaegertracing"              % "jaeger-core"                   % Versions.jaeger,
  "io.jaegertracing"              % "jaeger-client"                 % Versions.jaeger,
  "io.jaegertracing"              % "jaeger-zipkin"                 % Versions.jaeger,
  "com.github.pureconfig"        %% "pureconfig"                    % "0.16.0",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % Versions.sttp,
  "com.softwaremill.sttp.client" %% "circe"                         % Versions.sttp,
  "dev.zio"                      %% "zio-interop-cats"              % Versions.zioInteropCats,
  "io.zipkin.reporter2"           % "zipkin-reporter"               % Versions.zipkin,
  "io.zipkin.reporter2"           % "zipkin-sender-okhttp3"         % Versions.zipkin
)
```

Let's create a `ZLayer` for `OpenTracing` which provides us Jaeger tracer. Each microservice uses this dependency to send its tracing data to the _Jaeger Backend_:

```scala
import io.jaegertracing.Configuration
import io.jaegertracing.internal.samplers.ConstSampler
import io.jaegertracing.zipkin.ZipkinV2Reporter
import org.apache.http.client.utils.URIBuilder
import zio.ZLayer
import zio.clock.Clock
import zio.telemetry.opentracing.OpenTracing
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.okhttp3.OkHttpSender

object JaegerTracer {
  def makeJaegerTracer(host: String, serviceName: String): ZLayer[Clock, Throwable, Clock with OpenTracing] =
    OpenTracing.live(new Configuration(serviceName)
      .getTracerBuilder
      .withSampler(new ConstSampler(true))
      .withReporter(
        new ZipkinV2Reporter(
          AsyncReporter.create(
            OkHttpSender.newBuilder
              .compressionEnabled(true)
              .endpoint(
                new URIBuilder()
                  .setScheme("http")
                  .setHost(host)
                  .setPath("/api/v2/spans")
                  .build.toString
              )
              .build
          )
        )
      )
      .build
    ) ++ Clock.live
}
```

The _BackendServer_:

```scala
import io.opentracing.propagation.Format.Builtin.{HTTP_HEADERS => HttpHeadersFormat}
import io.opentracing.propagation.TextMapAdapter
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import zio.clock.Clock
import zio.interop.catz._
import zio.telemetry.opentracing._
import JaegerTracer.makeJaegerTracer
import zio.{ExitCode, ZEnv, ZIO}

import scala.jdk.CollectionConverters._

object BackendServer extends CatsApp {
  type AppTask[A] = ZIO[Clock, Throwable, A]

  val dsl: Http4sDsl[AppTask] = Http4sDsl[AppTask]
  import dsl._

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    ZIO.runtime[Clock].flatMap { implicit runtime =>
      BlazeServerBuilder[AppTask](runtime.platform.executor.asEC)
        .bindHttp(port = 9000, host = "0.0.0.0")
        .withHttpApp(
          Router[AppTask](mappings = "/" ->
            HttpRoutes.of[AppTask] { case request@GET -> Root =>
              ZIO.unit
                .spanFrom(
                  format = HttpHeadersFormat,
                  carrier = new TextMapAdapter(request.headers.toList.map(h => h.name.value -> h.value).toMap.asJava),
                  operation = "GET /"
                )
                .provide(makeJaegerTracer(host = "0.0.0.0:9411", serviceName = "backend-service")) *> Ok("Ok!")
            }
          ).orNotFound
        )
        .serve
        .compile
        .drain
    }.exitCode
}
```

And the _ProxyServer_ which calls the _BackendServer_:

```scala
import cats.effect.{ExitCode => catsExitCode}
import io.opentracing.propagation.Format.Builtin.{HTTP_HEADERS => HttpHeadersFormat}
import io.opentracing.propagation.TextMapAdapter
import io.opentracing.tag.Tags
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client.basicRequest
import sttp.model.Uri
import zio.clock.Clock
import zio.interop.catz._
import zio.telemetry.opentracing.OpenTracing
import JaegerTracer.makeJaegerTracer
import zio.{ExitCode, UIO, ZEnv, ZIO}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object ProxyServer extends CatsApp {

  type AppTask[A] = ZIO[Clock, Throwable, A]

  private val backend = AsyncHttpClientZioBackend()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    ZIO.runtime[Clock].flatMap { implicit runtime =>
      implicit val ec = runtime.platform.executor.asEC
      BlazeServerBuilder[AppTask](ec)
        .bindHttp(port = 8080, host = "0.0.0.0")
        .withHttpApp(
          Router[AppTask](mappings = "/" -> {
            val dsl: Http4sDsl[AppTask] = Http4sDsl[AppTask]
            import dsl._

            HttpRoutes.of[AppTask] { case GET -> Root =>
              (for {
                _ <- OpenTracing.tag(Tags.SPAN_KIND.getKey, Tags.SPAN_KIND_CLIENT)
                _ <- OpenTracing.tag(Tags.HTTP_METHOD.getKey, GET.name)
                _ <- OpenTracing.setBaggageItem("proxy-baggage-item-key", "proxy-baggage-item-value")
                buffer = new TextMapAdapter(mutable.Map.empty[String, String].asJava)
                _ <- OpenTracing.provide(HttpHeadersFormat, buffer)
                headers <- extractHeaders(buffer)
                res <-
                  backend.flatMap { implicit backend =>
                    basicRequest.get(Uri("0.0.0.0", 9000).path("/")).headers(headers).send()
                  }.map(_.body)
                    .flatMap {
                      case Right(_) => Ok("Ok!")
                      case Left(_) => Ok("Oops!")
                    }
              } yield res)
                .root(operation = "GET /")
                .provide(
                  makeJaegerTracer(host = "0.0.0.0:9411", serviceName = "proxy-server")
                )
            }
          }).orNotFound
        )
        .serve
        .compile[AppTask, AppTask, catsExitCode]
        .drain
        .as(ExitCode.success)
    }.exitCode

  private def extractHeaders(adapter: TextMapAdapter): UIO[Map[String, String]] = {
    val m = mutable.Map.empty[String, String]
    UIO(adapter.forEach { entry =>
      m.put(entry.getKey, entry.getValue)
      ()
    }).as(m.toMap)
  }

}
```

First, we run the following command to start Jaeger backend:

```bash
docker run -d --name jaeger \
  -e COLLECTOR_ZIPKIN_HTTP_PORT=9411 \
  -p 5775:5775/udp \
  -p 6831:6831/udp \
  -p 6832:6832/udp \
  -p 5778:5778 \
  -p 16686:16686 \
  -p 14268:14268 \
  -p 9411:9411 \
  jaegertracing/all-in-one:1.6
```

It's time to run Backend and Proxy servers. After starting these two, we can start calling `ProxyServer`:

```scala
curl -X GET http://0.0.0.0:8080/
```

Now we can check the Jaeger service (http://localhost:16686/) to see the result.

## Articles

- [Trace your microservices with ZIO](https://kadek-marek.medium.com/trace-your-microservices-with-zio-telemetry-5f88d69cb26b) by Marek Kadek (September 2021)

