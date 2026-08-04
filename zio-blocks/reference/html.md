# HTML

> `zio-blocks-html` is a **type-safe HTML templating library** providing immutable data structures and a fluent DSL for building HTML, CSS, and JavaScript. It offers compile-time safety, automatic XSS protection, and zero-dependency simplicity across Scala 2.13, 3.x, and both JVM and Scala.js platforms.

`zio-blocks-html` is a **type-safe HTML templating library** providing immutable data structures and a fluent DSL for building HTML, CSS, and JavaScript. It offers compile-time safety, automatic XSS protection, and zero-dependency simplicity across Scala 2.13, 3.x, and both JVM and Scala.js platforms.

Core types: `Dom` (HTML tree), `CssSelector` (CSS queries), `DomSelection` (DOM navigation), `Css` (stylesheets), `Js` (JavaScript expressions).

The module is structured around these core types:

```
import zio.blocks.html._
import zio.blocks.chunk.Chunk

// Dom variants
sealed trait Dom
case class Dom.Text(content: String) extends Dom
sealed trait Dom.Element extends Dom
case class Dom.Element.Generic(tag: String, attributes: Chunk[Dom.Attribute], children: Chunk[Dom]) extends Dom.Element
case class Dom.Element.Script(attributes: Chunk[Dom.Attribute], children: Chunk[Dom]) extends Dom.Element
case class Dom.Element.Style(attributes: Chunk[Dom.Attribute], children: Chunk[Dom]) extends Dom.Element

// Attribute variants
sealed trait Dom.Attribute
case class Dom.Attribute.KeyValue(name: String, value: Dom.AttributeValue) extends Dom.Attribute
case class Dom.Attribute.AppendValue(name: String, value: Dom.AttributeValue, separator: Dom.AttributeSeparator) extends Dom.Attribute
case class Dom.Attribute.BooleanAttribute(name: String, enabled: Boolean) extends Dom.Attribute

// CssSelector variants (additional types omitted for brevity)
sealed trait CssSelector
case class CssSelector.Element(tag: String) extends CssSelector
case class CssSelector.Class(name: String) extends CssSelector

// Css variants
sealed trait Css
case class Css.Rule(selector: CssSelector, declarations: Chunk[Css.Declaration]) extends Css
case class Css.Declaration(property: String, value: String) extends Css
```

## Motivation

Why use `zio-blocks-html`?

String concatenation for HTML is error-prone:
- No type safety — typos in tag names or attributes only surface at runtime
- Manual escaping is tedious and easy to forget
- Tightly couples HTML structure to data serialization

Template engines with runtime parsing add overhead and complexity:
- Parsing validation on every render
- Separate template syntax to learn
- Difficult to compose programmatically

`zio-blocks-html` provides:
- **Type-safe construction** via DSL functions (`div(id := "main", p("Hello"))`)
- **Automatic XSS protection** through position-aware typeclasses and HTML escaping
- **Scala 3 compile-time optimizations** for string interpolators (constant folding, macro validation)
- **Zero dependencies** — pure Scala, works with any HTTP framework
- **Cross-platform** — identical API on JVM and Scala.js
- **Structured querying** — CSS selectors for DOM navigation and testing

## Installation

Add to `build.sbt`:

```
libraryDependencies += "dev.zio" %% "zio-blocks-html" % "0.0.51"
```

For Scala.js projects, use `%%%`:

```
libraryDependencies += "dev.zio" %%% "zio-blocks-html" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x

:::note
The `html` module depends transitively on `zio-blocks-schema` and `zio-blocks-chunk`. The schema dependency enables `ToJs` auto-derivation via JSON encoding — if your type has a `Schema` instance, it automatically becomes usable in `js"..."` expressions.
:::

## Overview: How They Work Together

The module is organized around five core subsystems that compose together:

```
┌─────────────────────────────────────────────────────┐
│  DSL Functions (div, p, span, ...)                  │
│  + Attribute Builders (id :=, className +=)         │
│  └─> Produces: Dom.Element.Generic                  │
│                                                      │
├─────────────────────────────────────────────────────┤
│  String Interpolators (html"", css"", js"")          │
│  └─> Position-aware typeclasses                      │
│      └─> html"" → Dom.Element (attribute escaping)   │
│      └─> css"" → Css (CSS value types)               │
│      └─> js"" → Js (JavaScript with </script> guard) │
│                                                      │
├─────────────────────────────────────────────────────┤
│  Dom ADT (sealed trait Dom)                          │
│  ├─> Dom.Text (HTML-escaped text content)            │
│  ├─> Dom.Element (Generic, Script, Style)            │
│  ├─> Dom.Doctype (<!DOCTYPE html>)                   │
│  └─> Dom.Empty (renders to nothing)                  │
│                                                      │
├─────────────────────────────────────────────────────┤
│  Rendering                                           │
│  ├─> dom.render → minified HTML String               │
│  ├─> dom.render(indent=2) → pretty-printed String    │
│  └─> Used in HTTP responses, testing                 │
│                                                      │
├─────────────────────────────────────────────────────┤
│  CSS Selectors + DOM Querying                        │
│  ├─> CssSelector ADT (Element, Class, Id, etc.)      │
│  ├─> Fluent combinators (>, >>, +, ~, &, |)          │
│  ├─> DomSelection API (select, filter, extract)      │
│  └─> Used for testing, transformation, navigation    │
└─────────────────────────────────────────────────────┘
```

**Typical workflow:**

1. **Build** — use DSL (`div(className := "card", ...)`) or interpolators (`html"<div>$content</div>"`)
2. **Compose** — nest elements and use typeclasses to extend for custom types
3. **Query** — use CSS selectors (`page.select(div.hover).texts`) for testing or transformation
4. **Render** — call `Dom#render` or `Dom#render(indent)` for HTTP response or testing
5. **Style** — use `css"..."` and `Css.Rule` to define or embed stylesheets

## The Dom ADT

The `Dom` sealed trait is the core data model. Everything in the module works with or produces `Dom` nodes.

### The Four Node Types

A `Dom` tree is composed of four node types:

#### Dom.Text

A text node containing string content. Text is automatically HTML-escaped when rendered to prevent XSS injection. Escaping happens at render time (not construction time), so `Dom.Text` stores the raw string.

To create text nodes, use the DSL (strings are converted via `ToModifier[String]`) or explicitly. Call `Dom#render` to convert a text node to an HTML string:

```scala
import zio.blocks.html._

val text = Dom.Text("Hello, world!")
// text: Text = Text("Hello, world!")
text.render
// res0: String = "Hello, world!"
```

#### Dom.Element.Generic

A standard HTML element. The tag is the element name, attributes is a `Chunk` of key-value pairs and modifiers, and children is a `Chunk` of DOM nodes. Text content in children is HTML-escaped during rendering.

The DSL functions (`div`, `p`, `span`, etc.) construct these:

```scala
import zio.blocks.html._

val elem = div(id := "main", p("Content"))
```

#### Dom.Element.Script

A specialized script element with `tag = "script"`. Unlike `Generic`, Script renders its text children **without HTML escaping**, allowing inline JavaScript to be emitted as-is. This is the mechanism that enables safe `js"..."` interpolation — the interpolator escapes the JavaScript, but the Script element does not double-escape.

Use `script().inlineJs(js"...")` or `script().externalJs(url)`:

```scala
import zio.blocks.html._

val inlineScript = script().inlineJs(js"console.log('Hello');")
val externalScript = script().externalJs("/app.js")
```

#### Dom.Element.Style

A specialized style element with `tag = "style"`. Like Script, Style renders text children **without escaping**, allowing raw CSS to be emitted. Use `style().inlineCss(css"...")`:

```scala
import zio.blocks.html._

val inlineStyle = style().inlineCss(css"body { margin: 0; }")
```

#### Dom.Doctype

A DOCTYPE declaration node. Renders as `<!DOCTYPE value>`. The singleton `doctype` value renders as `<!DOCTYPE html>`:

```scala
import zio.blocks.html._

doctype.render
// res4: String = "<!DOCTYPE html>"
```

#### Dom.Empty

A no-op node that renders to empty string. Useful for conditional rendering (e.g., `if (condition) element else Dom.Empty`).

### Attributes and Values

Elements carry a `Chunk[Dom.Attribute]` where each attribute is one of:

- **`Dom.Attribute.KeyValue(name: String, value: Dom.AttributeValue)`** — A standard `name="value"` attribute. The value can be a string, multi-value, boolean, or JavaScript expression.

- **`Dom.Attribute.AppendValue(name: String, value: Dom.AttributeValue, separator: Dom.AttributeSeparator)`** — Declares a value to be concatenated with any existing value for the same attribute. Provides accumulation of multi-valued attributes like `class` and `rel`. The separator appears between values (Space, Comma, Semicolon, or Custom).

- **`Dom.Attribute.BooleanAttribute(name: String, enabled: Boolean)`** — A standalone attribute (like `disabled`, `checked`, `required`) that renders as just the name when enabled, or is omitted when disabled.

:::tip
The DSL provides `id := value` (`:=`) for single-valued attributes and `className += "class"` (`+=`) for appending to multi-valued attributes. Both are easier to use than constructing `Dom.Attribute` directly.
:::

### Tree Traversal Operations

`Dom` provides four pure tree transformation methods:

**`Dom#collect(pf: PartialFunction[Dom, Dom]): List[Dom]`** — Applies a partial function to every node in the tree (depth-first) and collects the results. Useful for extracting or transforming specific nodes:

```scala
import zio.blocks.html._

val tree = div(p("A"), span("B"), p("C"))
val paragraphs = tree.collect { case el: Dom.Element if el.tag == "p" => el }
```

**`Dom#filter(predicate: Dom => Boolean): Dom`** — Removes any node for which the predicate returns false. Non-matching nodes are replaced with `Dom.Empty`, and their children are lost. Matching elements have their children recursively filtered:

```scala
import zio.blocks.html._

val tree = div(p("A"), span("Keep"), p("B"), span("Also keep"))
val filtered = tree.filter {
  case el: Dom.Element => el.tag == "div" || el.tag == "span"
  case _ => true
}
```

**`Dom#find(predicate: Dom => Boolean): Option[Dom]`** — Returns the first node (depth-first) matching the predicate, or `None`:

```scala
import zio.blocks.html._

val tree = div(p("First"), span("Second"), p("Third"))
tree.find { case el: Dom.Element => el.tag == "p"; case _ => false }
```

**`Dom#transform(f: Dom => Dom): Dom`** — Applies a transformation function to every node in pre-order. Each node receives the transformation first, and if it is an Element, its children are recursed on the transformed node, so a transformation that changes the child list affects what gets recursed into:

```scala
import zio.blocks.html._

val tree = div(h3("Old"), p("Content"))
val upgraded = tree.transform {
  case el: Dom.Element.Generic if el.tag == "h3" => el.copy(tag = "h2")
  case other => other
}
```

## The HTML DSL

The DSL provides functions for all HTML5 elements, allowing fluent construction with a modifier pattern.

### Element Functions

All standard HTML elements are available as lowercase functions (or backtick-quoted if they shadow Scala keywords):

```scala
import zio.blocks.html._

// Basic elements
val paragraph = p("Hello, world!")
val heading = h1("Welcome")
val container = div("Content here")

// Void elements (auto self-closing)
val image = img(src := "photo.jpg", alt := "A photo")
val lineBreak = br
val horizontalRule = hr

// Keyword-shadowed elements (use backticks)
val objElement = `object`("data.bin")
```

### Setting Attributes

Attributes are set using the `:=` operator on `AttributeKey` builders:

```scala
import zio.blocks.html._

val link = a(
  href := "https://example.com",
  target := "_blank",
  titleAttr := "Visit Example",
  "Visit Example"
)
```

### Multi-Valued Attributes: Override vs Accumulate

For attributes that can have multiple values (`class`, `rel`, `accept`), you can override or accumulate:

- **`:=` (override)** — Replaces any previous value. Last assignment wins:

```scala
import zio.blocks.html._

val div1 = div(className := "a", className := "b")
```

- **`+=` (append)** — Concatenates with the previous value using a separator (space for `class`):

```scala
import zio.blocks.html._

val div2 = div(className += "card", className += "active")
```

- **Mixed** — Set a base value, then append:

```scala
import zio.blocks.html._

val div3 = div(className := "base").when(true)(className += "extra")
```

