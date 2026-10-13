# Tracing

> Tracing index: the trace entry point, TracerProvider, Tracer, Span, and supporting types for distributed tracing in the telemetry module.

Tracing follows a single request as it moves through your application — and across services — recording each step as a **span**: a timed unit of work with a name, attributes, and an outcome. Spans nest into a **trace**, a parent/child tree that shows where a request spent its time and where it failed. Because one trace can stretch across several services, it's how you answer "why was this request slow?" when the answer is three network hops away.

You create spans through the `trace` object: `trace.span("handle-request") { span => … }` wraps a block of work — it opens a span, runs the block, and closes the span automatically when the block returns. A `trace.span` opened inside another automatically becomes its child, so the tree builds itself with no manual wiring. Inside the block you annotate the span with attributes, events, and a status, and classify its role with a [`SpanKind`](./span-kind.md) when it crosses a service boundary.

With no setup, `trace` records every span into an in-memory buffer — the default for development and tests, where `trace.collectedSpans` hands back what was recorded. For production, call `trace.install(provider)` once at startup to send spans to a real backend (Jaeger, OTLP, …); a [`Sampler`](./sampler.md) then decides which spans to keep so you aren't exporting everything under load. The types behind `trace` — [`TracerProvider`](./tracer-provider.md), [`Tracer`](./tracer.md), [`Span`](./span.md), and the [`SpanProcessor`](./span-processor.md)s — are configured once at startup; day to day you just call `trace.span`.

## Open a Span and Record Work

Wrap a unit of work in `trace.span(name) { span => … }`.

```scala
object trace {
  def span[A](name: String)(f: Span => A): A
}
```

The block receives a live `Span`; set attributes, add timeline events, and set the completion status on it. The span closes automatically when the block returns.

```scala
import zio.blocks.telemetry._

trace.span("handle-request") { span =>
  span.setAttribute("http.route", "/orders")
  span.setAttribute("http.status_code", 200L)
  span.addEvent("validated")
  span.setStatus(SpanStatus.Ok)
}
```

## Classify a Span and Pre-Set Attributes

Two overloads add a [`SpanKind`](./span-kind.md) and, optionally, initial [`Attributes`](../common/attributes.md).

```scala
object trace {
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A
}
```

`SpanKind` marks a span's role in a trace (`Server`, `Client`, `Producer`, `Consumer`, or the default `Internal`), and the `Attributes` set stamps values known at creation.

```scala
import zio.blocks.telemetry._

trace.span("db-query", SpanKind.Client, Attributes.of(AttributeKey.string("db.system"), "postgresql")) { span =>
  span.setAttribute("db.statement", "SELECT * FROM orders")
}
```

## Nest Spans into a Trace Tree

A `trace.span` opened inside another automatically attaches to the enclosing span as its parent through the shared `ContextStorage`, so nested calls build a parent/child tree with no manual context passing.

```scala
import zio.blocks.telemetry._

trace.span("checkout", SpanKind.Server) { _ =>
  trace.span("load-cart") { child =>
    child.setAttribute("cart.items", 3L)
  }
  trace.span("charge-card")(_ => ())
}
```

## Scope Spans to an Instrumentation Library

Every span records which code produced it. Spans opened with `trace.span(...)` are all attributed to one scope named `"default"`, which is fine for an application tracing its own work — but it means spans from a library you depend on arrive indistinguishable from your own. `trace.get(name)` returns a [`Tracer`](./tracer.md) that stamps a name of your choosing on every span it records instead, so a backend can group them and someone debugging a slow request can see which component the time went to:

```scala
object trace {
  def get(name: String): Tracer
}
```

Calling this is the application's job, at startup: name the scope after the component it's for, then pass the `Tracer` to that component as a constructor parameter. A library should accept a `Tracer` rather than reach for the global `trace` itself — that keeps it testable in isolation and free of global state. Otherwise the two behave the same for opening spans: same overloads, same automatic parent nesting, same sampler and exporters.

```scala
import zio.blocks.telemetry._

val tracer: Tracer = trace.get("com.example.orders")

trace.clearSpans()
tracer.span("reserve-inventory")(_ => ())
trace.span("unscoped-work")(_ => ())

// the scope each span was recorded under
trace.collectedSpans.foreach(sd => println(sd.instrumentationScope.name))
// com.example.orders
// default
```

Order matters here. A `Tracer` is bound to whichever provider was installed when `trace.get` ran, while `trace.span` looks up the current provider on every call. Get your tracers *after* `trace.install(...)`, or a tracer created during startup will keep recording into the default in-memory buffer and its spans will never reach the exporter you installed afterwards.

## Inspect Recorded Spans

Until you install a provider, finished spans stay in memory where a test can read them back:

```scala
object trace {
  def collectedSpans: List[SpanData]
  def clearSpans(): Unit
}
```

`collectedSpans` returns each completed span as [`SpanData`](./span-data.md), oldest first, and `clearSpans()` empties the buffer — call it at the start of a test so you assert on that test's spans alone.

Two limits are worth knowing before you rely on this. The buffer is a ring of 1024 spans, so a long run silently overwrites its oldest entries; assert as you go rather than at the end of a large suite. And both methods read the buffer belonging to the **default** provider, so once you call `trace.install(...)`, they stop seeing new spans — a test that installs a provider has to collect through that provider's own processors instead.

## Install a Provider and Reset

`trace.install(provider)` swaps in a production `TracerProvider` — configured with a [`Resource`](../common/resource.md), a `Sampler`, and one or more `SpanProcessor` exporters.

```scala
object trace {
  def install(provider: TracerProvider): Unit
  def removeAll(): Unit
}
```

Build the provider once at startup and install it before any span is opened — name your service in the `Resource` so exported spans can be told apart from other services, and pick a `Sampler` to decide how many spans to keep:

```scala
import zio.blocks.telemetry._

trace.install(
  TracerProvider.builder
    .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "order-service")))
    .setSampler(AlwaysOnSampler)
    .build()
)

// every trace.span in the application now records through this provider
trace.span("handle-request", SpanKind.Server)(_.setAttribute("http.route", "/orders"))
```

`trace.removeAll()` swaps in a provider whose sampler drops everything. Your `trace.span` blocks still run — they just receive a no-op `Span` that records nothing — so removing tracing never changes what your code does, only what it reports. Note that it leaves the in-memory buffer untouched; use `clearSpans()` to empty that.

## See Also

- [Telemetry Guide](../../../guides/telemetry-guide.md) — tracing data flow, sampling, and production patterns
- [Telemetry Reference](../index.md) — module overview and all three pillars
- [Common Types](../common/index.md) — `Attributes`, `AttributeKey`, `Resource`, and `InstrumentationScope`
