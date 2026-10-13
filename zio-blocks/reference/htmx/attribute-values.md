# Attribute Values and Infrastructure

> This section documents supporting attribute value types and infrastructure for the HTMX DSL. These types handle specialized data encoding, configuration, and type-class infrastructure that enables extensibility.

## Attribute Value Types

These types enable specialized data encoding and configuration:

### HxVals — Custom Values

`HxVals` represents the `hx-vals` attribute, allowing you to send custom data alongside form parameters. It accepts both schema-backed values (automatically JSON-encoded) and raw JSON:

```scala
import zio.http.htmx._
import zio.blocks.schema._
import zio.blocks.schema.json.Json

// From a schema-backed value
case class Extra(userId: Int, role: String)
HxVals.from(Extra(123, "admin"))

// From raw JSON
HxVals.json(Json.Object(Chunk("userId" -> Json.Number(123))))
```

Use `HxVals.from[T](value)` with an implicit `Schema[T]` to automatically encode any data type to JSON. First, set up a schema for your data type:

```scala
import zio.blocks.html._
import zio.http.htmx._
import zio.blocks.schema._

case class RequestContext(userId: Int, sessionId: String)
object RequestContext {
  implicit val schema: Schema[RequestContext] = Schema.derived
}
```

Then use it in your form:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/api/action",
  hxVals := HxVals.from(RequestContext(42, "abc123")),
  button("Submit")
)
```

Or use `HxVals.json(json)` when you already have JSON. Set up the imports first:

```scala
import zio.blocks.schema.json.Json
import zio.blocks.chunk.Chunk
```

Then construct the JSON:

```scala
import zio.blocks.html._
import zio.http.htmx._
import zio.blocks.schema.json.Json
import zio.blocks.chunk.Chunk

form(
  hxPost := "/api/action",
  hxVals := HxVals.json(Json.Object(Chunk("priority" -> Json.String("high")))),
  button("Submit")
)
```

### HxHeadersValue — Custom Headers

`HxHeadersValue` represents the `hx-headers` attribute, sending custom HTTP headers with HTMX requests. Like `HxVals`, it accepts schema-backed values or raw JSON:

```scala
import zio.http.htmx._
import zio.blocks.schema.json.Json
import zio.blocks.chunk.Chunk

// From raw JSON object
val example1 = HxHeadersValue.json(Json.Object(Chunk(
  "X-Custom-Header" -> Json.String("value"),
  "X-Request-ID" -> Json.String("12345")
)))

// From a schema-backed value
import zio.blocks.schema._
case class Headers(traceId: String)
object Headers {
  implicit val schema: Schema[Headers] = Schema.derived
}
val example2 = HxHeadersValue.from(Headers("xyz789"))
```

Add custom headers to all requests within an element:

```scala
import zio.blocks.html._
import zio.http.htmx._
import zio.blocks.schema.json.Json
import zio.blocks.chunk.Chunk

div(
  hxHeaders := HxHeadersValue.json(Json.Object(Chunk(
    "X-API-Key" -> Json.String("secret123"),
    "X-Client-Version" -> Json.String("2.0")
  ))),
  button(hxPost := "/action", "Request (with custom headers)")
)
```

### HxSwapOob — Out-of-Bounds Swaps

`HxSwapOob` represents the `hx-swap-oob` attribute, enabling out-of-bounds swaps that update content outside the primary request target. This allows a single response to update multiple DOM regions simultaneously.

Use `HxSwapOob(true)` or `HxSwapOob(false)` for boolean flags:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  id := "status-badge",
  hxSwapOob := HxSwapOob(true),
  "Ready"
)
```

Or use `HxSwapOob.using(swap)` to specify a swap strategy:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  id := "notification",
  hxSwapOob := HxSwapOob.using(HxSwap.BeforeEnd),
  span("New notification")
)
```

Combine with a target selector:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  hxSwapOob := HxSwapOob.using(
    HxSwap.InnerHTML,
    HxTarget.css("#status")
  ),
  "Updated Status"
)
```

### HxExtensions — HTMX Extensions

`HxExtensions` represents the `hx-ext` attribute, enabling HTMX extensions. Pass extension names as comma-separated values:

```scala
import zio.http.htmx._

// Enable multiple extensions
HxExtensions("json-enc", "class-tools")
```

Use in elements to opt in to extensions:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/api/submit",
  hxExt := HxExtensions("json-enc", "debug"),
  button("Submit")
)
```

The `hxExt` attribute key provides a convenience builder to construct extensions:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxExt("json-enc", "class-tools"),
  button("Submit")
)
```

### HxAttributeNames — Attribute Disinheritance

`HxAttributeNames` represents the `hx-disinherit` attribute, preventing child elements from inheriting specific HTMX attributes from their ancestors. Pass attribute names as space-separated values:

```scala
import zio.http.htmx._

// Prevent inheriting these attributes
HxAttributeNames("hx-trigger", "hx-swap")
```

Use to override inherited attributes:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  hxPost := "/parent-action",
  hxTrigger := HxTrigger.click,
  button(
    hxDisinherit := HxAttributeNames("hx-post", "hx-trigger"),
    hxPost := "/child-action",
    hxTrigger := HxTrigger.submit,
    "Child (overrides parent)"
  )
)
```

The `hxDisinherit` attribute key provides a convenience builder:

```scala
import zio.blocks.html._
import zio.http.htmx._

div(
  hxPost := "/parent-action",
  button(
    hxDisinherit("hx-post", "hx-trigger"),
    hxPost := "/child-action",
    "Child"
  )
)
```

## Infrastructure Types

These types provide the internal machinery for the HTMX DSL:

### HtmxAttrKey — Typed Attribute Keys

`HtmxAttrKey[-A]` is the typed attribute key binding an attribute name to its expected value type. Unlike plain HTML attributes, HTMX attributes carry compile-time type information, preventing type mismatches.

The `HtmxAttributes` mixin provides predefined keys like `hxPost`, `hxSwap`, `hxTarget`, etc.; use these rather than constructing `HtmxAttrKey` directly. Each key enforces the correct value type:

```scala
import zio.blocks.html._
import zio.http.htmx._

// hxSwap expects HxSwap; this compiles
div(hxSwap := HxSwap.InnerHTML)

// hxTarget expects HxTarget; this compiles
div(hxTarget := HxTarget.This)

// hxPost expects UrlLike (String | Path | URL); this compiles
div(hxPost := "/api/endpoint")
```

### ToHtmxValue — Type Class for Rendering

`ToHtmxValue[-A]` is the type class that renders HTMX domain values to attribute strings. It's automatically derived for types like `HxSwap`, `HxTarget`, `HxTrigger`, and basic primitives.

The module provides instances for common types out of the box:

- **Primitives:** `String`, `Boolean`, `Int`, `Long`, `Double` render to their string representations
- **Selectors:** `CssSelector` renders via its `render` method
- **URLs:** `Path` and `URL` render to encoded strings
- **HTMX domain types:** `HxSwap`, `HxTarget`, `HxTrigger`, `HxParams`, etc. render via their `render` methods
- **Schema-backed types:** `Schema[A]` instances enable `HxVals.from()` to encode any data to JSON

#### Extending with Custom Types

Define your own `ToHtmxValue` instance to make custom domain types work in the HTMX DSL:

```scala
import zio.http.htmx._

sealed trait Priority
object Priority {
  case object Low extends Priority
  case object Medium extends Priority
  case object High extends Priority
  
  implicit val toHtmxValue: ToHtmxValue[Priority] = new ToHtmxValue[Priority] {
    def toHtmxValue(value: Priority): String = value match {
      case Low    => "low"
      case Medium => "medium"
      case High   => "high"
    }
  }
}
```

Use `Priority` values directly in HTMX attributes through the `ToHtmxValue` type class:

```scala
import zio.blocks.html._
import zio.http.htmx._

button(
  hxPost := "/api/task",
  hxOn.click := Js(s"console.log('Priority: ${Priority.High.toString()}')"),
  "Create High Priority Task"
)
```

#### Implicit Resolution

When you assign a value to an HTMX attribute, the type class is summoned implicitly:

```scala
import zio.blocks.html._
import zio.http.htmx._

// Implicit ToHtmxValue[HxSwap] is summoned
div(hxSwap := HxSwap.InnerHTML)

// Implicit ToHtmxValue[String] is summoned
div(hxPost := "/endpoint")
```

If you get an error like "No `ToHtmxValue` instance found for type X," implement the type class for your custom type as shown above.

## Integration

These types work together to provide a complete, extensible HTMX DSL. For example, a form might use multiple attribute value types:

```scala
import zio.blocks.html._
import zio.http.htmx._
import zio.blocks.schema.json.Json
import zio.blocks.chunk.Chunk
import scala.concurrent.duration._

form(
  hxPost := "/api/submit",
  hxTrigger := HxTrigger.submit,
  hxTarget := HxTarget.closest("form"),
  hxSwap := HxSwap.InnerHTML.settle(250.millis),
  hxParams := HxParams.only("name", "email"),
  hxEncoding := HxEncoding.Multipart,
  hxHeaders := HxHeadersValue.json(Json.Object(Chunk(
    "X-Request-ID" -> Json.String("12345")
  ))),
  hxVals := HxVals.from(RequestContext(42, "session-abc")),
  button("Submit")
)
```

Each attribute key is type-safe, and the `ToHtmxValue` type class ensures values render correctly to HTMX syntax.
