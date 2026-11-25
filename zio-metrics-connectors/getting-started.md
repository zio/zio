# Getting Started

> ZIO Metrics Connectors reports the metrics that ZIO records, both your own and those of the ZIO runtime and the JVM, to Prometheus, StatsD, Datadog, New Relic, or Micrometer. The core library keeps the metric state, and a connector reads it and sends or serves it. See [How It Works](how-it-works.md) for details, and [How to Report ZIO Runtime and JVM Metrics](how-to/report-runtime-and-jvm-metrics.md) for the runtime and JVM metrics.

To get started, add the dependencies from [Installation](#installation), then follow the [tutorial](tutorial/instrument-and-scrape.md).

## Installation

Add the core library and the connector for your monitoring backend to your `build.sbt`. The [Introduction](index.md#installation)
lists the dependency line for each module.

## Next Steps

- To instrument an application and see its metrics in Prometheus, follow the [tutorial](tutorial/instrument-and-scrape.md).
- To pick a connector, see [How to Choose a Connector](how-to/choose-a-connector.md).
- To see how the connectors read the metrics, see [How It Works](how-it-works.md).
