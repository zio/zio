# Server-Sent Events

> `DatastarEvent` builds the events a server sends to patch a live page: replace elements, patch signals, execute a script, or remove elements. Each constructor returns a builder for its event kind, and `DatastarEvent#renderSSE` produces the wire format. `DatastarEvent.executeScript` is the exception noted below: it reuses the element-patch builder, so it offers two options that do not apply to it. Supporting types: `PatchElementsBuilder`, `PatchSignalsBuilder`, `RemoveElementsBuilder`, `ElementPatchMode`, `EventType`. The constructors and the one method they all end in:

```scala
sealed trait DatastarEvent {
  def renderSSE: String
}

object DatastarEvent {
  def patchElements(elements: Dom): PatchElementsBuilder
  def patchSignals(first: SignalUpdate[_], rest: SignalUpdate[_]*): PatchSignalsBuilder
  def patchSignalsRaw(json: String): PatchSignalsBuilder
  def executeScript(code: Js): PatchElementsBuilder
  def removeElements(selector: CssSelector): RemoveElementsBuilder
}
```

## Motivation

The Datastar response protocol is SSE with structure inside the `data:` field. A patch looks like this on the wire:

```
event: datastar-patch-elements
data: selector #results
data: mode append
data: elements <li>Widget</li>

```

Three things make that easy to get wrong by hand. The event name must match the protocol exactly. The `data:` lines are a small keyed format, not free text, and the keys differ per event kind. And the terminating blank line is required — omit it and the browser waits indefinitely for an event it already has.

`DatastarEvent` removes all three concerns. Each constructor knows its event name, each builder method maps to one protocol field, and `DatastarEvent#renderSSE` delegates the SSE envelope to `ServerSentEvent`, which supplies the terminator.

## Quick Showcase

Build an event and render it:

```scala
import zio.blocks.html._
import zio.http.datastar._

val count = Signal[Int]("count")
```

A signal patch is one line of data:

```scala
DatastarEvent.patchSignals(count := 42).renderSSE
// res0: String = """event: datastar-patch-signals
// data: signals {"count":42}
// 
// """
```

An element patch carries its target and mode:

```scala
DatastarEvent
  .patchElements(li("Widget"))
  .selector(CssSelector.id("results"))
  .mode(ElementPatchMode.Append)
  .renderSSE
// res1: String = """event: datastar-patch-elements
// data: selector #results
// data: mode append
// data: elements <li>Widget</li>
// 
// """
```

## Patching Elements

`DatastarEvent.patchElements` takes a `Dom` and returns a `PatchElementsBuilder`. Its options map one-to-one onto protocol fields:

| Method | Field | Default |
| ------ | ----- | ------- |
| `selector(CssSelector)` | `selector` | omitted — patch by element id |
| `mode(ElementPatchMode)` | `mode` | `Outer`, which is omitted |
| `viewTransition` | `useViewTransition true` | omitted |
| `namespace(String)` | `namespace` | omitted |
| `eventId(String)` | SSE `id:` | omitted |
| `retry(Long)` | SSE `retry:` | omitted |

Every option is omitted when unset, so the minimal event is just the elements:

```scala
import zio.blocks.html._
import zio.http.datastar._

val row = li("Widget")
```

With no selector, Datastar matches on the element's own id, and `mode` is absent because `Outer` is the default:

```scala
DatastarEvent.patchElements(row).renderSSE
// res3: String = """event: datastar-patch-elements
// data: elements <li>Widget</li>
// 
// """
```

Adding options adds exactly the corresponding lines:

```scala
DatastarEvent.patchElements(row).selector(CssSelector.id("list")).viewTransition.eventId("evt-1").renderSSE
// res4: String = """event: datastar-patch-elements
// id: evt-1
// data: selector #list
// data: useViewTransition true
// data: elements <li>Widget</li>
// 
// """
```

:::note[`mode` is omitted when it is `Outer`]
`ElementPatchMode.Outer` is the protocol default, so setting it explicitly produces no `mode` line. That is intentional and keeps the payload minimal; do not read the absence of a `mode` line as "no mode".
:::

### ElementPatchMode

Eight modes decide where the content lands relative to the target. Each renders as its lowercase name:

| Mode | Effect |
| ---- | ------ |
| `Outer` | Replace the target element itself *(default)* |
| `Inner` | Replace the target's children |
| `Replace` | Replace using replacement semantics |
| `Prepend` | Insert before the target's existing children |
| `Append` | Insert after the target's existing children |
| `Before` | Insert immediately before the target |
| `After` | Insert immediately after the target |
| `Remove` | Remove the target |

`Append` is the one to reach for when adding to a list without re-rendering it:

```scala
ElementPatchMode.Append.render
// res5: String = "append"
ElementPatchMode.Inner.render
// res6: String = "inner"
```

## Patching Signals

`DatastarEvent.patchSignals` takes one or more `SignalUpdate`s and renders them as a single JSON object under the `signals` key:

```scala
import zio.blocks.html._
import zio.http.datastar._

val price    = Signal[Double]("price")
val quantity = Signal[Int]("quantity")
```

Multiple updates go in one event rather than one event each:

