# Telemetry: Architecture, Patterns, and Real-World Usage

> `zio-blocks-telemetry` is an effect-free, zero-allocation observability library that gives you structured logging, distributed tracing, and metrics without pulling in the OpenTelemetry SDK or any effect system. This guide explains why it works the way it does, shows what happens inside each operation, and walks through patterns for production systems.

If you're looking for API signatures and quick copy-paste snippets, the [Telemetry Reference](../reference/telemetry.md) has those. This guide focuses on the "how" and "why": architecture, design trade-offs, and the kind of understanding that helps when things go wrong at 3 AM.

## Installation

```scala
// Core: logging, tracing, metrics (JVM + JS)
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry" % "0.0.51"

// OTLP JSON export over HTTP (JVM only)
libraryDependencies += "dev.zio" %% "zio-blocks-telemetry-otel" % "0.0.51"
```

---

## Part 1: Architecture and Design Philosophy

### Why effect-free?

Most Scala observability libraries wrap logging or tracing calls in an effect type. ZIO Logging needs `ZIO[R, E, Unit]`. Odin uses `F[Unit]`. Even scribe's direct API leans on Cats effect in production setups.

Effect-wrapping is a real cost. You can't call `log.info(...)` from a callback, a `Future`, a background thread, or a Java library without threading the effect runtime through. You can't log in a constructor, an `AutoCloseable.close()`, or any other place where effects are inconvenient.

`zio-blocks-telemetry` takes a different position: logging and metrics are inherently synchronous, low-level operations. They should work anywhere, with no ceremony. You import the package, call `log.info(...)`, and it works. The library is responsible for making that cheap, not for constraining where you can use it.

This is the same approach `logback` and `java.util.logging` take, but with structured attributes, zero boxing on the fast path, and a modern Scala macro interface.

### The provider-instrument-processor pipeline

Every signal (log, span, metric) flows through the same three-stage pipeline:

```
Call site
    |
    v
[Provider]  -- creates and configures instruments --
    |                                                  LoggerProvider
    |                                                  TracerProvider
    |                                                  MeterProvider
    v
[Instrument]  -- the thing you call at call sites --
    |                                                  Logger  (wraps LogRecord)
    |                                                  Tracer  (wraps Span)
    |                                                  Counter / Histogram / Gauge
    v
[Processor]  -- receives the completed signal --
    |                                                  LogRecordProcessor
    |                                                  SpanProcessor
    |                                                  MetricReader (pull-based)
    v
[Exporter]  -- sends data out of process --
    |                                                  OtlpJsonLogExporter
    |                                                  OtlpJsonTraceExporter
    |                                                  OtlpJsonMetricExporter
    v
OTLP collector / file / console
```

Processors are composable: you can chain several. A `LoggerProvider` built with `.addLogRecordProcessor(console).addLogRecordProcessor(otelExporter)` will call both processors for every log record.

The `log` object and the `Global*` singletons sit in front of this pipeline. They hold a reference to the current provider (an `AtomicReference`) and forward calls through it. You can swap the provider at runtime via `install()` without touching any call sites.

### Zero-allocation design

Two separate things must both be cheap: the fast path when a level is disabled, and the slow path when it's enabled.

**Disabled level fast path.** The first check in every `log.*` call is:

```scala
if (severity.number >= log.globalMinLevel) { ... }
```

`globalMinLevel` is a `@volatile Int`. Reading it costs roughly one memory fence. If the level is disabled, the compiler has already eliminated all argument evaluation (the arguments are passed by-name or inlined via macro). No string is built, no object is allocated.

**Enabled level slow path.** When a level is enabled, attribute pairs need to be assembled into a `LogRecord`. The library uses a pooled `AttributeBuilder` (one per thread, reused), backed by parallel primitive arrays. Primitive values (Long, Double, Boolean) are stored as unboxed longs using type discriminator bytes. A `(String, Long)` attribute doesn't box the Long.

For metrics, `Counter` uses a `ConcurrentHashMap[Attributes, LongAdder]`. `LongAdder` is Java's striped counter, designed for high-concurrency increment without contention. `Gauge` uses `AtomicLong` storing the double bits directly. `Histogram` uses `ReentrantLock` per attribute set because it needs to update count, sum, min, max, and bucket counts atomically.

**What actually allocates on the slow path:**
- `Attributes.empty` is a singleton: no allocation
- Attribute builders are pooled: no allocation per record
- The `LogRecord` itself is allocated (unavoidably), but only when the level is enabled
- `SpanContext` stores trace ID as two `Long` fields, not a `UUID` object

### Global singletons vs explicit provider wiring

The three global entry points (`log`, `trace`, `metric`) work without any setup. On first use, each creates a sensible default:

- `log` creates a `ConsoleLogRecordProcessor` that writes to stdout
- `trace` creates an `InMemorySpanProcessor` that stores spans in a ring buffer (useful for testing)
- `metric` creates a basic `MeterProvider` with a `MetricReader`

For tests and exploratory code, the defaults are exactly right. For production, call `install()` once at startup to replace each default with your configured provider.

The global singletons are intentionally not safe to use across threads during `install()`. Call `install()` before starting any request processing. After installation, the `AtomicReference` read on every call is safe and cheap.

---

## Part 2: Logging Deep Dive

### Getting started

The simplest possible start:

```scala
import zio.blocks.telemetry._

log.info("server started")
```

No imports beyond the package. No provider setup. Output goes to stdout as human-readable text. This is intentional: the zero-configuration default is production-safe for development.

