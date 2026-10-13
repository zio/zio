# LoggerProvider

> Root factory for structured logging: produces Logger instances sharing a Resource, LogRecordProcessor pipeline, and ContextStorage.

A `LoggerProvider` is what makes [`Logger`](./logger.md)s. You build one at startup, and it hands out every logger your application uses.

It exists so that two questions get answered once instead of at every call site: *who is logging* — your service's identity, a [`Resource`](../common/resource.md) — and *where the logs go* — the [`LogRecordProcessor`](./log-record-processor.md)s that render or ship each record. Every logger the provider creates inherits both, so pointing your whole application at a different destination is a one-line change at startup rather than an edit everywhere you log.

Most applications never hold a provider directly: build one, hand a logger from it to `log.install(...)`, and use the global [`log`](./index.md) from then on. Reach for the provider itself when you need that startup configuration under your own control.

```scala
final class LoggerProvider private[telemetry] (...) extends AutoCloseable {
  def get(name: String, version: String = ""): Logger  // a Logger for a named scope

  def shutdown(): Unit        // shut down every processor; call once at exit
  override def close(): Unit  // alias for shutdown() — satisfies AutoCloseable
}

object LoggerProvider {
  def builder: LoggerProviderBuilder
}

final class LoggerProviderBuilder private[telemetry] (...) {
  def setResource(resource: Resource): LoggerProviderBuilder
  def addLogRecordProcessor(processor: LogRecordProcessor): LoggerProviderBuilder
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): LoggerProviderBuilder
  def build(): LoggerProvider
}
```

## Usage

Build a provider once at startup, set only what you need — a `Resource` for your service identity and the `LogRecordProcessor`s that handle your records — then `get` a `Logger` from it. Anything left unset takes a sensible default (`Resource.default`, and an empty pipeline until you add a processor):

```scala
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
  .addLogRecordProcessor(LogRecordProcessor.noop) // replace with a console writer or OTLP exporter
  .build()

val logger = provider.get("com.example.payments")
logger.info("order placed", "orderId" -> AttributeValue.StringValue("ord-001"))

provider.shutdown()
```

Note that `get` builds a logger rather than looking one up: call it twice with the same name and you get two separate `Logger` objects that behave identically. So call it once where a component is created and keep the result in a `val`, not inside a request handler that runs thousands of times a second — per-request calls produce the same log output, they just make throwaway objects for the garbage collector:

```scala
import zio.blocks.telemetry._

class PaymentService(provider: LoggerProvider) {
  private val logger = provider.get("com.example.payments")   // once, at construction

  def handle(orderId: String): Unit =
    logger.info("handling payment", "orderId" -> AttributeValue.StringValue(orderId))
}
```

The name you pass is what identifies where a log came from, so one logger per component — a service, a module — is the usual granularity.

Passing a `Logger` down through every constructor gets tedious, though. Register one on the global [`log`](./index.md) at startup instead, and the rest of your code can log without knowing a provider exists:

```scala
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "catalog")))
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

log.install(provider.get("com.example.catalog"))

log.info("server started")
```

## Lifecycle

Logs don't always leave your process the moment you write them. A processor that ships records to a backend usually batches them, so at any instant the most recent logs may still be sitting in a buffer — and if the process exits right then, they're gone. Those are exactly the logs you want when something went wrong at shutdown.

`provider.shutdown()` prevents that. It walks the registered processors in the order you added them and shuts each one down, which is a processor's cue to flush whatever it's holding and release what it owns — a file handle, a network connection. Call it once, on the way out:

```scala
import zio.blocks.telemetry._

val provider = LoggerProvider.builder
  .addLogRecordProcessor(LogRecordProcessor.noop)
  .build()

try log.install(provider.get("com.example"))
finally provider.shutdown()   // last logs flushed, resources released
```

That `finally` is the easy part to forget. So a provider implements `AutoCloseable` — the standard interface for "something that must be closed when you're done with it," the same one files and sockets use — with `close()` simply calling `shutdown()`. That lets you hand the cleanup to the language rather than writing it yourself. In Scala, `scala.util.Using.resource` does the calling:

```scala
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(LoggerProvider.builder.addLogRecordProcessor(LogRecordProcessor.noop).build()) { provider =>
  log.install(provider.get("com.example"))
  log.info("server started")
}   // close() — and so shutdown() — runs here, even if the block throws
```

The block marks how long the provider lives, and cleanup happens on the way out whether the code finishes normally or raises an exception. From Java the same interface makes a provider usable in try-with-resources.

## Trace–Log Correlation

This is the part you get for free. Write a log inside a span and it carries that span's trace and span IDs — no request id to thread through your functions, no logger to pass around, nothing to configure. A `LoggerProvider` and a [`TracerProvider`](../tracing/tracer-provider.md) read the same ambient context by default, so each is aware of the span the other opened:

```scala
import zio.blocks.telemetry._

trace.install(TracerProvider.builder.build())
log.install(
  LoggerProvider.builder
    .addLogRecordProcessor(new ConsoleLogRecordProcessor)  // a provider with no processor emits nothing
    .build()
    .get("com.example")
)

trace.span("checkout") { _ =>
  log.info("order validated", "orderId" -> "ord-123")  // stamped with the checkout span's IDs
  log.warn("inventory low", "sku" -> "sku-42")         // same trace, same span
}
```

That stamping is what makes a log backend useful: filter on one trace id and you have every log line from that single request, in order, across every component that touched it — and from any one of those lines you can open the trace it belongs to.

If you ever need to control that shared context yourself — isolating correlation between test cases, or fitting a custom runtime — pass one `ContextStorage` to `setContextStorage` on both providers.

For the full picture of how `LoggerProvider`, `TracerProvider`, and `MeterProvider` interconnect, see the [Telemetry module index](../index.md).
