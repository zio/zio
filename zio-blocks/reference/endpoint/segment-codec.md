# SegmentCodec

> `SegmentCodec[A]` describes a single URL path segment. It supports basic typed segment kinds — `SegmentCodec.bool`, `SegmentCodec.int`, `SegmentCodec.long`, `SegmentCodec.string`, `SegmentCodec.uuid`, and `SegmentCodec.literal` — as well as intra-segment composition via `~`, which combines multiple typed parts within a single path segment (for example, `v42` as a literal prefix followed by an integer). Ambiguous adjacencies (`string ~ string`, numeric ~ numeric in any `Int`/`Long` order, anything involving `Trailing`) are rejected at runtime with `IllegalArgumentException`. Combine representation first, then transform: `transform` / `transformOrFail` lift a segment into a `PathCodec`, ending intra-segment composition. The core type-level shape is:

```scala
sealed trait SegmentCodec[A]
```

(The trait also includes methods for `~` composition, `transform` lifting, documentation, examples, formatting, and rendering.)

## Motivation

Standard routing libraries treat path segments as plain strings, deferring all parsing to handler code. `SegmentCodec` encodes the type of each segment statically, so the compiler knows whether a segment produces an `Int`, a `UUID`, or a custom domain type. This enables:

- **Type-safe path building**: `PathCodec.int("id")` produces a `PathCodec[Int]`, not `PathCodec[String]`.
- **Bidirectional conversion**: every `SegmentCodec` can both decode a string into `A` and format an `A` back to a string.
- **Fail-fast combination validation**: the `~` operator rejects ambiguous adjacencies (`string ~ string`, numeric ~ numeric) at composition time, so bad splits surface immediately instead of misrouting.

## Segment Kinds

Each segment kind maps to a specific URL token type. Literal segments match a fixed string; the others capture and parse dynamic values.

### Literal segments

A `Literal` segment matches exactly one fixed string value. Use `SegmentCodec.literal` with a compile-time string constant:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val usersLit: SegmentCodec.Literal = SegmentCodec.literal("users")
```

`SegmentCodec.literal` is a macro: it validates that the value is a non-empty, URL-safe, single-segment string at compile time and rejects `""`, `"foo/bar"`, or strings requiring percent-encoding.

### Typed dynamic segments

Standard typed segment constructors each accept a name string for documentation and OpenAPI path parameter labels:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val boolSeg: SegmentCodec[Boolean]        = SegmentCodec.bool("flag")
val intSeg: SegmentCodec[Int]             = SegmentCodec.int("id")
val longSeg: SegmentCodec[Long]           = SegmentCodec.long("id")
val stringSeg: SegmentCodec[String]       = SegmentCodec.string("slug")
val uuidSeg: SegmentCodec[java.util.UUID] = SegmentCodec.uuid("id")
```

If a route should capture a segment for matching or formatting but the handler intentionally does not consume the variable, the leaf dynamic segment codecs expose `.unused`:

```scala
import zio.blocks.endpoint.SegmentCodec

val requiredId = SegmentCodec.int("id")
val ignoredId  = SegmentCodec.int("id").unused
```

`.unused` keeps decoding, formatting, rendering, and composition identical. The only difference is at the type level: downstream tooling (handler macros, static checks) sees the variable as intentionally unused and does not require the handler to bind it.

The ordering of match priority in the routing trie follows the kind: `Literal` matches first, then `Int`, `Long`, `UUID`, `Bool`, `String`, `Combined`, and `Trailing` last.

### Trailing segment

`SegmentCodec.Trailing` captures all remaining path segments as a `zio.http.Path`. Use it for wildcard routes; it always has the lowest priority in the trie:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Path

val rest: SegmentCodec[Path] = SegmentCodec.Trailing
```

## Intra-Segment Composition with `~`

The `~` operator combines two `SegmentCodec` values into a `Combined` codec that matches a single path segment containing both parts consecutively. This is useful for version strings like `v42` or prefixed identifiers like `usr-550e8400`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val versionSeg: SegmentCodec[Int] =
  SegmentCodec.literal("v") ~ SegmentCodec.int("major")
```

