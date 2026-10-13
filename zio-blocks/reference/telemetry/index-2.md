# Logging

> Logging index: the log entry point, LoggerProvider, Logger, LogRecord, and supporting types for structured logging in the telemetry module.

Logging records what your application did as it runs, so you can understand its behavior and diagnose problems after the fact. Unlike a plain `println`, these logs are **structured** — each entry carries typed key/value fields (an order id, a duration) you can search and filter on, not just a line of text — and **severity-leveled**, tagged `trace`, `debug`, `info`, `warn`, `error`, or `fatal` so you can keep production quiet and turn up detail only when debugging. Each log is also tied back to the trace that produced it, so from one log line you can pull up the whole request it belongs to. 

You write logs through the `log` object: call `log.info("order placed")` (or `debug`, `warn`, `error`, …) anywhere in your code and it works immediately, with no setup. Behind each call, `log` automatically records where the log came from — the file, class, method, and line — and stamps it with the trace and span of whatever [`trace.span`](../tracing/index.md) you happen to be inside. That stamping is what lets logs and traces line up later: no need to thread a request id through your code by hand.

Out of the box those records print to stdout as readable text, so `log.info(...)` shows up on your console with nothing configured. When you want a different destination you add one once, at application startup: `log.writer(...)` adds a channel with a formatter of your choosing — JSON, say — through a [`LogWriter`](./log-writer.md), while `log.addProcessor(...)` wires up a pipeline that ships records to a backend for storage and search. Those two are additive, so the default console output stays alongside whatever you add. `log.install(...)` is the replacing move: it swaps in a whole logger, dropping the default console processor, any writers added earlier, and any per-package severity overrides.

After the message you attach context, and this is where structured logging pays off. The common case is key/value pairs — `log.info("order placed", "orderId" -> "ord-123", "amount" -> 99L)` — which become searchable fields on that entry instead of being buried in the text, so later you can query "all logs where `orderId = ord-123`". Pass a `Throwable` and its type, message, and stack trace are captured for you.

A few other values mean something specific rather than becoming a field: a [`Severity`](./severity.md) overrides the entry's level, an [`Attributes`](../common/attributes.md) set adds many fields at once, and a plain `String` replaces the message body. And when you want to log one of your own types directly, give it a [`LogEnrichment`](./log-enrichment.md) instance that tells `log` how to turn it into fields.

## Example Usage

Logging's core job is to **emit structured, correlated logs**. Point `log` at a writer once, then emit records inside your spans; each record carries its typed key-value context and the active trace and span IDs automatically.

```scala
import zio.blocks.telemetry._

trace.install(TracerProvider.builder.build())
log.writer(TextLogFormatter, StdoutWriter)

trace.span("checkout") { _ =>
  log.info("order placed", "orderId" -> "ord-123", "amount" -> 99L)
  log.warn("inventory low", "sku" -> "sku-42", "remaining" -> 3L)
}
```

## Emit Structured, Severity-Leveled Records

Six severities — `trace`, `debug`, `info`, `warn`, `error`, `fatal` — cover the whole scale.

```scala
object log {
  def trace(message: String, enrichments: Any*): Unit
  def debug(message: String, enrichments: Any*): Unit
  def info(message: String, enrichments: Any*): Unit
  def warn(message: String, enrichments: Any*): Unit
  def error(message: String, enrichments: Any*): Unit
  def fatal(message: String, enrichments: Any*): Unit
}
```

Pass typed key/value pairs for structured attributes, a `Throwable` to capture an exception, or an `Attributes` set to merge many values at once.

```scala
import zio.blocks.telemetry._

log.info("order placed", "orderId" -> "ord-123", "amount" -> 99L, "express" -> true)
log.debug("cache lookup", "hit" -> false)

try throw new RuntimeException("payment declined")
catch { case e: Throwable => log.error("charge failed", "orderId" -> "ord-123", e) }
```

## Limit Log Volume at Hot Call Sites

Two rate-limiting families, each spanning all six severities, keep high-frequency sites quiet.

```scala
object log {
  def <level>Every(every: Int, message: String, enrichments: Any*): Unit
  def <level>AtMost(intervalMillis: Long, message: String, enrichments: Any*): Unit
}
```

The `Every` family is count-based — `<level>Every(every, message, enrichments*)` emits on every Nth call at that site. The `AtMost` family is time-based — `<level>AtMost(intervalMillis, message, enrichments*)` emits at most once per interval at that site. Each site gets its own counter and clock, keyed by its file and line into a fixed table of 4096 slots; two distant sites can land in the same slot, in which case they share a counter and one of them logs less often than its `every` suggests.

```scala
import zio.blocks.telemetry._

// Count-based: one line for every 100th retry
log.warnEvery(100, "retrying upstream call", "endpoint" -> "/inventory")

// Time-based: at most one line per 5 seconds, whatever the call rate
log.infoAtMost(5000L, "processing batch", "size" -> 512L)
log.errorAtMost(1000L, "connection pool exhausted")
```

## Attach Scoped Annotations

Attach key/value pairs to every record emitted inside a block with `annotated`.

