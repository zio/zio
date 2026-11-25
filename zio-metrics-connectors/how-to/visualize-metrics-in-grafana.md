# How to Visualize Prometheus Metrics in Grafana

> This guide shows how to scrape an application that serves Prometheus metrics, and how to chart them in Grafana with the dashboard from the repository. It assumes that your application already serves the metrics at `http://localhost:8080/metrics`, as described in [How to Serve Prometheus Metrics over HTTP](serve-prometheus-metrics.md). You also need a checkout of the repository, which this guide refers to as `$DIR`.

## Run Prometheus

[Download Prometheus](https://prometheus.io/download/) and extract it to a directory that this guide refers to as `$PROMDIR`. The repository has a sample configuration, `examples/prometheus/promcfg.yml`, with a job named `zio` that polls `localhost:8080` every 10 seconds. Prometheus reads the default path, `/metrics`. Start Prometheus with it:

```
cd $PROMDIR
./prometheus --config.file $DIR/examples/prometheus/promcfg.yml
```

If your application serves the metrics on another host, port, or path, edit the `targets` of the `zio` job in the configuration file, and add a `metrics_path` for a path other than `/metrics`.

## Run Grafana

[Download Grafana](https://grafana.com/grafana/download) and start its server. Then open `http://localhost:3000`. The default user is `admin` with the password `admin`, and Grafana asks you to change the password at the first login.

Add Prometheus as a data source of Grafana. Use the address of the Prometheus server, which is `http://localhost:9090` by default.

## Import the Example Dashboard

The repository has a dashboard for the metrics of the example application. To import it:

1. In the Grafana menu, open **Dashboards** and select **Import**.
2. Paste the content of `examples/prometheus/ZIOMetricsDashboard.json` into the text field and select **Load**.
3. Import the dashboard and open it from the dashboard list.

![A simple Grafana Dashboard](../img/ZIOMetrics-Grafana.png)

The dashboard queries the metric names of the example application. Rename the metrics in your own application to match the queries, or edit the queries.

For the setup of Prometheus and Grafana beyond this example, see their own documentation.
