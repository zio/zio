# Laws

> Property-based laws, generators and assertions for testing instances of ZIO Prelude abstractions with ZIO Test.

The Laws module gives us ready-made property-based tests for the algebraic laws that every ZIO Prelude abstraction must satisfy. An `Associative` instance is only correct if its operator is really associative, and a `Covariant` instance is only correct if `map` preserves identity and composition. The compiler cannot check either fact, so the module encodes each one as a ZIO Test law that we run over random values.

The module is published as `zio-laws-laws`, and it contains four kinds of building blocks: one `*Laws` object per abstraction (for example `AssociativeLaws` and `CovariantLaws`), the generators `Gens` and `GenFs`, the assertions in `TestAssertions`, and the `<->` family of comparison operators that make the laws read like the equations in the documentation.

The following declarations show the shape of the three kinds of building blocks:

```scala
// One Laws object per abstraction, each a Lawful from ZIO Test
object IdentityLaws extends Lawful[EqualIdentity]
object CovariantLaws extends LawfulF.Covariant[CovariantDeriveEqual, Equal]

// Generators for ZIO Prelude data types
object Gens   { def nonEmptyListOf[R <: Sized, A](a: Gen[R, A]): Gen[R, NonEmptyList[A]] }
object GenFs  { def either[R <: Sized, E](e: Gen[R, E]): GenF[R, ({ type lambda[+a] = Either[E, a] })#lambda] }
```

## Installation

To use the laws in our tests, we add the module to the `Test` configuration. It depends on ZIO Prelude and ZIO Test, and it is available for the JVM, Scala.js and Scala Native:

```scala
libraryDependencies += "dev.zio" %% "zio-laws-laws" % "1.0.0-RC49" % Test
```

## Testing an Instance

The shortest path from an instance to a passing test is `checkAllLaws`, which comes from `zio.test.laws`. We give it a `*Laws` object and a generator of values of our type, and ZIO Test checks every law in the set. To show the flow, we define a small `Money` type with an `Identity` instance that adds amounts:

```scala
import zio.prelude._
import zio.prelude.laws._
import zio.test._
import zio.test.laws._

final case class Money(cents: Long)

object Money {
  implicit val equal: Equal[Money] =
    Equal.default

  implicit val identity: Identity[Money] =
    Identity.make(Money(0), (l, r) => Money(l.cents + r.cents))

  val gen: Gen[Any, Money] =
    Gen.long(-1000L, 1000L).map(Money(_))
}
```

`IdentityLaws.laws` checks the left and right identity laws and, because an identity is also associative, the associativity law. A spec for `Money` is one test:

```scala
object MoneySpec extends ZIOSpecDefault {
  def spec =
    suite("Money")(
      test("identity laws")(checkAllLaws(IdentityLaws)(Money.gen))
    )
}
```

The `Lawful` instance is what ties a `Laws` object to `checkAllLaws`. It checks the whole set of laws of the object. If one law fails, ZIO Test reports the failing law by name together with the input that broke it, shrunk to a small counterexample, so we see which equation our instance violates.

## Laws Objects

Each `*Laws` object exposes every law of its abstraction as a `lazy val` or `val` and combines them all in one set. The combined set includes the laws of the abstractions it extends, so `IdentityLaws` also runs the laws of `AssociativeLaws`, and `InverseLaws` runs those of `PartialInverseLaws`, which runs those of `IdentityLaws`. We pick the object that matches the strongest abstraction our instance claims.

The objects for abstractions over a type `A` take one generator of `A`:

| Object                | Individual laws                                                                                      |
| --------------------- | ---------------------------------------------------------------------------------------------------- |
| `EqualLaws`           | `reflexiveLaw`, `symmetryLaw`, `transitivityLaw`                                                     |
| `HashLaws`            | `consistencyLaw`, plus `EqualLaws.laws`                                                              |
| `PartialOrdLaws`      | `transitivityLaw1`, `transitivityLaw2`, `antisymmetryLaw1`, `antisymmetryLaw2`, `eqConsistencyLaw`   |
| `OrdLaws`             | `connexityLaw1`, `connexityLaw2`, `complementLaw`, plus the `PartialOrd` laws                        |
| `AssociativeLaws`     | `associativityLaw`                                                                                   |
| `CommutativeLaws`     | `commutativeLaw`, plus `AssociativeLaws.laws`                                                        |
| `IdempotentLaws`      | `idempotentLaw`, plus `AssociativeLaws.laws`                                                         |
| `IdentityLaws`        | `leftIdentityLaw`, `rightIdentityLaw`, plus `AssociativeLaws.laws`                                   |
| `PartialInverseLaws`  | `partialInverseLaw`, plus `IdentityLaws.laws`                                                        |
| `InverseLaws`         | `inverseLaw`, plus `PartialInverseLaws.laws`                                                         |

The objects for type constructors `F[_]` take a `GenF` (a generator of `F[A]` for any generator of `A`) followed by a generator of `A`, which is the pattern ZIO Prelude's own test suite uses:

