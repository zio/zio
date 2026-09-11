---
id: common-pitfalls
title: "Common Pitfalls and Known Limitations"
sidebar_label: "Common Pitfalls"
description: "Interruption behaviours in ZIO that surprise people: timeouts inside uninterruptible regions, onInterrupt firing for collateral interruption, instantaneous transitive interruption, plus the currently open interruption defects."
keywords:
  - "Interruption Pitfalls"
  - "timeout"
  - "raceFirst"
  - "onInterrupt"
  - "foreachPar"
  - "Known Limitations"
---

Each of the behaviours on this page is intended and supported: they follow from rules that are individually reasonable, and they surprise people only where two features meet. The last section is different in kind — it records a defect that is open at the time of writing, so that a reader who hits it does not spend a day suspecting their own code.

## Timeouts and Races Do Not Work Inside Uninterruptible Regions

`ZIO#timeout`, `ZIO#race` and `ZIO#raceFirst` all work the same way: run two workflows, take the first result, interrupt the other one, and do not complete until the interrupted one has finished executing. None of them changes the interruptibility of the workflows they run — racing a workflow is not a request to make it cancellable.

So when the race itself sits inside an uninterruptible region, the losing side inherits that region, the interrupt cannot be delivered to it, and the operator waits forever for a workflow that will never stop:

```scala mdoc:compile-only
import zio._

// Never completes: the sleep wins after one second and tries to interrupt
// `ZIO.never`, but `ZIO.never` inherited the enclosing uninterruptible region,
// so the interrupt is only recorded and `ZIO#timeout` waits for a workflow that
// never finishes.
val neverTimesOut: UIO[Option[Nothing]] =
  ZIO.uninterruptible(ZIO.never.timeout(1.second))
```

`ZIO#disconnect` does not rescue this. It changes fiber supervision — who has to wait for whose finalizers — not the interruptibility of the region the raced effects run in, so the losing side still never receives the signal.

The fix is to make the raced workflow interruptible on purpose, while keeping whatever else the region was protecting uninterruptible:

```scala mdoc:compile-only
import zio._

// Completes after one second: `restore` makes the raced workflow interruptible
// again, so the loser can actually be torn down.
val timesOutProperly: UIO[Option[Nothing]] =
  ZIO.uninterruptibleMask(restore => restore(ZIO.never).timeout(1.second))
```

`ZIO#interruptible` on the raced workflow achieves the same thing when we know the workflow should always be cancellable, regardless of where it is composed.

## `onInterrupt` Can Fire When Your Effect Was Not the One Interrupted

When one branch of `ZIO.foreachPar`, `ZIO.collectAllPar` or `ZIO#zipPar` dies, the healthy branches are interrupted as part of normal parallel-failure semantics. That interruption is real, so the healthy branch's `ZIO#onInterrupt` finalizer fires, and the combined `Cause` contains both the defect and the interruption.

The consequence is that `ZIO#onInterrupt` answers "was this effect's cause an interruption?", not "did somebody deliberately cancel this effect?":

```scala mdoc:compile-only
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      exit <- ZIO
                .foreachPar(List(1, 2)) {
                  case 1 => ZIO.die(new RuntimeException("boom"))
                  case _ => ZIO.never.onInterrupt(ZIO.debug("healthy branch: onInterrupt fired"))
                }
                .exit
      _ <- exit.foldExit(
             cause =>
               ZIO.debug(s"isInterrupted:     ${cause.isInterrupted}") *>
                 ZIO.debug(s"isInterruptedOnly: ${cause.isInterruptedOnly}") *>
                 ZIO.debug(s"defects:           ${cause.defects.map(_.getMessage)}"),
             _ => ZIO.unit
           )
    } yield ()
}
```

The healthy branch's finalizer runs, and the overall cause reports interruption even though the workflow actually failed with a defect:

```
healthy branch: onInterrupt fired
isInterrupted:     true
isInterruptedOnly: false
defects:           List(boom)
```

Two practical rules follow. First, distinguish the cases with `Cause#isInterruptedOnly` rather than `Cause#isInterrupted` when we need "this workflow was cancelled and nothing else went wrong". Second, do not use `Cause#squash` to report the failure of a parallel operator: `Cause#squash` reduces a whole `Cause` to a single `Throwable` and treats interruption as more important than a defect, so it turns the example above into a bare `InterruptedException` and throws away the real `RuntimeException`. Inspect the full `Cause` instead.

A finalizer installed in several branches of a parallel operator can therefore run concurrently on several fibers. Data structures that are not safe for concurrent access need their own protection inside such a finalizer.

## Interruption Propagates Instantly Down the Fiber Tree

In ZIO 2, transitive fiber interruption is instantaneous: interrupting a fiber signals every descendant immediately, without waiting for any ancestor's wind-down to finish. A grandchild can therefore be interrupted before the parent's own `ZIO#onInterrupt` handler has finished running. This is a deliberate change from ZIO 1, which unwound the tree in a more coordinated order, and it is a better default — cancellation is prompt, and nothing waits on a slow ancestor. It does mean that code migrated from ZIO 1 may carry an ordering assumption that no longer holds.

There is no operator that restores the old ordering. What we can control is the scope a child is forked into, and each forking operator gives a different guarantee:

- `ZIO#fork` issues interrupt signals to children before interrupting the parent, but gives no guarantee that the children are interrupted *before* the parent is.
- `ZIO#forkScoped` ties the child to the enclosing `Scope`, and a scope releases in reverse order of what was added to it.
- `ZIO#forkDaemon` detaches the child entirely, so it is not interrupted when the parent is.

When cleanup really must happen in a specific order, create the child scope explicitly and fork into it, rather than relying on the implicit ordering of `ZIO#fork`:

```scala mdoc:compile-only
import zio._

def orderedTeardown(work: UIO[Unit]): ZIO[Scope, Nothing, Unit] =
  for {
    childScope <- ZIO.scope.flatMap(_.fork)
    _          <- work.forkIn(childScope)
  } yield ()
```

Closing `childScope` interrupts the fiber and runs its finalizers, and the outer scope closes each of its children in reverse order of creation, so the teardown order is under our control rather than the runtime's.

## Known Limitations

This section records defects that are open at the time of writing. They are **not** documented behaviour: each one is expected to be fixed, and this entry should be deleted rather than rewritten when its issue closes.

:::warning[Open defect — a pure `Exit` canceler can leave a fiber uninterruptible (issue #11115, current as of ZIO 2.1.x)]
When a fiber parked in `ZIO.asyncInterrupt` is interrupted and its `Left` canceler is a **pure `Exit` value** such as `Left(Exit.unit)` rather than an effect, the runtime disables interruption in order to run the canceler and never re-enables it. The interrupt is delivered once — a surrounding `ZIO#catchAllCause` sees it — but interruption stays switched off afterwards, so the fiber can survive its own interrupt and can no longer be interrupted if it parks again.

This is reachable from ordinary code, not only from direct `ZIO.asyncInterrupt` use: `ZIO.fromFuture` over a not-yet-completed, non-cancelable `scala.concurrent.Future` registers exactly this kind of canceler.

Until it is fixed, prefer an effectful canceler (`Left(ZIO.succeed(...))`) whenever we write `ZIO.asyncInterrupt` ourselves, since the extra run-loop step it introduces avoids the leak. See [zio/zio#11115](https://github.com/zio/zio/issues/11115).
:::
