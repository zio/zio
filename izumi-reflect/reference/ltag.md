# LTag and LTT

> Reference for LTag, LTT and weak tags, which build a LightTypeTag without resolving type parameters from scope.

`LTag[T]` and `LTT[T]` create a [`LightTypeTag`](light-type-tag.md) for a type without going through a [`Tag`](tag.md). They live in the `izumi.reflect.macrortti` package. Unlike `Tag`, they never look for tags of type parameters in the implicit scope, so they are simpler and faster to compile, but they only describe types whose type parameters are all known at the call site.

You rarely need them in application code; they are lower-level building blocks for libraries and tests, prefer `Tag` when you write generic code. The declarations below show their shape:

```scala
package izumi.reflect.macrortti

final case class LTag[T](tag: LightTypeTag)

object LTag {
  def apply[T: LTag]: LTag[T]
  final case class Weak[T](tag: LightTypeTag)
}

def LTT[T]: LightTypeTag
```

## Usage

`LTT` and `LTag` give the `LightTypeTag` of a concrete type directly, and `LTag.Weak` keeps unresolved type parameters as placeholders:

```scala
import izumi.reflect.macrortti._

LTT[List[Int]]
// res1: LightTypeTag = List[+Int]
LTag[Either[String, Int]].tag
// res2: LightTypeTag = Either[+String,+Int]

def weakOptionTag[T]: LightTypeTag = LTag.Weak[Option[T]].tag

weakOptionTag[Int]
// res3: LightTypeTag = Option[+_$T]
```

## LTT

`LTT[T]` is a method that returns the `LightTypeTag` of `T` directly. It is the shortest way to get a tag for a concrete type, for example in tests:

```scala
import izumi.reflect.Tag
import izumi.reflect.macrortti._

LTT[Either[String, Int]]
// res4: LightTypeTag = Either[+String,+Int]
LTT[Either[String, Int]] == Tag[Either[String, Int]].tag
// res5: Boolean = true
```

`LTT` never fails on an abstract type parameter. It keeps the parameter as a named placeholder, the same way as [weak tags](#weak-tags) do, so the result does not describe the type the caller passes:

```scala
def lttOfList[T]: LightTypeTag = LTT[List[T]]

lttOfList[Int]
// res6: LightTypeTag = List[+_$T]
lttOfList[Int] == LTT[List[Int]]
// res7: Boolean = false
```

Use `LTT` only for concrete types, and use `Tag` in generic code.

For type constructors, the `macrortti` package defines variants of `LTT` whose names spell out the kind, such as ``LTT[_]`` for `F[_]` and ``LTT[_,_]`` for `F[_, _]`. Their names contain brackets, so you call them with backticks:

```scala
val listConstructor = `LTT[_]`[List]
// listConstructor: LightTypeTag = ? %0 ? List[+0]
val eitherConstructor = `LTT[_,_]`[Either]
// eitherConstructor: LightTypeTag = ? %0,%1 ? Either[+0,+1]
```

The full list is `LTT[_]`, `LTT[+_]`, `LTT[_,_]`, `LTT[_[_]]`, `LTT[_[+_]]`, `LTT[_[_[_]]]`, `LTT[_[+_[_]]]`, `LTT[_[_,_]]`, `LTT[_[+_,+_]]`, `LTT[_[_,_],_,_]`, `LTT[_[+_,+_],_,_]`, `LTT[_[_],_[_]]`, `LTT[_[_[_],_[_]]]`, plus two variants for bounded type parameters.

## LTag

`LTag[T]` is a type class that wraps a `LightTypeTag`, like `Tag` but without the class information and without combining tags of type parameters. Use it as an implicit parameter when you want a type tag for a fully known type:

```scala
def nameOf[T](implicit ltag: LTag[T]): String = ltag.tag.toString

nameOf[Map[String, Boolean]]
// res8: String = "Map[=String,+Boolean]"
```

Because `LTag` doesn't combine tags, it fails to compile when the type mentions an abstract type parameter, even if a tag for that parameter is in scope. For example, `def listOf[T: Tag] = LTag[List[T]]` doesn't compile; use `Tag[List[T]]` for that.

For type constructors, the package defines `LTagK`, `LTagKK`, `LTagK3`, `LTagT`, `LTagTK`, `LTagTKK` and `LTagTK3`, which mirror the [`TagK` family](tag.md#tags-for-type-constructors):

```scala
LTagK[Option].tag
// res9: LightTypeTag = ? %0 ? Option[+0]
```

## Weak Tags

`LTag.Weak[T]` (also available as `LWeakTag[T]`) is a variant of `LTag` that accepts types with unresolved type parameters. Instead of failing, it keeps each unresolved type parameter as a named placeholder in the tag:

```scala
def weakListTag[T]: LightTypeTag = LTag.Weak[List[T]].tag

weakListTag[Int]
// res10: LightTypeTag = List[+_$T]
```

The result contains a placeholder for `T`, not `Int`: the macro ignores the type argument at the call site. A weak tag is only equal to another weak tag created from the same type parameter, so it is not suitable for comparing types across call sites. Dependency injection frameworks use weak tags to describe generic method signatures; application code should use `Tag`.

`LTag.StrongHK[T]` and `LTag.WeakHK[T]` are the higher-kinded variants behind the `LTagK` family and `LTag.Weak`: the first creates tags for type constructors, the second keeps unresolved type parameters as placeholders. On Scala 3 they are aliases of `LTag` and `LTag.Weak`.

## When to Use Which

The tag creators differ in how they treat type parameters:

| Creator        | Result         | Abstract type parameters                     | Typical use                          |
| -------------- | -------------- | -------------------------------------------- | ------------------------------------ |
| `Tag[T]`       | `Tag[T]`       | Resolved from `Tag`s in scope                | Generic application and library code |
| `LTag[T]`      | `LTag[T]`      | Compile error                                | Implicit tags for concrete types     |
| `LTT[T]`       | `LightTypeTag` | Kept as named placeholders, without an error | Tests and one-off concrete tags      |
| `LTag.Weak[T]` | `LTag.Weak[T]` | Kept as named placeholders                   | Describing generic signatures        |

## Running the Examples

All code from this page is available as runnable examples in the `izumi-reflect-examples` project.

The examples of the Usage section as one program, covering `LTT`, `LTag` and a weak tag:

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/ltag/BasicUsage.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
// Run from this folder:  scala-cli run BasicUsage.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.ltag.BasicUsage"

package izumi.reflect.examples.ltag

import izumi.reflect.macrortti._

/**
  * LTag and LTT ? Usage
  *
  * Builds LightTypeTags of concrete types with LTT and LTag, and a weak tag that keeps a type parameter as a placeholder.
  */
object BasicUsage {
  def weakOptionTag[T]: LightTypeTag = LTag.Weak[Option[T]].tag

  def main(args: Array[String]): Unit = {
    println(s"LTT[List[Int]]: ${LTT[List[Int]]}")
    println(s"LTag[Either[String, Int]].tag: ${LTag[Either[String, Int]].tag}")
    println(s"weakOptionTag[Int]: ${weakOptionTag[Int]}")
  }
}
```

([source](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/ltag/BasicUsage.scala)) Run it with `scala-cli run BasicUsage.scala` from that folder, or with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.ltag.BasicUsage"` from the repository root.
