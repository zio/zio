# Tutorial: Validate an Order

> Build an order validator step by step with smart newtypes, Validation, Identity, ForEach and ZPure.

Welcome to ZIO Prelude! This tutorial is for Scala developers who are comfortable with case classes, `List`, `Option` and `Either`. You don't need any prior experience with ZIO Prelude, or with functional programming vocabulary, to follow along.

We build one small project over five steps: a validator for orders that arrive as raw, untrusted data. Each step adds one ZIO Prelude idea and builds on the code of the previous step, so the whole page forms a single program.

## Learning Objectives

By the end of this tutorial, you will understand:

- How smart newtypes turn raw `String` and `Int` values into domain types that cannot hold invalid data
- How `Validation` collects every error instead of stopping at the first one
- How `Identity` describes values that can be combined, and how that gives us totals for free
- How `ForEach` runs a validation over a whole collection
- How `ZPure` threads state and errors through a computation without side effects

We'll learn these concepts through:

1. Modeling an order with smart newtypes
2. Validating one order line and accumulating errors
3. Combining values with `Associative` and `Identity`
4. Traversing a whole order with `ForEach`
5. Reserving stock with `ZPure`

We recommend reading from top to bottom, because each step reuses the code of the steps before it.

## Background

Data that enters a program from outside, such as a web form or a JSON payload, can be wrong in many ways at once. A customer can mistype an email address and also enter a negative quantity. If our validator stops at the first problem, the customer fixes one field, submits again, and meets the next error. We want to report all problems in one pass.

ZIO Prelude gives us small, composable pieces for this. Smart newtypes make invalid values impossible to represent after validation. `Validation` collects errors. `Identity` and `ForEach` describe how to combine values and walk collections. `ZPure` handles the stateful part of the job. The rest of this tutorial puts these pieces together.

## 1. Modeling an Order with Smart Newtypes

An order has an id, a customer email, and a list of lines. Each line has a product code (a SKU), a quantity, and a unit price in cents. If we model all of these with `String` and `Int`, the compiler cannot tell a quantity from a price. A newtype gives the same underlying value its own type at no run-time cost, and a smart newtype adds an `assertion` describing which values are valid. This tutorial uses `Subtype`, so each new type can still be used wherever its underlying type is expected, for example to multiply a quantity by a price.

To use the project, add the dependency to `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-prelude" % "1.0.0-RC49"
```

We import the package and the assertions, then define the five smart newtypes. Each one extends `Subtype` with the underlying type and an `assertion`, and is followed by a type alias so we can write `Quantity` instead of `Quantity.Type`:

```scala
import zio.prelude._
import zio.prelude.Assertion._

object OrderId extends Subtype[String] {
  override def assertion = assert { matches("ORD-[0-9]+") }
}
type OrderId = OrderId.Type

object Email extends Subtype[String] {
  override def assertion = assert { matches(".+@.+\\..+") }
}
type Email = Email.Type

object Sku extends Subtype[String] {
  override def assertion = assert { matches("[A-Z]{3}-[0-9]{3}") }
}
type Sku = Sku.Type

object Quantity extends Subtype[Int] {
  override def assertion = assert { greaterThanOrEqualTo(1) }
}
type Quantity = Quantity.Type

object Cents extends Subtype[Int] {
  override def assertion = assert { greaterThanOrEqualTo(0) }
}
type Cents = Cents.Type
```

The code above:

- `Subtype[String]` and `Subtype[Int]` — name the underlying type of each new type
- `assert { ... }` — wraps an `Assertion` so the macro can check it at compile time for literals
- `matches(...)` — requires a string to match a regular expression
- `greaterThanOrEqualTo(...)` — requires a number to be at least the given bound
- `type Sku = Sku.Type` — lets us use the object's name as a type name

When we pass a literal to `Sku.apply`, the assertion is checked while compiling. A valid literal gives us a `Sku` directly:

```scala
val keyboard: Sku = Sku("KEY-001")
// keyboard: Sku = "KEY-001"
```

Notice that an invalid literal such as `Sku("keyboard")` does not compile. For values that only exist at run time, we use `Sku.make`, which checks the assertion while the program runs and returns a `Validation`:

```scala
Sku.make("keyboard")
// res1: ZValidation[Nothing, String, Sku.Type] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(keyboard did not satisfy matches([A-Z]{3}-[0-9]{3}))
// )
```

The result is a `Failure` that carries a readable message. Before we move on, we define the order itself. `RawLine` and `RawOrder` hold the untrusted input, and `OrderLine` and `Order` hold the validated domain values:

