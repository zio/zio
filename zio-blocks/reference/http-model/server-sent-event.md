# ServerSentEvent

> `ServerSentEvent[A]` is an immutable envelope for one Server-Sent Event: a payload of type `A` plus the three optional SSE metadata fields. `SseDataEncoder[A]` turns the payload into the `data:` lines of the wire format. The type is covariant in `A`, and the metadata fields are `Maybe` rather than `Option` to stay allocation-free when absent:

```scala
final class ServerSentEvent[+A] private (
  val data: A,
  val eventType: Maybe[String],
  val eventId: Maybe[String],
  val retryMillis: Maybe[Long]
)

trait SseDataEncoder[-A] {
  def lines(value: A): Chunk[String]
}
```

## Motivation

The SSE wire format is small enough to hand-write and fiddly enough to get wrong. Fields are `name: value` lines, the event ends with a blank line, and a multi-line payload has to become several `data:` lines rather than one line containing newlines. Emit a payload with an embedded `\n` as a single `data:` line and the stream desynchronizes — the client reads the remainder as a new field, or as the end of the event.

`ServerSentEvent` owns that formatting. The envelope holds the metadata, an `SseDataEncoder` decides how the payload becomes lines, and `ServerSentEvent#render` assembles them in the order the specification requires. Splitting a multi-line string is the encoder's job, so a payload containing newlines is correct by construction rather than by remembering.

Separating the encoder from the envelope is what lets the payload be typed. `ServerSentEvent[String]` and `ServerSentEvent[Chunk[String]]` work out of the box, and any other type works as soon as it has an encoder — the envelope never needs to change.

## Quick Showcase

An event is a payload plus optional metadata, and rendering produces the wire format:

```scala
import zio.http.ServerSentEvent

val event = ServerSentEvent("hello", "greeting").id("42").retry(3000)
```

Rendering emits the metadata fields, then the data lines, then the blank line that terminates the event:

```scala
print(event.render)
// event: greeting
// id: 42
// retry: 3000
// data: hello
//
```

## Construction

Three entry points cover the cases: payload only, payload with an event name, and payload with any combination of metadata.

### `ServerSentEvent.apply` — payload, optionally named

The one-argument form creates an event with no metadata at all, and the two-argument form sets the `event:` field:

```scala
object ServerSentEvent {
  def apply[A](data: A): ServerSentEvent[A]
  def apply[A](data: A, event: String): ServerSentEvent[A]
}
```

A bare event renders as a single `data:` line followed by the blank line:

```scala
import zio.http.ServerSentEvent

val bare  = ServerSentEvent("tick")
val named = ServerSentEvent("tick", "heartbeat")
```

Naming the event adds one line above the data:

```scala
print(bare.render)
// data: tick
// 
print(named.render)
// event: heartbeat
// data: tick
//
```

### `ServerSentEvent.fromOptions` — all metadata at once

`ServerSentEvent.fromOptions` takes the three metadata fields as `Option`, each defaulting to `None`, which suits code that already has them in optional form:

```scala
object ServerSentEvent {
  def fromOptions[A](
    data: A,
    event: Option[String] = None,
    id: Option[String] = None,
    retry: Option[Long] = None
  ): ServerSentEvent[A]
}
```

Supplying a subset by name leaves the rest absent:

```scala
import zio.http.ServerSentEvent

val event = ServerSentEvent.fromOptions("payload", id = Some("7"), retry = Some(1000))
```

Only the fields you set appear on the wire:

```scala
print(event.render)
// id: 7
// retry: 1000
// data: payload
//
```

This is the only constructor that takes `Option`. The accessors return `Maybe`, so a round trip through `ServerSentEvent.fromOptions` converts between the two.

## Setting Metadata

Four methods produce a modified copy. Each returns a new envelope; nothing mutates.

| Method                        | Effect                                    |
| ----------------------------- | ----------------------------------------- |
| `ServerSentEvent#event`       | Sets the `event:` field.                   |
| `ServerSentEvent#clearEvent`  | Removes the `event:` field.                |
| `ServerSentEvent#id`          | Sets the `id:` field.                      |
| `ServerSentEvent#retry`       | Sets the `retry:` field, in milliseconds.  |
| `ServerSentEvent#clearRetry`  | Removes the `retry:` field.                |

They chain, since each returns a `ServerSentEvent[A]`:

```scala
import zio.http.ServerSentEvent

val event = ServerSentEvent("payload").event("update").id("100").retry(5000)
```

Clearing a field drops its line without disturbing the others:

```scala
print(event.clearRetry.render)
// event: update
// id: 100
// data: payload
// 
print(event.clearEvent.clearRetry.render)
// id: 100
// data: payload
//
```

:::note[There is no `clearId`]
`ServerSentEvent#clearEvent` and `ServerSentEvent#clearRetry` exist, but the `id:` field has no clearing method. To produce an event without an id, build a fresh envelope or use `ServerSentEvent.fromOptions` with `id = None`.
:::

The accessors expose what is set, as `Maybe`:

```scala
event.data
// res9: String = "payload"
event.eventType
// res10: Maybe[String] = "update"
event.eventId
// res11: Maybe[String] = "100"
event.retryMillis
// res12: Maybe[Long] = 5000L
```

## Validation

Two invariants are enforced at construction, and both throw rather than returning an error, because an invalid event cannot be rendered safely.

The `event:` and `id:` fields must not contain a carriage return or line feed. The reason is the same as for header values: a newline inside a field would terminate that field early and let the remainder be read as a new field or a new event, desynchronizing the stream.

