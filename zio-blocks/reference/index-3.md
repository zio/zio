# HTMX

> `zio.http.htmx` is a **typed HTMX DSL** for building safe, compile-time HTMX attribute declarations within `zio.blocks.html`. It provides immutable types representing HTMX events, swap strategies, target selectors, and request modifiers, eliminating stringly-typed misuse through rich domain types while maintaining explicit string surfaces for URLs and raw JavaScript where needed.

Core types: `HxTrigger`, `HxSwap`, `HxTarget`, `HxParams`, `HxUrlUpdate`, `HxEncoding`, `HxSync`, `HtmxAttrKey`, `ToHtmxValue`.

Here are the core patterns of typed HTMX construction:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Compile-safe event triggers with modifiers
div(hxPost := "/search", hxTrigger := HxTrigger.input.delay(500.millis))

// Typed swap strategies with multiple modifiers
div(hxSwap := HxSwap.InnerHTML.swap(1.second).transition)

// Type-safe target selection
button(hxTarget := HxTarget.closest("form"))

// Request control through domain types
form(hxParams := HxParams.only("query", "page"))
```

## Introduction

The `htmx` module eliminates accidental stringly mistakes in HTMX attribute construction. Rather than writing raw strings like `"innerHTML swap:1s settle:500ms transition:true"`, you compose domain values through a type-safe DSL that guides you toward correct HTMX syntax at compile time.

Most attributes are narrower than plain HTML strings. `hxSwap` accepts `HxSwap`, `hxTarget` accepts `HxTarget`, and selector-only attributes accept `CssSelector`. When a string surface is genuinely necessary—for URLs, custom JavaScript filters, or dynamic values—the DSL provides explicit entry points.

## Motivation

HTMX introduces many new attributes and modifier combinations. Writing these as raw strings is error-prone:
- Typos in strategy names (`"innterHTML"`, `"delya:1s"`) silently fail at runtime
- Mixing unrelated modifiers in the same attribute (`"innerHTML queue:first threshold:0.5"`) compiles but confuses intent
- URLs and JavaScript filters have no type guidance, leading to unsafe assumptions

The typed HTMX DSL catches these mistakes at compile time:
- Strategy names are exhaustive case objects: `HxSwap.InnerHTML`, `HxSwap.AfterBegin`, etc.
- Modifier methods enforce correct grouping: repeated calls within the same modifier group (`swap()` or `settle()`) replace earlier values, while different groups (`swap`, `settle`, `transition`) can be combined
- Type-safe attributes like `hxTarget` prevent passing raw strings where structured selectors belong
- Extensible type class `ToHtmxValue` lets custom domain values render themselves to HTMX syntax

## Installation

Add the HTMX module to your project dependencies:

```scala
// JVM
libraryDependencies += "dev.zio" %% "zio-blocks-http-htmx" % "0.0.51"

// Scala.js
libraryDependencies += "dev.zio" %%% "zio-blocks-http-htmx" % "0.0.51"
```

Supported Scala versions: Scala 3.x. The module is cross-compiled for JVM and Scala.js.

## Overview

Each type in the module addresses a specific HTMX concern:

**Event Triggering:** `HxTrigger` declares which event fires a request (click, input, load, custom). Modifiers add timing (`HxTrigger#delay`, `HxTrigger#throttle`), use `HxTrigger#filter` for filtering, source control (`from`), and queue strategy (`queue`). `HxTriggerSet` composes multiple triggers in a comma-separated list for more complex interactions.

**Swap Strategies:** `HxSwap` selects how the response content replaces the DOM (innerHTML, outerHTML, beforeBegin, etc.). Modifiers control timing with `HxSwap#swap` and `HxSwap#settle`, animation (`transition`), scrolling (`scroll`, `show`), and focus behavior (`focusScroll`, `ignoreTitle`).

**Target Selection:** `HxTarget` selects where swap happens (current element, closest ancestor, next sibling, custom CSS selector). Variants support common patterns like `HxTarget.This`, `HxTarget.closest(selector)`, and `HxTarget.next(selector)`.

**Request Control:** `HxParams` filters which form fields are submitted (all, none, only listed, all except listed). `HxUrlUpdate` controls whether the URL bar updates after the request. `HxEncoding` and `HxSync` handle multi-step request concerns.

