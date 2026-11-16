# Reference Overview

> How Tag, TagK, LightTypeTag and LTag fit together in izumi-reflect, and which one to use for each job.

`izumi-reflect` describes Scala types as runtime values, so programs can compare types, check subtyping and use types as keys after erasure has removed type arguments from the JVM classes. The core types are `Tag` with its `TagK` family, `LightTypeTag`, and the lower-level `LTag` and `LTT`:

```scala
trait Tag[T] extends AnyTag { def tag: LightTypeTag; def closestClass: Class[_] }
type TagK[K[_]] // ... one alias per kind: TagKK, TagK3, TagT, ...
abstract class LightTypeTag { def =:=(other: LightTypeTag): Boolean; def <:<(maybeParent: LightTypeTag): Boolean }
final case class LTag[T](tag: LightTypeTag)
```

## Overview

Each page of this reference covers one part of the library:

- [`Tag` and the `TagK` family](tag.md) are the type classes you use in application code: context bounds such as `A: Tag` and `F[_]: TagK` carry type information into generic code.
- [`LightTypeTag`](light-type-tag.md) is the runtime model of a type that every tag holds. It implements equality, subtype checks, combining of type constructors, rendering and serialization.
- [`LTag` and `LTT`](ltag.md) create a `LightTypeTag` directly, without the `Tag` wrapper and without looking up tags of type parameters.
- [Scala 2 vs Scala 3](scala-2-vs-scala-3.md) lists the differences between the two macro implementations.
- [Debugging and DebugProperties](debugging.md) describes the settings that make the library explain its decisions.
- [Comparison with scala-reflect TypeTag](typetag-comparison.md) shows what carries over from `TypeTag` and what does not.

## How They Work Together

A tag goes through two phases: the macro builds it while your code compiles, and the program reads it while it runs.

1. You ask for a tag, either directly with `Tag[List[Int]]` or with a context bound such as `def f[A: Tag]`.
2. For a type without abstract type parameters, the macro computes the whole `LightTypeTag` at compile time: the structure of the type and the parents of every type in it. It stores the result in your code as a few serialized strings, which `LightTypeTag.parse` reads lazily the first time the tag is used.
3. For a type that mentions type parameters, such as `Map[String, A]` or `F[A]`, the macro finds the tags of those parameters in the implicit scope and writes code that combines them at runtime: `Tag.appliedTag` applies a type constructor with `LightTypeTag#combine`, and siblings such as `Tag.refinedTag` build intersections and refinements.
4. At runtime, `Tag#tag` gives the `LightTypeTag`. Its `LightTypeTag#equals` and `LightTypeTag#hashCode` make it a map key, `LightTypeTag#<:<` checks subtyping against the parents stored in step 2, and `LightTypeTag#serialize` turns it into strings again for storage.

The relationships between the types:

```
 your code                      compile time (macro)                 runtime
 ─────────                      ────────────────────                 ───────
 Tag[List[Int]]  ─────────────▶ LightTypeTag of List[Int]  ────────▶ LightTypeTag.parse(...)
                                (structure + parents)                    │
 def f[A: Tag, F[_]: TagK]                                               ▼
   Tag[F[A]]     ─────────────▶ Tag.appliedTag(TagK[F], Tag[A]) ──▶ LightTypeTag#combine
                                                                         │
 LTT[T] / LTag[T] ────────────▶ LightTypeTag of T (no lookup              ▼
                                of type parameters)               Tag[T].tag: LightTypeTag
                                                                   ├─ =:=, ==, hashCode
                                                                   ├─ <:<
                                                                   ├─ toString, repr, ...
                                                                   └─ serialize ⇄ parse
```

To see the two phases from code, compare a tag created for a concrete type with one built from tags in generic code. Both describe the same type, so they are equal:

```scala
import izumi.reflect.{Tag, TagK}

def built[F[_]: TagK, A: Tag]: Tag[F[A]] = Tag[F[A]]

built[List, Int] == Tag[List[Int]]
// res0: Boolean = true
```

## Choosing a Tag Type

The ways to get a type description differ in how they treat type parameters, which decides where each one fits:

| You need                                                        | Use                            |
| --------------------------------------------------------------- | ------------------------------ |
| A type description in generic code, including type parameters   | `A: Tag` context bound         |
| The same for a type constructor `F[_]`                          | `F[_]: TagK` (or `TagKK`, ...) |
| The `LightTypeTag` of a concrete type, for example in a test    | `LTT[T]`                       |
| An implicit tag for a concrete type, without the class          | `LTag[T]`                      |
| A description of a generic signature with unresolved parameters | `LTag.Weak[T]`                 |

When in doubt, use `Tag`: it is the only one that resolves type parameters from the caller, and it never contains placeholders.

## Common Patterns

Most programs use tags in one of three ways, each covered by a guide:

- **Type-indexed containers.** A map from `LightTypeTag` to values, looked up by exact type or by supertype. This is how dependency injection containers and the ZIO environment work. See [Build a Type-Safe Heterogeneous Map](../guides/heterogeneous-map.md) and [Use izumi-reflect Tags with the ZIO Environment](../guides/zio-environment.md).
- **Generic code over type constructors.** Tagless-final services generic in `F[_]` take a `TagK[F]` and build tags of `Service[F]`. See [Tag and the TagK Family](tag.md#type-constructors-in-generic-code).
- **Replacing `TypeTag`.** Code that uses `scala-reflect` for type comparisons moves to `Tag` to cross-build for Scala 3, Scala.js and Scala Native. See [Migrate from scala-reflect TypeTag](../guides/migrate-from-typetag.md).

## Integration Points

ZIO 2 is the most common user of `izumi-reflect`: `zio.Tag` extends `izumi.reflect.Tag`, the `zio` package re-exports `TagK` and its siblings, and `ZEnvironment` stores services under their `LightTypeTag`. Code written against `izumi-reflect` tags works with ZIO directly. The [ZIO environment guide](../guides/zio-environment.md) shows how.

## Running the Examples

All code from this page is available as runnable examples in the `izumi-reflect-examples` project.

A tag built at runtime from `TagK[F]` and `Tag[A]` equals the tag the compiler creates for the concrete type, as this program shows:

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/referenceoverview/TwoPhasesExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
// Run from this folder:  scala-cli run TwoPhasesExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.referenceoverview.TwoPhasesExample"

package izumi.reflect.examples.referenceoverview

import izumi.reflect.{Tag, TagK}

/**
  * Reference Overview ? How They Work Together
  *
  * A tag built at runtime from TagK[F] and Tag[A] equals the tag the compiler creates for the concrete type.
  */
object TwoPhasesExample {
  def built[F[_]: TagK, A: Tag]: Tag[F[A]] = Tag[F[A]]

  def main(args: Array[String]): Unit = {
    println(s"built[List, Int]: ${built[List, Int]}")
    println(s"built[List, Int] == Tag[List[Int]]: ${built[List, Int] == Tag[List[Int]]}")
  }
}
```

([source](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/referenceoverview/TwoPhasesExample.scala)) Run it with `scala-cli run TwoPhasesExample.scala` from that folder, or with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.referenceoverview.TwoPhasesExample"` from the repository root.
