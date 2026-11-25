# Syntax and Instances

> A single wildcard import, `import zio.prelude._`, gives access to every abstraction in ZIO Prelude, the infix operators and extension methods that go with them, a set of type aliases, and the instances for the standard library and ZIO types. This page is a reference for what that import provides and which standard types have instances of which abstractions.

## What the Import Brings Into Scope

The `zio.prelude` package object extends one `*Syntax` trait per abstraction. Each trait contributes implicit classes whose methods work for any type that has an instance of the corresponding abstraction, so a method such as `Covariant`'s `map` is available on values of `F[A]` only when an implicit `Covariant[F]` exists. Methods that a type already defines itself, such as `List#map`, always take precedence over the implicit syntax.

To check the syntax in action, we import the package once and use a few operators from different abstractions:

```scala
import zio.prelude._

Option(1).zipWith(Option(2))(_ + _)
// res0: Option[Int] = Some(value = 3)

List(1, 2) <*> List("a", "b")
// res1: List[(Int, String)] = List((1, "a"), (1, "b"), (2, "a"), (2, "b"))

"zio" === "zio"
// res2: Boolean = true
```

### Syntax by Abstraction

The following table lists the operators and methods that each syntax trait adds. The first column links to the page of the abstraction.

| Abstraction                                                                         | Syntax added by the import                                                                                                                          |
| ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Associative](./functional-abstractions/concrete-types/associative.md)              | `<>`, `combine`, `repeat`, `multiplyOption`                                                                                                         |
| [Identity](./functional-abstractions/concrete-types/identity.md)                    | `identity`                                                                                                                                          |
| [Inverse](./functional-abstractions/concrete-types/inverse.md)                      | `~~`, `inverse`, `multiplyBy`                                                                                                                       |
| [PartialInverse](./functional-abstractions/concrete-types/partialinverse.md)        | `~?~`, `inverseOption`                                                                                                                              |
| [Equal](./functional-abstractions/concrete-types/equal.md)                          | `===`, `!==`                                                                                                                                        |
| [Hash](./functional-abstractions/concrete-types/hash.md)                            | `hash`, `##`                                                                                                                                        |
| [Ord](./functional-abstractions/concrete-types/ord.md)                              | `=?=`, `max`, `min`                                                                                                                                 |
| [PartialOrd](./functional-abstractions/concrete-types/partialord.md)                | `>`, `>=`, `<`, `<=`, `=??=`                                                                                                                        |
| [Debug](./functional-abstractions/concrete-types/debug.md)                          | `debug`, `render`, and the `d` string interpolator                                                                                                  |
| [Invariant](./functional-abstractions/parameterized-types/invariant.md)             | `invmap`                                                                                                                                            |
| [Covariant](./functional-abstractions/parameterized-types/covariant.md)             | `map`, `as`, `fproduct`, `fproductLeft`, `unit`                                                                                                     |
| [Contravariant](./functional-abstractions/parameterized-types/contravariant.md)     | `contramap`                                                                                                                                         |
| [Bicovariant](./functional-abstractions/parameterized-types/bicovariant.md)         | `bimap`, `leftMap`, `rightMap`                                                                                                                      |
| [Divariant](./functional-abstractions/parameterized-types/divariant.md)             | `dimap`, `leftMap`, `rightMap`                                                                                                                      |
| [Zivariant](./functional-abstractions/parameterized-types/zivariant.md)             | `zimap`, `contramap`, `mapLeft`, `map`, `bimap`, `dimap`                                                                                            |
| [AssociativeBoth](./functional-abstractions/parameterized-types/associativeboth.md) | `<*>`, `zip`, `<*`, `*>`, `zipLeft`, `zipRight`, `zipWith`, `forever`, `bothWith`, and `mapN` and `tupleN` on tuples of up to 22 elements           |
| [CommutativeBoth](./functional-abstractions/parameterized-types/commutativeboth.md) | `<&>`, `zipPar`, `zipWithPar`, `bothWithPar`, and `mapParN` and `tupledPar` on tuples of up to 22 elements                                          |
| [IdentityBoth](./functional-abstractions/parameterized-types/identityboth.md)       | `succeed`                                                                                                                                           |
| [AssociativeEither](./functional-abstractions/parameterized-types/associativeeither.md) | `<+>`, `orElseEither`, `orElse`, `eventually`, `eitherWith`                                                                                     |
| [CommutativeEither](./functional-abstractions/parameterized-types/commutativeeither.md) | `<\|>`, `orElseEitherPar`, `orElsePar`, `eitherWithPar`                                                                                         |
| [IdentityEither](./functional-abstractions/parameterized-types/identityeither.md)   | `fail`                                                                                                                                              |
| [AssociativeFlatten](./functional-abstractions/parameterized-types/associativeflatten.md) | `flatten`, `flatMap`                                                                                                                          |
| [IdentityFlatten](./functional-abstractions/parameterized-types/identityflatten.md) | `when`, `unless`                                                                                                                                    |
| [AssociativeCompose](./functional-abstractions/parameterized-types/associativecompose.md) | `>>>`, `andThen`, `<<<`, `compose`                                                                                                            |
| [ForEach](./functional-abstractions/parameterized-types/foreach.md)                 | `forEach`, `forEach_`, `forEachFlatten`, `collect`, `collectM`, `filter`, `filterM`, `fold`, `foldLeft`, `foldRight`, `foldMap`, `find`, `exists`, `forall`, `count`, `size`, `isEmpty`, `nonEmpty`, `contains`, `sum`, `product`, `reverse`, `intersperse`, `zipWithIndex`, `zipAll`, `partitionMap`, `groupByNonEmpty`, `maxOption`, `minOption`, `toChunk`, `flip`, and more |
| [NonEmptyForEach](./functional-abstractions/parameterized-types/nonemptyforeach.md) | `forEach1`, `forEach1_`, `reduce1`, `reduceAll`, `reduceMap`, `reduceMapLeft`, `reduceMapRight`, `toNonEmptyChunk`, `toNonEmptyList`, `flip1`      |

