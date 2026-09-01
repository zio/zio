# RoutePattern

> `RoutePattern[A]` pairs an HTTP method with a typed path pattern. It is the primary routing descriptor in `zio-blocks-endpoint`: every `Endpoint` carries a `RoutePattern` that determines which HTTP method and URL path it matches. Like `PathCodec`, it also carries a phantom `PathVars` track that mirrors the ordered path-variable declarations contributed by its path codec. Its shape is:

```scala
final case class RoutePattern[A](
  method: Method,
  pathCodec: PathCodec[A],
  doc: Doc = Doc.empty
)
```

The `PathVars` track is not a constructor parameter because it is purely type-level: it has zero runtime footprint and is derived from `pathCodec`. A literal-only route contributes no path-variable markers; a route with dynamic segments preserves them in declaration order.

## Motivation

HTTP routing requires matching both a method (GET, POST, …) and a path (`/users/42`). `RoutePattern` holds both in a single typed value. The type parameter `A` is the type of values extracted from the dynamic path segments — `Unit` for fully-literal paths, `Int` for a single integer segment, `(String, UUID)` for two dynamic segments, and so on.

Using a typed route pattern means route construction and path extraction are verified at compile time: the type of the extracted path value is always consistent with the path codec definition. The phantom `PathVars` track keeps the declared path-variable names and ignored-variable markers available to tooling without changing runtime behavior.

## Construction

Several construction forms exist. The method-first syntax is the most common and most readable; the others cover less typical use cases.

### Method-first syntax (recommended)

The primary syntax uses the `Method` extension method `/` to produce a `RoutePattern` directly:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val getUsers    = Method.GET / "users"
val postUser    = Method.POST / "users"
val deleteOrder = Method.DELETE / "orders" / PathCodec.uuid("orderId")
```

This is the recommended construction style: it reads like the route itself and keeps the method close to the path.

### Constant constructors

Pre-built method-only patterns are available as constants on the `RoutePattern` companion:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val get     = RoutePattern.GET
val post    = RoutePattern.POST
val put     = RoutePattern.PUT
val delete  = RoutePattern.DELETE
val patch   = RoutePattern.PATCH
val head    = RoutePattern.HEAD
val options = RoutePattern.OPTIONS
```

These are equivalent to `RoutePattern(Method.GET)` etc., with an empty path codec. Additional constants like `CONNECT` and `TRACE` are also available for the complete set of HTTP methods.

### From a `Path` value

To construct a pattern from a runtime `Path` (all literal segments), use `RoutePattern.apply(method, path)`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Path}

val route = RoutePattern(Method.GET, Path("/users/active"))
```

### Catch-all trailing patterns

To match any path suffix, use `RoutePattern.any`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Path}

val catchAll: RoutePattern[Path]   = RoutePattern.any
val getAny: RoutePattern[Path]     = RoutePattern.any(Method.GET)
```

These helpers preserve `PathVars = SegmentCodec.NoPathVars`: a trailing catch-all captures a `zio.http.Path` runtime value, but it does not declare any named path variables.

## Path Composition with `/`

To append additional `PathCodec` segments to a `RoutePattern`, use the `/` method:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val route = Method.GET / "users" / PathCodec.int("id") / "posts"
```

Each `/` call produces a new `RoutePattern` with a widened type. The type is automatically flattened, eliminating `Unit` components: `Unit / Int / Unit` becomes `Int`, not `((Unit, Int), Unit)`. At the same time, the phantom `PathVars` track is concatenated left-to-right, so a route like `Method.GET / PathCodec.int("id") / PathCodec.string("slug")` preserves the declaration order of `"id"` then `"slug"` for downstream tooling.

## Decoding and Encoding

`RoutePattern` is bidirectional: `RoutePattern#decode` validates a live request against the pattern, and `RoutePattern#encode` rebuilds a request pair from a typed path value.

### `RoutePattern#decode`

To check whether a method and path match, and extract the typed path value:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Path}

val route = Method.GET / "users" / PathCodec.int("id")

val result: Either[String, Int] = route.decode(Method.GET, Path("/users/42"))
```

`RoutePattern#decode` returns `Left` if the method does not match or any segment fails to parse, and `Right(value)` with the extracted path value otherwise. HEAD requests automatically fall back to GET for compatibility.

### `RoutePattern#encode`

To turn a typed value back into a `(Method, Path)` pair:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Path}

val route = Method.POST / "orders" / PathCodec.uuid("orderId")

val result: Either[String, (Method, Path)] = route.encode(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
```

### `RoutePattern#matches`

To test membership without extracting the value:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Path}

val route   = Method.GET / "users"
val matches = route.matches(Method.GET, Path("/users"))
```

## Structural Operations

Three structural operations transform an existing `RoutePattern` without building a new one from scratch: `RoutePattern#alternatives`, `RoutePattern#nest`, and `RoutePattern#render`.

### Alternatives

`RoutePattern#alternatives` expands `Method.ANY` and `Method.Methods` into a flat list of single-method patterns. `RouteTree` calls this before inserting into the trie:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val anyGet: RoutePattern[?] = RoutePattern.any(Method.GET)
val expanded = anyGet.alternatives
```

### Nesting

`RoutePattern#nest` prepends a literal path prefix without modifying the route's type or dynamic segments:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val route   = Method.GET / "users" / PathCodec.int("id")
val versioned = route.nest(PathCodec("/api/v1"))
```

This is useful for adding a version prefix to a group of existing routes without rewriting each one.

### Rendering

`RoutePattern#render` produces a human-readable string representation, useful for logging and OpenAPI path generation:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val route = Method.GET / "users" / PathCodec.int("id")
val rendered: String = route.render
```

Dynamic segments are rendered as `{name}` by default, matching OpenAPI path parameter convention.
