# OpenTelemetry Example

> You can find the source code [here](https://github.com/zio/zio-telemetry/tree/series/2.x/opentelemetry-example).

You can find the source code [here](https://github.com/zio/zio-telemetry/tree/series/2.x/opentelemetry-example).

For an explanation in more detail, check the [OpenTracing Example](opentracing-example.md).

We're going to show an example of how to pass contextual information using [Baggage](https://opentelemetry.io/docs/concepts/signals/baggage/) and collect traces, metrics, and logs.

# Run

## Print OTEL signals to console

By default the example code uses [OTLP Logging Exporters](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/logging-otlp) to print all signals to stdout in OTLP JSON encoding. This means that you can run the application immediately and observe the results.

For this, you need to run proxy and backend parts of application in different terminals via sbt.

Run proxy:
```bash
sbt "opentelemetryExample/runMain zio.telemetry.opentelemetry.example.ProxyApp"
```
Run backend:
```bash
sbt "opentelemetryExample/runMain zio.telemetry.opentelemetry.example.BackendApp"
```
Now perform the following request to see the results immediately:
```bash
curl -X GET http://localhost:8080/statuses
```

## Publish OTEL signals to other observability platforms

In case you want to try different observability platforms such as [Jaeger](https://www.jaegertracing.io/), [Fluentbit](https://fluentbit.io/), [Seq](https://datalust.co/seq), [DataDog](https://www.datadoghq.com/), [Honeycomb](https://www.honeycomb.io/) or others, please change the [OtelSdk.scala](https://github.com/zio/zio-telemetry/blob/series/2.x/opentelemetry-example/src/main/scala/zio/telemetry/opentelemetry/example/otel/OtelSdk.scala) file by choosing from the available tracer, meter, and logger providers or by implementing your own. 

## Publish OTEL signals to Jaeger and Seq

We chose [Jaeger](https://www.jaegertracing.io/) for distributed traces and [Seq](https://datalust.co/seq) to store logs to demonstrate how the library works with available open-source observability platforms.

Start Jaeger by running the following command:
```bash
docker run --rm -it \
  -d \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 4317:4317 \
  -p 16686:16686 \
  jaegertracing/all-in-one:1.47
```

To run Seq, you also need to specify an admin password (user is `admin`):
```bash
PH=$(echo 'admin123' | docker run --rm -i datalust/seq config hash)

docker run \
  -d \
  --restart unless-stopped \
  -e ACCEPT_EULA=Y \
  -e SEQ_FIRSTRUN_ADMINPASSWORDHASH="$PH" \
  -p 80:80 \
  -p 5341:5341 \
  datalust/seq
```

You must also switch the Tracing and Logging providers to Jaeger and Seq. For this, you need to swap the `stdout` providers we use by default in [OtelSdk.scala](https://github.com/zio/zio-telemetry/blob/series/2.x/opentelemetry-example/src/main/scala/zio/telemetry/opentelemetry/example/otel/OtelSdk.scala#L10) to [TracerProvider.jaeger](https://github.com/zio/zio-telemetry/blob/series/2.x/opentelemetry-example/src/main/scala/zio/telemetry/opentelemetry/example/otel/TracerProvider.scala#L37) and [LoggerProvider.seq](https://github.com/zio/zio-telemetry/blob/series/2.x/opentelemetry-example/src/main/scala/zio/telemetry/opentelemetry/example/otel/LoggerProvider.scala#L36)

Run the application and fire a curl request as shown above. Head over to [Jaeger UI](http://localhost:16686/) and [Seq UI](http://localhost:80/) to see the result.
