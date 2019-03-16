---
layout: docs
position: 3
section: overview
title:  "Overview"
---

# {{page.title}}

ZIO is a library for asynchronous and concurrent programming that is based on pure functional programming. 

> For background on how pure functional programming deals with effects like input and output, see the [Background](background.html) section.

At the core of ZIO is `ZIO`, a powerful data type inspired by Haskell's `IO` monad. The `ZIO` effect type lets you solve complex problems with simple, type-safe, testable, and composable code.

# ZIO 

The `ZIO[R, E, A]` data type has three type parameters:

 - **`R` - Environment Type**. This is the type of environment required by the effect. An effect that has no requirements can use `Any` for the type parameter.
 - **`E` - Failure Type**. This is the type of value the effect may fail with. Some applications will use `Throwable`. A type of `Nothing` indicates the effect cannot fail.
 - **`A` - Success Type**. This is the type of value the effect may succeed with. This type parameter will depend on the specific effect, but `Unit` can be used for effects that do not produce any useful information, while `Nothing` can be used for effects that run forever.

For example, a value of type `ZIO[Any, IOException, Byte]` is an effect that has no requirements; it may fail with a value of type `IOException`, or it may succeed with a value of type `Byte`.

# Type Aliases

Although the `ZIO` data type is the only effect type in ZIO, there are a family of type aliases and companion objects that make it easier to work with common cases:

 - `UIO[A]` — This is a type alias for `ZIO[Any, Nothing, A]`, which represents an effect that has no requirements, and cannot fail, or succeed with an `A`.
 - `Task[A]` — This is a type alias for `ZIO[Any, Throwable, A]`, which represents an effect that has no requirements, and may fail with a `Throwable` value, or succeed with an `A`.
 - `TaskR[R, A]` — This is a type alias for `ZIO[R, Throwable, A]`, which represents an effect that requires an `R`, and may fail with a `Throwable` value, or succeed with an `A`.
 - `IO[E, A]` — This is a type alias for `ZIO[Any, E, A]`, which represents an effect that has no requirements, and may fail with an `E`, or succeed with an `A`.

The type aliases all have companion objects, and these companion objects have methods that can be used to construct values of the appropriate type.

If you are new to functional effects, we recommend starting with the `Task` type, which has a single type parameter, and corresponds most closely to the `Future` data type built into Scala's standard library.

If you are using Cats Effect libraries, you may find the `TaskR` type to be most useful, since it allows you to thread requirements through third-party libraries and your application.

No matter what type alias you use in your application, `UIO` can be useful for describing infallible effects, including those resulting from handling all errors on fallible effects.

Finally, if you are an experienced functional programmer, then direct use of the `ZIO` data type is recommended, although you may find it useful to create your own family of type aliases in different parts of your application.

# Next Steps

If you are comfortable with the ZIO data type (and its family of type aliases), the next step is learning how to [create effects](creating_effects.html).