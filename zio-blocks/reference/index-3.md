# Datastar

> `zio.http.datastar` builds [Datastar](https://data-star.dev) hypermedia applications: it renders the `data-*` attributes that make a page reactive, and it produces the SSE events that patch that page from the server. Core types: `Signal`, `SignalUpdate`, `DatastarEvent`, `DataOn`, `DatastarAttrKey`, `ToDatastarExpr`, `ElementPatchMode`. The two halves of the module:

```scala
final class Signal[A] private (val name: String) {
  def :=(value: A)(implicit schema: Schema[A]): SignalUpdate[A]
  def ref: DatastarRef
}

sealed trait DatastarEvent {
  def renderSSE: String
}
```

## Introduction

Datastar puts application state in the browser as named *signals*, and drives changes to it declaratively. A page says what it reacts to — "when this button is clicked, POST to `/increment`" — using `data-*` attributes, and the server replies with events that patch elements or signals in place. No client-side framework code, no JSON API to design, no separate view model.

This module is the Scala side of that contract. It gives you a typed way to write the attributes, a typed way to name and update signals, and a builder for each kind of SSE event, so both directions of the exchange are checked by the compiler rather than assembled from strings.

Two things it deliberately does not do: it does not ship a server, and it does not ship the Datastar JavaScript. It produces `Dom` attributes and SSE payload strings, which you serve with whatever HTTP layer you already have.

## Motivation

The natural way to write Datastar from a server language is string interpolation — `s"data-on-click=\"@post('/increment')\""` — and it goes wrong in the usual ways. A misspelled signal name fails silently in the browser. An attribute name typo produces an attribute Datastar ignores. A value that should be a Datastar expression gets a Scala `String`, which renders as a literal instead of an expression, and the page just does nothing.

This module closes each of those:

- **Signal names are validated, literals at compile time.** `Signal[Int]("count")` checks the name during compilation; `Signal.dynamic` defers the same check to runtime for names you compute.
- **Raw strings are rejected in expression positions.** `ToDatastarExpr` is deliberately ambiguous for `String`, so passing one is a compile error that names the fix. Expressions come from the `js"..."` interpolator or from typed signals.
- **Attributes are `Dom.Attribute` values**, so they compose with the rest of the HTML DSL and cannot be misplaced into text content. The helpers also settle the naming convention for you: plain attributes render with a hyphen (`data-text`), and keyed ones with a colon (`data-on:click`, `data-class:active`, `data-computed:total`).
- **SSE events are builders**, so each event kind offers the options it accepts and rendering emits the exact protocol field names. The one exception is `executeScript`, which returns an element-patch builder and so exposes `selector` and `mode` that do not apply to it.

## Installation

Add the module to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-datastar" % "0.0.56"
```

For Scala.js, use `%%%` instead of `%%`:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-datastar" % "0.0.56"
```

Supported Scala versions: 2.13.x and 3.x.

:::note[The package is `zio.http.datastar`]
Despite living under `zio/blocks/datastar` in the source tree, the package is `zio.http.datastar`. The package object extends `DatastarAttributes`, so a single wildcard import brings every `data*` helper into scope.
:::

## Overview

The module divides along the direction data flows.

### Signals — state that lives in the browser

`Signal[A]` is a named, typed handle on a client-side signal. `Signal#:=` pairs it with a value to produce a `SignalUpdate[A]`, serialized through the type's JSON codec. `Signal#ref` produces a `DatastarRef`, the `$name` form used inside expressions. See [Signals](./signals.md).

### Attributes — declaring reactivity on the page

`DatastarAttributes` supplies 27 `data*` helpers — `dataText`, `dataShow`, `dataBind`, `dataClass`, `dataComputed`, and the rest — each returning either a `Dom.Attribute` or a `DatastarAttrKey` awaiting a value. `ToDatastarExpr` governs what may be assigned. See [Attributes](./attributes.md).

### Events — reacting to the user

`dataOn` opens the event side: sixteen predefined events, fourteen chainable modifiers for debouncing, throttling, and propagation control, and four specialized triggers for intersection, interval, signal-patch, and init. See [Event Handlers](./events.md).

### SSE — patching the page from the server

`DatastarEvent` builds the four server-to-browser events: patch elements, patch signals, execute a script, and remove elements. `ElementPatchMode` chooses where content lands, and `EventType` names the protocol event. See [Server-Sent Events](./sse.md).

## How They Work Together

A Datastar interaction is a loop, and the module sits on both ends of it:

```
1. Server renders HTML          html DSL + data* attributes
2. Browser becomes reactive     Datastar reads data-* and wires up signals
3. User acts                    an event matching a data-on:* fires
4. Browser issues a request     @post('/increment') from the expression
5. Server responds with SSE     DatastarEvent.patchSignals / patchElements
6. Browser applies the patch    signals update, elements morph in place
                                └─> back to 3
```

The types involved at each end:

```
BROWSER SIDE (rendered into HTML)

  Signal[A] ──ref──> DatastarRef ──> "$count"   used inside js"..." expressions
      │
      └─:=─> SignalUpdate[A] ──> {"count": 42}  JSON via Schema[A].jsonCodec

  dataOn.click ──> DataOn ──modifiers──> DataOn ──:=──> Dom.Attribute
                              │                          data-on:click__debounce.300ms
                              ├─ EventModifier            (14: debounce, throttle,
                              │                            once, passive, stop, …)
                              └─ CaseModifier             (__case.camel | kebab | snake | pascal)

  dataText / dataShow / dataClass(…) ──> DatastarAttrKey ──:=──> Dom.Attribute
                                                    ▲
                                          ToDatastarExpr guards the value:
                                          js"…" and Signal ok, raw String rejected

SERVER SIDE (rendered into an SSE stream)

  DatastarEvent.patchElements(dom) ──> PatchElementsBuilder ──renderSSE──> String
        │                                    ├─ selector(CssSelector)
        │                                    ├─ mode(ElementPatchMode)
        │                                    ├─ viewTransition / namespace
        │                                    └─ eventId / retry
        ├─ patchSignals(updates*) ──> PatchSignalsBuilder    ├─ onlyIfMissing
        ├─ executeScript(js) ──────> PatchElementsBuilder
        └─ removeElements(sel) ────> RemoveElementsBuilder

  renderSSE delegates to zio.http.ServerSentEvent, so the result is
  standard SSE with a Datastar-specific event name and data body.
```

## Common Patterns

Four shapes cover most Datastar work.

### A Counter, End to End

The smallest complete loop: a signal, an attribute that displays it, a button that asks the server to change it, and an SSE event that does:

```scala
import zio.blocks.html._
import zio.http.datastar._

val count = Signal[Int]("count")

val page = div(
  dataSignals(count := 0),
  span(dataText := count),
  button(dataOn.click := js"@post('/increment')", "increment")
)
```

Rendering gives ordinary HTML that Datastar can read:

```scala
page.renderMinified
// res0: String = "<div data-signals=\"{&quot;count&quot;: 0}\"><span data-text=\"$count\"></span><button data-on:click=\"@post(&#x27;/increment&#x27;)\">increment</button></div>"
```

The handler for `/increment` replies with a signal patch rather than a JSON body:

```scala
DatastarEvent.patchSignals(count := 1).renderSSE
// res1: String = """event: datastar-patch-signals
// data: signals {"count":1}
// 
// """
```

### Deriving State Instead of Storing It

`dataComputed` defines a signal whose value is an expression over other signals, so the derived value never has to be kept in sync:

```scala
import zio.blocks.html._
import zio.http.datastar._

val price    = Signal[Double]("price")
val quantity = Signal[Int]("quantity")
val total    = Signal[Double]("total")

val form = div(
  dataSignals(price := 9.99, quantity := 1),
  dataComputed(total) := js"$$price * $$quantity",
  span(dataText := total)
)
```

The computed attribute carries the expression, and the browser recomputes it whenever either input changes:

```scala
form.renderMinified
// res3: String = "<div data-signals=\"{&quot;price&quot;: 9.99, &quot;quantity&quot;: 1}\" data-computed:total=\"$price * $quantity\"><span data-text=\"$total\"></span></div>"
```

### Rate-Limiting a Chatty Event

Modifiers chain on `dataOn` before the handler is assigned, which is how a keystroke-driven search avoids one request per character:

```scala
import zio.blocks.html._
import zio.http.datastar._

val term = Signal[String]("term")

val search = input(
  dataBind(term),
  dataOn.input.debounce(300) := js"@get('/search')"
)
```

The modifier becomes part of the attribute name, so the browser applies it without any JavaScript of yours:

```scala
search.renderMinified
// res5: String = "<input data-bind:term data-on:input__debounce.300ms=\"@get(&#x27;/search&#x27;)\"/>"
```

### Patching a Fragment Rather Than the Page

`DatastarEvent.patchElements` with a selector and a mode replaces part of the page, leaving the rest — and its signal state — untouched:

```scala
import zio.blocks.html._
import zio.http.datastar._

val row = tr(td("Widget"), td("in stock"))

val event = DatastarEvent
  .patchElements(row)
  .selector(CssSelector.id("inventory"))
  .mode(ElementPatchMode.Append)
  .renderSSE
```

The rendered event names the selector and mode as protocol fields:

```scala
event
// res7: String = """event: datastar-patch-elements
// data: selector #inventory
// data: mode append
// data: elements <tr><td>Widget</td><td>in stock</td></tr>
// 
// """
```

## Integration Points

The module is a thin typed layer over three other blocks, and adds no dependency of its own:

- **`zio-blocks-html`** supplies `Dom`, `Dom.Attribute`, `Js`, `CssSelector`, and the `ToJs` type class. Every attribute helper returns a `Dom.Attribute`, so Datastar attributes compose with the HTML DSL exactly like `id` or `class` — see [HTML](../html.md).
- **`zio-blocks-schema`** supplies the JSON codec behind `Signal#:=`. A signal of type `A` needs a `Schema[A]`, and the serialized form is whatever that schema's JSON codec produces — see [Schema](../schema/index.md).
- **`zio-http-model`** supplies `ServerSentEvent`, which `DatastarEvent#renderSSE` delegates to for the wire format. The Datastar-specific part is the event name and the structured `data:` body — see [ServerSentEvent](../http-model/server-sent-event.md).

Within the module, the dependency direction is one-way: attributes and events both consume `Signal`, and neither knows about the other. Nothing in the SSE layer reads the attribute DSL.

## Next Steps

Start with [Signals](./signals.md), which every other page builds on, then [Attributes](./attributes.md) for the declarative surface and [Event Handlers](./events.md) for triggers. [Server-Sent Events](./sse.md) covers the server half.
