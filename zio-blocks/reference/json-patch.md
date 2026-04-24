# JsonPatch

> `JsonPatch` is an **untyped, composable patch** for [`Json`](./json.md) values. It represents a sequence of operations that transform one `Json` value into another — computed automatically via a diff algorithm or constructed manually. The two fundamental operations are `JsonPatch.diff` to compute a patch between two `Json` values, and `JsonPatch#apply` to apply it.

`JsonPatch` is an **untyped, composable patch** for [`Json`](./json.md) values. It represents a sequence of operations that transform one `Json` value into another — computed automatically via a diff algorithm or constructed manually. The two fundamental operations are `JsonPatch.diff` to compute a patch between two `Json` values, and `JsonPatch#apply` to apply it.

`JsonPatch`:
- is a pure value — applying it never mutates the input
- is composable via `++`, sequencing two patches one after another
- supports three failure-handling modes: `Strict`, `Lenient`, and `Clobber`
- carries its own `Schema` instances for full serialization support
- converts bidirectionally to/from `DynamicPatch` for use in generic patching pipelines

The `JsonPatch` type wraps a sequence of operations:

```scala
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp])
```

## Motivation

In most systems, updating JSON data means transmitting the entire new value — even when only a single field changed. `JsonPatch` solves this by representing changes as a **first-class value** that can be:

- **Transmitted efficiently** — send only what changed, not the entire document
- **Stored for audit logs** — record every change for compliance, debugging, or undo
- **Composed** — merge multiple changes into a single atomic patch
- **Serialized** — persist patches to disk or a message queue and replay them later

```
Source JSON                    Target JSON
┌─────────────────────┐        ┌─────────────────────┐
│ { "name": "Alice",  │        │ { "name": "Alice",  │
│   "age": 25,        │──diff──│   "age": 26,        │
│   "city": "NYC" }   │        │   "city": "NYC" }   │
└─────────────────────┘        └─────────────────────┘
           │
           ▼
      JsonPatch {
        ObjectEdit(
          Modify("age", NumberDelta(1))
        )
      }
           │
           ▼ apply
┌─────────────────────┐
│ { "name": "Alice",  │
│   "age": 26,        │
│   "city": "NYC" }   │
└─────────────────────┘
```

The "hello world" for `JsonPatch` is diff-then-apply. We compute a patch from `source` to `target`, then verify that applying it to `source` reproduces `target`:

```scala

val source = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(25))
val target = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(26))

val patch: JsonPatch = JsonPatch.diff(source, target)
```

Applying the patch to `source` always yields `Right(target)`:

```scala
patch.apply(source) == Right(target)
// res1: Boolean = true
```

## Creating Patches

There are three ways to create a `JsonPatch`: compute one automatically with `JsonPatch.diff`, construct one manually with `JsonPatch.root` or `JsonPatch.apply`, or start from the identity patch `JsonPatch.empty`.

### `JsonPatch.diff`

