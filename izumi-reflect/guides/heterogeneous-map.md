# Build a Type-Safe Heterogeneous Map

> Store values of different types in one map keyed by their full Scala type, and look them up by type or supertype.

In this guide, we build `TypedMap`, a small immutable map that stores values of different types and finds them again by type, as in `map.get[List[Int]]`. It is the same idea that dependency injection containers and ZIO's environment are built on. We start with lookups by exact type, then make the map work in generic code, and finally add lookups by supertype.

## The Problem

A map from types to values needs a key that identifies a type at runtime. The obvious key on the JVM is a `Class`, but a class doesn't know the type arguments of a generic type: `List[Int]` and `List[String]` share the class `List`, so their values overwrite each other.

For example, consider a registry of settings keyed by class:

```scala
final case class ClassMap(entries: Map[Class[_], Any]) {
  def put[A](value: A)(implicit ct: scala.reflect.ClassTag[A]): ClassMap =
    ClassMap(entries + (ct.runtimeClass -> value))

  def get[A](implicit ct: scala.reflect.ClassTag[A]): Option[A] =
    entries.get(ct.runtimeClass).map(_.asInstanceOf[A])
}

val classMap = ClassMap(Map.empty)
  .put(List(1, 2, 3))
  .put(List("a", "b"))
// classMap: ClassMap = ClassMap(
//   Map(class scala.collection.immutable.List -> List("a", "b"))
// )

classMap.get[List[Int]]
// res0: Option[List[Int]] = Some(List("a", "b"))
```

The lookup for `List[Int]` returns the list of strings: the second call to `ClassMap#put` replaces the first one, and the cast in `ClassMap#get` cannot detect it. The error only shows up later, as a `ClassCastException` far from its cause. A `Tag` from `izumi-reflect` knows the full type, including type arguments, so it makes a safe key.

## Prerequisites

Add `izumi-reflect` to your build:

```scala
libraryDependencies += "dev.zio" %% "izumi-reflect" % "3.0.12-SNAPSHOT"
```

The guide uses `Tag`, described in [Tag and the TagK Family](../reference/tag.md), and its `LightTypeTag`, described in [LightTypeTag](../reference/light-type-tag.md). All examples use these imports:

```scala
import izumi.reflect.{Tag, TagK}
import izumi.reflect.macrortti.LightTypeTag
```

## Storing Values by Exact Type

`TypedMap` keeps its entries in a `Map[LightTypeTag, Any]`. `LightTypeTag` implements `LightTypeTag#equals` and `LightTypeTag#hashCode` as type equality, so it works as a map key directly. The `TypedMap#put` and `TypedMap#get` methods ask for a `Tag` of the value type and use its `tag` field as the key:

```scala
final case class TypedMap(entries: Map[LightTypeTag, Any]) {
  def put[A: Tag](value: A): TypedMap =
    TypedMap(entries + (Tag[A].tag -> value))

  def get[A: Tag]: Option[A] =
    entries.get(Tag[A].tag).map(_.asInstanceOf[A])
}

object TypedMap {
  val empty: TypedMap = TypedMap(Map.empty)
}
```

The cast in `TypedMap#get` is safe: a value is only ever stored under the tag of its own type. Now the two lists get separate keys:

```scala
val settings = TypedMap.empty
  .put(List(1, 2, 3))
  .put(List("a", "b"))
  .put(Map("timeout" -> 30))
// settings: TypedMap = TypedMap(
//   Map(
//     List[+Int] -> List(1, 2, 3),
//     List[+String] -> List("a", "b"),
//     Map[=String,+Int] -> Map("timeout" -> 30)
//   )
// )

settings.get[List[Int]]
// res1: Option[List[Int]] = Some(List(1, 2, 3))
settings.get[List[String]]
// res2: Option[List[String]] = Some(List("a", "b"))
settings.get[Map[String, Int]]
// res3: Option[Map[String, Int]] = Some(Map("timeout" -> 30))
settings.get[Option[Int]]
// res4: Option[Option[Int]] = None
```

Type aliases and different import paths name the same type, so they find the same entry:

```scala
type Ports = List[Int]

settings.get[Ports]
// res5: Option[Ports] = Some(List(1, 2, 3))
settings.get[scala.collection.immutable.List[Int]]
// res6: Option[List[Int]] = Some(List(1, 2, 3))
```

## Using the Map in Generic Code

A `TypedMap` is most useful inside generic code, where values come in as type parameters. Because `TypedMap#put` and `TypedMap#get` only need a `Tag`, a generic method can call them as long as it has a `Tag` context bound for its own type parameter. `izumi-reflect` builds the tag for the full type from the tags in scope:

```scala
def putAll[A: Tag](map: TypedMap, values: List[A]): TypedMap =
  map.put(values).put(values.headOption)
```

Calling it with a concrete type stores the values under `List[A]` and `Option[A]` for that type:

```scala
val withScores = putAll(TypedMap.empty, List(9.5, 7.0))
// withScores: TypedMap = TypedMap(
//   Map(List[+Double] -> List(9.5, 7.0), Option[+Double] -> Some(9.5))
// )

withScores.get[List[Double]]
// res7: Option[List[Double]] = Some(List(9.5, 7.0))
withScores.get[Option[Double]]
// res8: Option[Option[Double]] = Some(Some(9.5))
```

The same works for type constructors. A method generic in `F[_]` asks for a `TagK[F]`, and can then store and read values of types such as `F[String]`:

