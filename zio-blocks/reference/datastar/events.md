# Event Handlers

> `dataOn` opens the event side of the attribute DSL: sixteen predefined DOM events, fourteen chainable modifiers, and four case modifiers, all rendering into a single `data-on:<event>__<modifiers>` attribute. Four sibling builders cover triggers that are not DOM events — intersection, interval, signal patches, and initialization. Core types: `DataOn`, `PartialDataOn`, `EventModifier`, `CaseModifier`, `DataOnIntersect`, `DataOnInterval`, `DataOnSignalPatch`, `DataInit`. The shape of the builder:

```scala
final class DataOn private (name: String, modifiers: Maybe[EventModifier], caseModifier: CaseModifier) {
  def debounce(millis: Long): DataOn
  def once: DataOn
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute
}
```

## Motivation

An event handler in Datastar is an attribute whose name encodes both the event and its options. `data-on:input__debounce.300ms` debounces; `data-on:click__once__prevent` fires once and calls `preventDefault`. The name is structured, order-insensitive between modifiers, and entirely stringly-typed on the wire.

Written by hand that is a lot of punctuation to get right, and a modifier that Datastar does not recognize is silently ignored — the handler still fires, just without the debouncing you thought you had. A search box that issues a request per keystroke looks like it works, right up until it doesn't.

`dataOn` turns the whole name into a method chain. The event is a method, each modifier is a method, and the attribute name is assembled for you. A modifier that does not exist is a compile error, and the rendered name is correct by construction.

## Quick Showcase

Chain modifiers before assigning the handler:

```scala
import zio.blocks.html._
import zio.http.datastar._

val term = Signal[String]("term")

val box = input(
  dataBind(term),
  dataOn.input.debounce(300) := js"@get('/search')"
)
```

The modifier becomes part of the attribute name:

```scala
box.renderMinified
// res0: String = "<input data-bind:term data-on:input__debounce.300ms=\"@get(&#x27;/search&#x27;)\"/>"
```

## Predefined Events

Sixteen events have dedicated methods, each returning a `DataOn` ready for modifiers or assignment:

| Category | Methods |
| -------- | ------- |
| Pointer | `dataOn.click`, `dataOn.mouseover`, `dataOn.mouseout`, `dataOn.mouseenter`, `dataOn.mouseleave` |
| Keyboard | `dataOn.keydown`, `dataOn.keyup`, `dataOn.keypress` |
| Form | `dataOn.submit`, `dataOn.input`, `dataOn.change`, `dataOn.focus`, `dataOn.blur` |
| Window | `dataOn.scroll`, `dataOn.resize`, `dataOn.load` |

Each renders as `data-on:<event>`:

```scala
import zio.blocks.html._
import zio.http.datastar._
```

The method name is the event name — `dataOn.click`, `dataOn.submit`, `dataOn.input` — so nothing needs looking up:

```scala
button(dataOn.click := js"@post('/save')").renderMinified
// res2: String = "<button data-on:click=\"@post(&#x27;/save&#x27;)\"></button>"
form(dataOn.submit := js"@post('/submit')").renderMinified
// res3: String = "<form data-on:submit=\"@post(&#x27;/submit&#x27;)\"></form>"
```

### `dataOn.apply` — any other event

For an event without a dedicated method, `dataOn(name)` takes it as a string. The name is validated, so a structurally invalid event name fails rather than rendering a broken attribute:

```scala
div(dataOn("animationend") := js"@get('/done')").renderMinified
// res4: String = "<div data-on:animationend=\"@get(&#x27;/done&#x27;)\"></div>"
```

`dataOn` on its own is a `PartialDataOn` — the builder that the event methods live on — so `dataOn` alone is not a complete attribute.

## Event Modifiers

Fourteen modifiers chain before `:=`, each contributing a `__` suffix.

