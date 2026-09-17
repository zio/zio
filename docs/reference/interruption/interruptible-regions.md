---
id: interruptible-regions
title: "Interruptible and Uninterruptible Regions"
sidebar_label: "Interruptible Regions"
description: "How ZIO's interruptible and uninterruptible regions work: ZIO#uninterruptible, ZIO#interruptible, ZIO.uninterruptibleMask and what the restore function actually restores."
keywords:
  - "Uninterruptible"
  - "uninterruptibleMask"
  - "restore"
  - "InterruptStatus"
  - "checkInterruptible"
  - "Critical Section"
---

Interruption is asynchronous: another fiber can ask for our fiber to stop at any moment, and we never poll for that request. That is exactly what we want almost all of the time, and exactly what we do not want while we are halfway through updating shared state or acquiring a resource. ZIO's answer is the *region*: a lexically scoped block of an effect that declares whether interruption may be delivered inside it.

## Every Fiber Is Interruptible by Default

A fiber starts life interruptible, and every effect it runs is interruptible unless some enclosing region says otherwise. The current region's status is a value of type `InterruptStatus`, which has exactly two cases, `InterruptStatus.Interruptible` and `InterruptStatus.Uninterruptible`.

To read the status of the region we are currently in, use `ZIO.checkInterruptible`:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    ZIO.checkInterruptible(status => ZIO.debug(s"current status: $status"))
}
```

Since a fresh fiber is interruptible, this prints:

```
current status: Interruptible
```

When the status is not known until runtime — for example, when a caller passes it in — `ZIO#interruptStatus` takes an `InterruptStatus` value instead of hard-coding the choice:

```scala mdoc:compile-only
import zio._

def runWith[R, E, A](status: InterruptStatus)(effect: ZIO[R, E, A]): ZIO[R, E, A] =
  effect.interruptStatus(status)
```

`ZIO#uninterruptible` and `ZIO#interruptible` are the two fixed choices, and they are the operators to reach for in practice.

## Making a Region Uninterruptible

`ZIO#uninterruptible` marks a region in which interruption is not *delivered*. It does not make the fiber immune to interruption: a fiber interrupted while it is inside an uninterruptible region records the interrupt and keeps going, and the interrupt is delivered at the first moment the fiber becomes interruptible again. Interruption is deferred, never discarded.

The following program makes that visible. The `started` promise guarantees that the child fiber is already inside the uninterruptible region when the parent interrupts it:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      started <- Promise.make[Nothing, Unit]
      fiber <- ZIO
                 .uninterruptible {
                   started.succeed(()) *>
                     ZIO.debug("critical section: start") *>
                     ZIO.sleep(1.second) *>
                     ZIO.debug("critical section: end")
                 }
                 .flatMap(_ => ZIO.debug("this line never runs"))
                 .fork
      _    <- started.await
      exit <- fiber.interrupt
      _    <- ZIO.debug(s"interrupted: ${exit.isInterrupted}")
    } yield ()
}
```

The critical section runs to completion even though the interrupt arrived in the middle of it, and the interrupt takes effect the instant the region ends, so the `ZIO#flatMap` continuation is never reached:

```
critical section: start
critical section: end
interrupted: true
```

The flip side of "deferred, never discarded" is that a region which never ends defers the interrupt forever. An uninterruptible region wrapped around an effect that never completes produces a fiber that nothing can stop, and whose finalizers therefore never run:

```scala mdoc:compile-only
import zio._

// Interrupting a fiber running this effect never completes: the region has no
// end, so the recorded interrupt has no boundary at which to fire, and the
// finalizer installed by `ZIO#ensuring` never runs.
val unstoppable: UIO[Nothing] =
  ZIO.never.ensuring(ZIO.debug("cleanup")).uninterruptible
```

This is the single most important reason to keep uninterruptible regions small and bounded. An uninterruptible region should cover a critical section, not a whole workflow.

## `uninterruptibleMask` and `restore`

Wrapping a whole workflow in `ZIO#uninterruptible` is almost never what we want, because the workflow usually contains one part that must be atomic and another part that should stay cancellable. `ZIO.uninterruptibleMask` expresses exactly that: it makes the region uninterruptible and hands us a `restore` function we can wrap around the parts that should remain interruptible.

