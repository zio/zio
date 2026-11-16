# Getting Started with izumi-reflect

> Install izumi-reflect, create your first type tags, and carry them through generic code on Scala 2 and 3.

`izumi-reflect` gives you a runtime value that describes a Scala type, so you can compare types, check subtyping, and use types as keys while your program runs. It works the same way on Scala 2.12, 2.13 and 3, on the JVM, Scala.js and Scala Native. This page installs the library, creates the first type tags, and shows how to carry them through generic code.

## Installation

Add the library to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "izumi-reflect" % "3.0.12-SNAPSHOT"
```

For Scala.js or Scala Native projects, use `%%%` instead of `%%`:

```scala
libraryDependencies += "dev.zio" %%% "izumi-reflect" % "3.0.12-SNAPSHOT"
```

`izumi-reflect` has no runtime dependencies besides the Scala standard library. In particular, it does not depend on `scala-reflect`.

:::note
If you use ZIO 2, you already have `izumi-reflect` on your classpath: ZIO uses it to identify services in the environment. See [Use izumi-reflect Tags with the ZIO Environment](guides/zio-environment.md).
:::

## Create Your First Tag

A `Tag[T]` is a value that describes the type `T`. The compiler creates it for you at compile time, so summoning it is cheap and needs no runtime reflection. To create tags for a few concrete types and print them:

```scala
import izumi.reflect.Tag

Tag[Int]
// res0: Tag[Int] = Tag[Int]
Tag[List[String]]
// res1: Tag[List[String]] = Tag[List[+String]]
Tag[Either[String, Option[Int]]]
// res2: Tag[Either[String, Option[Int]]] = Tag[Either[+String,+Option[+Int]]]
```

The printed form shows each type argument with its variance: `+` for covariant, `-` for contravariant and `=` for invariant.

Every `Tag` holds a `LightTypeTag` in its `tag` field. That value does the work: it compares types, renders them, and checks subtyping. To render the same type in short and fully qualified forms:

```scala
val listTag = Tag[List[String]].tag
// listTag: LightTypeTag = List[+String]

listTag.toString
// res3: String = "List[+String]"
listTag.repr
// res4: String = "scala.collection.immutable.List[+java.lang.String]"
listTag.shortName
// res5: String = "List"
```

## Compare Types at Runtime

Two tags are equal when they describe the same type, no matter which alias or import path names it. To compare tags for equality with `Tag#=:=` and check subtyping with `Tag#<:<`:

```scala
Tag[List[Int]] =:= Tag[scala.collection.immutable.List[Int]]
// res6: Boolean = true

Tag[List[Int]] <:< Tag[Seq[Any]]
// res7: Boolean = true

Tag[Seq[Int]] <:< Tag[List[Int]]
// res8: Boolean = false
```

The subtype check understands variance, so `List[Int] <:< Seq[Any]` holds because `List` and `Seq` are covariant. `Tag` also implements `Tag#equals` and `Tag#hashCode` consistently with `Tag#=:=`, so you can use tags as keys in a `Map` or members of a `Set`.

## Carry Tags Through Generic Code

The main use of `izumi-reflect` is inside generic code, where the concrete type is only known at the call site. Ask for a tag with a context bound, and the compiler passes it from the call site:

```scala
def describe[A: Tag](value: A): String =
  s"$value has type ${Tag[A].tag}"

describe(42)
// res9: String = "42 has type Int"
describe(List(Some("a"), None))
// res10: String = "List(Some(a), None) has type List[+Option[+String]]"
```

`izumi-reflect` builds tags for bigger types out of the tags in scope. Inside `describe`, you can ask for a tag of any type that mentions `A`, such as `Map[String, A]`, and the macro combines the tag of `A` with the rest of the type:

```scala
def mapOf[A: Tag]: Tag[Map[String, A]] = Tag[Map[String, A]]

mapOf[Double]
// res11: Tag[Map[String, Double]] = Tag[Map[=String,+Double]]
```

For a type parameter that is a type constructor, such as `F[_]`, use `TagK` instead of `Tag`. `TagK` describes an unapplied type constructor, and `izumi-reflect` applies it to the other tags in scope:

```scala
import izumi.reflect.TagK

def containerOf[F[_]: TagK, A: Tag]: Tag[F[A]] = Tag[F[A]]

containerOf[Option, Int]
// res12: Tag[Option[Int]] = Tag[Option[+Int]]
containerOf[Vector, String]
// res13: Tag[Vector[String]] = Tag[Vector[+String]]
```

`TagK` has siblings for other kinds, such as `TagKK` for `F[_, _]` and `TagK3` for `F[_, _, _]`. The [Tag and the TagK Family](reference/tag.md) page lists them all.

<details>
  <summary>Runnable version, with the three generic methods of this section in one program: <code>Step4GenericCodeExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/gettingstarted/Step4GenericCodeExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
// Run from this folder:  scala-cli run Step4GenericCodeExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.gettingstarted.Step4GenericCodeExample"

package izumi.reflect.examples.gettingstarted

import izumi.reflect.{Tag, TagK}

/**
  * Getting Started with izumi-reflect ? Carry Tags Through Generic Code
  *
  * Passes tags into generic methods with Tag and TagK context bounds and builds tags of bigger types from them.
  */
object Step4GenericCodeExample {
  def describe[A: Tag](value: A): String =
    s"$value has type ${Tag[A].tag}"

  def mapOf[A: Tag]: Tag[Map[String, A]] = Tag[Map[String, A]]

  def containerOf[F[_]: TagK, A: Tag]: Tag[F[A]] = Tag[F[A]]

  def main(args: Array[String]): Unit = {
    println(describe(42))
    println(describe(List(Some("a"), None)))
    println(s"mapOf[Double]: ${mapOf[Double]}")
    println(s"containerOf[Option, Int]: ${containerOf[Option, Int]}")
    println(s"containerOf[Vector, String]: ${containerOf[Vector, String]}")
  }
}
```

</details>

## When a Tag Is Missing

If a generic method asks for `Tag[A]` but has no tag for `A` in scope, compilation fails. The first line of the error names the part of the type that has no tag, and the message always contains this suggestion:

```text
could not find implicit value for izumi.reflect.Tag[T]. Did you forget to put on a Tag, TagK or TagKK context bound on one of the parameters in T? e.g. def x[T: Tag, F[_]: TagK] = ...
```

To fix it, add a `Tag` context bound to the type parameter (`def f[T: Tag]`), or `TagK`, `TagKK` and so on for a type constructor parameter. On Scala 3 the message also includes the internal structure of the type and a macro stack trace; the first lines contain the useful part.

## Running the Examples

All runnable code from this page is in [`izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/gettingstarted/`](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/gettingstarted). Run any file from that folder with `scala-cli run <File>.scala`, or from the repository root with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.gettingstarted.<ObjectName>"`.

## Next Steps

From here, you can read about the building blocks in depth or jump to a task:

- [Tag and the TagK Family](reference/tag.md) describes every tag type and how to create tags by hand.
- [LightTypeTag](reference/light-type-tag.md) covers equality, subtyping, combining type constructors, rendering and serialization.
- [Build a Type-Safe Heterogeneous Map](guides/heterogeneous-map.md) uses tags as keys in a map of values of different types.
- [Migrate from scala-reflect TypeTag](guides/migrate-from-typetag.md) maps `TypeTag` code to `izumi-reflect`.
