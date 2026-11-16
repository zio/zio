# Use izumi-reflect Tags with the ZIO Environment

> Write generic ZIO services and layers by adding the Tag and TagK context bounds that the ZIO environment needs.

In this guide, we write generic ZIO code that reads services from the environment: an accessor for any service type, a layer that provides a service for any type parameters, and a service generic in an effect type. Along the way we see why ZIO asks for a `Tag` in each of these places and how to satisfy it. ZIO 2 uses `izumi-reflect` to identify services, so everything here relies on the tags described in the rest of this documentation.

## The Problem

The environment of a ZIO effect, `R` in `ZIO[R, E, A]`, is a map from service types to service values. To find a service at runtime, ZIO needs a value that describes its type, and in ZIO 2 that value is an `izumi-reflect` tag. For concrete types, the compiler creates the tag automatically, so most ZIO code never mentions it. In generic code, it can't, and an accessor for any service type does not compile:

```scala
import zio._

def serviceOf[A]: URIO[A, A] = ZIO.service[A]
// error:
// A is a type parameter without an implicit Tag!
//   could not find implicit value for izumi.reflect.Tag[A]. Did you forget to put on a Tag, TagK or TagKK context bound on one of the parameters in A? e.g. def x[T: Tag, F[_]: TagK] = ...
// 
// def serviceOf[A]: URIO[A, A] = ZIO.service[A]
//                                             ^
```

The fix is always to pass the tag in from the call site, where the type is known.

## Prerequisites

ZIO 2 depends on `izumi-reflect`, so a project that already uses ZIO 2 needs no extra dependency. The `zio` package re-exports the types you need: `zio.Tag`, a subtype of `izumi.reflect.Tag`, and aliases for `izumi.reflect.TagK`, `TagKK` and the rest of the family, `LightTypeTag`, and `EnvironmentTag`, an alias of `izumi.reflect.Tag`. All examples use one import:

```scala
import zio._
```

To run the effects on this page, we use a small helper that runs a ZIO effect and returns its result:

```scala
def run[A](effect: ZIO[Any, Nothing, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
  }
```

## Accessing Any Service

To write an accessor that works for any service type, add a `Tag` context bound to its type parameter. The caller's concrete type then carries its tag into the method, and `ZIO.service` uses it to find the service:

```scala
def serviceOf[A: Tag]: URIO[A, A] = ZIO.service[A]

final case class Config(url: String)
```

To provide a `Config` and read it through the generic accessor:

```scala
run(serviceOf[Config].provide(ZLayer.succeed(Config("jdbc:h2:mem"))))
// res1: Config = Config("jdbc:h2:mem")
```

<details>
  <summary>Runnable version, with the `run` helper from Prerequisites: <code>Step3AccessAnyServiceExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/zioenvironment/Step3AccessAnyServiceExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using dep dev.zio::zio:2.1.27
//> using file Runner.scala
// Run from this folder:  scala-cli run Step3AccessAnyServiceExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.zioenvironment.Step3AccessAnyServiceExample"

package izumi.reflect.examples.zioenvironment

import zio._
import Runner.run

/**
  * Use izumi-reflect Tags with the ZIO Environment ? Accessing Any Service
  *
  * A generic service accessor with a Tag context bound, run with the helper from Prerequisites.
  */
object Step3AccessAnyServiceExample {
  def serviceOf[A: Tag]: URIO[A, A] = ZIO.service[A]

  final case class Config(url: String)

  def main(args: Array[String]): Unit = {
    println(s"serviceOf[Config]: ${run(serviceOf[Config].provide(ZLayer.succeed(Config("jdbc:h2:mem"))))}")
  }
}
```

</details>

## Writing Generic Layers

A layer that is generic in its type parameters needs a tag for every type parameter that appears in the service it provides. With them, ZIO keeps services that differ only in their type arguments apart, because their tags differ. For example, a generic in-memory cache:

```scala
final case class Cache[K, V](entries: Map[K, V]) {
  def get(key: K): Option[V] = entries.get(key)
}

def cacheLayer[K: Tag, V: Tag](entries: (K, V)*): ULayer[Cache[K, V]] =
  ZLayer.succeed(Cache(entries.toMap))
