# Introduction to ZIO Prelude

> [ZIO Prelude](https://github.com/zio/zio-prelude) is a lightweight, distinctly Scala take on **functional abstractions**, with tight ZIO integration.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-prelude/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-prelude_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-prelude_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-prelude_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-prelude_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-prelude_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-prelude_2.13) [![ZIO Prelude](https://img.shields.io/github/stars/zio/zio-prelude?style=social)](https://github.com/zio/zio-prelude)

## Introduction

ZIO Prelude is a small library that brings common, useful algebraic abstractions and data types to scala developers. It is an alternative to libraries like _Scalaz_ and _Cats_ based on radical ideas that embrace **modularity** and **subtyping** in Scala and offer **new levels of power and ergonomics**. It throws out the classic functor hierarchy in favor of a modular algebraic approach that is smaller, easier to understand and teach, and more expressive.

ZIO Prelude has three key areas of focus:

1. **Data structures, and type classes for traversing them.** ZIO Prelude embraces the collections in the Scala standard library, and extends them with new instances and new useful additions.
2. **Patterns of composition for types.** ZIO Prelude provides a small catalog of patterns for binary operators, which combine two values into another value of the same type. These patterns are named after the algebraic laws they satisfy: associativity, commutativity, and identity.
3. **Patterns of composition for type constructors.** ZIO Prelude provides a catalog of patterns for binary operators on type constructors (things like `Future`, `Option`, ZIO `Task`). These patterns are named after the algebraic laws they satisfy (associativity, commutativity, and identity) and the structure they produce, whether a tuple or an either.

Design principles behind ZIO Prelude:

1. **Radical** — So basically it ignores all dogma, and it is completely written with a new mindset.
2. **Orthogonality** — The goal for ZIO Prelude is to have no overlap. Type classes should do one thing and fit it well. So there is not any duplication to describe type classes.
3. **Principled** — All type classes in ZIO Prelude include a set of laws that instances must obey.
4. **Pragmatic** — If we have data types that don't satisfy laws but that are still useful to use in most cases, we can go ahead and provide instances for them.
5. **Scala-First** - It embraces subtyping and benefit from object-oriented features of Scala.

ZIO Prelude gives us:

- **[Functional Data Types](functional-data-types/index.md)**— Additional data types to supplement the ones in the Scala standard library such as `Validation` and `NonEmptyList` to enable more accurate domain modeling and handle common problems like data validation. For example:
  - `NonEmptyList`, `NonEmptySet`
  - `ZSet`, `ZNonEmptySet`
  - `Validation`, `ZValidation`
- **[Functional Abstractions](functional-abstractions/index.md)**— Functional abstractions to describe different ways of combining data, making it easy for us to combine complex data types in a principled way.
- **[New Types](newtypes/index.md)**— that allow to _increase type safety_ in domain modeling. Wrapping existing type adding no runtime overhead. These refined newtypes allow us to increase the type safety of our code base with zero overhead and minimal boilerplate.
- **[ZPure](zpure/index.md)**— A description of a computation that supports logging, context, state, and errors, providing all the functionality traditionally offered by monad transformers with dramatically better performance and ergonomics.

The `zio.prelude.fx` package holds `ZPure`, the data type for pure computations with state, logging, an environment and errors.

## Installation

ZIO Prelude is published for Scala 2.12, 2.13 and 3. Add the core module to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-prelude" % "1.0.0-RC49"
```

On Scala.js and Scala Native use `%%%` instead of `%%`.

## Modules

The core module contains everything most programs need. The other modules are optional.

### Core

```scala
libraryDependencies ++= Seq(
  // Type classes, data types, newtypes and ZPure. JVM, Scala.js, Native.
  "dev.zio" %% "zio-prelude" % "1.0.0-RC49"
)
```

### Testing

```scala
libraryDependencies ++= Seq(
  // Laws and generators for testing your own instances with ZIO Test. JVM, Scala.js, Native.
  "dev.zio" %% "zio-laws-laws" % "1.0.0-RC49" % Test
)
```

See [Laws](laws/index.md).

### Derivation

```scala
libraryDependencies ++= Seq(
  // Automatic Debug derivation based on Magnolia. JVM, Scala.js, Native.
  "dev.zio" %% "zio-prelude-magnolia" % "1.0.0-RC49"
)
```

See [Magnolia Derivation](magnolia/index.md).

### Experimental

```scala
libraryDependencies ++= Seq(
  // Experimental abstractions such as Absorption, Subtract and Divide. JVM, Scala.js, Native.
  "dev.zio" %% "zio-prelude-experimental" % "1.0.0-RC49",

  // Laws for the experimental abstractions. JVM, Scala.js, Native.
  "dev.zio" %% "zio-prelude-experimental-laws" % "1.0.0-RC49" % Test
)
```

See [Experimental Abstractions](experimental/index.md).

### Collections

```scala
libraryDependencies ++= Seq(
  // Instances and syntax for Scala parallel collections. JVM and Native only.
  "dev.zio" %% "zio-prelude-scala-parallel-collections" % "1.0.0-RC49"
)
```

See [Scala Parallel Collections](scala-parallel-collections/index.md), and note that the published artifact is empty from `1.0.0-RC37` to `1.0.0-RC48`.

### Internal Modules

`zio-prelude-macros` holds the macros behind smart newtypes. It comes with the core module, so you never add it yourself.

## Example

In this example, we are going to create a simple voting application. We will use two features of ZIO Prelude:
1. To become more type safety we are going to use _New Types_ and introducing `Topic` and `Votes` data types.
2. Providing instance of `Associative` type class for `Votes` data type which helps us to combine `Votes` values.

```scala
import zio.prelude._

object VotingExample extends scala.App {

  object Votes extends Subtype[Int] {
    implicit val associativeVotes: Associative[Votes] =
      new Associative[Votes] {
        override def combine(l: => Votes, r: => Votes): Votes =
          Votes(l + r)
      }
  }
  type Votes = Votes.Type

  object Topic extends Subtype[String]
  type Topic = Topic.Type

  final case class VoteState(map: Map[Topic, Votes]) { self =>
    def combine(that: VoteState): VoteState =
      VoteState(self.map combine that.map)
  }

  val zioHttp    = Topic("zio-http")
  val uziHttp    = Topic("uzi-http")
  val zioTlsHttp = Topic("zio-tls-http")

  val leftVotes  = VoteState(Map(zioHttp -> Votes(4), uziHttp -> Votes(2)))
  val rightVotes = VoteState(Map(zioHttp -> Votes(2), zioTlsHttp -> Votes(2)))

  println(leftVotes combine rightVotes)
  // Output: VoteState(Map(zio-http -> 6, uzi-http -> 2, zio-tls-http -> 2))
}
```
