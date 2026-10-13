# Telemetry

> Module reference index for the telemetry module: tracing, logging, and metrics with a unified provider API.

The telemetry module provides the three pillars of modern observability — distributed tracing, structured logging, and dimensional metrics — with a single coherent API aligned to the OpenTelemetry data model. 

Three global entry points (`trace`, `log`, `metric`) work without any configuration. `trace` and `metric` delegate to an installed provider (`TracerProvider`, `MeterProvider`) that produces scope-specific instances (`Tracer`, `Meter`); `log` holds an installed `Logger` directly, which a `LoggerProvider` is one way to build. 

The same metadata types — `Attributes`, `AttributeKey`, `Resource`, and `InstrumentationScope` — describe signals in all three pillars, and a shared `ContextStorage` enables automatic trace–log correlation out of the box.

The three-pillar structure mirrors the following shape:

```scala
// Global entry points — work without any setup
object trace  { /* span creation, provider install */ }
object log    { /* structured log emission, writer config */ }
object metric { /* instrument factories, reader access */ }

// Provider layer — configure and install once at application startup
final class TracerProvider private[telemetry] (...) extends AutoCloseable
final class LoggerProvider private[telemetry] (...) extends AutoCloseable
final class MeterProvider  private[telemetry] (...) extends AutoCloseable

// Scope-specific instances — created by providers on demand
final class Tracer private[telemetry] (...)
final class Logger private[telemetry] (...)
final class Meter  private[telemetry] (...)
```

## Motivation

Modern services produce three distinct kinds of observability data. Traces capture the causal shape of a request as it traverses services and threads. Logs capture structured events at discrete points in time, ideally correlated to the active trace. Metrics capture aggregated numerical measurements — request counts, latencies, queue depths — that accumulate over time. The telemetry module covers all three in one library that pulls in no third-party dependencies and cross-compiles for the JVM and Scala.js.

## Installation

```scala
// Core: logging, tracing, metrics
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry" % "0.0.56"

// Optional: OTLP JSON export over HTTP (JVM only)
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry-otel" % "0.0.56"
```

