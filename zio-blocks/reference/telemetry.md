# Common Types

> The four types every signal carries — Attributes and AttributeKey for its detail, Resource and InstrumentationScope for what produced it.

These four types describe every signal, across all three telemetry pillars — tracing, logging, and metrics. Every span, log record, and metric data point is annotated with `Attributes`; every provider accepts a `Resource`; every scope instance is tagged with an `InstrumentationScope`. Understanding these four types unlocks the rest of the module.

## Types

| Type | Description |
|------|-------------|
| [Attributes](./attributes.md) | Immutable, unboxed parallel-array collection of typed key-value pairs. Carried by every signal: `Span`, `LogRecord`, `Counter`, `Resource`, and `InstrumentationScope`. |
| [AttributeKey](./attribute-key.md) | Typed key for an `Attributes` entry. Binds a string name to one of eight value types (`String`, `Long`, `Double`, `Boolean`, and their `Seq` variants). |
| [AttributeValue](./any-value.md) | Boxed existential wrapper for the same eight kinds, used at API boundaries — `Logger`'s vararg attributes, `Span#setAttribute`, metric label maps — where the type isn't known ahead of time. |
| [Resource](./resource.md) | Immutable `Attributes` wrapper that describes the entity producing telemetry (service name, SDK version, deployment environment). Shared across all three providers. |
| [InstrumentationScope](./instrumentation-scope.md) | Named and versioned identity for the library or component that created a signal. Set when calling `TracerProvider.get`, `LoggerProvider.get`, or `MeterProvider.get`. |

## Design

All four types are designed for hot-path telemetry code:

- **`Attributes`** stores `Long`, `Double`, and `Boolean` values unboxed in a parallel primitive array via bit-casting. Reading a value back boxes it — through `get`, `foreach`, or a conversion to `Map` — so hot paths that only write stay allocation-free.
- **`AttributeKey`** is a small case class carrying the name and the value type, so a typo in a name or a mismatched type is a compile error rather than a silently separate series.
- **`Resource`** is immutable and stamped onto a signal when it is created, not at export: `Tracer` puts it on every span and `Logger` on every log record. Metric instruments do not carry it — a `MeterProvider` holds a `Resource`, but the `Meter` and the collected `MetricData` never see it.
- **`InstrumentationScope`** is constructed once per scope by the provider and referenced from all signals that scope creates.

## See Also

- [Telemetry](../index.md) — module overview and the three-pillar architecture
- [Tracing](../tracing/index.md) — span lifecycle types that use `Attributes` and `Resource`
- [Logging](../logging/index.md) — structured log record types that carry `Attributes`
- [Metrics](../metrics/index.md) — dimensional measurement types that use `Attributes` as labels
