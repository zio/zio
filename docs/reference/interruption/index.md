---
id: index
title: "Introduction to ZIO's Interruption Model"
sidebar_label: "Interruption Model"
description: "Guide to ZIO's asynchronous interruption model: when fibers get interrupted, how interruptible and uninterruptible regions work, how finalizers and resource safety survive interruption, and how interruption is attributed in the resulting Cause."
keywords:
  - "Fiber Interruption"
  - "Asynchronous Interruption"
  - "Uninterruptible Regions"
  - "uninterruptibleMask"
  - "Finalizers"
  - "Resource Safety"
  - "Timeout"
  - "Race"
  - "Blocking Operations"
  - "Child Fibers"
  - "Cause"
---

While developing concurrent applications, there are several cases that we need to _interrupt_ the execution of other fibers, for example:

1. A parent [Fiber](../fiber/index.md) might start some child fibers to perform a task, and later the parent might decide that it doesn't need the result of some or all of the child fibers.
2. Two or more fibers start a race with each other. The fiber whose result is computed first wins and all other fibers are no longer needed so they should be interrupted.
3. In interactive applications, a user may want to stop some already running tasks, such as clicking on the "stop" button to prevent downloading more files.
4. Computations that run longer than expected should be aborted by using timeout operations.
5. When we have an application that perform compute-intensive tasks based on the user inputs, if the user changes the input we should cancel the current task and perform another one.

## Polling vs. Asynchronous Interruption

A simple and naive way to implement fiber interruption is to provide a mechanism for one fiber to _kill/terminate_ another fiber. This is not a correct solution because if the target fiber is in the middle of changing a shared state it leads to an inconsistent state. So this solution doesn't guarantee to leave the shared mutable state internally consistent.

Other than the very simple kill solution, there are two popular valid solutions to this problem:

  1. **Semi-asynchronous Interruption (Polling for Interruption)**— Imperative languages such as Java often use polling to implement a semi-asynchronous signaling mechanism. In this model, a fiber sends a request for interruption of other fiber. The target fiber keeps polling the interrupt status, and based on the interrupt status will find out that whether there is an interruption request from other fibers. If so, it should terminate itself as soon as possible.

  Using this solution, the fiber itself takes care of critical sections. So while a fiber is in the middle of a critical section, if it receives an interruption request it should ignore the interruption and postpone the delivery of interruption during the critical section.

  The drawback of this solution is that, if the programmer forgets to poll regularly enough, then the target fiber becomes unresponsive and causes deadlocks. Another problem is that polling a global flag is not a functional operation and doesn't fit with ZIO's paradigm.

  2. **Asynchronous Interruption**— In asynchronous interruption, a fiber is allowed to terminate another fiber. So the target fiber is not responsible for polling the status, instead in critical sections the target fiber disables the interruptibility of these regions. This is a purely-functional solution and doesn't require polling a global state. ZIO uses this solution for its interruption model. It is a fully asynchronous signalling mechanism.

  This mechanism doesn't have the drawback of forgetting to poll regularly and also it's fully compatible with the functional paradigm because in a purely-functional computation, we can abort the computation at any point, except for critical sections.

ZIO deliberately has no notion of _pausing_ a fiber. Stopping a fiber always means unwinding it: the fiber's finalizers run and the fiber dies. A "freeze and resume later" operation was considered and rejected, because a paused fiber holds resources whose finalizers might never run if nobody resumes it. Conflating "stop this fiber" with "run this fiber's finalizers and let it die" is what makes resource safety under interruption mechanical rather than a matter of discipline.

## The Interruption Model in One Page

The rest of this documentation builds on five invariants. If you only remember one section, remember this one.

- **Interruption is delivered asynchronously, at safe points.** A fiber does not poll. The runtime delivers a pending interrupt when the fiber reaches a suspension point, a region boundary, or a yield point.
- **Inside an uninterruptible region an interrupt is recorded and deferred, never discarded.** It fires the moment the region ends. If the region never ends, the interrupt never fires — see [Interruptible and Uninterruptible Regions](interruptible-regions.md).
- **Finalizers always run, and they run uninterruptibly.** `ZIO#ensuring`, `ZIO#onExit`, `ZIO#onInterrupt` and `ZIO.acquireReleaseWith` cannot themselves be interrupted away — see [Interruption, Finalizers, and Resource Safety](interruption-and-finalizers.md).
- **Interruption is not a typed error.** `ZIO#catchAll` never sees it; only `ZIO#catchAllCause`, `ZIO#sandbox` and the resulting `Exit` expose it.
- **`Fiber#interrupt` does not return until the target has finished running all of its finalizers.** That is what makes interruption safe, and it is also why an interrupt can appear to hang — see [Interrupting Fibers](triggering-interruption.md).

