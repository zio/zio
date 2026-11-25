# Patch and Differ

> Reference for the Differ and Patch types that compute and apply changes between two values.

`Differ[A]` computes the difference between two values of type `A`, and `Patch[A]` describes that difference so it can be applied to an old value. Most code uses `Schema#diff` and `Schema#patch`, which are described in [Diffing and Patching](../operations/diffing-and-patching.md). This page covers the types themselves, for code that builds or combines patches directly.

## Differ

A `Differ[A]` has one abstract method, `Differ#apply`, which takes the old and new values and returns a `Patch[A]`. `Schema#diff` builds its differ from the schema through `Differ.fromSchema`. The `Differ` trait also has combinators:

- `Differ#zip`, written `<*>`, combines two differs into one for a pair.
- `Differ#transform` adapts a differ to a related type, given conversions in both directions.
- `Differ#transformOrFail` does the same with conversions that can fail. When a value cannot be converted, the resulting patch is not comparable.
- `Differ#chunk` returns a differ for `Chunk[A]`, which uses a longest common subsequence algorithm to find inserted and removed elements.
- `Differ#optional` returns a differ for `Option[A]`.

The `Differ` companion object also provides ready-made differs, such as `Differ.either`, `Differ.record` for a `GenericRecord`, `Differ.enumN` for the cases of an enumeration, and one differ for each standard type.

The following example diffs two chunks. The patch records that the element `2` is removed, and applying it to the old chunk gives the new one:

```scala
import zio.Chunk
import zio.schema._

val listDiffer: Differ[Chunk[Int]] = Differ.fromSchema(Schema[Int]).chunk
```

Calling the differ on the old and new chunks returns the patch, which we then apply to the old chunk:

```scala
val patch = listDiffer(Chunk(1, 2, 3), Chunk(1, 3))
// patch: Patch[Chunk[Int]] = LCS(
//   edits = IndexedSeq(Keep(value = 1), Delete(value = 2), Keep(value = 3))
// )

patch.patch(Chunk(1, 2, 3))
// res0: Either[String, Chunk[Int]] = Right(value = IndexedSeq(1, 3))
```

## Patch

A `Patch[A]` is a sealed trait with these operations:

- `Patch#patch` applies the patch to a value and returns `Either[String, A]`.
- `Patch#invert` returns a patch that goes in the opposite direction. For most patches, applying the inverse to the new value gives the old value. The exceptions are listed below.
- `Patch#zip`, written `<*>`, combines a patch for `A` with a patch for `B` into one for `(A, B)`.
- `Patch#isIdentical` is `true` when the patch changes nothing.
- `Patch#isComparable` is `false` for the patch returned by `Patch.notComparable`, which reports that two values cannot be compared, and for a record, `Either`, `Fallback`, or transform patch that contains one. A `Patch.Tuple` always reports `true`, even when one side is not comparable, so applying it can still fail.

The `Patch` companion object provides `Patch.identical` and `Patch.notComparable`, which build the two special patches. Use them when you write your own differ and need to report no change or an incomparable pair.

The following example shows the inverse. Applying the inverted patch to the newer value restores the older one:

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val john  = Person("John", 42)
val older = john.copy(age = 43)
```

The forward patch turns `john` into `older`:

```scala
val forward = Person.schema.diff(john, older)
```

Applying its inverse to `older` gives back `john`:

```scala
Person.schema.patch(older, forward.invert) == Right(john)
// res1: Boolean = true
```

Comparing a value with itself gives an identical patch:

```scala
Person.schema.diff(john, john).isIdentical
// res2: Boolean = true

Patch.identical[Person].isIdentical
// res3: Boolean = true
```

## Patches That Cannot Be Inverted

`Patch#invert` cannot restore the old value when the patch does not record it. This is the case for the following patches:

- `Patch.Total` replaces the whole value with a new one. The differ for `Option` produces it for a change between `Some` and `None`, and its inverse is the same patch.
- `Patch.NotComparable` reports two values that cannot be compared, and its inverse is itself. Applying it always fails.
- `Patch.SchemaMigration` describes a change between two schemas. Applying it always returns `Left`.

:::caution
`Differ.currency` produces a `Patch.Currency` whose `Patch#patch` method returns its input unchanged, so the change is lost. `Schema#diff` is not affected, because the differ that `Differ.fromSchema` builds for a `Currency` field compares currency codes as strings. Use `Differ.fromSchema(Schema[java.util.Currency])` instead of `Differ.currency` when you need a differ for currencies.
:::

## Writing Patches by Hand

Patches are data, so they can be built without a differ. Every case of `Patch` is a public case class in the `Patch` companion object, such as `Patch.Total`, `Patch.Number`, `Patch.LCS`, and `Patch.Record`. The `Patch.identical` and `Patch.notComparable` helpers build the two special patches. The following patch sets an integer to `30`, whatever its old value:

```scala
val setTo30: Patch[Int] = Patch.Total(30)
// setTo30: Patch[Int] = Total(value = 30)

setTo30.patch(42)
// res4: Either[String, Int] = Right(value = 30)
```

For a custom type, write a `Differ` with `Differ.instancePartial`, which returns `Patch.notComparable` for the pairs that its partial function does not cover, or start from `Differ.identical` when no change is possible.