**Infrastructure:** `HtmxAttrKey` is the typed attribute key binding a name to a value type. `ToHtmxValue` is the type class rendering domain values to HTMX attribute strings, enabling custom types to participate in the DSL.

## How They Work Together

A typical HTMX request flow combines multiple types: trigger defines when, swap defines what, target defines where, and request control refines how. Here is the overall flow:

```
User Action
    ↓
HxTrigger (when: click, input, load, every N seconds, etc.)
    ├─ Modifiers: delay, throttle, filter, from, queue
    ↓
Request Sent
    ├─ URL: hxPost, hxGet, hxPut, hxPatch, hxDelete
    ├─ Parameters: HxParams (all, none, only, not)
    ├─ Encoding: HxEncoding (multipart override; default is URL-encoded)
    └─ Synchronization: HxSync (queue strategy, abort behavior)
    ↓
Response Received
    ↓
HxTarget (where: this, closest, find, next, previous, css selector)
    ↓
HxSwap (how: innerHTML, outerHTML, beforeBegin, etc.)
    ├─ Modifiers: swap delay, settle delay, transition, scroll, show
    └─ Focus: ignoreTitle, focusScroll
    ↓
DOM Updated & Settled
```

**Example workflow:** When a user types in a search input, fire a POST request to `/api/search` after a 500ms delay. Send only the query and page parameters. Replace the results section (the closest parent with CSS class `results`) with innerHTML strategy, wait 250ms for CSS transitions, then scroll the results into view:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

input(
  placeholder := "Search...",
  hxPost := "/api/search",
  hxTrigger := HxTrigger.input.delay(500.millis),
  hxParams := HxParams.only("query", "page"),
  hxTarget := HxTarget.closest(".results"),
  hxSwap := HxSwap.InnerHTML.settle(250.millis).scroll(HxSwap.ScrollPosition.Top)
)
```

This example shows how types guide each concern: `HxTrigger.input` is compile-checked (not a typo), `HxTrigger#delay` is a method not a string, `HxParams.only()` is exhaustively typed, `HxTarget.closest()` takes a string but validates non-emptiness, and `HxSwap` chains modifiers with type safety.

## Common Patterns

The HTMX DSL supports several common interaction patterns. Here are representative examples:

### Pattern 1: Progressive Enhancement with Boost

Use `hxBoost := true` to transform regular links and form submissions into HTMX requests, maintaining server-side rendering and graceful degradation:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Entire nav boosted—clicks are HTMX requests, but work without JS
nav(
  hxBoost := true,
  ul(
    li(a(href := "/", "Home")),
    li(a(href := "/products", "Products")),
    li(a(href := "/contact", "Contact"))
  )
)
```

### Pattern 2: Polling and Periodic Updates

Use `HxTrigger.every()` with a duration to poll an endpoint at regular intervals:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Poll every 2 seconds for new notifications
div(
  id := "notifications",
  hxGet := "/api/notifications",
  hxTrigger := HxTrigger.every(2.seconds),
  hxSwap := HxSwap.InnerHTML
)
```

### Pattern 3: Chained Modifiers for Complex Interactions

Combine multiple trigger modifiers to refine when and how a request fires:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

// Fire on input, throttle to once per second, only if value changed
input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.throttle(1.second).changed,
  hxSwap := HxSwap.InnerHTML
)
```

Modifier methods return updated `HxTrigger` instances, so you can chain: `HxTrigger.click.delay(100.millis).once` creates a single-fire click handler with a delay.

### Pattern 4: Out-of-Band Swaps

Update multiple DOM regions with a single response using `hxSwapOob` (out-of-bounds):

```scala
import zio.blocks.html._
import zio.http.htmx._

// Main content updates inline; status badge updates separately
div(
  hxPost := "/api/action",
  hxSwap := HxSwap.InnerHTML,
  button("Submit"),
  div(id := "status", hxSwapOob := HxSwapOob.using(HxSwap.InnerHTML), "Ready")
)
```

The response contains both the new main content and a separate element targeted by `hxSwapOob`, allowing one request to update multiple areas.

### Pattern 5: Conditional Rendering with JavaScript Filters

Use `HxTrigger#filter` with the `Js` type to add a JavaScript condition that gates the request:

```scala
import zio.blocks.html._
import zio.http.htmx._

input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.filter(Js("event.target.value.length > 2")),
  "Only POST if search has 3+ characters"
)
```

