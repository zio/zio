# Introduction to ZIO Constraintless

> ZIO Constraintless allows you to build programs as mere descriptions with maximum polymorphism, maximum modularity, zero abstraction leakage, and zero casting.

ZIO Constraintless allows you to build programs as mere descriptions with maximum polymorphism, maximum modularity, zero abstraction leakage, and zero casting.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-constraintless/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-constraintless_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-constraintless_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-constraintless_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-constraintless_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-constraintless-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-constraintless-docs_2.13) [![ZIO Constraintless](https://img.shields.io/github/stars/zio/zio-constraintless?style=social)](https://github.com/zio/zio-constraintless)

## Introduction

It is **a Scala take on the following paper in Haskell, on parametrising the program with logical constraints at every node, without compromising modularity**

http://www.doc.ic.ac.uk/~wlj05/files/Deconstraining.pdf

An excerpt from the paper:

_"The key principle that underpins our idea is that implementation- specific constraints should be imposed at the point of use of a data type, not at the point of definition, i.e. it embodies the established principle that an interface should be separated from its implementation(s)."_

## Installation

```sbt
libraryDependencies += "dev.zio" %% "constraintless" % "0.3.9"
```

## Example

Imagine you're building a small expression DSL that supports multiple types (`Int`, `Boolean`, etc.).
You want two interpreters — one that **evaluates** and one that **pretty-prints** — and you want to apply *both* to the *same* expression.

### ❌ Without zio-constraintless

Say you define a simple expression GADT. The types inside it are existential — when you pattern match
on a node like `Value(a)`, the compiler has forgotten what `A` was. You're holding a value with
no type information. If you want to *do* anything useful with `a` — print it, compare it, convert it —
your only option without a carried constraint is `asInstanceOf`, and at that point safety depends entirely
on the GADT and interpreter developer never making a mistake. So you're forced to capture a typeclass
instance (e.g. `Show[A]`) inside each node at the point of construction:

```scala
// A is existential in the GADT — when you pattern match on Value,
// the compiler forgets what A was. So you MUST carry a Show[A]
// inside the node, otherwise you can't do anything with `a`.
enum Expr[A]:
  case Value(a: A, show: Show[A])
  case Cond(b: Expr[Boolean], t: Expr[A], f: Expr[A])

def pretty[A](e: Expr[A]): String = e match
  case Expr.Value(a, show) => show.show(a) // Without `show` here, we're stuck — A is unknown
  case Expr.Cond(b, t, f)  => s"if ${pretty(b)} then ${pretty(t)} else ${pretty(f)}"
```

This works — but now `Expr` is married to `Show`. What if you also want an evaluator that
needs `ToInt[A]` (convert any value to `Int`)? You're stuck:

```scala
// ⚠️ You'd have to either:
//   1. Shove EVERY possible constraint into every node — leaks all implementation details
//   2. Duplicate the entire GADT per interpreter
//   3. Give up and use asInstanceOf everywhere
```

### ✅ With zio-constraintless

Instead of baking a specific typeclass into the GADT, you only track **which types are allowed**
via a type-level list. Each interpreter supplies its own constraints at the point of *use*, not definition.

```scala
import zio.constraintless.*
import TypeList.*

// The expression knows nothing about Show or ToInt —
// it only knows which types are allowed via the type-level list `As`
enum Expr[As <: TypeList, A]:
  case Value(a: A)(using val ev: A IsElementOf As)
  case Cond(b: Expr[As, Boolean], t: Expr[As, A], f: Expr[As, A])(using
    val c1: A IsElementOf As, val c2: Boolean IsElementOf As
  )

// ✅ Pretty-printer — supplies Show instances at the *use site*
def pretty[As <: TypeList, A](e: Expr[As, A])(using show: Instances[Show, As]): String =
  e match
    case v: Expr.Value[As, A] =>
      show.withInstance((s: Show[A]) => s.show(v.a))(v.ev)
    case c: Expr.Cond[As, A] =>
      s"if ${pretty(c.b)} then ${pretty(c.t)} else ${pretty(c.f)}"

// ✅ Evaluator — supplies ToInt instances at the *use site*
def compileSM[As <: TypeList, A](e: Expr[As, A])(using ib: Instances[ToInt, As]): String =
  e match
    case v: Expr.Value[As, A] =>
      ib.withInstance((i: ToInt[A]) => i.toInt(v.a).toString)(v.ev)
    case c: Expr.Cond[As, A] =>
      s"if (${compileSM(c.b)}) then ${compileSM(c.t)} else ${compileSM(c.f)}"

// 🎉 Both interpreters work on the SAME expression!
type T = Int :: Boolean :: TypeList.End

val expr: Expr[T, Int] =
  Expr.Cond(Expr.Value(true), Expr.Value(1), Expr.Value(0))

pretty(expr)     // "if true then 1 else 0"
compileSM(expr)  // "if (1) then 1 else 0"
```

**The key idea:** the expression is defined *once*, fully polymorphic over its interpreters.
Each interpreter brings its own typeclass instances via `Instances[MyTypeClass, As]` — no duplication, no casting, no leaking implementation details into the definition.

For a full worked example, see [Expr.scala](https://github.com/zio/zio-constraintless/blob/master/examples/shared/src/main/scala/zio/constraintless/examples/Expr.scala).

## Context

The key to many inspectable programs such as an execution planner, a configuration DSL etc is the basic concept of "programs as descriptions", but this idea comes with limitations.

This description (or data) can easily turn out to be a Generalised ADT that can be recursive, such that compiler has to traverse through the unknown types (existential) and for the compiler to do any advanced/useful stuff with it, it needs to know more about these types.

The obvious implication of having to handle "unknown" is that, the data should hold on to informations as constraints (that are relevant to implementation) on types at the definition site. A possible solution is to compromise on parametric polymorphism, or fall back to relying unsafe/safe (relative) casting (asInstanceOf).

This naive approach imposes modularity issues, and possible runtime crashes. The reasonsing and solution is given in the above paper, and this project solves the exact problem in scala.

## Why not the Hughes schema?

It doesn't allow you to have a compiler with multiple constraints.

A few excerpts from the paper on why it doesn't work:

```scala
class Typeable p a valueP :: a → p a
```

```scala
newtype SM a = SM {fromSM :: Int}

instance IntBool a ⇒ Typeable SM a where
  valueP = SM · toInt
```

```scala
newtype Pretty a = Pretty {fromPretty :: String}

instance Show a ⇒ Typeable Pretty a where valueP = Pretty · show
```

```scala
data Exp p a where
ValueE::Typeable p a ⇒ a → Exp p a

CondE ::Expp Bool→Exp p a → Exp p a → Exp p a 

EqE :: Eq a ⇒ Exp p a → Exp p a → Exp p Bool
```

```scala
pretty :: Exp Pretty a → String // works
compileSM :: Exp SM a → String // works
```

However, now suppose that we wish to apply the two functions to the same expression, as in:

```scala
f :: Exp p a → . . .
f e = ...(compileSM e)...(pretty e)..
```

and that's impossible
