# MeterProvider

> Configure metrics once for a service: name what produced them, hand out meters per component, and read every measurement back through one reader.

A `MeterProvider` is where metrics get configured, once, at startup. You build one, and it hands out the [`Meter`](./meter.md)s your components take.

It exists because two things can't be decided per instrument. The first is **identity**: an exported measurement has to say what produced it, or a backend shows a number with no owner — and repeating that on every counter would be both tedious and inconsistent. You set it once as a [`Resource`](../common/resource.md) on the provider. Unlike spans and log records, collected metric data does not carry it, so read `provider.resource` when you export and attach it there.

The second is **one place to read from**. Instruments end up scattered across components, and nothing useful happens until something collects them all together. The provider keeps the registry every meter joins, so a single `reader.collectAllMetrics()` returns every measurement from every scope beneath it — you never assemble a list of instruments yourself.

Most applications never hold one: they hand a provider to `metric.install(...)` at startup and use the global [`metric`](./index.md) everywhere after. Build one yourself when you want a `Resource` of your own, or a scope you can collect and shut down independently of the global one.

## Configuring Metrics for Your Service

Build the provider with a `Resource` naming your service:

```scala
import zio.blocks.telemetry._

val provider = MeterProvider.builder
  .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "payments")))
  .build()
```

Do set the name. Skipping it doesn't fail — the default `Resource` supplies `service.name = "unknown_service"` alongside SDK version attributes — but that placeholder is what a backend will show for every measurement you export.

## Getting a Meter per Component

Ask for a meter by the component's name; that name is what attributes its measurements:

```scala
import zio.blocks.telemetry._

val provider = MeterProvider.builder.build()

val meter: Meter = provider.get("com.example.payments")
```

The full signature is `get(name: String, version: String = "")`; passing a version records it on the scope, which is how you tell measurements from two releases of the same library apart. Asking twice for the same name *and* version returns the same meter, so components take theirs independently without coordinating. The provider also exposes the `Resource` you built it with as `provider.resource`. See [Meter](./meter.md) for what to do with a meter.

## Reading Everything Back

`reader` is a single [`MetricReader`](./metric-data.md) for the provider's whole lifetime, created at `build()` — there is nothing to register, and no second reader to keep in sync:

```scala
import zio.blocks.telemetry._

val provider = MeterProvider.builder.build()
provider.get("com.example").counterBuilder("ops").build().add(1L)

val snapshots: Seq[MetricData] = provider.reader.collectAllMetrics()
```

Each call aggregates what has accumulated so far into [`MetricData`](./metric-data.md) — one value per registered instrument, across every meter. This is a pull: nothing is sent anywhere until you or an exporter asks.

## Shutting Down

A provider is `AutoCloseable`, with `close()` calling `shutdown()`, so it fits `scala.util.Using.resource`:

```scala
import zio.blocks.telemetry._
import scala.util.Using

Using.resource(MeterProvider.builder.build()) { provider =>
  provider.reader.collectAllMetrics()
}
```

Unlike logging, there is nothing buffered here waiting to be flushed — metrics are read on demand, so the built-in reader's shutdown does no work today. Call it anyway at exit: it costs nothing, and it's what an export pipeline would hook into.
