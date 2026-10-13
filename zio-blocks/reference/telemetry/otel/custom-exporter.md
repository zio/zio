# Building an Exporter

> Encode telemetry into OTLP JSON, send it yourself, and interpret the result — the public pieces of the export path and how they fit together.

The exporters that ship with this module are internal, but the pieces they are built from are not. `OtlpJsonEncoder` turns recorded signals into OTLP JSON bytes, [`HttpSender`](./index.md) puts bytes on the wire, and `ExportResult` classifies what came back. Together they are enough to export telemetry today, without waiting for a public exporter API. Supporting types: `NamedMetric`, `HttpResponse`. The public surface of the export path:

```scala
object OtlpJsonEncoder {
  def encodeTraces(spans: Seq[SpanData], resource: Resource, scope: InstrumentationScope): Array[Byte]
  def encodeMetrics(metrics: Seq[NamedMetric], resource: Resource, scope: InstrumentationScope): Array[Byte]
  def encodeLogs(logs: Seq[LogRecord], resource: Resource, scope: InstrumentationScope): Array[Byte]
}

sealed trait ExportResult

object ExportResult {
  case object Success                                           extends ExportResult
  final case class Failure(retryable: Boolean, message: String) extends ExportResult

  def fromHttpResponse(response: HttpResponse): ExportResult
}
```

## Motivation

`OtlpJsonTraceExporter`, `OtlpJsonLogExporter`, `OtlpJsonMetricExporter`, and the `BatchProcessor` behind them are all `private[otel]`, so there is no supported way to construct one. That is a real limitation, and the [module overview](./index.md) says so.

What it does not mean is that you cannot export. Three of the four pieces an exporter needs are public: the encoder that produces the payload, the sender that transmits it, and the result type that says whether to retry. Only the batching and retry loop is missing, and for many deployments that loop is a scheduled task you already have — a periodic flush from whatever scheduler your application runs.

Writing it yourself also buys control the internal exporter does not offer: your own retry policy, your own queue semantics, metrics about the exporter itself, and a payload you can inspect before it leaves the process.

## The Encoder

`OtlpJsonEncoder` produces the OTLP protobuf-JSON mapping directly into a `StringBuilder`, with no JSON library involved. Each method takes the signals plus the `Resource` and `InstrumentationScope` that describe their origin, and returns UTF-8 bytes ready to POST.

### Encoding Rules

The output follows the protobuf JSON mapping rather than a naive rendering, which matters if you plan to compare payloads or write assertions against them:

| Wire concern              | Encoding                                       |
| ------------------------- | ---------------------------------------------- |
| `traceId`, `spanId` bytes | Lowercase hex strings                           |
| `int64` / `uint64`        | Quoted strings, not JSON numbers                |
| Enums                     | Integers                                        |
| Field names               | camelCase                                       |
| Control characters        | `\uXXXX` escapes, including lone surrogates      |

The quoted-integer rule is the one that surprises people: OTLP timestamps are `uint64`, so they appear as `"1755990000000000000"` rather than a bare number. A collector expects that; a hand-written comparison usually does not.

### Encoding Traces

`OtlpJsonEncoder.encodeTraces` takes `SpanData` values — the same records the core module's in-memory processor collects:

```scala
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

val payload: Array[Byte] = OtlpJsonEncoder.encodeTraces(
  trace.collectedSpans,
  Resource.empty,
  InstrumentationScope("checkout-service", Some("1.4.0"))
)
```

`trace.collectedSpans` returns everything recorded so far, so a real exporter tracks what it has already sent rather than re-encoding the whole list on every flush.

### Encoding Logs

`OtlpJsonEncoder.encodeLogs` takes `LogRecord` values, and is otherwise identical in shape:

```scala
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

def exportLogs(records: Seq[LogRecord]): Array[Byte] =
  OtlpJsonEncoder.encodeLogs(records, Resource.empty, InstrumentationScope("checkout-service"))
```

### Encoding Metrics

Metrics need one extra step, because the encoder wants a name and the reader does not supply one. `NamedMetric` pairs the descriptor with the data:

```scala
final case class NamedMetric(
  name: String,
  description: String,
  unit: String,
  data: MetricData
)
```

`MetricReader#collectAllMetrics` returns `Seq[MetricData]`, and `MetricData` — `SumData`, `HistogramData`, or `GaugeData` — carries only data points. The name, description, and unit are not on it:

```scala
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._

val collected: Seq[MetricData] = metric.reader.collectAllMetrics()

val named: Seq[NamedMetric] =
  collected.map(data => NamedMetric("http.server.duration", "Request duration", "ms", data))

val payload = OtlpJsonEncoder.encodeMetrics(named, Resource.empty, InstrumentationScope("checkout-service"))
```

:::warning[`MetricData` does not carry its own name]
`MetricReader#collectAllMetrics` returns metric data with no identifying name, and there is no public way to recover which instrument produced which element. Attaching the right name means tracking the correspondence yourself — record the instruments you created in the same order you read them back, or collect per-instrument rather than in bulk. Mapping a whole batch to one name, as above, is only correct when you registered exactly one instrument.
:::

`OtlpJsonEncoder.NamedMetric` is a type alias and value alias for the same case class, so either spelling compiles.

