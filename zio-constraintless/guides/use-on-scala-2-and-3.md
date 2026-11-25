# How to Use Constraintless on Scala 2 and Scala 3

> Write the same expression language with implicit parameters on Scala 2 and with using clauses on Scala 3.

In this guide, we write the same small program for Scala 2 and for Scala 3 and see where the two differ. By the end, you will be able to translate a program between the two versions or share a design between projects on different versions.

The library itself is the same on both versions: [`TypeList`](../reference.md#typelist), [`IsElementOf`](../reference.md#iselementof), [`AreElementsOf`](../reference.md#areelementsof) and [`Instances`](../reference.md#instances) have the same members. Only the way a program asks for evidence and instances changes.

## The Problem

Examples of ZIO Constraintless are written for one Scala version, and the syntax for passing evidence differs between the two. On Scala 2 the evidence is an implicit parameter and wildcard imports use `_`. On Scala 3, parameters are passed with `using` and imports use `*`. Copying code from one version to the other fails with errors that do not mention the library.

## Prerequisites

The dependency is the one from [Installation](../index.md#installation). The Scala 3 artifact is compiled with Scala 3.8.2, so a Scala 3 project needs Scala 3.8 or newer. We use a program with two allowed types, `Int` and `Boolean`, and a `Show` type class.

## Define the Type Class and Its Instances

On Scala 2 the instances are `implicit val` definitions. On Scala 3 they are `given` instances:

  

**Scala 2**

```scala
import zio.constraintless._
import zio.constraintless.TypeList._

type Allowed = Int :: Boolean :: End

trait Show[A] {
  def show(a: A): String
}

object Show {
  implicit val int: Show[Int] = (a: Int) => a.toString
  implicit val boolean: Show[Boolean] = (a: Boolean) => a.toString
}
```

  
  

**Scala 3**

```scala
import zio.constraintless.*
import zio.constraintless.TypeList.*

type Allowed = Int :: Boolean :: End

trait Show[A]:
  def show(a: A): String

object Show:
  given Show[Int] = (a: Int) => a.toString
  given Show[Boolean] = (a: Boolean) => a.toString
```

  

## Define the Expression and the Interpreter

On Scala 3, an `enum` can hold the evidence in a `using` clause of its case. An interpreter then asks for `Instances` with `using`, and passes the evidence to `Instances#withInstance` explicitly with `using`. The Scala 3 code writes the evidence as `IsElementOf[A, As]`, because the compiler warns about alphanumeric infix types when `IsElementOf` is written infix:

  

**Scala 2**

```scala
sealed trait Expr[As <: TypeList, A]
case class Value[As <: TypeList, A](a: A, ev: A IsElementOf As) extends Expr[As, A]
case class Cond[As <: TypeList, A](
  test: Expr[As, Boolean],
  ifTrue: Expr[As, A],
  ifFalse: Expr[As, A]
) extends Expr[As, A]

def pretty[As <: TypeList, A](e: Expr[As, A])(implicit show: Instances[Show, As]): String =
  e match {
    case Value(a, ev) =>
      show.withInstance((s: Show[A]) => s.show(a))(ev)
    case Cond(test, ifTrue, ifFalse) =>
      s"if ${pretty(test)} then ${pretty(ifTrue)} else ${pretty(ifFalse)}"
  }
```

  
  

**Scala 3**

```scala
enum Expr[As <: TypeList, A]:
  case Value(a: A)(using val ev: IsElementOf[A, As])
  case Cond(test: Expr[As, Boolean], ifTrue: Expr[As, A], ifFalse: Expr[As, A])

def pretty[As <: TypeList, A](e: Expr[As, A])(using show: Instances[Show, As]): String =
  e match
    case v: Expr.Value[As, A] =>
      show.withInstance((s: Show[A]) => s.show(v.a))(using v.ev)
    case c: Expr.Cond[As, A] =>
      s"if ${pretty(c.test)} then ${pretty(c.ifTrue)} else ${pretty(c.ifFalse)}"
```

  

## Build and Run an Expression

The two programs build the same expression and print the same text. On Scala 3 the evidence of each `Value` is found by the compiler from the expected type:

  

**Scala 2**

```scala
val expr: Expr[Allowed, Int] =
  Cond(
    Value(true, IsElementOf[Boolean, Allowed]),
    Value(1, IsElementOf[Int, Allowed]),
    Value(0, IsElementOf[Int, Allowed])
  )
// expr: Expr[Allowed, Int] = Cond(
//   test = Value(a = true, ev = Tail(ev = Head())),
//   ifTrue = Value(a = 1, ev = Head()),
//   ifFalse = Value(a = 0, ev = Head())
// )

pretty(expr)
// res1: String = "if true then 1 else 0"
```

  
  

**Scala 3**

```scala
val expr: Expr[Allowed, Int] =
  Expr.Cond(Expr.Value(true), Expr.Value(1), Expr.Value(0))

println(pretty(expr))
```

  

On Scala 3 the program prints `if true then 1 else 0`.

:::note
The Scala 3 snippets on this page are not compiled by the documentation build, which runs on Scala 2.13. They are compiled and run separately against the released library on Scala 3.8.2.
:::

## Going Further

For a walkthrough that builds an expression language step by step, see the [tutorial](../tutorial.md). For the members of each type, see the [Reference](../reference.md).