```scala
object log {
  def annotated[A](annotations: (String, String)*)(f: => A): A
}
```

The pairs reach every record from the block, including calls in nested methods, without threading them through each `log.*` call.

```scala
import zio.blocks.telemetry._

log.annotated("requestId" -> "req-42", "tenant" -> "acme") {
  log.info("started")   // both annotations attached
  log.info("finished")  // both annotations attached
}
```

## Correlate Logs with the Active Span

When a `log.*` call runs inside a `trace.span`, the record is stamped with the enclosing span's trace and span IDs automatically, because logging and tracing share the same `ContextStorage`. No extra wiring is required.

```scala
import zio.blocks.telemetry._

trace.span("checkout") { _ =>
  log.info("order validated", "orderId" -> "ord-123") // carries the checkout span's IDs
}
```

## Filter by Severity

Every log carries a `Severity`, and a **minimum-severity floor** decides which ones actually get recorded: anything below the floor is dropped — cheaply, before the record is even built. The floor starts at `Trace`, so everything passes until you raise it. You use this to control noise: run production at `Info` (dropping the `trace`/`debug` chatter) and turn detail back up only where and when you need it.

```scala
object log {
  def setMinSeverity(severity: Severity): Unit
  def setMinSeverity(prefix: String, severity: Severity): Unit
  def clearMinSeverity(prefix: String): Unit
  def clearAllOverrides(): Unit
  def withMinSeverity[A](severity: Severity)(f: => A): A
}
```

`setMinSeverity(severity)` sets one floor for the whole application. `setMinSeverity(prefix, severity)` overrides it for a package — matched against the call site's namespace — and works both ways: raise the floor on a chatty dependency to quiet it, or lower it on the package you're debugging to see more, without touching the rest of the app. `clearMinSeverity(prefix)` removes one override and `clearAllOverrides()` removes them all, back to the global floor. Set the global floor first: `setMinSeverity(severity)` also discards every prefix override, so calling it after the overrides silently wipes them.

When several prefixes match a call site, the longest one wins — so `"com.example"` at `Warn` plus `"com.example.orders"` at `Debug` gives you debug logs from the orders package and warnings from everything else under `com.example`. Matching is a plain string prefix on the enclosing class name, not a package-boundary check, so `"com.acme"` also matches `com.acmecorp`.

```scala
import zio.blocks.telemetry._

log.setMinSeverity(Severity.Info)                       // drop trace/debug globally
log.setMinSeverity("com.acme.noisy", Severity.Warn)     // quiet a chatty dependency
log.setMinSeverity("com.example.orders", Severity.Debug) // more detail where you're debugging
```

These floors stay in effect until you change them. When you only need extra detail around a specific operation, `withMinSeverity(severity) { … }` lowers the floor for just that block and restores it afterward — no cleanup needed.

```scala
import zio.blocks.telemetry._

log.withMinSeverity(Severity.Trace) {
  log.trace("visible only inside this block")
}
```

## Route Output

A record isn't useful until it leaves the process. Routing decides two things: how each record becomes text — a [`LogFormatter`](./log-formatter.md), plain lines or JSON — and where those bytes go — a `LogWriter`, such as stdout, stderr, or a file. There are two ways to wire this up: a simple console writer for local development, or a processor pipeline for production.

```scala
object log {
  def writer(formatter: LogFormatter, logWriter: LogWriter): Unit
  def clearWriters(): Unit
  def install(logger: Logger, minSeverity: Severity = Severity.Trace): Unit
  def addProcessor(processor: LogRecordProcessor): Unit
  def removeAll(): Unit
}
```

`log.writer(formatter, writer)` is the simple path: pair a formatter with a writer to add one console sink. It's additive — call it again to send the same records to a second destination (say, human-readable text to stdout and JSON to stderr) — and `log.clearWriters()` removes them all.

For production you usually want a pipeline that batches records and ships them to a backend for storage and search. `log.install(logger)` swaps in a fully configured [`Logger`](./logger.md) (built from a `LoggerProvider` with its export processors) in place of whatever was registered before — including the default console output, any writers, and any prefix overrides — `log.addProcessor(processor)` appends a single [`LogRecordProcessor`](./log-record-processor.md) to the current backend without disturbing the rest, and `log.removeAll()` detaches everything — `log.*` calls become no-ops until you add an output again.

```scala
import zio.blocks.telemetry._

// Human-readable text to stdout, JSON to stderr
log.writer(TextLogFormatter, StdoutWriter)
log.writer(JsonLogFormatter, StderrWriter)

// Or install a processor-based backend
val logger = LoggerProvider.builder
  .addLogRecordProcessor(new ConsoleLogRecordProcessor)
  .build()
  .get("com.example")
log.install(logger, Severity.Info)
```

## See Also

- [Telemetry Guide](../../../guides/telemetry-guide.md) — logging data flow, rate limiting, and production patterns
- [Telemetry Reference](../index.md) — module overview and all three pillars
- [Common Types](../common/index.md) — `Attributes`, `AttributeKey`, `Resource`, and `InstrumentationScope`
