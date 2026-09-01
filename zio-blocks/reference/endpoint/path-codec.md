# PathCodec

> `PathCodec[A]` is a composable descriptor for URL path structures. It holds a tree of segment codecs connected by concatenation and fallback nodes, and provides bidirectional path conversion: `PathCodec#decode` extracts a typed value from a `Path`, returning `Either[String, A]` (the typed value or error), and `PathCodec#format` formats a typed value back to a `Path`, returning `Either[String, Path]` (the path or error). In addition to its runtime value type `A`, each codec also carries a phantom `PathVars` track that records the ordered list of declared path-variable markers contributed by its dynamic segments. Its definition begins:

```scala
sealed trait PathCodec[A] {
  type PathVars
}
```

`PathVars` is purely type-level: it has zero runtime footprint and does not affect decoding or formatting. It exists so downstream tooling (for example, handler macros or static checks) can recover which named path variables a route declared, in order, and whether any of them were explicitly marked as ignored.

## Motivation

URL paths need both matching and generation. A routing library that only matches paths requires a separate URL-builder for links and redirects, leading to duplication and drift. `PathCodec` is bidirectional: every codec that can decode `/users/42` into `42: Int` can also format `42` back into `/users/42`. This makes it safe to use the same path definition for routing, link generation, and OpenAPI path parameter documentation.

## Structure

`PathCodec` is an ADT with four node types:

| Node        | Meaning                                                        |
| ----------- | -------------------------------------------------------------- |
| `Segment`   | A single path segment, described by a `SegmentCodec[A]`        |
| `Concat`    | Two path codecs composed sequentially with `/` or `++`         |
| `Transform` | Bidirectional type mapping over an existing codec              |
| `Fallback`  | Two literal alternatives (applies only with `orElse`)          |

## Construction

We build a `PathCodec` from smart constructors that produce typed segment nodes, from a path string, or by wrapping a `SegmentCodec` directly.

### Predefined segment constructors

The most common path building blocks are available as smart constructors on the companion:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val literalUsers: PathCodec[Unit]          = PathCodec.literal("users")
val intId: PathCodec[Int]                  = PathCodec.int("id")
val longId: PathCodec[Long]               = PathCodec.long("id")
val stringSlug: PathCodec[String]         = PathCodec.string("slug")
val uuidId: PathCodec[java.util.UUID]     = PathCodec.uuid("id")
val boolFlag: PathCodec[Boolean]          = PathCodec.bool("enabled")
val rest: PathCodec[zio.http.Path]        = PathCodec.trailing
```

`PathCodec.literal` is a macro that validates the value at compile time — it rejects empty strings and strings containing `/` or characters requiring URL encoding.

For literal names like `PathCodec.int("id")`, the name is preserved as a singleton type inside `PathVars`, so the phantom track remembers not just that the codec captures an `Int`, but that it came from the path variable named `"id"`.

### From a string

To build a codec from a slash-separated string of literal segments, use `PathCodec.apply`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val path: PathCodec[Unit] = PathCodec("/api/v1/users")
```

This is equivalent to concatenating `PathCodec.literal` for each segment.

### From a `SegmentCodec`

To wrap a custom `SegmentCodec[A]` into a `PathCodec[A]`, use `PathCodec.apply(segment)`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val combined = PathCodec(SegmentCodec.literal("v") ~ SegmentCodec.int("version"))
```

There is also an implicit conversion from `SegmentCodec[A]` to `PathCodec[A]` and from `String` to `PathCodec[Unit]`, so both can appear directly in `/` expressions.

## Phantom `PathVars` Track and `.unused`

Every dynamic path segment contributes one phantom marker to `PathCodec#PathVars`:

- `PathCodec.int("id")` contributes `PathVar["id", Int]`
- `PathCodec.uuid("orderId")` contributes `PathVar["orderId", UUID]`
- literal segments and `PathCodec.trailing` contribute no markers

