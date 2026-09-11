---
id: merging-the-error-channel-into-the-success-channel
title: "Merging the Error Channel into the Success Channel"
description: "Use ZIO#merge to collapse the error channel into the success channel when the error and success types are compatible, producing an infallible effect."
keywords:
  - "Error Channel"
  - "Success Channel"
  - "Infallible Effect"
  - "Nothing"
  - "Merge"
---

`ZIO#merge` collapses the error channel into the success channel, producing an infallible `URIO` whose value is drawn from whichever channel fired. Its signature includes implicit evidence that constrains when the operation is legal:

```scala
trait ZIO[-R, +E, +A] {
  def merge[A1 >: A](implicit ev1: E IsSubtypeOfError A1, ev2: CanFail[E]): URIO[R, A1]
}
```

`merge` is only available when `E` is a subtype of `A1`, the common supertype of both channels. The implicit evidence `E IsSubtypeOfError A1` enforces this at compile time, so the compiler rejects a call to `merge` when the two types share no common supertype other than `Any`. At runtime, if the effect succeeds it returns the success value; if it fails, it returns the error value — both surfaced through the success channel. The result is always an infallible effect (`URIO[R, A1]`), meaning the typed error channel is eliminated entirely.

The most common scenario is when `E =:= A` — the error and success types are identical. The following example uses `ZIO.fail` to produce a value whose error type and success type are both `String`, then merges the channels into one:

```scala mdoc:compile-only
import zio._

val merged : ZIO[Any, Nothing, String] =
  ZIO.fail("Oh uh!") // ZIO[Any, String, Nothing]
    .merge           // ZIO[Any, Nothing, String]
```

`merge` also handles the case where `E` and `A` are different types that share a common supertype — the result type becomes that supertype. The following example uses an `AppStatus` sealed trait whose variants represent both success and failure outcomes, so `merge` produces a `URIO[Any, AppStatus]` regardless of which channel fires:

```scala mdoc:compile-only
import zio._

sealed trait AppStatus
case object Ok    extends AppStatus
case object Error extends AppStatus

// E = Error.type, A = Ok.type — both are subtypes of AppStatus
val status: URIO[Any, AppStatus] =
  ZIO.fail(Error: AppStatus).merge
```

## When to Use

`merge` is useful when the typed error and success represent the same domain type — for example, an enumeration whose variants include both success and failure states — and you want to eliminate the error channel without losing the value. For cases where `E` is a `Throwable`, prefer `ZIO#orDie` (which converts the error to a defect) over `merge`: `orDie` signals that the failure is unrecoverable and crashes the fiber, while `merge` would surface the exception as a plain value in the success channel, which is rarely the right semantic for a `Throwable`.