```scala
def putGreeting[F[_]: TagK](map: TypedMap, greeting: F[String]): TypedMap =
  map.put(greeting)
```

To store an `Option[String]` through the generic method and read it back:

```scala
putGreeting[Option](TypedMap.empty, Some("hello")).get[Option[String]]
// res9: Option[Option[String]] = Some(Some("hello"))
```

If a generic method forgets the context bound, for example `def broken[A](map: TypedMap, a: A) = map.put(a)`, compilation fails with a message that asks for a `Tag` for `A`. The map never stores a value under an incomplete key.

<details>
  <summary>Runnable version, with the `TypedMap` from "Storing Values by Exact Type": <code>Step4GenericCodeExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/heterogeneousmap/Step4GenericCodeExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using file TypedMap.scala
// Run from this folder:  scala-cli run Step4GenericCodeExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.heterogeneousmap.Step4GenericCodeExample"

package izumi.reflect.examples.heterogeneousmap

import izumi.reflect.{Tag, TagK}

/**
  * Build a Type-Safe Heterogeneous Map ? Using the Map in Generic Code
  *
  * Stores values through generic methods with Tag and TagK context bounds, using the TypedMap from the previous section.
  */
object Step4GenericCodeExample {
  def putAll[A: Tag](map: TypedMap, values: List[A]): TypedMap =
    map.put(values).put(values.headOption)

  def putGreeting[F[_]: TagK](map: TypedMap, greeting: F[String]): TypedMap =
    map.put(greeting)

  def main(args: Array[String]): Unit = {
    val withScores = putAll(TypedMap.empty, List(9.5, 7.0))

    println(s"get[List[Double]]: ${withScores.get[List[Double]]}")
    println(s"get[Option[Double]]: ${withScores.get[Option[Double]]}")
    println(s"get[Option[String]]: ${putGreeting[Option](TypedMap.empty, Some("hello")).get[Option[String]]}")
  }
}
```

</details>

## Looking Up by Supertype

Exact lookups are not always enough. A plugin registry, for example, stores concrete implementations but is queried by interface. `LightTypeTag#<:<` checks subtyping at runtime, so we can find all values whose type is a subtype of the requested one:

```scala
implicit final class TypedMapSubtypes(private val map: TypedMap) {
  def getAll[A: Tag]: List[A] =
    map.entries.collect {
      case (key, value) if key <:< Tag[A].tag => value.asInstanceOf[A]
    }.toList
}
```

To query the settings stored earlier by the supertype `Seq[Any]`, which matches both lists, and by `Iterable[Int]`, which matches only the list of numbers:

```scala
settings.getAll[Seq[Any]]
// res10: List[Seq[Any]] = List(List(1, 2, 3), List("a", "b"))
settings.getAll[Iterable[Int]]
// res11: List[Iterable[Int]] = List(List(1, 2, 3))
```

The subtype check respects variance: `List[String]` is a subtype of `Seq[Any]` because `List` and `Seq` are covariant, but not of `Iterable[Int]`. A `Map[String, Int]` is an `Iterable[(String, Int)]`, not an `Iterable[Int]`, so it does not match either.

:::note
A subtype lookup tests every entry, so it is slower than an exact lookup. If you query the same supertype often, cache the result per tag, the way ZIO's environment does.
:::

<details>
  <summary>Runnable version, with the settings stored in "Storing Values by Exact Type": <code>Step5SupertypeExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/heterogeneousmap/Step5SupertypeExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using file TypedMap.scala
// Run from this folder:  scala-cli run Step5SupertypeExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.heterogeneousmap.Step5SupertypeExample"

package izumi.reflect.examples.heterogeneousmap

import izumi.reflect.Tag

/**
  * Build a Type-Safe Heterogeneous Map ? Looking Up by Supertype
  *
  * Finds all values whose type is a subtype of the requested type, starting from the settings stored in "Storing Values by Exact Type".
  */
object Step5SupertypeExample {
  implicit final class TypedMapSubtypes(private val map: TypedMap) {
    def getAll[A: Tag]: List[A] =
      map
        .entries.collect {
          case (key, value) if key <:< Tag[A].tag => value.asInstanceOf[A]
        }.toList
  }

  def main(args: Array[String]): Unit = {
    // Setup from "Storing Values by Exact Type": three settings stored by exact type
    val settings = TypedMap
      .empty
      .put(List(1, 2, 3))
      .put(List("a", "b"))
      .put(Map("timeout" -> 30))

    println(s"getAll[Seq[Any]]: ${settings.getAll[Seq[Any]]}")
    println(s"getAll[Iterable[Int]]: ${settings.getAll[Iterable[Int]]}")
  }
}
```

</details>

## Running the Examples

All runnable code from this guide is in [`izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/heterogeneousmap/`](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/heterogeneousmap). Run any file from that folder with `scala-cli run <File>.scala`, or from the repository root with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.heterogeneousmap.<ObjectName>"`.

## Going Further

`TypedMap` covers the core of a type-indexed container. From here you can:

- Add a `remove[A: Tag]` method, or a `getOrElse` that reports the missing type with `Tag[A].tag.toString`.
- Store tags of type constructors with `TagK` to register one implementation per effect type.
- Read [Use izumi-reflect Tags with the ZIO Environment](zio-environment.md) to see how ZIO builds its environment on the same idea.
- Read [LightTypeTag](../reference/light-type-tag.md#subtyping) for the details and limits of subtype checks.