| Method | Renders | Effect |
| ------ | ------- | ------ |
| `debounce(ms)` | `__debounce.<ms>ms` | Fire after quiet period |
| `debounceLeading(ms)` | `__debounce.<ms>ms.leading` | Fire immediately, then suppress |
| `throttle(ms)` | `__throttle.<ms>ms` | At most once per interval |
| `throttleLeading(ms)` | `__throttle.<ms>ms.leading` | Leading-edge throttle |
| `delay(ms)` | `__delay.<ms>ms` | Wait before firing |
| `DataOn#once` | `__once` | Fire at most once |
| `DataOn#passive` | `__passive` | Passive listener |
| `DataOn#capture` | `__capture` | Capture phase |
| `DataOn#stop` | `__stop` | `stopPropagation` |
| `DataOn#prevent` | `__prevent` | `preventDefault` |
| `DataOn#outside` | `__outside` | Fire on events outside the element |
| `DataOn#window` | `__window` | Listen on the window object |
| `DataOn#document` | `__document` | Listen on the document object |
| `DataOn#viewTransition` | `__viewTransition` | Wrap the resulting patch in a view transition |

Modifiers combine, and the rendered name carries each one:

```scala
import zio.blocks.html._
import zio.http.datastar._
```

A click that fires once and suppresses the default action needs two, `DataOn#once` and `DataOn#prevent`:

```scala
button(dataOn.click.once.prevent := js"@post('/subscribe')").renderMinified
// res6: String = "<button data-on:click__once__prevent=\"@post(&#x27;/subscribe&#x27;)\"></button>"
```

`DataOn#outside` is the one worth knowing about: it inverts the target, firing when the event happens anywhere *but* this element — which is how a dropdown closes when you click away:

```scala
div(dataOn.click.outside := js"$$open = false").renderMinified
// res7: String = "<div data-on:click__outside=\"$open = false\"></div>"
```

Repeating a timing or target modifier does not stack: the chain is normalized to the last effective value before the name is rendered, so chaining `DataOn#debounce` twice keeps only the second value.

Modifiers are represented by the `EventModifier` ADT — `Debounce`, `Throttle`, `Delay`, `Once`, `Passive`, `Capture`, `Stop`, `Prevent`, `Outside`, `Window`, `Document`, `ViewTransition`, and `And` for combination — but the builder methods are the intended interface.

Each trigger has its own modifier ADT, and they are not interchangeable: `EventModifier` for `dataOn`, `IntersectModifier` for `dataOnIntersect` (which adds `Half`, `Full`, `Exit`, and `Threshold`), `OnIntervalModifier` for `dataOnInterval` (`Duration`, `ViewTransition`), `OnSignalPatchModifier` for `dataOnSignalPatch` (`Delay`, `Debounce`, `Throttle`), and `InitModifier` for `dataInit` (`Delay`, `ViewTransition`). Every one of them has an `And` variant, which is how a chain of builder calls accumulates. You never need to construct these directly — the builder methods do it — but they are what a modifier chain is made of.

## Case Modifiers

`CaseModifier` controls how the event name is cased in the rendered attribute. Its four variants — `Camel`, `Kebab`, `Snake`, and `Pascal` — are selected by the same-named builder methods `DataOn#camel`, `DataOn#kebab`, `DataOn#snake`, and `DataOn#pascal`. The suffix appears only when the requested case differs from the builder's default, which for `dataOn` is kebab:

```scala
import zio.blocks.html._
import zio.http.datastar._
```

A custom event whose real name is camelCase needs the modifier. Note what it actually does: the attribute key stays kebab-cased either way, and the modifier is what tells Datastar to convert it back:

```scala
div(dataOn("myCustomEvent").camel := js"@get('/handle')").renderMinified
// res9: String = "<div data-on:my-custom-event__case.camel=\"@get(&#x27;/handle&#x27;)\"></div>"
div(dataOn("myCustomEvent") := js"@get('/handle')").renderMinified
// res10: String = "<div data-on:my-custom-event=\"@get(&#x27;/handle&#x27;)\"></div>"
```

