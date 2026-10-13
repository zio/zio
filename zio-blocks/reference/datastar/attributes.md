# Datastar Attributes

> `DatastarAttributes` supplies 27 `data*` helpers that make rendered HTML reactive. Each returns either a finished `Dom.Attribute` or a `DatastarAttrKey` awaiting a value, and `ToDatastarExpr` decides what may be assigned — rejecting raw `String` at compile time. The package object extends this trait, so one wildcard import brings every helper into scope. The two types every helper funnels through:

```scala
final class DatastarAttrKey(val name: String) {
  def :=[T](value: T)(implicit toDatastarExpr: ToDatastarExpr[T]): Dom.Attribute
}

trait ToDatastarExpr[-A] {
  def toDatastarExpr(a: A): String
}
```

## Motivation

Datastar's whole interface is attribute names and expression strings. `data-text="$count"` displays a signal; `data-show="$count > 0"` conditionally renders; `data-class:active="$selected"` toggles a class. Getting one character wrong produces an attribute the framework ignores, with no error anywhere.

The failure that costs the most time is subtler than a typo. Attribute values are *expressions*, evaluated in the browser — `"$count"` reads a signal, but `"count"` is a string literal that happens to look right. Interpolating a Scala `String` into an expression position produces exactly that: valid HTML, ignored semantics, a blank element.

These helpers make the attribute name a method call and the value type-checked. There is no attribute name to misspell, and `ToDatastarExpr` is deliberately unavailable for `String`, so the one mistake that fails silently in the browser fails loudly at compile time instead.

## Quick Showcase

Attributes compose with the HTML DSL like any other:

```scala
import zio.blocks.html._
import zio.http.datastar._

val open = Signal[Boolean]("open")

val panel = div(
  dataSignals(open := false),
  button(dataOn.click := js"$open = !$open", "toggle"),
  div(dataShow := open, dataClass("visible") := open, "panel contents")
)
```

Rendering produces the attribute names and expressions Datastar expects:

```scala
panel.renderMinified
// res0: String = "<div data-signals=\"{&quot;open&quot;: false}\"><button data-on:click=\"$open = !$open\">toggle</button><div data-show=\"$open\" data-class:visible=\"$open\">panel contents</div></div>"
```

## Attribute Naming

Two shapes appear in the rendered output, and which one a helper produces tells you whether it takes a key.

| Helper form | Renders as | Example |
| ----------- | ---------- | ------- |
| No key | `data-<name>` | `dataText` → `data-text` |
| Keyed | `data-<name>:<key>` | `dataClass("active")` → `data-class:active` |
| Own attribute | `data-<multi-word-name>` | `dataOnIntersect` → `data-on-intersect` |
| Modified | `…__<modifier>` | `dataOn.click.once` → `data-on:click__once` |

Keys derived from signal names are kebab-cased, so a `Signal[Int]("itemCount")` used as a `dataComputed` key becomes `data-computed:item-count`.

The third row is the one that breaks the pattern. A DOM event is a *key* on `data-on`, giving `data-on:click`, but the non-DOM triggers are attribute names in their own right — `data-on-intersect`, `data-on-interval`, `data-on-signal-patch` — with hyphens and no key. Their modifiers still attach with `__`.

## Declaring State

Two helpers put signals on the page.

### `dataSignals` — initial values

`dataSignals` has three forms. Given one or more `SignalUpdate`s it renders the whole object as the attribute value:

```scala
import zio.blocks.html._
import zio.http.datastar._

val price    = Signal[Double]("price")
val quantity = Signal[Int]("quantity")
```

Several updates become a single `data-signals` attribute:

```scala
div(dataSignals(price := 9.99, quantity := 1)).renderMinified
// res2: String = "<div data-signals=\"{&quot;price&quot;: 9.99, &quot;quantity&quot;: 1}\"></div>"
```

Given a single `Signal`, it returns a `DataSignalsBuilder` for the keyed form — `data-signals:<name>` — which sets one signal rather than an object:

```scala
div(dataSignals(price) := js"42.0").renderMinified
// res3: String = "<div data-signals:price=\"42.0\"></div>"
```

