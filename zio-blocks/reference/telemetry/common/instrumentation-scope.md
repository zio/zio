# InstrumentationScope

> Identifies the library or component that produced a signal, so a backend can tell your telemetry apart from a dependency's.

`InstrumentationScope` records which library or component produced a signal. It's what lets a backend tell spans from your own code apart from spans emitted by a dependency, and group or filter by either.

```scala
final case class InstrumentationScope(
  name:       String,
  version:    Option[String] = None,
  attributes: Attributes     = Attributes.empty
)
```

You rarely name the type: it's built for you from the name you pass to `TracerProvider#get`, `LoggerProvider#get`, or `MeterProvider#get`, and every [`Span`](../tracing/span.md), [`SpanData`](../tracing/span-data.md), [`LogRecord`](../logging/log-record.md), [`Tracer`](../tracing/tracer.md), and [`Meter`](../metrics/meter.md) then carries it.