## Interpreting the Response

`ExportResult` classifies an HTTP response into "delivered", "retry this", and "drop this". `ExportResult.fromHttpResponse` applies the standard OTLP rules, so you do not need to remember which status codes are worth a second attempt:

```scala
import zio.blocks.telemetry.otel._

def result(status: Int): ExportResult =
  ExportResult.fromHttpResponse(HttpResponse(status, Array.emptyByteArray, Map.empty))
```

Any `2xx` is a success:

```scala
result(200)
// res3: ExportResult = Success
result(204)
// res4: ExportResult = Success
```

Four status codes are treated as retryable — `429`, `502`, `503`, and `504` — because each means "not now" rather than "not ever":

```scala
result(429)
// res5: ExportResult = Failure(retryable = true, message = "HTTP 429")
result(503)
// res6: ExportResult = Failure(retryable = true, message = "HTTP 503")
```

Everything else is a permanent failure, and retrying it only wastes the batch. A `400` means the collector rejected the payload itself, so the same bytes will be rejected again:

```scala
result(400)
// res7: ExportResult = Failure(retryable = false, message = "HTTP 400")
result(401)
// res8: ExportResult = Failure(retryable = false, message = "HTTP 401")
```

The `message` is a short diagnostic rather than the response body, so log the body separately when you need to know *why* a `400` was rejected.

A rejected export arrives as a non-`2xx` `HttpResponse`, not as an exception. A connection that never completes is different: `HttpSender.jdk` lets `IOException` out, so a custom loop must catch throwables and treat them as retryable itself — `ExportResult.fromHttpResponse` never sees them.

:::tip[Honour `Retry-After`]
`HttpResponse#firstHeader` looks a header up case-insensitively, which is what a `429` or `503` needs. `ExportResult` does not read it, so a retry loop that ignores `Retry-After` will keep hammering a collector that just asked it to wait.
:::

## Putting It Together

A minimal exporter is a flush function: take what has accumulated, encode it, send it, and act on the result. This is the whole shape, with the retry decision made from `ExportResult`:

```scala
import zio.blocks.telemetry._
import zio.blocks.telemetry.otel._
import java.time.Duration

val config = ExporterConfig(
  endpoint = "https://otlp.example.com:4318",
  headers = Map("Authorization" -> "Bearer <token>"),
  timeout = Duration.ofSeconds(10)
)

val sender = HttpSender.jdk(config.timeout)
val scope  = InstrumentationScope("checkout-service", Some("1.4.0"))

def flushTraces(spans: Seq[SpanData]): ExportResult =
  if (spans.isEmpty) ExportResult.Success
  else {
    val body = OtlpJsonEncoder.encodeTraces(spans, Resource.empty, scope)
    try ExportResult.fromHttpResponse(
      sender.send(config.endpoint + "/v1/traces", config.headers + ("Content-Type" -> "application/json"), body)
    )
    catch {
      case e: java.io.IOException => ExportResult.Failure(retryable = true, message = e.getMessage)
    }
  }
```

Three details in there are easy to get wrong. The signal path is appended to the endpoint — `/v1/traces`, `/v1/logs`, or `/v1/metrics` — because `ExporterConfig#endpoint` is a base URL. `Content-Type: application/json` must be set, since the payload is the JSON mapping rather than protobuf. And the `IOException` catch is not optional: without it, a collector that is simply unreachable takes down whatever thread the flush runs on.

Call `HttpSender#shutdown` when the process is stopping. The JDK sender holds nothing that needs releasing, but a custom sender may, and a flush that never runs at shutdown loses whatever was still queued.

## What You Are Reimplementing

The internal `BatchProcessor` does four things a hand-rolled flush does not, and they are worth deciding about explicitly rather than discovering later:

- **Bounded queueing.** It caps the queue at `ExporterConfig#maxQueueSize` and evicts the oldest record when full, so recording never blocks and memory never grows without bound. A naive accumulator has neither property.
- **Chunking.** It splits a drained queue into `ExporterConfig#maxBatchSize` pieces, so one flush after a traffic spike becomes several right-sized requests instead of one oversized one.
- **Interval flushing.** It flushes every `ExporterConfig#flushIntervalMillis` regardless of how full the batch is, which is what bounds how stale exported data can be.
- **Retry on retryable failures.** It re-queues a batch whose `ExportResult` was `Failure(retryable = true)` and drops one that was not.

`ExporterConfig` carries all three sizing fields even though nothing public reads them, so use them as the source of truth for your own loop rather than inventing separate numbers. See [the module overview](./index.md) for what each field means.

## Integration Points

This page uses only public API: `OtlpJsonEncoder`, `NamedMetric`, `ExportResult`, `HttpResponse`, `HttpSender`, and `ExporterConfig` from this module, and `SpanData`, `LogRecord`, `MetricData`, `Resource`, and `InstrumentationScope` from the core telemetry module.

The signals themselves come from the core module's recording APIs — [`trace`](../tracing/index.md) for spans, [`log`](../logging/index.md) for records, and [`metric`](../metrics/index.md) for measurements. For trace context across service boundaries rather than export, see [the module overview](./index.md).
