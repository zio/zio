---
id: zio-optics
title: "ZIO Optics"
---

[ZIO Optics](https://github.com/zio/zio-optics) is a library that makes it easy to modify parts of larger data structures based on a single representation of an optic as a combination of a getter and setter.

## Introduction

When we are working with immutable nested data structures, updating and reading operations could be tedious with lots of boilerplates. Optics is a functional programming construct that makes these operations more clear and readable.

Key features of ZIO Optics:

- **Unified Optic Data Type** — All the data types like `Lens`, `Prism`, `Optional`, and so forth are type aliases for the core `Optic` data type.
- **Composability** — We can compose optics to create more advanced ones.
- **Embracing the Tremendous Power of Concretion** — Using concretion instead of unnecessary abstractions, makes the API more ergonomic and easy to use.
- **Integration with ZIO Data Types** — It supports effectful and transactional optics that works with ZIO data structures like `Ref` and `TMap`.
- **Helpful Error Channel** — Like ZIO, the `Optics` data type has error channels to include failure details.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-optics" % "0.1.0"
```

## Example

In this example, we are going to update a nested data structure using ZIO Optics:

```scala
import zio.optics._

case class Developer(name: String, manager: Manager)
case class Manager(name: String, rating: Rating)
case class Rating(upvotes: Int, downvotes: Int)

val developerLens = Lens[Developer, Manager](
  get = developer => Right(developer.manager),
  set = manager => developer => Right(developer.copy(manager = manager))
)

val managerLens = Lens[Manager, Rating](
  get = manager => Right(manager.rating),
  set = rating => manager => Right(manager.copy(rating = rating))
)

val ratingLens = Lens[Rating, Int](
  get = rating => Right(rating.upvotes),
  set = upvotes => rating => Right(rating.copy(upvotes = upvotes))
)

// Composing lenses
val optic = developerLens >>> managerLens >>> ratingLens

val jane    = Developer("Jane", Manager("Steve", Rating(0, 0)))
val updated = optic.update(jane)(_ + 1)

println(updated)
```

## Resources

- [Zymposium - Optics](https://www.youtube.com/watch?v=-km5ohYhJa4) by Adam Fraser and Kit Langton (June 2021) — Optics are great tools for working with parts of larger data structures and come up in disguise in many places such as ZIO Test assertions.
