---
id: zio-prelude
title: "ZIO Prelude"
---

[ZIO Prelude](https://github.com/zio/zio-prelude) is a lightweight, distinctly Scala take on **functional abstractions**, with tight ZIO integration.

## Introduction

ZIO Prelude is a small library that brings common, useful algebraic abstractions and data types to scala developers.

It is an alternative to libraries like _Scalaz_ and _Cats_ based on radical ideas that embrace **modularity** and **subtyping** in Scala and offer **new levels of power and ergonomics**. It throws out the classic functor hierarchy in favor of a modular algebraic approach that is smaller, easier to understand and teach, and more expressive.

Design principles behind ZIO Prelude:

1. **Radical** — So basically it ignores all dogma and it is completely written with a new mindset.
2. **Orthogonality** — The goal for ZIO Prelude is to have no overlap. Type classes should do one thing and fit it well. So there is not any duplication to describe type classes.
3. **Principled** — All type classes in ZIO Prelude include a set of laws that instances must obey.
4. **Pragmatic** — If we have data types that don't satisfy laws but that are still useful to use in most cases, we can go ahead and provide instances for them.
5. **Scala-First** - It embraces subtyping and benefit from object-oriented features of Scala.

ZIO Prelude gives us:
- **Data Types** that complements the Scala Standard Library:
    - `NonEmptyList`, `NonEmptySet`
    - `ZSet`, `ZNonEmptySet`
    - `Validation`
    - `ZPure`
- **Type Classes** to describe similarities across different types to eliminate duplications and boilerplates:
    - Business entities (`Person`, `ShoppingCart`, etc.)
    - Effect-like structures (`Try`, `Option`, `Future`, `Either`, etc.)
    - Collection-like structures (`List`, `Tree`, etc.)
- **New Types** that allow to _increase type safety_ in domain modeling. Wrapping existing type adding no runtime overhead.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-prelude" % "1.0.0-RC5"
```

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
