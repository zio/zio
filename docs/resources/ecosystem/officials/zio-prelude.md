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

## Resources

- [SF Scala: Reimagining Functional Type Classes](https://www.youtube.com/watch?v=OwmHgL9F_9Q) John A. De Goes and Adam Fraser (August 2020) — In this presentation, John A. De Goes and Adam Fraser introduce a new Scala library with a completely different factoring of functional type classes—one which throws literally everything away and starts from a clean slate. In this new factoring, type classes leverage Scala’s strengths, including variance and modularity. Pieces fit together cleanly and uniformly, and in a way that satisfies existing use cases, but enables new ones never before possible. Finally, type classes are named, organized, and described in a way that makes teaching them easier, without compromising on algebraic principles.
- [The Terror-Free Guide To Introducing Functional Scala At Work](https://www.youtube.com/watch?v=Sinde_P7nmY) by Jorge Vasquez (December 2020) — Too often, our applications are dominated by boilerplate that's not fun to write or test, and that makes our business logic complicated. In object-oriented programming, classes and interfaces help us with abstraction to reduce boilerplate. But, in functional programming, we use type classes. Historically, type classes in functional programming have been very complex and confusing, partially because they import ideas from Haskell that don't make sense in Scala, and partially because of their esoteric origins in category theory. In this presentation, Jorge Vásquez presents a new library called ZIO Prelude, which offers a distinctly Scala take on Functional Abstractions, and you will learn how you can eliminate common types of boilerplate by using it. Come see how you can improve your happiness and productivity with a new take on what it means to do functional programming in Scala!
- [ZIO WORLD - ZIO Prelude](https://www.youtube.com/watch?v=69ngoqVXKPI) by Jorge Vasquez (March 2020) — In this talk, Jorge Vasques discusses his work bringing refined newtypes to ZIO Prelude, which are working natively on Scala 3 with a beautiful syntax and DSL.
- [Zymposium - ZIO Prelude](https://www.youtube.com/watch?v=M3HmROwOoRU) by Adam Fraser and Kit Langton (May 2021) — We'll see how ZIO Prelude gives us the tools for solving some common problems in day-to-day development. We'll also see how ZIO Prelude provides a set of abstractions we can use for inspiration when implementing our own data types but never forces us to use these abstractions.
- [Zymposium - Prelude Redux (Type-classes without Type-classes)](https://www.youtube.com/watch?v=97Yc0Ub9aZ8) by Adam and Kit Langton (May 2021) — We will see how thinking in terms of producers and consumers of values can give us powerful insights into the structure of our programs and how we can use these to develop composable operators for own data types, regardless of whether or not we choose to depend on a library like ZIO Prelude.
