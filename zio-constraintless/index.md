# Understanding ZIO Constraintless

> Why programs as descriptions need type constraints at the point of use, and how ZIO Constraintless provides them, explained from scratch.

This page explains the problem ZIO Constraintless solves and the idea behind its solution, starting from the basics. You need to know Scala syntax, `case class` and `sealed trait`; everything else is introduced here. It does not teach the API; for that, see [Getting Started](../getting-started.md) and the [Reference](../reference.md).

## Programs as Descriptions

An ordinary function does its work immediately: when we call it, it runs. A different style is to build a value that only describes the work, and to run that value later with a separate piece of code. The value is the description, and the code that runs it is an interpreter.

Here is a tiny description language. An expression is a plain value, a choice between two expressions, or a test of whether two expressions are equal:

```scala
sealed trait Expr[A]
case class Value[A](a: A) extends Expr[A]
case class Cond[A](test: Expr[Boolean], ifTrue: Expr[A], ifFalse: Expr[A]) extends Expr[A]
case class Equal[A](left: Expr[A], right: Expr[A]) extends Expr[Boolean]
```

The type parameter `A` is the type of the result the expression produces, so the compiler rejects a `Cond` whose test is not a `Boolean` expression. Building an expression computes nothing:

```scala
val program: Expr[Int] = Cond(Equal(Value(1), Value(1)), Value(10), Value(20))
```

`program` is only data. One interpreter that runs it is `eval`:

```scala
def eval[A](e: Expr[A]): A =
  e match {
    case Value(a)                    => a
    case Cond(test, ifTrue, ifFalse) => if (eval(test)) eval(ifTrue) else eval(ifFalse)
    case Equal(left, right)          => eval(left) == eval(right)
  }
```

```scala
eval(program)
// res0: Int = 10
```

The benefit of a description is that we can write more than one interpreter for it: one that runs it, one that prints it, one that checks or optimises it, or one that sends it to another machine. Such descriptions are common in execution planners, query builders and configuration languages.

Notice `Equal`: it compares two expressions of some type `A` and produces an `Expr[Boolean]`. The `A` appears in the fields of the node but not in its own type. A data type whose nodes can produce a different type than the one they hold is called a generalised algebraic data type (GADT). When an interpreter matches on an `Equal` node, the compiler knows that there is some type `A` inside, but not which one: the type is existential.

## Interpreters Need Information About Types

Running a program, as `eval` does, needs nothing from the stored values. Most other interpreters do. To print a value, the interpreter must know how to turn a value of type `A` into text, but `A` can be any type.

Scala solves this kind of problem with type classes. A type class is a trait, such as `Show[A]`, that describes an operation on a type from the outside, together with one instance per type that supports it:

```scala
trait Show[A] {
  def show(a: A): String
}

object Show {
  implicit val int: Show[Int] = (a: Int) => a.toString
  implicit val boolean: Show[Boolean] = (a: Boolean) => if (a) "yes" else "no"
}
```

The natural attempt at a printing interpreter asks for the `Show` instance of the result type:

```scala
def pretty[A](e: Expr[A])(implicit show: Show[A]): String =
  e match {
    case Value(a) =>
      show.show(a)
    case Cond(test, ifTrue, ifFalse) =>
      s"if ${pretty(test)} then ${pretty(ifTrue)} else ${pretty(ifFalse)}"
    case Equal(left, right) =>
      s"${pretty(left)} == ${pretty(right)}"
  }
// error: could not find implicit value for parameter show: repl.MdocSession.MdocApp.Show[Any]; incompatible interpolation method s
//       s"${pretty(left)} == ${pretty(right)}"
//       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
```

This does not compile. The `Show[A]` in the signature belongs to the result type of the whole expression. The two sides of an `Equal` have a hidden type that the interpreter knows nothing about, so the compiler cannot find a `Show` instance for it; the error mentions `Show[Any]` because `Any` is all the compiler can say about that type. In general, an expression can contain values of many types that never appear in the type of the expression as a whole.

## Three Naive Solutions

There are three ways to give an interpreter that information without this library, and each has a cost.

The first is to store the instance in the data at the definition site: `Value` takes an implicit `Show[A]` and keeps it. Then `pretty` can read it out of every node, but the description is tied to one interpreter. A second interpreter that needs a `ToInt[A]` cannot be written without changing every node, and every constraint any interpreter might ever need ends up in the definition. Worse, a value whose type has no `Show` instance can no longer be put into the description at all, even though we may never print it.

The second is to duplicate the whole data type once per interpreter, with different constraints on each copy. Every new interpreter means a new copy of the type and of everything that builds it.

The third is to give up on type safety: the interpreter takes `Any` and calls `asInstanceOf`. That moves the safety of the program from the compiler to the author of the interpreter, and a mistake becomes a runtime crash.

A fourth approach, from the research behind this library, is explained on its own page, [Why Not the Hughes Schema?](./why-not-the-hughes-schema.md). It makes the interpreter a type parameter of the description, and it breaks down as soon as one expression has to be used by two interpreters.

## The Idea

The paper behind this library puts the solution in one sentence: constraints belong at the point of use of a data type, not at the point of definition. In ZIO Constraintless the definition of the data type records only which types are allowed in it, as a type-level list called a [`TypeList`](../reference.md#typelist). Each node carries [`IsElementOf`](../reference.md#iselementof) evidence, a proof that its type is one of the allowed types.

An interpreter then asks for [`Instances`](../reference.md#instances) of its own type class for the whole list: a bundle holding one instance for each allowed type. Because every node's evidence says "my type is in the list", the interpreter can pick the instance for that node's type out of the bundle. The following diagram contrasts the definition site with the point of use:

```
        definition site                            point of use
┌──────────────────────────────┐      ┌────────────────────────────────────┐
│ Expr[As <: TypeList, A]      │      │ pretty: needs Instances[Show, As]  │
│   each node proves that      │ ───▶ │ toInt:  needs Instances[ToInt, As] │
│   A is an element of As      │      │                                    │
└──────────────────────────────┘      └────────────────────────────────────┘
   knows only "which types"             brings its own type class
```

The data type is defined once. Adding a new interpreter means adding a new function with a new `Instances` requirement, and the compiler checks that every allowed type has the instances that interpreter needs. The error appears where the interpreter is called, not inside the description.

## Why There Is One Cast Inside the Library

`Instances#withInstance` is implemented with a single `asInstanceOf`, inside the library. The membership evidence is what makes it sound: a proof that `B` is the head of the list means the head instance is a `TypeClass[B]`. Users of the library never write that cast. For the details, see [`Instances`](../reference.md#instances).

## Further Reading

To see the idea working, follow the [tutorial](../tutorial.md), which builds an expression language with two interpreters. The paper itself and other links are collected on the [Resources](../resources.md) page.
