# JsonDiffer

> `JsonDiffer` is a **diff algorithm for JSON values** that computes the minimal [`JsonPatch`](./json-patch.md) transforming one `Json` value into another. It is the foundation of `JsonPatch.diff`.

`JsonDiffer` is a **diff algorithm for JSON values** that computes the minimal [`JsonPatch`](./json-patch.md) transforming one `Json` value into another. It is the foundation of `JsonPatch.diff`.

```scala
object JsonDiffer {
  def diff(source: Json, target: Json): JsonPatch
}
```

## Overview

`JsonDiffer` solves a fundamental problem: given two `Json` values, what is the most compact representation of the changes from one to the other?

Instead of transmitting or storing the entire new value, `JsonDiffer` emits a `JsonPatch` containing only the differences. For each type of change — numeric updates, string mutations, array reordering, field additions — it selects the most space-efficient representation:

```
Original JSON              Target JSON
┌──────────────────┐      ┌──────────────────┐
│ {                │      │ {                │
│   "name": "Alice"│      │   "name": "Alice"│
│   "age": 25      │─────→│   "age": 26      │
│   "city": "NYC"  │ diff │   "city": "NYC"  │
│ }                │      │ }                │
└──────────────────┘      └──────────────────┘
        │
        ▼
   JsonPatch {
     ObjectEdit(
       Modify("age", NumberDelta(1))
     )
   }
```

The core guarantee: `JsonDiffer.diff(source, target).apply(source) == Right(target)` always holds.

## Motivation

Transmitting or storing entire JSON documents wastes bandwidth, disk space, and network latency when only a fraction of the data changes. `JsonDiffer` enables:

- **Efficient data synchronization** — send only what changed, not the entire document
- **Change tracking and audit logs** — record every mutation as a first-class value
- **Optimistic concurrency control** — detect conflicts by analyzing patches rather than full documents
- **Replay and time-travel debugging** — reconstruct any historical state by applying patches in sequence
- **Compression** — patches are often 10–100× smaller than the full target value

## The Diffing Algorithm

`JsonDiffer.diff` adapts its strategy per `Json` type, choosing the most compact representation for each kind of change.

### Type Mismatches

When the types differ (e.g., number to string, object to array), `JsonDiffer` emits `Op.Set` to replace the value entirely. This is the only compact choice when the types diverge:

```scala

val numberToString = JsonDiffer.diff(Json.Number(42), Json.String("hello"))
val objectToArray  = JsonDiffer.diff(
  Json.Object("x" -> Json.Number(1)),
  Json.Array(Json.Number(1), Json.Number(2))
)
```

Both patches consist of a single `Set` operation:

```scala

numberToString.apply(Json.Number(42))
objectToArray.apply(Json.Object("x" -> Json.Number(1)))
```

### Numbers

For numeric changes, `JsonDiffer` emits `NumberDelta` — representing the difference between old and new values as a delta rather than replacing the entire number:

```scala

val increment = JsonDiffer.diff(Json.Number(100), Json.Number(105))
val decrement = JsonDiffer.diff(Json.Number(50), Json.Number(48))
```

The patches store deltas, not full new values:

```scala

increment.apply(Json.Number(100))
decrement.apply(Json.Number(50))
```

### Strings

For strings, `JsonDiffer` chooses between two strategies:

1. **LCS (Longest Common Subsequence) edit operations** — if the edits are more compact than the full new string.
2. **Full replacement with `Set`** — if the string has changed so much that storing the entire new value is smaller.

The algorithm computes common characters, then emits `Insert` and `Delete` operations (with `Append` and `Modify` existing as supported patch ops but not currently produced by `JsonDiffer`). It only emits the edits if their byte size is smaller than the new string's length:

```scala

// Prefix insertion — common suffix preserved
val addPrefix = JsonDiffer.diff(Json.String("world"), Json.String("hello world"))

// Complete replacement — almost no common subsequence
val replacement = JsonDiffer.diff(Json.String("abc"), Json.String("xyz"))
```

The first patch uses `StringEdit` (compact), the second uses `Set` (more efficient):

```scala
addPrefix.apply(Json.String("world"))
replacement.apply(Json.String("abc"))
```

### Arrays

For arrays, `JsonDiffer` uses **LCS-aligned insert, delete, and append operations** to transform the old array into the new one. It aligns common elements and generates the minimum sequence of mutations:

```scala

// Append elements at the end
val append = JsonDiffer.diff(
  Json.Array(Json.Number(1), Json.Number(2)),
  Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
)

// Delete and reorder — LCS finds the common elements
val reorder = JsonDiffer.diff(
  Json.Array(Json.Number(1), Json.Number(2), Json.Number(3)),
  Json.Array(Json.Number(3), Json.Number(2), Json.Number(1))
)
```

Both patches use efficient `ArrayEdit` operations:

```scala
append.apply(Json.Array(Json.Number(1), Json.Number(2)))
reorder.apply(Json.Array(Json.Number(1), Json.Number(2), Json.Number(3)))
```

