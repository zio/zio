# LogRecordProcessor

> A destination for log records — each registered processor receives every log your application emits, to print, export, or discard.

A `LogRecordProcessor` is a destination for your logs. Every time you call `log.info(...)`, the finished [`LogRecord`](./log-record.md) is handed to each registered processor, and what happens next is entirely up to that processor: print it, ship it to a backend, forward it into another logging library, or drop it.

The type exists because where logs belong depends on your deployment, which the library can't know. Registering destinations is how you tell it — and you can register several, each receiving the same record, which is how logs reach your console and a remote backend at once.

Most applications never implement one, because the module ships the common destinations. `ConsoleLogRecordProcessor` prints readable text to stdout — the default, and also the fastest path: when it's the only processor registered, the `Logger` skips building a `LogRecord` and formats straight from the raw values. `LogRecordProcessor.noop` accepts every record and discards it, for tests or as a placeholder. And for formatted output — JSON, a file, your own shape — [`log.writer(formatter, writer)`](./index.md) builds a processor for you out of a [`LogFormatter`](./log-formatter.md) and a [`LogWriter`](./log-writer.md), which is less code than implementing this trait.

Write your own to reach somewhere the module doesn't cover — an OTLP collector, a vendor's API, or an existing logging framework you're migrating from.

```scala
trait LogRecordProcessor extends AutoCloseable {
  def onEmit(logRecord: LogRecord): Unit   // fires for every record the Logger lets through
  def shutdown(): Unit                     // flush and release; called by LoggerProvider.shutdown()
  def forceFlush(): Unit                   // flush buffered records now
  override def close(): Unit = shutdown()

  def minimumLevel: Int = 1                // lowest Severity.number accepted; default accepts all
}

object LogRecordProcessor {
  val noop: LogRecordProcessor  // ignores every record
}
```

`onEmit` is the method that matters — it runs once per log, **on the thread that logged**, before your code continues. So keep it quick. If you send each record over the network from inside `onEmit`, every `log.info` in your application waits for that round trip; buffer records instead and ship them in batches from a background thread, then flush what's left when `shutdown` or `forceFlush` is called.

`minimumLevel` lets a processor say "only send me warnings and worse" as a `Severity` number. It's a volume hint, not a filter applied per processor: the `Logger` takes the lowest `minimumLevel` across every registered processor and, for anything below it, doesn't even build a `LogRecord` — so a debug line no destination wants costs almost nothing. Anything above that shared floor is delivered to every processor, including ones whose own `minimumLevel` is higher, so a processor that must not see debug records has to re-check `record.severity` in `onEmit`.

Your own processor implements those three methods — with nothing buffered, `shutdown` and `forceFlush` can stay empty — and registers on the global [`log`](./index.md):

```scala
import zio.blocks.telemetry._

val exporter = new LogRecordProcessor {
  def onEmit(r: LogRecord): Unit = println(s"[EXPORT] ${r.severity.text} ${r.body.value}")
  def shutdown(): Unit           = ()
  def forceFlush(): Unit         = ()
}

log.addProcessor(exporter)
log.info("checkout complete")
```

`log.addProcessor` is the easiest route: your processor joins the destinations already registered, with nothing to rebuild — when you build a provider yourself, pass processors to `LoggerProvider.builder.addLogRecordProcessor(...)` instead. Either way, an exception your processor throws is caught and printed to stderr, and the remaining processors still receive the record, so one broken destination can't take down your logging.