| Object                     | Abstraction                                                          |
| -------------------------- | -------------------------------------------------------------------- |
| `CovariantLaws`            | `Covariant`: `identityLaw`, `compositionLaw`                         |
| `ContravariantLaws`        | `Contravariant`: `identityLaw`, `compositionLaw`                     |
| `ForEachLaws`              | `ForEach`, runs `CovariantLaws.laws`                                 |
| `NonEmptyForEachLaws`      | `NonEmptyForEach`, runs `ForEachLaws.laws`                           |
| `AssociativeBothLaws`      | `AssociativeBoth`: `associativityLaw`                                |
| `CommutativeBothLaws`      | `CommutativeBoth`: `commutativeLaw`                                  |
| `IdentityBothLaws`         | `IdentityBoth`: left and right identity, plus associativity          |
| `AssociativeEitherLaws`    | `AssociativeEither`: `associativityLaw`                              |
| `CommutativeEitherLaws`    | `CommutativeEither`: `commutativeLaw`                                |
| `IdentityEitherlaws`       | `IdentityEither`: left and right identity, plus associativity        |
| `AssociativeFlattenLaws`   | `AssociativeFlatten`: `associativityLaw`                             |
| `IdentityFlattenLaws`      | `IdentityFlatten`: left and right identity, plus associativity       |

The object for `IdentityEither` is spelled `IdentityEitherlaws`, with a lowercase `l`, in the source, so that is the name we import.

`EquivalenceLaws` is the one object with two generators, because an `Equivalence[A, B]` relates values of two types. We pass a generator for each side, as in `checkAllLaws(EquivalenceLaws)(genLeft, genRight)`.

To test a type constructor, we pair a `GenF` for it with a generator of the element type. Here we check that the standard `Either` with a fixed error type satisfies the `Covariant` laws:

```scala
object EitherSpec extends ZIOSpecDefault {
  def spec =
    suite("Either")(
      test("covariant laws")(checkAllLaws(CovariantLaws)(GenFs.either(Gen.int), Gen.int))
    )
}
```

The instance under test only needs to be in implicit scope. The laws look it up, together with an `Equal` for comparing results.

## Generators

The module ships generators for ZIO Prelude data types that ZIO Test does not know about. `Gens` holds generators of concrete values, and `GenFs` holds `GenF` values, which are generators parameterized by the element generator and are what the laws for type constructors consume.

| `Gens` member                      | Generates                                              |
| ---------------------------------- | ------------------------------------------------------ |
| `anyNatural`                       | `Natural` values from zero to `Int.MaxValue`           |
| `natural(min, max)`                | `Natural` values within a range                        |
| `parSeq(z, a)`                     | `ParSeq` values, both sequential and parallel          |
| `nonEmptyListOf(a)`                | `NonEmptyList` values                                  |
| `nonEmptyMultiSetOf(a)`            | `NonEmptyMultiSet` values                              |
| `nonEmptySetOf(a)`                 | `NonEmptySet` values                                   |
| `state(s, a)`                      | `State` transition functions                           |
| `these(a, b)`                      | `These` values                                         |
| `validation(w, e, a)`              | `ZValidation` values with logs, failures and successes |

The `GenFs` members are `cause`, `either`, `exit`, `future`, `map`, `nonEmptyChunk`, `nonEmptyList`, `nonEmptySet`, `parSeq`, `these`, `tryScala`, `tuple2`, `tuple3`, `validation` and `validationFailure`. Each one wraps the matching `Gens` generator or ZIO Test generator in a `GenF`, fixing the other type parameters it takes, such as the generator of errors.

To use a generator directly outside a law, we pass it to `check` like any other ZIO Test generator:

```scala
object GensSpec extends ZIOSpecDefault {
  def spec =
    test("non-empty lists have a head")(
      check(Gens.nonEmptyListOf(Gen.int))(list => assertTrue(list.size > 0))
    )
}
```

For a type constructor that has no `GenF` in the module, we write our own by implementing `GenF`, as the Scala parallel collections tests do for `ParSeq` and `ParMap`. The module does not generate every ZIO Prelude type; for the rest we use ZIO Test generators such as `Gen.listOf`.

## Assertions

The `zio.prelude.laws` package object extends `TestAssertions` and adds infix syntax, so a single import gives us assertions that compare values with the ZIO Prelude type classes instead of `==`.

| Member                                                          | Requires        | Meaning                                                    |
| --------------------------------------------------------------- | --------------- | ---------------------------------------------------------- |
| `equalTo(expected)`                                             | `Equal`         | The value equals `expected` according to `Equal`           |
| `isGreaterThan`, `isGreaterThanEqualTo`, `isLessThan`, `isLessThanEqualTo` | `PartialOrd` | The value compares to the reference as named     |
| `isSuccessV(assertion)`                                         | none            | The `ZValidation` is a success whose value satisfies `assertion` |
| `isFailureV(assertion)`                                         | none            | The `ZValidation` is a failure whose errors satisfy `assertion`  |
| `assertionRec(name)(assertion)(get)`                            | none            | Builds a custom assertion that first extracts a nested value     |

The infix operators are `<->` and `isEqualTo` (equality through `Equal`) and `greater`, `greaterOrEqual`, `less` and `lessOrEqual` (comparison through `PartialOrd`). Each returns a ZIO Test `TestResult`, so we can combine them with `&&` and return them from a `test`.

To compare values with `Equal` and `Ord` instead of `==`, and to inspect a validation:

```scala
object AssertionsSpec extends ZIOSpecDefault {
  def spec =
    suite("assertions")(
      test("equality through Equal")(
        Money(2) <-> Money(2)
      ),
      test("ordering through PartialOrd")(
        assert(5)(isGreaterThan(3)) && (1 greater 0)
      ),
      test("validation")(
        assert(Validation.succeed(1))(isSuccessV(equalTo(1)))
      )
    )
}
```

The `<->` operator is what the laws themselves use. For example, the associativity law is written `(a1 <> (a2 <> a3)) <-> ((a1 <> a2) <> a3)`, so a failing law reads like the equation it checks.

## Related Abstractions

The laws for the experimental abstractions live in a separate module, `zio-prelude-experimental-laws`, and follow the same `Lawful` pattern. See [Experimental Abstractions](../experimental/index.md).