```scala
final case class RawLine(sku: String, quantity: Int, unitPrice: Int)
final case class RawOrder(id: String, email: String, lines: List[RawLine])

final case class OrderLine(sku: Sku, quantity: Quantity, unitPrice: Cents)
final case class Order(id: OrderId, email: Email, lines: List[OrderLine])
```

An `Order` cannot be built from a bad email or a zero quantity, because those types only hold values that satisfy their assertion. The remaining question is how to get from a `RawOrder` to an `Order`. For more on smart newtypes, see [Smart Newtypes](../newtypes/smart-newtypes.md).

## 2. Validating a Line and Accumulating Errors

`Sku.make` and the other `make` constructors return a `Validation[String, A]`, which is either a success with an `A` or a failure with one or more `String` errors. The important property is how we combine validations: `Validation.validateWith` takes several validations and a function, and when more than one fails it keeps the errors of all of them.

We validate a single `RawLine` by running the three `make` constructors and combining their results with the `OrderLine` constructor:

```scala
def validateLine(raw: RawLine): Validation[String, OrderLine] =
  Validation.validateWith(
    Sku.make(raw.sku),
    Quantity.make(raw.quantity),
    Cents.make(raw.unitPrice)
  )(OrderLine.apply)
```

Let's try a line that is entirely valid:

```scala
validateLine(RawLine("KEY-001", 2, 1999))
// res2: Validation[String, OrderLine] = Success(
//   log = IndexedSeq(),
//   value = OrderLine(sku = "KEY-001", quantity = 2, unitPrice = 1999)
// )
```

Now try a line where all three fields are wrong. Instead of reporting only the SKU, the result holds all three messages:

```scala
validateLine(RawLine("keyboard", 0, -5))
// res3: Validation[String, OrderLine] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(keyboard did not satisfy matches([A-Z]{3}-[0-9]{3}), 0 did not satisfy greaterThanOrEqualTo(1), -5 did not satisfy greaterThanOrEqualTo(0))
// )
```

:::note
`Validation` is different from `Either`. A for comprehension over `Either` stops at the first `Left`, because each step depends on the previous result. The three validations above are independent of each other, so `Validation.validateWith` can run all of them and collect every failure.
:::

Try changing the quantity to `1` and running again: the list of errors shrinks to two. For the full API, see [Validation](../functional-data-types/validation.md) and [ZValidation](../functional-data-types/zvalidation.md).

## 3. Combining Values with Associative and Identity

A checkout page wants a summary of the order: the number of items, the total price, and the set of products. Computing it means combining one small value per line into one big value. ZIO Prelude describes this with `Associative`, which says "two values of this type can be combined, and the grouping does not matter", and `Identity`, which adds a neutral value that changes nothing when combined.

Many types already have instances. The `Sum` newtype from `zio.prelude.newtypes` selects addition, and `Max` selects the larger value. We combine values with the `<>` operator:

```scala
import zio.prelude.newtypes.{Max, Sum}

Sum(2) <> Sum(3)
// res4: Sum.Type[Int] = 5

Max(2) <> Max(3)
// res5: Max.Type[Int] = 3
```

Notice that the same operator behaves differently for each newtype. The newtype chooses how the values combine, which is why a plain `Int` has no instance: addition and maximum are both valid ways to combine integers.

Now we describe our own summary. We define a case class, and give it an `Identity` instance with `Identity.make`, which takes the neutral value and the combining function. The instance lives in the companion object so it is found automatically:

```scala
final case class Summary(items: Int, totalCents: Long, skus: Set[Sku])

object Summary {
  implicit val SummaryIdentity: Identity[Summary] =
    Identity.make(
      Summary(0, 0L, Set.empty),
      (l, r) => Summary(l.items + r.items, l.totalCents + r.totalCents, l.skus ++ r.skus)
    )

  def of(line: OrderLine): Summary =
    Summary(line.quantity, line.quantity.toLong * line.unitPrice, Set(line.sku))
}
```

The code above:

- `Identity.make(neutral, combine)` — builds the instance from the neutral value and the combining function
- `Summary(0, 0L, Set.empty)` — the neutral summary, which changes nothing when combined
- `Summary.of` — turns one `OrderLine` into a summary of that single line

The combining function is associative because integer addition and set union are. Next, we combine two summaries with the same `<>` operator, and get the neutral value with `Identity#identity`:

