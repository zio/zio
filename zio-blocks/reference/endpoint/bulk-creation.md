# Bulk Endpoint Creation

> The `endpoints` macro (Scala 3.8+ with `-experimental` for `NamedTuple`, also works on Scala 3.7 with `-experimental`) lets you define multiple `Endpoint` values in a block and access them by name on the returned `NamedTuple`. Member names are either explicit `val` names or auto-generated from the `RoutePattern.render` string (method prefix + path template). Prefix grouping via `/` (`"api" / endpoints { ... }`, `PathCodec.int("id") / endpoints { ... }`) is available via the default `import zio.blocks.endpoint.*` — no extra import is needed. All examples below assume `import zio.blocks.endpoint.*` and `scalacOptions += "-experimental"`.

## Bulk endpoint creation with `endpoints { ... }`

String prefixes such as `"api"` are auto-converted to a literal `PathCodec[Unit]` via a `Conversion[String, PathCodec[Unit]]` provided by `zio.blocks.endpoint.*` — there is no String-specific `/` operator; constant prefixes auto-convert and use the same grouping `/` as capturing prefixes.

> **Inline-only:** prefix grouping (`prefix / endpoints { ... }`) requires an inline `endpoints { ... }` block. Binding the group to a value first (`val g = endpoints { ... }; "api" / g`) is not supported — the macro must see the block literal to compose prefixes.

```scala
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val api = "api" / endpoints {
  val customer = Endpoint(Method.GET / "customers")
  Endpoint(Method.GET / "health")
}
```

Member access is static (zero runtime cost):

```scala
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val api = "api" / endpoints {
  val customer = Endpoint(Method.GET / "customers")
  Endpoint(Method.GET / "health")
}
val c: Endpoint[Unit, Unit, Unit, Unit, AuthType.None.type] = api.customer
val h: Endpoint[Unit, Unit, Unit, Unit, AuthType.None.type] = api.`GET /health`
```

Auto-naming follows `RoutePattern.render` exactly:

- `GET /user/{userId}` for path variables (RFC 6570 `{var}`)
- `GET#|POST /orders` for multi-method
- `v{major}` for `~` concat segments
- `...` for trailing segments
- `.unused` renders as `{name}`

Constant-prefix nesting bakes the prefix into each child's `RoutePattern` at the description level; grouping nodes have no path themselves:

```scala
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val nested = "api" / endpoints {
  "v1" / endpoints {
    val users = Endpoint(Method.GET / "users")
  }
}
val u = nested.v1.users // route.render == "GET /api/v1/users"
```

Path-variable prefixes are implemented the same way. A capturing prefix contributes its segment to every child's path, while children keep their relative auto-names:

```scala
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val byId = PathCodec.int("id") / endpoints {
  val get = Endpoint(Method.GET / "orders")
  Endpoint(Method.DELETE / "orders") // auto-named `DELETE /orders`
}

val getOrder: Endpoint[Int, Unit, Unit, Unit, AuthType.None.type] = byId.get
val delOrder = byId.`DELETE /orders`
```

Both children carry the captured segment: `byId.get.route.render == "GET /{id}/orders"` and the auto-named member renders `"DELETE /{id}/orders"`. Static types are preserved — `byId.get` is an `Endpoint[Int, Unit, Unit, Unit, AuthType.None.type]`, so the captured `id` will be delivered to handlers when routes are created on the zio-http side. A child with its own path variables composes positionally: under `int("id")`, `Endpoint(Method.GET / "orders" / PathCodec.int("orderId"))` renders as `GET /{id}/orders/{orderId}` and carries `(Int, Int)`:

```scala
import zio.blocks.endpoint.*
import zio.blocks.endpoint.RoutePattern.*
import zio.http.Method

val ordersById = PathCodec.int("id") / endpoints {
  val o = Endpoint(Method.GET / "orders" / PathCodec.int("orderId"))
}

val lookup: Endpoint[(Int, Int), Unit, Unit, Unit, AuthType.None.type] = ordersById.o
// lookup.route.render == "GET /{id}/orders/{orderId}"
```

Variable prefixes should be bound to an explicit `val` — the val names the subgroup whose members you access through it (`byId.get`, `ordersById.o`). Constant-prefix and capturing-prefix subgroups can be freely nested inside each other: `"api" / endpoints { PathCodec.int("id") / endpoints { ... } }` composes both prefixes into every leaf route at compile time.

The returned type is a Scala 3 `NamedTuple` — static member access, erased at runtime. The DSL is Scala 3 only (3.7+ with `-experimental` for named tuples). All examples above compile against the `endpoint` module on Scala 3.8.3 with `scalacOptions += "-experimental"`.
