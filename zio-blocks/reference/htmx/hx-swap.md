# HxSwap

> `HxSwap` represents the `hx-swap` attribute, controlling how HTMX replaces DOM content after a successful response. It combines a base strategy (innerHTML, outerHTML, etc.) with optional modifiers that refine timing, animation, scrolling, and focus behavior.

`HxSwap` represents the `hx-swap` attribute, controlling how HTMX replaces DOM content after a successful response. It combines a base strategy (innerHTML, outerHTML, etc.) with optional modifiers that refine timing, animation, scrolling, and focus behavior.

Start from one of the predefined strategies, then call modifier methods to customize timing, animation, and scroll behavior. All modifiers are optional, and calling the same modifier twice replaces the earlier value rather than stacking. Here are the core patterns:

```scala
import zio.http.htmx._
import scala.concurrent.duration._

// Base strategy only
HxSwap.InnerHTML

// With timing modifiers
HxSwap.OuterHTML.swap(1.second).settle(500.millis)

// With animation and scroll
HxSwap.BeforeEnd.transition.scroll(HxSwap.ScrollPosition.Top)

// Full pipeline
HxSwap.InnerHTML
  .swap(500.millis)
  .settle(250.millis)
  .transition
  .scroll(HxSwap.ScrollPosition.Bottom)
  .show(HxSwap.ShowPosition.Top)
  .focusScroll(true)
```

## Swap Strategies

Core HTMX strategies define where and how content replaces the DOM:

- `HxSwap.InnerHTML` — Replaces the inner content of the target element. This is the default HTMX behavior.
- `HxSwap.OuterHTML` — Replaces the target element itself, including its opening and closing tags.
- `HxSwap.TextContent` — Replaces only the text content, leaving the element structure intact.
- `HxSwap.BeforeBegin` — Inserts the response as a sibling before the target element.
- `HxSwap.AfterBegin` — Inserts the response as the first child of the target element.
- `HxSwap.BeforeEnd` — Inserts the response as the last child of the target element.
- `HxSwap.AfterEnd` — Inserts the response as a sibling after the target element.
- `HxSwap.Delete` — Removes the target element; response content is discarded.
- `HxSwap.NoneSwap` — Prevents any content from being swapped into the DOM; the response is ignored.

All strategies are available as immutable `HxSwap` values, ready for modifier chaining:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(hxSwap := HxSwap.InnerHTML)
div(hxSwap := HxSwap.OuterHTML)
div(hxSwap := HxSwap.BeforeBegin)
```

## Timing Modifiers

Control when and how long the swap operation takes:

**`swap(duration: FiniteDuration)`** adds a `swap:` delay before the swap begins. Useful for coordinating with request latency or preparing the DOM:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Wait 500ms after response before swapping
div(hxSwap := HxSwap.InnerHTML.swap(500.millis))
```

**`settle(duration: FiniteDuration)`** adds a `settle:` delay after the swap before settling (CSS transitions, settling messages). This allows animations to run:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Swap immediately, but wait 250ms for CSS to settle
div(hxSwap := HxSwap.InnerHTML.settle(250.millis))
```

Both `swap()` and `settle()` accept any `FiniteDuration` that renders to a valid HTMX duration (milliseconds: `ms`, seconds: `s`, or bare integers treated as milliseconds). Calling either method twice replaces the first value:

```scala
import zio.http.htmx._
import scala.concurrent.duration._

val swap1 = HxSwap.InnerHTML.swap(1.second)
val swap2 = swap1.swap(500.millis)  // replaces 1.second with 500.millis
```

## Animation & Transition

**`transition: HxSwap`** enables the `transition:true` modifier, allowing CSS transitions to run during the swap. Useful with timing modifiers to coordinate animation:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Enable transitions and give them 300ms to complete
div(hxSwap := HxSwap.InnerHTML.transition.settle(300.millis))
```

## Scroll Behavior

Control where the page scrolls after a swap:

**`scroll(position: HxSwap.ScrollPosition)`** adds a `scroll:` modifier. Valid positions are `HxSwap.ScrollPosition.Top` and `HxSwap.ScrollPosition.Bottom`:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Scroll to top after swapping
div(hxSwap := HxSwap.InnerHTML.scroll(HxSwap.ScrollPosition.Top))