For Scala.js (telemetry core only):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-telemetry" % "0.0.56"
```

Supports Scala 2.13.x and 3.x.

## Overview

The module is organized into three areas — tracing, logging, and metrics — that map directly to the OTel signal types, plus the metadata types all three rely on.

### Tracing

[Tracing](./tracing/index.md) covers the full span lifecycle. `TracerProvider` is a factory that shares a `Resource`, a `Sampler`, and a set of `SpanProcessor` hooks across all `Tracer` instances it creates. A `Tracer` opens and closes `Span` scopes, consulting the `Sampler` on each new span and calling `SpanProcessor.onStart` and `SpanProcessor.onEnd` for exporting or in-memory collection. Supporting types — `SpanContext`, `SpanData`, `SpanKind`, `SpanStatus`, `SpanId`, `TraceId`, and `TraceFlags` — capture the identity and state of a span.

### Logging

[Logging](./logging/index.md) covers structured, severity-leveled log emission. `LoggerProvider` is a factory that shares a `Resource` and a set of `LogRecordProcessor` hooks across all `Logger` instances it creates. A `Logger` emits `LogRecord` snapshots through configured processors; processors can write formatted output via a `LogFormatter` and `LogWriter`, export records to an external system, or gate emission by `Severity`. The macro-generated methods on the global `log` object capture source location at compile time and automatically stamp the active span context into every emitted record.

### Metrics

[Metrics](./metrics/index.md) covers dimensional cumulative and instantaneous measurements. `MeterProvider` is a factory for `Meter` instances; each `Meter` creates and registers instruments — `Counter`, `UpDownCounter`, `Histogram`, and `Gauge` — and each instrument keeps one series per `Attributes` set it sees. Instruments themselves are not cached by name; build each once and hold it. A `MetricReader` collects all registered instruments into `MetricData` snapshots on demand for export or inspection.

### Common Types

A span, a log record, and a measurement are different kinds of data, but they answer the same three questions: what happened, which service produced it, and which component inside that service. Four types carry those answers, and because all three pillars use the same four, whatever you learn about them applies everywhere:

- [`Attributes`](./common/attributes.md) — the detail on a signal, as typed key-value pairs: the route on a span, the order id on a log record, the labels on a measurement.
- [`AttributeKey`](./common/attribute-key.md) — a name paired with the type of its value, so reading an attribute back gives you a `String` or a `Long` rather than something to cast.
- [`Resource`](./common/resource.md) — which service produced the signal, attached to every one the provider emits. Give all three providers the same one.
- [`InstrumentationScope`](./common/instrumentation-scope.md) — which library or component produced it, taken from the name you pass to `TracerProvider.get`, `LoggerProvider.get`, or `MeterProvider.get`.

## How They Work Together

All three pillars follow the same structural pattern: a global singleton wraps a provider that acts as a factory for lightweight, scope-specific instances. The provider holds the shared configuration — `Resource`, processor lists, and `ContextStorage` — and creates instances on demand. Signals are enriched by `Attributes` and labeled by `InstrumentationScope`. The `ContextStorage[Option[SpanContext]]` instance that `TracerProvider` and `LoggerProvider` share is what makes trace–log correlation automatic. Give all three providers the *same* `Resource` rather than one each — identical service-identity attributes on every signal are what let a backend line up traces, logs, and metrics as one service.

```
  Global entry points          Provider layer                 Scope instances
                             ┌─────────────────────────┐   ┌──────────────────┐
                             │  TracerProvider         │──▶│  Tracer          │──▶ Span
  ┌──────────────────────┐   │  · Resource             │   └──────────────────┘    │ SpanProcessor
  │  trace  (object)     │──▶│  · Sampler              │         ContextStorage ◀╴╴┤
  └──────────────────────┘   │  · SpanProcessors       │                           │ (shared)
                             │  · ContextStorage ╶╶╶╶╶╶┼╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╯
                             └─────────────────────────┘   ┌──────────────────┐
                             ┌─────────────────────────┐   │  Logger          │
  ┌──────────────────────┐   │  LoggerProvider         │   │  (traceId+spanId │──▶ LogRecord
  │  log    (object)     │──▶│  · Resource             │──▶│   from storage)  │    LogRecordProcessor
  └──────────────────────┘   │  · LogRecordProcessor   │   └──────────────────┘
                             │  · ContextStorage ╶╶╶╶╶╶┼╶╶╶╶╶╶╶(same instance)
                             └─────────────────────────┘
                             ┌─────────────────────────┐   ┌──────────────────┐
  ┌──────────────────────┐   │  MeterProvider          │   │  Meter           │──▶ Counter
  │  metric (object)     │──▶│  · Resource             │──▶│  · counterBuilder│    Histogram
  └──────────────────────┘   │  · MeterRegistry        │   │  · histogramBld. │    Gauge
                             └─────────────────────────┘   └──────────────────┘    UpDownCounter
                                                                    │
                                                              MetricReader ──▶ MetricData

  ──────────────── Shared across all three pillars ─────────────────────────────────
        Attributes  ·  AttributeKey  ·  Resource  ·  InstrumentationScope
```

The following sub-sections walk through the data flow for each pillar and explain the correlation mechanism.

### Tracing Data Flow

When application code calls `trace.span("operation")`, the following sequence occurs:

1. The global `trace` object delegates to the active `TracerProvider`, which creates or retrieves a `Tracer` for the `"default"` instrumentation scope.
2. The `Tracer` consults its `Sampler` via `shouldSample`. If the decision is `RecordAndSample`, a `RecordingSpan` is constructed via `SpanBuilder.startSpan` and `SpanProcessor.onStart` is called; if `Drop`, a `Span.NoOp` is returned — the span records nothing and reaches no processor, though a `SpanContext` is still allocated and scoped so nested spans and log records stay on the same trace.
3. The active `SpanContext` is stored into `ContextStorage` for the duration of the user block so that nested spans can read it as their parent.
4. On exit, `Span.end()` is called, `SpanData` is snapshotted, and `SpanProcessor.onEnd` fires — delivering the completed span to exporters or the in-memory test processor.

The following example shows a minimal production tracing configuration:

```scala
import zio.blocks.telemetry._