```

`izumi-reflect` builds `Tag[Cache[K, V]]` from the tags of `K` and `V`. An effect can then require two caches that differ only in their type arguments:

```scala
val lookups: URIO[Cache[String, Int] with Cache[Int, String], (Option[Int], Option[String])] =
  for {
    byName <- ZIO.serviceWith[Cache[String, Int]](_.get("answer"))
    byId   <- ZIO.serviceWith[Cache[Int, String]](_.get(42))
  } yield (byName, byId)
```

To provide both caches and run the effect:

```scala
run(lookups.provide(cacheLayer("answer" -> 42), cacheLayer(42 -> "answer")))
// res2: Tuple2[Option[Int], Option[String]] = (Some(42), Some("answer"))
```

A lookup keyed by the class alone could not keep the two `Cache` services apart, because both have the class `Cache`.

<details>
  <summary>Runnable version, with the layers, the effect and the `run` helper in one program: <code>Step4GenericLayersExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/zioenvironment/Step4GenericLayersExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using dep dev.zio::zio:2.1.27
//> using file Runner.scala
// Run from this folder:  scala-cli run Step4GenericLayersExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.zioenvironment.Step4GenericLayersExample"

package izumi.reflect.examples.zioenvironment

import zio._
import Runner.run

/**
  * Use izumi-reflect Tags with the ZIO Environment ? Writing Generic Layers
  *
  * Provides two caches that differ only in their type arguments and reads both.
  */
object Step4GenericLayersExample {
  final case class Cache[K, V](entries: Map[K, V]) {
    def get(key: K): Option[V] = entries.get(key)
  }

  def cacheLayer[K: Tag, V: Tag](entries: (K, V)*): ULayer[Cache[K, V]] =
    ZLayer.succeed(Cache(entries.toMap))

  def main(args: Array[String]): Unit = {
    val lookups: URIO[Cache[String, Int] with Cache[Int, String], (Option[Int], Option[String])] =
      for {
        byName <- ZIO.serviceWith[Cache[String, Int]](_.get("answer"))
        byId <- ZIO.serviceWith[Cache[Int, String]](_.get(42))
      } yield (byName, byId)

    println(s"lookups: ${run(lookups.provide(cacheLayer("answer" -> 42), cacheLayer(42 -> "answer")))}")
  }
}
```

</details>

## Writing Services Generic in an Effect Type

In tagless-final style, a service is generic in an effect type `F[_]`. To put such a service in the ZIO environment, the code that builds or reads it needs a `TagK[F]`, which describes the type constructor `F`. `izumi-reflect` combines it with the rest of the type to build `Tag[Repository[F]]`:

```scala
trait Repository[F[_]] {
  def find(id: Int): F[String]
}

def repositoryLayer[F[_]: TagK](repo: Repository[F]): ULayer[Repository[F]] =
  ZLayer.succeed(repo)

def findWith[F[_]: TagK](id: Int): URIO[Repository[F], F[String]] =
  ZIO.serviceWith[Repository[F]](_.find(id))
```

An `Option`-based repository implements it for one effect type:

```scala
val optionRepo: Repository[Option] = new Repository[Option] {
  def find(id: Int): Option[String] = if (id == 1) Some("Alice") else None
}
```

To provide the repository and call it through the generic accessor:

```scala
run(findWith[Option](1).provide(repositoryLayer(optionRepo)))
// res3: Option[String] = Some("Alice")
```

Without the `TagK` context bound on `findWith`, compilation fails with a message asking for a `Tag`, `TagK` or `TagKK` for `F`.

<details>
  <summary>Runnable version, with the service, the repository and the `run` helper in one program: <code>Step5EffectTypeExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/zioenvironment/Step5EffectTypeExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using dep dev.zio::zio:2.1.27
//> using file Runner.scala
// Run from this folder:  scala-cli run Step5EffectTypeExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.zioenvironment.Step5EffectTypeExample"

package izumi.reflect.examples.zioenvironment

import zio._
import Runner.run

/**
  * Use izumi-reflect Tags with the ZIO Environment ? Writing Services Generic in an Effect Type
  *
  * A tagless-final repository in the ZIO environment, read through a generic accessor with a TagK context bound.
  */
object Step5EffectTypeExample {
  trait Repository[F[_]] {
    def find(id: Int): F[String]
  }

  def repositoryLayer[F[_]: TagK](repo: Repository[F]): ULayer[Repository[F]] =
    ZLayer.succeed(repo)

  def findWith[F[_]: TagK](id: Int): URIO[Repository[F], F[String]] =
    ZIO.serviceWith[Repository[F]](_.find(id))