The syntax is also available for specific data types: `toNonEmptyList` on `List` values, `toNonEmptySet` on iterables, `toMultiSet` on a `Map[A, Natural]`, `head`, `peel` and `tail` on `MultiSet` values, `toNonEmptyMultiSetOption` on a `Map[A, Natural]`, and `head`, `peel`, `peelNonEmpty`, `tail` and `tailNonEmpty` on `NonEmptyMultiSet` values.

### Type Aliases and Values

Besides syntax, the package object defines aliases and values that show up in signatures throughout the library:

| Name                                               | Definition                                                                       |
| -------------------------------------------------- | -------------------------------------------------------------------------------- |
| `A <=> B`                                          | `Equivalence[A, B]`                                                              |
| `Validation[E, A]`                                 | `ZValidation[Nothing, E, A]`, with the `Validation` companion for construction   |
| `State`, `EState`, `Reader`, `EReader`, `Writer`, `EWriter` | Aliases of [`ZPure`](./zpure/index.md) with the matching parameters fixed       |
| `MultiSet[A]`, `NonEmptyMultiSet[A]`               | `ZSet[A, Natural]` and `ZNonEmptySet[A, Natural]`                                |
| `AnyF[_]`                                          | A type constructor that ignores its argument and is always `Any`                 |
| `DeriveEqual[F]`, `DeriveHash[F]`, and so on       | `Derive[F, Equal]`, `Derive[F, Hash]`, for `Associative`, `Commutative`, `Debug`, `Identity`, `Inverse` and `Ord` too |
| `Id[A]` and `Const[A, B]`                          | Parameterized newtypes of the identity and the constant functor                  |
| `NewtypeF`                                         | Base class for parameterized newtypes, with `wrapAll` and `unwrapAll`            |

The `zio.prelude.classic` object contains aliases that map the library's fine-grained abstractions to the traditional names. We import it explicitly when we want those names:

