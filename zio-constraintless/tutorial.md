# Build an Expression Language with Two Interpreters

> Build a small expression language and run a pretty-printer and an Int converter over the same expression.

Welcome! This tutorial is for Scala developers who know case classes, sealed traits and implicit parameters. You don't need any prior experience with type-level programming to follow along.

## Learning Objectives

By the end of this tutorial, you will understand:

- How to describe the allowed types of a program with a `TypeList`
- How to store `IsElementOf` evidence in the nodes of an expression tree
- How to write interpreters that bring their own type class through `Instances`
- Why one expression can be run by several interpreters without casting

We'll learn these concepts through:

1. Choosing the allowed types
2. Writing the expression tree
3. Writing the first interpreter
4. Writing a second interpreter
5. Running both on one expression
6. Allowing a new type
7. Letting the compiler reject mistakes

We recommend reading from top to bottom: each section builds on the previous one.

## Background

An expression tree is a description of a program, and interpreters give it meaning. When an interpreter visits a leaf that holds a value of type `A`, the compiler has forgotten what `A` was, so the interpreter cannot print the value or convert it without extra information. In this tutorial the tree records only which types are allowed, and each interpreter brings the extra information it needs. To read more about the idea, see [Understanding ZIO Constraintless](./explanation/index.md).

## 1. Choose the Allowed Types

Let's start with the types our expressions may contain. We make a [`TypeList`](./reference.md#typelist) of `Int` and `Boolean`:

```scala
import zio.constraintless._
import zio.constraintless.TypeList._

type Allowed = Int :: Boolean :: End
```

The line `type Allowed = Int :: Boolean :: End` builds a list with the head `Int`, then `Boolean`, then the empty list `End`. Notice that no type class is mentioned: the list only says which types are allowed.

## 2. Write the Expression Tree