Moving to a more realistic setup:

```scala
import zio.blocks.telemetry._

// Structured attributes alongside the message
log.info("user signed in", "user_id" -> "u-7890", "method" -> "oauth2")

// Numbers are unboxed on the fast path
log.info("payment processed", "amount_cents" -> 4999L, "currency" -> "EUR")

// Exceptions get type, message, and stacktrace automatically
log.error("payment failed", new IllegalStateException("card declined"))

// Mix freely
log.warn(
  "upstream degraded",
  "service" -> "inventory",
  "latency_ms" -> 850L,
  new RuntimeException("timeout")
)
```

Adding a file output at startup:

```scala
import zio.blocks.telemetry._

// Human-readable text to one file, OTLP JSON to another
log.writer(TextLogFormatter, FileLogWriter("logs/app.log"))
log.writer(JsonLogFormatter, FileLogWriter("logs/app.jsonl"))
```

Both outputs are active simultaneously. `writer()` is additive.

### Macro mechanics explained

Every `log.*` method is a Scala inline macro. The macro generates something like this for `log.info("msg", "key" -> 42L)`:

```
Conceptual expansion of:
  log.info("request received", "user_id" -> userId, "attempt" -> 3)

Expands to (pseudocode):
  if (Severity.Info.number >= log.globalMinLevel) {
    val state = log.getState()
    if (state != null && Severity.Info.number >= state.effectiveLevel(<current class name>)) {
      val now = EpochClock.epochNanos()
      val builder = AttributeBuilderPool.get()
        .put("code.filepath", "<source file>")
        .put("code.namespace", "<current package>")
        .put("code.function", "<current method>")
        .put("code.lineno", <line number>.toLong)
      // for each enrichment argument, the macro calls the right builder method:
      builder.put("user_id", userId)      // resolved as (String, String)
      builder.put("attempt", 3.toLong)    // resolved as (String, Int) -> toLong
      // reads annotations from scoped context
      val annotations = LogAnnotations.get()
      // reads span context from ContextStorage
      val spanCtx = state.logger.currentSpanContext()
      state.logger.emitRaw(now, Severity.Info, "INFO", "request received",
                           builder, traceIdHi, traceIdLo, spanId, traceFlags,
                           resource, scope, None)
    }
  }
```

The important things the macro does:
- Captures `code.filepath`, `code.namespace`, `code.function`, `code.lineno` at compile time
- Resolves each enrichment argument type at compile time and calls the specific builder method (no runtime dispatch on the fast path)
- For rate-limited variants (`infoEvery`, `warnAtMost`), synthesizes a `val counter` or `val lastEmit` at the call site so each call site has independent state

For `*Every(N, msg)`, the macro synthesizes:

```scala
// Synthesized at call site by the macro
val _counter_42 = new java.util.concurrent.atomic.AtomicLong(0L)

// At each call:
if (_counter_42.getAndIncrement() % N == 0) { log.emit(...) }
```

For `*AtMost(windowMs, msg)`, it's:

```scala
val _lastEmit_77 = new java.util.concurrent.atomic.AtomicLong(0L)

val now = System.currentTimeMillis()
val last = _lastEmit_77.get()
if (now - last >= windowMs && _lastEmit_77.compareAndSet(last, now)) { log.emit(...) }
```

### Custom enrichment types

The `LogEnrichment[A]` typeclass lets the macro delegate argument resolution to your code.

Say you want to log `UUID` values directly:

```scala
import zio.blocks.telemetry._
import java.util.UUID

// The implicit must be in scope at log call sites
implicit val uuidEnrichment: LogEnrichment[(String, UUID)] =
  new LogEnrichment[(String, UUID)] {
    def enrich(record: LogRecord, value: (String, UUID)): LogRecord =
      record.copy(
        attributes = record.attributes ++ Attributes.of(
          AttributeKey.string(value._1), value._2.toString
        )
      )
  }

// Now this compiles and works
val requestId = UUID.randomUUID()
log.info("request started", "request_id" -> requestId)
```

For `java.time.Instant`:

```scala
import zio.blocks.telemetry._
import java.time.Instant

implicit val instantEnrichment: LogEnrichment[(String, Instant)] =
  new LogEnrichment[(String, Instant)] {
    def enrich(record: LogRecord, value: (String, Instant)): LogRecord =
      record.copy(
        attributes = record.attributes ++ Attributes.of(
          AttributeKey.string(value._1), value._2.toString
        )
      )
  }

log.info("event occurred", "timestamp" -> Instant.now())
```

**Performance note.** The built-in enrichments for `(String, String)`, `(String, Long)`, `(String, Int)`, `(String, Double)`, and `(String, Boolean)` go through the attribute builder's primitive path: no `AttributeValue` boxing. Custom enrichments through `LogEnrichment` go through `record.copy(attributes = ...)`, which does allocate an `Attributes` merge. For very hot paths, prefer the built-in types.

### Multiple outputs simultaneously

Every call to `log.writer(formatter, writer)` adds another output. The state is maintained inside the `log` object and composited into the active logger:

```scala
import zio.blocks.telemetry._

// Setup at startup; all three are active simultaneously
log.writer(TextLogFormatter, FileLogWriter("logs/app.log"))
log.writer(JsonLogFormatter, FileLogWriter("logs/app.jsonl"))
// The OTEL exporter is a LogRecordProcessor, added via LoggerProvider (see Part 5)
```

