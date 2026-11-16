# Migrate from scala-reflect TypeTag

> Replace scala-reflect TypeTag with izumi-reflect Tag to cross-build for Scala 3, Scala.js and Scala Native.

In this guide, we move code that uses `TypeTag` from `scala-reflect` to `Tag` from `izumi-reflect`. By the end, the code compiles on Scala 2 and Scala 3 and runs on the JVM, Scala.js and Scala Native. We start with a mapping of the common operations, then convert generic code, higher-kinded code and type-keyed maps, and finish with a way to migrate a large Scala 2 code base gradually.

## The Problem

`TypeTag` is part of `scala-reflect`, which exists only for Scala 2 on the JVM. Scala 3 has no `TypeTag`, so any library or application that uses it can't cross-build for Scala 3 until the `TypeTag` usages are gone.

For example, consider this Scala 2 code, which keeps a registry of handlers by the type they accept:

```scala
// Scala 2 and scala-reflect (not compiled here)
import scala.reflect.runtime.universe._

final class Handlers {
  private var handlers = List.empty[(Type, Any => Unit)]

  def register[A: TypeTag](handler: A => Unit): Unit =
    handlers ::= (typeOf[A] -> handler.asInstanceOf[Any => Unit])

  def handlersFor[A: TypeTag]: List[Any => Unit] =
    handlers.collect { case (tpe, h) if typeOf[A] <:< tpe => h }
}
```

The code does not compile on Scala 3. It also has subtler problems on Scala 2: `Type` values must be compared with `=:=`, not `==`, so they can't be used as `Map` keys, and `scala-reflect` has known thread-safety issues.

## Prerequisites

Add `izumi-reflect` to your build:

```scala
libraryDependencies += "dev.zio" %% "izumi-reflect" % "3.0.12-SNAPSHOT"
```

Use `%%%` for Scala.js and Scala Native projects. During a gradual migration on Scala 2 you keep `scala-reflect` as well; remove it at the end.

All examples on the `izumi-reflect` side use these imports:

```scala
import izumi.reflect.{Tag, TagK}
import izumi.reflect.macrortti.LightTypeTag
```

## Mapping the Common Operations

Most `TypeTag` code uses a handful of operations, and each has a direct counterpart. In this table, `tpe` is a `scala-reflect` `Type` and `tag` is a `LightTypeTag`, obtained with `Tag[A].tag`:

| `scala-reflect`                           | `izumi-reflect`                        |
| ----------------------------------------- | -------------------------------------- |
| `A: TypeTag` context bound                | `A: Tag` context bound                 |
| `A: WeakTypeTag` context bound            | `A: Tag` context bound                 |
| `F[_]` with `TypeTag[F[Any]]` workarounds | `F[_]: TagK` context bound             |
| `typeTag[A]`                              | `Tag[A]`                               |
| `typeOf[A]`                               | `Tag[A].tag`                           |
| `tpe1 =:= tpe2`                           | `tag1 =:= tag2`, or `tag1 == tag2`     |
| `tpe1 <:< tpe2`                           | `tag1 <:< tag2`                        |
| `tpe.typeArgs`                            | `tag.typeArgs`                         |
| `tpe.typeConstructor`                     | `TagK[F].tag` for the constructor `F`  |
| `tpe.typeSymbol.name.toString`            | `tag.shortName`                        |
| `tpe.typeSymbol.fullName`                 | `tag.longNameWithPrefix`               |
| `tpe.toString`                            | `tag.toString` (with variance markers) |
| `mirror.runtimeClass(tpe)`                | `Tag[A].closestClass`                  |
| `tpe.decls`, `tpe.members`, annotations   | No counterpart                         |

The last row is the one real gap: `izumi-reflect` does not keep fields, methods or annotations. Code that inspects them needs another approach, such as a type class derived at compile time. See [Comparison with scala-reflect TypeTag](../reference/typetag-comparison.md) for the full comparison.

`WeakTypeTag` maps to `Tag`, not to the weak tags of `izumi-reflect`: a `WeakTypeTag` context bound resolves the caller's type arguments, and so does `Tag`.

## Converting Generic Code

In most code, the migration is a change of context bound and of the call that reads the type. For example, a generic function that describes the type of its argument becomes:

```scala
def describe[A: Tag](value: A): String =
  s"$value: ${Tag[A].tag}"

describe(Map("a" -> List(1, 2)))
// res0: String = "Map(a -> List(1, 2)): Map[=String,+List[+Int]]"
```

The rendering differs from `Type#toString`: `izumi-reflect` prints variance markers (`+`, `-`, `=`) on type arguments. If you compare rendered names with stored strings, use `LightTypeTag#shortName` or `LightTypeTag#longNameWithPrefix`, which contain only the class name. Subtype checks keep the same shape:

```scala
def accepts[A: Tag, B: Tag]: Boolean = Tag[A] <:< Tag[B]

accepts[List[Int], Seq[Any]]
// res1: Boolean = true
accepts[Seq[Any], List[Int]]
// res2: Boolean = false
```

## Converting Higher-Kinded Code

`scala-reflect` has no tag for an unapplied type constructor such as `F[_]`, so code generic in `F` usually asks for a `TypeTag[F[Any]]` or similar and extracts the type constructor at runtime. With `izumi-reflect`, ask for a `TagK[F]` directly. The macro then builds the tag of any type that mentions `F`:

