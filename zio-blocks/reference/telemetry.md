# Telemetry

> `zio-blocks-telemetry` is an **effect-free, zero-allocation** observability library covering logging, tracing, and metrics. It follows the OpenTelemetry data model but has no dependency on the OpenTelemetry SDK. Hot paths are macro-generated so that a disabled log level costs exactly one volatile read plus one array scan (usually empty). Backends are pluggable: the default writes to the console with no setup, and production exporters live in the companion `zio-blocks-telemetry-otel` module.

The three main entry points are:
- `log` — structured logging with macro-generated call sites
- `trace` — distributed tracing with automatic parent propagation
- `metric` — counters, histograms, and gauges

All three work out of the box with no wiring. Replace the default provider at any time via the `install` methods.

## Installation

```scala
// Core: logging, tracing, metrics
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry" % "0.0.51"

// Optional: OTLP JSON export over HTTP (JVM only)
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry-otel" % "0.0.51"
```

For Scala.js (telemetry core only):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-telemetry" % "0.0.51"
```

Supports Scala 2.13.x and 3.x.

## Logging

### Zero-config console logging

Import `zio.blocks.telemetry._` and call `log` directly. No setup required. The default backend prints human-readable text to stdout.

```scala
import zio.blocks.telemetry._

log.info("server started")
log.debug("handling request", "method" -> "GET", "path" -> "/api/v1/users")
log.warn("slow query", "duration_ms" -> 450L)
log.error("unhandled exception", new RuntimeException("disk full"))
log.fatal("shutting down", "reason" -> "out of memory")
```

Each call is a Scala inline macro. When the log level is disabled, the compiler eliminates the entire call site, including argument evaluation.

### Structured key-value enrichment

Every `log.*` method accepts a varargs list of enrichments after the message. Enrichments are resolved via implicit `LogEnrichment[A]` instances.

```scala
import zio.blocks.telemetry._

// String attribute
log.info("user login", "user_id" -> "u-1234")

// Long attribute
log.info("item purchased", "price_cents" -> 4999L)

// Int attribute (auto-widened to Long internally)
log.info("retry attempt", "attempt" -> 3)

// Double attribute
log.info("cpu load", "load_avg" -> 0.87)

// Boolean attribute
log.info("feature flag", "dark_mode" -> true)

// Throwable: adds exception.type, exception.message, and stacktrace
log.error("payment failed", new IllegalStateException("card declined"))

// Severity override: emit at the declared level but record a different severity
log.info("degraded response", Severity.Warn)

// Pre-built Attributes block
val extra = Attributes.builder
  .put("region", "eu-west-1")
  .put("az", "b")
  .build
log.info("instance started", extra)

// Mix enrichments freely in a single call
log.warn(
  "request failed",
  "status" -> 503,
  "retries" -> 2L,
  "cached"  -> false,
  new RuntimeException("upstream timeout")
)
```

### Supported enrichment types reference

| Scala type             | OTLP attribute type | Notes |
|------------------------|---------------------|-------|
| `(String, String)`     | `STRING`            | |
| `(String, Long)`       | `INT64`             | |
| `(String, Int)`        | `INT64`             | auto-promoted via `.toLong` |
| `(String, Double)`     | `DOUBLE`            | |
| `(String, Boolean)`    | `BOOL`              | |
| `Attributes`           | merged              | pre-built attribute set |
| `Severity`             | overrides level     | e.g. `Severity.Warn` |
| `Throwable`            | `exception.*`       | adds type, message, stacktrace |

**Not directly supported:** `(String, Float)`, `(String, Short)`, `(String, Byte)`, `(String, Char)`. Convert with `.toDouble`, `.toLong`, or `.toString`. Same for `UUID`, `Instant`, `LocalDateTime`, and other JDK types: use `.toString`, or provide a custom `LogEnrichment[T]` implicit.

### What happens at runtime

When a log call executes:

1. Check `log.globalMinLevel` — one volatile read. If the message severity is below the global floor, stop immediately.
2. Check per-namespace level overrides — one array scan over the override list (empty by default, O(1) for the common case).
3. Build attributes into a pooled `AttributeBuilder` — no heap allocation on the hot path.
4. Merge scoped annotations from `LogAnnotations` (thread-local / ScopedValue on JVM 25+).
5. Read the current `SpanContext` from `ContextStorage` and attach `traceId` / `spanId` if present.
6. Call `LogRecordProcessor.onEmit` on each processor.

Steps 1 and 2 are the entire cost when logging is disabled. No string formatting, no argument boxing.

### Scoped annotations

`log.annotated` adds key-value string pairs to every log call within its scope. Annotations are thread-local (ScopedValue on JDK 25+, `ThreadLocal` otherwise).

```scala
import zio.blocks.telemetry._

