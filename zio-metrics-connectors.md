# Metrics

> ZIO Metrics allows you to apply special aspects to the workflows of any ZIO based application. This is called
instrumentation. The type of the original ZIO workflow will not change by adding one or more aspects to it. (From here
on, we'll use the word _effect_ instead of workflow.)

ZIO Metrics allows you to apply special aspects to the workflows of any ZIO based application. This is called
instrumentation. The type of the original ZIO workflow will not change by adding one or more aspects to it. (From here
on, we'll use the word _effect_ instead of workflow.)

Whenever an instrumented effect executes, all the aspects will be executed as well. Each of the aspects will capture
some data of interest and update some Metric internal state. Which data will be captured and how it can be used later on
is dependent on the metric type associated with the aspect. Capturing metrics data is usually called 'measuring'.

Metrics are normally collected to be displayed in an application like [Grafana](https://grafana.com/) or a cloud based
platform like [DatadogHQ](https://docs.datadoghq.com/) or [NewRelic](https://newrelic.com).
In order to support such a range of different platforms, the metric state is kept in an internal data structure which is
optimized to update as efficiently as possible. The data required by the collecting platforms is generated only when it
is required.

ZIO Metrics Connectors also allows us to dump current metric states (in one of the next minor releases) out of the box
to analyze the metrics in the development phase before the decision for a metric platform has been made or in cases when
the platform might not be feasible to use in development.

> Changing which platform the metrics are reported to has no impact on the application at all. Once instrumented
> properly, the decision where to report to happens 'at the end of the world', that is, in the ZIO application's main
> entry point, by providing one or more of the available reporting clients.

Currently, ZIO Metrics Connectors provides integrations with the following reporting clients:
* [Prometheus](https://prometheus.io/)
* [Datadog](https://docs.datadoghq.com/)
* [New Relic](https://newrelic.com)
* [StatsD](https://github.com/statsd/statsd)
* [Micrometer](https://micrometer.io)

### Adding ZIO Metrics Connectors to your project

Import the corresponding dependency based on your reporting infrastructure:
```sbt
libraryDependencies ++= {
  Seq(
    "dev.zio" %% "zio-metrics-connectors"             % "2.5.4", // core library
    "dev.zio" %% "zio-metrics-connectors-prometheus"  % "2.5.4", // Prometheus client
    "dev.zio" %% "zio-metrics-connectors-datadog"     % "2.5.4", // DataDog client
    "dev.zio" %% "zio-metrics-connectors-newrelic"    % "2.5.4", // NewRelic client
    "dev.zio" %% "zio-metrics-connectors-statsd"      % "2.5.4", // StatsD client
    "dev.zio" %% "zio-metrics-connectors-micrometer"  % "2.5.4"  // Micrometer client
  )
}
```

Please refer to:

* [Metrics Reference](metric-reference.md) for more information on the metrics currently supported
* [Prometheus Client](prometheus-client.md) to learn more about the mapping from ZIO Metrics to Prometheus
* [StatsD Client](statsd-client.md) to learn more about the mapping from ZIO Metrics to StatsD
* [DataDog Client](datadog-client.md) to learn more about the mapping from ZIO Metrics to DataDog
* [Micrometer](micrometer-connector.md) to learn more about the mapping from ZIO Metrics to Micrometer
