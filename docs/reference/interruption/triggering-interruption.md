---
id: triggering-interruption
title: "Interrupting Fibers"
sidebar_label: "Interrupting Fibers"
description: "How to interrupt a fiber in ZIO: Fiber#interrupt and its fire-and-forget variants, self-interruption, reading the interruptor's FiberId out of a Cause, and ZIO#disconnect."
keywords:
  - "Fiber#interrupt"
  - "interruptFork"
  - "interruptAs"
  - "Self-Interruption"
  - "FiberId"
  - "Cause.Interrupt"
  - "disconnect"
---

Most interruption in a ZIO application happens automatically, as a consequence of structured concurrency. This page is about the cases where we ask for it ourselves: which operator to call, what each one waits for, and how to find out afterwards who interrupted whom.

## `Fiber#interrupt`

`Fiber#interrupt` interrupts a fiber on behalf of the calling fiber and returns the target's `Exit` value. It does not return as soon as the signal has been sent. It returns only after the target fiber has finished running every one of its finalizers.

That guarantee is the whole point — when `Fiber#interrupt` returns, the target's resources are released and its cleanup is done — but it also means a slow finalizer slows down the caller:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val slowToClose: UIO[Nothing] =
    ZIO.never.ensuring(
      ZIO.debug("closing the resource...") *> ZIO.sleep(3.seconds) *> ZIO.debug("closed")
    )

  def run =
    for {
      fiber <- slowToClose.fork
      _     <- ZIO.sleep(1.second)
      _     <- ZIO.debug("interrupting")
      _     <- fiber.interrupt
      _     <- ZIO.debug("interrupt returned")
    } yield ()
}
```

Four seconds pass in total: one before the interrupt, then three more while the caller waits for the finalizer:

```
interrupting
closing the resource...
closed
interrupt returned
```

## Choosing an Interruption Operator

Four operators send an interrupt signal, and they differ along two axes: whether the caller waits for the target's finalizers, and which `FiberId` the interruption is attributed to.

| Operator                            | Returns                | Waits for the Target's Finalizers? | Attributed To         | Use It When                                                             |
| ----------------------------------- | ---------------------- | ---------------------------------- | --------------------- | ----------------------------------------------------------------------- |
| `Fiber#interrupt`                   | `UIO[Exit[E, A]]`      | Yes                                | The calling fiber      | We need the target's result, or need its cleanup finished before moving on. |
| `Fiber#interruptAs(fiberId)`        | `UIO[Exit[E, A]]`      | Yes                                | The given `FiberId`    | A combinator interrupts on behalf of another fiber and wants the `Cause` to say so. |
| `Fiber#interruptFork`               | `UIO[Unit]`            | No                                 | The calling fiber      | We want the fiber gone but must not block on its cleanup.                |
| `Fiber#interruptAsFork(fiberId)`    | `UIO[Unit]`            | No                                 | The given `FiberId`    | Fire-and-forget interruption attributed to another fiber.               |

The fire-and-forget variants do not weaken any guarantee about the target: its finalizers still run, and they still run uninterruptibly. The only thing that changes is that the caller stops waiting for them. Rewriting the previous example with `Fiber#interruptFork` lets the parent continue immediately:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val slowToClose: UIO[Nothing] =
    ZIO.never.ensuring(
      ZIO.debug("closing the resource...") *> ZIO.sleep(3.seconds) *> ZIO.debug("closed")
    )

  def run =
    for {
      fiber <- slowToClose.fork
      _     <- ZIO.sleep(1.second)
      _     <- fiber.interruptFork
      _     <- ZIO.debug("carrying on without waiting")
      _     <- ZIO.sleep(5.seconds)
    } yield ()
}
```

Now the parent prints its message before the target's finalizer has even started:

```
carrying on without waiting
closing the resource...
closed
```

## Self-Interruption

A fiber can interrupt itself. `ZIO.interrupt` fails the current fiber with an interruption cause attributed to the current fiber, and `ZIO.interruptAs` does the same but attributes it to a `FiberId` we supply. Self-interruption behaves like any other interruption, running finalizers and reporting interruption in the fiber's `Exit`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      exit <- ZIO.interrupt.onInterrupt(ZIO.debug("cleanup ran")).exit
      _    <- ZIO.debug(s"interrupted: ${exit.isInterrupted}")
    } yield ()
}
```

Both the finalizer and the exit inspection run, because `ZIO#exit` converts the interruption into a value rather than letting it propagate:

```
cleanup ran
interrupted: true
```