## When Does a Fiber Get Interrupted?

A fiber is interrupted either because some other fiber explicitly asked for it, or because ZIO's structured concurrency decided the fiber's work is no longer needed. Four situations account for every interruption in a ZIO application, and each of them is shown below.

### Calling `Fiber#interrupt` Explicitly

A fiber can be interrupted by calling [`Fiber#interrupt`](../fiber/fiber.md#fiber-interruption) on that fiber.

Let's try to make a fiber and then interrupt it:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def task = {
    for {
      fn <- ZIO.fiberId.map(_.threadName)
      _ <- ZIO.debug(s"$fn starts a long running task")
      _ <- ZIO.sleep(1.minute)
      _ <- ZIO.debug("done!")
    } yield ()
  }

  def run =
    for {
      f <-
        task.onInterrupt(
          ZIO.debug(s"Task interrupted while running")
        ).fork
      _ <- f.interrupt
    } yield ()
}
```

Here is the output of running this piece of code, which denotes that the task was interrupted:

```
Task interrupted while running
```

`Fiber#interrupt` returns the target fiber's `Exit` value, and it does not return until the target has finished running every one of its finalizers. When the target's cleanup is slow, the caller waits with it. [Interrupting Fibers](triggering-interruption.md) covers the fire-and-forget alternatives, `Fiber#interruptFork` and `ZIO#disconnect`.

### Interruption of Parallel Effects

When we compose effects in parallel, the combinator is responsible for all of the fibers it started. If either side fails or is interrupted, the other side is interrupted too, so that no fiber outlives the operation that forked it:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def task[R, E, A](name: String)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio.onInterrupt(ZIO.debug(s"the $name task was interrupted"))

  def run = {
    // A fiber that interrupts itself as soon as it starts
    val first = task("first")(ZIO.interrupt)

    // A fiber that never completes on its own
    val second = task("second")(ZIO.never)

    first <&> second
  }
}
```

Running this program prints two lines, in an order that depends on scheduling:

```
the first task was interrupted
the second task was interrupted
```

The `ZIO#zipPar`/`<&>` operator runs both tasks in two parallel fibers. The `first` task interrupts itself, `ZIO#zipPar` interrupts the loser, and `second` — which would otherwise run forever — is torn down. The combinator does not complete until the interrupted side has finished finalizing, which is why the loser's `ZIO#onInterrupt` handler is guaranteed to have run by the time the program exits.

The same rule applies to `ZIO.foreachPar`, `ZIO.collectAllPar`, `ZIO#race` and every other parallel operator. One consequence surprises almost everyone the first time: a healthy branch's `ZIO#onInterrupt` fires when a *sibling* branch dies, because the healthy branch really was interrupted. [Common Pitfalls](common-pitfalls.md#oninterrupt-can-fire-when-your-effect-was-not-the-one-interrupted) explains how to tell the two cases apart.

### Child Fibers Are Scoped to Their Parents

A fiber forked with `ZIO#fork` belongs to the scope of the fiber that forked it. If the parent finishes — normally or by being interrupted — its children are interrupted:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      fn <- ZIO.fiberId.map(_.threadName)
      _  <- ZIO.debug(s"$fn starts working.")
      child =
        for {
          cfn <- ZIO.fiberId.map(_.threadName)
          _   <- ZIO.debug(s"$cfn starts working by forking from its parent ($fn)")
          _   <- ZIO.never
        } yield ()
      _  <- child.onInterrupt(ZIO.debug("the child task was interrupted")).fork
      _  <- ZIO.sleep(1.second)
      _  <- ZIO.debug(s"$fn finishes its job and is going to exit.")
    } yield ()
}
```

Here is the result of one of the executions of this sample code:

```
zio-fiber-2 starts working.
zio-fiber-7 starts working by forking from its parent (zio-fiber-2)
zio-fiber-2 finishes its job and is going to exit.
the child task was interrupted
```

If the parent has *already* been interrupted at the moment it forks, the child is interrupted at birth and its body may never run at all. That is the usual explanation for "my forked fiber printed nothing".

`ZIO#forkDaemon`, `ZIO#forkScoped` and `ZIO#forkIn` attach the child to a different scope and therefore change this lifetime. The [Lifetime of Child Fibers](../fiber/fiber.md#lifetime-of-child-fibers) section documents each of them in depth. Note that changing a child's *scope* does not change its *interruptibility*: a fiber forked inside `ZIO.uninterruptible` is born uninterruptible even when it is a daemon.

### Timeouts and Races

