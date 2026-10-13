# LogWriter

> The sink that routes formatted log text to a destination: the built-in stdout, stderr, and no-op writers behind log.writer.

`LogWriter` is the sink half of console log output: it takes text that has already been formatted and pushes it to a destination — stdout, stderr, a file. Its partner is the [`LogFormatter`](./log-formatter.md), which turns a [`LogRecord`](./log-record.md) into that text. You rarely name a writer directly — you hand a formatter/writer pair to [`log.writer`](./index.md), and the built-ins below cover the usual destinations. Reach for a custom one only to send output somewhere the built-ins don't.

```scala
trait LogWriter {
  def write(content: CharSequence): Unit
  def flush(): Unit = ()
  def close(): Unit = ()
}
```

`write` receives one fully-formatted line; `flush` and `close` default to no-ops, so a simple writer overrides only `write`.

## Built-in Writers

Three singletons cover the common destinations:

| Writer | Destination |
|--------|-------------|
| `StdoutWriter` | `System.out` |
| `StderrWriter` | `System.err` |
| `NoopWriter` | Discards output — for benchmarking the formatting path |

## Example Usage

Pair a writer with a formatter and hand both to `log.writer`. It is additive — each call adds another channel, and every record is sent to all of them:

```scala
import zio.blocks.telemetry._

log.writer(TextLogFormatter, StdoutWriter)  // human-readable text to stdout
log.writer(JsonLogFormatter, StderrWriter)  // OTLP JSON to stderr

log.info("server ready", "port" -> 8080L)   // written to both channels
```

## Custom Writer

Implement the trait to send formatted output somewhere the built-ins don't — a file, say. Override `write`, plus `flush`/`close` if the destination holds resources:

```scala
import zio.blocks.telemetry._
import java.io.PrintWriter

val fileWriter = new LogWriter {
  private val out = new PrintWriter("app.log")
  def write(content: CharSequence): Unit = out.println(content)
  override def flush(): Unit             = out.flush()
  override def close(): Unit             = out.close()
}

log.writer(TextLogFormatter, fileWriter)
```
