# Why Not the Hughes Schema?

> What the Hughes schema is, how it gives an interpreter its type class instances, and why it cannot serve two interpreters at once.

The paper [Deconstraining](https://web.archive.org/web/20260115140505/http://www.doc.ic.ac.uk/~wlj05/files/Deconstraining.pdf), on which ZIO Constraintless is based, first shows an approach that works for one interpreter and then shows why it breaks for two. The paper describes that approach as a scheme analogous to the one in John Hughes' 1999 paper "Restricted Data Types in Haskell", and this page calls it the Hughes schema. This page explains it from the beginning, assuming only the ideas from [Understanding ZIO Constraintless](./index.md): a description of a program, an interpreter, and a type class.

## The Problem It Tries to Solve

Suppose we have a description language `Expr` and an interpreter that needs a type class instance for the values inside an expression, such as `Show` to print them. As shown on the [Concepts](./index.md#interpreters-need-information-about-types) page, the interpreter cannot get those instances from the type of the whole expression. The simplest fix is to demand the instance in the node that stores the value. This restricts the description itself: after adding `Show`, every `Value` must hold a type with a `Show` instance, for all interpreters, forever.

The Hughes schema tries to restrict only the interpreter, and leave the description alone.

## The Idea: Make the Interpreter a Type Parameter

The schema adds a type parameter `P` to the description. `P` stands for the target platform, which here means the interpreter: one `P` for the printer, another `P` for the interpreter that converts to integers. A node that stores a value no longer demands `Show[A]`. It demands a more general type class that says "type `A` is supported on platform `P`" and, when asked, turns an `A` into the platform's own representation. The paper calls it `Typeable`:

```haskell
class Typeable p a where
  valueP :: a -> p a

data Exp p a where
  ValueE :: Typeable p a => a -> Exp p a
  CondE  :: Exp p Bool -> Exp p a -> Exp p a -> Exp p a
  EqE    :: Eq a => Exp p a -> Exp p a -> Exp p Bool
```

The snippets in this section are Haskell, taken from the paper; they are not compiled. Here is the same idea in Scala, which is compiled by this page. First the two ordinary type classes that two interpreters need, and the `Typeable` class:

```scala
trait Show[A] {
  def show(a: A): String
}

object Show {
  implicit val int: Show[Int] = (a: Int) => a.toString
  implicit val boolean: Show[Boolean] = (a: Boolean) => a.toString
}

trait ToInt[A] {
  def toInt(a: A): Int
}

object ToInt {
  implicit val int: ToInt[Int] = (a: Int) => a
  implicit val boolean: ToInt[Boolean] = (a: Boolean) => if (a) 1 else 0
}

trait Typeable[P[_], A] {
  def valueP(a: A): P[A]
}
```

Each platform is a type with one parameter that holds what the interpreter produces. The printer produces text, and the integer interpreter produces a number. Each platform gets a `Typeable` instance that requires exactly the type class that interpreter needs:

```scala
case class Pretty[A](text: String)
case class AsInt[A](value: Int)

object Typeable {
  implicit def pretty[A](implicit s: Show[A]): Typeable[Pretty, A] =
    (a: A) => Pretty[A](s.show(a))

  implicit def asInt[A](implicit t: ToInt[A]): Typeable[AsInt, A] =
    (a: A) => AsInt[A](t.toInt(a))
}
```

The description now carries the platform `P`. The `Value` node asks for `Typeable[P, A]` and keeps it:

```scala
sealed trait Expr[P[_], A]

case class Value[P[_], A](a: A)(implicit val typeable: Typeable[P, A]) extends Expr[P, A]

case class Cond[P[_], A](
  test: Expr[P, Boolean],
  ifTrue: Expr[P, A],
  ifFalse: Expr[P, A]
) extends Expr[P, A]
```

Each interpreter works on its own instantiation of `P`:

```scala
def pretty[A](e: Expr[Pretty, A]): String =
  e match {
    case v: Value[Pretty, A] =>
      v.typeable.valueP(v.a).text
    case Cond(test, ifTrue, ifFalse) =>
      s"if ${pretty(test)} then ${pretty(ifTrue)} else ${pretty(ifFalse)}"
  }

def leafSum[A](e: Expr[AsInt, A]): Int =
  e match {
    case v: Value[AsInt, A] =>
      v.typeable.valueP(v.a).value
    case Cond(test, ifTrue, ifFalse) =>
      leafSum(test) + leafSum(ifTrue) + leafSum(ifFalse)
  }
```

This works for one interpreter at a time, and the description was not restricted: the `Show` requirement lives in the `Typeable[Pretty, A]` instance, so it applies only when `P` is `Pretty`. We build an expression for the printer and print it:

```scala
val forPrinter: Expr[Pretty, Int] = Cond(Value(true), Value(1), Value(0))
// forPrinter: Expr[Pretty, Int] = Cond(
//   test = Value(a = true),
//   ifTrue = Value(a = 1),
//   ifFalse = Value(a = 0)
// )

pretty(forPrinter)
// res0: String = "if true then 1 else 0"
```

## The Problem: One Expression Cannot Serve Two Interpreters

The platform `P` is part of the type of the expression. When we build `forPrinter`, the compiler fixes `P` to `Pretty`, and every `Value` inside it holds a `Typeable[Pretty, _]`. The expression is an `Expr[Pretty, Int]` and can never be an `Expr[AsInt, Int]`, so the other interpreter rejects it:

```scala
leafSum(forPrinter)
// error: type mismatch;
//  found   : repl.MdocSession.MdocApp.Expr[repl.MdocSession.MdocApp.Pretty,Int]
//  required: repl.MdocSession.MdocApp.Expr[repl.MdocSession.MdocApp.AsInt,?]
```

A function that wants to use the same expression in two ways is therefore impossible. This is the Haskell version from the paper:

```haskell
f :: Exp p a -> ...
f e = ...(compileSM e)...(pretty e)...
```

Here `compileSM` needs an `Exp SM a` and `pretty` needs an `Exp Pretty a`. Typechecking the two calls would have to instantiate `p` to two different types at once, so `f` is rejected. In Scala the equivalent is:

```scala
def both[P[_], A](e: Expr[P, A]): (String, Int) = (pretty(e), leafSum(e))
// error: type mismatch;
//  found   : repl.MdocSession.MdocApp.Expr[P,A]
//  required: repl.MdocSession.MdocApp.Expr[repl.MdocSession.MdocApp.Pretty,?]
// def both[P[_], A](e: Expr[P, A]): (String, Int) = (pretty(e), leafSum(e))
//                                                           ^
// error: type mismatch;
//  found   : repl.MdocSession.MdocApp.Expr[P,A]
//  required: repl.MdocSession.MdocApp.Expr[repl.MdocSession.MdocApp.AsInt,?]
// def both[P[_], A](e: Expr[P, A]): (String, Int) = (pretty(e), leafSum(e))
//                                                                       ^
```

## The Usual Fix and Why It Does Not Work

In Haskell, a function can accept an argument that works for every platform by quantifying over `p` inside a wrapper type. A type that contains such a quantifier is a higher-rank type, and the paper tries it first:

```haskell
newtype AnyExp a = AnyExp (forall p. Exp p a)
```

An `AnyExp a` is an expression that exists for every platform at once, so `f` can use it with `compileSM` and with `pretty`. The paper shows that the fix fails in two steps.

First, building an `AnyExp` such as `AnyExp (ValueE False)` does not typecheck, because the type of `AnyExp` has no `Typeable p Bool` constraint. The type must list the requirement:

```haskell
newtype AnyExp a = AnyExp (forall p. Typeable p a => Exp p a)
```

Second, this constrains only the type of the whole expression. An expression such as a `CondE` over an integer test and `Float` branches contains values of several types, and the compiler needs a guarantee that every type in it is supported by `p`. The wrapper must name all of them:

```haskell
newtype AnyExp a = AnyExp (forall p. (Typeable p τ1, Typeable p τ2, ..., Typeable p τn) => Exp p a)
```

The paper calls this "a mess", and also observes that every platform must now support at least the types `τ1` to `τn`, "which defeats the purpose of abstracting `p` in the first place". Constraining each function over the description instead does not help either: the same list of constraints appears, only closer to the function.

## How ZIO Constraintless Differs

The Hughes schema fails because the interpreter is part of the type of the expression, so an expression has exactly one. In ZIO Constraintless, the expression does not mention the interpreter at all. Its type parameter is a [`TypeList`](../reference.md#typelist) of the allowed types, and each interpreter supplies its own [`Instances`](../reference.md#instances) when it runs. The same `Expr[As, A]` value can therefore be passed to `pretty` and to `toInt`, and there is no list of constraints to repeat, because the list of types is the one `TypeList` in the type of the expression. For a working version of this, see the [tutorial](../tutorial.md).
