# HxTrigger

> `HxTrigger` represents the `hx-trigger` attribute, declaring which event fires an HTMX request. It combines an event name with optional modifiers that refine timing (delay, throttle), state (once, changed), source (from), queue strategy, and filtering. `HxTriggerSet` composes multiple triggers in a comma-separated list for complex event handling.

Start from a predefined trigger like `HxTrigger.click` or construct one with `HxTrigger("eventName")`. Add modifiers by chaining methods. Modifiers within the same group (e.g., two `delay` calls) replace earlier values; unrelated modifiers accumulate. Here are the core patterns:

```scala
import zio.http.htmx._
import scala.concurrent.duration._

// Predefined trigger
HxTrigger.click

// Custom event name
HxTrigger("myEvent")

// With modifiers
HxTrigger.input.delay(500.millis).changed

// Multiple triggers
HxTrigger(HxTrigger.click, HxTrigger.load)

// Polling
HxTrigger.every(2.seconds)
```

## Predefined Triggers

Common HTMX events are available as case objects:

- `HxTrigger.click` — Fires on element click.
- `HxTrigger.submit` — Fires on form submission.
- `HxTrigger.load` — Fires when the element is first loaded and added to the DOM.
- `HxTrigger.change` — Fires when an input/select/textarea value changes.
- `HxTrigger.input` — Fires on each keystroke in an input/textarea.
- `HxTrigger.revealed` — Fires when the element scrolls into view (requires Intersection Observer).
- `HxTrigger.intersect` — Fires when the element enters the intersection observer threshold.

All predefined triggers are ready for modifier chaining:

```scala
import zio.blocks.html._
import zio.http.htmx._

input(hxPost := "/search", hxTrigger := HxTrigger.input)
form(hxPost := "/submit", hxTrigger := HxTrigger.submit)
div(hxGet := "/status", hxTrigger := HxTrigger.load)
```

## Custom Events

Construct a trigger from an arbitrary event name using `HxTrigger("eventName")`:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Custom event (must be fired by JavaScript)
div(hxPost := "/api/action", hxTrigger := HxTrigger("custom-event"))
```

The module validates that the event name is non-empty but otherwise accepts any string. Custom events must be fired by your JavaScript code or other HTMX event sources.

## Timing Modifiers

Control when the request fires relative to the triggering event:

**`delay(duration: FiniteDuration)`** adds a `delay:` modifier, waiting before firing the request. Useful for debouncing high-frequency events like typing:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Wait 500ms after typing stops before firing
input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.delay(500.millis)
)
```

**`throttle(duration: FiniteDuration)`** adds a `throttle:` modifier, firing at most once per duration. Useful for rate-limiting requests on frequent events:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Fire at most once per second
input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.throttle(1.second)
)
```

Both modifiers accept any `FiniteDuration`. Calling `delay()` or `throttle()` twice replaces the earlier value:

```scala
import zio.http.htmx._
import scala.concurrent.duration._

val trigger1 = HxTrigger.input.delay(1.second)
val trigger2 = trigger1.delay(500.millis)  // replaces 1.second
```

## State Modifiers

Refine which events fire the request based on element state:

**`once: HxTrigger`** adds the `once` modifier, firing the request exactly once and then disabling the trigger:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Load data only on first page visit
div(hxGet := "/initial-data", hxTrigger := HxTrigger.load.once)
```

**`changed: HxTrigger`** adds the `changed` modifier, firing only when the element's value actually changes (not on every event):

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Fire only if the value is different from the last value
input(
  hxPost := "/validate",
  hxTrigger := HxTrigger.input.changed
)
```

Combine these with timing modifiers for fine-grained control:

```scala
import zio.http.htmx._
import scala.concurrent.duration._

HxTrigger.input.delay(500.millis).changed
```

## Source & Target Control

Direct where the request originates and how it affects other elements:

**`from(selector: String)` or `from(target: HxTarget)`** adds a `from:` modifier, listening for the trigger on a different element. The request still fires on the original element, but it listens for the trigger event on the specified source:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Button fires a request, but listens for clicks on the input
input(id := "search")
button(
  "Go",
  hxPost := "/search",
  hxTrigger := HxTrigger.click.from("#search")
)
```

**`target(selector: String)`** adds a `target:` modifier, restricting the trigger to events from specific descendant elements. Useful for event delegation:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Only fire on clicks within .clickable items
div(
  hxPost := "/item-selected",
  hxTrigger := HxTrigger.click.target(".clickable"),
  div(className := "clickable", "Item 1"),
  div(className := "clickable", "Item 2")
)
```

## Queue Strategy

Control how requests queue when multiple triggers fire in quick succession:

**`queue(strategy: HxTrigger.QueueStrategy)`** adds a `queue:` modifier. Valid strategies are:

- `HxTrigger.QueueStrategy.First` — Queue the first request only; discard later ones until it completes.
- `HxTrigger.QueueStrategy.Last` — Keep the most recent request; discard earlier queued ones.
- `HxTrigger.QueueStrategy.All` — Queue all requests and fire them in order (default HTMX behavior).
- `HxTrigger.QueueStrategy.None` — Abort pending requests and fire the new one immediately.

Here are examples using different queue strategies:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Only fire the most recent request
input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.delay(500.millis).queue(HxTrigger.QueueStrategy.Last)
)

// Fire only the first request in a burst
button(
  hxPost := "/submit",
  hxTrigger := HxTrigger.click.queue(HxTrigger.QueueStrategy.First)
)
```

