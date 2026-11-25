# Id and Const

> `Id` and `Const` are two new types that give simple, effect-free meaning to the type parameters of functional abstractions. Most code does not use them directly. They appear when a function needs a type constructor, such as a `ForEach` operator, and the effect it uses should be trivial.

Both exist because [ForEach](../functional-abstractions/parameterized-types/foreach.md) has a single general traversal operator, `ForEach#forEach`, that takes the effect to run as a type parameter. Choosing `Id` as the effect makes the traversal a plain transformation, and choosing `Const` makes it a summary of the elements. The library itself defines `ForEach#map` with `Id` and `ForEach#foldMap` with `Const`.

We import both from the `zio.prelude` package:

```scala
import zio.prelude._
```

## Id

`Id[A]` is a wrapper around `A` with no effect. It is the type constructor you get when you want an operator that expects an effect, but the effect should do nothing special.

Because `Id` has all the instances that such operators need, we can use it with `ForEach` to apply a function to each element of a `List` and get the result back as an `Id`:

```scala
val doubled: Id[List[Int]] =
  ForEach[List].forEach(List(1, 2, 3))(a => Id(a * 2))
// doubled: Id[List[Int]] = List(2, 4, 6)
```

`Id.unwrap` returns the plain `List` inside the wrapper:

```scala
Id.unwrap(doubled)
// res1: List[Int] = List(2, 4, 6)
```

The wrapper adds nothing at run time. `Id` is a newtype, so its values are the same as the underlying `A` values.

Reach for `Id` when you write a generic function that is parameterized by an effect type `F[_]` and you want to call it with no effect, for example in a test or in a pure code path. It has `ForEach`, `IdentityBoth`, `IdentityFlatten` and `CommutativeBoth` instances, among others.

## Const

`Const[A, B]` holds a value of type `A` and ignores the second type parameter `B`. The `B` parameter is a phantom type, which means it does not appear in the value. It only appears in the type, so that `Const` can fit a position that expects a type constructor with one parameter.

Because `B` never appears in a value, the same `Const` value can stand in for a `Const` of any `B`. To construct a value, we call `Const.apply`:

```scala
val label: Const[String, Int] =
  Const("name")
// label: Const[String, Int] = "name"

Const.unwrap(label)
// res2: String = "name"
```

Like `Id`, `Const` is a newtype, so it adds no allocation at run time. Its `A` value is the underlying value.

The useful property of `Const[A, B]` is how it combines. When `A` has an `Identity` instance, `Const[A, _]` has an `IdentityBoth` instance that combines the two `A` values and ignores the phantom parameter. A traversal that uses `Const` as its effect therefore collects one `A` per element and combines them, which is exactly a fold. `ForEach#foldMap` is written this way, so we can see the same result from the public operator and from `Const` directly:

```scala
import zio.prelude.newtypes.Sum

type Total[+A] = Const[Sum[Int], A]
```

The two calls below add up the lengths of the words, first with `ForEach#foldMap` and then by traversing with `Const` as the effect:

```scala
ForEach[List].foldMap(List("a", "bb", "ccc"))(word => Sum(word.length))
// res3: Sum.Type[Int] = 6

Const.unwrap(ForEach[List].forEach[Total, String, Int](List("a", "bb", "ccc"))(word => Const(Sum(word.length))))
// res4: Sum[Int] = 6
```

## Related Abstractions

[ForEach](../functional-abstractions/parameterized-types/foreach.md) is where `Id` and `Const` are used. [Identity](../functional-abstractions/concrete-types/identity.md) is the type class a `Const` needs on its first parameter to be combined, and [IdentityBoth](../functional-abstractions/parameterized-types/identityboth.md) is the abstraction that `Const` provides an instance of. For the general mechanism behind both types, see [Newtypes](../newtypes/index.md).
