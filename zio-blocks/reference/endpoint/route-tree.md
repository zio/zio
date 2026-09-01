# RouteTree

> `RouteTree[A]` is a routing trie keyed by HTTP method and path. It maps `(Method, Path)` pairs to values of type `A`, performing prefix-tree lookup with segment-priority ordering. Server-side interpreters primarily use it to build efficient route dispatch tables from a collection of `RoutePattern` values. Its structure is:

```scala
final case class RouteTree[A](
  roots: Map[Method, SegmentSubtree[A]]
)
```

`SegmentSubtree[A]` is a single level of the trie, holding literal-keyed branches and priority-ordered dynamic segment branches:

```scala
final case class SegmentSubtree[A](
  literals: Map[String, SegmentSubtree[A]],
  others: ListMap[SegmentCodec.Key, (SegmentCodec[_], SegmentSubtree[A])],
  value: Option[A]
)
```

## Motivation

Routing a `(Method, Path)` pair to a handler requires checking many patterns efficiently. A flat scan through all routes is O(n) and scales poorly. `RouteTree` uses a prefix trie keyed on path segments, so matching is O(depth) — proportional to the number of segments in the path, not the number of registered routes.

Within each trie level, literal segments are stored in a `Map[String, ...]` for O(1) lookup, while dynamic segments are stored in a `ListMap` ordered by match priority. The priority order (literal → int → long → uuid → bool → string → combined → trailing) ensures that more specific segments win ambiguous matches.

## Building a `RouteTree`

Start from an empty tree and add patterns with `RouteTree#add`:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Path}

val tree = RouteTree.empty[String]
  .add(Method.GET / "users", "list-users")
  .add(Method.GET / "users" / PathCodec.int("id"), "get-user")
  .add(Method.POST / "users", "create-user")
```

`RouteTree#add` calls `RoutePattern#alternatives` internally to expand `Method.ANY` and `Method.Methods` into individual method entries before inserting into the trie.

## Lookup

`RouteTree#get` looks up a `(Method, Path)` pair and returns the associated value if a match is found:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Path}

val tree = RouteTree.empty[String]
  .add(Method.GET / "users" / PathCodec.int("id"), "get-user")

val result: Option[String] = tree.get(Method.GET, Path("/users/42"))
```

HEAD requests automatically fall back to the GET subtree if no HEAD handler is registered, matching the HTTP specification.

## Merging

`RouteTree#merge` combines two trees. On conflicts, the right-hand-side value takes precedence:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val treeA = RouteTree.empty[String].add(Method.GET / "users", "users-v1")
val treeB = RouteTree.empty[String].add(Method.GET / "users", "users-v2")

val merged = treeA.merge(treeB)
// GET /users → "users-v2"
```

## Mapping

`RouteTree#map` transforms all values in the trie while preserving its structure:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val tree: RouteTree[String] = RouteTree.empty[String]
  .add(Method.GET / "users", "get-users")

val lengths: RouteTree[Int] = tree.map(_.length)
```

## Match Priority

Within a single trie level, `SegmentSubtree` tries literal branches first, then dynamic branches in priority order. Given a path like `/users/active`:

1. `literals.get("active")` is tried first — if an exact literal route for `"active"` exists, it matches.
2. Otherwise, dynamic segment codecs are tried in order: `Int` (fails, `"active"` is not numeric), `Long` (fails), `UUID` (fails), `Bool` (fails), `String` (succeeds).

This means `/users/42` matches an `Int` route even if a `String` route is also registered, because integers are higher priority than strings.

## `SegmentSubtree`

`SegmentSubtree` is the internal per-level trie node. It is not typically constructed directly — `RouteTree#add` builds it internally. The two key fields are:

- **`literals`**: a `Map[String, SegmentSubtree[A]]` for O(1) exact-match lookups.
- **`others`**: a `ListMap[SegmentCodec.Key, (SegmentCodec[_], SegmentSubtree[A])]` ordered by priority for dynamic segment matching.

The `value: Option[A]` field holds the registered value when a complete path terminates at this node. Trailing segments have special handling: if a `Trailing` codec is registered and the current index is past the end of the path segments, the lookup returns its subtree value.
