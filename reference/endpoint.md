# Endpoint

> `Endpoint[PathInput, Input, Err, Output, Auth]` is the top-level descriptor for an HTTP endpoint. It holds a typed route, three independent codec channels (request input, error output, success output), an authentication type, and documentation metadata. `Endpoint` is pure data — it carries no server or client logic and imposes no effect type. Its full shape is:

```scala
final case class Endpoint[PathInput, Input, Err, Output, Auth <: AuthType](
  route: RoutePattern[PathInput],
  input: HttpCodec[CodecKind.Request, Input],
  error: HttpCodec[CodecKind.Response, Err],
  output: HttpCodec[CodecKind.Response, Output],
  auth: Auth,
  doc: Doc
)
```

## Motivation

An endpoint descriptor separates the **shape** of an HTTP surface from its **execution**. A single `Endpoint` value can be interpreted by a server to generate routes, by a client generator to produce typed API calls, or by an OpenAPI renderer to produce specification documents. This means the endpoint definition is the single source of truth — change it once and every interpreter updates.

The five type parameters track everything the compiler needs to enforce consistency across the whole stack:

| Parameter   | Meaning                                                    |
|-------------|------------------------------------------------------------|
| `PathInput` | Type of values extracted from path segments                |
| `Input`     | Aggregate type of all request inputs (query, header, body) |
| `Err`       | Aggregate type of all error response shapes                |
| `Output`    | Aggregate type of all success response shapes              |
| `Auth`      | Authentication scheme, carries the client requirement      |

## Construction

We create an `Endpoint` from a `RoutePattern` using `Endpoint.apply`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val ep = Endpoint(Method.GET / "users" / PathCodec.int("id"))
```

The route can also be built from a separate `RoutePattern` value:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val route = Method.POST / "orders" / PathCodec.uuid("orderId")
val ep    = Endpoint(route)
```

The initial `Endpoint` starts with `Unit` for all three codec channels and `AuthType.None` for auth, so further builder calls always widen the types additively.

## Request Input Builders

Every `Endpoint#in`, `Endpoint#query`, and `Endpoint#header` call adds another input component to the endpoint, widening the `Input` type parameter.

### Body input

To add a request body typed by a `Schema`, use `Endpoint#in`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.POST / "users")
  .in(Schema.string)
```

To specify the content type explicitly, pass a `MediaType` as well:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.mediatype.MediaTypes
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.POST / "users")
  .in(MediaTypes.application.`json`, Schema.string)
```

To add a raw `HttpCodec.Body` node directly, use `Endpoint#in`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.POST / "users")
  .in(HttpCodec.requestBody(Schema.string))
```

### Query parameters

To add a query parameter by name and schema, use `Endpoint#query`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .query("page", Schema.int)
  .query("limit", Schema.int)
```

To add a pre-built `HttpCodec.Query` node, use `Endpoint#query`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val pageCodec = HttpCodec.query("page", Schema.int)
val ep        = Endpoint(Method.GET / "users").query(pageCodec)
```

### Request headers

To add a request header by name and schema, use `Endpoint#header`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .header("X-Trace-Id", Schema.string)
```

To add a pre-built `HttpCodec.Header` node, use `Endpoint#header`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val traceCodec = HttpCodec.requestHeader("X-Trace-Id", Schema.string)
val ep         = Endpoint(Method.GET / "users").header(traceCodec)
```

## Success Output Builders

Every `Endpoint#out` and `Endpoint#outHeader` call adds a success response alternative or header component, widening `Output`.

### Response body

To add a 200 OK response body, use `Endpoint#out`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .out(Schema.string)
```

To specify a non-200 status code, use `Endpoint#out` with a `Status`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val ep = Endpoint(Method.POST / "users")
  .out(Status.Created, Schema.int)
```

To add content-type negotiation, pass a `MediaType`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.mediatype.MediaTypes
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val ep = Endpoint(Method.GET / "users")
  .out(MediaTypes.application.`json`, Schema.string)
  .out(Status.Created, MediaTypes.text.`plain`, Schema.int)
```

Multiple `Endpoint#out` calls produce alternatives. The output type widens from `Unit` to the first schema type, then to a nested `Either` for each additional alternative.

### Response headers

To add a typed response header, use `Endpoint#outHeader`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .out(Schema.string)
  .outHeader("X-Total-Count", Schema.int)
```

## Error Output Builders

Error channels work like success channels but populate the `Err` type parameter. Two variants exist: `Endpoint#outError` (cross-version) and `Endpoint#orOutError` (Scala 3 unions).

### `Endpoint#outError` — cross-version additive errors

To add an error response with a status code and body schema, use `Endpoint#outError`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val ep = Endpoint(Method.GET / "users")
  .out(Schema.string)
  .outError(Status.NotFound, Schema.string)
  .outError(Status.BadRequest, Schema.string)
```

Each `Endpoint#outError` call widens `Err` by one nested `Either` layer.

### `Endpoint#orOutError` — Scala 3 union errors

On Scala 3, `Endpoint#orOutError` accumulates error types as a native union type instead of nested `Either`s. The first call replaces the initial `Unit` error outright; subsequent calls build a `Fallback` codec backed by `Unions` derivation:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val ep = Endpoint(Method.GET / "users")
  .orOutError(Status.NotFound, Schema.string)
  .orOutError(Status.Conflict, Schema.int)

val typed: Endpoint[Unit, Unit, String | Int, Unit, AuthType.None.type] = ep
```

The compiler rejects overlapping union members — two `Endpoint#orOutError` calls both using `Schema.string` produce a compile error because `String | String` is not a valid discriminated union.

## Authentication

To attach an authentication scheme, use `Endpoint#auth`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val secured = Endpoint(Method.GET / "me")
  .auth(AuthType.Bearer)
```

The `Auth` type parameter carries the `ClientRequirement` associated type, so a bearer-secured endpoint exposes `auth.codec` typed as `HttpCodec[CodecKind.Request, zio.http.Header.Authorization.Bearer]`. See [AuthType](./auth-type.md) for all variants.

To override the HTTP status the server sends when the client does not meet the auth requirement, use `Endpoint#unauthorizedStatus`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Status}

val secured = Endpoint(Method.GET / "me")
  .auth(AuthType.Bearer)
  .unauthorizedStatus(Status.Unauthorized)
```

## Documentation

To attach a `Doc` value to the endpoint as a whole, use `Endpoint#doc`:

```scala
import zio.blocks.docs.Doc
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val ep = Endpoint(Method.GET / "users")
  .doc(Doc.empty)
```

Documentation attached here flows through to OpenAPI generation and any other documentation interpreters.