Internally, `log.writer(...)` creates a `FormattedLogRecordProcessor` (which pairs a formatter with a writer) and calls `rebuildState()`, which reconstructs the active `Logger` with the accumulated processor list. The original provider-based processors are preserved alongside the writer processors.

To remove all file writers:

```scala
log.clearWriters()
```

This shuts down each `FormattedLogRecordProcessor` cleanly and reverts to processor-only routing.

### Log output format comparison

The same call:

```scala
log.warn("slow query", "table" -> "orders", "duration_ms" -> 450L)
```

**TextLogFormatter output:**
```
2026-05-14T09:30:00.123Z WARN  [PaymentService.handleRequest:87] slow query {table="orders", duration_ms=450}
```

**JsonLogFormatter output:**
```json
{"timeUnixNano":"1747216200123000000","severityNumber":13,"severityText":"WARN","body":{"stringValue":"slow query"},"attributes":[{"key":"code.filepath","value":{"stringValue":"PaymentService.scala"}},{"key":"code.namespace","value":{"stringValue":"com.example.PaymentService"}},{"key":"code.function","value":{"stringValue":"handleRequest"}},{"key":"code.lineno","value":{"intValue":"87"}},{"key":"table","value":{"stringValue":"orders"}},{"key":"duration_ms","value":{"intValue":"450"}}]}
```

The JSON format follows the OTLP log data model directly, making it compatible with any OTLP-aware log processor (Grafana Loki, OpenTelemetry Collector, etc.).

The text formatter caches the timestamp prefix per second (the part before milliseconds) using two `@volatile` fields, so it avoids repeated date formatting work on high-throughput paths.

### Testing with telemetry

For unit tests, you usually want to assert that specific log calls happened. The cleanest approach is a custom `LogRecordProcessor`:

```scala
import zio.blocks.telemetry._
import scala.collection.concurrent.TrieMap
import java.util.concurrent.CopyOnWriteArrayList

class CapturingLogProcessor extends LogRecordProcessor {
  val records = new CopyOnWriteArrayList[LogRecord]()

  def onEmit(record: LogRecord): Unit = records.add(record)
  def shutdown(): Unit = records.clear()
  def forceFlush(): Unit = ()
}

// In your test setup:
val capturing = new CapturingLogProcessor()

val loggerProvider = LoggerProvider.builder
  .addLogRecordProcessor(capturing)
  .build()

log.install(loggerProvider.get("test"))

// Call the code under test
MyService.processPayment(...)

// Assert on the captured records
val warns = capturing.records.toArray.collect {
  case r: LogRecord if r.severity == Severity.Warn => r
}
assert(warns.exists(_.body.value.contains("payment")))
```

Don't forget to restore state between tests:

```scala
log.uninstall()  // reverts to default console logger
```

---

## Part 3: Distributed Tracing Deep Dive

### Trace lifecycle

When you call `tracer.span("name") { span => ... }`, here's the complete sequence:

1. **Read parent context.** `contextStorage.get()` returns `Option[SpanContext]`. If a span is already active on this thread (set by a parent `span` call), it becomes the parent.

2. **Sampler decision.** `sampler.shouldSample(...)` is called with the parent context, new trace ID bits, span name, and kind. The result is one of `Drop`, `RecordOnly`, or `RecordAndSample`.

3. **Drop path.** If `Drop`, your block runs with `Span.NoOp`. All `setAttribute`, `addEvent`, `setStatus` calls are no-ops. Zero allocation.

4. **Record path.** A `RecordingSpan` is built with a new `SpanContext`. The span gets:
   - The parent's `traceIdHi`/`traceIdLo` if a valid parent exists, or a fresh random pair
   - A fresh random `spanId`
   - `traceFlags` set to `sampled` (for `RecordAndSample`) or `none` (for `RecordOnly`)

5. **Processors on start.** Each `SpanProcessor.onStart(span)` is called.

6. **Context storage scoping.** `contextStorage.scoped(Some(span.spanContext)) { f(span) }` runs your block. While your block is executing, any inner `span` calls will see this span as their parent.

7. **End and export.** When the block exits (normally or via exception), `span.end()` records the end timestamp. Then `SpanProcessor.onEnd(spanData)` is called on each processor. The OTLP exporter's `onEnd` enqueues the `SpanData` in the `BatchProcessor`.

```
tracer.span("checkout") { span =>
    |
    +-- sampler.shouldSample() -> RecordAndSample
    |
    +-- SpanProcessor.onStart(span)  [each processor]
    |
    +-- contextStorage.scoped(Some(span.spanContext)) {
    |       tracer.span("validate-cart") { ... }  // sees checkout as parent
    |       tracer.span("charge-card")  { ... }   // sees checkout as parent
    |   }
    |
    +-- span.end()
    |
    +-- SpanProcessor.onEnd(spanData)  [each processor]
         |
         +-- OtlpJsonTraceExporter.onEnd(spanData)
              |
              +-- BatchProcessor.enqueue(spanData)
                   |
                   (background flush every 5s or at maxBatchSize)
                   |
                   +-- HTTP POST /v1/traces to OTLP collector
```

### Sampling strategies

**`AlwaysOnSampler`** returns `RecordAndSample` for every span. Use this in development or when you have low enough traffic that 100% sampling is affordable. It's the default.

**`AlwaysOffSampler`** returns `Drop` for every span. Useful when you want tracing code paths to exist but not actually produce data, for instance in a test that doesn't care about traces.

