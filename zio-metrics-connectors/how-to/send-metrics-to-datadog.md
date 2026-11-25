# How to Send Metrics to Datadog

> This guide shows how to run a Datadog agent in Docker, send the metrics of a ZIO application to it over a Unix domain
> socket, and import a ready-made dashboard. You need a [Datadog](https://www.datadoghq.com/) account and its API key,
> which this guide refers to as `$APIKEY`, and Docker on a Linux host. The Datadog agent accepts datagrams on a Unix
> domain socket starting with version 6.0.0.

## Start the Datadog Agent

The application writes to a socket file, so the agent and the application must share a directory. Create the directory,
then start the agent with that directory mounted and `DD_DOGSTATSD_SOCKET` pointing at a socket inside it. Use the
`DD_SITE` of your account, such as `datadoghq.com` or `datadoghq.eu`:

```
mkdir -p /var/run/datadog

docker run --name dd-agent -e DD_API_KEY=$APIKEY -e DD_SITE=datadoghq.eu \
  -e DD_DOGSTATSD_SOCKET=/var/run/datadog/datadog.sock \
  -v /var/run/datadog:/var/run/datadog \
  -v /var/run/docker.sock:/var/run/docker.sock:ro \
  datadog/agent
```

The agent creates the socket file when it starts. Keep it running while you test.

## Send the Metrics over the Socket

Add the Datadog module to your build, as listed in the [Introduction](../index.md#modules). Then provide the
`statsd.statsdUDS` layer, which sends to the socket, together with `datadog.live`. The following application counts a
request every second and reports it to the socket that the agent created:

```scala
import zio._
import zio.metrics.Metric
import zio.metrics.connectors.{datadog, statsd, MetricsConfig}

object DatadogSocketApp extends ZIOAppDefault {

  val requests: Metric.Counter[Any] =
    Metric.counter("dd_requests", "Requests handled").fromConst(1L)

  val program: UIO[Unit] =
    (ZIO.unit @@ requests *> ZIO.sleep(1.second)).forever

  override def run =
    program.provide(
      ZLayer.succeed(MetricsConfig(10.seconds)),
      ZLayer.succeed(statsd.DatagramSocketConfig("/var/run/datadog/datadog.sock")),
      ZLayer.succeed(datadog.DatadogPublisherConfig()),
      statsd.statsdUDS,
      datadog.live,
    )
}
```

Read the [Datadog connector page](../reference/datadog-client.md) before you run this combination, because it explains
what each layer reports and a known duplication of datagrams.

If your agent listens on UDP instead of a socket, use `statsd.statsdUDP` with a `StatsdConfig`, as shown on the
connector page. To forward UDP datagrams from a host port to the socket, you can run `socat` next to the agent:

```
sudo socat -s -u udp-recv:8125 unix-sendto:/var/run/datadog/datadog.sock
```

Add `-v` to print the traffic that `socat` forwards, which shows whether datagrams are sent.

## Import the Example Dashboard

The repository has a dashboard that charts the metrics of the example application. To import it:

1. Log in to Datadog.
2. Select **Dashboards** and then **New Dashboard**.
3. In the upper right corner, open the dashboard settings and select **Import Dashboard JSON**.
4. Choose the file `examples/statsd/ZIOMetrics.json` from a checkout of the repository.
5. Confirm that you want to override the dashboard configuration, then save the dashboard.
6. Open **ZIO metrics** from the dashboard list.

![A simple Datadog Dashboard](../img/Datadog.png)

The dashboard shows data for the metric names that the example application records. Rename the metrics in your own
application to match the dashboard queries, or edit the queries.