## Intersection Observer

**`threshold(value: Double)`** adds a `threshold:` modifier for Intersection Observer-based triggers (e.g., `HxTrigger.intersect`). The value is a fraction between 0.0 and 1.0:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Load when 50% of the element is visible
div(
  hxGet := "/load-more",
  hxTrigger := HxTrigger.intersect.threshold(0.5)
)
```

**`root(selector: String)`** adds a `root:` modifier, specifying the container for Intersection Observer calculations:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Observe intersection within a scrollable container
div(
  id := "scrollable-list",
  div(
    hxGet := "/item",
    hxTrigger := HxTrigger.intersect.root(".scrollable-list")
  )
)
```

## JavaScript Filtering

**`filter(expression: Js)`** adds a JavaScript expression that must return true for the request to fire. The `Js` type is intentionally raw—do not build it from unsanitized user input:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Only POST if input has 3+ characters
input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.filter(Js("event.target.value.length > 2"))
)
```

The filter expression has access to the native JavaScript `event` object and the element's JavaScript context.

## Request Consumption

**`consume: HxTrigger`** adds the `consume` modifier, preventing the triggering event from bubbling to parent handlers:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Click on button fires request and stops event propagation
button(
  hxPost := "/action",
  hxTrigger := HxTrigger.click.consume
)
```

## Polling

**`HxTrigger.every(duration: FiniteDuration)`** creates a special polling trigger that fires every N milliseconds/seconds:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Poll every 3 seconds
div(
  hxGet := "/status",
  hxTrigger := HxTrigger.every(3.seconds)
)
```

The duration can be any `FiniteDuration`. Polling triggers can be paused and resumed by HTMX via the `htmx:beforeRequest` and `htmx:afterRequest` events.

## Rendering & Parsing

**`render: String`** produces the literal `hx-trigger` attribute value string:

```scala
import zio.http.htmx._
import scala.concurrent.duration._

val trigger = HxTrigger.input.delay(500.millis).changed
trigger.render  // "input delay:500ms changed"
```

## Multiple Triggers (HxTriggerSet)

Use the `HxTrigger` constructor to combine multiple triggers in a comma-separated list:

```scala
import zio.http.htmx._
import scala.concurrent.duration._

// Fire on click or every 5 seconds
val triggers = HxTrigger(
  HxTrigger.click,
  HxTrigger.every(5.seconds)
)
triggers.render  // "click, every 5s"
```

Assign `HxTriggerSet` directly to the `hxTrigger` attribute:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

div(
  hxGet := "/update",
  hxTrigger := HxTrigger(
    HxTrigger.click,
    HxTrigger.load,
    HxTrigger.every(10.seconds)
  )
)
```

## Common Patterns

`HxTrigger` enables sophisticated event handling through modifiers. Here are practical usage patterns:

### Debounced Search

Delay firing to let the user finish typing, and only fire if the value changed:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

input(
  placeholder := "Search...",
  hxPost := "/api/search",
  hxTrigger := HxTrigger.input.delay(500.millis).changed
)
```

### Rate-Limited Live Updates

Throttle rapid events to avoid overwhelming the server:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

div(
  hxPost := "/analytics",
  hxTrigger := HxTrigger.input.throttle(1.second)
)
```

### Load Once

Fire the request exactly once when the page loads:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  hxGet := "/welcome-message",
  hxTrigger := HxTrigger.load.once
)
```

### Lazy Loading with Intersection Observer

Load content when it scrolls into view:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Load when 10% of the image is visible
img(
  src := "/placeholder.jpg",
  hxGet := "/lazy-image",
  hxSwap := HxSwap.OuterHTML,
  hxTrigger := HxTrigger.intersect.threshold(0.1)
)
```

### Dual Triggers: Click or Automatic

Fire on click or every 30 seconds:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

button(
  hxPost := "/refresh",
  hxTrigger := HxTrigger(
    HxTrigger.click,
    HxTrigger.every(30.seconds)
  ),
  "Refresh Now"
)
```

### Event Delegation

Parent element listens for clicks on child items:

```scala
import zio.blocks.html._
import zio.http.htmx._

ul(
  hxPost := "/item-selected",
  hxTrigger := HxTrigger.click.target("li"),
  li("Item 1"),
  li("Item 2"),
  li("Item 3")
)
```

## Integration with Other Module Types

`HxTrigger` pairs with `HxTarget` (where to swap) and `HxSwap` (how to swap):

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

button(
  hxPost := "/api/action",
  hxTrigger := HxTrigger.click.delay(100.millis),
  hxTarget := HxTarget.closest("form"),
  hxSwap := HxSwap.InnerHTML.settle(250.millis),
  "Submit"
)
```

The `ToHtmxValue` type class handles both `HxTrigger` (single trigger) and `HxTriggerSet` (multiple triggers), so you can assign either directly to the `hxTrigger` attribute key.
