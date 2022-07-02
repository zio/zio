---
id: overview_index
title:  "Summary"
---

ZIO is a next-generation framework for building cloud-native applications on the JVM. With a beginner-friendly yet powerful functional core, ZIO lets developers quickly build best-practice applications that are highly scalable, testable, robust, resilient, resource-safe, efficient, and observable.

At the heart of ZIO is a powerful data type called `ZIO`, which is the fundamental building block for every ZIO application.

## ZIO 

The `ZIO` data type is called a _functional effect_, and represents a unit of computation inside a ZIO application. Similar to a blueprint or a workflow, functional effects are precise plans that _describe_ a computation or interaction. When executed by the ZIO runtime system, a functional effect will either fail with some type of error, or succeed with some type of value.

Like the `List` data type, the `ZIO` data type is a _generic_ data type, and uses type parameters for improved type-safety. The `List` data type has a single type parameter, which represents the type of element that is stored in the `List`. The `ZIO` data type has three type parameters: `ZIO[R, E, A]`.

The type parameters of the `ZIO` data type have the following meanings:

 - **`R` - Environment Type**. The environment type parameter represents the type of contextual data that is required by the effect before it can be executed. For example, some effects may require a connection to a database, while others might require an HTTP request, and still others might require a user session. If the environment type parameter is `Any`, then the effect has no requirements, meaning the effect can be executed without first providing it any specific context.
 - **`E` - Failure Type**. The failure type parameter represents the type of error that the effect can fail with when it is executed. Although `Exception` or `Throwable` are common failure types in ZIO applications, ZIO imposes no requirement on the error type, and it is sometimes useful to define custom business or domain error types for different parts of an application. If the error type parameter is `Nothing`, it means the effect cannot fail.
 - **`A` - Success Type**. The success type parameter represents the type of success that the effect can succeed with when it is executed. If the success type parameter is `Unit`, it means the effect produces no useful information (similar to a `void`-returning method), while if it is `Nothing`, it means the effect runs forever, unless it fails.

 As several examples of how to interpret the types of ZIO effects:

  - An effect of type `ZIO[Any, IOException, Byte]` has no requirements, and when executed, such an effect may fail with a value of type `IOException`, or may succeed with a value of type `Byte`.
  - An effect of type `ZIO[Connection, SQLException, ResultSet]` requires a `Connection`, and when executed, such an effect may fail with a value of type `SQLException`, or may succeed with a value of type `ResultSet`.
  - An effect of type `ZIO[HttpRequest, HttpFailure, HttpSuccess]` requires an `HttpRequest`, and when executed, such an effect may fail with a value of type `HttpFailure`, or may succeed with a value of type `HttpSuccess`.

The environment type parameter is a _composite type parameter_, because sometimes, a single effect can require _multiple_ values of _different_ types. If you see that an effect has a type of `ZIO[UserSession with HttpRequest, E, A]` (Scala 2.x) or `ZIO[UserSession & HttpRequest, E, A]` (Scala 3.x), it means that the effect requires multiple contextual values before it can be executed.

Although this analogy is not precise, a ZIO effect can be thought of as a function:

```scala
ZEnvironment[R] => Either[Cause[E], A]
```

This function, which requires a `ZEnvironment[R]` (which is a ZIO environment that contains context values of different types), might produce either a `Cause[E]`, which represents a failure of type `E` (augmented with additional metadata, such as stack trace and secondary failures), or an `A`, representing success. 

ZIO effects are not actually functions, of course, because they model complex computations and interactions, which may be asynchronous, concurrent, or resourceful.

## Type Aliases

The `ZIO` data type is the only effect type in ZIO. However, there are a family of type aliases that reduce the need to type:

 - `UIO[A]` — A type alias for `ZIO[Any, Nothing, A]`, representing an effect that has no requirements, cannot fail, and can succeed with an `A`.
 - `URIO[R, A]` — A type alias for `ZIO[R, Nothing, A]`, representing an effect that requires an `R`, cannot fail, and can succeed with an `A`.
 - `Task[A]` — A type alias for `ZIO[Any, Throwable, A]`, representing an effect that has no requirements, may fail with a `Throwable` value, or succeed with an `A`.
 - `RIO[R, A]` — A type alias for `ZIO[R, Throwable, A]`, representing an effect that requires an `R`, may fail with a `Throwable` value, or succeed with an `A`.
 - `IO[E, A]` — A type alias for `ZIO[Any, E, A]`, representing an effect that has no requirements, may fail with an `E`, or succeed with an `A`.


**Tips For Getting Started With Type Aliases**

If you are new to functional effects, we recommend starting with the `Task` type, which has a single type parameter and corresponds most closely to the `Future` data types built into the Scala and Java standard libraries.

If you are using _Cats Effect_ libraries, you may find the `RIO` type useful, since it allows you to thread cobntext through third-party libraries.

No matter what type alias you use in your application, `UIO` can be useful for describing infallible effects, including those resulting from handling all errors.

Finally, if you are an experienced functional programmer, then direct use of the `ZIO` data type is recommended, although you may find it useful to create your own family of type aliases in different parts of your application.

## Next Steps

If you are comfortable with the `ZIO` data type, and its family of type aliases, the next step is learning how to [create effects](creating_effects.md).
