# Experimental Abstractions

> Experimental ZIO Prelude abstractions for lattices, Boolean algebra, arithmetic and composition, with laws for each.

The experimental module collects abstractions that are not part of the core ZIO Prelude API yet. They describe structure that the core abstractions do not cover: two operations that interact (addition with multiplication, join with meet), a complement operation, subtraction and division, and arrows that can pair or choose between values. Because they are experimental, their names and shapes can change between releases.

The abstractions fall into three families, each documented on its own page: [Lattices and Boolean Algebra](lattice-and-boolean-algebra.md) (`Absorption`, `DistributiveAbsorption`, `Complement`, `ExcludedMiddle`, `Noncontradiction`, `Involution`), [Arithmetic](arithmetic.md) (`DistributiveProd`, `Annihilation`, `Subtract`, `PartialDivide`, `Divide`) and [Composition](compose.md) (`BothCompose`, `EitherCompose`, `ApplicationCompose`).

The core declarations of one abstraction from each family are:

```scala
trait Absorption[A]       { def Or: Associative[OrF[A]];  def And: Associative[AndF[A]] }
trait DistributiveProd[A] { def Sum: Associative[Sum[A]]; def Prod: Associative[Prod[A]] }
trait BothCompose[=>:[-_, +_]] extends AssociativeCompose[=>:] { type :*:[+_, +_] }
```

## Installation

The abstractions live in `zio-prelude-experimental`, and their laws live in a separate module, `zio-prelude-experimental-laws`, that we add to the `Test` configuration. Both are available for the JVM, Scala.js and Scala Native:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-prelude-experimental"      % "1.0.0-RC49",
  "dev.zio" %% "zio-prelude-experimental-laws" % "1.0.0-RC49" % Test
)
```

The infix syntax for every abstraction comes from the package object, so a single import brings it into scope:

```scala
import zio.prelude.experimental._
```

## How They Work Together

Every abstraction is a type class built from the core abstractions. Each operation is expressed as an `Associative`, `Identity` or `Inverse` instance over a newtype that names it (`Sum`, `Prod`, `OrF`, `AndF`), and the experimental type class adds the relationship between the operations. The hierarchy below shows which abstraction extends which:

```
DistributiveProd  (Sum, Prod: Associative)
 ├── Annihilation   (Sum: Identity, annihilation = Sum.identity)
 ├── Subtract       (Sum: Inverse)
 └── PartialDivide  (Prod: PartialInverse)
      └── Divide    (Prod: Inverse)

Absorption  (Or, And: Associative)
 ├── DistributiveAbsorption
 └── Complement  (complement)
      ├── ExcludedMiddle     (top, And: Identity)
      ├── Noncontradiction   (bottom, Or: Identity)
      └── Involution         (ExcludedMiddle with Noncontradiction)

AssociativeCompose  (core)
 ├── BothCompose
 │    └── ApplicationCompose
 └── EitherCompose
```

The typical workflow has four steps:

1. Pick the abstraction that matches the structure of the type, for example `DistributiveAbsorption` for sets or `Subtract` for numbers.
2. Import `zio.prelude.experimental._` for the operators, such as `vvv`, `^^^`, `+++`, `***` and `---`.
3. Use the built-in instance, or provide our own implicit instance for our type.
4. Check our own instance with the matching `*Laws` object from `zio-prelude-experimental-laws`, as described on the [Laws](../laws/index.md) page.

## Laws

The laws module contains a `Lawful` object for each abstraction that has laws as data: `AbsorptionLaws`, `DistributiveAbsorptionLaws`, `ExcludedMiddleLaws`, `NoncontradictionLaws`, `InvolutionLaws`, `DistributiveProdLaws` and `AnnihilationLaws`. They live in `zio.prelude.experimental.laws`, and each one includes the laws of the abstractions it extends. `Subtract`, `PartialDivide`, `Divide` and `Complement` have no laws object of their own, and the composition abstractions state their laws as methods on the traits themselves.

To check the laws for `Boolean`, we pass a generator to `checkAllLaws`:

```scala
import zio.prelude.experimental.laws._
import zio.test._
import zio.test.laws._

object BooleanLawsSpec extends ZIOSpecDefault {
  def spec =
    test("Boolean is an involution")(checkAllLaws(InvolutionLaws)(Gen.boolean))
}
```