```scala
val first  = Summary.of(OrderLine(Sku("KEY-001"), Quantity(2), Cents(1999)))
// first: Summary = Summary(
//   items = 2,
//   totalCents = 3998L,
//   skus = Set("KEY-001")
// )
val second = Summary.of(OrderLine(Sku("MOU-002"), Quantity(1), Cents(2500)))
// second: Summary = Summary(
//   items = 1,
//   totalCents = 2500L,
//   skus = Set("MOU-002")
// )

first <> second
// res6: Summary = Summary(
//   items = 3,
//   totalCents = 6498L,
//   skus = Set("KEY-001", "MOU-002")
// )

Identity[Summary].identity
// res7: Summary = Summary(items = 0, totalCents = 0L, skus = Set())
```

The value `3` items and `6498` cents confirm that two lines were merged. The `Identity` instance has a second benefit: it lets us fold a whole collection into one value, which is the topic of the next step. See [Associative](../functional-abstractions/concrete-types/associative.md) and [Identity](../functional-abstractions/concrete-types/identity.md) for more.

## 4. Traversing a Whole Order with ForEach

An order has many lines, and we need to validate every one of them and collect the errors of all of them. `ForEach` is the abstraction for collections such as `List`, `Chunk` and `Option`. Its operator `ForEach#forEach` runs a function that returns an effect-like value (here a `Validation`) for each element, and gathers the results in one value of the same shape.

We need no new import, because `zio.prelude._` already provides `ForEach#forEach` as an operator on `List`. We validate every line of a `RawLine` list in one expression:

```scala
def validateLines(raws: List[RawLine]): Validation[String, List[OrderLine]] =
  raws.forEach(validateLine)
```

When every line is valid, we get back the full list of `OrderLine` values. When some lines are invalid, we get the errors of every invalid line:

```scala
validateLines(List(RawLine("KEY-001", 2, 1999), RawLine("MOU-002", 1, 2500)))
// res8: Validation[String, List[OrderLine]] = Success(
//   log = IndexedSeq(),
//   value = List(
//     OrderLine(sku = "KEY-001", quantity = 2, unitPrice = 1999),
//     OrderLine(sku = "MOU-002", quantity = 1, unitPrice = 2500)
//   )
// )

validateLines(List(RawLine("KEY-001", 0, 1999), RawLine("mouse", 1, 2500)))
// res9: Validation[String, List[OrderLine]] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(0 did not satisfy greaterThanOrEqualTo(1), mouse did not satisfy matches([A-Z]{3}-[0-9]{3}))
// )
```

Both bad lines are reported together. Now we can validate a whole order: the id, the email and the lines are three independent validations, so we combine them with `Validation.validateWith` as before:

```scala
def validateOrder(raw: RawOrder): Validation[String, Order] =
  Validation.validateWith(
    OrderId.make(raw.id),
    Email.make(raw.email),
    validateLines(raw.lines)
  )(Order.apply)
```

To see accumulation across the whole order, we define one good order and one with five different problems:

```scala
val goodOrder = RawOrder(
  "ORD-1001",
  "ada@example.com",
  List(RawLine("KEY-001", 2, 1999), RawLine("MOU-002", 1, 2500))
)

val badOrder = RawOrder(
  "1002",
  "nope",
  List(RawLine("KEY-001", 0, 1999), RawLine("mouse", 1, -1))
)
```

Validating them shows the contrast between an order we can process and one we can send back with a full list of problems:

```scala
validateOrder(goodOrder)
// res10: Validation[String, Order] = Success(
//   log = IndexedSeq(),
//   value = Order(
//     id = "ORD-1001",
//     email = "ada@example.com",
//     lines = List(
//       OrderLine(sku = "KEY-001", quantity = 2, unitPrice = 1999),
//       OrderLine(sku = "MOU-002", quantity = 1, unitPrice = 2500)
//     )
//   )
// )

validateOrder(badOrder)
// res11: Validation[String, Order] = Failure(
//   log = IndexedSeq(),
//   errors = NonEmptyChunk(1002 did not satisfy matches(ORD-[0-9]+), nope did not satisfy matches(.+@.+\..+), 0 did not satisfy greaterThanOrEqualTo(1), mouse did not satisfy matches([A-Z]{3}-[0-9]{3}), -1 did not satisfy greaterThanOrEqualTo(0))
// )
```

`ForEach` also gives us folds. Because `Summary` has an `Identity`, `foldMap` can turn each line into a `Summary` and combine them all, starting from the neutral value. We use it to total the lines of the valid order:

```scala
validateOrder(goodOrder).map(order => order.lines.foldMap(Summary.of))
// res12: ZValidation[Nothing, String, Summary] = Success(
//   log = IndexedSeq(),
//   value = Summary(
//     items = 3,
//     totalCents = 6498L,
//     skus = Set("KEY-001", "MOU-002")
//   )
// )
```