### Boolean Attributes

Use `BooleanAttribute.:=` to conditionally include attributes like `disabled`, `required`, or `checked`:

```scala
import zio.blocks.html._

val isDisabled = true
val submitBtn = button(disabled := isDisabled, "Submit")
```

### Data and ARIA Attributes

Use `dataAttr(name)` and `aria(name)` builders for HTML5 data attributes and ARIA accessibility attributes:

```scala
import zio.blocks.html._

val userDiv = div(
  dataAttr("user-id") := "42",
  dataAttr("action") := "edit",
  "User Card"
)

val closeBtn = button(
  aria("label") := "Close",
  aria("expanded") := "false",
  "×"
)
```

For custom attributes not provided by the DSL, use the generic `attr(name)` builder:

```scala
import zio.blocks.html._

val alpineDiv = div(attr("x-data") := "{open: false}", "Alpine.js component")
val htmxDiv = div(attr("hx-get") := "/api/data", "HTMX target")
```

### Programmatic Multi-Valued Attributes

For programmatic construction of multi-valued attributes (when the DSL `+=` operator is not available), use `Dom.multiAttr(name)` or `Dom.multiAttr(name, separator)`:

```scala
import zio.blocks.html._

// Create a custom multi-valued attribute builder (alternative to DSL's className)
val classBuilder = Dom.multiAttr("class")
val div1 = div(classBuilder := "base", classBuilder += "active")

// Create a multi-valued attribute with explicit separator
val rel = Dom.multiAttr("rel", Dom.AttributeSeparator.Space)
val link = a(rel := "prev", rel += "first", href := "/previous")
```

The `MultiAttributeKey` class handles accumulation of values with configurable separators (Space, Comma, Semicolon, or Custom).

For constructing multi-valued attributes directly from collections (without a builder chain), use the `Iterable[String]` overload:

```scala
import zio.blocks.html._

// Directly create a multi-valued attribute from a collection
val customClasses = Dom.multiAttr("class", List("card", "active", "large"))
val div1 = div(customClasses)
```

This approach is useful when programmatically building multi-valued attributes outside the DSL's builder pattern.

### Children

Children can be strings, elements, or collections. The DSL uses the `ToModifier` typeclass to convert values into DOM nodes:

```scala
import zio.blocks.html._

// String children are converted to Dom.Text
val simple = p("Plain text")

// Element children are nested
val nested = div(p("First"), p("Second"))

// Lists of children — elements append directly, no wrapper
val items = List("Apple", "Banana", "Cherry")
val listEl = ul(items.map(item => li(item)))
```

### Conditional Rendering

Use `when(condition)` to apply modifiers conditionally:

```scala
import zio.blocks.html._

val isHighlighted = true
val box = div(
  className := "box"
).when(isHighlighted)(
  className += "highlighted",
  titleAttr := "This is highlighted"
)
```

Use `whenSome(option)` to apply modifiers based on an `Option`:

```scala
import zio.blocks.html._

val maybeTitle: Option[String] = Some("Important")
val card = div(
  className := "card",
  p("Content")
).whenSome(maybeTitle)(t => Seq(
  titleAttr := t,
  className += "has-title"
))
```

### Void Elements

Void elements (self-closing tags) automatically render with the correct syntax:

```scala
import zio.blocks.html._

val voidElements = div(
  br,
  hr,
  img(src := "photo.jpg", alt := "A photo"),
  input(`type` := "text", placeholder := "Enter text")
)
```

## String Interpolators

The module provides four string interpolators: `html""`, `css""`, `js""`, and `selector""`. All offer compile-time safety (on Scala 3) and automatic escaping appropriate to their context.

### `html""` Interpolator

The `html""` interpolator is **position-aware**: it detects whether each interpolated argument is in an attribute-value position or content position, then summons the appropriate typeclass.

The `html""` interpolator determines position based on the preceding string:

**Position detection:**
- **Attribute position**: if the preceding string part ends with `=`, `='`, or `="`, the next argument is treated as an attribute value and uses `ToAttrValue[A]`
- **Content position**: otherwise, the argument is treated as element content and uses `ToElements[A]`

Position-aware interpolation enables safe attribute values and content:

```scala
import zio.blocks.html._

val name = "Alice"
val age = 30

// name is in content position → ToElements[String]
// age is in attribute position → ToAttrValue[Int]
val elem = html"""<div id="user-$age" class="profile">User: $name</div>"""
```

**Single-root requirement**: The `html""` interpolator requires a **single root element**. Multiple top-level nodes cause an exception at runtime (Scala 2) or compile error for static templates (Scala 3):

```scala
import zio.blocks.html._

// This compiles (single root)
val page = html"<div><p>A</p><p>B</p></div>"
```

**XSS Protection:**

Content interpolated into `html""` is stored as `Dom.Text` nodes, which are HTML-escaped during `Dom#render` to prevent XSS:

```scala
import zio.blocks.html._

val userInput = "<script>alert('XSS')</script>"
val safe = html"<p>$userInput</p>"
```

:::danger
The `html""` interpolator requires a **single root element**. On Scala 3, pure-static templates with multiple roots fail at compile time. On Scala 2 or for dynamic templates, the error occurs at evaluation time. Always wrap multiple elements in a container (e.g., `<div>`).
:::

### `css""` Interpolator

The `css""` interpolator returns a `Css` value with `ToCss` typeclass dispatch for interpolated parts:

```scala
import zio.blocks.html._

val color = "blue"
val size = 16

val styles = css"color: $color; font-size: ${size}px;"
```

Use `CssLength` and `CssColor` types for type-safe CSS values:

```scala
import zio.blocks.html._

val width = css"width: ${CssLength(300.0, "px")};"
val background = css"background: ${CssColor.Hex.unsafe("ff0000")};"
```

:::note
`CssColor.Hex` returns `Option[CssColor]` because hex validation may fail. Use `CssColor.Hex.apply()` (or just `CssColor.Hex(...)`) for safe validation, or `CssColor.Hex.unsafe()` for known-valid hex strings.
:::

### `js""` Interpolator

The `js""` interpolator returns a `Js` value. Strings are automatically quoted and escaped; numerics and booleans are rendered unquoted:

```scala
import zio.blocks.html._

val message = "Hello, world!"
val count = 42

val code = js"console.log($message); alert($count);"
```

:::warning
Never interpolate untrusted user input directly into `js""`. The interpolator escapes string values, but the `Js` value is rendered without additional escaping in script elements. Always use `ToJs[String]` (which automatically quotes and escapes) for any untrusted input.
:::

The interpolator protects against `</script>` injection by escaping `<` and `>` as Unicode escapes:

```scala
import zio.blocks.html._

val userInput = "if (x < y) alert('<script>');"
val code = js"let check = $userInput"

println(code.value)
// let check = "if (x < y) alert('<script>');"
// (with < and > characters escaped as < and > in actual output)
```

### `selector""` Interpolator

The `selector""` interpolator returns a `CssSelector`:

```scala
import zio.blocks.html._

val className = "active"
val selector = selector".$className"
```

## CSS Selectors and the DSL

The `CssSelector` ADT provides a fluent DSL for building CSS selectors with combinator methods.

### Basic Selectors

Create selectors for elements, classes, IDs, or universal matches:

```scala
import zio.blocks.html._

val divSel = CssSelector.Element("div")
val classSel = CssSelector.Class("container")
val idSel = CssSelector.Id("header")
val universal = CssSelector.Universal
```

### Combinators

All HTML elements (`div`, `span`, `p`, etc.) implement `CssSelectable`, so you can use them directly as selectors:

```scala
import zio.blocks.html._

// Element selectors via DSL elements
val divSel = div.selector
val childSel = div > span              // div > span
val descendantSel = div >> span        // div span (descendant)
val adjacentSel = div + span           // div + span
val siblingSel = div ~ span           // div ~ span
val andSel = div & CssSelector.Class("active")  // div.active
val orSel = div | span                 // div, span
```

### Pseudo-Classes and Pseudo-Elements

Pseudo-classes match elements by their state, and pseudo-elements create dynamic content:

```scala
import zio.blocks.html._

val hoverSel = a.hover              // a:hover
val firstChild = li.firstChild      // li:first-child
val nthChild = tr.nthChild(2)       // tr:nth-child(2)
val before = div.before             // div::before
val after = span.after              // span::after
```