**`ParentBasedSampler(root)`** defers to the parent's sampling decision. If there's no parent, it falls back to `root`. If there is a parent:
- Parent sampled (`traceFlags.isSampled == true`) → `RecordAndSample`
- Parent not sampled → `Drop`

This is the right choice for most production services. The gateway or entry service decides sampling, and all downstream services follow automatically. Configure it with `AlwaysOnSampler` as the root if you want the gateway to sample everything, or with a `RateLimitedSampler` (if you implement one) for head-based sampling:

```scala
import zio.blocks.telemetry._

val tracerProvider = TracerProvider.builder
  .setSampler(ParentBasedSampler(root = AlwaysOnSampler))
  .addSpanProcessor(yourExporter)
  .build()

trace.install(tracerProvider)
```

### SpanContext internals

```scala
final case class SpanContext(
  traceIdHi: Long,    // high 64 bits of the 128-bit trace ID
  traceIdLo: Long,    // low  64 bits of the 128-bit trace ID
  spanId: SpanId,     // AnyVal wrapping a Long
  traceFlags: TraceFlags, // AnyVal wrapping a Byte
  traceState: String,
  isRemote: Boolean
)
```

The 128-bit trace ID is stored as two `Long` fields rather than a `UUID` object. A `UUID` would be 32 bytes of heap allocation per span, plus GC pressure. Two `Long` fields on a case class cost nothing beyond the case class itself, and they're JIT-friendly (no dereference).

A fresh trace ID is generated by `TraceId.random()`, which calls `ThreadLocalRandom.current().nextLong()` twice. `ThreadLocalRandom` is faster than `Random` in concurrent scenarios because it doesn't share state between threads.

`SpanId` is an `AnyVal` wrapping a single `Long`, similarly generated by `ThreadLocalRandom.current().nextLong()`. At runtime on the JVM, `AnyVal`s are unboxed wherever possible, so a `SpanId` in a `SpanContext` field costs nothing extra.

`traceIdHex` is computed on demand by `TraceId.toHex(traceIdHi, traceIdLo)`, which formats the two longs into 32 hex characters. No caching: hex formatting is only needed when building headers or log output.

### Context propagation across process boundaries

Here's a complete round trip. The server receives an HTTP request with W3C `traceparent`, continues the trace, and the client injects context into an outbound call.

**HTTP server (receiving a trace):**

```scala
package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

// Simulate incoming HTTP headers
val incomingHeaders = Map(
  "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
)

// Extract SpanContext from the traceparent header
val parentCtx: Option[SpanContext] =
  W3CTraceContextPropagator.extract(incomingHeaders, (m, k) => m.get(k))

val tracer = trace.get("api-service")

// Wire the extracted context as the parent by scoping it in ContextStorage
val tracerProvider = TracerProvider.builder
  .addSpanProcessor(yourExporter)
  .build()

// Use the provider's context storage to set the remote parent
val contextStorage = tracerProvider.contextStorage

contextStorage.scoped(parentCtx) {
  tracer.span("handle-checkout", SpanKind.Server) { span =>
    span.setAttribute("http.method", "POST")
    span.setAttribute("http.route", "/checkout")
    // Your handler logic here
    processCheckout()
  }
}
```

**HTTP client (injecting trace into outbound call):**

```scala
package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

val tracer = trace.get("api-service")

tracer.span("call-inventory", SpanKind.Client) { span =>
  // Inject the current span's context into outbound headers
  val headers = W3CTraceContextPropagator.inject(
    span.spanContext,
    Map.empty[String, String],
    (carrier, k, v) => carrier + (k -> v)
  )

  // headers now contains: "traceparent" -> "00-<traceId>-<spanId>-01"
  httpClient.post("http://inventory-service/check", headers)
}
```

The `traceparent` header encodes: `{version}-{traceId32hex}-{spanId16hex}-{flags2hex}`. The inventory service receives this, calls `extract`, gets back a `SpanContext` with `isRemote = true`, and starts its own child span under the same trace.

For Zipkin/B3 compatibility, swap `W3CTraceContextPropagator` for `B3Propagator.single` or `B3Propagator.multi`.

### Testing traces

The default `trace` provider stores spans in an in-memory processor:

```scala
import zio.blocks.telemetry._

// Reset before each test
trace.clearSpans()

// Call code under test
val tracer = trace.get("test")
tracer.span("outer") { _ =>
  tracer.span("inner") { span =>
    span.setAttribute("result", "ok")
  }
}

// Inspect what was recorded
val spans = trace.collectedSpans

assert(spans.size == 2)
val inner = spans.find(_.name == "inner").get
assert(inner.attributes.get(AttributeKey.string("result")) == Some("ok"))

// Check parent-child relationship
val outer = spans.find(_.name == "outer").get
assert(inner.parentSpanContext.spanId == outer.spanContext.spanId)
```

`trace.collectedSpans` returns a `List[SpanData]` in the order spans ended. No setup needed; this works out of the box.

### Span-log correlation explained

When you log inside an active span, the logger automatically attaches `traceId` and `spanId` to the record. This works because both the `Logger` and the `Tracer` share the same `ContextStorage[Option[SpanContext]]`.

At provider creation time:

```scala
// TracerProvider.builder.build() creates a ContextStorage
val cs = ContextStorage.create[Option[SpanContext]](None)
val tracerProvider = new TracerProvider(resource, sampler, processors, cs)

// LoggerProvider can share the same ContextStorage
val loggerProvider = LoggerProvider.builder
  .setContextStorage(cs)  // <-- same instance
  .build()
```

