# SpanBuilder

> Mutable builder for creating spans with explicit kind, start timestamp, links, or parent context outside the normal Tracer.span lifecycle.

`SpanBuilder` is a mutable builder for creating a [`Span`](./span.md) by hand, outside the automatic [`trace.span`](./index.md) lifecycle, with control the `span(name, kind, attributes)` overloads cannot express: an explicit start timestamp, links to spans in other traces, or a non-default parent context. 

Crucially, a span it starts bypasses the [`Tracer`](./tracer.md)'s [`Sampler`](./sampler.md) and [`SpanProcessor`](./span-processor.md) list — nothing samples it, no `onStart`/`onEnd` fires, and **it is never exported to a backend**. The span lives only in memory, readable via `toSpanData`, and you must `end()` it yourself. Use `SpanBuilder` for tests or local inspection; for any span that should reach your tracing pipeline, use `trace.span`.

```scala
object SpanBuilder {
  def apply(name: String): SpanBuilder
}

final class SpanBuilder {
  // Configuration
  def setKind(kind: SpanKind): SpanBuilder
  def setParent(parentContext: SpanContext): SpanBuilder
  def setAttribute[A](key: AttributeKey[A], value: A): SpanBuilder
  def addLink(link: SpanLink): SpanBuilder
  def setStartTimestamp(nanos: Long): SpanBuilder
  def setResource(resource: Resource): SpanBuilder
  def setInstrumentationScope(scope: InstrumentationScope): SpanBuilder

  // Finalization
  def startSpan(): Span                                  // parent's trace ID, or a random one at the root
  def startSpan(traceIdHi: Long, traceIdLo: Long): Span  // explicit trace ID
}
```

## Usage

Obtain a pre-configured builder from `Tracer.spanBuilder` — it fills in the tracer's `Resource` and `InstrumentationScope` — set the metadata the `span` overloads cannot, then `startSpan()` and `end()` the span in a `try`/`finally`:

```scala
import zio.blocks.telemetry._

val tracer = TracerProvider.builder.build().get("com.example")

val span = tracer.spanBuilder("enqueue-message")
  .setKind(SpanKind.Producer)
  .addLink(SpanLink(SpanContext.invalid, Attributes.empty)) // in practice, an upstream context
  .setStartTimestamp(System.nanoTime())
  .startSpan()

try span.setAttribute("message.id", "msg-456")
finally span.end()
```

`SpanBuilder(name)` also builds one standalone, without a tracer. It is rarely what you want — the span it produces is attributed to `Resource.empty` and the scope name `"default"` rather than to your service and component, on top of reaching no sampler or processor. Reach for it only where no tracer exists yet, such as bridging a span in from another system. Note also that `startSpan()` marks the span sampled whenever there is no valid parent, so a standalone span always claims the sampled bit.