Computes the minimal `JsonPatch` that transforms `source` into `target`. Uses a smart diff strategy per value type — see the [Diffing Algorithm](#diffing-algorithm) section for details:

```scala
object JsonPatch {
  def diff(source: Json, target: Json): JsonPatch
}
```

`JsonPatch.diff` is also available as the `Json#diff` extension method:

```scala

// Via companion object
val p1 = JsonPatch.diff(Json.Number(10), Json.Number(15))

// Via extension method on Json
val p2 = Json.Number(10).diff(Json.Number(15))

// Nested object diff produces minimal ObjectEdit
val p3 = JsonPatch.diff(
  Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2)),
  Json.Object("a" -> Json.Number(1), "b" -> Json.Number(9))
)
// p3 only touches "b", leaves "a" unchanged
```

### `JsonPatch.root`

Creates a patch with a single operation applied at the root of the value:

```scala
object JsonPatch {
  def root(operation: JsonPatch.Op): JsonPatch
}
```

For example, we can replace the root entirely, increment a number, or add a field to a root object:

```scala

// Replace the entire value
val replaceAll = JsonPatch.root(Op.Set(Json.Null))

// Increment a number at the root
val increment = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))

// Add a field to a root object
val addField = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Add("active", Json.Boolean(true)))))
```

### `JsonPatch.apply`

Creates a patch with a single operation applied at the specified `DynamicOptic` path. Use this when targeting a nested location within the value:

```scala
object JsonPatch {
  def apply(path: DynamicOptic, operation: JsonPatch.Op): JsonPatch
}
```

Paths are built fluently on `DynamicOptic.root` using `.field(name)` to navigate object fields and `.at(index)` to navigate array elements. For instance, to increment `age` inside a `user` object:

```scala

val agePath  = DynamicOptic.root.field("user").field("age")
val agePatch = JsonPatch(agePath, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))
val nested   = Json.Object("user" -> Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(25)))
```

Applying the patch navigates to the nested `age` field and increments it:

```scala
agePatch.apply(nested)
// res5: Either[SchemaError, Json] = Right(
//   Object(
//     IndexedSeq(
//       (
//         "user",
//         Object(IndexedSeq(("name", String("Alice")), ("age", Number(26))))
//       )
//     )
//   )
// )
```

### `JsonPatch.empty`

The empty patch. Applying it to any `Json` value returns that value unchanged. `JsonPatch.empty` is the identity element for `++`:

```scala
object JsonPatch {
  val empty: JsonPatch
}
```

`JsonPatch.empty` is useful as a neutral starting point when building patches conditionally:

```scala

```

Both `JsonPatch#isEmpty` and applying `JsonPatch.empty` confirm the identity property:

```scala
JsonPatch.empty.isEmpty
// res7: Boolean = true
JsonPatch.empty.apply(Json.Number(42))
// res8: Either[SchemaError, Json] = Right(Number(42))
(JsonPatch.empty ++ JsonPatch.empty).isEmpty
// res9: Boolean = true
```

### `JsonPatch.fromDynamicPatch`

Converts a generic `DynamicPatch` to a `JsonPatch`. Returns `Left[SchemaError]` for operations not representable in JSON:
- Temporal deltas (`InstantDelta`, `DurationDelta`, etc.) — JSON has no native time type
- Non-string map keys — JSON object keys must always be strings

All numeric delta types (`IntDelta`, `LongDelta`, `DoubleDelta`, etc.) are widened to `NumberDelta(BigDecimal)`:

```scala
object JsonPatch {
  def fromDynamicPatch(patch: DynamicPatch): Either[SchemaError, JsonPatch]
}
```

The round-trip through `DynamicPatch` preserves numeric deltas:

```scala

val original: JsonPatch                  = JsonPatch.diff(Json.Number(1), Json.Number(2))
val dynPatch: DynamicPatch               = original.toDynamicPatch
val back: Either[SchemaError, JsonPatch] = JsonPatch.fromDynamicPatch(dynPatch)
```

The roundtrip succeeds and the recovered patch equals the original:

```scala
back == Right(original)
// res11: Boolean = true
```

## Core Operations

`JsonPatch` exposes operations for applying patches, composing them, and converting between formats. The three groups of operations are applying, composing, and converting.

### Applying Patches

The primary way to use a `JsonPatch` is to call `JsonPatch#apply` or the `Json#patch` extension method, both of which accept an optional `PatchMode` argument.

#### `apply`

Applies this patch to a `Json` value. Returns `Right` with the patched value on success, or `Left[SchemaError]` on failure. The `mode` parameter controls failure handling — see [`PatchMode`](#patchmode):

```scala
case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def apply(value: Json, mode: PatchMode = PatchMode.Strict): Either[SchemaError, Json]
}
```

`apply` is also available via the `Json#patch` extension method. Both forms produce the same result:

```scala

val applyJson  = Json.Object("score" -> Json.Number(10))
val applyPatch = JsonPatch.root(Op.ObjectEdit(Chunk(
  ObjectOp.Modify("score", JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5)))))
)))
```

The direct call and the extension method are equivalent:

```scala
applyPatch.apply(applyJson)
// res13: Either[SchemaError, Json] = Right(
//   Object(IndexedSeq(("score", Number(15))))
// )
applyJson.patch(applyPatch)
// res14: Either[SchemaError, Json] = Right(
//   Object(IndexedSeq(("score", Number(15))))
// )
applyJson.patch(applyPatch, PatchMode.Lenient)
// res15: Either[SchemaError, Json] = Right(
//   Object(IndexedSeq(("score", Number(15))))
// )
```

#### `isEmpty`

Returns `true` if this patch contains no operations:

```scala
case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def isEmpty: Boolean
}
```

A patch computed between two identical values also produces an empty patch:

```scala

```

```scala
JsonPatch.empty.isEmpty
// res17: Boolean = true
JsonPatch.diff(Json.Number(1), Json.Number(1)).isEmpty
// res18: Boolean = true
```

### Composing Patches

`++` is the principal operator for building complex patches from smaller, focused ones. The `JsonPatch.empty` value is the identity element for `++`:

```scala
case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def ++(that: JsonPatch): JsonPatch
}
```

Concatenating two patches applies `this` first, then `that`. This allows building a single patch that updates multiple fields independently:

```scala

val renamePatch = JsonPatch(
  DynamicOptic.root.field("name"),
  Op.Set(Json.String("Bob"))
)
val incrAgePatch = JsonPatch(
  DynamicOptic.root.field("age"),
  Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))
)
val combinedPatch = renamePatch ++ incrAgePatch
val personJson    = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(25))
```

The combined patch applies both operations in sequence:

```scala
combinedPatch.apply(personJson)
// res20: Either[SchemaError, Json] = Right(
//   Object(IndexedSeq(("name", String("Bob")), ("age", Number(26))))
// )
```

### Converting

`toDynamicPatch` converts a `JsonPatch` to a [`DynamicPatch`](./patch.md). This is always safe — every JSON operation maps to a corresponding dynamic operation. `NumberDelta` widens to `BigDecimalDelta`:

```scala
case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def toDynamicPatch: DynamicPatch
}
```

To convert in the opposite direction, use `JsonPatch.fromDynamicPatch` — see [Creating Patches](#creating-patches) above:

```scala

val patch: JsonPatch  = JsonPatch.diff(Json.Number(1), Json.Number(5))
val dyn: DynamicPatch = patch.toDynamicPatch
```

## PatchMode

`PatchMode` controls how `JsonPatch#apply` reacts when an operation's precondition is not met (e.g., a field is missing, or `ObjectOp.Add` targets a key that already exists):

| Mode | Behaviour |
|------|-----------|
| `PatchMode.Strict` (default) | Returns `Left[SchemaError]` on the first failure |
| `PatchMode.Lenient` | Silently skips failing operations; returns `Right` with partial result |
| `PatchMode.Clobber` | Overwrites on conflicts; forces through missing-field errors where possible |

`ObjectOp.Add` fails in `Strict` mode when the key already exists. In `Lenient` mode the conflicting add is silently skipped; in `Clobber` mode it overwrites the existing value:

```scala

val modeJson  = Json.Object("a" -> Json.Number(1))
val modePatch = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Add("a", Json.Number(99)))))
```

The three modes produce different outcomes for the same conflicting patch:

```scala
modeJson.patch(modePatch, PatchMode.Strict)
// res23: Either[SchemaError, Json] = Left(
//   SchemaError(
//     List(
//       ExpectationMismatch(
//         source = DynamicOptic(IndexedSeq()),
//         expectation = "Key 'a' already exists in object"
//       )
//     )
//   )
// )
modeJson.patch(modePatch, PatchMode.Lenient)
// res24: Either[SchemaError, Json] = Right(
//   Object(IndexedSeq(("a", Number(1))))
// )
modeJson.patch(modePatch, PatchMode.Clobber)
// res25: Either[SchemaError, Json] = Right(
//   Object(IndexedSeq(("a", Number(99))))
// )
```

## Operation Types

A `JsonPatch` is a sequence of `JsonPatchOp` values. Each `JsonPatchOp` pairs a `DynamicOptic` path with an `Op`:

```scala
final case class JsonPatchOp(path: DynamicOptic, operation: Op)
```

The full `Op` hierarchy covers five cases, from full replacement to fine-grained array and object edits:

```
Op (sealed trait)
 ├── Op.Set                — replace the target value entirely
 ├── Op.PrimitiveDelta     — numeric increment or string edit
 │    ├── PrimitiveOp.NumberDelta
 │    └── PrimitiveOp.StringEdit
 │         ├── StringOp.Insert
 │         ├── StringOp.Delete
 │         ├── StringOp.Append
 │         └── StringOp.Modify
 ├── Op.ArrayEdit          — insert / append / delete / modify array elements
 │    ├── ArrayOp.Insert
 │    ├── ArrayOp.Append
 │    ├── ArrayOp.Delete
 │    └── ArrayOp.Modify
 ├── Op.ObjectEdit         — add / remove / modify object fields
 │    ├── ObjectOp.Add
 │    ├── ObjectOp.Remove
 │    └── ObjectOp.Modify
 └── Op.Nested             — groups a sub-patch under a shared path prefix
```

### `Op.Set`

Replaces the target value with a new `Json` value, regardless of the current value. Works on any `Json` type:

```scala
final case class Set(value: Json) extends Op
```

`Op.Set` can replace across types — for example, replacing a number with a string, or resetting a nested field to `null`:

```scala

val setString  = JsonPatch.root(Op.Set(Json.String("replaced")))
val setNull    = JsonPatch(DynamicOptic.root.field("status"), Op.Set(Json.Null))
val withStatus = Json.Object("status" -> Json.String("active"), "id" -> Json.Number(1))
```

Both patches replace their target regardless of its current type:

```scala
setString.apply(Json.Number(123))
// res27: Either[SchemaError, Json] = Right(String("replaced"))
setNull.apply(withStatus)
// res28: Either[SchemaError, Json] = Right(
//   Object(IndexedSeq(("status", null), ("id", Number(1))))
// )
```

### `Op.PrimitiveDelta`

Applies a primitive mutation to a scalar value — either a numeric increment (`NumberDelta`) or a sequence of string edits (`StringEdit`):

```scala
final case class PrimitiveDelta(op: PrimitiveOp) extends Op
```

#### `PrimitiveOp.NumberDelta`

Adds `delta` to a `Json.Number`. Use a negative value to subtract. Fails if the target is not a `Json.Number`:

```scala
final case class NumberDelta(delta: BigDecimal) extends PrimitiveOp
```

Positive deltas increment; negative deltas decrement:

```scala

val inc = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
val dec = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(-3))))
```

```scala
inc.apply(Json.Number(10))
// res30: Either[SchemaError, Json] = Right(Number(15))
dec.apply(Json.Number(10))
// res31: Either[SchemaError, Json] = Right(Number(7))
```

#### `PrimitiveOp.StringEdit`

Applies a sequence of `StringOp` operations to a `Json.String`. `JsonPatch.diff` generates `StringEdit` automatically when it is more compact than a full `Set`:

```scala
final case class StringEdit(ops: Chunk[StringOp]) extends PrimitiveOp
```

The `StringOp` cases:

| Case | Parameters | Effect |
|------|-----------|--------|
| `StringOp.Insert(index, text)` | position, text | Inserts `text` before character `index` |
| `StringOp.Delete(index, length)` | position, count | Removes `length` characters starting at `index` |
| `StringOp.Append(text)` | text | Appends `text` to the end |
| `StringOp.Modify(index, length, text)` | position, count, text | Replaces `length` characters at `index` with `text` |

We can insert a prefix before the first character using `StringOp.Insert`:

```scala

val insertPatch = JsonPatch.root(
  Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Insert(0, "Hello, "))))
)
```

```scala
insertPatch.apply(Json.String("world"))
// res33: Either[SchemaError, Json] = Right(String("Hello, world"))
```

:::tip
For most use cases, let `JsonPatch.diff` generate `StringEdit` automatically. The diff algorithm uses an LCS (Longest Common Subsequence) comparison and only emits `StringEdit` when it produces fewer bytes than a plain `Set`.
:::

### `Op.ArrayEdit`

Applies a sequence of `ArrayOp` operations to a `Json.Array`. Operations are applied in order, and each one sees the result of the previous:

```scala
final case class ArrayEdit(ops: Chunk[ArrayOp]) extends Op
```

The `ArrayOp` cases:

| Case | Parameters | Effect |
|------|-----------|--------|
| `ArrayOp.Insert(index, values)` | position, elements | Inserts `values` before `index` |
| `ArrayOp.Append(values)` | elements | Appends `values` to the end |
| `ArrayOp.Delete(index, count)` | position, count | Removes `count` elements starting at `index` |
| `ArrayOp.Modify(index, op)` | position, op | Applies `op` to the element at `index` |

Multiple `ArrayOp`s in a single `ArrayEdit` can be combined — here we transform `[1, 2, 3]` into `[0, 1, 2, 4]` in one pass:

```scala

val arrayPatch = JsonPatch.root(Op.ArrayEdit(Chunk(
  ArrayOp.Insert(0, Chunk(Json.Number(0))),
  ArrayOp.Delete(3, 1),
  ArrayOp.Append(Chunk(Json.Number(4)))
)))
val originalArr = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
```

```scala
arrayPatch.apply(originalArr)
// res35: Either[SchemaError, Json] = Right(
//   Array(IndexedSeq(Number(0), Number(1), Number(2), Number(4)))
// )
```

:::note
Array indices in `ArrayOp.Delete` and `ArrayOp.Modify` refer to the state of the array **after** all preceding ops in the same `ArrayEdit` have been applied.
:::

### `Op.ObjectEdit`

Applies a sequence of `ObjectOp` operations to a `Json.Object`. Operations are applied in order:

```scala
final case class ObjectEdit(ops: Chunk[ObjectOp]) extends Op
```

The `ObjectOp` cases:

| Case | Parameters | Effect |
|------|-----------|--------|
| `ObjectOp.Add(key, value)` | field name, value | Adds a new field; fails in `Strict` mode if key exists |
| `ObjectOp.Remove(key)` | field name | Removes an existing field |
| `ObjectOp.Modify(key, patch)` | field name, sub-patch | Applies `patch` recursively to the field value |

A single `ObjectEdit` can add, remove, and modify fields in one operation:

```scala

val originalObj = Json.Object(
  "name"  -> Json.String("Alice"),
  "age"   -> Json.Number(25),
  "city"  -> Json.String("NYC")
)

val objPatch = JsonPatch.root(Op.ObjectEdit(Chunk(
  ObjectOp.Add("email", Json.String("alice@example.com")),
  ObjectOp.Remove("city"),
  ObjectOp.Modify("age", JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))))
)))
```

```scala
objPatch.apply(originalObj)
// res37: Either[SchemaError, Json] = Right(
//   Object(
//     IndexedSeq(
//       ("name", String("Alice")),
//       ("age", Number(26)),
//       ("email", String("alice@example.com"))
//     )
//   )
// )
```

### `Op.Nested`

Groups a sub-patch under a shared path prefix. `JsonPatch.diff` emits `Nested` automatically when multiple operations share a common navigation path — this avoids repeating the full path in each `JsonPatchOp`:

```scala
final case class Nested(patch: JsonPatch) extends Op
```

You rarely need to construct `Nested` manually; it is primarily an internal optimization used by the diff algorithm.

## Diffing Algorithm

`JsonPatch.diff` (and its alias `Json#diff`) delegate to [`JsonDiffer.diff`](./json-differ.md), which selects the most compact representation for each type of change:

| Value type | Change | Strategy |
|------------|--------|----------|
| Any | No change | No operation emitted |
| `Json.Number` | Value changed | `NumberDelta` — stores the numeric difference |
| `Json.String` | Value changed | `StringEdit` via LCS if smaller; otherwise `Set` |
| `Json.Array` | Elements changed | `ArrayEdit` with LCS-aligned `Insert`/`Delete`/`Append`/`Modify` |
| `Json.Object` | Fields changed | `ObjectEdit` with recursive per-field diff |
| Any | Type changed | `Set` — full replacement |

:::tip
`JsonPatch.diff` followed by `JsonPatch#apply` is always a lossless roundtrip: for any `source` and `target`, `JsonPatch.diff(source, target).apply(source) == Right(target)`.
:::

## JsonPatch vs RFC 6902 JSON Patch

ZIO Blocks' `JsonPatch` is **not** an implementation of [RFC 6902](https://datatracker.ietf.org/doc/html/rfc6902). The two share the same motivation but differ in design:

| | ZIO Blocks `JsonPatch` | RFC 6902 JSON Patch |
|---|---|---|
| Operations | Typed ADT (`Op.Set`, `Op.ArrayEdit`, …) | String-tagged JSON objects (`"op": "replace"`) |
| Paths | `DynamicOptic` (typed, composable) | JSON Pointer strings (`"/a/b/0"`) |
| Number changes | `NumberDelta` (stores diff) | `replace` (stores full new value) |
| String changes | LCS-based `StringEdit` | `replace` only |
| Array changes | LCS-aligned insert/delete | `add`, `remove`, `replace` at absolute indices |
| Serialization | Via ZIO Blocks `Schema` in any format | Always JSON |
| Composition | `++` operator | Array concatenation |

Use `JsonPatch` when working within ZIO Blocks. For interoperability with RFC 6902 tooling, convert the patch to JSON using the built-in `Schema` instances and reformat as needed.

## Advanced Usage

`JsonPatch`'s composability and first-class serializability unlock patterns beyond simple point-in-time updates.

### Building a Change Log

Because `JsonPatch` is a pure value with a `Schema`, we can serialize every change and replay or audit it later:

```scala

// Every mutation is a patch — store it instead of overwriting
val v0 = Json.Object("count" -> Json.Number(0))
val v1 = Json.Object("count" -> Json.Number(1))
val v2 = Json.Object("count" -> Json.Number(2))

val log: List[JsonPatch] = List(
  JsonPatch.diff(v0, v1),
  JsonPatch.diff(v1, v2)
)

// Replay: reconstruct any historical state
val replay = log.foldLeft(v0: Json)((state, patch) => patch.apply(state).getOrElse(state))
assert(replay == v2)
```

### Composing Targeted Sub-Patches

We can build a single patch that updates multiple nested fields by combining focused per-field patches with `++`:

```scala

def setField(field: String, value: Json): JsonPatch =
  JsonPatch(DynamicOptic.root.field(field), Op.Set(value))

val fieldPatch =
  setField("status", Json.String("active")) ++
  setField("updatedAt", Json.String("2025-01-01"))

val doc = Json.Object("status" -> Json.String("draft"), "id" -> Json.Number(42))
```

Applying the composed patch updates both fields in one step:

```scala
fieldPatch.apply(doc)
// res40: Either[SchemaError, Json] = Left(
//   SchemaError(
//     List(
//       MissingField(source = DynamicOptic(IndexedSeq()), fieldName = "updatedAt")
//     )
//   )
// )
```

## Integration

`JsonPatch` integrates with `Json`, `DynamicPatch`, and the ZIO Blocks serialization system. Each integration point is covered below.

### With `Json`

`Json` exposes two extension methods as entry points into `JsonPatch`:

```scala

val source = Json.Object("x" -> Json.Number(1))
val target = Json.Object("x" -> Json.Number(2))

val patch: JsonPatch  = source.diff(target)          // compute patch
val result            = source.patch(patch)          // apply (Strict)
val lenient           = source.patch(patch, PatchMode.Lenient)
```

See [Json](./json.md) for the complete `Json` API.

### With `DynamicPatch`

`JsonPatch` and `DynamicPatch` are bidirectionally convertible. This is useful when patches originate from the typed `Patch[S]` system and need to be applied to raw JSON:

```scala

val jsonPatch: JsonPatch                  = JsonPatch.diff(Json.Number(1), Json.Number(3))

// JsonPatch → DynamicPatch (always succeeds)
val dynPatch: DynamicPatch               = jsonPatch.toDynamicPatch

// DynamicPatch → JsonPatch (may fail for temporal ops or non-string keys)
val back: Either[SchemaError, JsonPatch] = JsonPatch.fromDynamicPatch(dynPatch)
```

See [Patching](./patch.md) for the typed `Patch[S]` API.

### Serialization

`JsonPatch` ships with `Schema` instances for all nested operation types, enabling round-trip serialization via any ZIO Blocks codec:

```scala

val schema: Schema[JsonPatch] = implicitly[Schema[JsonPatch]]
```

See [Codec & Format](./codec.md) for how to derive and use codecs.

## Examples

Runnable examples are in `schema-examples/src/main/scala/jsonpatch/`:

| File | Topic |
|------|-------|
| `JsonPatchDiffAndApplyExample.scala` | `JsonPatch.diff`, `Json#diff`, `Json#patch`, roundtrip guarantee |
| `JsonPatchManualBuildExample.scala` | `JsonPatch.root`, path-based patches, `JsonPatch.empty` |
| `JsonPatchOperationsExample.scala` | All `Op` types — `Set`, `NumberDelta`, `StringEdit`, `ArrayEdit`, `ObjectEdit` |
| `JsonPatchCompositionExample.scala` | `++`, `PatchMode`, `toDynamicPatch`, `fromDynamicPatch` |
| `CompleteJsonPatchExample.scala` | Collaborative document editor with a full patch log, replay, and sync |

Run any example with:

```bash
sbt "schema-examples/runMain jsonpatch.CompleteJsonPatchExample"
```