When `tracer.span(...)` runs, it calls `contextStorage.scoped(Some(span.spanContext)) { ... }`. This scopes the span context for the duration of the block using `ScopedValue` (on JDK 25+) or `ThreadLocal`. Inside that block, `logger.currentSpanContext()` reads from the same storage and sees the active span. No explicit linkage code at the call site.

If you use the global singletons without explicitly sharing a `ContextStorage`, the default logger gets its own context storage, separate from the default tracer's. Span-log correlation only works automatically when you wire them to share one. The OTEL module's production setup example (Part 5) shows how to do this correctly.

---

## Part 4: Metrics Deep Dive

### Instrument selection guide

| What you're measuring | Instrument | Example |
|---|---|---|
| Monotonically increasing total | `Counter` | Total requests served |
| Value that can go up or down | `UpDownCounter` | Active connections, queue depth |
| Distribution of values | `Histogram` | Request latency, payload size |
| Current point-in-time value | `Gauge` | CPU usage, memory used, pool size |

More specifically:

- Use **`Counter`** when you're counting things that only ever increase: requests processed, errors encountered, bytes sent. Negative deltas are silently ignored.
- Use **`UpDownCounter`** when the value can decrease: pending jobs in a queue (enqueue = +1, dequeue = -1), active WebSocket connections.
- Use **`Histogram`** when you care about the distribution, not just the total: latency percentiles (p50, p95, p99), request sizes. Histograms record min, max, sum, count, and configurable bucket counts.
- Use **`Gauge`** when you're sampling the current value of something external: a JVM heap usage check, a pool size read from a connection pool object. Last-write wins.

```scala
import zio.blocks.telemetry._

val requests     = metric.counter("http.requests.total")
val activeConns  = metric.upDownCounter("http.connections.active")
val latency      = metric.histogram("http.request.duration_ms")
val heapUsed     = metric.gauge("jvm.heap.used_bytes")

// Counter: only increases
requests.add(1, "method" -> "GET", "status" -> "200")

// UpDownCounter: tracks net change
activeConns.add(1)   // connection opened
activeConns.add(-1)  // connection closed

// Histogram: records the value into the appropriate bucket
latency.record(42.5, "method" -> "GET")

// Gauge: always reflects the current value
heapUsed.record(Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory())
```

### Labeled instruments with pre-bound attributes

When you record against the same label combination at high frequency, pre-binding avoids the `Attributes` lookup on every call:

```scala
import zio.blocks.telemetry._

val requests = metric.counter("http.requests.total")

// Bind once, typically at initialization
val getOk   = requests.bind(Attributes.builder.put("method", "GET").put("status", "200").build)
val get404  = requests.bind(Attributes.builder.put("method", "GET").put("status", "404").build)
val postOk  = requests.bind(Attributes.builder.put("method", "POST").put("status", "201").build)

// Hot path: just add to the pre-bound LongAdder
def onGetOk():  Unit = getOk.add(1)
def onGet404(): Unit = get404.add(1)
def onPostOk(): Unit = postOk.add(1)
```

A `BoundCounter` holds a direct reference to the `LongAdder` for that attribute set. The call `add(1)` is just `adder.add(1)`: no map lookup, no attribute construction.

The same pattern works for histograms and gauges:

```scala
import zio.blocks.telemetry._

val latency    = metric.histogram("http.request.duration_ms")
val getLatency = latency.bind(Attributes.builder.put("method", "GET").build)

val poolSize     = metric.gauge("db.pool.size")
val primaryPool  = poolSize.bind(Attributes.builder.put("pool", "primary").build)

// Hot paths
def recordGetLatency(ms: Double): Unit = getLatency.record(ms)
def updatePrimaryPoolSize(n: Double): Unit = primaryPool.record(n)
```

### MetricData and collection

`Counter.collect()`, `Histogram.collect()`, and `Gauge.collect()` snapshot the current state of the instrument without resetting it:

```scala
import zio.blocks.telemetry._

val requests = metric.counter("http.requests.total")
requests.add(42, "method" -> "GET", "status" -> "200")
requests.add(7,  "method" -> "POST", "status" -> "201")

val data = requests.collect()
// data: MetricData.SumData(List(
//   SumDataPoint(Attributes{method="GET",status="200"}, startNanos, nowNanos, 42),
//   SumDataPoint(Attributes{method="POST",status="201"}, startNanos, nowNanos, 7)
// ))
```

Each `SumDataPoint` contains the full attribute set plus the accumulated value. The OTLP exporter's `collectFn` calls `.collect()` on each instrument you register:

```scala
package zio.blocks.telemetry.otel

val metricExporter = new OtlpJsonMetricExporter(
  config, resource, scope, sender,
  () => Seq(
    NamedMetric("http.requests.total",    "Total HTTP requests",   "1",  requests.collect()),
    NamedMetric("http.request.duration",  "Request duration",      "ms", latency.collect()),
    NamedMetric("db.pool.size",           "DB pool size",          "1",  poolSize.collect())
  )
)
```

The `collectFn` is called by the `BatchProcessor`'s flush task on the configured interval.

### Thread safety internals

