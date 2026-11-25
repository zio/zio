# Arithmetic

> DistributiveProd, Annihilation, Subtract, PartialDivide and Divide describe addition, multiplication, subtraction and division.

These abstractions describe types with a notion of addition and multiplication, and optionally subtraction and division. The shared base is `DistributiveProd`, which pairs a sum with a product that distributes over it. `Annihilation`, `Subtract`, `PartialDivide` and `Divide` each strengthen one of the two operations. The two operations are expressed through the `Sum` and `Prod` newtypes, which let one type carry both an additive and a multiplicative `Associative` instance.

The core declarations of the family are:

```scala
trait DistributiveProd[A] {
  def Sum: Associative[Sum[A]]
  def Prod: Associative[Prod[A]]
  def sum(l: => A, r: => A): A
  def prod(l: => A, r: => A): A
}
trait Subtract[A] extends DistributiveProd[A]      { def Sum: Inverse[Sum[A]];          def subtract(l: => A, r: => A): A }
trait PartialDivide[A] extends DistributiveProd[A] { def Prod: PartialInverse[Prod[A]]; def divideOption(l: => A, r: => A): Option[A] }
```

All of the types on this page are in `zio.prelude.experimental`. The [overview](index.md) shows how they relate to the other families.

## DistributiveProd

`DistributiveProd[A]` provides `sum` and `prod`, defined through the `Sum` and `Prod` instances, with the infix operators `+++` (alias `sum`) and `***` (alias `prod`). Its laws object, `DistributiveProdLaws`, checks that `prod` distributes over `sum` on both sides: `a1 *** (a2 +++ a3)` equals `(a1 *** a2) +++ (a1 *** a3)`, and `(a1 +++ a2) *** a3` equals `(a1 *** a3) +++ (a2 *** a3)`.

The built-in instances are provided for `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, `Double` and `BigDecimal` and, as plain `DistributiveProd` instances, for `ParSeq[Unit, A]` and `zio.Cause[A]`. To add and multiply with the operators:

```scala
import zio.prelude.experimental._

10 +++ 5
// res0: Int = 15

10 *** 5
// res1: Int = 50
```

## Annihilation

`Annihilation[A]` extends `DistributiveProd[A]` and requires `Sum` to be an `Identity`. It defines `annihilation` as the identity of `Sum`, the zero of the type. `AnnihilationLaws` adds that zero absorbs the product on both sides: `annihilation *** a` and `a *** annihilation` both equal `annihilation`. The numeric instances above are all `Annihilation`.

To read the zero of `Int` through its instance:

```scala
Annihilation[Int].annihilation
// res2: Int = 0
```

## Subtract

`Subtract[A]` extends `DistributiveProd[A]` and requires `Sum` to be an `Inverse`, which is what makes subtraction possible. `subtract` is defined through `Sum.inverse`, and the infix forms are `---` and `subtract`. It has no laws object, because the inverse laws belong to the `Inverse` instance of `Sum`. The numeric types, from `Byte` to `BigDecimal`, have `Subtract` instances.

To subtract with the operator:

```scala
10 --- 4
// res3: Int = 6
```

## PartialDivide

`PartialDivide[A]` extends `DistributiveProd[A]` and requires `Prod` to be a `PartialInverse`. Division can fail, for example when the divisor is zero, so `divideOption` returns an `Option`, and the infix forms are `-/-` and `divideOption`. The instances are provided for `Float`, `Double` and `BigDecimal`, which return `None` when the divisor is zero.

To divide, and to divide by zero:

```scala
10.0 -/- 4.0
// res4: Option[Double] = Some(value = 2.5)

10.0 -/- 0.0
// res5: Option[Double] = None
```

## Divide

`Divide[A]` extends `PartialDivide[A]` and requires `Prod` to be an `Inverse`, so division always succeeds. It adds `divide`, with the infix forms `-:-` and `divide`, and `divideOption` is derived from the product's partial inverse. ZIO Prelude does not provide a built-in `Divide` instance for any type, so `Divide` is an extension point for our own types whose product is a total inverse.

## Writing an Instance

To support our own type, we provide `Sum` and `Prod` instances for it and extend the most specific abstraction it satisfies, overriding `sum` and `prod` only when we want something faster than combining through the newtypes. We then test the instance with `DistributiveProdLaws` or `AnnihilationLaws` from `zio-prelude-experimental-laws`, as shown on the [overview](index.md#laws).
