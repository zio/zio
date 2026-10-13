# ReflectTransformer

> `ReflectTransformer[F, G]` rewrites a `Reflect[F, A]` tree into a `Reflect[G, A]` tree, changing the binding-metadata type constructor from `F` to `G` at every node while leaving the shape — field names, type IDs, docs, modifiers, defaults, examples — untouched. `Reflect#transform` walks the tree and recurses into fields, cases, elements, and keys/values for you; a `ReflectTransformer` only has to say how to rebuild the metadata-bearing leaf of each node kind once its children are already transformed.

Three things in the codebase are built on it: stripping bindings to serialize a [`Schema`](./schema.md) as a [`DynamicSchema`](./dynamic-schema.md), attaching bindings back with `RebindTransformer`, and [type class derivation](./type-class-derivation.md), which pairs each node's binding with a derived instance. This page covers all three, plus `OnlyMetadata`, the base class most transformers use instead of implementing the full interface.

## Design & Structure

`ReflectTransformer` declares one method per [`Reflect`](./reflect.md) node kind — seven of the eight; `Deferred` is handled separately (see [Cycle Safety](#cycle-safety-and-the-transform-cache)) because it has no metadata of its own to transform. `transformRecord` is representative of all seven — each takes the node's already-transformed children plus its untouched shape fields, and returns a `Lazy` of the rebuilt node:

```scala
trait ReflectTransformer[-F[_, _], G[_, _]] {
  def transformRecord[A](
    path: DynamicOptic,
    fields: IndexedSeq[Term[G, A, ?]],
    typeId: TypeId[A],
    metadata: F[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Record[G, A]]

  // ...one more method per remaining node kind, same shape
}
```

The other six follow the identical pattern — a `path`, the already-transformed children, the shape fields, and the still-untransformed `metadata: F[...]` — differing only in which children they carry and which `Reflect` node they return:

| Node kind | Method | Transformed children it receives |
| ---------- | -------- | ----------------------------------- |
| `Record`     | `transformRecord`    | `fields: IndexedSeq[Term[G, A, ?]]` |
| `Variant`    | `transformVariant`   | `cases: IndexedSeq[Term[G, A, ? <: A]]` |
| `Sequence`   | `transformSequence`  | `element: Reflect[G, A]` |
| `Map`        | `transformMap`       | `key: Reflect[G, Key]`, `value: Reflect[G, Value]` |
| `Dynamic`    | `transformDynamic`   | none — `DynamicValue` has no children |
| `Primitive`  | `transformPrimitive` | none |
| `Wrapper`    | `transformWrapper`   | `wrapped: Reflect[G, B]` |

Every result is wrapped in [`Lazy`](./lazy.md) because a transformer built for a recursive schema must be able to build a node before its own children are fully forced.

## `OnlyMetadata` — The Common Case

Most transformers only need to change the metadata and leave every field, case, and element exactly as it already was. `ReflectTransformer.OnlyMetadata[F, G]` implements all seven `transformX` methods for you in terms of one method:

```scala
abstract class OnlyMetadata[F[_, _], G[_, _]] extends ReflectTransformer[F, G] {
  def transformMetadata[K, A](f: F[K, A]): Lazy[G[K, A]]
}
```

Each `transformX` implementation `OnlyMetadata` provides is one line: transform the metadata, then rebuild the same node with the same children and shape, swapping in the new metadata. `HasBinding[F]` — the type class that extracts a `Binding[T, A]` out of an arbitrary `F[T, A]` — is itself a `ReflectTransformer.OnlyMetadata[F, Binding]` whose `transformMetadata` calls the one method a `HasBinding[F]` instance supplies.

## Stripping Bindings: `ReflectTransformer.noBinding`

`ReflectTransformer.noBinding[F]()` is a predefined, stateless transformer that discards whatever metadata `F` holds and replaces it with the singleton `NoBinding` value, regardless of what `F` was. It is what `Reflect#noBinding` and, through it, `Schema#toDynamicSchema` use to turn a bound, operational schema into a pure-data one safe to serialize:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Reflect.Bound[Person] -> Reflect.Unbound[Person], via ReflectTransformer.noBinding()
val unbound: Reflect.Unbound[Person] = Person.schema.reflect.noBinding

// The public entry point most code uses instead
val dynamicSchema: DynamicSchema = Person.schema.toDynamicSchema
```

Because `ReflectTransformer.noBinding` never inspects the metadata it's replacing, one singleton instance (cast per call) serves every `F`, with no allocation per use.

## Attaching Bindings: `RebindTransformer`

`RebindTransformer` is the transformer in the opposite direction — `NoBinding` to `Binding` — that [`DynamicSchema#rebind`](./dynamic-schema.md) constructs internally to turn a deserialized, pure-data schema back into an operational one. It is `private[schema]`; the public surface is `DynamicSchema#rebind`, which builds one for you from a [`BindingResolver`](./binding-resolver.md):

```scala
import zio.blocks.schema._
import zio.blocks.schema.binding._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val dynamicSchema: DynamicSchema = Person.schema.toDynamicSchema

val resolver = BindingResolver.defaults.bind(Binding.of[Person])
val rebound: Schema[Person] = dynamicSchema.rebind[Person](resolver)
```

Unlike `ReflectTransformer.noBinding`, `RebindTransformer` implements every `transformX` method individually rather than extending `OnlyMetadata` — each one must call a different resolver lookup (`resolveRecord`, `resolveVariant`, `resolveSeqFor`, `resolveMapFor`, `resolveDynamic`, `resolvePrimitive`, `resolveWrapper`) and fail in a way that names which kind of binding was missing.

### `RebindException`

A resolver that's missing a binding for some type in the tree makes `DynamicSchema#rebind` throw `RebindException` rather than return quietly, since a `Schema` with an unresolved node isn't safe to hand back:

```scala
import zio.blocks.schema._
import zio.blocks.schema.binding._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val dynamicSchema = Person.schema.toDynamicSchema

try dynamicSchema.rebind[Person](BindingResolver.empty)
catch {
  case e: RebindException =>
    // path: the DynamicOptic where the lookup failed
    // typeId: the missing type
    // expectedKind: "Record", "Variant", "Sequence", "Map", "Dynamic", "Primitive", or "Wrapper"
    println(s"${e.expectedKind} binding missing for ${e.typeId.fullName} at ${e.path}")
}
```

## Writing a Full `ReflectTransformer`: Type Class Derivation

`OnlyMetadata` covers every transformer described so far, but the interface exists in full for a reason: automatic type class derivation implements every `transformX` method directly, because deriving an instance for a `Record` needs the already-derived instances of its *fields*, not just a metadata swap. `DerivationBuilder.derive` walks a bound schema with a custom `ReflectTransformer[Binding, BindingInstance[TC, ?, ?]]` that, at each node, either substitutes a user-supplied override or invokes a [`Deriver[TC]`](./type-class-derivation.md) with the node's already-transformed children, then pairs the result with the original binding. This is the pattern to reach for when a transformation genuinely depends on more than the metadata at a single node.

## Cycle Safety and the Transform Cache

A recursive schema — one whose `Reflect` tree refers back to itself — reaches that self-reference through a `Reflect.Deferred` node. `Deferred#transform` doesn't call any `transformX` method; it looks up the pair `(this Deferred, this transformer)` in a per-thread memoization map first, and only builds a new deferred node on a miss. This is what makes `Reflect#transform` terminate on a recursive schema, and what lets a `Deferred` reached through multiple paths share one transformed result instead of being rebuilt repeatedly.

The two public entry points — `Reflect#noBinding` and `DynamicSchema#rebind` — wrap their call in an internal scope that clears this cache once the outermost `Reflect#transform` finishes, so a transformer's captured state (override maps, resolvers) doesn't stay pinned on the thread once its call returns. Reaching either entry point is enough to get this for free; it isn't something you configure.

## See Also

- [Reflect](./reflect.md) — the tree `ReflectTransformer` rewrites, and its eight node kinds
- [Binding](./binding.md) — what `F`/`G` are in practice: `Binding` and `NoBinding`
- [BindingResolver](./binding-resolver.md) — the lookup `RebindTransformer` delegates to
- [DynamicSchema](./dynamic-schema.md) — `Schema#toDynamicSchema` and `DynamicSchema#rebind`, the public entry points
- [Type Class Derivation](./type-class-derivation.md) — the fullest real use of the interface