Sequential composition with `/` or `++` concatenates those markers in declaration order, matching the left-to-right route shape.

Sometimes a route needs to capture a segment for matching or formatting, but a downstream handler intentionally does not consume that variable. For that case, single-variable codecs expose `.unused`, which keeps the runtime behavior identical while relabeling the phantom marker to `PathVar.Ignored[Name, Type]`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val userId: PathCodec[Int] = PathCodec.int("id")
val ignoredUserId: PathCodec[Int] = PathCodec.int("id").unused
```

`.unused` has zero runtime cost: decoding, formatting, rendering, and matching all behave exactly the same as the non-`.unused` codec. The only difference is the phantom `PathVars` marker, which tells tooling that this declared path variable was intentionally ignored.

## Composition

Path codecs compose in two ways: sequential concatenation with `/` or `++`, and literal alternatives with `orElse`.

### Sequential composition with `/` and `++`

`/` and `++` are equivalent: both concatenate two path codecs. The result type is flattened automatically (so `Unit / Int` gives `Int`, not `(Unit, Int)`), eliminating `Unit` components:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val route: PathCodec[Int] = PathCodec.literal("users") / PathCodec.int("id")
```

In the context of a `RoutePattern`, the same operator works directly:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val pattern = Method.GET / "users" / PathCodec.int("id") / "posts"
```

### Literal alternatives with `orElse`

To match either of two literal segments, use `orElse`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val either: PathCodec[Unit] =
  PathCodec.literal("users").orElse(PathCodec.literal("members"))
```

`orElse` is for literal alternatives only. Both branches must be `PathCodec[Unit]` with no captured path variables, and `PathCodec#alternatives` still validates at runtime that the branches are genuinely literal-only. In practice, use `orElse` only with `PathCodec.literal(...)` or string-literal path codecs.

## Decoding and Formatting

`PathCodec` is bidirectional: `PathCodec#decode` turns a runtime `Path` into a typed value, and `PathCodec#format` turns a typed value back into a `Path`.

### `PathCodec#decode`

To extract a typed value from a runtime `Path`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Path

val codec = PathCodec.int("id")

val result: Either[String, Int] = codec.decode(Path("/42"))
```

`PathCodec#decode` returns `Left(message)` when no segment matches or a segment cannot be parsed.

### `PathCodec#format`

To turn a typed value into a `Path`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Path

val codec = PathCodec.uuid("id")

val path: Either[String, Path] =
  codec.format(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
```

### `PathCodec#matches`

To test whether a `Path` matches without extracting a value:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Path

val codec   = PathCodec.literal("users")
val matched = codec.matches(Path("/users"))
```

## Type Transformations

Use these methods to map the typed value that `PathCodec` decodes or encodes without changing the underlying path structure.

### `PathCodec#transform`

To map the decoded value to a different type without changing the path structure, use `PathCodec#transform`. Both directions must be total:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.endpoint.PathCodec._

final case class UserId(value: Int)

val userIdCodec: PathCodec[UserId] =
  PathCodec.int("id").transform[UserId](UserId(_), _.value)
```

### `PathCodec#transformOrFail`

When decoding or encoding can fail, use `PathCodec#transformOrFail`. A `Left` from the decode function causes the path not to match:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val nonNegativeInt: PathCodec[Int] =
  PathCodec.int("count").transformOrFail[Int](
    n => if (n >= 0) Right(n) else Left(s"Expected non-negative, got $n"),
    n => Right(n)
  )
```

## Rendering

`PathCodec#render` produces a human-readable path string. Dynamic segments appear as `{name}` by default:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val codec = PathCodec.literal("users") / PathCodec.int("id")
val rendered: String = codec.render
```

To use a different prefix/suffix for dynamic segments (e.g., `:id` for Express-style paths), call the lower-level `PathCodec.render(codec, prefix = ":", suffix = "")`.