def handleRequest(requestId: String): Unit =
  log.annotated("requestId" -> requestId, "service" -> "payments") {
    log.info("request received")    // includes requestId and service
    processPayment()
    log.info("request complete")    // still includes them
  }
```

Annotations are inherited by nested calls and removed automatically when the block exits.

### Rate-limited logging

Use `*Every` to emit only on every Nth invocation at a given call site. Use `*AtMost` to emit at most once per time window (milliseconds). Both are per-call-site, not global.

```scala
import zio.blocks.telemetry._

// Emit on every 100th call at this exact call site
def onTick(): Unit =
  log.infoEvery(100, "heartbeat")

// Emit at most once per 5 seconds at this call site
def onRequest(): Unit =
  log.warnAtMost(5000L, "high latency detected", "threshold_ms" -> 200L)

// All six severity levels have *Every and *AtMost variants
log.traceEvery(1000, "fine-grained trace")
log.debugAtMost(1000L, "debug sampling")
log.errorEvery(10, "repeated error suppressed")
log.fatalAtMost(60000L, "fatal alert")
```

The call-site counter/timestamp is stored in a `val` synthesized by the macro — no shared state between call sites.

### Per-namespace log levels

Override the minimum severity for any package prefix. The longest matching prefix wins.

```scala
import zio.blocks.telemetry._

// Suppress DEBUG noise from a chatty library
log.setMinSeverity("com.thirdparty.noisylibrary", Severity.Warn)

// Enable TRACE for your own module during debugging
log.setMinSeverity("com.myapp.payments", Severity.Trace)

// Restore a namespace to the global default
log.clearMinSeverity("com.thirdparty.noisylibrary")

// Remove all overrides
log.clearAllOverrides()
```

### File logging (JVM only)

`FileLogWriter` writes to a file using `FileChannel` with a shared byte buffer protected by a `ReentrantLock` (virtual-thread safe). ASCII content takes the fast path (no charset encoding step).

```scala
import zio.blocks.telemetry._

// Text format, append mode, 8 KB buffer (defaults)
val writer = FileLogWriter("logs/app.log")
log.writer(TextLogFormatter, writer)

// JSON format with a larger buffer
val jsonWriter = FileLogWriter(
  java.nio.file.Paths.get("logs/app.json"),
  append = true,
  bufferSize = 16384
)
log.writer(JsonLogFormatter, jsonWriter)

// Both outputs are active simultaneously — writer() is additive
```

`log.writer` adds an output; calling it twice gives you two outputs. Use `log.clearWriters()` to remove all file outputs and revert to processor-based routing.

### JSON logging

Swap `TextLogFormatter` for `JsonLogFormatter` to get OTLP-compatible JSON lines:

```scala
import zio.blocks.telemetry._

log.writer(JsonLogFormatter, FileLogWriter("logs/app.jsonl"))

// Each line looks like:
// {"timeUnixNano":"...","severityNumber":9,"severityText":"INFO","body":{"stringValue":"server started"},"attributes":[...]}
```

### Custom LogEnrichment

Provide a `LogEnrichment[T]` implicit to support your own types:

```scala
import zio.blocks.telemetry._
import java.util.UUID

implicit val uuidEnrichment: LogEnrichment[(String, UUID)] =
  new LogEnrichment[(String, UUID)] {
    def enrich(record: LogRecord, value: (String, UUID)): LogRecord =
      record.copy(
        attributes = record.attributes ++ Attributes.of(
          AttributeKey.string(value._1), value._2.toString
        )
      )
  }

log.info("user action", "traceId" -> UUID.randomUUID())
```

## Tracing

### Basic span

Get a `Tracer` from `trace` and call `span`. The block receives the active `Span`. The span starts, runs your code, then ends — even if an exception is thrown.

```scala
import zio.blocks.telemetry._

val tracer = trace.get("my-service")

val result = tracer.span("compute-order") { span =>
  span.setAttribute("order.id", "ord-7890")
  span.addEvent("validation-complete")
  // ... your logic here
  42
}
```

### Span kinds

```scala
import zio.blocks.telemetry._

val tracer = trace.get("gateway")

