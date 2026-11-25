# How to Allow a New Type in a Type List

> Add a type to a TypeList and supply the missing type class instances so every interpreter keeps compiling.

In this guide, we add `Double` to the allowed types of an existing program and fix the compile errors that follow. By the end, you will know where each missing instance is reported and how to supply it.

We'll add the type to the list, read the error, and add the instances one interpreter at a time.

## The Problem

The allowed types of a program live in one [`TypeList`](../reference.md#typelist), and every interpreter needs an instance of its type class for each of them. Adding a type therefore affects every interpreter, and forgetting one of them must not turn into a runtime crash. With ZIO Constraintless the compiler reports the missing instance, and in this guide we use that report to find every place that needs work.

## Prerequisites

We use the dependency from [Installation](../index.md#installation). The program has one data type and one interpreter, and the allowed types are `Int` and `Boolean`:

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

case class Value[As <: TypeList, A](a: A, ev: A IsElementOf As)

def pretty[As <: TypeList, A](v: Value[As, A])(implicit show: Instances[Show, As]): String =
  show.withInstance((s: Show[A]) => s.show(v.a))(v.ev)
```

## Add the Type to the List

We define the new list. The type `Double` is now allowed, so a `Value` can hold one:

```scala
type Extended = Int :: Boolean :: Double :: End

val pi: Value[Extended, Double] =
  Value(3.14, IsElementOf[Double, Extended])
```

Constructing the value needs no instance. Only the interpreters that run on the new list do.

## Read the Error

Running `pretty` on the new list needs [`Instances[Show, Extended]`](../reference.md#instances), and there is no `Show[Double]` yet. The compiler does not accept the call:

```scala
pretty(pi)
// error: could not find implicit value for parameter show: zio.constraintless.Instances[MdocApp0.this.Show,MdocApp0.this.Extended]
```

The error is reported at the call, which is where an instance for `Double` is missing. It does not point at the list, because the list is correct: `Double` is allowed, and only the interpreter lacks what it needs.

## Supply the Instance

We add the instance to the type class's companion object, so every interpreter that uses `Show` finds it. Because `Show` is already defined in this page, we define the instance next to it here:

```scala
implicit val showDouble: Show[Double] = (a: Double) => a.toString
```

Now the same call compiles:

```scala
pretty(pi)
// res2: String = "3.14"
```

## Repeat for Every Interpreter

Each interpreter with its own type class needs its own instance. Instances are required wherever `Instances[TypeClass, Extended]` is requested, so a program with three type classes needs three new instances. The compiler lists the missing ones as each interpreter is called.

:::tip
Keep a program's type lists in one place, as a single `type` alias. A new type is then one line in the list plus one instance per type class.
:::

## Going Further

To add a type class instead of a type, see [How to Add an Interpreter to an Existing Expression Tree](./add-an-interpreter.md). For how `Instances` is built from the list, see the [Reference](../reference.md#instances).
