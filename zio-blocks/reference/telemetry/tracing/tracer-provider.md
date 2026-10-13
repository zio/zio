# TracerProvider

> Root factory for distributed tracing: produces Tracer instances sharing a Resource, Sampler, SpanProcessors, and ContextStorage.

`TracerProvider` exists so tracing is configured once, in a single place. You set the service identity ([`Resource`](../common/resource.md)), the sampling policy ([`Sampler`](./sampler.md)), and where finished spans go ([`SpanProcessor`](./span-processor.md)s) when you build it at startup; from then on every [`Tracer`](./tracer.md) it hands out — and every span they create — shares that one configuration, so all your telemetry carries a consistent identity and policy. You build it once, install it, and rarely touch it again.

```scala
final class TracerProvider private[telemetry] (...) extends AutoCloseable {
  def get(name: String, version: String = ""): Tracer  // a Tracer for a named scope

  def shutdown(): Unit        // shut down every processor; call once at exit
  def forceFlush(): Unit      // ask processors to export buffered spans now
  override def close(): Unit  // alias for shutdown() — satisfies AutoCloseable
}

object TracerProvider {
  def builder: TracerProviderBuilder
}

final class TracerProviderBuilder private[telemetry] (...) {
  def setResource(resource: Resource): TracerProviderBuilder
  def setSampler(sampler: Sampler): TracerProviderBuilder
  def addSpanProcessor(processor: SpanProcessor): TracerProviderBuilder
  def setContextStorage(contextStorage: ContextStorage[Option[SpanContext]]): TracerProviderBuilder
  def build(): TracerProvider
}
```

## Usage

Build a provider once at startup, set only what you need — a `Resource` for your service identity, a `Sampler`, and the `SpanProcessor`s that export your spans — then `get` a `Tracer` from it. Unset fields take defaults: `Resource.default` (whose `service.name` is the placeholder `unknown_service`) and `AlwaysOnSampler`, which records every span.

Do add a processor, though. The default processor list is **empty**, so a provider you build yourself samples spans and then discards them — nothing keeps or exports them. The in-memory buffer behind `trace.collectedSpans` belongs to the global `trace` object's own provider, not to yours:

```scala
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .addSpanProcessor(SpanProcessor.noop) // replace with an OTLP exporter
  .build()

val tracer = provider.get("com.example.payments")
tracer.span("process-payment") { span =>
  span.setAttribute("payment.id", "pay-001")
}

provider.shutdown()
```

Most applications never hold the provider directly. Install it once on the global `trace` object at startup and use [`trace.span`](./index.md) everywhere, removing the setup burden from individual call sites:

```scala
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "catalog")))
  .addSpanProcessor(SpanProcessor.noop) // replace with your exporter
  .build()

trace.install(provider)
```

Each `get` builds a new `Tracer` — there is no caching — so take one per component at startup and hold it, rather than calling `get` inside a request. Passing a `version` alongside the name records it on the scope, which is how you tell spans from two releases of the same library apart.

## Lifecycle

Call `shutdown()` once when the app exits — it forwards to every registered processor so each can flush what it is holding and release its resources. `forceFlush()` does the flushing without the release, for when you want buffered spans exported now.

`shutdown()` does not disable the provider: it still hands out tracers and still records spans afterwards, they just reach processors that have already been shut down. Treat it as the last thing you call, not a switch you can toggle. Since a provider is `AutoCloseable` — `close()` is an alias for `shutdown()` — you can also let the language make that call:

```scala
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(TracerProvider.builder.addSpanProcessor(SpanProcessor.noop).build()) { provider =>
  provider.get("com.example").span("startup")(_ => ())
}
```

## Trace–Log Correlation

Logs emitted inside a span are correlated with it automatically: a `TracerProvider` and a `LoggerProvider` share the same default context storage, so every log record carries that span's trace and span IDs with no wiring. (Override `setContextStorage` on both only to isolate correlation — e.g. in tests.) See the [Telemetry module index](../index.md).