// HTTP server handler
tracer.span("handle-request", SpanKind.Server) { span =>
  span.setAttribute("http.method", "POST")
  span.setAttribute("http.route", "/checkout")
  processCheckout()
}

// Outbound HTTP call
tracer.span("call-payment-api", SpanKind.Client) { span =>
  span.setAttribute("peer.service", "stripe")
  callStripe()
}
```

### Nested spans and automatic parent propagation

Child spans are created by calling `tracer.span` inside a parent span's block. The parent `SpanContext` is stored in `ContextStorage` (ScopedValue on JDK 25+) and automatically picked up.

```scala
import zio.blocks.telemetry._

val tracer = trace.get("checkout-service")

tracer.span("checkout") { _ =>
  // This span becomes the parent automatically
  tracer.span("validate-cart") { _ =>
    tracer.span("check-inventory") { _ =>
      // grandchild — all three linked in the same trace
      checkInventory()
    }
  }
  tracer.span("charge-card") { _ =>
    chargeCard()
  }
}
```

### Span attributes and events

```scala
import zio.blocks.telemetry._

val tracer = trace.get("my-service")

tracer.span("process-file") { span =>
  // Typed attribute keys
  span.setAttribute(AttributeKey.string("file.path"), "/data/input.csv")
  span.setAttribute(AttributeKey.long("file.size_bytes"), 1048576L)
  span.setAttribute(AttributeKey.boolean("file.compressed"), true)

  // String/Long/Double/Boolean convenience overloads
  span.setAttribute("file.format", "csv")
  span.setAttribute("row.count", 50000L)

  // Events mark instants within the span
  span.addEvent("parsing-started")
  val rows = parseFile()
  span.addEvent("parsing-complete", Attributes.builder.put("rows", rows.toLong).build)

  // Span status
  span.setStatus(SpanStatus.Ok)
  rows
}
```

### Log-trace correlation

When you log inside an active span, the logger automatically reads the current `SpanContext` and attaches `traceId` and `spanId` to the log record. No extra code needed.

```scala
import zio.blocks.telemetry._

val tracer = trace.get("order-service")

tracer.span("place-order") { _ =>
  log.info("order received", "order_id" -> "ord-5678")
  // This log record automatically includes traceId and spanId
  processOrder()
  log.info("order complete")
}
```

## Metrics

### Counter, Histogram, Gauge

```scala
import zio.blocks.telemetry._

// Counter: monotonically increasing
val requests = metric.counter("http.server.requests")
requests.add(1, "method" -> "GET", "status" -> "200")
requests.add(1, "method" -> "POST", "status" -> "201")

// Histogram: records distributions
val latency = metric.histogram("http.server.duration_ms")
latency.record(42.5, "method" -> "GET")
latency.record(310.0, "method" -> "POST")

// Gauge: last-write-wins, can go up or down
val poolSize = metric.gauge("db.connection_pool.size")
poolSize.record(10.0, "pool" -> "primary")
poolSize.record(5.0, "pool" -> "replica")

// UpDownCounter: like a counter but supports negative deltas
val queueDepth = metric.upDownCounter("jobs.queue.depth")
queueDepth.add(3, "queue" -> "high-priority")
queueDepth.add(-1, "queue" -> "high-priority")
```

### Bound instruments for hot paths

Pre-binding a fixed attribute set avoids the attribute-lookup cost on every recording. Use `bind` when you record against the same label combination at high frequency.

```scala
import zio.blocks.telemetry._

val requests = metric.counter("http.server.requests")

// Bind once — typically at startup or first use
val getOk  = requests.bind(Attributes.builder.put("method", "GET").put("status", "200").build)
val postOk = requests.bind(Attributes.builder.put("method", "POST").put("status", "201").build)

// Hot path: just increment the pre-bound adder
def onGetSuccess(): Unit  = getOk.add(1)
def onPostSuccess(): Unit = postOk.add(1)
```

Histograms and gauges have the same `bind` pattern:

```scala
import zio.blocks.telemetry._

val latency      = metric.histogram("rpc.duration_ms")
val getLatency   = latency.bind(Attributes.builder.put("method", "GET").build)
val poolSize     = metric.gauge("db.pool.size")
val primaryPool  = poolSize.bind(Attributes.builder.put("pool", "primary").build)

