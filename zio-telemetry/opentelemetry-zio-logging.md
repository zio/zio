# OpenTelemetry ZIO Logging

> `zio-opentelemetry` logging facilities are implemented around OpenTelemetry Logging.

`zio-opentelemetry` logging facilities are implemented around OpenTelemetry Logging.

In order to use `zio-opentelemetry` feature with `zio-logging` you should use `zio-opentelemetry-zio-logging` module.

`OpenTelemetry ZIO Logging` contains utilities for combining ZIO Opentelemetry with ZIO Logging

## Installation

```scala
"dev.zio" %% "zio-opentelemetry-zio-logging" % "<version>"
```

## Features

### Log formats

This library implements [Log Format](https://zio.dev/zio-logging/formatting-log-records) for span information (`spanId` and `traceId`). 
To use them you need a `LogFormats` service in the environment. For this, use the `ZioLogging.logFormats` layer which in turn required a suitable `ContextStorage` implementation.

```scala
//> using scala "3.4.2"
//> using dep dev.zio::zio:2.1.7
//> using dep dev.zio::zio-opentelemetry:3.0.0-RC24
//> using dep dev.zio::zio-opentelemetry-zio-logging:3.0.0-RC24
//> using dep io.opentelemetry:opentelemetry-sdk:1.40.0
//> using dep io.opentelemetry:opentelemetry-sdk-trace:1.40.0
//> using dep io.opentelemetry:opentelemetry-exporter-logging-otlp:1.40.0
//> using dep io.opentelemetry.semconv:opentelemetry-semconv:1.22.0-alpha

import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingLogRecordExporter
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.`export`.SimpleLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.api
import zio.*
import zio.logging.console
import zio.logging.LogFormat._
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.zio.logging.LogFormats
import zio.telemetry.opentelemetry.zio.logging.ZioLogging

object ZioLoggingApp extends ZIOAppDefault {

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

  // Setup zio-logging with spanId and traceId labels
  val loggingLayer: URLayer[LogFormats, Unit] = ZLayer {
    for {
      logFormats     <- ZIO.service[LogFormats]
      format          =
        timestamp.fixed(32) |-|
          level |-|
          label("message", quoted(line)) |-|
          logFormats.spanIdLabel |-|
          logFormats.traceIdLabel
      myConsoleLogger = console(format.highlight)
    } yield Runtime.removeDefaultLoggers >>> myConsoleLogger
  }.flatten

  override def run =
    ZIO
      .serviceWithZIO[Tracing] { tracing =>
        val logic = for {
          // Read user input
          message <- Console.readLine
          // Print span and trace ids along with message
          _       <- ZIO.logInfo(s"User message: $message")
        } yield ()

        // All log messages produced by `logic` will be correlated with a "root_span" automatically
        logic @@ tracing.aspects.root("root_span")
      }
      .provide(
        otelSdkLayer,
        OpenTelemetry.logging(instrumentationScopeName),
        OpenTelemetry.tracing(instrumentationScopeName),
        OpenTelemetry.contextZIO,
        ZioLogging.logFormats,
        loggingLayer
      )

}
```