The `Js` type is intentionally raw—do not build it from unsanitized user input.

## Integration Points

**With `zio.blocks.html`:** All HTMX attributes integrate seamlessly into the HTML DSL. `HtmxAttributes` is mixed into the `zio.http.htmx` package object, making attributes like `hxPost`, `hxTrigger`, and `hxSwap` directly available when you `import zio.http.htmx._`.

**With `zio.http` URL/Path types:** URL-bearing attributes accept `URL` and `Path` types from `zio.http`, which are then rendered to valid URL strings. The `ToHtmxValue` type class provides encoding for these types.

**With `zio.blocks.schema` for JSON encoding:** For domain values, use `HxVals.from(...)` and `HxHeadersValue.from(...)` to encode values via their `Schema` to JSON. This allows schema-backed types to be automatically JSON-encoded in HTMX attributes.

**With CSS selectors:** Attributes that accept selectors (hxTarget, hxSelect, hxDisabledElt, hxIndicator) accept `CssSelector` from `zio.blocks.html`, providing type-safe selector construction.

**With JavaScript:** Attributes that accept raw JavaScript expressions (`hxOn:*`, `hxTrigger.filter()`) accept the `Js` type, making it explicit that you're writing unescaped JavaScript code. This prevents accidental XSS while allowing intentional dynamic behavior.

**With headers:** The `zio.http.htmx.headers` submodule provides typed HTMX request/response headers (HX-Request, HX-Trigger, HX-Redirect, etc.), letting you inspect and build headers with the same type safety as attributes.

**Extending with custom types:** Implement `ToHtmxValue[MyType]` to let your domain types render themselves in the DSL. For example, a custom `enum Status { Active, Inactive }` defines `implicit val statusToHtmx: ToHtmxValue[Status] = ...` and renders directly in HTMX attributes.

## Running the Examples

All code from this guide is available as runnable examples in the `zio-blocks-htmx-examples` module.

**1. Clone the repository and navigate to the project:**

Start by cloning the repository and entering the project directory:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Usage

Demonstrates fundamental HTMX attribute construction: triggering requests (hxPost, hxGet), swapping strategies (InnerHTML, OuterHTML), and target selection (This, closest, find). Shows how the typed DSL ensures correct HTMX syntax at compile time. Here is the source code:

```scala title="zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/BasicUsage.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zioBlocksHtmx

import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

/**
 * HTMX DSL — Basic Usage
 *
 * Demonstrates fundamental HTMX attribute construction: triggering requests
 * (hxPost, hxGet), swapping strategies (InnerHTML, OuterHTML), and target
 * selection (This, closest, find). Shows how the typed DSL ensures correct HTMX
 * syntax at compile time.
 *
 * Run with: sbt "zio-blocks-htmx-examples/runMain zioBlocksHtmx.BasicUsage"
 */
object BasicUsage {
  def main(args: Array[String]): Unit = {
    println("=== HTMX Basic Usage Examples ===\n")

    // Example 1: Simple click trigger with POST
    println("1. Click trigger with POST request:")
    val clickButton = button(
      hxPost    := "/api/action",
      hxTrigger := HxTrigger.click,
      "Click me"
    )
    println(s"  Rendered: $clickButton\n")

    // Example 2: Input with GET request
    println("2. Input with debounced GET request:")
    val searchInput = input(
      `type`      := "text",
      placeholder := "Search...",
      hxGet       := "/api/search",
      hxTrigger   := HxTrigger.input.delay(500.millis),
      hxTarget    := HxTarget.next("div")
    )
    println(s"  Rendered: $searchInput\n")

    // Example 3: Different swap strategies
    println("3. Swap strategies:")
    val innerHTMLDiv = div(
      id     := "content",
      hxGet  := "/api/content",
      hxSwap := HxSwap.InnerHTML,
      "Click to load inner content"
    )
    val outerHTMLDiv = div(
      id     := "card",
      hxPost := "/api/replace",
      hxSwap := HxSwap.OuterHTML,
      "This entire div will be replaced"
    )
    val appendDiv = div(
      id     := "messages",
      hxGet  := "/api/new-message",
      hxSwap := HxSwap.BeforeEnd.scroll(HxSwap.ScrollPosition.Bottom),
      "New messages will be appended"
    )
    println(s"  InnerHTML: ${innerHTMLDiv}")
    println(s"  OuterHTML: ${outerHTMLDiv}")
    println(s"  Append with scroll: ${appendDiv}\n")

    // Example 4: Target selection patterns
    println("4. Target selection:")
    val targetThis = button(
      hxPost   := "/api/toggle",
      hxTarget := HxTarget.This,
      "Toggle this button"
    )
    val targetClosest = button(
      hxPost   := "/api/validate",
      hxTarget := HxTarget.closest("form"),
      "Validate form"
    )
    val targetFind = div(
      hxGet    := "/api/update",
      hxTarget := HxTarget.find(".result"),
      "Content with .result child"
    )
    println(s"  This: $targetThis")
    println(s"  Closest: $targetClosest")
    println(s"  Find: $targetFind\n")

    // Example 5: Form submission with parameters
    println("5. Form submission with selective parameters:")
    val searchForm = form(
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.submit,
      hxParams  := HxParams.only("query", "page"),
      hxSwap    := HxSwap.InnerHTML,
      input(`type`  := "text", name   := "query", placeholder := "Query"),
      input(`type`  := "hidden", name := "page", value        := "1"),
      input(`type`  := "hidden", name := "unused", value      := "ignored"),
      button(`type` := "submit", "Search")
    )
    println(s"  Form with selective params: $searchForm\n")

    // Example 6: Load event with once modifier
    println("6. Load event with once modifier:")
    val initialLoadDiv = div(
      id        := "initial",
      hxGet     := "/api/bootstrap-data",
      hxTrigger := HxTrigger.load.once,
      hxSwap    := HxSwap.InnerHTML,
      "Loading initial data..."
    )
    println(s"  Load once: $initialLoadDiv\n")

    // Example 7: Change event on select
    println("7. Change event on select element:")
    val selectDropdown = select(
      name      := "category",
      hxPost    := "/api/category-changed",
      hxTrigger := HxTrigger.change,
      hxTarget  := HxTarget.next("div"),
      option(value := "all", "All Categories"),
      option(value := "news", "News"),
      option(value := "updates", "Updates")
    )
    println(s"  Select with change: $selectDropdown\n")

    println("✓ Basic usage examples complete")
  }
}
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/BasicUsage.scala))

Run this example with the following command:

```bash
sbt "zio-blocks-htmx-examples/runMain zioBlocksHtmx.BasicUsage"
```

### Advanced Patterns

Demonstrates complex HTMX interactions: combining multiple triggers, chaining modifiers, controlling request queuing, animation with transitions, and JavaScript-based filtering. Shows how modifiers compose to create sophisticated client-side behaviors. Here is the source code:

```scala title="zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/AdvancedPatterns.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zioBlocksHtmx

import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

/**
 * HTMX DSL — Advanced Patterns
 *
 * Demonstrates complex HTMX interactions: combining multiple triggers, chaining
 * modifiers, controlling request queuing, animation with transitions, and
 * JavaScript-based filtering. Shows how modifiers compose to create
 * sophisticated client-side behaviors.
 *
 * Run with: sbt "zio-blocks-htmx-examples/runMain
 * zioBlocksHtmx.AdvancedPatterns"
 */
