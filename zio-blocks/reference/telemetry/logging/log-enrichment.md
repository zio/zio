# LogEnrichment

> Typeclass that lets a log.* call accept a domain value directly

`LogEnrichment[A]` is what lets you pass a value of your own type straight into a [`log`](./index.md) call and have it become part of the [`LogRecord`](./log-record.md). When the macro behind `log.info("msg", value)` meets an argument whose type it does not handle natively, it looks for an implicit `LogEnrichment[A]`, calls `enrich` to fold the value into the record, and fails to compile if none is in scope. You rarely name the typeclass directly — the built-in instances below cover the everyday types — you define one only to log a domain value without unpacking it by hand at every call site.

```scala
trait LogEnrichment[A] {
  def enrich(record: LogRecord, value: A): LogRecord
}
```

`enrich` takes the record built so far and returns a copy with the value folded in — adding attributes, replacing the body, or setting the severity, depending on `A`.

## Built-in Instances

| Type                                                                                               | Effect on the record                                                                                         |
|----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `String`                                                                                           | Replaces the message body.                                                                                   |
| `Throwable`                                                                                        | Adds `exception.type` and `exception.message` attributes and stores the throwable for stack-trace rendering. |
| `Attributes`                                                                                       | Merges the whole set into the record's attributes.                                                           |
| `Severity`                                                                                         | Overrides the recorded severity — but not the one used for filtering (see below).                            |
| `(String, String)` / `(String, Long)` / `(String, Int)` / `(String, Double)` / `(String, Boolean)` | Adds one typed key-value attribute (`Int` is widened to `Long`).                                             |

These common types are also recognized directly by the `log.*` macro, so passing them costs nothing at runtime:

```scala
import zio.blocks.telemetry._

val ex: Throwable = new RuntimeException("connection refused")

log.error(
  "payment failed",
  ex,                        // Throwable — attaches type, message, stack trace
  "orderId"   -> "ord-123",  // (String, String)
  "amount"    -> 99L,        // (String, Long)
  "retryable" -> false       // (String, Boolean)
)
```

Passing a `Severity` is the one case that behaves unexpectedly. It changes the severity written into the record, but filtering already happened using the severity of the method you called — so `log.warn("slow query", Severity.Debug)` is recorded as DEBUG even under an `Info` floor, and `log.info("charge failed", Severity.Error)` is dropped by a `Warn` floor despite arriving as an error. Call the method matching the level you want filtered on.

Types outside the table are rejected at compile time, which is what a missing `LogEnrichment` error means. `Float`, `Short`, `Byte`, `Char`, `UUID`, and `Instant` are the ones people hit first: convert them at the call site (`.toDouble`, `.toLong`, `.toString`) or give the type an instance of its own.

## Custom Instances

Define a `LogEnrichment[MyType]` in implicit scope to accept your own type at a `log.*` call. The macro resolves it at that call site and threads the record through your `enrich`:

```scala
import zio.blocks.telemetry._

final case class RequestId(value: String)

implicit val requestIdEnrichment: LogEnrichment[RequestId] = new LogEnrichment[RequestId] {
  def enrich(record: LogRecord, value: RequestId): LogRecord =
    record.copy(attributes = record.attributes ++ Attributes.of(AttributeKey.string("request.id"), value.value))
}

log.info("handling request", RequestId("req-001")) // adds request.id="req-001"
```

## Integration

Resolution happens entirely at compile time, so there is no runtime cost for type dispatch. An argument whose type has neither native macro handling nor an implicit `LogEnrichment[A]` is a compile error, which keeps unloggable values out of the call. A custom instance must be in implicit scope wherever the `log.*` call appears.