The rule that makes this compose — and the one that is most often misread — is that **`restore` returns the region to the interruptibility status of the enclosing region, not unconditionally to interruptible**. If `ZIO.uninterruptibleMask` was invoked from an interruptible region, `restore` makes its argument interruptible. If it was invoked from a region that was already uninterruptible, `restore` leaves its argument uninterruptible.

Running the same masked effect from both kinds of enclosing region shows the difference:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def report(label: String): UIO[Unit] =
    ZIO.checkInterruptible(status => ZIO.debug(s"$label: $status"))

  val masked: UIO[Unit] =
    ZIO.uninterruptibleMask { restore =>
      report("inside the mask") *> restore(report("inside restore"))
    }

  def run =
    masked *> ZIO.uninterruptible(masked)
}
```

The first run is made from the fiber's default interruptible region, the second from an enclosing uninterruptible one:

```
inside the mask: Uninterruptible
inside restore: Interruptible
inside the mask: Uninterruptible
inside restore: Uninterruptible
```

This is what makes masks nest safely. A combinator that builds on `ZIO.uninterruptibleMask` composes into any region and never widens the interruptibility of its caller's region, so a library function cannot accidentally make a caller's critical section cancellable. The restorer also exposes the enclosing status directly, through `InterruptibilityRestorer#isParentRegionInterruptible` and `InterruptibilityRestorer#parentInterruptStatus`, for the rare combinator that needs to branch on it.

`ZIO.interruptibleMask` is the mirror image: it makes the region interruptible and gives a `restore` that returns to the enclosing status in the same way.

:::note[Known limitations]
`ZIO.uninterruptibleMask` interacts with asynchronous effects in ways that have historically hidden defects. Before building a data structure on top of `ZIO.uninterruptibleMask` plus `ZIO.asyncInterrupt`, read [Known Limitations](common-pitfalls.md#known-limitations), which tracks the open defects in this area.
:::

## Choosing Between Them

Reach for the highest-level tool that solves the problem, and treat the region operators as the last resort:

- For "this resource must be released no matter what", use `ZIO.acquireReleaseWith` or a `Scope`, which already apply the right regions internally. See [Interruption, Finalizers, and Resource Safety](interruption-and-finalizers.md#acquirereleasewith-and-scope).
- For "run this cleanup on the way out", use `ZIO#ensuring`, `ZIO#onExit` or `ZIO#onInterrupt`. Finalizers registered this way already run uninterruptibly.
- For "this small block must not be torn down halfway", use `ZIO#uninterruptible` around that block only.
- For "this combinator must be atomic except for the part the caller supplied", use `ZIO.uninterruptibleMask` with `restore` around the caller's effect. This is the pattern `ZIO#onExit`, `ZIO#disconnect` and `ZIO.acquireReleaseWith` themselves are built from.

If we find ourselves writing `ZIO#uninterruptible` or `ZIO.uninterruptibleMask` in application code rather than in a combinator, it is usually a sign that a higher-level operator — `ZIO.acquireRelease*`, `ZIO#ensuring`, `ZIO#onInterrupt`, `ZIO#race`, `ZIO.foreachPar` — already expresses what we want, with the regions placed correctly.

## Interruptibility Is Inherited by Forked Fibers

A forked fiber inherits the interruptibility status of the region it was forked from, and this includes fibers forked with `ZIO#forkDaemon`. Forking inside `ZIO.uninterruptible` therefore produces a child that starts life uninterruptible, so calling `Fiber#interrupt` on it hangs. This is intended behaviour, and the fix is to make the forked effect interruptible explicitly, either with `ZIO#interruptible` or with `restore` from a surrounding `ZIO.uninterruptibleMask`. Both forms are documented, with runnable examples, in [fork and join](../fiber/fiber.md#fork-and-join).

:::warning[Do not manipulate the interruption runtime flags directly]
`RuntimeFlag.Interruption` and `RuntimeFlag.WindDown` are the runtime's internal representation of the current region, and they are the only runtime flags that are not propagated back to a parent fiber after a fork. Setting them through a `Runtime.enableFlags` layer produces results that depend on whether layers were composed sequentially or in parallel, and almost always breaks interruption somewhere else in the application. Use `ZIO#uninterruptible`, `ZIO#interruptible` and the mask operators instead.
:::
