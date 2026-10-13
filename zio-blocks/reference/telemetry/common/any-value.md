# AttributeValue

> Boxed existential wrapper for the eight attribute kinds — the value type accepted by Logger, Span#setAttribute, and metric label maps when the type isn't known ahead of time.

`AttributeValue` is the closed set of eight kinds an attribute can hold — one case class per kind, so code that accepts "any attribute value" can pattern match on which one it got. It exists at the boundary of the telemetry API: `Attributes` itself never stores one (see [Attributes](./attributes.md) for the unboxed parallel-array representation it uses instead), but constructing or reading one at a boundary — a log call, a span attribute buffer, a metric label map, an exporter — needs a single type that can be any of the eight, and `AttributeValue` is it. Its eight variants:

```scala
sealed trait AttributeValue

object AttributeValue {
  final case class StringValue(value: String)           extends AttributeValue
  final case class BooleanValue(value: Boolean)         extends AttributeValue
  final case class LongValue(value: Long)               extends AttributeValue
  final case class DoubleValue(value: Double)           extends AttributeValue
  final case class StringSeqValue(value: Seq[String])   extends AttributeValue
  final case class LongSeqValue(value: Seq[Long])       extends AttributeValue
  final case class DoubleSeqValue(value: Seq[Double])   extends AttributeValue
  final case class BooleanSeqValue(value: Seq[Boolean]) extends AttributeValue
}
```

The name in this page's title is the Scala identifier; the file uses `any-value` because the type's own doc comment describes it as mirroring OpenTelemetry's `AnyValue` protobuf message, which supports the same eight shapes: a string, a boolean, a 64-bit integer, a double, and an array of each.

## Where It Shows Up

`AttributeValue` is the value type wherever an attribute is accepted without a pre-declared [`AttributeKey`](./attribute-key.md). The `Logger` API is the clearest example — every logging method takes a variadic list of name/value pairs typed against it:

```scala
import zio.blocks.telemetry._

val provider = LoggerProvider.builder.build()
val logger   = provider.get("com.example")

logger.info(
  "order placed",
  "orderId" -> AttributeValue.StringValue("ord_123"),
  "total"   -> AttributeValue.DoubleValue(49.99),
  "rush"    -> AttributeValue.BooleanValue(true)
)

provider.shutdown()
```

The same ADT appears on the reading side of `Attributes` — `Attributes#foreach` and `Attributes#toMap` box each stored value into an `AttributeValue` so you can match on its kind, which [Attributes](./attributes.md#iterating) covers in full, including why `Attributes#accept` is the zero-allocation alternative for a hot path. `Span#setAttribute`, `Tracer`'s span-creation attribute maps, and the label maps `Counter`/`Histogram`/`Gauge`/`UpDownCounter` accept all convert through `AttributeValue` the same way `Logger` does — construct or match on one of the eight cases, never a ninth.

## Relationship to AttributeType

Each `AttributeValue` variant corresponds to exactly one [`AttributeType`](./attribute-key.md) discriminator and one `AttributeKey` factory — the three are the same eight-way split viewed from three angles: the runtime value, the compile-time type tag, and the typed key constructor:

| `AttributeValue` variant | `AttributeType` | `AttributeKey` factory |
| ------------------------- | ---------------- | ------------------------ |
| `StringValue(String)`             | `StringType`      | `AttributeKey.string`      |
| `BooleanValue(Boolean)`           | `BooleanType`     | `AttributeKey.boolean`     |
| `LongValue(Long)`                 | `LongType`        | `AttributeKey.long`        |
| `DoubleValue(Double)`             | `DoubleType`      | `AttributeKey.double`      |
| `StringSeqValue(Seq[String])`     | `StringSeqType`   | `AttributeKey.stringSeq`   |
| `LongSeqValue(Seq[Long])`         | `LongSeqType`     | `AttributeKey.longSeq`     |
| `DoubleSeqValue(Seq[Double])`     | `DoubleSeqType`   | `AttributeKey.doubleSeq`   |
| `BooleanSeqValue(Seq[Boolean])`   | `BooleanSeqType`  | `AttributeKey.booleanSeq`  |

`AttributeType` never appears on its own in application code — it's the tag `AttributeKey[A]#type` carries so a key and a stored value can be checked against each other without reflecting on `A` itself.

## OTLP JSON Mapping

The [OTLP exporter](../otel/index.md) converts every `Attributes` set to OTLP's `AnyValue` JSON shape by boxing each entry into an `AttributeValue` and matching on it. The eight cases map onto four wire keys, with sequences nesting under `arrayValue`:

| `AttributeValue` variant | OTLP JSON field | Notes |
| -------------------------- | ------------------ | ------- |
| `StringValue`     | `"stringValue"`                              | plain JSON string |
| `BooleanValue`    | `"boolValue"`                                | plain JSON boolean |
| `LongValue`       | `"intValue"`                                 | JSON string, per OTLP's `int64` mapping — `42L` becomes `"42"` |
| `DoubleValue`     | `"doubleValue"`                              | plain JSON number |
| `StringSeqValue`  | `"arrayValue":{"values":[{"stringValue":...}, ...]}` | one wrapped element per entry |
| `LongSeqValue`    | `"arrayValue":{"values":[{"intValue":"..."}, ...]}`  | each element quoted, same as scalar `LongValue` |
| `DoubleSeqValue`  | `"arrayValue":{"values":[{"doubleValue":...}, ...]}` | |
| `BooleanSeqValue` | `"arrayValue":{"values":[{"boolValue":...}, ...]}`   | |

## See Also

- [Attributes](./attributes.md#iterating) — `Attributes#foreach`/`Attributes#toMap` (which box into `AttributeValue`) versus `Attributes#accept` (which doesn't)
- [AttributeKey](./attribute-key.md) — the typed key that pairs with a specific `AttributeValue` variant for allocation-free reads
- [OTLP Export](../otel/index.md) — where `AttributeValue` becomes OTLP's `AnyValue` JSON representation
- [Common Types](./index.md) — the types every telemetry pillar uses
