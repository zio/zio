# LogRecord

> Immutable snapshot of a single log emission — the read-only value a LogRecordProcessor receives and a LogFormatter renders.

A `LogRecord` is one log entry as a value — everything a single `log.info(...)` call captured, bundled together: the message, its severity, when it happened, the key/value fields attached to it, and which request it belongs to.

The type exists because a log entry has to travel. From the call site it may go to your console, to a file, and to a backend that indexes it, and each of those destinations needs the whole story in one self-contained package — a bare string wouldn't carry the severity or the trace it came from. Because a record is immutable, every destination can read the same one safely.

You never build a `LogRecord` in normal use; the global [`log`](./index.md) (or a [`Logger`](./logger.md)) builds one for you on every call. You meet it when you extend the pipeline: it's the value handed to a [`LogRecordProcessor`](./log-record-processor.md)'s `onEmit` and to a [`LogFormatter`](./log-formatter.md), so writing either means reading fields off a record.

```scala
final case class LogRecord(
  timestampNanos:         Long,
  observedTimestampNanos: Long,
  severity:               Severity,
  severityText:           String,
  body:                   LogMessage,           // LogMessage.Simple(text), or a lazy template
  attributes:             Attributes,           // code.* source location + caller-supplied attrs
  traceIdHi:              Long,                 // 0L when no span is active
  traceIdLo:              Long,                 // 0L when no span is active
  spanId:                 Long,                 // 0L when no span is active
  traceFlags:             Byte,                 // 0x00 when not sampled
  resource:               Resource,
  instrumentationScope:   InstrumentationScope,
  throwable:              Option[Throwable] = None
) {
  def hasTraceId: Boolean  // traceIdHi != 0L || traceIdLo != 0L
  def hasSpanId:  Boolean  // spanId != 0L
}

object LogRecord {
  def builder: LogRecordBuilder
}
```

Each field mirrors what the emission recorded. The source location the macro captured arrives inside `attributes`, as the `code.filepath`, `code.namespace`, `code.function`, and `code.lineno` keys. The trace and span IDs are what tie this entry back to the request that produced it: they're filled in when the call ran inside an active span, and left as `0` when it didn't — so check `hasTraceId`/`hasSpanId` rather than comparing to zero yourself.