The type is automatically flattened (eliminating `Unit` from the literal), so the resulting codec decodes `"v42"` into `42` and formats `42` back to `"v42"`.

### Runtime adjacency validation

The `~` operator checks the physical adjacency at composition time. Two categories of combination are always rejected with `IllegalArgumentException`:

- **Two string segments**: `SegmentCodec.string("a") ~ SegmentCodec.string("b")` — both are unbounded greedy matchers; the parser cannot know where one ends and the other begins.
- **Two numeric segments**: `SegmentCodec.int("a") ~ SegmentCodec.int("b")` — numeric segments are also ambiguously bounded (any `Int`/`Long` order, including through a flattened combined tail).

Anything involving `Trailing` is likewise rejected. Combinations that are safe compose normally:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

// Safe: literal delimiter separates two dynamic segments
val versionMajorMinor =
  SegmentCodec.literal("v") ~ SegmentCodec.int("major") ~
  SegmentCodec.literal("x") ~ SegmentCodec.int("minor")

// Safe: UUID in the middle, bounded by strings on both sides
val prefixedUuid =
  SegmentCodec.string("prefix") ~ SegmentCodec.uuid("id") ~ SegmentCodec.string("suffix")
```

Attempting an ambiguous combination like `string ~ string` throws `IllegalArgumentException` at composition time, not a silent misroute.

## Type Transformations

Combine representation first, then transform: `transform` / `transformOrFail` map the decoded segment value into a domain type and lift the segment into a `PathCodec`, ending intra-segment composition (a transformed codec no longer offers `~`; compose with `/` instead).

### `SegmentCodec#transform`

To map the decoded segment value to a different type, use `SegmentCodec#transform`. Both the decode and encode functions must be total:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

final case class UserId(value: java.util.UUID)

val userIdCodec: PathCodec[UserId] =
  SegmentCodec.uuid("id").transform(UserId(_), _.value)
```

### `SegmentCodec#transformOrFail`

When decoding can fail, use `SegmentCodec#transformOrFail`. A `Left` result causes segment matching to fail for that candidate:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

final case class PositiveInt(value: Int)

val positiveIntCodec: PathCodec[PositiveInt] =
  SegmentCodec.int("count").transformOrFail(
    n => if (n > 0) Right(PositiveInt(n)) else Left(s"Expected positive, got $n"),
    p => Right(p.value)
  )
```

## Formatting and Rendering

These methods convert a typed value back to a string for use in URL construction and documentation output.

### `SegmentCodec#format`

To format a typed value into a single-segment `Path`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Path

val seg  = SegmentCodec.int("id")
val path: Path = seg.format(42)
```

### `SegmentCodec#render`

To produce the human-readable representation of the segment for use in OpenAPI path parameters and documentation:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._

val intSeg = SegmentCodec.int("id")
val rendered: String = intSeg.render()
```

By default, `SegmentCodec.render` produces `/{name}` for dynamic segments and `/{value}` for literal segments (including the leading slash in both cases). This leading slash is part of the segment's representation, not added by path composition. To customize the format, pass prefix and suffix arguments: `intSeg.render(":", "")` produces `/:id`. Both dynamic and literal segments always include this leading slash in their rendered form.

## Priority Ordering

When `RouteTree` builds a routing trie, it uses `SegmentCodec.Kind` ordering to resolve ambiguous matches. Literals always win; within dynamic segments, more specific types take priority:

| Priority | Kind       |
| -------- | ---------- |
| 1        | `Literal`  |
| 2        | `Int`      |
| 3        | `Long`     |
| 4        | `UUID`     |
| 5        | `Bool`     |
| 6        | `String`   |
| 7        | `Combined` |
| 8        | `Trailing` |

This ordering ensures that `/users/42` matches an `Int` route before a `String` route, and `/users/active` matches a `String` route because `"active"` is not a valid integer.
