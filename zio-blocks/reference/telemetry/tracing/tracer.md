# Tracer

> Reference page for Tracer, the span-creation engine in the telemetry module's tracing area.

`Tracer` creates spans within a single instrumentation scope — a named library or component. Call `Tracer#span` to wrap a block of work in a timed, attributed span; the tracer consults the configured [`Sampler`](./sampler.md), propagates the parent context so nested spans inherit the right trace, and notifies each [`SpanProcessor`](./span-processor.md) as spans start and end. 

Library code takes a `Tracer` — obtained from a [`TracerProvider`](./tracer-provider.md) — to tag its spans with its own scope; application code usually prefers the global [`trace.span`](./index.md), which manages a tracer for you. A `Tracer` is always produced by a `TracerProvider` and cannot be constructed directly.

```scala
final class Tracer private[telemetry] (...) {
  val instrumentationScope: InstrumentationScope
  val resource: Resource

  // Wrap a block in a span — three progressive overloads
  def span[A](name: String)(f: Span => A): A
  def span[A](name: String, kind: SpanKind)(f: Span => A): A
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A

  // Manual span creation (advanced)
  def spanBuilder(name: String): SpanBuilder

  // The active span context, or None outside any span
  def currentSpan: Option[SpanContext]
}
```

## Usage

Obtain a `Tracer` from a provider (or `trace.get` in application code), then wrap work in `span`. The three overloads add control progressively: `span(name)` defaults to `SpanKind.Internal`, `span(name, kind)` sets the kind, and `span(name, kind, attributes)` also seeds initial attributes. Nested `span` calls inherit the parent trace automatically:

```scala
import zio.blocks.telemetry._

val tracer = TracerProvider.builder.build().get("com.example.orders")

tracer.span("process-order", SpanKind.Server) { span =>
  span.setAttribute("order.id", "ord-123")

  tracer.span("persist-order", SpanKind.Client) { inner =>
    inner.setAttribute("db.system", "postgresql")
  }
}
```

For advanced needs — a manual span lifecycle via [`SpanBuilder`](./span-builder.md) (local-only, never exported) or reading the active context with `currentSpan` (a [`SpanContext`](./span-context.md)) — see those pages. For all normal work, `span` is enough.
