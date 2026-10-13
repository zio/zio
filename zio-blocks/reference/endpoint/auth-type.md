# AuthType

> `AuthType` is a sealed trait that describes an HTTP authentication scheme as a first-class type parameter on `Endpoint`. Each `AuthType` variant carries an associated type `ClientRequirement` — the type of credential the client must provide — and a codec that extracts it from the request. Its definition is:

```scala
sealed trait AuthType {
  type ClientRequirement
  def codec: HttpCodec[CodecKind.Request, ClientRequirement]
  def unauthorizedStatus: Status
}
```

## Motivation

Authentication requirements are often encoded informally — a comment in the handler, a middleware convention, or a bare string header check. `AuthType` makes the auth requirement part of the endpoint's static type. A bearer-secured endpoint has type `Endpoint[..., AuthType.Bearer]`, which means:

- The `auth.codec` field is typed as `HttpCodec[CodecKind.Request, zio.http.Header.Authorization.Bearer]`, not `HttpCodec[..., String]`.
- Interpreters (server, client, OpenAPI) can inspect the auth type without stringly-typed reflection.
- Composing auth types with `|` produces a union that the compiler verifies is discriminated.

## Built-in Variants

Each built-in variant maps to a specific HTTP authorization scheme. Use the one that matches your API's authentication model.

### `AuthType.None`

The default auth type — no authentication required. Its `ClientRequirement` is `Unit` and its codec is `HttpCodec.Empty`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val publicEndpoint = Endpoint(Method.GET / "health")
```

### `AuthType.Basic`

HTTP Basic authentication. The `ClientRequirement` is `zio.http.Header.Authorization.Basic`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val basicEndpoint = Endpoint(Method.GET / "admin")
  .auth(AuthType.Basic)

val codec = basicEndpoint.auth.codec
```

### `AuthType.Bearer`

Bearer token authentication (OAuth 2.0 / JWT). The `ClientRequirement` is `zio.http.Header.Authorization.Bearer`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val bearerEndpoint = Endpoint(Method.GET / "me")
  .auth(AuthType.Bearer)
```

### `AuthType.Digest`

HTTP Digest authentication. The `ClientRequirement` is `zio.http.Header.Authorization.Digest`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val digestEndpoint = Endpoint(Method.GET / "secure")
  .auth(AuthType.Digest)
```

### `AuthType.Custom`

For authentication schemes not covered by the built-in variants, `Custom` wraps any `HttpCodec[CodecKind.Request, ClientReq]`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

final case class ApiKey(value: String)

val apiKeyCodec = HttpCodec.requestHeader("X-Api-Key", Schema.string.transform[ApiKey](ApiKey(_), _.value))
val apiKeyAuth  = AuthType.Custom(apiKeyCodec)
val keyEndpoint = Endpoint(Method.GET / "data").auth(apiKeyAuth)
```

## Composition

`AuthType` values compose in two ways: `|` builds an OR alternative that accepts either scheme, and `Scoped` attaches scope requirements to an existing auth type.

### `AuthType#|` — OR composition

Two `AuthType` values can be combined with `|` to accept either scheme. The result is an `AuthType.Or` whose `ClientRequirement` is the union of both:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val flexEndpoint = Endpoint(Method.GET / "resource")
  .auth(AuthType.Basic | AuthType.Bearer)
```

The `|` operator automatically computes the combined `ClientRequirement` type as a union of both requirements. The codec of the resulting `Or` is a `HttpCodec.Fallback` — it tries the left scheme first and falls back to the right.

### `AuthType.Scoped`

To attach OAuth scope requirements to a bearer auth, wrap it in `AuthType.Scoped`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val scopedEndpoint = Endpoint(Method.GET / "admin")
  .auth(AuthType.Scoped(AuthType.Bearer, List("admin:read", "admin:write")))
```

`Scoped` does not change the codec — it carries the scopes as metadata for interpreters that perform scope-level authorization checks.

## Unauthorized Status

By default, `AuthType` returns `Status.NotFound` when the client does not meet the auth requirement (to avoid leaking endpoint existence). To change this, use `Endpoint#unauthorizedStatus` or call `AuthType#withUnauthorizedStatus` directly:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Status}

val endpoint = Endpoint(Method.GET / "me")
  .auth(AuthType.Bearer)
  .unauthorizedStatus(Status.Unauthorized)
```

`Or` composition preserves `AuthType#unauthorizedStatus` — it uses the left auth type's status.
