# HxSync

> `HxSync` represents the `hx-sync` attribute, coordinating multiple HTMX requests by specifying how new requests interact with pending or running requests on a target element. This prevents race conditions and ensures predictable behavior when multiple events fire in quick succession.

Create an `HxSync` by pairing an `HxTarget` (which element to synchronize) with a `HxSyncStrategy` (how to handle conflicts):

```scala
import zio.http.htmx._

// Wait for this element's request to finish before starting a new one
HxSync(HxTarget.This, HxSyncStrategy.Queue)

// Replace a pending request with a new one
HxSync(HxTarget.closest("form"), HxSyncStrategy.Replace)

// Drop new requests while one is in flight
HxSync(HxTarget.This, HxSyncStrategy.Drop)

// Abort the current request and start the new one immediately
HxSync(HxTarget.This, HxSyncStrategy.Abort)
```

## Sync Strategies

**`HxSyncStrategy.Queue`** queues new requests, executing them after the current request completes. This is the default HTMX behavior when `hx-sync` is not specified. Useful for ensuring requests are processed in order:

```scala
import zio.blocks.html._
import zio.http.htmx._

input(
  hxPost := "/search",
  hxSync := HxSync(HxTarget.This, HxSyncStrategy.Queue),
  placeholder := "Search..."
)
```

**`HxSyncStrategy.Replace`** cancels any pending request and immediately sends the new one. Useful for searches and filters where the latest value is all that matters:

```scala
import zio.blocks.html._
import zio.http.htmx._

input(
  hxPost := "/search",
  hxSync := HxSync(HxTarget.This, HxSyncStrategy.Replace),
  placeholder := "Live Search (only latest request sent)"
)
```

**`HxSyncStrategy.Drop`** discards new requests while one is in flight. Only the first request in a burst is sent; others are ignored. Useful for expensive operations that shouldn't be duplicated:

```scala
import zio.blocks.html._
import zio.http.htmx._

button(
  hxPost := "/expensive-operation",
  hxSync := HxSync(HxTarget.This, HxSyncStrategy.Drop),
  "Process (only first request sent)"
)
```

**`HxSyncStrategy.Abort`** cancels any in-flight request and immediately sends the new one. The old request's response is discarded. Useful for time-sensitive operations:

```scala
import zio.blocks.html._
import zio.http.htmx._

button(
  hxPost := "/time-sensitive-action",
  hxSync := HxSync(HxTarget.This, HxSyncStrategy.Abort),
  "Execute (cancels previous)"
)
```

## Target Specification

The first parameter of `HxSync` is an `HxTarget` specifying which element to synchronize. This allows coordinating requests across multiple elements:

**Synchronize the current element:**

Use `HxTarget.This` to synchronize the element that initiates the request:

```scala
import zio.blocks.html._
import zio.http.htmx._

input(
  hxPost := "/search",
  hxSync := HxSync(HxTarget.This, HxSyncStrategy.Replace),
  placeholder := "Search..."
)
```

**Synchronize a parent form:**

Synchronize across all fields in a form using `HxTarget.closest()`:

```scala
import zio.blocks.html._
import zio.http.htmx._

input(
  hxPost := "/validate",
  hxSync := HxSync(HxTarget.closest("form"), HxSyncStrategy.Queue),
  placeholder := "Email"
)
```

**Synchronize multiple fields at once:**

Apply the same sync strategy to multiple fields within a form:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  input(
    name := "firstName",
    hxPost := "/validate",
    hxSync := HxSync(HxTarget.closest("form"), HxSyncStrategy.Queue)
  ),
  input(
    name := "email",
    hxPost := "/validate",
    hxSync := HxSync(HxTarget.closest("form"), HxSyncStrategy.Queue)
  )
)
```

## Rendering & Parsing

**`render: String`** produces the literal `hx-sync` attribute value string:

```scala
import zio.http.htmx._

HxSync(HxTarget.This, HxSyncStrategy.Queue).render     // "this:queue"
HxSync(HxTarget.closest("form"), HxSyncStrategy.Replace).render  // "closest form:replace"
```

**`HxSync.parse(value: String): Either[String, HxSync]`** parses a rendered string back into a typed `HxSync`:

```scala
import zio.http.htmx._

HxSync.parse("this:queue")             // Right(HxSync(HxTarget.This, HxSyncStrategy.Queue))
HxSync.parse("closest form:replace")   // Right(HxSync(HxTarget.closest("form"), HxSyncStrategy.Replace))
```

## Common Patterns

Synchronization strategies prevent race conditions in interactive forms. Here are practical usage patterns:

### Search with Last-Value-Wins

Use `Replace` to ensure only the most recent search is sent when typing quickly:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.delay(300.millis),
  hxSync := HxSync(HxTarget.This, HxSyncStrategy.Replace),
  hxTarget := HxTarget.css("#results"),
  placeholder := "Search (only latest request sent)"
)
```

### Ordered Form Validation

Use `Queue` to validate form fields in the order they were changed, preventing race conditions:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  input(
    name := "username",
    hxPost := "/validate/username",
    hxSync := HxSync(HxTarget.closest("form"), HxSyncStrategy.Queue)
  ),
  input(
    name := "email",
    hxPost := "/validate/email",
    hxSync := HxSync(HxTarget.closest("form"), HxSyncStrategy.Queue)
  )
)
```

### Prevent Double-Submit

Use `Drop` to prevent duplicate submissions when users click a button repeatedly:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  button(
    hxPost := "/submit",
    hxSync := HxSync(HxTarget.This, HxSyncStrategy.Drop),
    "Submit (first click only)"
  )
)
```

### Abort Slow Requests

Use `Abort` for requests that shouldn't queue up, replacing old requests immediately:

```scala
import zio.blocks.html._
import zio.http.htmx._

button(
  hxPost := "/fetch-latest",
  hxSync := HxSync(HxTarget.This, HxSyncStrategy.Abort),
  "Fetch Latest (aborts previous)"
)
```

## Integration with Other Attributes

`HxSync` works alongside trigger and timing attributes to coordinate complex request patterns:

```scala
import zio.blocks.html._
import zio.http.htmx._
import scala.concurrent.duration._

input(
  hxPost := "/search",
  hxTrigger := HxTrigger.input.delay(500.millis).changed,
  hxSync := HxSync(HxTarget.This, HxSyncStrategy.Replace),
  hxParams := HxParams.only("query"),
  hxTarget := HxTarget.css("#results"),
  placeholder := "Search"
)
```

The `ToHtmxValue[HxSync]` instance renders automatically, so `HxSync` values work seamlessly with the `hxSync` attribute key.
