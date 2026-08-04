# HxTarget

> `HxTarget` represents the `hx-target` attribute and related selector-based attributes, declaring where HTMX applies the swap. It supports DOM traversal patterns like "closest ancestor," "first descendant," "next sibling," and raw CSS selectors, eliminating stringly mistakes in selector construction.

`HxTarget` represents the `hx-target` attribute and related selector-based attributes, declaring where HTMX applies the swap. It supports DOM traversal patterns like "closest ancestor," "first descendant," "next sibling," and raw CSS selectors, eliminating stringly mistakes in selector construction.

Use `HxTarget.This` for the current element, or call traversal methods like `closest()`, `find()`, `next()`, and `previous()` for common patterns. For arbitrary CSS selectors, use `css()`. Here are the core patterns:

```scala
import zio.http.htmx._

// Current element
HxTarget.This

// Closest ancestor matching selector
HxTarget.closest("form")

// First descendant matching selector
HxTarget.find(".results")

// Next sibling
HxTarget.next

// Next sibling matching selector
HxTarget.next(".item")

// Raw CSS selector
HxTarget.css("#results")
```

## Common Targets

**`HxTarget.This`** targets the element itself (the `this` keyword in HTMX). Backtick-friendly and identifier-friendly aliases are available:

```scala
import zio.blocks.html._
import zio.http.htmx._

button(
  hxPost := "/api/action",
  hxTarget := HxTarget.This,
  "Update Me"
)
```

```scala
import zio.http.htmx._

HxTarget.`this`  // backtick-friendly
HxTarget.this_   // identifier-friendly (underscore avoids Scala keyword)
```

## DOM Traversal Patterns

**`closest(selector: String)`** traverses up the DOM tree to find the closest ancestor matching the selector:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Update the closest form ancestor
input(
  hxPost := "/validate",
  hxTarget := HxTarget.closest("form")
)

// Update the closest .card ancestor
button(
  hxGet := "/refresh",
  hxTarget := HxTarget.closest(".card")
)
```

**`find(selector: String)`** finds the first descendant matching the selector:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Update the first .results div inside this element
div(
  hxGet := "/search-results",
  hxTarget := HxTarget.find(".results")
)
```

**`next`** targets the next sibling element without filtering:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Update the next sibling
button(
  hxPost := "/action",
  hxTarget := HxTarget.next,
  "Go"
)
```

**`next(selector: String)`** targets the next sibling matching the selector:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Update the next .row sibling
div(
  hxGet := "/row-data",
  hxTarget := HxTarget.next(".row")
)
```

**`HxTarget.previous`** targets the previous sibling element:

```scala
import zio.blocks.html._
import zio.http.htmx._

button(
  hxPost := "/action",
  hxTarget := HxTarget.previous
)
```

**`previous(selector: String)`** targets the previous sibling matching the selector:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  hxGet := "/prev-data",
  hxTarget := HxTarget.previous(".section")
)
```

## CSS Selectors

**`css(selector: String)`** uses a raw CSS selector string. Validation ensures the selector is non-empty but otherwise accepts any string:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  hxGet := "/data",
  hxTarget := HxTarget.css("#results")
)

// Complex CSS selector
div(
  hxPost := "/search",
  hxTarget := HxTarget.css("div.container > .results:first-child")
)
```

**`css(selector: CssSelector)`** accepts a typed `CssSelector` from `zio.blocks.html`, providing type-safe selector construction:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  hxGet := "/update",
  hxTarget := HxTarget.css(selector".results")
)
```

## Rendering & Parsing

**`render: String`** produces the literal HTMX target selector string:

```scala
import zio.http.htmx._

HxTarget.This.render                  // "this"
HxTarget.closest("form").render       // "closest form"
HxTarget.find(".results").render      // "find .results"
HxTarget.next.render                  // "next"
HxTarget.next(".item").render         // "next .item"
HxTarget.previous(".row").render      // "previous .row"
HxTarget.css("#results").render       // "#results"
```

**`HxTarget.parse(value: String): Either[String, HxTarget]`** parses a rendered selector string back into a typed `HxTarget`:

```scala
import zio.http.htmx._

HxTarget.parse("this")                // Right(HxTarget.This)
HxTarget.parse("closest form")        // Right(HxTarget.closest("form"))
HxTarget.parse("find .results")       // Right(HxTarget.find(".results"))
HxTarget.parse("next")                // Right(HxTarget.next)
HxTarget.parse("next .item")          // Right(HxTarget.next(".item"))
HxTarget.parse("#results")            // Right(HxTarget.css("#results"))
```

Parse failures return a descriptive `Left`:

```scala
import zio.http.htmx._

HxTarget.parse("")                    // Left("HTMX target selector must be non-empty")
HxTarget.parse("   ")                 // Left("HTMX target selector must be non-empty")
```

## Common Patterns

Selecting the right target element is essential for HTMX interactions. Here are practical patterns for common scenarios:

### Update a Form Container

When a button inside a form fires a request, target the form to replace all its content:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  id := "search-form",
  input(placeholder := "Search..."),
  button(
    hxPost := "/search",
    hxTarget := HxTarget.closest("form"),
    "Search"
  )
)
```

### Update a Results Section

Target a specific div outside the trigger element:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  input(
    hxPost := "/search",
    hxTarget := HxTarget.find(".results"),
    placeholder := "Search..."
  ),
  div(id := "results", "Results here")
)
```

### Append to a List

Use `AfterBegin` or `BeforeEnd` with a target on the list container:

```scala
import zio.blocks.html._
import zio.http.htmx._

ul(
  id := "items",
  li(
    button(
      hxPost := "/add-item",
      hxTarget := HxTarget.closest("ul"),
      hxSwap := HxSwap.BeforeEnd,
      "Add Item"
    )
  ),
  li("Item 1")
)
```

### Target the Next Row in a Table

Each row has an "edit" button that updates the next row:

```scala
import zio.blocks.html._
import zio.http.htmx._

tr(
  td("Data 1"),
  td(button(
    hxGet := "/edit",
    hxTarget := HxTarget.next("tr"),
    "Edit"
  ))
)
```

## Integration with Other Attributes

Multiple HTMX attributes accept `HxTarget` values:

- `hxTarget` — Where to apply the swap
- `hxInclude` — Which elements to include in the request (uses similar selector patterns)
- `hxSync` — Which element to synchronize with

All accept the same `HxTarget` type:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

form(
  hxPost := "/submit",
  hxTarget := HxTarget.closest("form"),
  hxInclude := HxTarget.closest("form"),
  hxSync := HxSync(HxTarget.This, HxSyncStrategy.Queue),
  button("Submit")
)
```

The `ToHtmxValue[HxTarget]` instance handles rendering automatically, so `HxTarget` values work seamlessly with all attribute keys that accept selectors.
