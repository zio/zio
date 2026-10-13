# Logger

> Instance-level structured logger bound to one instrumentation scope; library code takes one, application code prefers the global log.

A `Logger` writes logs. It's tied to one name — the component or library it belongs to, like `"com.example.OrderService"` — and a [`LoggerProvider`](./logger-provider.md) hands you one; you never construct it yourself.

That name travels with every record, marking which part of the system produced it. It's what lets you tell your own logs from a library's and turn one noisy component down without touching the rest. Records also arrive tied to the trace they happened in, so any line leads back to the request that caused it.

Most application code shouldn't use a `Logger` directly — the global [`log`](./index.md) is easier and does more. A `Logger` earns its place when a library accepts one as a parameter: then it's an ordinary dependency, and a test can pass in one whose records it inspects.

```scala
final class Logger {
  // One method per severity: trace, debug, info, warn, error, fatal
  def info(body: String, attrs: (String, AttributeValue)*): Unit
  // def trace/debug/warn/error/fatal are identical, just with different severity

  def emit(logRecord: LogRecord): Unit           // send a pre-built record
  def currentSpanContext(): Option[SpanContext]  // active span, or None
}
```

## Emitting a Log

This is what a `Logger` is for. Pick the method matching the severity, pass a message, and add any attributes worth searching on later — the scope name and the active span's identity are attached for you:

```scala
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

val logger = provider.get("com.example.OrderService", "1.0.0")

logger.info("order placed", "orderId" -> AttributeValue.StringValue("ord-001"))
logger.warn("payment delayed", "durationMs" -> AttributeValue.LongValue(250L))
logger.error("checkout failed", "code" -> AttributeValue.LongValue(503L))

provider.shutdown()
```

Attributes must be wrapped: `AttributeValue.StringValue`, `LongValue`, `DoubleValue`, `BooleanValue`, or one of their `*SeqValue` forms for arrays. That verbosity is the cost of a bare `Logger` — the global [`log`](./index.md) accepts the same values as plain literals.

## Forwarding a Record You Already Have

`emit(record)` sends a [`LogRecord`](./log-record.md) you built yourself, as-is. Where `info(...)` fills in the timestamp, scope name, and span identity, `emit` adds nothing, so a hand-built record without span identity won't correlate with any trace. Use it to forward another framework's logs into the same destinations:

```scala
import zio.blocks.telemetry._

val logger = LoggerProvider.builder
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)  // a provider with no processor emits nothing
  .build()
  .get("com.example.Bridge")

logger.emit(
  LogRecord.builder
    .setSeverity(Severity.Warn)
    .setBody("from the legacy logger")
    .build
)
```

## Reading the Active Span

`currentSpanContext()` returns the span you're inside, or `None` if there isn't one. Correlation is automatic, so you only need this to carry trace IDs across a boundary yourself — an outgoing HTTP header, a queue message, an error response:

```scala
import zio.blocks.telemetry._

val logger = LoggerProvider.builder.build().get("com.example.Client")

val traceHeader: Option[String] = logger.currentSpanContext().map(_.traceIdHex)
```

To configure where these logs go, see [`LoggerProvider`](./logger-provider.md).
