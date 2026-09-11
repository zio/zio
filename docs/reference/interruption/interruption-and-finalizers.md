---
id: interruption-and-finalizers
title: "Interruption, Finalizers, and Resource Safety"
sidebar_label: "Finalizers and Resource Safety"
description: "How ZIO guarantees resource safety under interruption: ensuring, onExit and onInterrupt finalizers, the acquireReleaseWith contract, why interruption is not a typed error, and interrupting asynchronous effects."
keywords:
  - "ensuring"
  - "onExit"
  - "onInterrupt"
  - "acquireReleaseWith"
  - "Scope"
  - "Resource Safety"
  - "catchAllCause"
  - "asyncInterrupt"
---

Interruption is only safe because of what happens on the way out. A fiber that is interrupted does not vanish: it unwinds, running every finalizer that was installed along the way, and it runs them in a region where interruption cannot be delivered. This page covers the operators that install those finalizers, the contract `ZIO.acquireReleaseWith` builds from them, and the two places where interruption interacts with the error channel and with callback-based APIs.

## Finalizers Always Run, and Run Uninterruptibly

`ZIO#ensuring` attaches a finalizer that runs on every exit — success, typed failure, defect, or interruption. `ZIO#onExit` is the same operator, except that it hands the `Exit` value to the finalizer, so the cleanup can branch on how the effect ends.

Both are built on `ZIO.uninterruptibleMask`: the effect itself is `restore`d so that it stays as interruptible as its caller made it, while the finalizer runs in the surrounding uninterruptible region. A finalizer therefore cannot be interrupted away, which is what allows ZIO to promise that cleanup happens even when the interrupt arrives in the middle of the work:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      started <- Promise.make[Nothing, Unit]
      fiber <- (started.succeed(()) *> ZIO.never)
                 .onExit(exit => ZIO.debug(s"finalizer sees: $exit"))
                 .fork
      _ <- started.await
      _ <- fiber.interrupt
    } yield ()
}
```

The finalizer receives the interrupted `Exit` and runs to completion before `Fiber#interrupt` returns:

```
finalizer sees: Failure(Interrupt(Runtime(2,...),Stack trace for thread "zio-fiber-2":...))
```

A finalizer that itself takes a long time therefore delays the fiber's death, and with it every caller waiting on `Fiber#interrupt`. Keep finalizers short, and use `ZIO#disconnect` when a slow cleanup must not hold up a race or a timeout.

## `onInterrupt`

`ZIO#onInterrupt` installs a finalizer that runs only when the effect ends because of interruption. It builds on `ZIO#onExit`, and its condition is `Cause#isInterruptedOnly` — the finalizer fires when the cause consists of interruption and nothing else.

Comparing it with `ZIO#ensuring` on both paths makes the difference concrete:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def instrument[A](label: String)(effect: UIO[A]): UIO[A] =
    effect
      .onInterrupt(ZIO.debug(s"$label: onInterrupt"))
      .ensuring(ZIO.debug(s"$label: ensuring"))

  def run =
    for {
      _       <- instrument("completed")(ZIO.unit)
      started <- Promise.make[Nothing, Unit]
      fiber   <- instrument("interrupted")(started.succeed(()) *> ZIO.never).fork
      _       <- started.await
      _       <- fiber.interrupt
    } yield ()
}
```

The `ZIO#ensuring` finalizer fires on both paths; the `ZIO#onInterrupt` finalizer fires only on the interrupted one:

```
completed: ensuring
interrupted: onInterrupt
interrupted: ensuring
```

`ZIO#onInterrupt` also has an overload whose finalizer receives the `Set[FiberId]` of the fibers that requested the interruption, which is useful for logging who tore the workflow down.

:::warning[`onInterrupt` is about the cause, not about the interruptor]
`ZIO#onInterrupt` fires whenever the effect's cause is interruption, including when the effect was interrupted as collateral damage from a sibling failing in a parallel operator. It does not mean "somebody deliberately cancelled *this* effect". See [Common Pitfalls](common-pitfalls.md#oninterrupt-can-fire-when-your-effect-was-not-the-one-interrupted).
:::

## `acquireReleaseWith` and `Scope`

`ZIO.acquireReleaseWith` is the operator that makes resource acquisition safe under interruption, and it does so by placing three regions for us:

1. **`acquire` runs uninterruptibly.** An interrupt cannot land between "the resource was opened" and "the release finalizer was registered", which is the window in which a resource would leak.
2. **`use` runs interruptibly** — more precisely, it is `restore`d to whatever interruptibility the caller had. This is the part we want to be able to cancel.
3. **`release` runs uninterruptibly.** It runs whether `use` succeeded, failed, died, or was interrupted, and it cannot itself be interrupted away.

