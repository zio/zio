# HxParams

> `HxParams` represents the `hx-params` attribute, controlling which form parameters are included in the HTMX request. Instead of sending all form fields, you can explicitly allow or forbid specific parameters through compile-safe domain types.

Use `HxParams.All` to include all parameters, `HxParams.None` to include none, or `HxParams.only()` and `HxParams.not()` to allow/forbid specific names. Here are the core patterns:

```scala
import zio.http.htmx._

// Include all parameters
HxParams.All

// Include no parameters
HxParams.None

// Include only specific parameters
HxParams.only("query", "page")

// Exclude specific parameters
HxParams.not("csrf_token", "session")
```

## Strategies

**`HxParams.All`** includes all form parameters in the request (the default HTMX behavior):

```scala
import zio.blocks.html._
import zio.http.htmx._

input(
  hxPost := "/search",
  hxParams := HxParams.All
)
```

**`HxParams.None`** excludes all form parameters. The request body is empty:

```scala
import zio.blocks.html._
import zio.http.htmx._

button(
  hxPost := "/ping",
  hxParams := HxParams.None,
  "Ping Server"
)
```

**`HxParams.only(first, rest*)`** includes only the listed parameters by name:

```scala
import zio.blocks.html._
import zio.http.htmx._

input(
  hxPost := "/search",
  hxParams := HxParams.only("query", "page", "limit"),
  placeholder := "Search..."
)
```

**`HxParams.not(first, rest*)`** excludes the listed parameters, sending all others:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/submit",
  hxParams := HxParams.not("csrf_token", "_method"),
  input(name := "username"),
  input(name := "csrf_token"),
  button("Submit")
)
```

## Rendering & Parsing

**`render: String`** produces the literal `hx-params` attribute value string:

```scala
import zio.http.htmx._

HxParams.All.render                           // "*"
HxParams.None.render                          // "none"
HxParams.only("query", "page").render         // "query,page"
HxParams.not("csrf_token", "session").render  // "not csrf_token,session"
```

**`HxParams.parse(value: String): Either[String, HxParams]`** parses a rendered string back into a typed `HxParams`:

```scala
import zio.http.htmx._

HxParams.parse("*")                    // Right(HxParams.All)
HxParams.parse("none")                 // Right(HxParams.None)
HxParams.parse("query,page")           // Right(HxParams.only("query", "page"))
HxParams.parse("not csrf,session")     // Right(HxParams.not("csrf", "session"))
```

Parse failures return a descriptive `Left`:

```scala
import zio.http.htmx._

HxParams.parse("")                     // Left("HTMX params list must be non-empty")
HxParams.parse("query,,page")          // Left("HTMX params list cannot contain empty names")
```

## Common Patterns

`HxParams` is useful when you need fine-grained control over which form fields are sent to the server. Here are representative usage patterns:

### Search Without CSRF Token

Include only the search query parameter, excluding the CSRF token typically added by form handlers:

```scala
import zio.blocks.html._
import zio.http.htmx._

input(
  name := "query",
  hxPost := "/search",
  hxParams := HxParams.only("query"),
  placeholder := "Search..."
)
```

### Selective Parameter Submission

When a form has many fields but an HTMX request should only send a few:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  input(name := "firstName"),
  input(name := "lastName"),
  input(
    name := "email",
    hxPost := "/validate-email",
    hxParams := HxParams.only("email"),
    hxTarget := HxTarget.next(".error"),
    placeholder := "Email"
  ),
  div("Error message")
)
```

### Exclude Framework Parameters

Prevent sending framework-specific parameters (like Rails' `_method`, CSRF tokens) to the HTMX endpoint:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/api/submit",
  hxParams := HxParams.not("_method", "authenticity_token", "utf8"),
  button("Submit")
)
```

### No Parameter Request

Send a request without any form data, useful for server-side-only actions:

```scala
import zio.blocks.html._
import zio.http.htmx._

button(
  hxPost := "/logout",
  hxParams := HxParams.None,
  "Logout"
)
```

## Integration with Request Control

`HxParams` works with other request-control attributes like `hxEncoding` and `hxSync`:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/upload",
  hxParams := HxParams.only("file", "description"),
  hxEncoding := HxEncoding.Multipart,
  input(name := "file", `type` := "file"),
  input(name := "description"),
  button("Upload")
)
```

The `ToHtmxValue[HxParams]` instance renders automatically, so `HxParams` values work seamlessly with the `hxParams` attribute key.