  def main(args: Array[String]): Unit = {
    val optionRepo: Repository[Option] = new Repository[Option] {
      def find(id: Int): Option[String] = if (id == 1) Some("Alice") else None
    }

    println(s"findWith[Option](1): ${run(findWith[Option](1).provide(repositoryLayer(optionRepo)))}")
  }
}
```

</details>

## Looking Up Services by Supertype

ZIO does not need an exact match to find a service. It looks for a service whose type is the requested type or a subtype of it, using `LightTypeTag#<:<`, and caches the result per requested tag. So an effect can ask for an interface while the environment holds an implementation registered under its own type:

```scala
trait Clock2 { def now: Long }
final case class FixedClock(now: Long) extends Clock2
```

To store a `FixedClock` and read it as a `Clock2`:

```scala
val environment: ZEnvironment[FixedClock] = ZEnvironment(FixedClock(1700000000L))
// environment: ZEnvironment[FixedClock] = ZEnvironment(MdocSession::MdocApp::FixedClock -> FixedClock(1700000000))

environment.get[Clock2].now
// res4: Long = 1700000000L
```

When several services match the requested type, ZIO returns the one added most recently. To avoid surprises, register each service under the type its users ask for, as `ZLayer.succeed[Clock2](FixedClock(...))` does.

<details>
  <summary>Runnable version, with the service types and the lookup in one program: <code>Step6SupertypeLookupExample.scala</code></summary>

```scala title="izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/zioenvironment/Step6SupertypeLookupExample.scala" showLineNumbers
//> using scala 3.3.8
//> using dep dev.zio::izumi-reflect:3.0.11
//> using dep dev.zio::zio:2.1.27
// Run from this folder:  scala-cli run Step6SupertypeLookupExample.scala
// Or from the repository root:  sbt "izumi-reflect-examples/runMain izumi.reflect.examples.zioenvironment.Step6SupertypeLookupExample"

package izumi.reflect.examples.zioenvironment

import zio._

/**
  * Use izumi-reflect Tags with the ZIO Environment ? Looking Up Services by Supertype
  *
  * Stores a FixedClock in a ZEnvironment and reads it back as its supertype Clock2.
  */
object Step6SupertypeLookupExample {
  trait Clock2 { def now: Long }
  final case class FixedClock(now: Long) extends Clock2

  def main(args: Array[String]): Unit = {
    val environment: ZEnvironment[FixedClock] = ZEnvironment(FixedClock(1700000000L))

    println(s"environment.get[Clock2].now: ${environment.get[Clock2].now}")
  }
}
```

</details>

## Common Mistakes

Most problems with tags in ZIO code have one of three causes:

- **A missing context bound.** The compile error names the type that has no tag. Add `A: Tag` for a proper type, or `F[_]: TagK` for a type constructor, to the method that has the type parameter, and to every generic method that calls it.
- **A tag for the wrong type.** When `A` is inferred, the tag describes the inferred type, which may be more specific than you meant, such as `FixedClock` instead of `Clock2`. Write the type argument explicitly in generic calls like `ZLayer.succeed[Clock2](...)`.
- **Abstract type members.** A service type that refers to an abstract type member, such as `x.Out`, needs a tag for that member in scope, like any other type parameter.

To register a `FixedClock` under the type its users ask for, and read it back as that type:

```scala
run(ZIO.serviceWith[Clock2](_.now).provide(ZLayer.succeed[Clock2](FixedClock(1700000000L))))
// res5: Long = 1700000000L
```

To see why a lookup fails at runtime, print the tags involved with `Tag[A].tag` and compare them, or follow [Debug a Wrong Tag or Subtype Check](debug-a-wrong-tag.md).

## Running the Examples

All runnable code from this guide is in [`izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/zioenvironment/`](https://github.com/zio/izumi-reflect/tree/develop/izumi-reflect/izumi-reflect-examples/src/main/scala-3/izumi/reflect/examples/zioenvironment). Run any file from that folder with `scala-cli run <File>.scala`, or from the repository root with `sbt "izumi-reflect-examples/runMain izumi.reflect.examples.zioenvironment.<ObjectName>"`.

## Going Further

The ZIO environment is a type-indexed map like the one in [Build a Type-Safe Heterogeneous Map](heterogeneous-map.md). [Tag and the TagK Family](../reference/tag.md) lists all tag types and their kinds, and [LightTypeTag](../reference/light-type-tag.md#subtyping) explains how subtype checks work and where they are less precise than the compiler.