def recordGet(ms: Double): Unit = getLatency.record(ms)
def updatePool(n: Double): Unit = primaryPool.record(n)
```

## OTEL Export

The `zio-blocks-telemetry-otel` module provides OTLP JSON exporters that batch and send over HTTP. The exporter classes (`OtlpJsonTraceExporter`, `OtlpJsonLogExporter`, `OtlpJsonMetricExporter`) live in `package zio.blocks.telemetry.otel` and are wired by placing your telemetry bootstrap code in the same package, or by creating a thin wrapper class there.

Each exporter takes an `ExporterConfig`, a `Resource`, an `InstrumentationScope`, an `HttpSender`, and a `PlatformExecutor`.

### Trace export

```scala
package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

val config = ExporterConfig(
  endpoint            = "http://otel-collector:4318",
  headers             = Map("Authorization" -> "Bearer my-token"),
  timeout             = java.time.Duration.ofSeconds(10),
  maxQueueSize        = 2048,
  maxBatchSize        = 512,
  flushIntervalMillis = 5000L
)

val resource = Resource.create(
  Attributes.builder
    .put("service.name", "my-service")
    .put("service.version", "1.0.0")
    .build
)

val scope    = InstrumentationScope("my-service")
val sender   = HttpSender.jdk(java.time.Duration.ofSeconds(10))
val executor = PlatformExecutor.create()

val traceExporter = new OtlpJsonTraceExporter(config, resource, scope, sender, executor)

val tracerProvider = TracerProvider.builder
  .addSpanProcessor(traceExporter)
  .setResource(resource)
  .build()

trace.install(tracerProvider)

// Now all spans are exported to the collector
val tracer = trace.get("my-service")
tracer.span("my-operation") { _ => doWork() }
```

### Log export

```scala
package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

val config   = ExporterConfig(endpoint = "http://otel-collector:4318")
val resource = Resource.create(Attributes.builder.put("service.name", "my-service").build)
val scope    = InstrumentationScope("my-service")
val sender   = HttpSender.jdk()
val executor = PlatformExecutor.create()

val logExporter = new OtlpJsonLogExporter(config, resource, scope, sender, executor)

val loggerProvider = LoggerProvider.builder
  .addLogRecordProcessor(logExporter)
  .setResource(resource)
  .build()

log.install(loggerProvider.get("my-service"))

// Now log.info / log.warn / etc. are batched and exported to the collector
log.info("application started")
```

### Metric export

`OtlpJsonMetricExporter` takes a `collectFn: () => Seq[NamedMetric]` callback that the exporter calls on each flush. Wire it with the instruments you create:

```scala
package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

val config   = ExporterConfig(endpoint = "http://otel-collector:4318", flushIntervalMillis = 15000L)
val resource = Resource.create(Attributes.builder.put("service.name", "my-service").build)
val scope    = InstrumentationScope("my-service")
val sender   = HttpSender.jdk()

// Create your instruments first
val requestCounter = metric.counter("http.server.requests")
val latencyHist    = metric.histogram("http.server.duration_ms")

// Provide a collectFn that harvests each instrument
val metricExporter = new OtlpJsonMetricExporter(
  config, resource, scope, sender,
  () => Seq(
    NamedMetric("http.server.requests", "Total HTTP requests", "1",     requestCounter.collect()),
    NamedMetric("http.server.duration_ms", "Request latency", "ms",    latencyHist.collect())
  )
)

// The exporter flushes on the interval from config
// Call metricExporter.exportMetrics() to trigger a manual flush
```

## Context Propagation

Propagators extract and inject `SpanContext` from/into a carrier (typically HTTP headers). Two formats ship out of the box.

### W3C TraceContext (recommended)

The `traceparent` header format from the W3C TraceContext spec.

```scala
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

// Inject into outgoing HTTP headers
val tracer = trace.get("gateway")

tracer.span("outbound-call") { span =>
  var headers = Map.empty[String, String]
  headers = W3CTraceContextPropagator.inject(
    span.spanContext,
    headers,
    (carrier, k, v) => carrier + (k -> v)
  )
  callDownstream(headers)
}

// Extract from incoming HTTP headers
def handleIncoming(incomingHeaders: Map[String, String]): Unit = {
  val parentCtx = W3CTraceContextPropagator.extract(
    incomingHeaders,
    (carrier, key) => carrier.get(key)
  )
  // parentCtx: Option[SpanContext] — use to establish parent link
  parentCtx.foreach { ctx =>
    // Store in ContextStorage to make it the active span context
  }
}
```

### B3 (Zipkin)

Single-header and multi-header variants.

```scala
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

// Single header: b3: {traceId}-{spanId}-{sampling}
val incomingB3Single = B3Propagator.single.extract(
  incomingHeaders,
  (carrier, key) => carrier.get(key)
)