```scala
DatastarEvent.patchSignals(price := 19.99, quantity := 3).renderSSE
// res8: String = """event: datastar-patch-signals
// data: signals {"price":19.99,"quantity":3}
// 
// """
```

`PatchSignalsBuilder#onlyIfMissing` adds `onlyIfMissing true`, which tells Datastar to set the signal only when it is not already present — the way to supply a default without clobbering client state:

```scala
DatastarEvent.patchSignals(price := 0.0).onlyIfMissing.renderSSE
// res9: String = """event: datastar-patch-signals
// data: onlyIfMissing true
// data: signals {"price":0.0}
// 
// """
```

### `DatastarEvent.patchSignalsRaw` — pre-serialized JSON

When the JSON already exists — from a cache, or a shape no `Schema` describes — `DatastarEvent.patchSignalsRaw` takes it verbatim:

```scala
DatastarEvent.patchSignalsRaw("""{"count":7}""").renderSSE
// res10: String = """event: datastar-patch-signals
// data: signals {"count":7}
// 
// """
```

:::warning[`patchSignalsRaw` does not validate]
The string is inserted into the payload unchanged. Malformed JSON produces an event the browser silently discards, and there is no server-side error. Prefer `DatastarEvent.patchSignals` with typed updates, which cannot produce invalid JSON.
:::

## Executing Scripts

`DatastarEvent.executeScript` takes a `Js` and returns a `PatchElementsBuilder` — because on the wire it *is* an element patch, appending a `<script>` element that the browser executes:

```scala
import zio.blocks.html._
import zio.http.datastar._
```

The rendered event shows the mechanism rather than hiding it:

```scala
DatastarEvent.executeScript(js"console.log('done')").renderSSE
// res12: String = """event: datastar-patch-elements
// data: selector body
// data: mode append
// data: elements <script data-effect="el.remove()">console.log('done')</script>
// 
// """
```

The script is appended to `body` and carries `data-effect="el.remove()"`, so it deletes itself after running and leaves no residue in the DOM.

Because the return type is `PatchElementsBuilder`, the element-patch options are all available — including `selector` and `mode`, which would override that targeting and rarely make sense here. Treat the builder as offering `PatchElementsBuilder#eventId` and `PatchElementsBuilder#retry`.

## Removing Elements

`DatastarEvent.removeElements` takes a `CssSelector` and returns a `RemoveElementsBuilder` with a deliberately narrow surface — `viewTransition`, `namespace`, `RemoveElementsBuilder#eventId`, and `RemoveElementsBuilder#retry`:

```scala
DatastarEvent.removeElements(CssSelector.id("banner")).renderSSE
// res13: String = """event: datastar-patch-elements
// data: selector #banner
// data: mode remove
// data: elements 
// 
// """
```

There is no `mode` or `selector` method, because the selector is the argument and the mode is fixed to `remove` with an empty element body. That is the builder pattern doing its job: an option that would be meaningless is not offered.

## EventType

`EventType` names the protocol event in the SSE `event:` field. Two values, and you rarely name them directly since each constructor selects the right one:

| Value | Renders |
| ----- | ------- |
| `EventType.PatchElements` | `datastar-patch-elements` |
| `EventType.PatchSignals` | `datastar-patch-signals` |

Both render the protocol string rather than the Scala name:

```scala
EventType.PatchElements.render
// res14: String = "datastar-patch-elements"
EventType.PatchSignals.render
// res15: String = "datastar-patch-signals"
```

Both element patches and script execution use `PatchElements`, and removal does too — the distinction between them is in the `data:` body, not the event name.

## Streaming Several Events

`DatastarEvent#renderSSE` produces one complete event including its terminating blank line, so a stream is a concatenation. Nothing in the module manages the stream itself; you write the strings to whatever response body your HTTP layer uses:

```scala
import zio.blocks.html._
import zio.http.datastar._

val progress = Signal[Int]("progress")

val stream: String =
  (1 to 3).map(step => DatastarEvent.patchSignals(progress := step * 33).renderSSE).mkString
```

Each event is self-delimiting, so concatenating what `DatastarEvent#renderSSE` returns is valid SSE:

```scala
stream
// res17: String = """event: datastar-patch-signals
// data: signals {"progress":33}
// 
// event: datastar-patch-signals
// data: signals {"progress":66}
// 
// event: datastar-patch-signals
// data: signals {"progress":99}
// 
// """
```

Set `PatchElementsBuilder#eventId` when the client should be able to resume with `Last-Event-ID`, and `PatchElementsBuilder#retry` to control the reconnection delay — both are ordinary SSE fields handled by `ServerSentEvent`.

## Integration Points

`DatastarEvent#renderSSE` delegates to `ServerSentEvent` from `zio-http-model`, which supplies the SSE envelope, the field ordering, and the terminating blank line — see [ServerSentEvent](../http-model/server-sent-event.md). The Datastar-specific part is the event name and the keyed `data:` body.

Element patches carry `Dom` values and `CssSelector` targets from [HTML](../html.md), and signal patches carry [SignalUpdate](./signals.md) values whose JSON comes from the type's `Schema`. What the patched page does with the result is determined by the [Attributes](./attributes.md) and [Event Handlers](./events.md) rendered into it.
