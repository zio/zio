# How to Migrate from Cats or Scalaz

> Map Cats and Scalaz abstractions, data types and syntax to their ZIO Prelude equivalents, with a worked example.

In this guide, we move code that uses Cats or Scalaz to ZIO Prelude. By the end, you will have a map from the type classes, data types and operators you know to the ones ZIO Prelude provides, and a worked example of a module that moves from one to the other.

ZIO Prelude breaks the classic hierarchy into smaller abstractions that you combine, so some names map one to one and others map to a pair of abstractions. We start with the map, then move a small module, and finish with the differences that cause surprises.

## The Problem

Code written with Cats or Scalaz uses one set of names for abstractions, instances and syntax, and ZIO Prelude uses another. Moving the code means finding the equivalent of each one. For example, a statistics type with a monoid, a show instance, validation and a traversal looks like this in Cats:

**Before (Cats 2.12, not compiled here):**

```scala
import cats._
import cats.data.{State, Validated, ValidatedNel}
import cats.implicits._

final case class Stats(count: Int, total: Long)

object Stats {
  implicit val monoid: Monoid[Stats] = new Monoid[Stats] {
    def empty: Stats                        = Stats(0, 0L)
    def combine(a: Stats, b: Stats): Stats = Stats(a.count + b.count, a.total + b.total)
  }
  implicit val show: Show[Stats]   = Show.show(s => s"Stats(${s.count}, ${s.total})")
  implicit val eq: Eq[Stats]       = Eq.fromUniversalEquals
  implicit val order: Order[Stats] = Order.by(s => (s.count, s.total))
}

def parse(s: String): ValidatedNel[String, Int] =
  Validated.fromOption(s.toIntOption, s"not a number: $s").toValidatedNel

val totals   = List(1, 2, 3).foldMap(i => Stats(1, i.toLong))
val parsed   = List("1", "x", "y").traverse(parse)
val combined = (parse("1"), parse("x")).mapN(_ + _)
val counter  = State[Int, Int](s => (s + 1, s))
```

The rest of this guide moves this module to ZIO Prelude, one step at a time. Only the ZIO Prelude side of the examples is compiled in the documentation build, because Cats and Scalaz are not on its classpath.

## The Abstractions

ZIO Prelude does not copy the Cats hierarchy. Each abstraction describes one property, and a classic type class corresponds to a combination of them. The table lists the closest equivalent of each Cats and Scalaz abstraction:

| Cats and Scalaz                      | ZIO Prelude                                    | Notes                                                    |
| ------------------------------------ | ---------------------------------------------- | -------------------------------------------------------- |
| `Semigroup`                          | `Associative`                                  | `combine`, operator `<>`                                 |
| `Monoid`                             | `Identity`                                     | `Identity.make(identity, combine)`                       |
| `CommutativeSemigroup`               | `Commutative`                                  |                                                          |
| `CommutativeMonoid`                  | `Commutative` and `Identity`                   |                                                          |
| `Band`                               | `Idempotent`                                   |                                                          |
| `Group`                              | `Inverse`                                      | `inverse` is total, `PartialInverse` is the partial form |
| `Eq`, `Equal`                        | `Equal`                                        | operator `===`                                           |
| `PartialOrder`                       | `PartialOrd`                                   |                                                          |
| `Order`                              | `Ord`                                          | `compare` returns an `Ordering`, operator `=?=`          |
| `Hash`                               | `Hash`                                         |                                                          |
| `Show`                               | `Debug`                                        | `render`, `debug`                                        |
| `Functor`                            | `Covariant`                                    | `map`                                                    |
| `Contravariant`                      | `Contravariant`                                | `contramap`                                              |
| `Invariant`                          | `Invariant`                                    | `invmap`                                                 |
| `Bifunctor`                          | `Bicovariant`                                  | `bimap`                                                  |
| `Apply`                              | `AssociativeBoth` and `Covariant`              | `both` pairs two values                                  |
| `Applicative`                        | `IdentityBoth` and `Covariant`                 | `any` replaces `pure`                                    |
| `FlatMap`                            | `AssociativeFlatten` and `Covariant`           | `flatten`, `flatMap` syntax                              |
| `Monad`                              | `IdentityFlatten` and `Covariant`              |                                                          |
| `SemigroupK`                         | `AssociativeEither`                            | `either` returns an `Either`                             |
| `MonoidK`, `Alternative`             | `IdentityEither`                               | `none` is the neutral value                              |
| `Foldable` and `Traverse`            | `ForEach`                                      | one abstraction for both                                 |
| `Reducible` and `NonEmptyTraverse`   | `NonEmptyForEach`                              |                                                          |