```scala
trait Repository[F[_]]

def repositoryTag[F[_]: TagK]: Tag[Repository[F]] = Tag[Repository[F]]

repositoryTag[Option]
// res3: Tag[Repository[[A >: Nothing <: Any] =>> Option[A]]] = Tag[MdocSession::MdocApp::Repository[=? %0 ? Option[+0]]]
```

To get the type constructor of an applied type, as `Type#typeConstructor` does, summon its `TagK`:

```scala
TagK[List].tag
// res4: LightTypeTag = ? %0 ? List[+0]
```

## Converting Type-Keyed Maps

Code that stores `Type` values as keys usually needs a list and a linear search with `=:=`, because `Type#equals` is not type equality. `LightTypeTag#equals` and `LightTypeTag#hashCode` match type equality, so the handler registry from [The Problem](#the-problem) can use a `Map` for exact lookups and `<:<` for supertype lookups:

```scala
final case class Handlers(handlers: Map[LightTypeTag, Any => Unit]) {
  def register[A: Tag](handler: A => Unit): Handlers =
    Handlers(handlers + (Tag[A].tag -> handler.asInstanceOf[Any => Unit]))

  def handlersFor[A: Tag]: List[Any => Unit] =
    handlers.collect { case (tag, h) if Tag[A].tag <:< tag => h }.toList
}
```

The registry is now an immutable value. To register handlers for two types:

```scala
val handlers = Handlers(Map.empty)
  .register[Seq[Int]](seq => println(s"seq of ${seq.size}"))
  .register[String](s => println(s"string $s"))
```

Only the handler for `Seq[Int]` accepts a `List[Int]`, so only it runs:

```scala
handlers.handlersFor[List[Int]].foreach(_(List(1, 2, 3)))
// seq of 3
```

The [Build a Type-Safe Heterogeneous Map](heterogeneous-map.md) guide develops this pattern further.

<details>
  <summary>Runnable version, with the registry and the lookup in one program: <code>Step6TypeKeyedMapExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/migratefromtypetag/Step6TypeKeyedMapExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
// Run from this folder:  scala-cli run Step6TypeKeyedMapExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.migratefromtypetag.Step6TypeKeyedMapExample"

package izumi.reflect.examples.migratefromtypetag

import izumi.reflect.Tag
import izumi.reflect.macrortti.LightTypeTag

/**
  * Migrate from scala-reflect TypeTag ? Converting Type-Keyed Maps
  *
  * A handler registry keyed by LightTypeTag, with lookups by supertype.
  */
object Step6TypeKeyedMapExample {
  final case class Handlers(handlers: Map[LightTypeTag, Any => Unit]) {
    def register[A: Tag](handler: A => Unit): Handlers =
      Handlers(handlers + (Tag[A].tag -> handler.asInstanceOf[Any => Unit]))

    def handlersFor[A: Tag]: List[Any => Unit] =
      handlers.collect { case (tag, h) if Tag[A].tag <:< tag => h }.toList
  }

  def main(args: Array[String]): Unit = {
    val handlers = Handlers(Map.empty)
      .register[Seq[Int]](seq => println(s"seq of ${seq.size}"))
      .register[String](s => println(s"string $s"))

    handlers.handlersFor[List[Int]].foreach(_(List(1, 2, 3)))
  }
}
```

</details>

## Migrating a Large Code Base Gradually

In a large Scala 2 code base, you may not be able to replace every `TypeTag` at once. During the transition, a module that still has a `TypeTag` can produce a `LightTypeTag` from it at runtime with `LightTypeTagImpl.makeLightTypeTag`, so it can talk to modules that already use `izumi-reflect`. This works on Scala 2 on the JVM only, and needs `scala-reflect` on the classpath:

```scala
// Scala 2, JVM only (not compiled here)
import scala.reflect.runtime.universe
import izumi.reflect.Tag
import izumi.reflect.macrortti.{LightTypeTag, LightTypeTagImpl}

def fromTypeTag[A](implicit tt: universe.TypeTag[A]): LightTypeTag =
  LightTypeTagImpl.makeLightTypeTag(universe)(tt.tpe)

fromTypeTag[Map[String, List[Int]]] == Tag[Map[String, List[Int]]].tag // true
```

The tag built at runtime is equal to the one the `Tag` macro creates at compile time, so both kinds of module agree on map keys and subtype checks. Building tags at runtime with `scala-reflect` is slower than summoning a `Tag`, so use this only at module boundaries, and remove it, together with the `scala-reflect` dependency, once the migration is complete.

## Running the Examples

All runnable code from this guide is in [`izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/migratefromtypetag/`](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/migratefromtypetag). Run any file from that folder with `scala-cli run <File>.scala`, or from the repository root with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.migratefromtypetag.<ObjectName>"`.

## Going Further

After the migration, the code no longer depends on `scala-reflect`. From here:

- Read [Scala 2 vs Scala 3](../reference/scala-2-vs-scala-3.md) for the differences between versions that remain, mostly in how tags for unusual kinds are declared.
- Read the [Limitations](../index.md#limitations) section of the introduction for the places where the subtype checks of `izumi-reflect` are less precise than `scala-reflect`.
- Read [Tag and the TagK Family](../reference/tag.md) for the full list of tag types.
