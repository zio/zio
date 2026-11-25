# Derive

> `Derive[F, Typeclass]` is a recipe for building a type class instance for a container type from an instance for its elements. The type constructor `F` is the container, such as `List`, and `Typeclass` is the type class, such as `Equal`.

For example, if we know how to compare two `Int` values for equality, we can compare two `List[Int]` values by checking that they have the same length and that each pair of corresponding elements is equal. That rule works for any element type that has an `Equal` instance, so it can be written once as a `Derive` instance.

The library provides `Derive` instances for `List`, `Chunk`, `Option`, `Either`, `Map`, `Set`, tuples, and several other containers, so we rarely need to write one ourselves. The implicit `Equal.DeriveEqual` method then turns any `Derive[F, Equal]` together with an `Equal[A]` into an `Equal[F[A]]`, which is how those instances are found without extra imports. `Hash`, `Ord`, `PartialOrd`, `Idempotent` and `Inverse` have the same kind of bridge, each defined in the companion object of the type class.

## Motivation

A type class such as `Equal[A]` describes one concrete type. A container such as `Box[A]` needs an `Equal[Box[A]]` for every `A` that has an `Equal`, and that rule is the same for all element types. Without `Derive`, we write `implicit def boxEqual[A: Equal]: Equal[Box[A]]` once for each type class we want. With `Derive`, the rule lives in a value of type `Derive[Box, Equal]`, and anything that needs to abstract over "a container whose elements have this type class" can ask for that value instead of a specific instance.

The most common reason to write your own is to make a container of your own work with `===` and with generic code that requires `Equal[F[A]]`.

## Type Aliases

For convenience, `zio.prelude` defines aliases for the common cases: `DeriveAssociative`, `DeriveCommutative`, `DeriveDebug`, `DeriveEqual`, `DeriveHash`, `DeriveIdentity`, `DeriveInverse` and `DeriveOrd`. Each alias fixes the second type parameter, so `DeriveEqual[List]` means `Derive[List, Equal]`.

## Using a Derive Instance

`Derive.apply` summons the derive instance for a container and a type class. Its `derive` method takes an instance for the element type and returns an instance for the container:

```scala
import zio.prelude._
```

Next, we compare two lists of integers, first with equal contents and then with different contents:

```scala
Derive[List, Equal].derive[Int].equal(List(1, 2), List(1, 2))
// res1: Boolean = true

Derive[List, Equal].derive[Int].equal(List(1, 2), List(2, 1))
// res2: Boolean = false
```

The same instance compares lists of any element type that has an `Equal` instance, such as strings:

```scala
Derive[List, Equal].derive[String].equal(List("a"), List("a"))
// res3: Boolean = true
```

## Defining Your Own Derive Instance

A derive instance is a value of type `Derive[F, Typeclass]` with a `derive` method. It is useful for a container that you define yourself, when you want every instance of the container to follow the same rule. The rule should be written once in terms of the element instance, and it must be valid for every element type the type class applies to.

To make a `Box` of any comparable value comparable, we write one `DeriveEqual[Box]`. It builds an `Equal[Box[A]]` from the `Equal[A]` by comparing the contents:

```scala
final case class Box[A](value: A)

implicit val boxDeriveEqual: DeriveEqual[Box] =
  new DeriveEqual[Box] {
    def derive[A: Equal]: Equal[Box[A]] =
      Equal[A].contramap(_.value)
  }
```

Next, the `===` operator works on boxes of any element type that has an `Equal`, without writing an instance for each:

```scala
Box(1) === Box(1)
// res4: Boolean = true

Box("a") === Box("b")
// res5: Boolean = false

Box(List(1, 2)) === Box(List(1, 2))
// res6: Boolean = true
```

## Laws

`Derive` has no laws of its own. The instance it produces must satisfy the laws of the type class it derives: an `Equal[Box[A]]` built by `derive` must be reflexive, symmetric and transitive, and so on. The laws module checks those through the type class, for example with `EqualLaws`, using generated values of `Box[A]`.

## Related Abstractions

[Equal](../concrete-types/equal.md), [Hash](../concrete-types/hash.md) and [Ord](../concrete-types/ord.md) are the type classes most often derived. Where a container should also support mapping, see [Covariant](./covariant.md); the `coherent` package of the library combines `Covariant` and `Derive` for types that have both.
