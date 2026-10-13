# SpanStatus

> SpanStatus — the sealed enumeration recording a span's completion outcome (Unset, Ok, Error) in the telemetry module's tracing area.

`SpanStatus` records the outcome of the operation a [`Span`](./span.md) represents. The default is `SpanStatus.Unset`; instrumentation sets `Ok` on success or `Error(description)` on failure before the span's `end()`.

```scala
sealed trait SpanStatus
object SpanStatus {
  case object Unset                           extends SpanStatus  // default; outcome unknown or normal
  case object Ok                              extends SpanStatus  // explicit success
  final case class Error(description: String) extends SpanStatus  // explicit failure with description
}
```

Set the status on the live span with `Span#setStatus` at any point before it ends:

```scala
import zio.blocks.telemetry._

val provider = TracerProvider.builder.build()
val tracer   = provider.get("com.example")

tracer.span("process-payment") { span =>
  val ok = true // result of processing
  if (ok) span.setStatus(SpanStatus.Ok)
  else span.setStatus(SpanStatus.Error("payment-gateway-timeout"))
}

provider.shutdown()
```

`SpanStatus` becomes part of the exported [`SpanData`](./span-data.md) (`SpanData.status`) and is read by [`SpanProcessor`](./span-processor.md) implementations and test assertions.

## See Also

- [Span](./span.md) — the unit of work whose outcome `SpanStatus` records.
- [SpanKind](./span-kind.md) — the companion classification, set once at span creation.
- [Tracing](./index.md) — the tracing overview.
