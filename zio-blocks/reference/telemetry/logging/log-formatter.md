# LogFormatter

> How a LogRecord is rendered to text: the built-in human-readable and OTLP-JSON formatters behind log.writer.

`LogFormatter` renders a [`LogRecord`](./log-record.md) into text; its partner, the [`LogWriter`](./log-writer.md), routes that text to a destination. You rarely name a formatter directly — you hand a formatter/writer pair to [`log.writer`](./index.md), and the two built-ins below cover the common cases. Implement the trait yourself when your pipeline expects a shape neither produces.

## Built-in Formatters

Two singletons cover the usual output formats — both safe to share across threads, with `TextLogFormatter` caching its timestamp prefix for the current second:

| Formatter          | Output                                                                                                                                       |
|--------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `TextLogFormatter` | Human-readable — `2026-07-29T10:00:00.000Z INFO  [Svc.doWork:42] message {key=val}`                                                          |
| `JsonLogFormatter` | OTLP-compatible JSON — `{"timeUnixNano":"...","severityNumber":9,"severityText":"INFO","body":{"stringValue":"message"},"attributes":[...]}` |

Both render the record's timestamp, severity, source location (from the `code.*` attributes), message body, and an attached throwable's stack trace. They differ in what else they carry: `TextLogFormatter` prints the remaining attributes as a compact `{key=value}` list and drops the trace and span IDs, while `JsonLogFormatter` emits every attribute — including the four `code.*` entries — plus `traceId` and `spanId` when the record was written inside a span.

## Example Usage

Choosing a formatter is choosing who reads your logs. In development that's you, so pick `TextLogFormatter` — one aligned line per record, easy to scan in a terminal. In production it's a log collector, so pick `JsonLogFormatter` — every attribute stays a separate field the collector can index and query. Select it once at startup, alongside a [`LogWriter`](./log-writer.md) for the destination:

```scala
import zio.blocks.telemetry._

val isDevelopment = sys.env.get("ENV").forall(_ != "production")

if (isDevelopment) log.writer(TextLogFormatter, StdoutWriter)  // readable in a terminal
else log.writer(JsonLogFormatter, StdoutWriter)                // queryable by a collector

log.info("server ready", "port" -> 8080L)
```

You can also register both — `log.writer` is additive, so each call adds another channel and every record goes to all of them. That's how you keep readable console output while also emitting machine-readable JSON:

```scala
import zio.blocks.telemetry._

log.writer(TextLogFormatter, StdoutWriter)  // for the developer watching the terminal
log.writer(JsonLogFormatter, StderrWriter)  // for the collector tailing stderr

log.info("server ready", "port" -> 8080L)   // written to both channels
```

## Custom Formatter

Sometimes neither built-in shape fits — an existing ingest pipeline expects logfmt, or a report wants CSV. Implement the trait yourself and pass your formatter to `log.writer` like any built-in. Two methods are abstract, but only one of them is yours to write:

```scala
trait LogFormatter {
  def format(
    sb: StringBuilder, timestampNanos: Long, severity: Severity, severityText: String,
    body: String, builder: Attributes.AttributesBuilder,
    traceIdHi: Long, traceIdLo: Long, spanId: Long, traceFlags: Byte,
    throwable: Option[Throwable]
  ): Unit

  def formatRecord(sb: StringBuilder, record: LogRecord): Unit
}
```

`formatRecord` is the one that runs: a channel registered with `log.writer` renders each finished [`LogRecord`](./log-record.md) through it. Put your format there.

`format` serves a lower-allocation path that skips building a `LogRecord`, and the library uses it only for its own default console output — no formatter you register ever reaches it. You still have to define it to satisfy the trait, so mirror the scalar fields and move on; it cannot render attributes anyway, since the accessors for reading them off the builder are internal.

Both methods receive a fresh, empty `StringBuilder` from the caller, which writes it out and discards it. Append your output and return — the caller handles writing.

A CSV formatter — three comma-separated fields in `formatRecord`, with `format` mirroring them:

```scala
import zio.blocks.telemetry._

object CsvFormatter extends LogFormatter {
  def formatRecord(sb: StringBuilder, record: LogRecord): Unit =
    sb.append(record.timestampNanos).append(',').append(record.severityText).append(',').append(record.body.value)

  def format(
    sb: StringBuilder, timestampNanos: Long, severity: Severity, severityText: String,
    body: String, builder: Attributes.AttributesBuilder,
    traceIdHi: Long, traceIdLo: Long, spanId: Long, traceFlags: Byte,
    throwable: Option[Throwable]
  ): Unit = sb.append(timestampNanos).append(',').append(severityText).append(',').append(body)
}

log.writer(CsvFormatter, StdoutWriter)
log.info("order placed")
// 2026-07-31T13:23:25.137Z INFO  [Main$.main:20] order placed  <- default console output
// 1785504205137002324,INFO,order placed                        <- your channel
```

Two lines, because `log.writer` adds a channel rather than replacing one, and the default console output is still registered.

To render attributes too, pass an `AttributeVisitor` to `record.attributes.accept` — it fires once per attribute with the raw unboxed value, so nothing is boxed on the way out. Only the four scalar methods are abstract; the seq variants default to no-ops. Source location arrives among those attributes as `code.filepath`, `code.namespace`, `code.function`, and `code.lineno`. Decide which convention you want: `TextLogFormatter` lifts them into its `[Svc.doWork:42]` prefix and then skips those keys, while `JsonLogFormatter` emits them as ordinary attributes. Skip any key starting with `code.` if you render the location separately.