// Scroll to bottom (useful for chat/log appends)
div(hxSwap := HxSwap.BeforeEnd.scroll(HxSwap.ScrollPosition.Bottom))
```

**`show(position: HxSwap.ShowPosition)`** adds a `show:` modifier to control where newly swapped content becomes visible. Valid positions are `HxSwap.ShowPosition.Top` and `HxSwap.ShowPosition.Bottom`:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Show the top of the newly swapped content
div(hxSwap := HxSwap.InnerHTML.show(HxSwap.ShowPosition.Top))
```

## Focus & Title Management

**`focusScroll(enabled: Boolean)`** sets the `focusScroll:` modifier to control whether focus moves during swap. Pass `true` to focus the swapped element, `false` to disable focus movement:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Enable focus scroll (default HTMX behavior)
div(hxSwap := HxSwap.InnerHTML.focusScroll(true))

// Disable automatic focus movement
div(hxSwap := HxSwap.InnerHTML.focusScroll(false))
```

**`ignoreTitle: HxSwap`** enables the `ignoreTitle:true` modifier, preventing HTMX from updating the document title if the response contains an `<title>` tag:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Don't update the page title even if response has one
div(hxSwap := HxSwap.InnerHTML.ignoreTitle)
```

## Rendering & Parsing

**`render: String`** produces the literal `hx-swap` attribute value string:

```scala
import zio.http.htmx._
import scala.concurrent.duration._

val swap = HxSwap.InnerHTML.swap(1.second).settle(500.millis).transition
swap.render  // "innerHTML swap:1s settle:500ms transition:true"
```

**`HxSwap.parse(value: String): Either[String, HxSwap]`** decodes a rendered HTMX swap string into a typed `HxSwap` value. The parser accepts the same format that `render` produces:

```scala
import zio.http.htmx._

val parsed = HxSwap.parse("outerHTML swap:2s settle:250ms transition:true scroll:bottom")
// Right(HxSwap.OuterHTML.swap(2 seconds).settle(250 millis).transition.scroll(ScrollPosition.Bottom))
```

Parse failures return a descriptive `Left` with the error reason:

```scala
import zio.http.htmx._

HxSwap.parse("")              // Left("Empty HTMX swap value")
HxSwap.parse("bogus")         // Left("Unknown HTMX swap strategy: bogus")
HxSwap.parse("innerHTML swap:later")  // Left("Unsupported HTMX duration: later")
HxSwap.parse("innerHTML scroll:middle")  // Left("Invalid HTMX scroll position: middle")
```

## Common Patterns

Effective swap strategies enhance user experience through proper timing and animation. Here are practical usage patterns:

### Fade-In New Content

Combine `HxSwap#transition` with `HxSwap#settle` delay to fade in new elements:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

div(
  hxGet := "/new-items",
  hxSwap := HxSwap.BeforeEnd.transition.settle(300.millis)
)
```

### Scroll New Content Into View

Use `HxSwap#scroll` with `BeforeEnd` or `AfterEnd` to append content and scroll to it:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

div(
  id := "messages",
  hxGet := "/new-messages",
  hxTrigger := HxTrigger.every(2.seconds),
  hxSwap := HxSwap.BeforeEnd.scroll(HxSwap.ScrollPosition.Bottom)
)
```

### Replace and Focus

Use `InnerHTML` or `OuterHTML` with `HxSwap#focusScroll` to replace content and move focus:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/submit",
  hxSwap := HxSwap.OuterHTML.focusScroll(true),
  button("Submit")
)
```

### Preserve Animations While Settling

Increase the `HxSwap#settle` delay to let CSS animations complete before HTMX considers the swap done:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// CSS animations run for 500ms; settle after they complete
div(
  hxPost := "/update",
  hxSwap := HxSwap.InnerHTML.transition.settle(500.millis)
)
```

## Integration with Other Module Types

`HxSwap` is most often paired with `HxTrigger` (when to request) and `HxTarget` (where to apply the swap):

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

button(
  hxPost := "/api/action",
  hxTrigger := HxTrigger.click.delay(100.millis),
  hxTarget := HxTarget.closest("form"),
  hxSwap := HxSwap.InnerHTML.settle(250.millis),
  "Perform Action"
)
```

The `ToHtmxValue[HxSwap]` type class renders automatically, enabling you to assign `HxSwap` values directly using the `:=` operator on the `hxSwap` attribute key.
