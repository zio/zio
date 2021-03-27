---
id: task 
title: "Task"
---

`Task[A]` is a type alias for `ZIO[Any, Throwable, A]`, which represents an effect that has no requirements, and may fail with a `Throwable` value, or succeed with an `A`.

Some time, we know that our effect may fail, but we don't care the type of that exception, this is where we can use `Task`. The type signature of this type-alias is similar to the `Future[T]` and Cats `IO`.

If we want to be less precise and want to eliminate the need to think about requirements and error types, we can use `Task`. This type-alias is a good start point for anyone who wants to refactor the current code base which is written in Cats `IO` or Monix `Task`. 