val tracerProvider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "order-service")))
  .setSampler(AlwaysOnSampler)
  .build()

trace.install(tracerProvider)

trace.span("process-order", SpanKind.Server) { span =>
  span.setAttribute("order.id", "ord-123")
  span.addEvent("validation-passed")
}
```

### Logging Data Flow

When application code calls `log.info("message", ...)`, the macro captures the call site's source file, enclosing class, method name, and line number at compile time. At runtime the severity is compared against the configured minimum; if the call passes the threshold, the captured source-location attributes are written into an `AttributesBuilder`, the current `SpanContext` is read from the shared `ContextStorage`, and `traceIdHi`, `traceIdLo`, `spanId`, and `traceFlags` are stamped into the record as unboxed primitives before being dispatched to all configured processors.

The following example routes log output to standard output in human-readable text format:

```scala
import zio.blocks.telemetry._

log.writer(TextLogFormatter, StdoutWriter)
log.info("order placed", "orderId" -> "ord-123", "amount" -> 99L)
```

### Metrics Data Flow

When a `Counter`, `Histogram`, `Gauge`, or `UpDownCounter` is created via `Meter.counterBuilder("name").build()`, the instrument is registered in the owning `Meter`'s internal instrument list. The `MeterProvider.reader` property returns a `MetricReader` that, on each `collectAllMetrics()` call, iterates every registered `Meter` and invokes `collect()` on each instrument, aggregating the results into a sequence of `MetricData` variants for export or inspection.

The following example records request counts and then reads the aggregated snapshot:

```scala
import zio.blocks.telemetry._

val requests = metric.counter("http.requests")
requests.add(1, "method" -> "GET", "status" -> "200")

val snapshots = metric.reader.collectAllMetrics()
snapshots.foreach {
  case MetricData.SumData(points)       => points.foreach(p => println(p.value))
  case MetricData.HistogramData(points) => points.foreach(p => println(p.count))
  case MetricData.GaugeData(points)     => points.foreach(p => println(p.value))
}
```

### Trace–Log Correlation

`TracerProvider` and `LoggerProvider` share the same `ContextStorage[Option[SpanContext]]` instance. When a `Tracer` opens a span it stores the `SpanContext` in that storage; when `Logger` emits a record it reads the same storage and stamps `traceIdHi`, `traceIdLo`, `spanId`, and `traceFlags` into the `LogRecord` fields as primitives — no boxing, no formatting until a `LogFormatter` runs. Because both providers default to the same internal `defaultSpanContextStorage` singleton, correlation is automatic when both are installed with their default settings.

To use an explicit shared `ContextStorage` — for example, to isolate correlation in tests — create one instance and pass it to both builders:

```scala
import zio.blocks.telemetry._

val sharedStorage = ContextStorage.create[Option[SpanContext]](None)

val tp = TracerProvider.builder.setContextStorage(sharedStorage).build()
val lp = LoggerProvider.builder.setContextStorage(sharedStorage).build()

trace.install(tp)
log.install(lp.get("com.example"))
```

## Common Patterns

The following patterns appear consistently across services that use this module. Each fits into a shared startup and request-handling lifecycle.

### Zero-Setup Global API

All three global entry points work immediately after import: `trace` buffers spans in memory, `log` prints readable text to stdout, and `metric` writes to an in-process `MeterProvider`. The following import is the only requirement for development and testing:

```scala
import zio.blocks.telemetry._

trace.span("bootstrap") { span =>
  span.setAttribute("step", "init")
}

log.writer(TextLogFormatter, StdoutWriter)
log.info("server started", "port" -> 8080L)

metric.counter("startup.count").add(1)
```

### Production Provider Installation

At application startup we configure all three providers once and install them before serving any requests. Sharing a common `Resource` propagates the service identity through all signals:

```scala
import zio.blocks.telemetry._

