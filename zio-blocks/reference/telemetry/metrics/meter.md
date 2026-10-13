# Meter

> Create the counters, histograms, and gauges one component records through — scoped to that component, and registered so collection finds them.

A `Meter` is where one component's [instruments](./instruments.md) come from. You take a meter under your component's name, create counters and histograms from it, and record through those.

It exists to answer a question a bare instrument name can't: *whose measurement is this?* A meter carries a scope name, so the instruments one component builds are grouped under it, and a reader can tell your `requests` counter from the one a library you depend on registered. (The two were never merged into one series — each instrument aggregates on its own — but without scopes there is nothing in the code that says which is which.)

The second problem it solves is reachability. Recording a number is useless if nothing can read it back, and an instrument only reaches collection if something registered it. A meter is that something: it is registered with its [`MeterProvider`](./meter-provider.md) when you obtain it, and it registers every instrument you build from it, so a measurement's path to `reader.collectAllMetrics()` is complete the moment you call `build()` — with no wiring step you could forget.

## Taking a Meter

Ask a provider for one by name, or `metric.get(name)` in application code. Use the component's package as the name:

```scala
import zio.blocks.telemetry._

val meter: Meter = MeterProvider.builder.build().get("com.example.server")
```

Asking twice for the same name gives you back the *same* meter, not a second one — meters are cached per scope. So separate call sites in one component can each take their meter without coordinating, and their instruments still land under a single scope.

## Creating an Instrument

Each of the four instrument kinds has a builder. Name it, optionally describe it and give it a unit, then `build()`:

```scala
import zio.blocks.telemetry._

val meter = MeterProvider.builder.build().get("com.example.server")

val requests = meter.counterBuilder("http.requests")
  .setDescription("Total HTTP requests")
  .setUnit("1")
  .build()
requests.add(1L, "method" -> "GET", "status" -> "200")

val latency = meter.histogramBuilder("request.latency").setUnit("ms").build()
latency.record(42.5, "route" -> "/api/orders")
```

The description and unit stay on the instrument — `collect()` returns data points only — so they reach a dashboard through whatever exporter reads the instrument, which is what lets it label an axis in milliseconds instead of showing bare numbers. Build each instrument once and hold the result — a `val` on the component that records through it. Building the same name twice gives you two registered instruments, which splits one logical metric into two series that no consumer can merge back together.

## Recording on a Hot Path

When the same label *names* repeat on every call, declaring them once keeps every call site consistent and short. `labeledCounter`, `labeledHistogram`, and `labeledGauge` fix the names at construction so callers pass just the values, positionally:

```scala
import zio.blocks.telemetry._

val meter = MeterProvider.builder.build().get("com.example")

val byRoute = meter.labeledCounter("http.requests", "method", "status")
byRoute.add(1L, "GET", "200")
```

See [Labeled Instruments](./labeled-instruments.md) for the trade-offs; for ordinary recording the tuple form above is simpler.

## Reporting a Value You Don't Push

Some numbers aren't events you count — they're state you can read at any time, like a cache's size or a pool's idle connections. Instead of pushing an update whenever it changes, `buildWithCallback` on the counter, up-down counter, and gauge builders makes an instrument that asks *you* for the value at collection time:

```scala
import zio.blocks.telemetry._

val meter = MeterProvider.builder.build().get("com.example")
val cache = scala.collection.mutable.Map("k" -> "v")

meter.gaugeBuilder("cache.entries").buildWithCallback { observer =>
  observer.record(cache.size.toDouble, Attributes.empty)
}
```

The block doesn't run when you build it — it runs once per collection, reading `cache.size` fresh each time, so the value can't go stale and you need no hook at every mutation. Keep it cheap and side-effect-free; it runs on the collecting thread. You can discard the returned instrument — the meter registered it, so collection finds it — and hold it only if you want to call its own `collect()` directly.

`buildWithCallback` returns an `ObservableCounter`, `ObservableUpDownCounter`, or `ObservableGauge` depending on the builder, and hands your block an `ObservableCallback` to record through. Histograms have no callback form, since a distribution has to see every observation as it happens.

Watch the type on the counters: both observable counters round what you report to a whole number, so a callback recording `1.5` is collected as `2`. Only `ObservableGauge` keeps the `Double` as given.

## Two Ways to Lose Measurements

Both come from an instrument that records into nothing:

1. **Constructing an instrument directly.** `Counter("http.requests", "", "")` compiles, because the companion `apply` is public. It records perfectly well into an object no meter registered, so `collectAllMetrics()` never sees it. Go through a meter or `metric.*` — the one case that justifies direct construction is a [histogram with custom bucket boundaries](./instruments.md), where you accept the loss of registration and call `collect()` yourself.
2. **Crossing providers.** An instrument reaches only the reader of the provider whose meter built it. `metric.install(...)` swaps in a provider with an empty registry, so take your meters and build your instruments *after* installing.

## See Also

- [Instruments](./instruments.md) — the recording API of each instrument kind
- [MeterProvider](./meter-provider.md) — where meters come from, and what configures them
- [MetricData](./metric-data.md) — what collection hands back
