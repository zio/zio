# SpanData

> Immutable snapshot of a finished span — the read-only record a SpanProcessor exports.

`SpanData` is an immutable, fully-populated snapshot of a finished span — its name, timing, attributes, events, status, and identity, captured when the span ends. You rarely handle it directly: it is the read-only record a [`SpanProcessor`](./span-processor.md) receives (`onEnd`) and serializes to a backend, and the value `trace.collectedSpans` returns for test assertions. You produce spans with [`trace.span`](./index.md), never `SpanData` itself.

```scala
final case class SpanData(
  name:                 String,
  kind:                 SpanKind,
  spanContext:          SpanContext,
  parentSpanContext:    SpanContext,        // SpanContext.invalid for a root span
  startTimeNanos:       Long,
  endTimeNanos:         Long,               // 0L if snapshotted before end()
  attributes:           Attributes,
  events:               List[SpanEvent],
  links:                List[SpanLink],
  status:               SpanStatus,
  resource:             Resource,
  instrumentationScope: InstrumentationScope
)
```

Each field mirrors what the span recorded; a `SpanProcessor` or exporter reads them to ship the span to a backend (OTLP or another format).
