# SpanContext

> Propagatable span identity: 128-bit trace ID (two Longs), SpanId, TraceFlags, trace state, and isRemote.

`SpanContext` is the propagatable identity of a span — the 128-bit trace ID (split into two `Long` fields to avoid boxing), the `SpanId`, the `TraceFlags`, a W3C trace-state string, and an `isRemote` flag that marks contexts extracted from an upstream request. It exists so telemetry from one request can be correlated across services and signals.

You rarely touch it directly: [`Tracer`](./tracer.md) generates one per span and logging stamps the active context onto every record automatically. Crossing a process boundary is explicit — a `Propagator` from the otel module injects it into outgoing headers and extracts it on the way in — so reach for it when you cross a boundary yourself. `SpanContext.invalid` is the sentinel for "no active span".

```scala
final case class SpanContext(
  traceIdHi:   Long,        // high 64 bits of the 128-bit trace ID
  traceIdLo:   Long,        // low  64 bits of the 128-bit trace ID
  spanId:      SpanId,      // AnyVal wrapping a Long
  traceFlags:  TraceFlags,  // AnyVal wrapping a Byte
  traceState:  String,      // W3C tracestate header value
  isRemote:    Boolean      // true when extracted from an incoming request
) {
  def isValid:    Boolean   // true only when BOTH the trace ID and span ID are non-zero
  def isSampled:  Boolean   // true when the sampled bit of traceFlags is set
  def traceIdHex: String    // 32-char lowercase hex trace ID
}

object SpanContext {
  val invalid: SpanContext  // sentinel: all-zero IDs, not sampled, not remote
  def create(traceIdHi: Long, traceIdLo: Long, spanId: SpanId, traceFlags: TraceFlags,
             traceState: String, isRemote: Boolean): SpanContext
}
```

`SpanContext` is read from `ContextStorage` by `Tracer.currentSpan` and by the logger on every emit for automatic trace–log correlation. [`SpanData`](./span-data.md) carries two of them: the span's own context and its parent's, where `SpanContext.invalid` marks a root span with no parent.