object AdvancedPatterns {
  def main(args: Array[String]): Unit = {
    println("=== HTMX Advanced Patterns ===\n")

    // Example 1: Debounced search with state modifier
    println("1. Debounced search with value-changed detection:")
    val debouncedSearch = input(
      `type`      := "text",
      name        := "query",
      placeholder := "Type to search...",
      hxPost      := "/api/search",
      hxTrigger   := HxTrigger.input.delay(500.millis).changed,
      hxParams    := HxParams.only("query"),
      hxTarget    := HxTarget.next("div"),
      hxSwap      := HxSwap.InnerHTML
    )
    println(s"  Trigger: ${HxTrigger.input.delay(500.millis).changed.render}")
    println(s"  Element: $debouncedSearch\n")

    // Example 2: Rate-limited input with throttle
    println("2. Rate-limited input events (at most 1 request per second):")
    val throttledStatus = input(
      id          := "status",
      `type`      := "text",
      hxPost      := "/api/status",
      hxTrigger   := HxTrigger.input.throttle(1.second),
      hxSwap      := HxSwap.OuterHTML,
      placeholder := "Type slowly..."
    )
    println(s"  Trigger: ${HxTrigger.input.throttle(1.second).render}")
    println(s"  Element: $throttledStatus\n")

    // Example 3: Multiple triggers with polling and user action
    println("3. Dual triggers: user click OR automatic polling:")
    val autoRefresh = div(
      id        := "data",
      hxGet     := "/api/data",
      hxTrigger := HxTrigger(
        HxTrigger.click,
        HxTrigger.every(30.seconds)
      ),
      hxSwap := HxSwap.InnerHTML.transition.settle(300.millis),
      "Data refreshes on click or every 30s"
    )
    println(s"  Triggers: ${HxTrigger(HxTrigger.click, HxTrigger.every(30.seconds)).render}")
    println(s"  Element: $autoRefresh\n")

    // Example 4: Request queuing strategy
    println("4. Queue strategy: keep only most recent request:")
    val queuedInput = input(
      `type`      := "text",
      placeholder := "Fast typing...",
      hxPost      := "/api/validate",
      hxTrigger   := HxTrigger.input.delay(300.millis).queue(HxTrigger.QueueStrategy.Last),
      hxTarget    := HxTarget.next("span"),
      hxSwap      := HxSwap.InnerHTML
    )
    println(s"  Trigger: ${HxTrigger.input.delay(300.millis).queue(HxTrigger.QueueStrategy.Last).render}")
    println(s"  Element: $queuedInput\n")

    // Example 5: Animation with timing modifiers
    println("5. CSS transition with settle delay:")
    val animatedSwap = button(
      hxPost    := "/api/action",
      hxTrigger := HxTrigger.click,
      hxSwap    := HxSwap.InnerHTML.transition.settle(400.millis),
      hxTarget  := HxTarget.This,
      "Click for animated response"
    )
    println(s"  Swap: ${HxSwap.InnerHTML.transition.settle(400.millis).render}")
    println(s"  Element: $animatedSwap\n")

    // Example 6: Event delegation with target modifier
    println("6. Event delegation: parent listens to child clicks:")
    val eventDelegation = ul(
      id        := "items",
      hxPost    := "/api/item-clicked",
      hxTrigger := HxTrigger.click.target("li"),
      hxSwap    := HxSwap.InnerHTML,
      li("Item 1"),
      li("Item 2"),
      li("Item 3")
    )
    println(s"""  Trigger: ${HxTrigger.click.target("li").render}""")
    println(s"  Element: $eventDelegation\n")

    // Example 7: JavaScript filtering
    println("7. JavaScript filter: only trigger if condition met:")
    val filteredTrigger = input(
      `type`      := "text",
      placeholder := "Min 3 characters...",
      hxPost      := "/api/search",
      hxTrigger   := HxTrigger.input.filter(Js("event.target.value.length > 2")),
      hxTarget    := HxTarget.next("div"),
      hxSwap      := HxSwap.InnerHTML
    )
    println(s"""  Trigger: ${HxTrigger.input.filter(Js("event.target.value.length > 2")).render}""")
    println(s"  Element: $filteredTrigger\n")

    // Example 8: Source control with from modifier
    println("8. Source control: button listens to input events:")
    val sourceControl = div(
      input(
        id          := "query-input",
        `type`      := "text",
        placeholder := "Type here..."
      ),
      button(
        id        := "search-btn",
        hxPost    := "/api/search",
        hxTrigger := HxTrigger.click.from("#query-input"),
        hxTarget  := HxTarget.next("div"),
        "Search"
      )
    )
    println(s"""  Button trigger: ${HxTrigger.click.from("#query-input").render}""")
    println(s"  Element: $sourceControl\n")

    // Example 9: Intersection observer with threshold
    println("9. Lazy loading with intersection observer:")
    val lazyLoad = img(
      src       := "/placeholder.jpg",
      hxGet     := "/api/lazy-image",
      hxTrigger := HxTrigger.intersect.threshold(0.5),
      hxSwap    := HxSwap.OuterHTML,
      alt       := "Lazy loaded image"
    )
    println(s"  Trigger: ${HxTrigger.intersect.threshold(0.5).render}")
    println(s"  Element: $lazyLoad\n")

    // Example 10: Complex modifier chain
    println("10. Complex modifier chain - multiple modifiers:")
    val complexChain = input(
      `type`      := "text",
      name        := "search",
      placeholder := "Advanced search",
      hxPost      := "/api/search",
      hxTrigger   := HxTrigger.input
        .delay(500.millis)
        .throttle(1.second)
        .changed
        .filter(Js("event.target.value.trim().length > 0")),
      hxParams := HxParams.only("search"),
      hxTarget := HxTarget.closest(".search-results"),
      hxSwap   := HxSwap.InnerHTML.transition.settle(250.millis)
    )
    println(
      s"""  Complex trigger: ${HxTrigger.input
          .delay(500.millis)
          .throttle(1.second)
          .changed
          .filter(Js("event.target.value.trim().length > 0"))
          .render}"""
    )
    println(s"  Swap: ${HxSwap.InnerHTML.transition.settle(250.millis).render}")
    println(s"  Element: $complexChain\n")

    // Example 11: Scroll positioning after swap
    println("11. Scroll to bottom after appending messages:")
    val messageList = div(
      id        := "messages",
      hxGet     := "/api/messages",
      hxTrigger := HxTrigger.every(2.seconds),
      hxSwap    := HxSwap.BeforeEnd.scroll(HxSwap.ScrollPosition.Bottom).show(HxSwap.ShowPosition.Bottom),
      "Messages will be appended and scrolled into view"
    )
    println(s"  Swap: ${HxSwap.BeforeEnd.scroll(HxSwap.ScrollPosition.Bottom).show(HxSwap.ShowPosition.Bottom).render}")
    println(s"  Element: $messageList\n")

    println("✓ Advanced pattern examples complete")
  }
}
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/AdvancedPatterns.scala))