Interrupting a fiber in the middle of `use` therefore still releases the resource:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      started <- Promise.make[Nothing, Unit]
      fiber <- ZIO
                 .acquireReleaseWith(ZIO.debug("acquire").as("resource"))(_ => ZIO.debug("release")) { _ =>
                   started.succeed(()) *> ZIO.debug("use") *> ZIO.never
                 }
                 .fork
      _ <- started.await
      _ <- fiber.interrupt
      _ <- ZIO.debug("interrupt returned")
    } yield ()
}
```

The release action runs before the interrupt is reported to the caller:

```
acquire
use
release
interrupt returned
```

`ZIO.acquireRelease` and `Scope` give the same three-part contract with the lifetime detached from a single `use` block, and finalizers added to a `Scope` are released in reverse order when the scope closes. The [Resource Management](../resource/index.md#acquire-release) page documents those APIs from the resource-safety side; what matters here is that every one of them is already correct with respect to interruption, so hand-written `ZIO.uninterruptibleMask` code is rarely needed in application code.

## Interruption Is Not a Typed Error

The error channel `E` of `ZIO[R, E, A]` models expected failures. Interruption is not one of them, so `ZIO#catchAll` and `ZIO#catchSome` never see it — they operate on `E`, and an interrupted effect does not fail with an `E` at all. Only the operators that expose the full `Cause` can observe interruption:

```scala mdoc:compile-only
import zio._

val request: IO[String, Int] = ZIO.fail("boom")

// `ZIO#catchAll` only ever sees the typed error channel.
val recovered: UIO[Int] =
  request.catchAll(message => ZIO.debug(s"recovered from $message").as(0))

// `ZIO#catchAllCause` sees the full `Cause`, including interruption.
val inspected: UIO[Int] =
  request.catchAllCause(cause => ZIO.debug(s"interrupted: ${cause.isInterrupted}").as(0))
```

Observing interruption is not the same as recovering from it. A fiber that another fiber interrupts stays interrupted: the runtime re-asserts the interruption at the next interruptible point, so a recovery effect installed with `ZIO#catchAllCause` does not get to run to completion:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      started   <- Promise.make[Nothing, Unit]
      recovered <- Ref.make(false)
      fiber <- (started.succeed(()) *> ZIO.never)
                 .catchAllCause(_ => recovered.set(true))
                 .fork
      _    <- started.await
      _    <- fiber.interrupt
      seen <- recovered.get
      _    <- ZIO.debug(s"handler completed: $seen")
    } yield ()
}
```

The handler is entered, but the fiber is interrupted again before the handler's effect takes hold:

```
handler completed: false
```

The practical rule follows from this: use `ZIO#catchAllCause` and `ZIO#sandbox` to *inspect* and *log* interruption, use finalizers to *act* on it, and read `Cause#isInterrupted` off an `Exit` — from `ZIO#exit` or from `Fiber#await` — when a caller needs to distinguish "cancelled" from "failed". The [Typed Errors Guarantees](../error-management/typed-errors-guarantees.md) page covers the same boundary from the error-management side.

## Interrupting Asynchronous Effects

A fiber that is parked on a callback-based API is suspended, which makes it a natural place to deliver an interrupt. What the runtime can do about the *underlying* operation depends on the constructor we choose. `ZIO.async`, `ZIO.asyncMaybe`, `ZIO.asyncZIO` and `ZIO.asyncInterrupt` all appear with their signatures on the [ZIO data type](../core/zio/zio.md#asynchronous) page; the difference that matters for interruption is whether the registration function can hand the runtime a canceler.

With `ZIO.async`, it cannot. Interrupting a fiber parked in `ZIO.async` unparks the fiber and unwinds it promptly, but nothing tells the third-party API to stop, so its callback may still fire later into a fiber that no longer exists. `ZIO.never` is exactly this: an async suspension with no canceler, which is why a fiber blocked on `ZIO.never` is interrupted immediately and cleanly.

`ZIO.asyncInterrupt` closes that gap. Its registration function returns an `Either`: a `Right` carries a result that was available synchronously, while a `Left` carries a canceler effect that the runtime runs when the fiber is interrupted while parked. Wrapping a subscription-style API therefore looks like this:

```scala mdoc:silent
import zio._

trait Subscription {
  def cancel(): Unit
}

trait Feed {
  def subscribe(onEvent: String => Unit): Subscription
}
```

The canceler in the `Left` unsubscribes, so an interrupted fiber leaves no dangling registration behind:

```scala mdoc:compile-only
import zio._

def nextEvent(feed: Feed): UIO[String] =
  ZIO.asyncInterrupt[Any, Nothing, String] { callback =>
    val subscription = feed.subscribe(event => callback(ZIO.succeed(event)))
    Left(ZIO.succeed(subscription.cancel()))
  }
```

Prefer an effect for the canceler, as above, over a pure `Exit` value such as `Left(Exit.unit)`. The two are not equivalent to the runtime, and the pure form is currently affected by an open defect — see [Known Limitations](common-pitfalls.md#known-limitations).