### Objects

For objects, `JsonDiffer` compares field-by-field:

- Fields in the target but not in the source become `Add` operations.
- Fields in the source but not in the target become `Remove` operations.
- Fields in both are recursively diffed — if the values differ, a `Modify` with a sub-patch is emitted.

```scala

val original = Json.Object(
  "name" -> Json.String("Alice"),
  "age"  -> Json.Number(25),
  "city" -> Json.String("NYC")
)

val updated = Json.Object(
  "name"  -> Json.String("Alice"),
  "age"   -> Json.Number(26),
  "email" -> Json.String("alice@example.com")
)

val patch = JsonDiffer.diff(original, updated)
```

The patch contains `Add`, `Remove`, and `Modify` operations:

```scala
patch.apply(original)
```

### Nested Structures

`JsonDiffer` handles deeply nested objects and arrays recursively. Each field or element is diffed independently, producing compact patches at every level:

```scala

val original = Json.Object(
  "user" -> Json.Object(
    "name" -> Json.String("Alice"),
    "scores" -> Json.Array(Json.Number(95), Json.Number(87))
  )
)

val updated = Json.Object(
  "user" -> Json.Object(
    "name" -> Json.String("Alice"),
    "scores" -> Json.Array(Json.Number(95), Json.Number(88), Json.Number(92))
  )
)

val patch = JsonDiffer.diff(original, updated)
```

The patch navigates to the nested array and emits only the array changes:

```scala
patch.apply(original)
```

## Core Operations

`JsonDiffer` exposes a single public operation: `diff`, which computes the minimal `JsonPatch` that transforms `source` into `target`. Returns an empty patch if the values are already equal:

```scala
object JsonDiffer {
  def diff(source: Json, target: Json): JsonPatch
}
```

The roundtrip property always holds — applying the patch to the source always yields the target:

```scala

val source = Json.Object("x" -> Json.Number(10), "y" -> Json.Number(20))
val target = Json.Object("x" -> Json.Number(10), "y" -> Json.Number(21))
val patch  = JsonDiffer.diff(source, target)
```

```scala
patch.apply(source) == Right(target)
// res13: Boolean = true
```

`JsonDiffer.diff` is also available as `JsonPatch.diff` and as the `Json#diff` extension method:

```scala

// Via JsonPatch companion
val p1 = JsonPatch.diff(Json.Number(1), Json.Number(2))

// Via Json extension method
val p2 = Json.Number(1).diff(Json.Number(2))
```

## Integration

`JsonDiffer` is the implementation behind the public `JsonPatch.diff` API. You typically interact with it through `JsonPatch.diff` or the `Json#diff` extension method rather than calling `JsonDiffer.diff` directly.

The relationship is simple: `JsonPatch.diff` delegates to `JsonDiffer.diff` and wraps the result in a `JsonPatch` for further composition and application.

Once you have a `JsonPatch` from `JsonDiffer`, use the full `JsonPatch` API to apply it with different modes, compose multiple patches, or convert to/from `DynamicPatch`:

```scala

val source = Json.Object("count" -> Json.Number(0))
val target = Json.Object("count" -> Json.Number(1))
val patch  = JsonDiffer.diff(source, target)
```

Apply with different failure-handling modes:

```scala
patch.apply(source, PatchMode.Strict)
patch.apply(source, PatchMode.Lenient)
patch.apply(source, PatchMode.Clobber)
```

Compose patches with `++`:

```scala
val patch2 = JsonDiffer.diff(target, Json.Object("count" -> Json.Number(2)))
val combined = patch ++ patch2
```

## Implementation Notes

`JsonDiffer.diff` uses the LCS (Longest Common Subsequence) algorithm for both strings and arrays, ensuring that the number of edit operations is minimized. For strings, it compares character sequences; for arrays, it compares JSON elements by structural equality.

The LCS-based approach is particularly effective for arrays and strings with significant common subsequences — a common pattern in real-world data mutation scenarios (e.g., adding an item to a list, inserting a few characters into a string).

:::note
The LCS algorithm has O(n·m) time complexity, where n and m are the lengths of the two sequences. For very large strings or arrays, consider whether you need the minimal patch or can accept a faster approximation.
:::

## Running the Examples

`JsonDiffer` is the foundation of the `JsonPatch` API. Runnable examples demonstrating `JsonPatch.diff` (which internally uses `JsonDiffer`) are available in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run JsonPatch examples with sbt:**

These examples demonstrate how `JsonDiffer.diff` computes minimal patches through the public `JsonPatch.diff` API:

```bash
sbt "schema-examples/runMain jsonpatch.JsonPatchDiffAndApplyExample"
```

```bash
sbt "schema-examples/runMain jsonpatch.JsonPatchOperationsExample"
```

```bash
sbt "schema-examples/runMain jsonpatch.JsonPatchCompositionExample"
```

```bash
sbt "schema-examples/runMain jsonpatch.CompleteJsonPatchExample"
```