val serviceResource = Resource.default.merge(
  Resource.create(Attributes.of(Attributes.ServiceName, "payments"))
)

val tp = TracerProvider.builder
  .setResource(serviceResource)
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .build()

val lp = LoggerProvider.builder.setResource(serviceResource).build()
val mp = MeterProvider.builder.setResource(serviceResource).build()

trace.install(tp)
log.install(lp.get("com.example.payments"))
metric.install(mp)
```

### Library Dependency-Injection Pattern

Library code should accept `Tracer` and `Logger` as constructor parameters rather than using the global singletons. This makes the library testable in isolation and avoids coupling to global state. The `Logger.info` method on the class itself takes `(String, AttributeValue)` pairs rather than the macro enrichment tuples used by the global `log` object:

```scala
import zio.blocks.telemetry._

final class OrderService(tracer: Tracer, logger: Logger) {
  def placeOrder(id: String): Unit =
    tracer.span("orders.place") { span =>
      span.setAttribute("order.id", id)
      logger.info("order placed", "orderId" -> AttributeValue.StringValue(id))
    }
}
```

Application startup code injects instances by calling `trace.get("com.example.orders")` and `loggerProvider.get("com.example.orders")`.

### Scoped Log Annotations

`log.annotated` propagates key-value pairs to every `log.*` call within the block. Annotations are scoped to the enclosing thread and do not leak outside it:

```scala
import zio.blocks.telemetry._

def handleRequest(requestId: String): Unit =
  log.annotated("requestId" -> requestId, "env" -> "prod") {
    log.info("processing started")
    log.info("processing done")
  }
```

### Rate-Limited Logging

The `*Every` family of log methods emits at most once every N invocations per call site. The counter is keyed by the call site's file and line, so sites with the same interval value are normally independent — though the keys index a fixed 4096-slot table, so two sites can collide and share one counter:

```scala
import zio.blocks.telemetry._

def heartbeat(): Unit =
  log.infoEvery(100, "heartbeat tick")
```

### Labeled Instruments

`Meter.labeledCounter`, `Meter.labeledHistogram`, and `Meter.labeledGauge` declare label names once and accept positional string values at recording time, matching the Prometheus-style label pattern:

```scala
import zio.blocks.telemetry._

val meter    = metric.get("com.example")
val requests = meter.labeledCounter("http.requests", "method", "status")

requests.add(1, "GET",  "200")
requests.add(1, "POST", "500")
```

## Integration Points

Scoped, thread-safe propagation of `SpanContext` through call stacks is handled by the module's own `ContextStorage`, which is backed by JDK `ScopedValue` on the JVM and by a saved-and-restored variable on Scala.js. At the build level the module depends only on the `context` and `chunk` blocks and requires no external libraries.

`SpanProcessor` and `LogRecordProcessor` are open traits. External exporters — for example, an OpenTelemetry SDK bridge — implement these traits to receive span and log data and forward it to collection infrastructure such as OTLP endpoints. Plug a custom `SpanProcessor` into `TracerProvider.builder.addSpanProcessor(...)` or a custom `LogRecordProcessor` into `LoggerProvider.builder.addLogRecordProcessor(...)`.

`LogEnrichment` is a typeclass resolved at compile time by the macro-generated `log.*` methods. Built-in instances cover `Throwable`, `Attributes`, `Severity`, and `(String, A)` pairs for `A ∈ {String, Long, Int, Double, Boolean}`. Adding support for a custom enrichment type requires only a new `implicit val LogEnrichment[MyType]` in scope at the `log.*` call site.

## See Also

- [Telemetry Guide](../../guides/telemetry-guide.md) — architecture, design trade-offs, and real-world usage patterns
- [Common Types](./common/index.md) — `Attributes`, `AttributeKey`, `Resource`, and `InstrumentationScope` shared across all three pillars
- [Context](../context.md) — `Context[R]`, which the otel module's `OtelContext` carries an active `SpanContext` through