### Attribute Selectors

Select elements by their attribute values using built-in matchers:

```scala
import zio.blocks.html._

val input = CssSelector.Element("input")

val hasType = input.withAttribute("type")              // input[type]
val exactType = input.withAttribute("type", "text")    // input[type="text"]
val containsClass = input.withAttributeContaining("class", "btn")  // input[class*="btn"]
val startsWithHref = a.withAttributeStarting("href", "https")      // a[href^="https"]
val endsWithPng = img.withAttributeEnding("src", ".png")           // img[src$=".png"]
```

## DOM Querying with DomSelection

The `DomSelection` API lets you query and navigate DOM trees using CSS selectors. It is useful for testing, transforming templates, and extracting information.

### Selecting Elements

Call `Dom#select(selector)` to query the tree:

```scala
import zio.blocks.html._

val page = div(
  header(nav(a(href := "/", "Home"), a(href := "/about", "About"))),
  main(p(className += "intro", "Hello"), p("World")),
  footer(p("© 2026"))
)

// Query by tag
val paragraphs = page.select(CssSelector.Element("p"))
paragraphs.length

// Query by class
val intros = page.select(CssSelector.Class("intro"))
intros.texts
```

### Navigation

The `DomSelection` API returned by `Dom#select` provides navigation methods like `.children` and `.first` to traverse the selection results:

```scala
import zio.blocks.html._

val page = div(
  nav(a(href := "/", "Home"), a(href := "/about", "About")),
  main(p("Content"))
)

// Direct children
val navLinks = page.select(CssSelector.Element("nav")).children
```

### Extraction

The `DomSelection` API provides methods like `.attrs` and `.texts` to extract attribute values and text content from selected elements:

```scala
import zio.blocks.html._

val page = div(
  a(href := "/home", "Home"),
  a(href := "/about", "About"),
  a(href := "/contact", "Contact")
)

val hrefs = page.select(CssSelector.Element("a")).attrs("href")
```

### Filtering

The `DomSelection` API provides `.filter` and `.withClass` methods to filter selections by predicate or attribute:

```scala
import zio.blocks.html._

val page = div(
  p(className += "visible", "A"),
  p(className += "hidden", "B"),
  p("C")
)

// Filter by predicate
val visible = page.select(CssSelector.Element("p")).filter { 
  case el: Dom.Element => el.attributes.exists {
    case attr: Dom.Attribute.KeyValue if attr.name == "class" => true
    case _ => false
  }
  case _ => false
}

// Filter by class
val withClass = page.select(CssSelector.Element("p")).withClass("visible")
```

### Modifying Selections

:::warning
`DomSelection` returns new copies with modifications; the original DOM tree remains unchanged. The `DomSelection` API provides functional transformations. To modify the original DOM tree, use `Dom#transform` with a tree-rewriting function, or rebuild the tree from scratch using the DSL.
:::

The `DomSelection` API provides methods like `.modifyAll`, `.replaceAll`, and `.removeAll` to transform or replace selected nodes:

```scala
import zio.blocks.html._

val page = div(
  p("Old 1"),
  p("Old 2"),
  span("Keep")
)

// Transform all Element nodes in the selection; non-Element nodes pass through unchanged
val modifiedSelection = page.select(CssSelector.Element("p")).modifyAll {
  case el: Dom.Element.Generic if el.tag == "p" => el.copy(tag = "div")
  case other => other
}

// Replace all selected nodes (returns DomSelection of replacement nodes)
val replacedSelection = page.select(CssSelector.Element("p")).replaceAll(p("New"))

// Remove all selected nodes (returns empty DomSelection)
val removedSelection = page.select(CssSelector.Element("p")).removeAll
```

:::note
Pseudo-class selectors (`:hover`, `:focus`, etc.) match elements structurally by their underlying element selector only — they cannot detect browser interaction state in a static DOM tree. `div.hover` matches the same elements as `CssSelector.Element("div")`.

Adjacent sibling (`+`) and general sibling (`~`) selectors are supported in CSS output but not in DOM querying — `DomSelection.select` with these combinators returns empty results.
:::

## The CSS ADT

