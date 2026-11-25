# Lattices and Boolean Algebra

> Absorption, DistributiveAbsorption, Complement, ExcludedMiddle, Noncontradiction and Involution describe joins, meets and complements.

These abstractions describe types that have two combining operations, a join (`or`, written `vvv`) and a meet (`and`, written `^^^`), and optionally a complement. `Boolean` and `Set` are the standard examples. Together they build up from a lattice (`Absorption`) to a full Boolean algebra (`Involution`).

The core declarations of the family are:

```scala
trait Absorption[A] {
  def or(l: => A, r: => A): A
  def and(l: => A, r: => A): A
  def Or: Associative[OrF[A]]
  def And: Associative[AndF[A]]
}
trait Complement[A] extends Absorption[A] { def complement(a: => A): A }
```

All of the types on this page are in `zio.prelude.experimental`. The [overview](index.md) shows how they relate to the other families.

## Absorption

`Absorption[A]` is the base of the family. It provides `or` and `and`, defined through two `Associative` instances over the `OrF` and `AndF` newtypes, and the infix operators `vvv` (alias `or`) and `^^^` (alias `and`). The name refers to the absorption laws that its laws object checks: `a1 vvv (a1 ^^^ a2)` equals `a1`, and `a1 ^^^ (a1 vvv a2)` equals `a1`.

The built-in instances are for `Boolean` and `Set[A]`. To join and meet values:

```scala
import zio.prelude.experimental._

true vvv false
// res0: Boolean = true

true ^^^ false
// res1: Boolean = false

Set(1, 2) vvv Set(2, 3)
// res2: Set[Int] = Set(1, 2, 3)

Set(1, 2) ^^^ Set(2, 3)
// res3: Set[Int] = Set(2)
```

For `Set`, `vvv` is union and `^^^` is intersection. To implement `Absorption` for our own type, we provide the two associative operations as `Or` and `And` and let `or` and `and` default to combining through them.

## DistributiveAbsorption

`DistributiveAbsorption[A]` extends `Absorption[A]` without adding members. It marks an instance whose join and meet distribute over each other, and `DistributiveAbsorptionLaws` adds the two distributive laws on top of the absorption laws: `a1 vvv (a2 ^^^ a3)` equals `(a1 vvv a2) ^^^ (a1 vvv a3)`, and the same with join and meet exchanged. The `Boolean` and `Set` instances are both `DistributiveAbsorption`.

## Complement

`Complement[A]` extends `Absorption[A]` with a single operation, `complement`. The package object adds the syntax `!a` and `a.complement`, available for any type with a `Complement` instance. It has no laws of its own; the two abstractions below attach laws to it.

## ExcludedMiddle and Noncontradiction

`ExcludedMiddle[A]` and `Noncontradiction[A]` extend `Complement[A]` with the two bounds of the lattice. `ExcludedMiddle` adds `top`, which it defines as the identity of `And`, so its `And` must be an `Identity`. `Noncontradiction` adds `bottom`, the identity of `Or`, so its `Or` must be an `Identity`.

Their laws state how the complement interacts with the bounds. `ExcludedMiddleLaws` checks that `!a vvv a` equals `top`, and `NoncontradictionLaws` checks that `!a ^^^ a` equals `bottom`. Each object also includes the absorption laws.

## Involution

`Involution[A]` is `ExcludedMiddle[A] with Noncontradiction[A]` and declares no members of its own. It is the Boolean algebra: a bounded lattice whose complement satisfies both laws above. `InvolutionLaws` adds `!(!a)` equals `a` on top of the `ExcludedMiddle` and `Noncontradiction` laws. The `Boolean` instance is an `Involution`.

To read the bounds and the complement of `Boolean` through its instance (the instance is used explicitly because `Boolean` already has its own `!` operator):

```scala
Involution[Boolean].top
// res4: Boolean = true

Involution[Boolean].bottom
// res5: Boolean = false

Complement[Boolean].complement(true)
// res6: Boolean = false
```

## Writing an Instance

To support our own type, we implement the most specific abstraction it satisfies and test it with the matching laws object, which also checks every weaker abstraction. For a type that forms a Boolean algebra we implement `Involution`, and then run `checkAllLaws(InvolutionLaws)(gen)` as shown on the [overview](index.md#laws). The generic law-testing workflow is described on the [Laws](../laws/index.md) page.