`ZIO.allowInterrupt` is the cooperative version of the same idea. It checks whether anybody has signalled an interrupt to this fiber and, if so, self-interrupts; otherwise it does nothing. It is useful in the middle of a long uninterruptible stretch, at a point where stopping is safe.

## Who Interrupted This Fiber?

An interrupted fiber does not simply fail with "interrupted". It fails with a `Cause` containing `Cause.Interrupt(fiberId, trace)`, where `fiberId` identifies the fiber that *asked* for the interruption. `Cause#interruptors` collects those ids, and `Cause#isInterrupted` and `Cause#isInterruptedOnly` answer whether interruption was involved at all and whether it was the only thing that went wrong.

Reading the interruptor out of an interrupted fiber's `Exit` confirms who is responsible:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      selfId  <- ZIO.fiberId
      started <- Promise.make[Nothing, Unit]
      fiber   <- (started.succeed(()) *> ZIO.never).fork
      _       <- started.await
      exit    <- fiber.interrupt
      _ <- exit.foldExit(
             cause =>
               ZIO.debug(s"interruptors: ${cause.interruptors}") *>
                 ZIO.debug(s"that includes us: ${cause.interruptors.contains(selfId)}"),
             _ => ZIO.debug("the fiber completed normally")
           )
    } yield ()
}
```

The interruptor is whichever fiber invokes `Fiber#interrupt`, which here is the main fiber:

```
interruptors: Set(Runtime(2,...))
that includes us: true
```

More than one fiber can interrupt the same workflow, and ZIO never drops an interruptor: every fiber that signalled an interrupt appears in the final `Cause`, because new interruption causes are accumulated onto the existing ones rather than replacing them. Structured operators also combine the ids of several participating fibers into a `FiberId.Composite`, which is why a single `Cause.Interrupt` can name more than one fiber. Both cases show up in `Cause#interruptors` as a set:

```scala mdoc:compile-only
import zio._

val alice: FiberId = FiberId(1, 0, Trace.empty)
val bob: FiberId   = FiberId(2, 0, Trace.empty)

val cause: Cause[Nothing]  = Cause.interrupt(alice) ++ Cause.interrupt(bob)
val interruptors: Set[FiberId] = cause.interruptors
```

When reading a fiber dump or a failure log, this is the field that answers "who killed this fiber". A `Cause` that contains both a defect and an interruption almost always means the interruption was collateral damage from a parallel operator, which is covered in [Common Pitfalls](common-pitfalls.md#oninterrupt-can-fire-when-your-effect-was-not-the-one-interrupted).

## Interrupting a Fiber That Has Already Finished

Interrupting a fiber that has already completed is a no-op, not an error. The fiber already holds its `Exit` value, so `Fiber#interrupt` returns that value immediately and does not turn a success into an interruption:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      fiber <- ZIO.succeed(42).fork
      _     <- fiber.await
      exit  <- fiber.interrupt
      _     <- ZIO.debug(s"exit: $exit")
    } yield ()
}
```

The already-finished fiber reports its original success:

```
exit: Success(42)
```

The same holds for a fiber that is currently winding down. Once a fiber has begun running its finalizers, it cannot be interrupted again, so a second `Fiber#interrupt` does not shorten or abort the cleanup — it simply waits for it like the first one.

## Disconnecting a Fiber

`ZIO#disconnect` runs an effect in its own daemon fiber and waits for that fiber's result, forwarding interruption to it in the background rather than waiting for it. The effect's finalizers still run in full; what changes is that whoever interrupts the *caller* no longer has to wait for them.

That makes it the right tool when a slow finalizer must not stall a race or a timeout:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  val slowToClose: UIO[Nothing] =
    ZIO.never.ensuring(ZIO.debug("closing...") *> ZIO.sleep(5.seconds) *> ZIO.debug("closed"))

  def run =
    for {
      result <- slowToClose.disconnect.timeout(1.second)
      _      <- ZIO.debug(s"timed out promptly: ${result.isEmpty}")
      _      <- ZIO.sleep(6.seconds)
    } yield ()
}
```

The timeout is honoured after one second, while the finalizer keeps running on its own fiber:

```
timed out promptly: true
closing...
closed
```

What `ZIO#disconnect` does *not* do is change the interruptibility of the effect it wraps. If the effect is uninterruptible, disconnecting it does not make the interrupt land any sooner, because the interrupt still cannot be delivered. See [Timeouts and Races Do Not Work Inside Uninterruptible Regions](common-pitfalls.md#timeouts-and-races-do-not-work-inside-uninterruptible-regions).