The `Css` ADT represents structured stylesheets as a typed data structure, separate from strings.

All `Css` subtypes support `.render()` for minified output and `.render(indent: Int)` for indented pretty-printing:
- `Css.Rule` — single CSS rule with selector and declarations
- `Css.Sheet` — collection of rules
- `Css.Raw` — raw CSS string
- `Css.Comment` — CSS comment

### Declarations and Rules

A `Css.Declaration` is a property-value pair:

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

val marginDecl = Css.Declaration("margin", "10px")
val colorDecl = Css.Declaration("color", "blue")

val rule = Css.Rule(
  CssSelector.Element("p"),
  Chunk(marginDecl, colorDecl)
)

rule.render
```

### Stylesheets

A `Css.Sheet` is a collection of rules:

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

val bodyRule = Css.Rule(
  CssSelector.Element("body"),
  Chunk(
    Css.Declaration("margin", "0"),
    Css.Declaration("font-family", "sans-serif")
  )
)

val stylesheet = Css.Sheet(Chunk(bodyRule))

stylesheet.render(indent = 2)
```

### Embedding Stylesheets

Embed stylesheets in HTML via the `style()` element:

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

val page = html(
  head(
    style().inlineCss(
      Css.Sheet(Chunk(
        Css.Rule(
          CssSelector.Element("body"),
          Chunk(Css.Declaration("background", "#f0f0f0"))
        )
      ))
    )
  ),
  body(p("Styled"))
)

page.render
```

### Raw CSS

For CSS features not expressible via the ADT (media queries, keyframes, at-rules), use `Css.Raw`:

```scala
import zio.blocks.html._

val mediaQuery = Css.Raw("""
  |@media (max-width: 600px) {
  |  body { font-size: 14px; }
  |}
""".stripMargin)

println(mediaQuery.render)
```

:::tip
Prefer `Css.Rule` and `Css.Sheet` over `Css.Raw` when possible — structured CSS enables future optimization and prevents CSS injection. Use `Css.Raw` only for trusted, hardcoded CSS.
:::

### CSS Comments

Add comments to stylesheets using `Css.Comment`:

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

val stylesheet = Css.Sheet(Chunk(
  Css.Comment("Mobile-first responsive design"),
  Css.Rule(
    CssSelector.Element("body"),
    Chunk(Css.Declaration("font-size", "16px"))
  ),
  Css.Comment("Tablet and desktop breakpoints"),
  Css.Raw("@media (min-width: 768px) { body { font-size: 18px; } }")
))

stylesheet.render(indent = 2)
```

## Rendering

All `Dom` and `Css` values support multiple rendering modes.

### Minified Rendering

`Dom#render` produces compact HTML with no extra whitespace. Use `Dom#renderMinified` as an explicit alias for the same operation:

```scala
import zio.blocks.html._

val page = div(h1("Title"), p("Content"))
page.render
```

### Pretty-Printed Rendering

`render(indent: Int)` produces indented, readable output:

```scala
import zio.blocks.html._

val page = div(h1("Title"), p("Content"))
page.render(indent = 2)
```

### Performance Notes

- `Dom#render` uses a pre-allocated `StringBuilder` and while-loop rendering — zero allocations for iteration
- Indentation strings get cached in an array of pre-built space strings (up to 128 characters) to prevent repeated string allocation
- Void elements automatically self-close with no children
- Script and Style elements render their children without escaping (with `</` → `<\/` protection for scripts)

## Security: XSS Protection

The module provides multiple layers of automatic XSS protection:

### HTML Text Escaping

All `Dom.Text` nodes are HTML-escaped during rendering:
- `&` → `&amp;`
- `<` → `&lt;`
- `>` → `&gt;`
- `"` → `&quot;`
- `'` → `&#x27;`

Untrusted content is always escaped to prevent XSS:

```scala
import zio.blocks.html._

val userInput = "<script>alert('XSS')</script>"
val safe = div(p(userInput))

safe.render
```

### JavaScript String Escaping

