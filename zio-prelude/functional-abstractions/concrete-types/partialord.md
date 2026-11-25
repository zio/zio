# PartialOrd

> `PartialOrd[A]` describes an ordering on values of type `A` in which some pairs of values may not be comparable at all. It is a weaker form of `Ord`, which requires every pair of values to be ordered.

Set inclusion is a common example. One set can contain another, or the two can be equal, but two sets can also overlap without either containing the other. Those two sets are not greater or less than each other, and `Ord` cannot express that. `PartialOrd` can.

`PartialOrd` extends [Equal](./equal.md), so every `PartialOrd` instance also answers whether two values are equal: they are equal exactly when `compare` returns `Ordering.Equals`. [Ord](./ord.md) in turn extends `PartialOrd`.

`compare` returns a `PartialOrdering`. The result is one of three orderings, `Ordering.LessThan`, `Ordering.Equals`, and `Ordering.GreaterThan`, or it is `PartialOrdering.Incomparable` when the two values cannot be compared.

## Motivation

Many real orderings are partial. A role with permissions `{read}` is below a role with `{read, write}`, but two roles with `{read}` and `{write}` are not ordered at all. Software versions with feature flags, dependency constraints, and records compared field by field with a "dominates in every field" rule behave the same way. Forcing such data into an `Ord` means inventing an answer for incomparable pairs, which hides the fact that no answer exists. With `PartialOrd`, the incomparable case is a value of the type, so the caller must handle it.

Use `PartialOrd` when some values have no meaningful order, and [Ord](./ord.md) when every pair is ordered. A good sign that `PartialOrd` is right is that the "ordering" you want is really an inclusion or dominance relation.

## Defining a PartialOrd

We create an instance with `PartialOrd.make`, which takes a function that returns a `PartialOrdering`. The following instance orders sets by inclusion:

```scala
import zio.prelude._

implicit val setInclusion: PartialOrd[Set[Int]] =
  PartialOrd.make { (left, right) =>
    if (left == right) Ordering.Equals
    else if (left.subsetOf(right)) Ordering.LessThan
    else if (right.subsetOf(left)) Ordering.GreaterThan
    else PartialOrdering.Incomparable
  }
```

Comparing a set with a set it is contained in gives `LessThan`, and comparing two sets that overlap without either containing the other gives `Incomparable`:

```scala
PartialOrd[Set[Int]].compare(Set(1), Set(1, 2))
// res1: PartialOrdering = LessThan

PartialOrd[Set[Int]].compare(Set(1, 2), Set(2, 3))
// res2: PartialOrdering = Incomparable
```

## Comparing Roles

An ordering for your own type is usually built from an existing one. `PartialOrd#contramap` takes a function from the new type to the old one, which makes a role as ordered as its permissions:

```scala
final case class Role(name: String, permissions: Set[Int])

val roleOrd: PartialOrd[Role] =
  PartialOrd[Set[Int]].contramap(_.permissions)

val viewer = Role("viewer", Set(1))
val editor = Role("editor", Set(1, 2))
val auditor = Role("auditor", Set(1, 3))
```

Next, we compare the roles. An editor includes a viewer, and an editor and an auditor each have a permission the other lacks:

```scala
roleOrd.compare(viewer, editor)
// res3: PartialOrdering = LessThan

roleOrd.compare(editor, auditor)
// res4: PartialOrdering = Incomparable

roleOrd.lessOrEqual(viewer, editor)
// res5: Boolean = true
```

`PartialOrd#less`, `PartialOrd#lessOrEqual`, `PartialOrd#greater` and `PartialOrd#greaterOrEqual` return `false` for incomparable values, so `less(a, b)` being `false` does not mean that `greaterOrEqual(a, b)` is `true`.

## Building Instances From Other Instances

`PartialOrd` provides combinators that build an instance for a pair of values from instances for each component. `both` compares the first components of two pairs, and only if they are equal does it compare the second components. `bothWith` does the same after converting a value with a function, which lets us describe an ordering for a case class in terms of its fields.

## Laws

Every `PartialOrd` instance must satisfy the laws of [Equal](./equal.md) and the following, defined in `PartialOrdLaws` in the `zio-laws-laws` module:

| Law              | Statement                                                                        |
| ---------------- | -------------------------------------------------------------------------------- |
| Transitivity     | If `a1 < a2` and `a2 < a3`, then `a1 < a3`, and likewise for `>`                 |
| Antisymmetry     | If `a1 <= a2` and `a2 <= a1`, then `a1 === a2`, and likewise for `>=`            |
| Eq consistency   | `a1 =??= a2` is `Ordering.Equals` exactly when `a1 === a2`                       |

To check an instance in a test, pass `PartialOrdLaws` and a generator to `checkAllLaws`:

```scala
import zio.prelude.laws._
import zio.test._
import zio.test.laws._

object RoleOrdSpec extends ZIOSpecDefault {
  def spec =
    suite("role ordering")(
      test("laws")(checkAllLaws(PartialOrdLaws)(Gen.setOf(Gen.int)))
    )
}
```

## Related Abstractions

[Ord](./ord.md) is the total version of `PartialOrd`. [Equal](./equal.md) is its parent, and [Hash](./hash.md) pairs an `Equal` with a hash code. `PartialOrd` has a `Contravariant` instance, so an ordering for one type gives an ordering for any type that can be converted to it.
