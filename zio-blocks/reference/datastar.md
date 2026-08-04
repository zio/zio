# Datastar

> `zio-blocks-datastar` provides a type-safe Scala SDK for [Datastar](https://data-star.dev/), the hypermedia framework that brings reactive UIs via server-sent events (SSE). It builds on `zio-blocks-html` for DOM construction and `zio-blocks-schema` for JSON serialization.

`zio-blocks-datastar` provides a type-safe Scala SDK for [Datastar](https://data-star.dev/), the hypermedia framework that brings reactive UIs via server-sent events (SSE). It builds on `zio-blocks-html` for DOM construction and `zio-blocks-schema` for JSON serialization.

## Installation

```scala
// JVM
libraryDependencies += "dev.zio" %% "zio-blocks-datastar" % "0.0.51"

// Scala.js
libraryDependencies += "dev.zio" %%% "zio-blocks-datastar" % "0.0.51"
```

## Signals

A `Signal[A]` represents a named reactive signal on the client. Use `:=` to pair it with a value, producing a `SignalUpdate` ready for SSE transmission:

```scala
import zio.http.datastar._
import zio.blocks.schema.Schema

case class User(name: String, age: Int)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val count    = Signal[Int]("count")
val username = Signal[String]("username")

// Create signal updates (serializes via Schema's cached JSON codec)
val update1 = count := 0
val update2 = username := "Alice"
```

## SSE Events

`DatastarEvent` is the sealed trait representing events sent to the browser. Use the companion object's builder methods:

### patchSignals

Send signal updates to the client:

```scala
val sse = DatastarEvent
  .patchSignals(count := 42, username := "Bob")
  .renderSSE

// With options:
val sseWithOptions = DatastarEvent
  .patchSignals(count := 1)
  .withOnlyIfMissing
  .withEventId("evt-1")
  .withRetry(5000)
  .renderSSE
```

### patchElements

Send DOM fragments to the client:

```scala
import zio.blocks.html._

val sse = DatastarEvent
  .patchElements(div(id := "status")("Online"))
  .withSelector(CssSelector.id("status"))
  .withMode(ElementPatchMode.Inner)
  .withViewTransition
  .renderSSE
```

### executeScript

Run JavaScript on the client:

```scala
val sse = DatastarEvent
  .executeScript(js"console.log('hello')")
  .renderSSE
```

### removeElements

Remove elements matching a CSS selector:

```scala
val sse = DatastarEvent
  .removeElements(CssSelector.id("old-item"))
  .renderSSE
```

## Attribute DSL

Import `zio.http.datastar._` to get all `data-*` attribute constructors. These integrate with the `zio-blocks-html` DSL:

```scala
import zio.blocks.html._
import zio.http.datastar._

val count    = Signal[Int]("count")
val username = Signal[String]("username")

div(
  dataSignals(count := 0),
  dataText := count
)(
  span()("Count: "),
  button(dataOn.click := js"$count++")("Increment")
)
```

Datastar expression positions are intentionally stricter than generic HTML/JS templating.
Raw `String` values are rejected in Datastar expression attributes; use `js"..."`
for expressions, or typed values like `Signal`, `SignalUpdate`, and `DatastarRef`.

```scala
dataText := count
dataText := count.ref
dataOn.click := js"$count++"

// does not compile:
// dataOn.click := "$count++"
```

### Available Attributes

| Method | Datastar Attribute | Description |
|--------|-------------------|-------------|
| `dataSignals(signal)` | `data-signals:name` | Declare one keyed signal with an initial value |
| `dataSignals(update, updates...)` | `data-signals` | Patch multiple signals with one object expression |
| `dataBind(signal)` | `data-bind:name` | Two-way bind an input to a signal |
| `dataText` | `data-text` | Set element text content |
| `dataShow` | `data-show` | Conditionally show/hide element |
| `dataClass("name")` | `data-class:name` | Toggle CSS class |
| `dataOn.click` | `data-on:click` | Event handler |
| `dataComputed(signal)` | `data-computed:name` | Computed signal |
| `dataEffect` | `data-effect` | Side-effect expression |
| `dataIndicator(signal)` | `data-indicator:name` | Loading indicator signal |
| `dataRef("name")` | `data-ref:name` | Element reference |
| `dataInit` | `data-init` | Initialization expression |

### Event Modifiers

Chain modifiers on `dataOn` before assigning a handler:

```scala
// Debounce input by 300ms
dataOn.input.debounce(300) := username

// Click with prevent default, only once
dataOn.click.prevent.once := js"handleSubmit()"

// Throttle scroll, listen on window
dataOn.scroll.throttle(100).window := js"onScroll()"
```

Available modifiers: `debounce`, `debounceLeading`, `throttle`, `throttleLeading`, `delay`, `once`, `passive`, `capture`, `stop`, `prevent`, `outside`, `window`, `document`, `viewTransition`.

### Case Modifiers

`dataOn` and `dataSignals(signal)` builders expose `.camel`, `.kebab`, `.snake`, and `.pascal` to control how the attribute key is cased. The default for `dataOn` is kebab; for `dataSignals(signal)` it is camel.

```scala
// Renders data-on:myCustomEvent (camel, explicit)
dataOn("myCustomEvent").camel := js"handleIt()"

// Renders data-signals:my-signal__case.kebab (kebab override)
dataSignals(count := 0).kebab
```

The suffix `__case.<modifier>` is appended to the attribute name only when the chosen case differs from the builder's default.

### Advanced Triggers

#### dataOnIntersect

Fires when the element enters (or exits) the viewport. Modifiers map directly to Datastar's intersection observer options:

```scala
// Fire once when 50% of the element is visible
div(dataOnIntersect.half.once := js"loadContent()")()

// Fire when element exits the viewport, debounced
div(dataOnIntersect.exit.debounce(200) := js"cleanup()")()
```

Available modifiers: `once`, `half`, `full`, `exit`, `threshold(pct: Double)`, `delay(millis)`, `debounce(millis)`, `throttle(millis)`, `viewTransition`.

#### dataOnInterval

Runs an expression on a repeating timer:

```scala
// Poll every second
div(dataOnInterval.duration(1000) := js"$count++")(count.ref.text)

// Leading-edge interval with view transitions
div(dataOnInterval.durationLeading(500).viewTransition := js"refresh()")()
```

#### dataOnSignalPatch / dataOnSignalPatchFilter

`dataOnSignalPatch` runs whenever the server patches signals. Use `dataOnSignalPatchFilter` to restrict which patches trigger the handler:

```scala
div(
  dataOnSignalPatch.debounce(100) := js"onPatch()",
  dataOnSignalPatchFilter         := js"$count > 0"
)()
```

Available modifiers on `dataOnSignalPatch`: `delay(millis)`, `debounce(millis)`, `throttle(millis)`.

### Style and Attribute Binding

#### dataStyle

Bind inline styles, either as a whole-object expression or per-property:

```scala
// Keyed: sets a single CSS property
div(dataStyle("color") := js"$isDark ? 'white' : 'black'")()

// Unkeyed: bind an entire style object
div(dataStyle := js"{color: $color, fontSize: $size + 'px'}")()
```

#### dataAttr

Bind arbitrary HTML attributes:

```scala
div(dataAttr("aria-label") := js"$label")()
div(dataAttr("tabindex")   := js"$isActive ? 0 : -1")()
```

### Morph Control

Mark elements so Datastar's morphing algorithm treats them specially:

```scala
// Exclude element and its children from morphing
div(dataIgnore)()

// Exclude only the element itself, not its children
div(dataIgnoreSelf)()

// Prevent the element from being morphed (keep existing DOM node)
div(dataIgnoreMorph)()

// Preserve a specific attribute across morphs
input(dataPreserveAttr("value"))()
```

### JSON Signals

`dataJsonSignals` passes a raw JSON string as the signals expression, useful when signals are serialized server-side:

```scala
div(dataJsonSignals := js"""{"count":0,"username":""}""")()
```

## ElementPatchMode

Controls how `patchElements` applies DOM content to the target:

| Mode | Description |
|------|-------------|
| `Outer` | Morphs the target element in place (default, omitted from SSE) |
| `Inner` | Replaces the target's children |
| `Replace` | Replaces the target element |
| `Prepend` | Inserts before the first child |
| `Append` | Inserts after the last child |
| `Before` | Inserts before the target element |
| `After` | Inserts after the target element |
| `Remove` | Removes the target element |

```scala
DatastarEvent
  .patchElements(li(id := "new")("item"))
  .withSelector(CssSelector.id("list"))
  .withMode(ElementPatchMode.Append)
  .renderSSE
```

## CssSelector

`CssSelector` (from `zio.blocks.html`) constructs CSS selectors with type-safe combinators:

```scala
import zio.blocks.html._

CssSelector.id("main")          // #main
CssSelector.`class`("active")   // .active
CssSelector.element("div")      // div
CssSelector.raw(".foo > .bar")  // .foo > .bar
CssSelector.universal           // *
```

Selectors compose with operators:

```scala
val sel = CssSelector.element("ul") > CssSelector.element("li")  // ul > li
val grouped = CssSelector.id("a") | CssSelector.id("b")          // #a, #b
```

## Signal.dynamic

`Signal[A]("name")` validates the signal name at compile time for string literals. Use `Signal.dynamic[A](name)` when the name is only known at runtime:

```scala
val fieldName = computeFieldName()               // runtime string
val sig       = Signal.dynamic[String](fieldName) // validated at runtime

sig := "hello"  // works like any other Signal
```

`dynamic` throws `IllegalArgumentException` if the name is invalid. The same validation rules apply: dot-separated JavaScript identifiers, no `__`.

## SSE Streaming

`DatastarEvent` has no ZIO or HTTP framework dependency. It just produces strings. To stream events, set the response content-type to `text/event-stream` and write each `renderSSE` result to the response body:

```scala
// Framework-agnostic pseudocode
response.setHeader("Content-Type", "text/event-stream")
response.setHeader("Cache-Control", "no-cache")

// Send an initial signal patch
response.write(DatastarEvent.patchSignals(count := 0).renderSSE)

// Stream DOM updates as data changes
val fragment = div(id := "result")(computedContent)
response.write(
  DatastarEvent
    .patchElements(fragment)
    .withSelector(CssSelector.id("result"))
    .renderSSE
)
```

Each `renderSSE` call returns a self-contained SSE block — multiple events can be written sequentially to the same stream.
