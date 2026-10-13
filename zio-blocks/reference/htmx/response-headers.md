# Request and Response Headers

> `zio.http.htmx.headers` provides typed HTTP headers for the request/response side of an HTMX exchange: the headers HTMX sends with every request, and the headers a handler sets to steer the client afterward. Each header follows the same `Header` / `Header.Typed` pattern that `zio.http` applies everywhere else, so reading one from a `Request` or `Response` returns a parsed domain value instead of a raw string, and several response headers reuse the same `HxSwap`, `HxTarget`, and `HxUrlUpdate` types that back the attribute DSL, keeping the two sides of an interaction consistent.

Here are the core patterns for reading and writing HTMX headers:

```scala
import zio.http.{Header, Headers, Request, Response, URL}
import zio.http.htmx.headers._

// Reading a request header HTMX sent
def handle(request: Request): Boolean =
  request.header(HxRequest).exists(_.enabled)

// Setting response headers that steer the client afterward
val response = Response.ok.addHeaders(
  Headers(HxRefresh.name -> "true", HxTriggerHeader.name -> "orderPlaced")
)
```

## Overview

The module splits into two directions and reuses supporting types from the attribute DSL for structured values:

| Direction | Type                     | Wire name                   | Shape                                             |
| --------- | ------------------------ | ---------------------------- | -------------------------------------------------- |
| Request   | `HxRequest`               | `hx-request`                 | `Boolean`, always `true` on an HTMX request         |
| Request   | `HxBoosted`                | `hx-boosted`                 | `Boolean`, set when the request came from `hxBoost` |
| Request   | `HxHistoryRestoreRequest`  | `hx-history-restore-request` | `Boolean`                                           |
| Request   | `HxCurrentUrl`             | `hx-current-url`             | non-empty `String` or `URL`                         |
| Request   | `HxTargetId`               | `hx-target`                  | non-empty `String` (target element id)              |
| Request   | `HxTriggerId`              | `hx-trigger`                 | non-empty `String` (triggering element id)          |
| Request   | `HxTriggerName`            | `hx-trigger-name`            | non-empty `String`                                  |
| Request   | `HxPrompt`                 | `hx-prompt`                  | `String`, trimmed                                   |
| Response  | `HxRedirect`               | `hx-redirect`                | non-empty `String` or `URL`                         |
| Response  | `HxRefresh`                | `hx-refresh`                 | `Boolean`                                           |
| Response  | `HxPushUrl`                | `hx-push-url`                | `HxUrlUpdate`                                       |
| Response  | `HxReplaceUrl`             | `hx-replace-url`             | `HxUrlUpdate`                                       |
| Response  | `HxReswap`                 | `hx-reswap`                  | `HxSwap`                                            |
| Response  | `HxRetarget`               | `hx-retarget`                | `CssSelector`                                       |
| Response  | `HxReselect`               | `hx-reselect`                | `CssSelector`                                       |
| Response  | `HxTriggerHeader`          | `hx-trigger`                 | `HxEventPayload`                                    |
| Response  | `HxTriggerAfterSettle`     | `hx-trigger-after-settle`    | `HxEventPayload`                                    |
| Response  | `HxTriggerAfterSwap`       | `hx-trigger-after-swap`      | `HxEventPayload`                                    |
| Response  | `HxLocation`               | `hx-location`                | `Json.Object`, built from path/target/swap          |

`HxTriggerId` (request) and `HxTriggerHeader` (response) share the wire name `hx-trigger` but read in opposite directions—one reports which element fired the request, the other tells the client which events to dispatch after the response arrives.

## Reading Request Headers

These headers describe the HTMX request itself: whether it came from HTMX at all, which element and value triggered it, and what the browser's current URL was. All of them parse from plain strings with no structured payload.

### Presence Flags

`HxRequest`, `HxBoosted`, and `HxHistoryRestoreRequest` are boolean headers—present with value `"true"` or `"false"`, parsed case-insensitively. Checking whether a request came from HTMX at all is the most common read:

```scala
import zio.http.{Headers, Request, URL}
import zio.http.htmx.headers._

val boosted = Request.get(URL.root).addHeaders(Headers(HxRequest.name -> "true", HxBoosted.name -> "true"))
```

Reading each flag back through `Request#header` parses the stored string into the typed value:

