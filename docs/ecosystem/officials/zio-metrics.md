---
id: zio-metrics
title: "ZIO Metrics"
---

[ZIO Metrics](https://github.com/zio/zio-metrics) is a high-performance, purely-functional library for adding instrumentation to any application, with a simple web client and JMX support.

## Introduction

ZIO Metrics is a pure-ZIO StatsD/DogStatsD client and a thin wrapper over both _[Prometheus](https://github.com/prometheus/client_java)_ and _[Dropwizard](https://metrics.dropwizard.io/4.2.0/manual/core.html)_ instrumentation libraries allowing us to measure the behavior of our application in a performant purely functional manner.

## Installation

In order to use this library, we need to one of the following lines in our `build.sbt` file:

```scala
// Prometheus
libraryDependencies += "dev.zio" %% "zio-metrics-prometheus" % "1.0.12"

// Dropwizard
libraryDependencies += "dev.zio" %% "zio-metrics-dropwizard" % "1.0.12"

// StatsD/DogStatsD
libraryDependencies += "dev.zio" %% "zio-metrics-statsd" % "1.0.12"
```

## Example

In this example we are using `zio-metrics-prometheus` module. Other that initializing default exporters, we register a counter to the registry:

```scala
import zio.Runtime
import zio.console.{Console, putStrLn}
import zio.metrics.prometheus._
import zio.metrics.prometheus.exporters._
import zio.metrics.prometheus.helpers._

object ZIOMetricsExample extends scala.App {

  val myApp =
    for {
      r <- getCurrentRegistry()
      _ <- initializeDefaultExports(r)
      c <- counter.register("ServiceA", Array("Request", "Region"))
      _ <- c.inc(1.0, Array("GET", "us-west-*"))
      _ <- c.inc(2.0, Array("POST", "eu-south-*"))
      _ <- c.inc(3.0, Array("GET", "eu-south-*"))
      s <- http(r, 9090)
      _ <- putStrLn(s"The application's metric endpoint: http://localhost:${s.getPort}/")
    } yield s

  Runtime
    .unsafeFromLayer(
      Registry.live ++ Exporters.live ++ Console.live
    )
    .unsafeRun(myApp)
}
```

Now, the application's metrics are accessible via `http://localhost:9090` endpoint.
