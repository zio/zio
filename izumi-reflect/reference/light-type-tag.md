# LightTypeTag

> Reference for LightTypeTag: type equality, subtype checks, combining type constructors, rendering and serialization.

`LightTypeTag` is the runtime model of a Scala type in `izumi-reflect`. Every [`Tag`](tag.md) holds one in its `tag` field. A `LightTypeTag` is an immutable value that knows the structure of the type (its type constructor, arguments, variance, prefixes, refinements) and enough of its inheritance to answer subtype checks. All its operations are thread-safe and need no runtime reflection. The class defines the following public members:

```scala
abstract class LightTypeTag extends Serializable {
  def ref: LightTypeTagRef

  def =:=(other: LightTypeTag): Boolean
  def <:<(maybeParent: LightTypeTag): Boolean

  def combine(args: LightTypeTag*): LightTypeTag
  def combineNonPos(args: Option[LightTypeTag]*): LightTypeTag
  def typeArgs: List[LightTypeTag]
  def withoutArgs: LightTypeTag

  def decompose: Set[LightTypeTag]
  def decomposeUnion: Set[LightTypeTag]
  def removeIntersectionTautologies: LightTypeTag
  def removeUnionTautologies: LightTypeTag

  def repr: String
  def scalaStyledRepr: String
  def shortName: String
  def longNameWithPrefix: String
  def longNameInternalSymbol: String
  def debug(name: String = ""): String

  def serialize(): LightTypeTag.Serialized
  def binaryFormatVersion: Int
}
```

## Usage

A `LightTypeTag` comes from a `Tag` and answers questions about the type at runtime: how it renders, whether it is a subtype of another type, what its type arguments are, and how it combines with other tags:

```scala
import izumi.reflect.{Tag, TagK}

val listOfInt = Tag[List[Int]].tag
// listOfInt: LightTypeTag = List[+Int]

listOfInt.toString
// res1: String = "List[+Int]"
listOfInt <:< Tag[Seq[Any]].tag
// res2: Boolean = true
listOfInt.typeArgs
// res3: List[LightTypeTag] = List(Int)
TagK[List].tag.combine(Tag[Int].tag) == listOfInt
// res4: Boolean = true
```

[Build a Type-Safe Heterogeneous Map](../guides/heterogeneous-map.md) uses `LightTypeTag` as a map key and for subtype lookups.

## Getting a LightTypeTag

The usual way to get a `LightTypeTag` is from the `tag` field of a `Tag`. To get one for a concrete type, you can also use the `LTT` shortcut from the `izumi.reflect.macrortti` package, which skips the `Tag` wrapper:

```scala
import izumi.reflect._
import izumi.reflect.macrortti._

val fromTag: LightTypeTag = Tag[List[Int]].tag
// fromTag: LightTypeTag = List[+Int]
val fromLtt: LightTypeTag = LTT[List[Int]]
// fromLtt: LightTypeTag = List[+Int]

fromTag == fromLtt
// res5: Boolean = true
```

`LTT` does not look for tags of type parameters: for an abstract type parameter, it keeps a placeholder instead of the caller's type. Use it only for concrete types. See [LTag and LTT](ltag.md) for the details.

## Equality

`LightTypeTag#=:=` and `LightTypeTag#equals` check that two tags describe the same type. Type aliases are expanded and package paths are normalized, so different spellings of the same type are equal:

```scala
type Name = String

Tag[Name].tag =:= Tag[String].tag
// res6: Boolean = true
Tag[scala.collection.immutable.List[Int]].tag == Tag[List[Int]].tag
// res7: Boolean = true
Tag[List[Int]].tag == Tag[List[Long]].tag
// res8: Boolean = false
```

`LightTypeTag#hashCode` is consistent with `LightTypeTag#equals`, so tags work as keys of a `Map` or members of a `Set`. Equality is structural and does not consider subtyping: `Tag[Some[Int]].tag == Tag[Option[Int]].tag` is `false` even though one is a subtype of the other.

## Subtyping

`LightTypeTag#<:<` checks whether the left type is a subtype of the right one. It follows the inheritance hierarchy captured at compile time and respects the variance of type parameters:

```scala
Tag[Some[Int]].tag <:< Tag[Option[Int]].tag
// res9: Boolean = true
Tag[List[Int]].tag <:< Tag[Seq[Any]].tag
// res10: Boolean = true
Tag[Function1[Any, Int]].tag <:< Tag[Function1[String, Any]].tag
// res11: Boolean = true
Tag[Option[Int]].tag <:< Tag[Some[Int]].tag
// res12: Boolean = false
```

The third check is `true` because `Function1` is contravariant in its argument and covariant in its result. Subtype checks also work between unapplied type constructors:

```scala
TagK[List].tag <:< TagK[Seq].tag
// res13: Boolean = true
TagK[Seq].tag <:< TagK[List].tag
// res14: Boolean = false
```

The model of the type system behind `LightTypeTag#<:<` is simplified on purpose, so a few checks give different answers from the Scala compiler. For example, `Null` is treated as a bottom type like `Nothing`, and `Any`, `AnyRef` and `Matchable` are all treated as top types:

```scala
Tag[Int].tag <:< Tag[AnyRef].tag
// res15: Boolean = true
Tag[Null].tag <:< Tag[Int].tag
// res16: Boolean = true
```

Both checks are `false` in the Scala compiler. The [Limitations](../index.md#limitations) section of the introduction lists all known differences.

## Combining Type Constructors

A `LightTypeTag` for a type constructor, such as the one in `TagK[Option]`, can be applied to argument tags at runtime. This is how `izumi-reflect` builds `Tag[F[A]]` from `TagK[F]` and `Tag[A]` in generic code, and you can do the same with these methods.

### LightTypeTag#combine

`LightTypeTag#combine` applies a type constructor to arguments, in order. The result is equal to the tag of the applied type:

```scala
TagK[Option].tag.combine(Tag[Int].tag)
// res17: LightTypeTag = Option[+Int]
TagK[Option].tag.combine(Tag[Int].tag) == Tag[Option[Int]].tag
// res18: Boolean = true
```

When we pass fewer arguments than the type constructor takes, the result is a partially applied type constructor that takes the remaining parameters:

```scala
type Result[A] = Either[String, A]

TagKK[Either].tag.combine(Tag[String].tag)
// res19: LightTypeTag = ? %1 ? Either[+String,+1]
TagKK[Either].tag.combine(Tag[String].tag) == TagK[Result].tag
// res20: Boolean = true
```

### LightTypeTag#combineNonPos

`LightTypeTag#combineNonPos` applies arguments at chosen positions. Pass `Some(tag)` for a position to fill and `None` for a position to leave open. To fill only the second parameter of `Either`:

```scala
TagKK[Either].tag.combineNonPos(None, Some(Tag[Int].tag))
// res21: LightTypeTag = ? %0 ? Either[+0,+Int]
```

### Type Arguments

`LightTypeTag#typeArgs` returns the tags of the type arguments of an applied type, and an empty list for a type without arguments:

```scala
Tag[Map[String, List[Int]]].tag.typeArgs
// res22: List[LightTypeTag] = List(String, List[+Int])
TagK[List].tag.typeArgs
// res23: List[LightTypeTag] = List()
```

`LightTypeTag#withoutArgs` strips all type arguments, from the type and from its parents, which is useful for rough comparisons by class only:

```scala
Tag[List[Int]].tag.withoutArgs
// res24: LightTypeTag = List
Tag[List[Int]].tag.withoutArgs == Tag[List[String]].tag.withoutArgs
// res25: Boolean = true
```

The result is not a type constructor: the result of `LightTypeTag#withoutArgs` is not equal to the matching `TagK` tag, and you can't call `LightTypeTag#combine` on it. To get back the type constructor of an applied type, summon its `TagK` instead.

## Intersections and Unions

An intersection type, such as `A with B`, or a union type on Scala 3, such as `A | B`, has a tag of its own. These methods take such tags apart and simplify them.

### Decomposing

`LightTypeTag#decompose` returns the members of an intersection, and `LightTypeTag#decomposeUnion` returns the members of a union. For any other type, they return a set with the tag itself:

```scala
Tag[java.io.Serializable with CharSequence].tag.decompose
// res26: Set[LightTypeTag] = Set(CharSequence, Serializable)
Tag[Int | String].tag.decomposeUnion
// res27: Set[LightTypeTag] = Set(Int, String)
Tag[Int].tag.decompose
// res28: Set[LightTypeTag] = Set(Int)
```

### Removing Tautologies

`LightTypeTag#removeIntersectionTautologies` removes members of an intersection that are supertypes of other members, since they don't change the type. `LightTypeTag#removeUnionTautologies` does the opposite for unions, removing members that are subtypes of other members:

```scala
Tag[List[Int] | Seq[Int]].tag.removeUnionTautologies
// res29: LightTypeTag = Seq[+Int]
Tag[Int | Nothing].tag.removeUnionTautologies
// res30: LightTypeTag = Int
```

### Building Intersections and Unions

The `LightTypeTag` companion object can build these types from their parts. You rarely need this; it is an advanced API for code that constructs types at runtime. `LightTypeTag.unionType` builds a union, `LightTypeTag.refinedType` builds an intersection with an optional structural refinement, and `LightTypeTag.wildcardType` builds a wildcard with lower and upper bounds:

```scala
LightTypeTag.unionType(List(Tag[Int].tag, Tag[String].tag)) == Tag[Int | String].tag
// res31: Boolean = true
```

## Rendering

`LightTypeTag` has several ways to turn a type into a string, from short names for log messages to fully qualified names for stable identifiers. To compare them on the same type:

```scala
val mapTag = Tag[Map[String, List[Int]]].tag
// mapTag: LightTypeTag = Map[=String,+List[+Int]]

mapTag.toString
// res32: String = "Map[=String,+List[+Int]]"
mapTag.repr
// res33: String = "scala.collection.immutable.Map[=java.lang.String,+scala.collection.immutable.List[+scala.Int]]"
mapTag.scalaStyledRepr
// res34: String = "scala.collection.immutable.Map[java.lang.String,+scala.collection.immutable.List[+scala.Int]]"
mapTag.shortName
// res35: String = "Map"
mapTag.longNameWithPrefix
// res36: String = "scala.collection.immutable.Map"
```

`LightTypeTag#toString` omits package names. `LightTypeTag#repr` includes them and marks the variance of each type argument. `LightTypeTag#scalaStyledRepr` is also fully qualified, but uses Scala's notation for unapplied type constructors, which is easier to read for type lambdas:

```scala
TagK[Result].tag.repr
// res37: String = "? %0 ? scala.util.Either[+java.lang.String,+0]"
TagK[Result].tag.scalaStyledRepr
// res38: String = "scala.util.Either[+java.lang.String,+_]"
```

`LightTypeTag#shortName` and `LightTypeTag#longNameWithPrefix` render only the name of the type constructor, without arguments: the first without packages, the second with them. `LightTypeTag#longNameInternalSymbol` renders the compiler's internal name for the symbol, which differs from `LightTypeTag#longNameWithPrefix` for types nested in objects:

```scala
Tag[Map.Map1[Int, Int]].tag.longNameWithPrefix
// res39: String = "scala.collection.immutable.Map.Map1"
Tag[Map.Map1[Int, Int]].tag.longNameInternalSymbol
// res40: String = "scala.collection.immutable.Map$.Map1"
```

Two older rendering methods are deprecated: `LightTypeTag#scalaStyledName`, renamed to `LightTypeTag#scalaStyledRepr` in 3.0.8, and `LightTypeTag#longName`, which produces output that differs between Scala versions. Use `LightTypeTag#longNameWithPrefix` instead of `LightTypeTag#longName`.

`LightTypeTag#debug` renders the tag together with the inheritance data it uses for subtype checks. Its output is long; it is meant for bug reports and for [debugging an unexpected result](../guides/debug-a-wrong-tag.md).

## Serialization

A `LightTypeTag` can be written to a compact, versioned form and read back, for example to store type information in a database or to send it between services. `LightTypeTag#serialize` returns a `LightTypeTag.Serialized` value, a case class of a hash code, two strings and a format version, and `LightTypeTag.parse` reads it back:

```scala
val serialized: LightTypeTag.Serialized = Tag[List[Int]].tag.serialize()
```

The two strings hold binary data, so store them in a column or field that preserves arbitrary characters. To check the format version and parse the tag back:

```scala
serialized.version
// res41: Int = 30

LightTypeTag.parse(serialized) == Tag[List[Int]].tag
// res42: Boolean = true
```

The format version identifies the binary layout of the strings; the version that the library on the classpath writes is `LightTypeTag.currentBinaryFormatVersion`. `LightTypeTag.parse` reads tags written by older versions of `izumi-reflect`, but it throws a `LinkageError` for a tag written by a newer version than the one on the classpath. When different services exchange serialized tags, upgrade the readers first. `LightTypeTag.parse` also has an overload that takes the four fields of `LightTypeTag.Serialized` as separate arguments: `LightTypeTag.parse(hashCode, refString, basesString, version)`.

`LightTypeTag` also implements `java.io.Serializable`, like `Tag`, so it works with Java serialization and frameworks built on it.

The [Store and Exchange Type Tags](../guides/store-and-exchange-type-tags.md) guide shows how to write tags to a database or a message, read them back in another process and handle format versions.

## The Underlying Structure

`LightTypeTag#ref` returns a `LightTypeTagRef`, the algebraic data type that holds the structure of the type: named references, applied types, type lambdas, intersections, unions, refinements and wildcards. You rarely need it; it is an advanced API for tools that walk the structure of types, prefer the methods above. Its shape can change between versions.

## Running the Examples

All code from this page is available as runnable examples in the `izumi-reflect-examples` project.

The examples of the Usage section as one program, covering rendering, subtyping, type arguments and combining:

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/lighttypetag/BasicUsage.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
// Run from this folder:  scala-cli run BasicUsage.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.lighttypetag.BasicUsage"

package izumi.reflect.examples.lighttypetag

import izumi.reflect.{Tag, TagK}

/**
  * LightTypeTag ? Usage
  *
  * Renders a LightTypeTag, checks subtyping, reads its type arguments and rebuilds it with combine.
  */
object BasicUsage {
  def main(args: Array[String]): Unit = {
    val listOfInt = Tag[List[Int]].tag

    println(s"listOfInt.toString: ${listOfInt.toString}")
    println(s"listOfInt <:< Tag[Seq[Any]].tag: ${listOfInt <:< Tag[Seq[Any]].tag}")
    println(s"listOfInt.typeArgs: ${listOfInt.typeArgs}")
    println(s"TagK[List].tag.combine(Tag[Int].tag) == listOfInt: ${TagK[List].tag.combine(Tag[Int].tag) == listOfInt}")
  }
}
```

([source](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/lighttypetag/BasicUsage.scala)) Run it with `scala-cli run BasicUsage.scala` from that folder, or with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.lighttypetag.BasicUsage"` from the repository root.