```scala
boosted.header(HxRequest)
// res1: Option[HxRequest] = Some(HxRequest(true))
boosted.header(HxBoosted)
// res2: Option[HxBoosted] = Some(HxBoosted(true))
```

### Identifiers and Text Values

`HxTargetId`, `HxTriggerId`, and `HxTriggerName` carry the id or name of the element involved in the request; `HxCurrentUrl` carries the browser's current URL. All four trim their value and reject blank input except `HxPrompt`, which passes through any trimmed string including an empty one:

```scala
import zio.http.{Headers, Request, URL}
import zio.http.htmx.headers._

val request = Request
  .get(URL.root)
  .addHeaders(Headers(HxTriggerName.name -> "search", HxCurrentUrl.name -> "https://zio.dev/blocks"))
```

Reading them back gives the trimmed values as typed headers:

```scala
request.header(HxTriggerName)
// res3: Option[HxTriggerName] = Some(HxTriggerName("search"))
request.header(HxCurrentUrl)
// res4: Option[HxCurrentUrl] = Some(HxCurrentUrl("https://zio.dev/blocks"))
```

`HxCurrentUrl` also accepts a `zio.http.URL` directly at construction time, encoding it to a string: `HxCurrentUrl(URL.parse("https://zio.dev/htmx").toOption.get)`.

## Setting Response Headers

Response headers tell HTMX what to do once the response body has been swapped in—redirect, refresh, change what gets swapped or where, fire client-side events, or update the URL bar. Several reuse types already documented for the attribute DSL, so the same modifiers and constructors apply on both sides of the exchange.

### Redirecting and Refreshing

`HxRedirect` sends the browser to a new URL with a full page load, and `HxRefresh` forces a full page reload of the current URL:

```scala
import zio.http.{Headers, Response, URL}
import zio.http.htmx.headers._

val redirect = Response.ok.addHeaders(Headers(HxRedirect.name -> "/login", HxRefresh.name -> "true"))
```

Reading them back through `Response#header` gives the parsed values:

```scala
redirect.header(HxRedirect)
// res6: Option[HxRedirect] = Some(HxRedirect("/login"))
redirect.header(HxRefresh)
// res7: Option[HxRefresh] = Some(HxRefresh(true))
```

`HxRedirect` also has a `URL` constructor—`HxRedirect(URL.parse("https://zio.dev/login").toOption.get)`—that encodes the URL before storing it.

### Controlling the URL Bar

`HxPushUrl` and `HxReplaceUrl` decide whether the browser's URL bar updates after a swap, using the same `HxUrlUpdate` type as the `hx-push-url` attribute: `HxUrlUpdate.Enabled` or `HxUrlUpdate.Disabled` for a plain boolean, or a `String`/`Path`/`URL` to push a specific address:

```scala
import zio.http.Path
import zio.http.htmx.HxUrlUpdate
import zio.http.htmx.headers.HxPushUrl

HxPushUrl(HxUrlUpdate.Enabled)
HxPushUrl(HxUrlUpdate(Path("/orders/42")))
```

### Overriding the Swap Strategy

`HxReswap` lets a response override the `hx-swap` strategy the requesting element declared, using the identical `HxSwap` DSL—including its timing and animation modifiers—documented in [HxSwap](./hx-swap.md):

```scala
import scala.concurrent.duration._
import zio.http.htmx.HxSwap
import zio.http.htmx.headers.HxReswap

val header = HxReswap(HxSwap.InnerHTML.swap(1.second).settle(250.millis))
```

Rendering reproduces the raw `hx-swap` syntax, and parsing that string round-trips it back to the same header:

```scala
HxReswap.render(header)
// res10: String = "innerHTML swap:1s settle:250ms"
HxReswap.parse(HxReswap.render(header)) == Right(header)
// res11: Boolean = true
```

### Retargeting and Reselecting

`HxRetarget` overrides where the response swaps in, and `HxReselect` overrides which part of the response fragment the client applies—both take a `CssSelector` from `zio.blocks.html`, the same type `hxTarget` and `hxSelect` accept:

```scala
import zio.blocks.html.CssSelector
import zio.http.htmx.headers.{HxReselect, HxRetarget}

HxRetarget(CssSelector.id("result"))
HxReselect(CssSelector.raw(".items > li"))
```

### Triggering Client-Side Events