| Instrument | Internal structure | Thread safety |
|---|---|---|
| `Counter` | `ConcurrentHashMap[Attributes, LongAdder]` | Lock-free reads, CAS on first write per label set |
| `UpDownCounter` | same as Counter, allows negative | same |
| `Gauge` | `ConcurrentHashMap[Attributes, AtomicLong]` | Lock-free: `AtomicLong.set(doubleToLongBits(v))` |
| `Histogram` | `ConcurrentHashMap[Attributes, State]` + `ReentrantLock` per State | `ReentrantLock` per label set during `record` and `collect` |

`LongAdder` is the right choice for `Counter` because it reduces contention under high concurrency: rather than competing for a single `AtomicLong`, threads maintain per-stripe counters and sum them on read. At high add rates, `LongAdder` outperforms `AtomicLong.addAndGet` significantly.

`Histogram` needs a lock because updating count, sum, min, max, and bucket counts is a multi-field operation. No single atomic primitive covers all of them.

---

## Part 5: Production Setup

### Full production wiring

The OTEL exporters live in `package zio.blocks.telemetry.otel` and are `private[otel]`. Your bootstrap code must be in the same package (or a class in that package):

```scala
package zio.blocks.telemetry.otel

import zio.blocks.telemetry._
import java.util.concurrent.TimeUnit

object Telemetry {

  def initialize(): (TracerProvider, LoggerProvider, MeterProvider, PlatformExecutor) = {
    val config = ExporterConfig(
      endpoint            = sys.env.getOrElse("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318"),
      headers             = parseHeaders(sys.env.getOrElse("OTEL_EXPORTER_OTLP_HEADERS", "")),
      timeout             = java.time.Duration.ofSeconds(10),
      maxQueueSize        = 4096,
      maxBatchSize        = 512,
      flushIntervalMillis = 5000L
    )

    val resource = Resource.create(
      Attributes.builder
        .put("service.name",    sys.env.getOrElse("OTEL_SERVICE_NAME", "my-service"))
        .put("service.version", sys.env.getOrElse("SERVICE_VERSION", "unknown"))
        .put("deployment.environment", sys.env.getOrElse("ENVIRONMENT", "development"))
        .build
    )

    val scope    = InstrumentationScope("my-service")
    val sender   = HttpSender.jdk(java.time.Duration.ofSeconds(10))
    val executor = PlatformExecutor.create()

    // Shared ContextStorage so log records inside spans get traceId/spanId
    val contextStorage = ContextStorage.create[Option[SpanContext]](None)

    // --- Tracing ---
    val traceExporter = new OtlpJsonTraceExporter(config, resource, scope, sender, executor)

    val tracerProvider = TracerProvider.builder
      .setSampler(ParentBasedSampler(root = AlwaysOnSampler))
      .addSpanProcessor(traceExporter)
      .setResource(resource)
      .setContextStorage(contextStorage)
      .build()

    trace.install(tracerProvider)

    // --- Logging ---
    val logExporter = new OtlpJsonLogExporter(config, resource, scope, sender, executor)

    val loggerProvider = LoggerProvider.builder
      .addLogRecordProcessor(logExporter)
      .setContextStorage(contextStorage)   // same storage for correlation
      .setResource(resource)
      .build()

    log.install(loggerProvider.get("my-service"), minSeverity = Severity.Info)

    // Optional: also write to a local file as backup
    log.writer(JsonLogFormatter, FileLogWriter("logs/app.jsonl"))

    // --- Metrics ---
    val meterProvider = MeterProvider.builder
      .setResource(resource)
      .build()

    metric.install(meterProvider)

    (tracerProvider, loggerProvider, meterProvider, executor)
  }

  // Call at JVM shutdown
  def shutdown(
    tracerProvider: TracerProvider,
    loggerProvider: LoggerProvider,
    meterProvider: MeterProvider,
    executor: PlatformExecutor
  ): Unit = {
    // Flush and close in dependency order:
    // 1. Stop accepting new data
    tracerProvider.forceFlush()
    loggerProvider.shutdown()
    // 2. Shut down the providers (calls shutdown on each processor/exporter)
    tracerProvider.shutdown()
    meterProvider.shutdown()
    // 3. Stop the scheduler last
    executor.shutdown()
    log.clearWriters()
  }

  private def parseHeaders(raw: String): Map[String, String] =
    if (raw.isEmpty) Map.empty
    else raw.split(',').flatMap { pair =>
      pair.split('=') match {
        case Array(k, v) => Some(k.trim -> v.trim)
        case _           => None
      }
    }.toMap
}
```

Use it from your main entry point:

```scala
object Main {
  def main(args: Array[String]): Unit = {
    val (tracerProvider, loggerProvider, meterProvider, executor) =
      zio.blocks.telemetry.otel.Telemetry.initialize()

    sys.addShutdownHook {
      zio.blocks.telemetry.otel.Telemetry.shutdown(
        tracerProvider, loggerProvider, meterProvider, executor
      )
    }

    log.info("application started")
    // ... your app
  }
}
```

### ExporterConfig tuning guide

The default values work for most services. Here's when to change them:

**High-throughput services (>1000 RPS):**
```scala
ExporterConfig(
  maxQueueSize        = 8192,   // larger buffer for spikes
  maxBatchSize        = 1024,   // send bigger payloads less often
  flushIntervalMillis = 5000L   // keep at 5s; collector handles large batches fine
)
```

**Low-latency services (real-time data required):**
```scala
ExporterConfig(
  maxQueueSize        = 1024,
  maxBatchSize        = 128,    // smaller batches = lower end-to-end latency
  flushIntervalMillis = 1000L   // flush more frequently
)
```

