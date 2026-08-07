# Introduction to ZIO Telemetry

> [ZIO telemetry](https://github.com/zio/zio-telemetry) is purely-functional and type-safe. It provides clients for
[OpenTracing](https://opentracing.io/), [OpenCensus](https://opencensus.io/) and [OpenTelemetry](https://opentelemetry.io/).

[ZIO telemetry](https://github.com/zio/zio-telemetry) is purely-functional and type-safe. It provides clients for
[OpenTracing](https://opentracing.io/), [OpenCensus](https://opencensus.io/) and [OpenTelemetry](https://opentelemetry.io/).

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-telemetry/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-opentracing_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-opentracing_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-opentracing_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-opentracing_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-telemetry-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-telemetry-docs_2.13) [![ZIO Telemetry](https://img.shields.io/github/stars/zio/zio-telemetry?style=social)](https://github.com/zio/zio-telemetry)

ZIO Telemetry consists of the following projects:

- [OpenTracing](opentracing.md)
- [OpenCensus](opencensus.md)
- [OpenTelemetry](opentelemetry.md)

## Introduction

In monolithic architecture, everything is in one place, and we know when a request starts and then how it goes through 
the components and when it finishes. We can obviously see what is happening with our request and where is it going. 
But, in distributed systems like microservice architecture, we cannot find out the story of a request through various 
services easily. This is where distributed tracing comes into play.

ZIO Telemetry is a purely functional client which helps up propagate context between services in a distributed environment.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file if we want to use [OpenTelemetry](https://opentelemetry.io/) client:

```scala
libraryDependencies += "dev.zio" %% "zio-opentelemetry" % "<version>"
```

If you're using [ZIO Logging](https://github.com/zio/zio-logging) you can combine OpenTelemetry with ZIO Logging using:

```scala
libraryDependencies += "dev.zio" %% "zio-opentelemetry-zio-logging" % "<version>"
```

For using [OpenTracing](https://opentracing.io/) client we should add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-opentracing" % "<version>"
```

And for using [OpenCensus](https://opencensus.io/) client we should add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-opencensus" % "<version>"
```

## Examples

You can find examples with full source code and instructions of how to run by following the links:
- [OpenTelemetry Example](opentelemetry-example.md)
- [OpenTelemetry Instrumentation Example](opentelemetry-instrumentation-example.md)
- [OpenTelemetry ZIO Logging Example](opentelemetry-zio-logging.md)
- [OpenTracing Example](opentracing-example.md)

## Articles

- [Trace your microservices with ZIO](https://kadek-marek.medium.com/trace-your-microservices-with-zio-telemetry-5f88d69cb26b) by Marek Kadek (September 2021)