The builder also carries the case modifiers described below. The third form, bare `dataSignals`, is a `DatastarAttrKey` for assigning a raw expression.

### `dataComputed` — derived signals

`dataComputed(signal)` declares a signal whose value is an expression over others, recomputed by the browser whenever an input changes:

```scala
div(dataComputed(Signal[Double]("total")) := js"$price * $quantity").renderMinified
// res4: String = "<div data-computed:total=\"$price * $quantity\"></div>"
```

The signal name becomes the attribute key, kebab-cased.

## Displaying and Binding

Three helpers read a signal and change what the element shows, without any handler being involved.

### `dataText` and `dataShow`

`dataText` sets an element's text content from an expression, and `dataShow` controls its visibility:

```scala
import zio.blocks.html._
import zio.http.datastar._

val count = Signal[Int]("count")
```

Both take an expression, so a signal or a `js"..."` both work:

```scala
span(dataText := count).renderMinified
// res6: String = "<span data-text=\"$count\"></span>"
div(dataShow := js"$count > 0", "non-empty").renderMinified
// res7: String = "<div data-show=\"$count &gt; 0\">non-empty</div>"
```

### `dataBind` — two-way input binding

`dataBind(signal)` binds a form control to a signal in both directions, and needs no value:

```scala
input(dataBind(count)).renderMinified
// res8: String = "<input data-bind:count/>"
```

The rendered attribute is `data-bind:<name>` with no value, which is how Datastar recognizes the binding form.

## Styling

Three helpers follow the same keyed-or-bare pattern: a key selects what to modify, and the expression decides when.

| Helper | Keyed form | Purpose |
| ------ | ---------- | ------- |
| `dataClass(name)` | `data-class:<name>` | Toggle one class |
| `dataClass` | `data-class` | Object of class names to conditions |
| `dataStyle(name)` | `data-style:<name>` | Set one CSS property |
| `dataStyle` | `data-style` | Object of properties to values |
| `dataAttr(name)` | `data-attr:<name>` | Set one HTML attribute |
| `dataAttr` | `data-attr` | Object of attributes to values |

The keyed form is the common one, toggling a single class from a condition:

```scala
import zio.blocks.html._
import zio.http.datastar._

val selected = Signal[Boolean]("selected")
```

Keyed and bare forms render differently, and the bare form takes an object expression:

```scala
div(dataClass("active") := selected).renderMinified
// res10: String = "<div data-class:active=\"$selected\"></div>"
div(dataStyle("color") := js"$selected ? 'red' : 'gray'").renderMinified
// res11: String = "<div data-style:color=\"$selected ? &#x27;red&#x27; : &#x27;gray&#x27;\"></div>"
```

## Effects and References

`dataEffect` runs an expression whenever its dependencies change, `dataIndicator(signal)` sets a signal while a request is in flight, and `dataRef(name)` exposes the element to expressions by name:

```scala
import zio.blocks.html._
import zio.http.datastar._

val loading = Signal[Boolean]("loading")
```

`dataIndicator` and `dataRef` are complete attributes rather than keys, since their argument is the whole content:

```scala
button(dataIndicator(loading), "save").renderMinified
// res13: String = "<button data-indicator:loading>save</button>"
div(dataRef("panel")).renderMinified
// res14: String = "<div data-ref:panel></div>"
```

## Morph Control

When the server patches elements, Datastar morphs the existing DOM rather than replacing it. Four helpers control what that morph may touch — all of them complete attributes taking no expression:

| Helper | Effect |
| ------ | ------ |
| `dataIgnore` | Datastar ignores this element and its subtree entirely |
| `dataIgnoreSelf` | Ignores this element but still processes its children |
| `dataIgnoreMorph` | Preserves this element across morphs |
| `dataPreserveAttr(attrs*)` | Preserves the named attributes across morphs |

Preserving an attribute matters for state the server does not know about — a scroll position, an open `details`, a user-resized width:

```scala
import zio.blocks.html._
import zio.http.datastar._
```

The first three are boolean attributes; the fourth names what to keep:

```scala
div(dataIgnore).renderMinified
// res16: String = "<div data-ignore></div>"
div(dataPreserveAttr("open", "style")).renderMinified
// res17: String = "<div data-preserve-attr=\"open style\"></div>"
```