`HxTriggerHeader`, `HxTriggerAfterSettle`, and `HxTriggerAfterSwap` fire HTMX events on the client—before, after settle, and after swap respectively. Each carries an `HxEventPayload`: either a plain event name or a JSON value forwarded to listeners as `event.detail`:

```scala
import zio.blocks.schema.json.Json
import zio.http.htmx.headers.{HxEventPayload, HxTriggerAfterSwap}

val plain = HxTriggerAfterSwap(HxEventPayload.Event("orderPlaced"))
val withDetail = HxTriggerAfterSwap(HxEventPayload.JsonValue(Json.Object("orderId" -> Json.Number(42))))
```

Rendering shows the two payload shapes side by side—a bare event name and a JSON object:

```scala
HxTriggerAfterSwap.render(plain)
// res14: String = "orderPlaced"
HxTriggerAfterSwap.render(withDetail)
// res15: String = "{\"orderId\":42}"
```

`HxEventPayload.parse` decides which shape it saw by inspecting the value: a leading `{` or `[` parses as JSON, anything else is treated as a plain event name, and a blank value is rejected.

:::note[Why `HxTriggerHeader`, not `HxTrigger`]
The response header is named `HxTriggerHeader` rather than `HxTrigger` to avoid colliding with the attribute-DSL type documented in [HxTrigger](./hx-trigger.md)—`zio.http.htmx.HxTrigger` declares which client event fires a request, while `zio.http.htmx.headers.HxTriggerHeader` tells the client which events to dispatch after a response. Both share the wire name `hx-trigger` but never appear in the same import.
:::

### Redirecting Without a Full Navigation

`HxLocation` triggers a client-side navigation to a new path without a full page load, optionally specifying where to swap the result and how—reusing `HxTarget` and `HxSwap` from the attribute DSL:

```scala
import zio.http.htmx.{HxSwap, HxTarget}
import zio.http.htmx.headers.HxLocation

val location = HxLocation("/next", target = Some(HxTarget.closest("section")), swap = Some(HxSwap.AfterEnd))
```

Rendering it produces the JSON object HTMX expects on the wire:

```scala
HxLocation.render(location)
// res17: String = "{\"path\":\"/next\",\"target\":\"closest section\",\"swap\":\"afterend\"}"
```

`HxLocation` also has overloads accepting a `zio.http.Path` or `zio.http.URL` for the first argument, encoding it the same way before building the JSON object. Parsing rejects anything that isn't a JSON object, since the wire format is always `{"path": ..., "target": ..., "swap": ...}` with `target` and `swap` omitted when absent.

## Reading and Writing Through Request and Response

Every header in this module is a `Header.Typed[H]`, so it works with the same `Request#header` / `Response#header` / `addHeader` / `addHeaders` methods `zio.http` provides everywhere else—there is no HTMX-specific accessor. Combining several response headers in one call is the common case, for example after processing a form submission:

```scala
import zio.http.{Response, URL}
import zio.http.htmx.headers.{HxRedirect, HxRefresh, HxTriggerAfterSettle, HxEventPayload}

val afterSubmit = Response.ok
  .addHeader(HxRedirect(URL.parse("/orders").toOption.get))
  .addHeader(HxTriggerAfterSettle(HxEventPayload.Event("orderPlaced")))
```

Because parsing failures return `Left` rather than throwing, `Request#header`/`Response#header` return `None` for a header that is present but malformed, exactly as they do for any other typed header in `zio.http`—see [Header](../http-model/headers.md) for the full read/write surface these types plug into.

## Integration Points

**With the attribute DSL:** `HxReswap`, `HxLocation`, `HxRetarget`, and `HxReselect` accept the exact same `HxSwap`, `HxTarget`, and `CssSelector` values that `hxSwap`, `hxTarget`, and `hxSelect` attributes accept, so a value built for one side works unchanged on the other.

**With `zio.blocks.schema.json`:** `HxEventPayload.JsonValue` wraps a `zio.blocks.schema.json.Json` value, letting event payloads carry structured data without a separate serialization step.

**With `zio.http`:** every type here is a `Header`/`Header.Typed[H]`, the same type class the built-in HTTP headers use, so `Headers`, `Request`, and `Response` treat HTMX headers no differently than `Content-Length` or `Cache-Control`.
