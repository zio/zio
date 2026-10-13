# Sampler

> Decides per-span whether to drop, record-only, or record-and-sample.

`Sampler` is the policy that decides, for every new span, whether to record it with the sampled flag set (`RecordAndSample`), record it with the flag cleared (`RecordOnly`), or drop it (`Drop`). Both recording decisions reach every `SpanProcessor` identically; `RecordOnly` differs only in the cleared flag, which is the signal a downstream exporter or service uses to leave the span unexported. It exists to keep tracing volume under control: the sampler runs once per span in the hot path, before any recording work happens. You rarely implement one — the three built-ins cover the common strategies — and you configure a sampler once on the [`TracerProvider`](./tracer-provider.md) rather than calling it yourself. When the decision is `Drop`, [`Tracer`](./tracer.md) hands your block a `Span.NoOp` that records nothing and reaches no processor.

```scala
trait Sampler {
  def shouldSample(
    parentContext: Option[SpanContext],
    traceIdHi:     Long,
    traceIdLo:     Long,
    name:          String,
    kind:          SpanKind,
    attributes:    Attributes,
    links:         Seq[SpanLink]
  ): SamplingResult

  def description: String
}

final case class SamplingResult(
  decision:   SamplingDecision,  // Drop | RecordOnly | RecordAndSample
  attributes: Attributes,        // extra attributes injected into the span
  traceState: String
)
```

## Predefined Samplers

Three built-in samplers cover the strategies most services need, so a custom implementation is rarely necessary:

| Sampler                             | Behaviour                                                                          |
|-------------------------------------|------------------------------------------------------------------------------------|
| `AlwaysOnSampler`                   | Always returns `RecordAndSample`. Default for `TracerProvider.builder`.            |
| `AlwaysOffSampler`                  | Always returns `Drop`, reusing one cached `SamplingResult`.                        |
| `ParentBasedSampler(root: Sampler)` | Follows the parent span's sampled flag; delegates to `root` when no parent exists. |

Set one on the provider at startup — this configures OpenTelemetry-compatible head sampling:

```scala
import zio.blocks.telemetry._

val provider = TracerProvider.builder
  .setSampler(ParentBasedSampler(AlwaysOnSampler))
  .build()

provider.shutdown()
```

## Custom Samplers

When the built-ins do not fit — for example, sampling only premium tenants or at a probabilistic rate — implement `Sampler` yourself. Cache the `SamplingResult` values so the hot path stays allocation-free:

```scala
import zio.blocks.telemetry._

object TenantSampler extends Sampler {
  private val sampled = SamplingResult(SamplingDecision.RecordAndSample, Attributes.empty, "")
  private val dropped = SamplingResult(SamplingDecision.Drop, Attributes.empty, "")

  def shouldSample(
    parentContext: Option[SpanContext],
    traceIdHi: Long, traceIdLo: Long,
    name: String, kind: SpanKind, attributes: Attributes,
    links: Seq[SpanLink]
  ): SamplingResult =
    if (attributes.get(AttributeKey.string("tenant")).contains("premium")) sampled
    else dropped

  def description: String = "TenantSampler"
}

val provider = TracerProvider.builder.setSampler(TenantSampler).build()
provider.shutdown()
```

The `attributes` your sampler sees are only the ones passed at creation — `trace.span(name, kind, attributes)` or `tracer.span(name, kind, attributes)`. Anything you set on the span *inside* the block happens after the decision, so a sampler can never route on it. A span started through [`SpanBuilder`](./span-builder.md) never consults a sampler at all.

A dropped span is not nothing. The block still runs with a no-op span, and a valid trace id is still put in scope with the sampled bit cleared — so a nested `trace.span` under `ParentBasedSampler` is dropped too, and an outgoing request still carries the trace with flags `00`. That's what lets a downstream service honor the decision you made at the edge.