```scala
import zio.prelude._
import zio.prelude.classic._

def double[F[+_]: Functor](fa: F[Int]): F[Int] =
  fa.map(_ * 2)
```

The aliases include `Semigroup` for `Associative`, `Monoid` for `Identity`, `Group` for `Inverse`, `Functor` for `Covariant`, `Applicative` for `Covariant` with `IdentityBoth`, and `Monad` for `Covariant` with `IdentityFlatten`.

## Coherent Combined Traits

Some abstractions are most useful in combination, and ZIO Prelude provides a trait for each common pair in the `zio.prelude.coherent` package. Each one extends both abstractions and comes with an implicit `derive` method in its companion object that builds the combined instance from two separate ones. One example is `CovariantIdentityBoth`, which extends `Covariant` and `IdentityBoth`.

The combined trait is what lets a signature ask for both capabilities with a single context bound. A type that has a `Covariant` instance and an `IdentityBoth` instance, even defined in different places, satisfies it with no extra work:

```scala
import zio.prelude.coherent.CovariantIdentityBoth

def swapped[F[+_]: CovariantIdentityBoth, A, B](fa: F[A], fb: F[B]): F[(B, A)] =
  fa.zip(fb).map(_.swap)

swapped(Option(1), Option("a"))
// res4: Option[(String, Int)] = Some(value = ("a", 1))

swapped(List(1, 2), List("a"))
// res5: List[(String, Int)] = List(("a", 1), ("a", 2))
```

There is no need to ask for the two instances separately and hope that they agree. When the `IdentityBoth` instance in scope is already a `CovariantIdentityBoth`, `derive` returns it unchanged, and otherwise it wraps the two instances. The combined trait also carries extra operations, such as `forEach`, `forEach_` and `collectM`, that need both capabilities.

The other coherent traits fall in two groups. Several combine an abstraction with `Equal` or `DeriveEqual` so that the combined value is still a valid instance of both, for example `AssociativeEqual`, `CommutativeEqual`, `EqualIdentity`, `EqualIdempotent`, `EqualInverse`, `EqualPartialInverse`, `CovariantDeriveEqual`, `ContravariantDeriveEqual`, `DeriveEqualForEach`, `DeriveEqualNonEmptyForEach` and `DeriveEqualIdentityBothInvariant`. The `HashOrd` and `HashPartialOrd` traits combine `Hash` with `Ord` or `PartialOrd`, which is why the instances for `String` or `Ordering` are typed as `Hash[A] with Ord[A]`, and why `Hash` and `Ord` agree on equality for such types. They provide `make` constructors that build both from a single set of functions.

## Which Standard Types Have Which Instances

The following tables list which of the abstractions each commonly used type supports through instances found in the companion objects of the abstractions, so they are in implicit scope without further imports. A dash means that no instance exists. Each row of the first table also implies the weaker abstractions: `ForEach` implies `Covariant` and `Invariant`, `IdentityBoth` implies `AssociativeBoth`, `IdentityEither` implies `AssociativeEither` and `IdentityFlatten` implies `AssociativeFlatten`.

### Type Constructors

For type constructors, the table shows the strongest instance available. For `Either` and `Map` the type parameter that varies is the right one and the value type, respectively.