So `DataOn#camel` does not change the rendered key — it appends `__case.camel` alongside it. The default differs per builder: `dataOn` defaults to kebab, while `dataSignals(signal)` defaults to camel, and a modifier matching the default is omitted. That is why the same case call is a no-op on one and meaningful on the other.

Note also that the non-DOM triggers render as their own hyphenated attribute names — `data-on-intersect`, not `data-on:intersect` — since the trigger is the attribute rather than a key on it.

## Non-DOM Triggers

Four builders fire on something other than a DOM event. Each has its own modifier set and its own `:=`.

### `dataOnIntersect` — element enters the viewport

Fires when the element becomes visible, which is the basis for infinite scroll and lazy loading:

| Modifier | Effect |
| -------- | ------ |
| `DataOnIntersect#once` | Fire only the first time |
| `half` | Require 50% visibility |
| `full` | Require 100% visibility |
| `exit` | Fire on leaving rather than entering |
| `threshold(pct)` | Require an explicit visibility fraction |
| `delay(ms)`, `debounce(ms)`, `throttle(ms)` | Timing control |
| `DataOnIntersect#viewTransition` | Wrap the patch in a view transition |

A sentinel element at the end of a list is the canonical use:

```scala
import zio.blocks.html._
import zio.http.datastar._
```

Loading the next page once, when the sentinel is fully visible:

```scala
div(dataOnIntersect.once.full := js"@get('/page/2')").renderMinified
// res12: String = "<div data-on-intersect__once__full=\"@get(&#x27;/page/2&#x27;)\"></div>"
```

### `dataOnInterval` — fire on a timer

Polls on an interval, with `DataOnInterval#duration` setting the period, `DataOnInterval#durationLeading` firing immediately as well, and `DataOnInterval#viewTransition` wrapping the resulting patch:

```scala
div(dataOnInterval.duration(5000) := js"@get('/status')").renderMinified
// res13: String = "<div data-on-interval__duration.5000ms=\"@get(&#x27;/status&#x27;)\"></div>"
```

Without a `DataOnInterval#duration`, the attribute renders bare and Datastar applies its default interval.

### `dataOnSignalPatch` — react to signal changes

Fires when signals are patched, with `DataOnSignalPatch#delay`, `DataOnSignalPatch#debounce`, and `DataOnSignalPatch#throttle` for timing. Pair it with `dataOnSignalPatchFilter` to narrow which signals count:

```scala
div(dataOnSignalPatch.debounce(200) := js"@get('/recalculate')").renderMinified
// res14: String = "<div data-on-signal-patch__debounce.200ms=\"@get(&#x27;/recalculate&#x27;)\"></div>"
```

### `dataInit` — fire once on load

Runs when Datastar first processes the element, with `DataInit#delay` and `DataInit#viewTransition`:

```scala
div(dataInit := js"@get('/bootstrap')").renderMinified
// res15: String = "<div data-init=\"@get(&#x27;/bootstrap&#x27;)\"></div>"
```

This is the hook for fetching initial content that is too expensive to render server-side on first paint.

## Choosing a Trigger

| You want to react to | Use |
| -------------------- | --- |
| A user interaction | `dataOn.<event>` |
| An event with no dedicated method | `dataOn("name")` |
| The element scrolling into view | `dataOnIntersect` |
| The passage of time | `dataOnInterval` |
| Signal state changing | `dataOnSignalPatch` |
| The page loading | `dataInit` |

## Integration Points

Every builder here ends in `:=`, which goes through `ToDatastarExpr` and returns a `Dom.Attribute` — see [Attributes](./attributes.md) for that type class and why raw `String` values are rejected. Handler expressions typically read and write [Signals](./signals.md) and call Datastar's `@get`/`@post` actions, which the server answers with [Server-Sent Events](./sse.md).

The `js"..."` interpolator producing those expressions, and the `Dom` types the attributes attach to, come from [HTML](../html.md).
