---
id: exceptional-and-unexceptional-effects
title: "Exceptional and Unexceptional Effects"
description: "Exceptional effects (Task, RIO) use Throwable; unexceptional effects (UIO, URIO) use Nothing, giving compile-time guarantees against failure."
keywords:
  - "Error Type Parameter"
  - "Exceptional Effects"
  - "Unexceptional Effects"
  - "Type Aliases"
  - "UIO"
  - "Task"
---

Besides the `IO` type alias, ZIO has four different type aliases which can be categorized into two different categories:

- **Exceptional Effect**— `Task` and `RIO` are two effects whose error parameter is fixed to `Throwable`, so we call them exceptional effects.
- **Unexceptional Effect**— `UIO` and `URIO` have error parameters that are fixed to `Nothing`, indicating that they are unexceptional effects. So they can't fail, and the compiler knows about it.

So when we compose different effects together, at any point of the codebase we can determine this piece of code can fail or cannot. As a result, typed errors offer a compile-time transition point between this can fail and this can't fail.

For example, the `ZIO.acquireReleaseWith` API asks us to provide three different inputs: _acquire_, _release_, and _use_. The `release` parameter requires a function from `A` to `URIO[R, Any]`. So, if we put an exceptional effect, it will not compile:

```scala
object ZIO {
  def acquireReleaseWith[R, E, A, B](
    acquire: => ZIO[R, E, A],
    release: A => URIO[R, Any],
    use: A => ZIO[R, E, B]
  ): ZIO[R, E, B]
}
```

## Why Unexceptional Effects Matter

`UIO` and `URIO` carry a compiler-enforced guarantee: the effect cannot fail. No hidden exception can escape through the typed error channel, and the compiler rejects any code that would introduce one. This guarantee is most valuable in three situations.

Cleanup actions and finalizers are the clearest case. The `release` arm of `ZIO.acquireReleaseWith` must be `URIO[R, Any]` precisely because a failing cleanup would otherwise silently swallow the original error. By forcing `release` to be unexceptional, ZIO ensures cleanup always runs to completion and never hides the real cause of a problem.

Logging and diagnostics are a second common use. A log action that can fail would contaminate every workflow it instruments with an unwanted error path; modeling it as `UIO` keeps the observability concern separate from the business logic.

Configuration reads that have already been validated are a third example: once a value is confirmed valid, the ongoing read is safely modeled as `UIO[Config]` — the possibility of failure has already been handled at the boundary.

The boundary between exceptional and unexceptional is an explicit, one-way conversion. When we call `ZIO.attempt` on code that may throw, the result is `Task[A]` — that is, `ZIO[Any, Throwable, A]`, an exceptional effect. After calling `ZIO#orDie` on that result, we obtain a `UIO[A]`: the compiler now knows the value is always produced, and the failure has been promoted to a defect, which crashes the fiber rather than propagating through the typed error channel.

The following example models a release arm as `URIO` to show how the type system enforces the cleanup guarantee:

```scala mdoc:compile-only
import zio._

def openConnection(): ZIO[Any, Throwable, String] =
  ZIO.succeed("connection")

// The release arm is URIO — the compiler guarantees cleanup cannot fail.
val program: ZIO[Any, Throwable, String] =
  ZIO.acquireReleaseWith(
    acquire = openConnection()
  )(
    release = conn => ZIO.attempt(s"closed $conn").orDie  // URIO[Any, Unit]
  )(
    use = conn => ZIO.succeed(s"used $conn")
  )
```

Because `release` is `URIO[Any, Unit]`, any caller of `program` can be certain that resource cleanup never introduces a new failure channel — the overall effect's error type remains `Throwable`, sourced only from `acquire` or `use`.

## Type Alias Quick Reference

The five main type aliases cover the most common combinations of environment and error type:

| Alias        | Full form                | Error type              | Typical use                            |
|--------------|--------------------------|-------------------------|----------------------------------------|
| `UIO[A]`     | `ZIO[Any, Nothing, A]`   | `Nothing` — cannot fail | Infallible computations, cleanup       |
| `URIO[R, A]` | `ZIO[R, Nothing, A]`     | `Nothing` — cannot fail | Infallible effects needing environment |
| `Task[A]`    | `ZIO[Any, Throwable, A]` | `Throwable`             | Wrapping JVM code that may throw       |
| `RIO[R, A]`  | `ZIO[R, Throwable, A]`   | `Throwable`             | JVM-code effects needing environment   |
| `IO[E, A]`   | `ZIO[Any, E, A]`         | Custom `` `E` ``        | Domain-typed errors, no environment    |