// Multi-header: X-B3-TraceId, X-B3-SpanId, X-B3-Sampled, ...
val incomingB3Multi = B3Propagator.multi.extract(
  incomingHeaders,
  (carrier, key) => carrier.get(key)
)

// Inject
var outHeaders = Map.empty[String, String]
outHeaders = B3Propagator.single.inject(
  spanContext,
  outHeaders,
  (c, k, v) => c + (k -> v)
)
```

## Custom Provider Wiring

The exporter classes live inside `package zio.blocks.telemetry.otel`, so place your bootstrap code there (or in a thin wrapper class in that package).

### Custom LoggerProvider

Build a logger with multiple processors — one for console output, one for OTEL export:

```scala
package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

val config   = ExporterConfig(endpoint = "http://otel-collector:4318")
val resource = Resource.create(
  Attributes.builder
    .put("service.name", "checkout")
    .put("service.version", "2.1.0")
    .build
)
val scope    = InstrumentationScope("checkout-service")
val sender   = HttpSender.jdk()
val executor = PlatformExecutor.create()

val logExporter = new OtlpJsonLogExporter(config, resource, scope, sender, executor)

val loggerProvider = LoggerProvider.builder
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)
  .addLogRecordProcessor(logExporter)
  .setResource(resource)
  .build()

val logger = loggerProvider.get("checkout-service")
log.install(logger, minSeverity = Severity.Info)
```

### Custom TracerProvider

Build a tracer with a custom sampler and processor chain:

```scala
package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

val config   = ExporterConfig(endpoint = "http://otel-collector:4318")
val resource = Resource.create(
  Attributes.builder
    .put("service.name", "checkout")
    .put("deployment.environment", "production")
    .build
)
val scope    = InstrumentationScope("checkout-service")
val sender   = HttpSender.jdk()
val executor = PlatformExecutor.create()

val traceExporter = new OtlpJsonTraceExporter(config, resource, scope, sender, executor)

val tracerProvider = TracerProvider.builder
  .setSampler(AlwaysOnSampler)
  .addSpanProcessor(traceExporter)
  .setResource(resource)
  .build()

trace.install(tracerProvider)
```

## ExporterConfig reference

```scala
final case class ExporterConfig(
  endpoint:            String   = "http://localhost:4318",
  headers:             Map[String, String] = Map.empty,
  timeout:             java.time.Duration = java.time.Duration.ofSeconds(30),
  maxQueueSize:        Int  = 2048,
  maxBatchSize:        Int  = 512,
  flushIntervalMillis: Long = 5000
)
```

| Field | Default | Description |
|-------|---------|-------------|
| `endpoint` | `http://localhost:4318` | OTLP HTTP base URL. Paths `/v1/traces`, `/v1/logs`, `/v1/metrics` are appended automatically. |
| `headers` | empty | Additional HTTP headers (e.g. authentication). `Content-Type: application/json` is added automatically. |
| `timeout` | 30 s | Per-request HTTP timeout. |
| `maxQueueSize` | 2048 | Maximum number of pending records before the oldest are dropped. |
| `maxBatchSize` | 512 | Maximum records per export batch. |
| `flushIntervalMillis` | 5000 | Background flush interval in milliseconds. |

## Formatters reference

| Formatter | Output |
|-----------|--------|
| `TextLogFormatter` | Human-readable: `2026-03-31T17:30:00.123Z INFO  [MyClass.doWork:42] message {key="val"}` |
| `JsonLogFormatter` | OTLP-compatible JSON: `{"timeUnixNano":"...","severityNumber":9,"severityText":"INFO","body":{"stringValue":"message"},"attributes":[...]}` |

Both are singleton objects that allocate nothing per log record. They write directly into a pooled `StringBuilder`.

## Severity levels

`Severity` follows the OTLP specification with 24 levels in six groups. The most common values are:

| Object | Number | Text |
|--------|--------|------|
| `Severity.Trace` | 1 | `TRACE` |
| `Severity.Debug` | 5 | `DEBUG` |
| `Severity.Info`  | 9 | `INFO` |
| `Severity.Warn`  | 13 | `WARN` |
| `Severity.Error` | 17 | `ERROR` |
| `Severity.Fatal` | 21 | `FATAL` |

Each group has four variants (`Trace`, `Trace2`, `Trace3`, `Trace4`, etc.) for fine-grained filtering. The default global minimum is `Severity.Trace` (all levels enabled).