| Type             | Covariant or stronger       | Both                         | Either                       | Flatten                      | CommutativeBoth | CommutativeEither |
| ---------------- | --------------------------- | ---------------------------- | ---------------------------- | ---------------------------- | --------------- | ----------------- |
| `List`           | `ForEach`                   | `IdentityBoth`               | `IdentityEither`             | `IdentityFlatten`            | yes             | -                 |
| `Vector`         | `ForEach`                   | `IdentityBoth`               | `IdentityEither`             | `IdentityFlatten`            | yes             | -                 |
| `Chunk`          | `ForEach`                   | `IdentityBoth`               | `IdentityEither`             | `IdentityFlatten`            | yes             | -                 |
| `NonEmptyChunk`  | `NonEmptyForEach`           | `AssociativeBoth`            | -                            | `IdentityFlatten`            | yes             | -                 |
| `Option`         | `ForEach`                   | `IdentityBoth`               | `IdentityEither`             | `IdentityFlatten`            | yes             | -                 |
| `Either[E, _]`   | `ForEach` and `Bicovariant` | `IdentityBoth`               | `AssociativeEither`          | `IdentityFlatten`            | -               | -                 |
| `Try`            | `Covariant`                 | `IdentityBoth`               | `AssociativeEither`          | `IdentityFlatten`            | -               | -                 |
| `Future`         | `Covariant`                 | `IdentityBoth`               | `AssociativeEither`          | `IdentityFlatten`            | -               | yes               |
| `Map[K, _]`      | `ForEach`                   | -                            | -                            | `AssociativeFlatten`         | -               | -                 |
| `Set`            | `Invariant`                 | -                            | `IdentityEither`             | -                            | -               | yes               |
| `ZIO[R, E, _]`   | `Covariant`                 | `IdentityBoth`               | `AssociativeEither`          | `IdentityFlatten`            | yes             | yes               |

`Future` instances require an implicit `ExecutionContext` for `Covariant`, `AssociativeEither` and `CommutativeEither`. `ZIO` also has a `CovariantIdentityBoth` instance, and its failure channel has `Covariant` and the other instances through the `Failure` newtype.

### Value Types

For ordinary types of kind `*`, the table shows the instances that exist when the element types have the required instances themselves.

| Type               | Equal | Hash | Ord | PartialOrd | Debug | Associative | Identity                       |
| ------------------ | ----- | ---- | --- | ---------- | ----- | ----------- | ------------------------------ |
| `List[A]`          | yes   | yes  | yes | yes        | yes   | yes         | yes                            |
| `Vector[A]`        | yes   | yes  | yes | yes        | yes   | yes         | yes                            |
| `Chunk[A]`         | yes   | yes  | yes | yes        | yes   | yes         | yes                            |
| `NonEmptyChunk[A]` | yes   | yes  | yes | yes        | yes   | yes         | -                              |
| `Option[A]`        | yes   | yes  | yes | yes        | yes   | yes         | yes, with `Associative[A]`     |
| `Either[E, A]`     | yes   | yes  | yes | yes        | yes   | yes         | yes, with `Identity[A]`        |
| `Try[A]`           | yes   | -    | -   | -          | -     | -           | -                              |
| `Map[K, V]`        | yes   | yes  | -   | yes        | yes   | yes         | yes, with `Associative[V]`     |
| `Set[A]`           | yes   | yes  | -   | yes        | -     | -           | -                              |
| `String`           | yes   | yes  | yes | yes        | yes   | yes         | yes                            |

`List`, `Vector`, `Chunk` and `NonEmptyChunk` need the corresponding instance for the element type for `Equal`, `Hash`, `Ord`, `PartialOrd` and `Debug`. Their `Associative` and `Identity` instances concatenate and need nothing from the element type. `Option` and `Either` combine their contents with the element's `Associative`, `Map` combines values with the same key, and `Set` has `Associative` only through the `AndF` and `OrF` newtypes for intersection and union. The numeric types have `Equal`, `Hash`, `Ord`, `PartialOrd` and `Debug` instances, and their `Associative` and `Identity` instances live behind the `Sum`, `Prod`, `Min` and `Max` newtypes so that the choice of operation is explicit.

## Notes and Caveats

The syntax for converting a `Map` or an iterable of pairs into a `NonEmptyMap` or a `NonEmptySortedMap`, and an iterable into a `NonEmptySortedSet`, is defined in `NonEmptyMapSyntax`, `NonEmptySortedMapSyntax` and `NonEmptySortedSetSyntax`, which the package object does not mix in. Those conversions are reached through the companion objects, such as `NonEmptyMap.fromMapOption`, rather than through the wildcard import.

`Id` and `Const` are parameterized newtypes defined by the package object, so they are available as `Id` and `Const` after the wildcard import.