Where a Cats or Scalaz type class combines several properties, ZIO Prelude asks for each property separately, so a function that needs only `map` and `both` does not demand a full `Monad`. There is no equivalent of `MonadError` or `ApplicativeError`. Error handling lives in the data types, for example `Validation` and `ZPure`.

## The Data Types

The data types map in the same way. We list the closest equivalent of each:

| Cats and Scalaz                              | ZIO Prelude                                  |
| -------------------------------------------- | -------------------------------------------- |
| `Validated`, `ValidatedNel`, `Validation`    | `Validation`, `ZValidation`                  |
| `Ior`                                        | `These`                                      |
| `NonEmptyList`                               | `NonEmptyList`                               |
| `NonEmptyMap`, `NonEmptySet`                 | `NonEmptyMap`, `NonEmptySet`                 |
| `State`, `Reader`, `Writer`                  | `State`, `Reader`, `Writer` aliases of `ZPure` |
| `StateT`, `ReaderWriterStateT` over `Either` | `ZPure`                                      |
| `Id`                                         | `Id`                                         |
| `Const`                                      | `Const`                                      |
| newtypes, `Tag`                              | `Newtype`, `Subtype`                         |

`ZPure` is one data type with parameters for log, state, environment and error, and the aliases such as `State`, `EState` and `Reader` are its common specializations. See [ZPure](../zpure/index.md).

## The Syntax

The operators have different names, and some have a different shape. We list the ones that most often need changing:

| Cats and Scalaz            | ZIO Prelude                                 |
| -------------------------- | ------------------------------------------- |
| `a \|+\| b`                | `a <> b`                                    |
| `a === b`                  | `a === b`                                   |
| `a compare b`              | `a =?= b`                                   |
| `x.show`                   | `x.render`                                  |
| `fa.traverse(f)`           | `fa.forEach(f)`                             |
| `fa.traverse_(f)`          | `fa.forEach_(f)`                            |
| `fa.sequence`              | `fa.flip`                                   |
| `fa.foldMap(f)`            | `fa.foldMap(f)`                             |
| `fa.combineAll`            | `ForEach[F].fold(fa)`                       |
| `(fa, fb).mapN(f)`         | `fa.zipWith(fb)(f)`, `Validation.validateWith(fa, fb)(f)` |
| `a.pure[F]`                | `a.succeed[F]`                              |

## Moving Instances

The monoid, show, eq and order instances become `Identity`, `Debug`, `Equal` and `Ord`. We build each from the smaller instances of the fields. `Ord[(Int, Long)].contramap` plays the role of `Order.by`:

```scala
import zio.prelude._
import zio.prelude.fx.ZPure

final case class Stats(count: Int, total: Long)

object Stats {
  implicit val statsIdentity: Identity[Stats] =
    Identity.make(Stats(0, 0L), (a, b) => Stats(a.count + b.count, a.total + b.total))

  implicit val statsDebug: Debug[Stats] =
    Debug.make(s => Debug.Repr.Constructor(Nil, "Stats", "count" -> s.count.debug, "total" -> s.total.debug))

  implicit val statsEqual: Equal[Stats] = Equal.default

  implicit val statsOrd: Ord[Stats] =
    Ord[(Int, Long)].contramap(s => (s.count, s.total))
}
```

Here `Equal.default` corresponds to `Eq.fromUniversalEquals`, and `Debug.make` takes a function that returns a structured `Debug.Repr`. The `render` operator turns it into text. We check that the new instances work with the operators from the syntax table:

```scala
List(1, 2, 3).foldMap(i => Stats(1, i.toLong))
// res1: Stats = Stats(count = 3, total = 6L)

Stats(1, 2) <> Stats(3, 4)
// res2: Stats = Stats(count = 4, total = 6L)

Stats(1, 2) === Stats(1, 2)
// res3: Boolean = true

Stats(1, 2).render
// res4: String = "Stats(count = 1, total = 2L)"

Stats(1, 2) =?= Stats(3, 4)
// res5: Ordering = LessThan
```

:::warning
ZIO Prelude has no instance that combines plain `Int` values. In Cats, `1 |+| 2` adds. In ZIO Prelude, the choice between addition, multiplication, minimum and maximum is explicit: wrap the values in `Sum`, `Prod`, `Min` or `Max` from `zio.prelude.newtypes`, for example `Sum(1) <> Sum(2)`.
:::

## Moving Validation and Traversals

`ValidatedNel[E, A]` becomes `Validation[E, A]`. The error side is a single error type `E`, and the failure keeps one or more of them in a `NonEmptyChunk`, so there is no `Nel` variant. `traverse` becomes `forEach`, and `mapN` on a tuple becomes `Validation.validateWith`:

```scala
def parse(s: String): Validation[String, Int] =
  Validation.fromOptionWith(s"not a number: $s")(s.toIntOption)
```

Both calls keep every error, as the Cats versions do:

```scala
List("1", "x", "y").forEach(parse)
// res6: Validation[String, List[Int]] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(not a number: x, not a number: y)
// )

Validation.validateWith(parse("1"), parse("x"))(_ + _)
// res7: ZValidation[Nothing, String, Int] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(not a number: x)
// )
```

Other operators of the table work the same way. `sequence` becomes `ForEach#flip`, and `pure` becomes `succeed`:

```scala
List(Option(1), Option(2)).flip
// res8: Option[List[Int]] = Some(value = List(1, 2))

1.succeed[Option]
// res9: Option[Int] = Some(value = 1)
```

## Moving State

`State[S, A]` exists under the same name, and it is an alias of `ZPure`, so it also composes with errors and logs. The function passed to `ZPure.modify` returns the value first and the new state second, which is the reverse of the tuple the Cats `State` constructor takes:

```scala
val counter: State[Int, Int] =
  ZPure.modify[Int, Int, Int](s => (s, s + 1))
// counter: State[Int, Int] = FlatMap(
//   value = Inspect(run0 = <function1>),
//   continue = zio.prelude.fx.ZPure$$$Lambda$19389/0x00007f348b2215b0@3d1f31f2
// )

counter.run(0)
// res10: (Int, Int) = (1, 0)
```

The `ZPure#run` operator returns the final state first and the value second, so `(1, 0)` means the state is `1` and the value is `0`, as in the Cats version.

## Watching for Differences

Some names look the same and behave differently. The ones that most often cause surprises are:

- The `<+>` operator on `AssociativeEither` returns `F[Either[A, B]]`, not `F[A]` as in Cats. Use `orElse` to choose the first success of the same type.
- `Ord` compares to an `Ordering` value of ZIO Prelude, with the cases `LessThan`, `Equals` and `GreaterThan`, not to an `Int`.
- `fold` on a standard collection takes a start value and a function, so `List#fold` does not use an `Identity` instance. Call `ForEach[List].fold(list)` to fold with one.
- `Equal`, `Ord` and `Hash` are related by inheritance, so defining all three as separate implicit values in one scope is ambiguous. See [How to Define Equal, Ord, Hash and Debug Instances](./define-equal-ord-hash-debug-instances.md).

## Going Further

- Read the [Introduction](../functional-abstractions/index.md) to the abstractions, and the [diagram of abstractions](../functional-abstractions/abstraction-diagrams.md) to see how they compose.
- See [How to Accumulate Validation Errors](./accumulate-validation-errors.md) and [How to Traverse Collections with ForEach](./traverse-with-foreach.md) for the idioms that replace `Validated` and `Traverse` code.