Run this example with the following command:

```bash
sbt "zio-blocks-htmx-examples/runMain zioBlocksHtmx.AdvancedPatterns"
```

### Complete Example

A realistic e-commerce search and filtering interface combining multiple HTMX attributes: debounced search input, live category filtering, paginated results, out-of-band notifications, and dynamic UI updates. Demonstrates how types compose to create a type-safe, interactive UI. Here is the source code:

```scala title="zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/CompleteExample.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zioBlocksHtmx

import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration.DurationInt

/**
 * HTMX DSL — Complete Realistic Example
 *
 * A real-world e-commerce search and filtering interface combining multiple
 * HTMX attributes: debounced search input, live category filtering, paginated
 * results, out-of-band notifications, and dynamic UI updates. Demonstrates how
 * types compose to create a type-safe, interactive UI.
 *
 * Run with: sbt "zio-blocks-htmx-examples/runMain
 * zioBlocksHtmx.CompleteExample"
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    println("=== HTMX Complete Realistic Example ===\n")

    println("Building a type-safe e-commerce search interface...\n")

    // Part 1: Search input with debounce and parameter filtering
    val searchInput = input(
      `type`      := "text",
      name        := "query",
      id          := "search-query",
      placeholder := "Search products...",
      hxPost      := "/api/search",
      hxTrigger   := HxTrigger.input
        .delay(500.millis)
        .changed,
      hxParams := HxParams.only("query", "category", "page"),
      hxTarget := HxTarget.find(".results-container"),
      hxSwap   := HxSwap.InnerHTML.transition.settle(250.millis)
    )

    println("1. Search Input:")
    println(s"   Placeholder: Search products")
    println(s"   Trigger: input with 500ms delay + changed")
    println(s"   Params: query, category, page (others excluded)")
    println(s"   Swap: InnerHTML with transition and 250ms settle")
    println(s"   Rendered: $searchInput\n")

    // Part 2: Category filter with instant response
    val categoryFilter = select(
      name      := "category",
      id        := "category-filter",
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.change,
      hxTarget  := HxTarget.find(".results-container"),
      hxSwap    := HxSwap.InnerHTML.transition,
      option(value := "all", selected, "All Categories"),
      option(value := "electronics", "Electronics"),
      option(value := "books", "Books"),
      option(value := "clothing", "Clothing")
    )

    println("2. Category Filter:")
    println(s"   Trigger: change event (no delay)")
    println(s"   Target: .results-container")
    println(s"   Rendered: $categoryFilter\n")

    // Part 3: Results container with dynamic content
    val resultsContainer = div(
      id        := "results",
      `class`   := "results-container",
      hxGet     := "/api/search",
      hxTrigger := HxTrigger.load,
      hxTarget  := HxTarget.This,
      hxSwap    := HxSwap.InnerHTML,
      div("Loading results...")
    )

    println("3. Results Container:")
    println(s"   Trigger: load (fetches initial results)")
    println(s"   Swap: InnerHTML")
    println(s"   Rendered: $resultsContainer\n")

    // Part 4: Pagination buttons
    val prevButton = button(
      id        := "btn-prev",
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.click,
      hxParams  := HxParams.only("query", "category", "page"),
      hxTarget  := HxTarget.find(".results-container"),
      hxSwap    := HxSwap.InnerHTML.scroll(HxSwap.ScrollPosition.Top),
      "← Previous"
    )

    val nextButton = button(
      id        := "btn-next",
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.click,
      hxParams  := HxParams.only("query", "category", "page"),
      hxTarget  := HxTarget.find(".results-container"),
      hxSwap    := HxSwap.InnerHTML.scroll(HxSwap.ScrollPosition.Top),
      "Next →"
    )

    println("4. Pagination Buttons:")
    println(s"   Behavior: POST request, replace results, scroll to top")
    println(s"   Prev: $prevButton")
    println(s"   Next: $nextButton\n")

    // Part 5: Status badge with out-of-band updates
    val statusBadge = div(
      id        := "search-status",
      hxSwapOob := HxSwapOob.using(HxSwap.InnerHTML),
      "Ready"
    )

    println("5. Status Badge (Out-of-Band):")
    println(s"   Updates separately from main results")
    println(s"   Server response can include status updates")
    println(s"   Rendered: $statusBadge\n")

    // Part 6: Complete search form integrating all parts
    val wholeForm = div(
      id := "search-interface",
      h2("Product Search"),
      div(
        `class` := "search-controls",
        div(
          label(`for` := "search-query", "Search:"),
          searchInput
        ),
        div(
          label(`for` := "category-filter", "Category:"),
          categoryFilter
        )
      ),
      resultsContainer,
      div(
        `class` := "pagination-controls",
        prevButton,
        span(id := "page-info", "Page 1"),
        nextButton
      ),
      statusBadge
    )

    println("6. Complete Search Form:")
    println(s"   Integrates search input, category filter, results, pagination")
    println(s"   Form structure: $wholeForm\n")

    // Part 7: Advanced example - filtering based on price range
    val priceRangeInput = input(
      `type`    := "range",
      name      := "max-price",
      id        := "price-slider",
      min       := "0",
      max       := "1000",
      value     := "1000",
      hxPost    := "/api/search",
      hxTrigger := HxTrigger.change.throttle(500.millis),
      hxParams  := HxParams.only("query", "category", "max-price"),
      hxTarget  := HxTarget.find(".results-container"),
      hxSwap    := HxSwap.InnerHTML.transition
    )

    println("7. Advanced - Price Range Filter:")
    println(s"   Trigger: change with 500ms throttle")
    println(s"   Prevents excessive requests while dragging slider")
    println(s"   Rendered: $priceRangeInput\n")

    // Part 8: Bonus - polling for real-time updates
    val liveUpdatesDiv = div(
      id        := "live-updates",
      hxGet     := "/api/trending",
      hxTrigger := HxTrigger.every(10.seconds),
      hxSwap    := HxSwap.InnerHTML,
      "Trending products..."
    )

    println("8. Bonus - Live Updates Panel:")
    println(s"   Polls /api/trending every 10 seconds")
    println(s"   Keeps trending products fresh without user action")
    println(s"   Rendered: $liveUpdatesDiv\n")

    println("=== Type Safety in Action ===")
    println("All HTMX attributes are compile-time checked:")
    println("✓ HxTrigger validates event names and modifiers")
    println("✓ HxSwap ensures correct strategy and modifier combinations")
    println("✓ HxParams prevents typos in parameter names")
    println("✓ HxTarget validates selector syntax")
    println("✓ No raw strings = no HTMX syntax errors at runtime")
    println("\n✓ Complete realistic example built successfully")
  }
}
```

([source](https://github.com/zio/zio-blocks/blob/main/zio-blocks-htmx-examples/src/main/scala/zioBlocksHtmx/CompleteExample.scala))

Run this example with the following command:

```bash
sbt "zio-blocks-htmx-examples/runMain zioBlocksHtmx.CompleteExample"
```

**3. Or compile all examples at once:**

To compile all example sources without running them, use:

```bash
sbt "zio-blocks-htmx-examples/compile"
```