This is the payoff of the previous step: we never wrote a loop or a mutable accumulator. See [ForEach](../functional-abstractions/parameterized-types/foreach.md) for the full list of operators.

## 5. Reserving Stock with ZPure

Our order is valid, but we still have to reserve its items from stock. Reserving changes the stock level, and it can fail if there is not enough. That is state plus errors, and `ZPure` describes such a computation as a pure value. A `ZPure[W, S1, S2, R, E, A]` takes an initial state of type `S1`, and either fails with an `E` or produces an `A` and a new state `S2`. The log `W` and the environment `R` stay at `Nothing` and `Any` here, because this computation needs neither.

We model the stock as a map from SKU to the number of units available, and write the reservation of a single line. It reads the state with `ZPure.get`, fails with `ZPure.fail` when there is not enough stock, and otherwise writes the new state with `ZPure.set`:

```scala
import zio.prelude.fx.ZPure

type Stock = Map[Sku, Int]

def reserve(line: OrderLine): ZPure[Nothing, Stock, Stock, Any, String, Unit] =
  for {
    stock    <- ZPure.get[Stock]
    available = stock.getOrElse(line.sku, 0)
    _        <- if (available >= line.quantity) ZPure.set(stock.updated(line.sku, available - line.quantity))
                else ZPure.fail(s"only $available of ${line.sku} left, wanted ${line.quantity}")
  } yield ()
```

Next, we reserve every line of an order with `ZPure.forEach`, which is the same traversal idea from the previous step, now over `ZPure` computations. The state flows from one line to the next, and the first failure stops the computation:

```scala
def reserveAll(order: Order): ZPure[Nothing, Stock, Stock, Any, String, Int] =
  ZPure.forEach(order.lines)(reserve).as(order.lines.size)
```

Nothing has run yet, because `reserveAll` only describes the work. We run it with an initial stock using `ZPure#runAll`, which returns the log together with either the error or the final state and the value:

```scala
val initialStock: Stock =
  Map(Sku("KEY-001") -> 5, Sku("MOU-002") -> 1)
```

Reserving the valid order succeeds and returns the remaining stock:

```scala
validateOrder(goodOrder).map(order => reserveAll(order).runAll(initialStock))
// res13: ZValidation[Nothing, String, (zio.Chunk[Nothing], Either[String, (Stock, Int)])] = Success(
//   log = IndexedSeq(),
//   value = (
//     IndexedSeq(),
//     Right(value = (Map("KEY-001" -> 3, "MOU-002" -> 0), 2))
//   )
// )
```

Reserving the same order twice in a row fails the second time, because only one mouse is in stock. We chain the two computations with `*>`, which runs one after the other and keeps the state:

```scala
validateOrder(goodOrder).map(order => (reserveAll(order) *> reserveAll(order)).runAll(initialStock))
// res14: ZValidation[Nothing, String, (zio.Chunk[Nothing], Either[String, (Stock, Int)])] = Success(
//   log = IndexedSeq(),
//   value = (IndexedSeq(), Left(value = "only 0 of MOU-002 left, wanted 1"))
// )
```

The failure is a plain value: no exception was thrown and no `Ref` was mutated. That is the point of `ZPure`, since the same description can be run again from any initial state. See [ZPure](../zpure/index.md) for the other capabilities, such as logging and environments.

## What You've Learned

In this tutorial, you learned:

- How smart newtypes such as `Quantity` and `Sku` reject invalid values, with `Sku.apply` for literals and `Sku.make` for run-time values
- How `Validation.validateWith` combines independent validations and keeps the errors of all of them
- How `Associative` and `Identity` describe combining values, and how to define an instance with `Identity.make`
- How `ForEach` traverses a collection with a validation, and folds it with `foldMap`
- How `ZPure` models stock reservation as a pure computation with state and errors

You now have a complete validator, from raw input to a checked reservation, built from five small ideas.

## Where to Go Next

- To apply one idea on its own, read the how-to guides [How to Create Smart Newtypes](../how-to/create-smart-newtypes.md), [How to Accumulate Validation Errors](../how-to/accumulate-validation-errors.md), [How to Combine Values with Associative and Identity](../how-to/combine-values-with-associative-and-identity.md), [How to Traverse Collections with ForEach](../how-to/traverse-with-foreach.md) and [How to Use ZPure for State and Errors](../how-to/use-zpure-for-state-and-errors.md).
- To go deeper into the API, read [Smart Newtypes](../newtypes/smart-newtypes.md), [ZValidation](../functional-data-types/zvalidation.md), [Identity](../functional-abstractions/concrete-types/identity.md), [ForEach](../functional-abstractions/parameterized-types/foreach.md) and [ZPure](../zpure/index.md).