**Development / debugging:**
```scala
ExporterConfig(
  maxQueueSize        = 256,
  maxBatchSize        = 32,
  flushIntervalMillis = 500L    // see data almost immediately
)
```

The `maxQueueSize` and `maxBatchSize` interact: if records arrive faster than you export, the queue fills. When full, the oldest items are dropped (and a message is written to stderr). `maxQueueSize` should be at least `maxBatchSize * 4` to absorb transient spikes without dropping.

`timeout` is per HTTP request. If your OTLP collector is slow or flaky, a low timeout means you drop data rather than blocking. 10 seconds is usually right for LAN deployments; 30 seconds for cross-region.

### BatchProcessor behavior

`BatchProcessor` runs inside the `PlatformExecutor`, which uses a virtual-thread-per-task executor for export tasks. This means:

- The scheduled flush (every `flushIntervalMillis`) runs on a virtual thread
- Export HTTP calls and their retries run on virtual threads, so sleeping during retry backoff doesn't pin a platform thread
- Queue overflow drops the oldest item, prints to stderr, and continues

The retry schedule uses exponential backoff: attempt 0 waits 1s, attempt 1 waits 2s, attempt 2 waits 4s... up to 30s maximum, for `maxRetries` (default 5) total attempts. Non-retryable failures (e.g., 400 Bad Request from the collector) are dropped immediately.

On `shutdown()`, `BatchProcessor` cancels the periodic flush, does a final synchronous flush of all pending items, then shuts down the export executor. Retries still happen during shutdown.

### Graceful shutdown order

Shut down in this order:

1. **Stop accepting new work.** Close your HTTP listener or message consumer.
2. **`forceFlush()` on `TracerProvider`.** Ensures all in-flight spans are exported.
3. **`loggerProvider.shutdown()`.** Flushes the log exporter's batch queue.
4. **`tracerProvider.shutdown()`.** Shuts down span processors.
5. **`meterProvider.shutdown()`.** Shuts down metric reader.
6. **`executor.shutdown()`.** Stops the scheduled executor.

Don't shut down the executor before the providers; the batch processor's retry threads need it.

### Environment-based configuration

The standard OTLP environment variables map naturally:

```scala
val config = ExporterConfig(
  endpoint = sys.env.getOrElse("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318"),
  headers  = sys.env.get("OTEL_EXPORTER_OTLP_HEADERS")
    .map(parseOtlpHeaders)
    .getOrElse(Map.empty),
  timeout  = sys.env.get("OTEL_EXPORTER_OTLP_TIMEOUT")
    .map(s => java.time.Duration.ofMillis(s.toLong))
    .getOrElse(java.time.Duration.ofSeconds(30))
)

val serviceName = sys.env.getOrElse("OTEL_SERVICE_NAME", "my-service")

// OTLP headers format: "key1=value1,key2=value2"
def parseOtlpHeaders(raw: String): Map[String, String] =
  raw.split(',').flatMap(_.split('=') match {
    case Array(k, v) => Some(k.trim -> v.trim)
    case _           => None
  }).toMap
```

For minimum log level:

```scala
val minLevel = sys.env.get("LOG_LEVEL").flatMap {
  case "TRACE" => Some(Severity.Trace)
  case "DEBUG" => Some(Severity.Debug)
  case "INFO"  => Some(Severity.Info)
  case "WARN"  => Some(Severity.Warn)
  case "ERROR" => Some(Severity.Error)
  case _       => None
}.getOrElse(Severity.Info)

log.install(logger, minSeverity = minLevel)
```

---

## Part 6: Integration Patterns

### With HTTP frameworks

A typical middleware pattern extracts incoming trace context, starts a server span, and injects context into outbound calls. Here's the conceptual shape (adapting to your specific HTTP library):

```scala
package zio.blocks.telemetry.otel

import zio.blocks.telemetry._

// Incoming request middleware
def traceIncoming[Req, Resp](
  request: Req,
  getHeader: (Req, String) => Option[String],
  handle: Req => Resp
): Resp = {
  val parentCtx = W3CTraceContextPropagator.extract(request, getHeader)
  val tracer    = trace.get("http-server")

  // Use the provider's contextStorage to scope the remote parent
  // Then start a child span under it
  tracer.span("http.request", SpanKind.Server) { span =>
    // Attach standard HTTP attributes
    // ... then run the handler
    handle(request)
  }
}

// Outbound call wrapper
def traceOutgoing[Resp](
  name: String,
  call: Map[String, String] => Resp
): Resp = {
  val tracer = trace.get("http-client")
  tracer.span(name, SpanKind.Client) { span =>
    val headers = W3CTraceContextPropagator.inject(
      span.spanContext,
      Map.empty[String, String],
      (c, k, v) => c + (k -> v)
    )
    call(headers)
  }
}
```

For the incoming case, propagating the remote parent into the tracer's `ContextStorage` before starting the span requires access to the `TracerProvider`'s `contextStorage` field. This is why `contextStorage` is exposed on `TracerProvider`.

### With existing Java logging (SLF4J/JUL)

There's no bridge today, but the shape would be a `SLF4JLogRecordProcessor` that consumes `LogRecord` from `zio-blocks-telemetry` and forwards it to the SLF4J backend, or vice versa. If you need Java library logs captured alongside your Scala logs, the simplest current approach is a Logback appender that writes to your file alongside `zio-blocks-telemetry`'s file output.

### Cross-platform considerations

The core telemetry module (`zio-blocks-telemetry`) compiles for JVM and Scala.js. What works on both:

