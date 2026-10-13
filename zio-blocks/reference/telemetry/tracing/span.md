# Span

> A mutable, thread-safe unit of work in a distributed trace.

`Span` is a mutable, thread-safe unit of work in a distributed trace — it records the name, kind, attributes, events, and outcome of one operation. You obtain a live `Span` from the higher-order [`trace.span`](./index.md) (or `Tracer#span`), which creates it, samples it, and ends it for you — you rarely create one directly.

Here is the full interface; the sections below cover the methods you reach for day to day:

```scala
trait Span {
  // Identity — fixed at creation
  def spanContext: SpanContext
  def name: String
  def kind: SpanKind

  // Annotate — no-ops once the span has ended
  def setAttribute[A](key: AttributeKey[A], value: A): Unit
  def setAttribute(key: String, value: String): Unit
  def setAttribute(key: String, value: Long): Unit
  def setAttribute(key: String, value: Double): Unit
  def setAttribute(key: String, value: Boolean): Unit
  def addEvent(name: String): Unit
  def addEvent(name: String, attributes: Attributes): Unit
  def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit
  def setStatus(status: SpanStatus): Unit

  // Lifecycle — managed for you by trace.span
  def isRecording: Boolean
  def end(): Unit
  def end(endTimeNanos: Long): Unit
  def toSpanData: SpanData
}
```

## Usage

Open a span with `trace.span`: it hands your block a live `Span` and ends it automatically when the block returns. Inside, annotate the span with attributes, events, and a status:

```scala
import zio.blocks.telemetry._

trace.span("http.request", SpanKind.Server) { span =>
  span.setAttribute("http.method", "GET")
  span.setAttribute("http.status_code", 200L)
  span.addEvent("auth.passed")
  span.setStatus(SpanStatus.Ok)
}
```

`trace.span` manages the lifecycle: it ends the span when the block returns, and on end the span becomes an immutable [`SpanData`](./span-data.md) snapshot that `SpanProcessor`s receive (end-user code seldom reads it via `toSpanData` directly). For the rare cases `trace.span` can't cover — a custom start timestamp, links, or crossing an async boundary — [`SpanBuilder`](./span-builder.md) starts a span you `end()` yourself; it bypasses the sampler and processors, so prefer `trace.span`.

## Annotating a Span

The four convenience overloads cover the common value types; the typed `setAttribute[A](key, value)` form accepts any [`AttributeKey`](../common/attribute-key.md), including the sequence-valued ones (`AttributeKey.stringSeq` and friends) that have no shorthand.

Everything you do with a span happens inside the `trace.span` block, before it ends. Once the span ends, every `setAttribute`, `addEvent`, and `setStatus` call is silently ignored. If the sampler dropped the span you receive `Span.NoOp` — a no-op you can still call freely, so no guards are needed; check `isRecording` only to skip expensive annotation work for a dropped or ended span. Reading from a `NoOp` gives you blanks rather than errors: an invalid `spanContext`, an empty `name`, `SpanKind.Internal`, and `isRecording = false`.

### Attributes

Attach typed key-value pairs with `setAttribute` — there are convenience overloads for `String`, `Long`, `Double`, and `Boolean`. Setting the same key twice replaces the value:

```scala
import zio.blocks.telemetry._

trace.span("db.query") { span =>
  span.setAttribute("db.system", "postgresql")
  span.setAttribute("db.rows", 12L)
  span.setAttribute("db.cached", false)
  span.setAttribute("db.latency_ms", 3.14)
}
```

### Events

Record a named point-in-time event with `addEvent`, optionally with its own attributes:

```scala
import zio.blocks.telemetry._

trace.span("payment.process") { span =>
  span.addEvent("validation.passed")
  span.addEvent("retry", Attributes.of(AttributeKey.long("attempt"), 2L))
}
```

### Status

Mark the outcome with `setStatus` — `SpanStatus.Ok` on success, `SpanStatus.Error(reason)` on failure (the default is `SpanStatus.Unset`):

```scala
import zio.blocks.telemetry._

trace.span("charge") { span =>
  val paid = true
  if (paid) span.setStatus(SpanStatus.Ok)
  else span.setStatus(SpanStatus.Error("gateway timeout"))
}
```

## Identity

A span's `name`, `kind`, and [`spanContext`](./span-context.md) are set at creation and never change. `spanContext` is the span's identity — the trace and span IDs that let all the telemetry from one request be tied together.

You rarely touch it. Log correlation happens for you: logging stamps the active span's IDs onto every record automatically. Carrying the identity to another service is an explicit step — a `Propagator` from the otel module writes it into outgoing headers. Reach for `span.spanContext.traceIdHex` directly only in the low-level case — correlating with a system that isn't wired in, or crossing a boundary the instrumentation doesn't cover.

## Ending a Span

`trace.span` ends the span for you, including when the block throws — the span is closed on the way out and the exception propagates. Nothing sets an error status for you, though: a span whose block threw still reports `SpanStatus.Unset` unless you call `setStatus(SpanStatus.Error(...))` yourself.

Ending is idempotent. The first `end()` wins and any later call is silently ignored, so a `finally` block that ends a span already ended by `trace.span` is harmless. The `end(endTimeNanos)` overload exists for when you are replaying or bridging spans and need to state the finish time rather than take the clock's.
