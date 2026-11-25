# Introduction to ZIO Metrics Connectors

> ZIO Metrics Connectors provides integration between ZIO metrics and industry-leading monitoring and observability systems such as
>   * [Prometheus](https://prometheus.io/)
>   * [Datadog](https://docs.datadoghq.com/)
>   * [New Relic](https://newrelic.com)
>   * [StatsD](https://github.com/statsd/statsd)
>   * [Micrometer](https://micrometer.io)

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-metrics-connectors/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-metrics-connectors_3.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-metrics-connectors_3) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-metrics-connectors_3%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-metrics-connectors_3/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-metrics-connectors_3/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-metrics-connectors_3) [![ZIO Metrics Connectors](https://img.shields.io/github/stars/zio/zio-metrics-connectors?style=social)](https://github.com/zio/zio-metrics-connectors)

## Installation

Add the core library to your `build.sbt`. All modules are published for Scala 2.12, Scala 2.13 and Scala 3, and run on the JVM only:

```sbt
libraryDependencies ++= Seq(
  // Core library: connector settings and the shared metrics client (JVM)
  "dev.zio" %% "zio-metrics-connectors" % "2.6.1"
)
```

Then add the connector for your monitoring backend, as described in [Modules](#modules).

## Modules

Each connector is a separate module. Add only the ones that you use:

```sbt
libraryDependencies ++= Seq(
  // Prometheus: encodes metrics as Prometheus text for you to serve over HTTP (JVM)
  "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.6.1",

  // StatsD: sends datagrams over UDP or a Unix domain socket (JVM)
  "dev.zio" %% "zio-metrics-connectors-statsd" % "2.6.1",

  // Datadog: sends DogStatsD datagrams through a StatsD client, also adds `zio-metrics-connectors-statsd` (JVM)
  "dev.zio" %% "zio-metrics-connectors-datadog" % "2.6.1",

  // New Relic: sends metrics to the New Relic Metric API over HTTPS, also adds `zio-http` and `zio-json` (JVM)
  "dev.zio" %% "zio-metrics-connectors-newrelic" % "2.6.1",

  // Micrometer: forwards every metric update to a Micrometer `MeterRegistry` (JVM)
  "dev.zio" %% "zio-metrics-connectors-micrometer" % "2.6.1"
)
```

Each module depends on the core library, so you do not need to list it separately. Learn more about each module in its
reference page: [Prometheus](reference/prometheus-client.md), [StatsD](reference/statsd-client.md),
[Datadog](reference/datadog-client.md), [New Relic](reference/new-relic-client.md), and
[Micrometer](reference/micrometer-connector.md).

To pick a connector, see [How to Choose a Connector](how-to/choose-a-connector.md). To instrument an application and see
its metrics in Prometheus, follow the [tutorial](tutorial/instrument-and-scrape.md).