Our tree has two nodes: `Value` holds a value, and `Cond` chooses between two branches. Each node that holds a value of type `A` also holds [`IsElementOf`](./reference.md#iselementof) evidence that `A` is allowed:

```scala
sealed trait Expr[As <: TypeList, A]

case class Value[As <: TypeList, A](a: A, ev: A IsElementOf As) extends Expr[As, A]

case class Cond[As <: TypeList, A](
  test: Expr[As, Boolean],
  ifTrue: Expr[As, A],
  ifFalse: Expr[As, A]
) extends Expr[As, A]
```

`Expr[As <: TypeList, A]` is an expression that may contain the types in `As` and evaluates to an `A`. `Value` stores the evidence `ev` next to the value, so any interpreter can later find the instance for `A`. `Cond` needs no evidence itself, because its children are already constrained by the same `As`.

To build expressions without writing the evidence by hand, we add two helper functions. The compiler derives the evidence from the expected type:

```scala
def value[As <: TypeList, A](a: A)(implicit ev: A IsElementOf As): Expr[As, A] =
  Value(a, ev)

def cond[As <: TypeList, A](
  test: Expr[As, Boolean],
  ifTrue: Expr[As, A],
  ifFalse: Expr[As, A]
): Expr[As, A] =
  Cond(test, ifTrue, ifFalse)
```

Now we can build an expression. Try changing `1` and `0` to see how the tree changes:

```scala
val expr: Expr[Allowed, Int] =
  cond(value(true), value(1), value(0))
```

## 3. Write the First Interpreter

Our first interpreter pretty-prints an expression. It needs a way to show a value, so we define a `Show` type class with an instance for each allowed type:

```scala
trait Show[A] {
  def show(a: A): String
}

object Show {
  implicit val int: Show[Int] = (a: Int) => a.toString
  implicit val boolean: Show[Boolean] = (a: Boolean) => a.toString
}
```

The interpreter asks for [`Instances[Show, As]`](./reference.md#instances), which holds a `Show` instance for every type in `As`:

```scala
def pretty[As <: TypeList, A](e: Expr[As, A])(implicit show: Instances[Show, As]): String =
  e match {
    case Value(a, ev) =>
      show.withInstance((s: Show[A]) => s.show(a))(ev)
    case Cond(test, ifTrue, ifFalse) =>
      s"if ${pretty(test)} then ${pretty(ifTrue)} else ${pretty(ifFalse)}"
  }
```

`Instances[Show, As]` is requested once, in the signature, and the compiler builds it at the call site. The method `Instances#withInstance` selects the `Show[A]` instance for the leaf, using the evidence `ev` stored in the node. No `asInstanceOf` appears in our code.

To pretty-print the expression built above:

```scala
pretty(expr)
// res0: String = "if true then 1 else 0"
```

## 4. Write a Second Interpreter

A second interpreter needs a different type class. It collects every leaf of the tree as an `Int`, so it requires a `ToInt` instance for each allowed type:

```scala
trait ToInt[A] {
  def toInt(a: A): Int
}

object ToInt {
  implicit val int: ToInt[Int] = (a: Int) => a
  implicit val boolean: ToInt[Boolean] = (a: Boolean) => if (a) 1 else 0
}

def leaves[As <: TypeList, A](e: Expr[As, A])(implicit ints: Instances[ToInt, As]): List[Int] =
  e match {
    case Value(a, ev) =>
      List(ints.withInstance((i: ToInt[A]) => i.toInt(a))(ev))
    case Cond(test, ifTrue, ifFalse) =>
      leaves(test) ++ leaves(ifTrue) ++ leaves(ifFalse)
  }
```

Notice that `Expr`, `Value` and `Cond` stay unchanged. The new interpreter brings `ToInt` at the point of use.

## 5. Run Both on One Expression

Both interpreters accept the same `expr` value:

```scala
pretty(expr)
// res1: String = "if true then 1 else 0"

leaves(expr)
// res2: List[Int] = List(1, 1, 0)
```

This is the property the library exists for. The expression is built once, and each interpreter uses the type class it needs.

:::note
With the Hughes schema, the type of the expression would be tied to one interpreter, so the second call would be impossible. For the explanation, see [Why Not the Hughes Schema?](./explanation/why-not-the-hughes-schema.md).
:::

## 6. Allow a New Type

To allow `String` in expressions, we add it to the list and give each interpreter's type class an instance for it:

```scala
type Extended = Int :: Boolean :: String :: End

implicit val showString: Show[String] = (a: String) => "\"" + a + "\""
implicit val stringToInt: ToInt[String] = (a: String) => a.length
```

We define the instances next to the type classes' other instances in a real program. Now an expression can hold strings, and `Expr`, `pretty` and `leaves` are unchanged:

```scala
val greeting: Expr[Extended, String] =
  cond(value(false), value("hello"), value("goodbye"))
// greeting: Expr[Extended, String] = Cond(
//   test = Value(a = false, ev = Tail(ev = Head())),
//   ifTrue = Value(a = "hello", ev = Tail(ev = Tail(ev = Head()))),
//   ifFalse = Value(a = "goodbye", ev = Tail(ev = Tail(ev = Head())))
// )

pretty(greeting)
// res3: String = "if false then \"hello\" else \"goodbye\""

leaves(greeting)
// res4: List[Int] = List(0, 5, 7)
```

## 7. Let the Compiler Reject Mistakes

A value whose type is not in the list cannot be put in the tree, because no `IsElementOf` evidence exists for it. Trying to use a `Double` in an `Allowed` expression is a compile error:

```scala
val bad: Expr[Allowed, Double] = value(1.5)
// error: could not find implicit value for parameter ev: zio.constraintless.IsElementOf[Double,repl.MdocSession.MdocApp.Allowed]
// val bad: Expr[Allowed, Double] = value(1.5)
//                                  ^^^^^^^^^^
```

If a type is in the list but an interpreter has no instance for it, the interpreter cannot be called on any expression over that list. Let's add `Double` to the list without a `Show[Double]`:

```scala
type WithDouble = Int :: Boolean :: Double :: End

val numbers: Expr[WithDouble, Double] = value(1.5)

pretty(numbers)
// error: could not find implicit value for parameter show: zio.constraintless.Instances[repl.MdocSession.MdocApp.Show,repl.MdocSession.MdocApp.WithDouble]
// pretty(numbers)
// ^^^^^^^^^^^^^^^
```

Both mistakes are found by the compiler, at the place where the problem is, and neither can crash at runtime.

## What You've Learned

In this tutorial, you learned:

- How to describe the allowed types of a program with a `TypeList`
- How to store `IsElementOf` evidence in the nodes of an expression tree
- How to write interpreters that bring their own type class through `Instances`
- Why one expression can be run by several interpreters without casting

You now have a solid foundation in ZIO Constraintless. The next step is to apply it to your own data types.

## Where to Go Next

- **Ready to use this in practice?** Read [How to Add an Interpreter to an Existing Expression Tree](./guides/add-an-interpreter.md) or [How to Allow a New Type in a Type List](./guides/allow-a-new-type.md).
- **Want to dive deeper into the API?** Read the [Reference](./reference.md).
- **Interested in the idea behind the library?** Read [Understanding ZIO Constraintless](./explanation/index.md).