`ZIO#timeout` is a race between the effect and a sleep, so it is interruption in disguise. When the sleep wins, the effect's fiber is interrupted and the result is `None` rather than a failure:

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      result <- ZIO.sleep(5.seconds).as("finished").timeout(1.second)
      _      <- ZIO.debug(s"result: $result")
    } yield ()
}
```

The program prints the timed-out result after one second:

```
result: None
```

Because the loser is interrupted and interruption waits for finalizers, `ZIO#timeout` returns only after the timed-out effect has finished cleaning up. An effect with a slow finalizer therefore overruns its own time bound. Use `ZIO#disconnect` to hand the cleanup to a separate fiber and get a prompt bound:

```scala mdoc:compile-only
import zio._

val promptlyBounded: ZIO[Any, Nothing, Option[Nothing]] =
  ZIO.never
    .ensuring(ZIO.sleep(10.seconds))
    .disconnect
    .timeout(1.second)
```

An effect that is *uninterruptible* cannot be timed out at all, and `ZIO#disconnect` does not change that. This is intended behaviour rather than a bug, and it is covered in [Common Pitfalls](common-pitfalls.md#timeouts-and-races-do-not-work-inside-uninterruptible-regions).

## Interruption Operators at a Glance

The operators below are the complete interruption-related surface of `ZIO` and `Fiber`. Follow the link in the last column for the section that explains each group.

| Operator                        | Signature (abbreviated)                                             | What It Does                                                                          | Details                                                                  |
| ------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| `Fiber#interrupt`               | `UIO[Exit[E, A]]`                                                    | Interrupts the fiber as the current fiber, waits for all of its finalizers, returns its `Exit`. | [Interrupting Fibers](triggering-interruption.md#fiberinterrupt)          |
| `Fiber#interruptAs`             | `FiberId => UIO[Exit[E, A]]`                                         | Same, but attributes the interruption to the given `FiberId`.                          | [Interrupting Fibers](triggering-interruption.md#choosing-an-interruption-operator) |
| `Fiber#interruptFork`           | `UIO[Unit]`                                                          | Sends the interrupt signal and returns immediately without waiting.                     | [Interrupting Fibers](triggering-interruption.md#choosing-an-interruption-operator) |
| `Fiber#interruptAsFork`         | `FiberId => UIO[Unit]`                                               | Fire-and-forget interruption attributed to the given `FiberId`.                         | [Interrupting Fibers](triggering-interruption.md#choosing-an-interruption-operator) |
| `ZIO.interrupt`                 | `UIO[Nothing]`                                                       | Interrupts the current fiber, attributed to the current fiber.                          | [Interrupting Fibers](triggering-interruption.md#self-interruption)       |
| `ZIO.interruptAs`               | `FiberId => UIO[Nothing]`                                            | Interrupts the current fiber, attributed to the given `FiberId`.                        | [Interrupting Fibers](triggering-interruption.md#self-interruption)       |
| `ZIO.allowInterrupt`            | `UIO[Unit]`                                                          | Checks for a pending interrupt and self-interrupts if there is one.                      | [Interrupting Fibers](triggering-interruption.md#self-interruption)       |
| `ZIO#uninterruptible`           | `ZIO[R, E, A]`                                                       | Runs the effect in a region where interruption is deferred.                             | [Regions](interruptible-regions.md#making-a-region-uninterruptible)       |
| `ZIO#interruptible`             | `ZIO[R, E, A]`                                                       | Runs the effect in an interruptible region, whatever the enclosing region is.            | [Regions](interruptible-regions.md#making-a-region-uninterruptible)       |
| `ZIO#interruptStatus`           | `InterruptStatus => ZIO[R, E, A]`                                    | Runs the effect with the given interruptibility, chosen at runtime.                     | [Regions](interruptible-regions.md#every-fiber-is-interruptible-by-default) |
| `ZIO.uninterruptibleMask`       | `(InterruptibilityRestorer => ZIO[R, E, A]) => ZIO[R, E, A]`         | Uninterruptible region plus a `restore` that returns to the *enclosing* status.          | [Regions](interruptible-regions.md#uninterruptiblemask-and-restore)       |
| `ZIO.interruptibleMask`         | `(InterruptibilityRestorer => ZIO[R, E, A]) => ZIO[R, E, A]`         | The mirror image: interruptible region plus a `restore` to the enclosing status.         | [Regions](interruptible-regions.md#uninterruptiblemask-and-restore)       |
| `ZIO.checkInterruptible`        | `(InterruptStatus => ZIO[R, E, A]) => ZIO[R, E, A]`                  | Reads the current region's interruptibility.                                            | [Regions](interruptible-regions.md#every-fiber-is-interruptible-by-default) |
| `ZIO#ensuring`                  | `URIO[R, Any] => ZIO[R, E, A]`                                       | Runs a finalizer on every exit, uninterruptibly.                                         | [Finalizers](interruption-and-finalizers.md#finalizers-always-run-and-run-uninterruptibly) |
| `ZIO#onExit`                    | `(Exit[E, A] => URIO[R, Any]) => ZIO[R, E, A]`                       | Like `ZIO#ensuring`, but the finalizer sees the `Exit`.                                  | [Finalizers](interruption-and-finalizers.md#finalizers-always-run-and-run-uninterruptibly) |
| `ZIO#onInterrupt`               | `URIO[R, Any] => ZIO[R, E, A]`                                       | Runs a finalizer only when the cause involves interruption.                              | [Finalizers](interruption-and-finalizers.md#oninterrupt)                  |
| `ZIO.acquireReleaseWith`        | `acquire => release => use`                                          | Acquire uninterruptibly, use interruptibly, release uninterruptibly.                     | [Finalizers](interruption-and-finalizers.md#acquirereleasewith-and-scope) |
| `ZIO#disconnect`                | `ZIO[R, E, A]`                                                       | Severs the supervision link so the caller need not wait for the effect's finalizers.     | [Interrupting Fibers](triggering-interruption.md#disconnecting-a-fiber)   |
| `ZIO#timeout`                   | `Duration => ZIO[R, E, Option[A]]`                                   | Races against a sleep; interrupts the effect and yields `None` on timeout.               | [Timeouts and Races](#timeouts-and-races)                                 |
| `ZIO.async`                     | `((ZIO[R, E, A] => Unit) => Unit) => ZIO[R, E, A]`                   | Parks the fiber on a callback. The fiber is interruptible; the operation is not cancelled. | [Async Effects](interruption-and-finalizers.md#interrupting-asynchronous-effects) |
| `ZIO.asyncInterrupt`            | `((ZIO[R, E, A] => Unit) => Either[URIO[R, Any], ZIO[R, E, A]]) => ZIO[R, E, A]` | Parks the fiber on a callback and registers a canceler in the `Left`.        | [Async Effects](interruption-and-finalizers.md#interrupting-asynchronous-effects) |
| `ZIO.never`                     | `UIO[Nothing]`                                                       | An async suspension that never completes; promptly interruptible.                        | [Async Effects](interruption-and-finalizers.md#interrupting-asynchronous-effects) |
| `ZIO.attemptBlockingInterrupt`  | `(=> A) => Task[A]`                                                  | Translates ZIO interruption into `Thread#interrupt`.                                     | [Blocking Operations](blocking-operations.md)                            |
| `ZIO.attemptBlockingCancelable` | `(=> A) => (=> URIO[R, Any]) => RIO[R, A]`                           | Translates ZIO interruption into a custom cancel action.                                 | [Blocking Operations](blocking-operations.md)                            |
| `Cause#isInterrupted`           | `Boolean`                                                            | Whether the cause contains interruption anywhere.                                        | [Who Interrupted This Fiber?](triggering-interruption.md#who-interrupted-this-fiber) |
| `Cause#isInterruptedOnly`       | `Boolean`                                                            | Whether interruption is the *only* thing in the cause.                                   | [Who Interrupted This Fiber?](triggering-interruption.md#who-interrupted-this-fiber) |
| `Cause#interruptors`            | `Set[FiberId]`                                                       | Every fiber that signalled an interrupt to this workflow.                                | [Who Interrupted This Fiber?](triggering-interruption.md#who-interrupted-this-fiber) |

## See Also

The rest of this section goes into depth on each part of the model:

- [Interruptible and Uninterruptible Regions](interruptible-regions.md) — `ZIO#uninterruptible`, `ZIO.uninterruptibleMask` and what `restore` really restores.
- [Interrupting Fibers](triggering-interruption.md) — `Fiber#interrupt` and its variants, self-interruption, reading the interruptor out of a `Cause`, and `ZIO#disconnect`.
- [Interruption, Finalizers, and Resource Safety](interruption-and-finalizers.md) — `ZIO#ensuring`, `ZIO#onInterrupt`, `ZIO.acquireReleaseWith`, and interrupting asynchronous effects.
- [Interrupting Blocking Operations](blocking-operations.md) — `ZIO.attemptBlockingInterrupt` and `ZIO.attemptBlockingCancelable`.
- [Common Pitfalls and Known Limitations](common-pitfalls.md) — the interactions that surprise people, and the one open defect worth knowing about.

Related pages elsewhere in the documentation:

- [Fiber](../fiber/fiber.md) — fiber lifetimes, forking strategies, and the `Fiber` API.
- [Resource Management](../resource/index.md#acquire-release) — `ZIO.acquireRelease` and `Scope` from the resource-safety side.
- [Typed Errors Guarantees](../error-management/typed-errors-guarantees.md) — why the typed error channel does not model interruption.