## Other Attributes

`dataJsonSignals` renders the full signal state as JSON, which is useful for debugging a page's reactive state, and `dataOnSignalPatchFilter` narrows which signal patches a `data-on-signal-patch` handler responds to. Both are `DatastarAttrKey`s taking an expression.

The trigger helpers — `dataOn`, `dataInit`, `dataOnIntersect`, `dataOnInterval`, `dataOnSignalPatch` — are covered in [Event Handlers](./events.md).

## ToDatastarExpr

`ToDatastarExpr[A]` is the type class deciding what may be assigned with `:=`. It is contravariant, and instances derive from `ToJs[A]`, so anything the HTML module can render as JavaScript works here.

### What Is Accepted

An instance exists for any `A` with a `ToJs[A]` — which covers `Js` values from the `js"..."` interpolator, `Signal[A]`, `SignalUpdate[A]`, and `DatastarRef`:

```scala
import zio.blocks.html._
import zio.http.datastar._

val count = Signal[Int]("count")
```

Each renders to its expression form:

```scala
span(dataText := count).renderMinified
// res19: String = "<span data-text=\"$count\"></span>"
span(dataText := js"$count + 1").renderMinified
// res20: String = "<span data-text=\"$count + 1\"></span>"
```

### Why `String` Is Rejected

There is deliberately **no** usable instance for `String`. The mechanism is a private `NotString` witness with two conflicting instances for `String`, which makes the implicit search ambiguous, plus an `@implicitAmbiguous` annotation supplying the message:

```scala
@implicitAmbiguous(
  "Raw String values are not allowed in Datastar expression positions. " +
  "Use js\"...\" for Datastar expressions or typed Signal/SignalUpdate values."
)
private sealed trait NotString[A]
```

The result is that `dataText := "count"` does not compile, and the error tells you to use `js"..."` instead.

:::tip[This is the module's most valuable guard]
Assigning a raw `String` is the one Datastar mistake that produces valid HTML and no error at runtime — the attribute renders, the browser reads it as a literal, and the element silently shows nothing useful. Making it a compile error is worth the unusual implicit machinery.
:::

`@implicitNotFound` covers the other case: a type with no `ToJs` instance at all gets a message naming the type and pointing at the same two options.

## DatastarAttrKey

`DatastarAttrKey` is the low-level escape hatch — a raw attribute name plus `:=`. Every keyed helper returns one, and you can construct one for an attribute the module has no helper for yet.

Since the constructor is `private[datastar]`, reach it through a helper that returns a bare key. `dataAttr`, `dataClass`, `dataStyle`, `dataText`, `dataShow`, `dataEffect`, `dataSignals`, `dataJsonSignals`, and `dataOnSignalPatchFilter` all have bare forms:

```scala
import zio.blocks.html._
import zio.http.datastar._

val theme = Signal[String]("theme")
```

The bare form takes an object expression covering several keys at once:

```scala
div(dataStyle := js"{color: $theme}").renderMinified
// res22: String = "<div data-style=\"{color: $theme}\"></div>"
```

:::warning[`dataAttr` collides with the HTML module]
`zio.blocks.html` also defines `dataAttr`, for plain static `data-*` attributes — `dataAttr("id") := "42"` renders `data-id="42"`. Datastar's renders `data-attr:id="<expression>"`, a reactive binding. With both packages wildcard-imported, the bare name is ambiguous and will not compile.

Qualify the one you mean, `zio.http.datastar.dataAttr` for the reactive form, or import selectively. The ambiguity error is the good case: it stops you silently getting a static attribute where you wanted a binding.
:::

## Integration Points

Every helper returns a `Dom.Attribute` from [HTML](../html.md), so Datastar attributes are indistinguishable from `id` or `class` at the point of use and compose in the same element constructors. `ToDatastarExpr` derives from that module's `ToJs`, and the `js"..."` interpolator producing most expression values is also its.

Values assigned here are usually [Signals](./signals.md), and the triggers that pair with these attributes are in [Event Handlers](./events.md). What the server sends back to change them is in [Server-Sent Events](./sse.md).
