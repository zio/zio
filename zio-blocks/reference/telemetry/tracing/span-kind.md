# SpanKind

> SpanKind — the sealed enumeration classifying a span's role in a trace (Internal, Server, Client, Producer, Consumer) in the telemetry module's tracing area.

`SpanKind` is a sealed enumeration that classifies a span by its role in a trace — internal work, the client or server side of a synchronous RPC, or the producer or consumer side of an asynchronous message. It aligns with the [OpenTelemetry SpanKind specification](https://opentelemetry.io/docs/specs/otel/trace/api/#spankind), and a [`Tracer`](./tracer.md) records it on each [`Span`](./span.md) so backends can reconstruct client↔server and producer↔consumer relationships across services.

```scala
sealed trait SpanKind
object SpanKind {
  case object Internal extends SpanKind
  case object Server   extends SpanKind
  case object Client   extends SpanKind
  case object Producer extends SpanKind
  case object Consumer extends SpanKind
}
```

Each kind describes what the span covers and how it relates to a span in another process:

| Kind       | Represents                                                                                                                       | Relationship in a trace                                   |
|------------|----------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| `Internal` | Work performed internally in the application. The default when no kind is given.                                                 | No remote parent or child.                                |
| `Server`   | The server side of a synchronous RPC — begins when the server starts processing the request and ends when it sends the response. | Usually the child of a remote `Client` span.              |
| `Client`   | The client side of a synchronous RPC — begins when the request is sent and ends when the response arrives.                       | Usually the parent of a remote `Server` span.             |
| `Producer` | The producer side of an asynchronous message — begins when the message is sent and ends once it is dispatched.                   | Parent that often completes before the `Consumer` begins. |
| `Consumer` | The consumer side of an asynchronous message — begins when the message is received and ends when processing finishes.            | Child of a `Producer` span.                               |

## Usage

Pass a `SpanKind` as the second argument to [`trace.span`](./index.md) (or `Tracer#span`) to classify a span; omit it to accept the `Internal` default.

```scala
import zio.blocks.telemetry._

// Server span: handling an inbound request
trace.span("handle-order", SpanKind.Server) { _ =>
  // Client span: an outbound call made while handling it
  trace.span("charge-payment", SpanKind.Client)(_ => ())
}
```

Setting the kind accurately lets a tracing backend pair a `Client` span in one service with the `Server` span it triggers in another, and a `Producer` span with the `Consumer` that eventually handles its message.

## See Also

- [Span](./span.md) — the unit of work a `SpanKind` classifies.
- [Tracing](./index.md) — the tracing overview and the `trace` entry point.
- [OpenTelemetry SpanKind specification](https://opentelemetry.io/docs/specs/otel/trace/api/#spankind) — the cross-language semantics this enumeration follows.