The `ToJs[String]` typeclass escapes strings to prevent breaking out of script contexts:
- `<` → backslash-u-0-0-3-c (the six-character Unicode escape sequence `<`)
- `>` → backslash-u-0-0-3-e (the six-character Unicode escape sequence `>`)
- `&` → backslash-u-0-0-2-6 (the six-character Unicode escape sequence `&`)
- `"` → `\"`, `'` → `\'`, `\` → `\\`
- Newlines, carriage returns, and Unicode line/paragraph separators are escaped

This protects against `</script>` injection:

```scala
import zio.blocks.html._

val userInput = "</script><script>alert('XSS');</script>"
val code = js"let payload = $userInput"

println(code.value)
// let payload = "</script><script>alert('XSS');</script>"
// (with < and > characters escaped as Unicode in actual output)
```

### URL Sanitization

Attributes named `href`, `src`, `action`, or `formaction` are checked for dangerous schemes at render time:
- `javascript:`, `vbscript:`, `data:text/html` → prefixed with `unsafe:`

Dangerous URLs are automatically sanitized in HTML output:

```scala
import zio.blocks.html._

val dangerous = a(href := "javascript:alert('XSS')", "Click me")
```

### No Raw HTML Escape Hatch

The module intentionally provides no `Dom.Raw` type for embedding arbitrary HTML. This prevents XSS by ensuring all dynamic content is either constructed via the DSL or interpolated through context-aware interpolators.

## Common Patterns

The module supports several architectural patterns for code organization and reuse:

### Building Reusable Components

Define functions that return `Dom.Element` to create reusable components:

```scala
import zio.blocks.html._

def card(title: String, content: String): Dom.Element =
  div(
    className := "card",
    h2(title),
    p(content)
  )

val page = div(
  card("Card 1", "Content A"),
  card("Card 2", "Content B")
)
```

### Conditional Rendering

Use `when` and `whenSome` for conditional modifiers:

```scala
import zio.blocks.html._

def userCard(name: String, isAdmin: Boolean): Dom.Element =
  div(
    className := "user-card"
  ).when(isAdmin)(
    className += "admin",
    span(className := "badge", "Admin")
  )
```

### Rendering Collections

Map over collections to create child elements:

```scala
import zio.blocks.html._

def userList(users: List[String]): Dom.Element =
  ul(users.map(user => li(user)))

val page = userList(List("Alice", "Bob", "Charlie"))
```

### Template Composition

Combine interpolators and DSL for flexibility:

```scala
import zio.blocks.html._

val title = "My Page"
val content = "Welcome to my site"

val page = html"""
<html>
  <head><title>$title</title></head>
  <body>
    ${div(p(content))}
  </body>
</html>
"""
```

### Querying for Tests

Use `DomSelection` to write structural assertions:

```scala
import zio.blocks.html._

val page = div(
  ul(
    li(a(href := "/home", "Home")),
    li(a(href := "/about", "About"))
  )
)

// Test: check number of links
val links = page.select(CssSelector.Element("a"))
assert(links.length == 2)

// Test: check specific href
val aboutLink = page.select(a.withAttribute("href", "/about"))
assert(aboutLink.texts.contains("About"))
```

## Complete Example: A Dashboard Page

Here is a complete, self-contained example building a full dashboard page with navigation, metadata, styling, and content sections:

```scala
import zio.blocks.html._
import zio.blocks.chunk.Chunk

val userName = "Alice"
val items = List("Dashboard", "Settings", "Logout")

val pieces: Chunk[Dom] = Chunk(
  doctype,
  html(
    head(
      meta(charset := "utf-8"),
      meta(name := "viewport", content := "width=device-width, initial-scale=1.0"),
      title("My App"),
      link(rel := "stylesheet", href := "/style.css"),
      style().inlineCss(
        Css.Sheet(Chunk(
          Css.Rule(CssSelector.Element("body"), Chunk(
            Css.Declaration("margin", "0"),
            Css.Declaration("font-family", "sans-serif")
          ))
        ))
      )
    ),
    body(
      header(
        nav(items.map(item => a(href := "#", item)))
      ),
      main(
        h1(s"Welcome, $userName!"),
        p("This is your dashboard.")
      ),
      footer(
        p("© 2026 My App")
      ),
      script().inlineJs(js"console.log('Page loaded');")
    )
  )
)

pieces.map(_.render(indent = 2)).mkString("\n")
```
