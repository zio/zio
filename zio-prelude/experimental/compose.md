# Composition

> BothCompose, EitherCompose and ApplicationCompose describe arrows that pair, choose between and apply values.

These abstractions describe arrow-like type constructors `=>:[-_, +_]`, such as functions, that can be composed and that gain extra structure on top of composition. `BothCompose` adds products (pairing outputs), `EitherCompose` adds sums (choosing between inputs), and `ApplicationCompose` adds currying and application. They build on [`AssociativeCompose`](../functional-abstractions/parameterized-types/associativecompose.md) from the core module, which provides `compose`.

The core declarations of `BothCompose` are:

```scala
trait BothCompose[=>:[-_, +_]] extends AssociativeCompose[=>:] {
  type :*:[+_, +_]
  def fromFirst[A]: (A :*: Any) =>: A
  def fromSecond[B]: (Any :*: B) =>: B
  def toBoth[A, B, C](a2b: A =>: B)(a2c: A =>: C): A =>: (B :*: C)
}
```

All of the types on this page are in `zio.prelude.experimental`. The [overview](index.md) shows how they relate to the other families.

## BothCompose

`BothCompose` adds a product type, the abstract type member `:*:`, together with its two projections, `fromFirst` and `fromSecond`, and `toBoth`, which builds an arrow into the product from two arrows with the same source. The `BothCompose#bothCompose` method checks the three laws that tie these together (the projections undo `toBoth`, and `toBoth` of the projections of an arrow gives the arrow back) for the arrows we supply, and returns a `Boolean`. It needs an `Equal` instance for the arrow type.

The only built-in instance is for `Function`, and it lives in the `BothCompose` companion. It is declared as an `ApplicationCompose[Function]` whose product is `Tuple2`. To pair the results of two functions on the same input:

```scala
import zio.prelude.experimental._

val increment: Int => Int = _ + 1
// increment: Int => Int = <function1>
val describe: Int => String = _.toString
// describe: Int => String = <function1>

val both = BothCompose.FunctionApplicationCompose.toBoth(increment)(describe)
// both: Function[Int, BothCompose.FunctionApplicationCompose.:*:[Int, String]] = zio.prelude.experimental.BothCompose$$anon$1$$Lambda$17571/0x00007f348ac79810@47f5da4b

println(both(1))
// (2,1)
```

The product type is an abstract member of the instance, so `both(1)` is typed with `:*:` rather than `Tuple2`, and we read the result as the pair `(2,1)` only at runtime. When a function over `Tuple2` is needed, we cast or provide our own instance with a concrete product.

## EitherCompose

`EitherCompose` is the dual of `BothCompose`. It adds a sum type, the abstract member `:+:`, with the injections `toLeft` and `toRight`, and `fromEither`, which builds an arrow out of the sum from two arrows with the same target. `EitherCompose#eitherCompose` checks the corresponding three laws and returns a `Boolean`, again requiring `Equal` for the arrow type.

The built-in `Function` instance, `EitherCompose.FunctionEitherCompose`, uses `Either` as the sum. To combine two functions that return the same type into one function over the sum:

```scala
val either = EitherCompose.FunctionEitherCompose.fromEither((i: Int) => i.toString)((s: String) => s)
// either: Function[EitherCompose.FunctionEitherCompose.:+:[Int, String], String] = zio.prelude.experimental.EitherCompose$$anon$1$$Lambda$17572/0x00007f348ac7a248@3fcaa39f
```

As with `BothCompose`, the sum is an abstract member, so values of the sum type are not statically known to be `Either`.

## ApplicationCompose

`ApplicationCompose` extends `BothCompose` with an exponential type, the abstract member `-->:`, which represents arrows as values. It adds `application` (apply an arrow value to its input), `curry` and `uncurry`. A `BothCompose` plus an `ApplicationCompose` together make a cartesian closed category, which is the structure behind functions of several arguments. `ApplicationCompose#applicationCompose` checks that `curry` and `uncurry` are inverses and that `application` recovers the original arrow, again for the arguments we supply.

The `Function` instance `BothCompose.FunctionApplicationCompose` is an `ApplicationCompose[Function]`, with `Tuple2` as the product and `Function` as the exponential. It is the same instance used for `BothCompose` above, so `BothCompose.FunctionApplicationCompose.curry` and `BothCompose.FunctionApplicationCompose.uncurry` convert between functions of a pair and curried functions.

## Syntax

The package object provides `&&&` and `toBoth` for `BothCompose`, `|||` and `fromEither` for `EitherCompose`, and `curry` and `uncurry` for `ApplicationCompose`. They take the matching `Aux` instance as an implicit parameter, which pins down the product, sum and exponential types. Because the built-in `Function` instances expose those types abstractly, as described above, the `Aux` evidence is not found for plain `Function1` values, and the operators are most useful with instances of our own whose members are concrete. For `Function` we call the instance methods directly.

## Classic Aliases

The `zio.prelude.experimental.classic` object defines type aliases that combine these abstractions with the `Category` type from `zio.prelude.classic`: `CartesianCategory`, `ClosedCartesianCategory` and `CoCartesianCategory`. They name the usual category-theory combinations: `Category` with `BothCompose`, with `ApplicationCompose` added, and with `EitherCompose`.
