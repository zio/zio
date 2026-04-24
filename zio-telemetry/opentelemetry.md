# OpenTelemetry

> OpenTelemetry is a collection of tools, APIs, and SDKs. You can use it to instrument, generate, collect, and export telemetry data for analysis in order to understand your software's performance and behavior. Well known implementations are [Jaeger](https://www.jaegertracing.io) and [Zipkin](https://www.zipkin.io).

OpenTelemetry is a collection of tools, APIs, and SDKs. You can use it to instrument, generate, collect, and export telemetry data for analysis in order to understand your software's performance and behavior. Well known implementations are [Jaeger](https://www.jaegertracing.io) and [Zipkin](https://www.zipkin.io).

The library provides an idiomatic ZIO 2.0 interface to OpenTelemetry, ensuring seamless interoperability with the native ZIO capabilities and beyond.

Some of the key features:

- **ZIO native** - Pleasant API that leverages native ZIO features, such as [Resource Management](https://zio.dev/reference/resource/), [Depenency Injection](https://zio.dev/reference/di/), [Streaming](https://zio.dev/reference/stream/), [Logging](https://zio.dev/reference/observability/logging), [Metrics](https://zio.dev/reference/observability/metrics/), and [ZIO Aspect](https://zio.dev/reference/core/zio/#zio-aspect)
- **OpenTelemetry Java SDK and ZIO Runtime interoperability** - Protecting users from directly engaging in OTEL context manipulations, offering a straightforward and clear interface for instrumenting spans, metrics, logs, and baggage. In this scenario, the ZIO effect serves as the span's scope.
- **Seamless signals correlation** -  Automatically correlates spans, metrics, and logs with a surrounding span.
- **Integration with ZIO capabilities** - Propagation of log annotations, metrics, and other data from the ZIO runtime as OTEL attributes and metric signals.

## Installation

Add the following dependency to your `build.sbt` to use OpenTelemetry inside your ZIO application:

```scala
"dev.zio" %% "zio-opentelemetry" % "<version>"
```

You will also need SDK dependencies to be able to provide configured instances of [Tracer](https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/io/opentelemetry/api/trace/Tracer.html), [Meter](https://javadoc.io/static/io.opentelemetry/opentelemetry-api/1.33.0/io/opentelemetry/api/metrics/Meter.html), and [Logger](https://javadoc.io/doc/io.opentelemetry/opentelemetry-api/latest/io/opentelemetry/api/logs/Logger.html), such as:

```scala
"io.opentelemetry"         % "opentelemetry-sdk"           % <opentelemetry-java-version>
"io.opentelemetry"         % "opentelemetry-exporter-otlp" % <opentelemetry-java-version>
"io.opentelemetry.semconv" % "opentelemetry-semconv"       % <opentelemetry-java-version>
```

For the complete list of available Java artifacts, please consult the information available at the [link](https://github.com/open-telemetry/opentelemetry-java#releases)

## Usage

All examples below can be run using amazing [Scala CLI](https://scala-cli.virtuslab.org/). You can find their full copies in the `scala-cli/opentelemetry/` directory. To run, type `scala-cli <AppName>.scala` while in the directory where the file is located.

### Setup

The `zio.telemetry.opentelemetry.OpenTelemetry` (aka entry point) offers a comprehensive set of layers for instrumenting your ZIO application. 

First of all, you need to provide an instance of `io.opentelemetry.api.Opentelemetry`.
In case you don't need an automatic instrumentation, you can use `OpenTelemetry.custom` layer. It receives a scoped ZIO effect indicating that the provided instance will be closed when the application is shut down. Here is an example:

```scala

def custom(resourceName: String): TaskLayer[api.OpenTelemetry] =
  OpenTelemetry.custom(
    for {
      tracerProvider <- TracerProvider.stdout(resourceName)
      meterProvider  <- MeterProvider.stdout(resourceName)
      loggerProvider <- LoggerProvider.stdout(resourceName)
      openTelemetry  <- ZIO.fromAutoCloseable(
                          ZIO.succeed(
                            OpenTelemetrySdk
                              .builder()
                              .setTracerProvider(tracerProvider)
                              .setMeterProvider(meterProvider)
                              .setLoggerProvider(loggerProvider)
                              .build
                          )
                        )
    } yield openTelemetry
  )
```

The library depends only on `opentelemetry-api` which means you have to manage an initialization of providers and depenendencies for `opentelemetry-sdk`, and `opentelemetry-exporter-*` inside your application.

For more details, please have a look at the source code of the [example application](https://github.com/zio/zio-telemetry/tree/series/2.x/opentelemetry-example/src/main/scala/zio/telemetry/opentelemetry/example).

#### Usage with OpenTelemetry automatic instrumentation

OpenTelemetry provides a [JVM agent for automatic instrumentation](https://opentelemetry.io/docs/instrumentation/java/automatic/) which supports many [popular Java libraries](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md).
Since [version 1.25.0](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/tag/v1.25.0) OpenTelemetry JVM agent supports ZIO.

To enable interoperability between automatic instrumentation and `zio-opentelemetry`, `Tracing` has to be created
using `ContextStorage` backed by OpenTelemetry's native `Context` and `Tracer` provided by globally registered `TracerProvider`. It means that instead of `OpenTelemetry.contextZIO` and `OpenTelemetry.custom` you have to provide `OpenTelemetry.contextJVM` and `OpenTelemetry.global` layers.

### Tracing

To send [Trace signals](https://opentelemetry.io/docs/concepts/signals/traces/), you will need a `Tracing` service in your environment. For this, use the `OpenTelemetry.tracing` layer which in turn requires an instance of `OpenTelemetry` provided by Java SDK and a suitable `ContextStorage` implementation. The `Tracing` API includes methods for creating spans, as well as for adding attributes and events to them. 
Here are some of the main ones:

- `root` - sets the current span to be the new root span
- `span` - sets the current span to be the child of the current span
- `spanScoped` - sets the current span to be the child of the current span, but ends it only when the scope closes
- `extractSpan` - extracts the span from carrier and set its child span to be the current span
- `injectSpan` - injects the current span into carrier
- `setAttribute` - sets an attribute of the current span
- `addEvent` - adds an event to the current span

Some of the methods above are available via [ZIO Aspect](https://zio.dev/reference/core/zio/#zio-aspect) syntax. 

```scala
//> using scala "3.4.2"
//> using dep dev.zio::zio:2.1.7
//> using dep dev.zio::zio-opentelemetry:3.0.0-RC24
//> using dep io.opentelemetry:opentelemetry-sdk:1.40.0
//> using dep io.opentelemetry:opentelemetry-sdk-trace:1.40.0
//> using dep io.opentelemetry:opentelemetry-exporter-logging-otlp:1.40.0
//> using dep io.opentelemetry.semconv:opentelemetry-semconv:1.22.0-alpha

object TracingApp extends ZIOAppDefault {

  val instrumentationScopeName = "dev.zio.TracingApp"
  val resourceName             = "tracing-app"

  // Prints to stdout in OTLP Json format
  val stdoutTracerProvider: RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter   <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingSpanExporter.create()))
      spanProcessor  <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleSpanProcessor.create(spanExporter)))
      tracerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkTracerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)))
              .addSpanProcessor(spanProcessor)
              .build()
          )
        )
    } yield tracerProvider

  val otelSdkLayer: TaskLayer[api.OpenTelemetry] =
    OpenTelemetry.custom(
      for {
        tracerProvider <- stdoutTracerProvider
        sdk            <- ZIO.fromAutoCloseable(
                            ZIO.succeed(
                              OpenTelemetrySdk
                                .builder()
                                .setTracerProvider(tracerProvider)
                                .build()
                            )
                          )
      } yield sdk
    )

  override def run =
    ZIO
      .serviceWithZIO[Tracing] { tracing =>
        val logic = for {
          // Set an attribute to the current span
          _       <- tracing.setAttribute("attr1", "value1")
          // Add an event to the current span
          _       <- tracing.addEvent("Waiting for the user input")
          // Read user input
          message <- Console.readLine
          // Add another event to the current span
          _       <- tracing.addEvent(s"User typed: $message")
        } yield message

        // Create a root span with a lifetime equal to the runtime of the given ZIO effect.
        // We use ZIO Aspect's @@ syntax here just for the sake of example.
        logic @@ tracing.aspects.root("root_span", SpanKind.INTERNAL)
      }
      .provide(
        otelSdkLayer,
        OpenTelemetry.tracing(instrumentationScopeName),
        OpenTelemetry.contextZIO
      )

}
```

### Metrics

To send [Metric signals](https://opentelemetry.io/docs/concepts/signals/metrics/), you will need a `Meter` service in your environment. For this, use the `OpenTelemetry.meter` layer which in turn requires an instance of `OpenTelemetry` provided by Java SDK and a suitable `ContextStorage` implementation. The `Meter` API lets you create [Counter](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#counter), [UpDownCounter](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#updowncounter), [Gauge](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#gauge), [Histogram](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#histogram) and their [asynchronous](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/api.md#asynchronous-instrument-api) (aka observable) counterparts. 
As a rule of thumb, observable instruments must be initialized on an application startup. They are scoped, so you should not be worried about shutting them down manually.
By default the metric instruments does not take ZIO log annotations into account. To turn it on pass `logAnnotated = true` parameter to the `OpenTelemetry.metrics` layer initializer.

```scala
//> using scala "3.4.2"
//> using dep dev.zio::zio:2.1.7
//> using dep dev.zio::zio-opentelemetry:3.0.0-RC24
//> using dep io.opentelemetry:opentelemetry-sdk:1.40.0
//> using dep io.opentelemetry:opentelemetry-sdk-trace:1.40.0
//> using dep io.opentelemetry:opentelemetry-exporter-logging-otlp:1.40.0
//> using dep io.opentelemetry.semconv:opentelemetry-semconv:1.22.0-alpha

object MetricsApp extends ZIOAppDefault {

  val instrumentationScopeName = "dev.zio.MetricsApp"
  val resourceName             = "metrics-app"

  // Prints to stdout in OTLP Json format
  val stdoutMeterProvider: RIO[Scope, SdkMeterProvider] =
    for {
      metricExporter <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingMetricExporter.create()))
      metricReader   <-
        ZIO.fromAutoCloseable(ZIO.succeed(PeriodicMetricReader.builder(metricExporter).setInterval(5.second).build()))
      meterProvider  <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkMeterProvider
              .builder()
              .registerMetricReader(metricReader)
              .setResource(Resource.create(common.Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)))
              .build()
          )
        )
    } yield meterProvider

  // Prints to stdout in OTLP Json format
  val stdoutTracerProvider: RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter   <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingSpanExporter.create()))
      spanProcessor  <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleSpanProcessor.create(spanExporter)))
      tracerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkTracerProvider
              .builder()
              .setResource(Resource.create(common.Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)))
              .addSpanProcessor(spanProcessor)
              .build()
          )
        )
    } yield tracerProvider

  val otelSdkLayer: TaskLayer[api.OpenTelemetry] =
    OpenTelemetry.custom(
      for {
        tracerProvider <- stdoutTracerProvider
        meterProvider  <- stdoutMeterProvider
        sdk            <- ZIO.fromAutoCloseable(
                            ZIO.succeed(
                              OpenTelemetrySdk
                                .builder()
                                .setTracerProvider(tracerProvider)
                                .setMeterProvider(meterProvider)
                                .build()
                            )
                          )
      } yield sdk
    )

  // Stores the number of seconds elapsed since the application startup
  val tickRefLayer: ULayer[Ref[Long]] =
    ZLayer(
      for {
        ref <- Ref.make(0L)
        _   <- ref
                 .update(_ + 1)
                 .repeat[Any, Long](Schedule.spaced(1.second))
                 .forkDaemon
      } yield ref
    )

  // Records the number of seconds elapsed since the application startup
  val tickCounterLayer: RLayer[Meter & Ref[Long], Unit] =
    ZLayer.scoped(
      for {
        meter <- ZIO.service[Meter]
        ref   <- ZIO.service[Ref[Long]]
        // Initialize observable counter instrument
        _     <- meter.observableCounter("tick_counter") { om =>
                   for {
                     tick <- ref.get
                     _    <- om.record(tick)
                   } yield ()
                 }
      } yield ()
    )

  override def run =
    ZIO
      .serviceWithZIO[Tracing] { tracing =>
        val logic = for {
          meter                <- ZIO.service[Meter]
          // Create a counter
          messageLengthCounter <- meter.counter("message_length_counter")
          // Read user input
          message              <- Console.readLine
          // Sleep for the number of seconds equal to the message length  to demonstrate the work of observable counter
          _                    <- ZIO.sleep(message.length.seconds)
          // Record the message length
          _                    <- messageLengthCounter.add(message.length, Attributes(Attribute.string("message", message)))
        } yield message

        // By wrapping our logic into a span, we make the `messageLengthCounter` data points correlated with a "root_span" automatically.
        // Additionally we implicitly add one more attribute to the `messageLenghtCounter` as it is wrapped into a `ZIO.logAnnotate` call.
        ZIO.logAnnotate("zio", "annotation")(logic) @@ tracing.aspects.root("root_span")
      }
      .provide(
        otelSdkLayer,
        OpenTelemetry.metrics(instrumentationScopeName, logAnnotated = true),
        OpenTelemetry.tracing(instrumentationScopeName),
        OpenTelemetry.contextZIO,
        tickCounterLayer,
        tickRefLayer
      )

}
```

#### Integration with ZIO metrics

To enable seamless integration with [ZIO metrics](https://zio.dev/reference/observability/metrics/), use the `OpenTelemetry.zioMetrics` layer. If you also need to publish JVM metrics, be sure to include `DefaultJvmMetrics.live.unit`.

### Logging

To send [Log signals](https://opentelemetry.io/docs/concepts/signals/logs/), you will need a `Logging` service in your environment. For this, use the `OpenTelemetry.logging` layer which in turn requires an instance of `OpenTelemetry` provided by Java SDK and a suitable `ContextStorage` implementation. You can achieve the same by incorporating [Logger MDC auto-instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/logger-mdc-instrumentation.md), so the rule of thumb is to use the `Logging` service when you need to propagate ZIO log annotations as log record attributes or, for some reason you don't want to use auto-instrumentation.

```scala
//> using scala "3.4.2"
//> using dep dev.zio::zio:2.1.7
//> using dep dev.zio::zio-opentelemetry:3.0.0-RC24
//> using dep io.opentelemetry:opentelemetry-sdk:1.40.0
//> using dep io.opentelemetry:opentelemetry-sdk-trace:1.40.0
//> using dep io.opentelemetry:opentelemetry-exporter-logging-otlp:1.40.0
//> using dep io.opentelemetry.semconv:opentelemetry-semconv:1.22.0-alpha

object LoggingApp extends ZIOAppDefault {

  val instrumentationScopeName = "dev.zio.LoggingApp"
  val resourceName             = "logging-app"

  // Prints to stdout in OTLP Json format
  val stdoutLoggerProvider: RIO[Scope, SdkLoggerProvider] =
    for {
      logRecordExporter  <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingLogRecordExporter.create()))
      logRecordProcessor <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleLogRecordProcessor.create(logRecordExporter)))
      loggerProvider     <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkLoggerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)))
              .addLogRecordProcessor(logRecordProcessor)
              .build()
          )
        )
    } yield loggerProvider

  // Prints to stdout in OTLP Json format
  val stdoutTracerProvider: RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter   <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingSpanExporter.create()))
      spanProcessor  <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleSpanProcessor.create(spanExporter)))
      tracerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkTracerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)))
              .addSpanProcessor(spanProcessor)
              .build()
          )
        )
    } yield tracerProvider

  val otelSdkLayer: TaskLayer[api.OpenTelemetry] =
    OpenTelemetry.custom(
      for {
        tracerProvider <- stdoutTracerProvider
        loggerProvider <- stdoutLoggerProvider
        sdk            <- ZIO.fromAutoCloseable(
                            ZIO.succeed(
                              OpenTelemetrySdk
                                .builder()
                                .setTracerProvider(tracerProvider)
                                .setLoggerProvider(loggerProvider)
                                .build()
                            )
                          )
      } yield sdk
    )

  override def run =
    ZIO
      .serviceWithZIO[Tracing] { tracing =>
        val logic = for {
          // Read user input
          message <- Console.readLine
          // Propagate a ZIO.logInfo message as an OTEL log signal and log annotations as log record attributes
          _       <- ZIO.logAnnotate("correlated", "true")(
                       ZIO.logInfo(s"User message: $message")
                     )
        } yield ()

        // All log messages produced by `logic` will be correlated with a "root_span" automatically
        logic @@ tracing.aspects.root("root_span")
      }
      .provide(
        otelSdkLayer,
        OpenTelemetry.logging(instrumentationScopeName),
        OpenTelemetry.tracing(instrumentationScopeName),
        OpenTelemetry.contextZIO
      )

}
```

### Baggage

To pass contextual information in [Baggage](https://opentelemetry.io/docs/concepts/signals/baggage/), you will need a `Baggage` service in your environment. For this, use the `OpenTelemetry.baggage` layer which in turn requires an instance of a suitable `ContextStorage` implementation. The `Baggage` API includes methods for getting/setting key/value pairs and injecting/extracting baggage data using the current context. By default the `Baggage` service does not take ZIO log annotations into account. To turn it on use `OpenTelemetry.baggage(logAnnotated = true)`.

```scala
//> using scala "3.4.2"
//> using dep dev.zio::zio:2.1.7
//> using dep dev.zio::zio-opentelemetry:3.0.0-RC24

object BaggageApp extends ZIOAppDefault {

  override def run =
    ZIO
      .serviceWithZIO[Baggage] { baggage =>
        for {
          // Read user input
          message <- Console.readLine
          // Set baggage key/value
          _       <- baggage.set("message", message)
          // Read all baggage data including ZIO log annotations
          data    <- ZIO.logAnnotate("message2", "annotation")(
                       baggage.getAll
                     )
          // Print the resulting data
          _       <- Console.printLine(s"Baggage data: $data")
        } yield message
      }
      .provide(
        OpenTelemetry.baggage(logAnnotated = true),
        OpenTelemetry.contextZIO
      )

}
```

### Context Propagation

Explicitly utilizing the context propagation API becomes relevant only when auto-instrumentation is not used.
Please note that injection and extraction are not referentially transparent due to the use of the mutable OpenTelemetry carrier Java API.

```scala
//> using scala "3.4.2"
//> using dep dev.zio::zio:2.1.7
//> using dep dev.zio::zio-opentelemetry:3.0.0-RC24
//> using dep io.opentelemetry:opentelemetry-sdk:1.40.0
//> using dep io.opentelemetry:opentelemetry-sdk-trace:1.40.0
//> using dep io.opentelemetry:opentelemetry-exporter-logging-otlp:1.40.0
//> using dep io.opentelemetry.semconv:opentelemetry-semconv:1.22.0-alpha

object PropagatingApp extends ZIOAppDefault {

  val instrumentationScopeName = "dev.zio.PropagatingApp"
  val resourceName             = "propagating-app"

  // Prints to stdout in OTLP Json format
  val stdoutTracerProvider: RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter   <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingSpanExporter.create()))
      spanProcessor  <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleSpanProcessor.create(spanExporter)))
      tracerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkTracerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)))
              .addSpanProcessor(spanProcessor)
              .build()
          )
        )
    } yield tracerProvider

  val otelSdkLayer: TaskLayer[api.OpenTelemetry] =
    OpenTelemetry.custom(
      for {
        tracerProvider <- stdoutTracerProvider
        sdk            <- ZIO.fromAutoCloseable(
                            ZIO.succeed(
                              OpenTelemetrySdk
                                .builder()
                                .setTracerProvider(tracerProvider)
                                .build()
                            )
                          )
      } yield sdk
    )

  override def run =
    ZIO
      .serviceWithZIO[Tracing] { tracing =>
        val tracePropagator   = TraceContextPropagator.default
        val baggagePropagator = BaggagePropagator.default
        // Using the same kernel and carriers for baggage and tracing context propagation is safe
        // since their encodings occupy different keys in the OTEL context.
        val kernel            = mutable.Map.empty[String, String]
        val outgoingCarrier   = OutgoingContextCarrier.default(kernel)
        val incomingCarrier   = IncomingContextCarrier.default(kernel)

        ZIO.serviceWithZIO[Baggage] { baggage =>
          // Representing upstream service
          val upstreamService = for {
            // Read user input
            message <- Console.readLine
            // Set and propagate the baggage data using outgoing carrier
            _       <- baggage.set("message", message)
            _       <- baggage.inject(baggagePropagator, outgoingCarrier)
            // Emulate the computation to be wrapped in a root span
            logic    = for {
                         _ <- ZIO.logInfo(s"Message length is ${message.length}")
                         // Inject the current span using outgoing carrier
                         _ <- tracing.injectSpan(tracePropagator, outgoingCarrier)
                       } yield ()
            // Run the logic, wrapping it into a root span
            _       <- logic @@ tracing.aspects.root("upstream_root_span")
          } yield ()

          // Representing downstream service
          val downstreamService = for {
            // Extract the baggage data using incoming carrier
            _      <- baggage.extract(baggagePropagator, incomingCarrier)
            data   <- baggage.getAll
            message = data("message")
            // Emulate the logic that computes message length and sets an attribute of the current span
            logic   = for {
                        _ <- ZIO.logInfo(s"Message length is ${message.length}")
                        _ <- tracing.setAttribute("message", message)
                      } yield ()
            // Run the logic, wrapping it into a child span of the upstream root span
            _      <- logic @@ tracing.aspects.extractSpan(tracePropagator, incomingCarrier, "downstream_root_span")
          } yield ()

          // Simulate the interaction between services
          upstreamService *> downstreamService
        }

      }
      .provide(
        otelSdkLayer,
        OpenTelemetry.tracing(instrumentationScopeName),
        OpenTelemetry.baggage(),
        OpenTelemetry.contextZIO
      )

}
```
