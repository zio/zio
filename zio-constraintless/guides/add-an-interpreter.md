# How to Add an Interpreter to an Existing Expression Tree

> Add a new interpreter with its own type class to an expression tree without changing the tree.

In this guide, we add a second interpreter to an expression tree that already has one, without changing the tree's definition. By the end, you will have a JSON renderer that runs over the same expression as an existing pretty-printer.

We'll start from a tree with a pretty-printer, define a new type class, write the interpreter, and run both.

## The Problem

Say an expression tree can only be printed, because each node stores the `Show` instance of its value. A second interpreter, such as a JSON renderer, then needs a different instance in every node:

```scala
trait Show[A] { def show(a: A): String }

sealed trait Expr[A]
case class Value[A](a: A, show: Show[A]) extends Expr[A]
case class Pair[A, B](left: Expr[A], right: Expr[B]) extends Expr[(A, B)]
```

To support JSON, we would have to add a `Json[A]` field to `Value`, change every place that builds a `Value`, and do the same again for the next interpreter. In this guide, we solve it by moving the constraint out of the tree and into the interpreter.

## Prerequisites

We use the dependency from [Installation](../index.md#installation) and the following imports:

```scala
import zio.constraintless._
import zio.constraintless.TypeList._
```

## The Existing Tree

The tree records the allowed types in a [`TypeList`](../reference.md#typelist) and stores [`IsElementOf`](../reference.md#iselementof) evidence in each value. The existing pretty-printer asks for `Instances[Show, As]`:

```scala
type Allowed = Int :: String :: End

trait Show[A] {
  def show(a: A): String
}

object Show {
  implicit val int: Show[Int] = (a: Int) => a.toString
  implicit val string: Show[String] = (a: String) => a
}

sealed trait Expr[As <: TypeList, A]
case class Value[As <: TypeList, A](a: A, ev: A IsElementOf As) extends Expr[As, A]
case class Sum[As <: TypeList](left: Expr[As, Int], right: Expr[As, Int]) extends Expr[As, Int]

def pretty[As <: TypeList, A](e: Expr[As, A])(implicit show: Instances[Show, As]): String =
  e match {
    case Value(a, ev) => show.withInstance((s: Show[A]) => s.show(a))(ev)
    case Sum(left, right) => s"${pretty(left)} + ${pretty(right)}"
  }
```

## Define the New Type Class

The new interpreter renders values as JSON text. We define the type class it needs and an instance for every allowed type:

```scala
trait Json[A] {
  def json(a: A): String
}

object Json {
  implicit val int: Json[Int] = (a: Int) => a.toString
  implicit val string: Json[String] = (a: String) => "\"" + a + "\""
}
```

## Write the Interpreter

The interpreter has the same shape as `pretty`. It requires `Instances[Json, As]` and selects the instance for each value with the evidence stored in the node:

```scala
def toJson[As <: TypeList, A](e: Expr[As, A])(implicit json: Instances[Json, As]): String =
  e match {
    case Value(a, ev) =>
      json.withInstance((j: Json[A]) => j.json(a))(ev)
    case Sum(left, right) =>
      s"""{"sum":[${toJson(left)},${toJson(right)}]}"""
  }
```

`Expr`, `Value`, `Sum` and `pretty` are unchanged.

## Run Both Interpreters

We build one expression and pass it to both interpreters:

```scala
val expr: Expr[Allowed, Int] =
  Sum(Value(1, IsElementOf[Int, Allowed]), Value(2, IsElementOf[Int, Allowed]))
// expr: Expr[Allowed, Int] = Sum(
//   left = Value(a = 1, ev = Head()),
//   right = Value(a = 2, ev = Head())
// )

pretty(expr)
// res2: String = "1 + 2"

toJson(expr)
// res3: String = "{\"sum\":[1,2]}"
```

:::tip
When an interpreter should be usable for every type list that has the right instances, keep `As` abstract in its signature, as `toJson` does. The type list is fixed only where the interpreter is called.
:::

## Going Further

To make a new type usable by every interpreter, see [How to Allow a New Type in a Type List](./allow-a-new-type.md). For the signature of `Instances#withInstance`, see the [Reference](../reference.md#instances).