- `log.*`, `trace.*`, `metric.*`
- `TracerProvider`, `LoggerProvider`, `MeterProvider` builders
- `Sampler`, `SpanContext`, `Attributes`, `MetricData`
- `TextLogFormatter`, `JsonLogFormatter`
- All the in-memory processors and default providers

JVM-only:
- `FileLogWriter`: uses `FileChannel`, not available in JS
- `ContextStorage` using `ScopedValue`: JDK 25+ specific; on Scala.js, a simpler mutable-variable implementation is used
- `PlatformExecutor`: uses `ScheduledExecutorService` with virtual threads
- The entire `zio-blocks-telemetry-otel` module (OTLP HTTP export, `BatchProcessor`)

If you're writing cross-platform code that uses telemetry, keep your call sites in the shared module and put all provider wiring in JVM-specific code.

---

## Part 7: FAQ / Troubleshooting

**"Why don't I see any log output?"**

Two common causes:

1. The global minimum level is filtering it out. Check `log.globalMinLevel`. Default is `Severity.Trace.number` (1), so everything passes. If you called `log.install(logger, minSeverity = Severity.Info)`, then trace and debug calls are suppressed.

2. The logger was installed but the processor chain is empty. Verify your `LoggerProvider` builder has at least one processor:
   ```scala
   LoggerProvider.builder
     .addLogRecordProcessor(new ConsoleLogRecordProcessor)  // don't forget this
     .build()
   ```

3. A namespace override is suppressing your package. Check for `log.setMinSeverity(...)` calls.

**"Why is my log call not compiling?"**

You're probably passing a type that has no `LogEnrichment` instance. Common culprits:
- `Float` (convert to `Double` with `.toDouble`)
- `Short`, `Byte`, `Char` (convert to `Long` or `String`)
- `UUID`, `Instant` (provide a `LogEnrichment[(String, YourType)]` implicit)
- Custom case classes (same: provide an implicit)

The compiler error will say something like `no implicit value for LogEnrichment[(String, UUID)]`.

**"How do I log a UUID, Instant, or custom type?"**

Define an implicit in your package object or companion:

```scala
import zio.blocks.telemetry._
import java.util.UUID

implicit val uuidEnrichment: LogEnrichment[(String, UUID)] =
  (record, kv) => record.copy(
    attributes = record.attributes ++ Attributes.of(AttributeKey.string(kv._1), kv._2.toString)
  )
```

Then `log.info("action", "id" -> someUuid)` compiles and works.

**"What's the overhead of a disabled log level?"**

Exactly one volatile read (`globalMinLevel`) plus, if the level passes that check, one array scan through the namespace overrides (usually empty, so O(1)). Both happen before any argument is evaluated. No string formatting, no object allocation, no method calls on your arguments.

**"Can I use this with ZIO, Cats Effect, or any other effect system?"**

Yes. The library is effect-free. `log.info(...)` is a plain synchronous call. Call it from anywhere: inside `ZIO.succeed`, inside `IO.apply`, inside a `Future`, inside a background thread. The only threading concern is that `ContextStorage` uses `ScopedValue` for span correlation, which is scoped to the current call stack. If you hop threads between starting a span and logging inside it, the log may not see the active span context. Most effect systems either stay on one thread or have ways to propagate context.

**"Why are the OTEL exporter classes private[otel]?"**

To enforce that the only way to construct and wire an `OtlpJsonTraceExporter` is from within the `zio.blocks.telemetry.otel` package. This prevents partial or incorrectly wired configurations from being assembled in arbitrary user code. Your bootstrap object, placed in that package, has full access. Everything downstream uses the installed global providers and never needs to import the exporter types.

**"How do I test my logging and tracing?"**

For logging: use a custom `LogRecordProcessor` that stores records (see "Testing with telemetry" in Part 2), install it via `log.install(...)`, run your code, assert on the captured records.

For tracing: use `trace.collectedSpans` and `trace.clearSpans()`. The default in-memory processor accumulates spans automatically.

**"What happens when the export queue fills up?"**

When `queueSize` exceeds `maxQueueSize`, `BatchProcessor.dropOldestIfOverCapacity()` polls the head of the queue (the oldest item), decrements the size, and prints a warning to stderr:

```
[zio-blocks-telemetry] BatchProcessor queue full (2048). Dropping oldest item.
```

The new item is still enqueued. This is a best-effort, head-dropping strategy. You won't get backpressure; callers are never blocked. If you see frequent drops, increase `maxQueueSize` or decrease `flushIntervalMillis`.

**"Is this virtual-thread safe?"**

Yes. `ContextStorage` on JDK 25+ uses `ScopedValue`, which is the JDK 25 native mechanism for per-virtual-thread context propagation. Each virtual thread inherits its `ScopedValue` bindings from the thread that spawns it, so if you start a virtual thread inside a span's block, the child thread sees the same span context. The `BatchProcessor` export threads also use virtual threads via `Executors.newVirtualThreadPerTaskExecutor()`, so retry sleeps don't pin platform threads.

---

## Where to Go Next

- **Complete API reference:** [Telemetry Reference](../reference/telemetry.md) for all types, methods, and parameter documentation
- **Installation and quick start:** same reference doc, Installation section
- **Context and dependency injection:** [Context Reference](../reference/context.md) for integrating `OtelContext` with the Context module
- **Resource management:** [Compile-Time Resource Safety with Scope](compile-time-resource-safety-with-scope.md) for managing provider lifetimes with Scope
