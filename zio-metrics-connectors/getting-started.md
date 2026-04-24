# Getting Started

> ZIO Metrics lets you observe everything that goes on in your ZIO application.

ZIO Metrics lets you observe everything that goes on in your ZIO application.

Tests are important for verifying the correctness of the code we write. However, when we go into production conditions
are inevitably different than in testing. Still, we do need to know what is going on in our application in real time so
that we can diagnose and address problems.

Logging is part of the solution, but it is not enough. We either only log in certain areas, which are often not the
areas where an issue actually occurs, or we log everywhere, significantly impacting application performance.

Diagnostics and metrics are the other pieces of the puzzle, but there has not been a solution for integrating existing
metrics backends with functional effect systems before, forcing users to develop their own workarounds. These solutions
are not connected with the runtime of the effect system, so they have no ability to provide insights on what is going
on at the level of the runtime such as the activity of individual fibers.

ZIO Metrics solves this problem. ZIO Metrics can be added to any application with only a few lines of code and provides
two types of insights:

- **Diagnostics** — Diagnostics are pre-defined data on the behavior of the application as it is running directly by the
  ZIO runtime, such as the number of fibers in the application, distribution of fiber lifetimes, and a breakdown of
  reasons for fiber termination.
- **[Metrics](metrics/metric-reference.md)** — Metrics are used-defined data, such as the number of times a certain
  section of code was executed, that are tracked by ZIO Metrics and made available either directly or through third
  party metrics solutions such as Prometheus, StatsD or Micrometer.

See the corresponding sections for more information on how to use each of these pieces of functionality.

## Installation

Choose and include ZIO Metrics connector in your project by adding the following to your `build.sbt` (for example
prometheus connector):

```
libraryDependencies += "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.5.4"
```
