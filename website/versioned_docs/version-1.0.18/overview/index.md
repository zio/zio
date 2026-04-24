---
id: overview_index
title: "Summary"
---

ZIO is a library for asynchronous and concurrent programming that is based on pure functional programming. 

> For background on how pure functional programming deals with effects like input and output, see the [Background](background.md) section.

At the core of ZIO is `ZIO`, a powerful effect type inspired by Haskell's `IO` monad. This data type lets you solve complex problems with simple, type-safe, testable, and composable code.

## ZIO 

The `ZIO[R, E, A]` data type has three type parameters:

 - **`R` - Environment Type**. The effect requires an environment of type `R`. If this type parameter is `Any`, it means the effect has no requirements, because you can run the effect with any value (for example, the unit value `()`).
 - **`E` - Failure Type**. The effect may fail with a value of type `E`. Some applications will use `Throwable`. If this type parameter is `Nothing`, it means the effect cannot fail, because there are no values of type `Nothing`.
 - **`A` - Success Type**. The effect may succeed with a value of type `A`. If this type parameter is `Unit`, it means the effect produces no useful information, while if it is `Nothing`, it means the effect runs forever (or until failure).

For example, an effect of type `ZIO[Any, IOException, Byte]` has no requirements, may fail with a value of type `IOException`, or may succeed with a value of type `Byte`.

A value of type `ZIO[R, E, A]` is like an effectful version of the following function type:

```scala
R => Either[E, A]
```

This function, which requires an `R`, might produce either an `E`, representing failure, or an `A`, representing success. ZIO effects are not actually functions, of course, because they model complex effects, like asynchronous and concurrent effects.

## Type Aliases

The `ZIO` data type is the only effect type in ZIO. However, there are a family of type aliases and companion objects that simplify common cases:

 - `UIO[A]` — This is a type alias for `ZIO[Any, Nothing, A]`, which represents an effect that has no requirements, and cannot fail, but can succeed with an `A`.
 - `URIO[R, A]` — This is a type alias for `ZIO[R, Nothing, A]`, which represents an effect that requires an `R`, and cannot fail, but can succeed with an `A`.
 - `Task[A]` — This is a type alias for `ZIO[Any, Throwable, A]`, which represents an effect that has no requirements, and may fail with a `Throwable` value, or succeed with an `A`.
 - `RIO[R, A]` — This is a type alias for `ZIO[R, Throwable, A]`, which represents an effect that requires an `R`, and may fail with a `Throwable` value, or succeed with an `A`.
 - `IO[E, A]` — This is a type alias for `ZIO[Any, E, A]`, which represents an effect that has no requirements, and may fail with an `E`, or succeed with an `A`.

These type aliases all have companion objects, and these companion objects have methods that can be used to construct values of the appropriate type.

If you are new to functional effects, we recommend starting with the `Task` type, which has a single type parameter, and corresponds most closely to the `Future` data type built into Scala's standard library.

If you are using _Cats Effect_ libraries, you may find the `RIO` type useful, since it allows you to thread environments through third-party libraries and your application.

No matter what type alias you use in your application, `UIO` can be useful for describing infallible effects, including those resulting from handling all errors.

Finally, if you are an experienced functional programmer, then direct use of the `ZIO` data type is recommended, although you may find it useful to create your own family of type aliases in different parts of your application.

## Next Steps

If you are comfortable with the ZIO data type, and its family of type aliases, the next step is learning how to [create effects](creating_effects.md).
