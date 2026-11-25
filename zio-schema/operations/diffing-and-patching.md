# Diffing and Patching

> A schema can compute the difference between two values of its type and apply that difference to a value later. `Schema#diff` and `Schema#patch` provide these two operations:

```scala
sealed trait Schema[A] {
  def diff(thisValue: A, thatValue: A): Patch[A]

  def patch(oldValue: A, diff: Patch[A]): scala.util.Either[String, A]
}
```

`Schema#diff` takes two values of the same type `A` and returns a `Patch[A]` value that describes the differences between the two values. Conversely, `Schema#patch` takes a value of type `A` and a `Patch[A]` value and returns a new value of type `A` that is the result of applying the patch to the original value. The [Patch and Differ](../reference/patch-and-differ.md) reference page describes the `Patch` and `Differ` types in detail.

## Diffing Two Records

The following example computes the difference between two versions of a person and applies it to the older version:

```scala
import zio.schema._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
}

val oldValue = Person("John", 42)
val newValue = Person("John", 43)

val personPatch: Patch[Person] = Person.schema.diff(oldValue, newValue)
```

Applying the patch to the old value gives the new value:

```scala
Person.schema.patch(oldValue, personPatch) == Right(newValue)
// res0: Boolean = true
```

A record patch holds one entry for every field, and the entry for an unchanged field is an identical patch. Applying the patch converts the whole record through `DynamicValue`, so the cost grows with the size of the record, not with the number of changed fields.

The `zio.schema.syntax` object adds the same operations as methods on any value that has an implicit schema. `DiffOps#diffEach` is an alias of `DiffOps#diff` that does not clash with `Seq#diff` from the Scala collections:

```scala
import zio.schema.syntax._

oldValue.runPatch(oldValue.diffEach(newValue))
// res1: Either[String, Person] = Right(
//   value = Person(name = "John", age = 43)
// )
```

## Changes That Produce a Failing Patch

Some pairs of values cannot be described as a change of one into the other. For these pairs, `Schema#diff` returns a patch whose `Patch#isComparable` method is `false`, and applying that patch always returns `Left`. This happens when:

- Two values of a sealed trait or enumeration are different cases.
- One `Either` is a `Left` and the other is a `Right`.
- The values are `DynamicValue`s, which are never compared.
- A `Schema#transformOrFail` schema cannot convert one of the values back to its underlying type.

When such a pair appears inside a record, for example as one field of a case class, the record patch contains the failing entry, so applying the whole patch fails. The following sealed trait shows the first case:

```scala
sealed trait Shape

object Shape {
  final case class Circle(radius: Double) extends Shape
  final case class Square(side: Double)   extends Shape

  implicit val schema: Schema[Shape] = DeriveSchema.gen[Shape]
}

val circle: Shape = Shape.Circle(1.0)
val square: Shape = Shape.Square(2.0)

val shapePatch: Patch[Shape] = Shape.schema.diff(circle, square)
```

The patch is not comparable, and applying it fails:

```scala
shapePatch.isComparable
// res2: Boolean = false

Shape.schema.patch(circle, shapePatch)
// res3: Either[String, Shape] = Left(
//   value = "Non-comparable diff cannot be applied"
// )
```

Check `Patch#isComparable` before you store or send a patch. When it is `false`, keep the new value itself instead of the patch.

## Optional Values

A change between `Some` and `None`, in either direction, produces a patch that replaces the whole value. Applying it always gives the new value, whatever the input:

```scala
val optionPatch = Schema[Option[Int]].diff(Some(1), None)
// optionPatch: Patch[Option[Int]] = Total(value = None)

Schema[Option[Int]].patch(Some(5), optionPatch)
// res4: Either[String, Option[Int]] = Right(value = None)
```

A change between two `Some` values diffs the inner values as usual. Inverting a `Some`-to-`None` patch does not restore the old value, as [Patches That Cannot Be Inverted](../reference/patch-and-differ.md#patches-that-cannot-be-inverted) explains.
