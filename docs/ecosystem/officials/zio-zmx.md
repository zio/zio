---
id: zio-zmx
title: "ZIO ZMX"
---

[ZIO ZMX](https://github.com/zio/zio-zmx) is a monitoring, metrics, and diagnostics toolkit for ZIO applications.

## Introduction

So ZIO ZMX is giving us a straightforward way to understand exactly what is going on in our ZIO application when we deploy that in production.

ZIO ZMX key features:

- **Easy Setup** — It seamlessly integrates with an existing application. We don't need to change any line of the existing ZIO application, except a few lines of code at the top level.
- **Diagnostics** —  To track the activity of fibers in a ZIP application including fiber lifetimes and reason for termination.
- **Metrics** — Tracking of user-defined metrics (Counter, Gauge, Histogram, etc.)
- **Integrations** — Support for major metrics collection services including _[Prometheus](https://github.com/prometheus/prometheus)_ and _[StatsD](https://github.com/statsd/statsd)_.
- **Zero Dependencies** - No dependencies other than ZIO itself.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-zmx" % "0.0.6"
```

## Example

To run this example, we also should add the following dependency in our `build.sbt` file:

```scala
libraryDependencies += "org.polynote" %% "uzhttp" % "0.2.7"
```

In this example, we expose metric information using _Prometheus_ protocol:

```scala
import uzhttp._
import uzhttp.server.Server
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console._
import zio.duration.durationInt
import zio.zmx.metrics._
import zio.zmx.prometheus.PrometheusClient

import java.io.IOException
import java.lang
import java.net.InetSocketAddress

object ZmxSampleApp extends zio.App {

  val myApp: ZIO[Console with Clock with Has[PrometheusClient] with Blocking, IOException, Unit] =
    for {
      server <-
        Server
          .builder(new InetSocketAddress("localhost", 8080))
          .handleSome { case request if request.uri.getPath == "/" =>
            PrometheusClient.snapshot.map(p => Response.plain(p.value))
          }
          .serve
          .use(_.awaitShutdown).fork
      program <-
        (for {
          _ <- (ZIO.sleep(1.seconds) *> request @@ MetricAspect.count("request_counts")).forever.forkDaemon
          _ <- (ZIO.sleep(3.seconds) *>
            ZIO.succeed(
              lang.Runtime.getRuntime.totalMemory() - lang.Runtime.getRuntime.freeMemory()
            ).map(_ / (1024.0 * 1024.0)) @@ MetricAspect.setGauge("memory_usage")).forever.forkDaemon
        } yield ()).fork
      _ <- putStrLn("Press Any Key") *> getStrLn.catchAll(_ => ZIO.none) *> server.interrupt *> program.interrupt
    } yield ()

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    myApp.provideCustom(PrometheusClient.live).exitCode

  private def request: UIO[Unit] = ZIO.unit
}
```

By calling the following API we can access metric information:

```bash
curl -X GET localhost:8080
```

Now we can config the Prometheus server to scrape metric information periodically.

## Resources

- [ZIO WORLD - ZIO ZMX - Development Testing, Deployment](https://www.youtube.com/watch?v=VM_8ByPuAcI) by Adam Fraser (May 2021) — Adam Fraser went on to present the state of ZMX, showing how metrics Iadded to the runtime system will enable rich real-time analytics out of any ZIO app with zero coding, including fiber lifetimes, fiber failure analysis, fiber counts, & much more (StatsD/Promethus/etc).