The `retry:` value must be non-negative, since it is a reconnection delay in milliseconds.

Both checks are visible from a bare import:

```scala
import zio.http.ServerSentEvent
```

Both rejections are `IllegalArgumentException` with a message naming the field:

```scala
scala.util.Try(ServerSentEvent("data", "bad\nevent")).failed.map(_.getMessage)
// res14: Try[String] = Success(
//   "SSE event must not contain CR or LF characters"
// )
scala.util.Try(ServerSentEvent("data").retry(-1)).failed.map(_.getMessage)
// res15: Try[String] = Success("SSE retry must be non-negative")
```

Validation applies to the metadata only. The **payload** is never validated, because embedded newlines in the payload are legitimate — the encoder splits them into separate `data:` lines.

## Rendering

`ServerSentEvent#render` needs an `SseDataEncoder` for the payload type and produces the complete wire representation, terminating blank line included:

```scala
final class ServerSentEvent[+A] {
  def render(implicit encoder: SseDataEncoder[A]): String
}
```

Fields are emitted in a fixed order — `event:`, then `id:`, then `retry:`, then the `data:` lines — with absent fields omitted entirely. A payload that produces no lines still emits one empty `data:` line, so an event is never rendered without a data field:

```scala
import zio.http.ServerSentEvent
import zio.blocks.chunk.Chunk

val empty = ServerSentEvent(Chunk.empty[String])
```

The result is a single valueless data line and the terminator:

```scala
empty.render
// res17: String = """data: 
// 
// """
```

## SseDataEncoder

`SseDataEncoder[A]` maps a payload to its `data:` lines. It is contravariant in `A`, so an encoder for a supertype serves every subtype:

```scala
trait SseDataEncoder[-A] {
  def lines(value: A): Chunk[String]
}

object SseDataEncoder {
  def apply[A](implicit encoder: SseDataEncoder[A]): SseDataEncoder[A]
  implicit val string: SseDataEncoder[String]
  implicit val stringChunk: SseDataEncoder[Chunk[String]]
}
```

### Built-in Instances

`SseDataEncoder.string` splits a `String` on line breaks, so a multi-line payload becomes one `data:` line per line. `\n`, `\r`, and `\r\n` all count as a single break:

```scala
import zio.http.ServerSentEvent

val multiline = ServerSentEvent("first line\nsecond line\nthird line")
```

Three lines in the payload become three `data:` lines, which is what the SSE specification requires:

```scala
print(multiline.render)
// data: first line
// data: second line
// data: third line
//
```

`SseDataEncoder.stringChunk` treats each element as a line and splits each element as well, so a chunk whose elements themselves contain newlines still flattens correctly:

```scala
import zio.http.ServerSentEvent
import zio.blocks.chunk.Chunk

val chunked = ServerSentEvent(Chunk("alpha", "beta\ngamma"))
```

The two elements yield three lines:

```scala
print(chunked.render)
// data: alpha
// data: beta
// data: gamma
//
```

An empty `Chunk` renders as one empty `data:` line rather than none, matching the single-`String` behaviour for an empty payload.

### Custom Instances

An encoder for your own type is one method. Returning several lines is how a structured payload spans multiple `data:` fields:

```scala
import zio.http.{ServerSentEvent, SseDataEncoder}
import zio.blocks.chunk.Chunk

final case class Progress(step: Int, total: Int, note: String)

implicit val progressEncoder: SseDataEncoder[Progress] =
  new SseDataEncoder[Progress] {
    def lines(value: Progress): Chunk[String] =
      Chunk(s"step=${value.step}/${value.total}", s"note=${value.note}")
  }
```

With the instance in implicit scope, the payload type flows through the envelope unchanged:

```scala
print(ServerSentEvent(Progress(3, 10, "compiling"), "progress").render)
// event: progress
// data: step=3/10
// data: note=compiling
//
```

:::warning[Split lines yourself, or don't produce them]
`ServerSentEvent#render` prefixes each line the encoder returns with `data: ` and does not inspect it. An encoder that returns a string containing `\n` therefore emits a malformed event. Either split within `SseDataEncoder#lines`, or delegate to `SseDataEncoder.string` for the parts that might contain breaks.
:::

For a JSON payload, encode to a `String` with the JSON codec and reuse `SseDataEncoder.string`, which handles any newlines the rendered document contains.

## Equality and Rendering as Text

`ServerSentEvent#equals` compares the payload and all three metadata fields, so two envelopes are equal when they would render identically. `ServerSentEvent#hashCode` agrees with it.

`ServerSentEvent#toString` is a diagnostic rendering, not the wire format — absent fields appear as `null` and no `data:` prefixes are added:

```scala
import zio.http.ServerSentEvent

val event = ServerSentEvent("payload").id("9")
```

Use `ServerSentEvent#render` for anything that goes over the wire and `ServerSentEvent#toString` only for logs:

```scala
event.toString
// res25: String = "ServerSentEvent(data=payload, event=null, id=9, retry=null)"
```

## Integration Points

An SSE response is an ordinary `Response` whose body streams rendered events with a `text/event-stream` content type, so `ServerSentEvent` composes with the rest of [the HTTP model](./model.md) rather than replacing any of it. Setting that content type is a `Header.ContentType` — see [Header](./headers.md).

The type depends on `Chunk` for the line sequence and on `Maybe` for its optional fields, both from the core blocks: see [Chunk](../chunk.md) and [Maybe](../maybe.md).
