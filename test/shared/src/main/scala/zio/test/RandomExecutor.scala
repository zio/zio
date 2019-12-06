package zio.test

import java.util.concurrent.atomic.AtomicReference

import zio.internal.{ ExecutionMetrics, Executor }
import zio.stream.ZStream
import zio.{ Exit, UIO, ZIO }

import scala.annotation.tailrec

object RandomExecutor {

  /**
   * Returns multiple possible outcomes of the given `zio`.
   */
  def paths[R, E, A](zio: ZIO[R, E, A]): ZStream[R, Nothing, Exit[E, A]] = {

    val yielding = yieldingEffects(zio)

    val singleRun = run(yielding)

    ZStream.repeatEffect(singleRun)
  }

  def run[R, E, A](zio: ZIO[R, E, A]): ZIO[R, Nothing, Exit[E, A]] =
    makeRandomExecutor.flatMap(yieldingEffects(zio).lock(_).run)

  /** An single threaded executor which resumes a random fiber after each yield  */
  private val makeRandomExecutor: UIO[Executor] =
    ZIO.effectTotal {
      new Executor {
        case class ExecutorState(pendingRunnables: Vector[Runnable], isRunning: Boolean)

        val state = new AtomicReference(ExecutorState(Vector.empty, isRunning = false))

        override def here: Boolean                     = true
        override def metrics: Option[ExecutionMetrics] = None
        override def yieldOpCount: Int                 = Int.MaxValue
        override def submit(runnable: Runnable): Boolean = {
          val isAlreadyRunning: Boolean =
            addToPendingAndReturnOldState(runnable).isRunning

          if (!isAlreadyRunning) {
            runRandomPath()
          }

          true
        }

        @tailrec
        def addToPendingAndReturnOldState(runnable: Runnable): ExecutorState = {
          val oldState = state.get()
          val newState = ExecutorState(runnable +: oldState.pendingRunnables, isRunning = true)

          if (state.compareAndSet(oldState, newState))
            oldState
          else
            addToPendingAndReturnOldState(runnable)
        }

        def runRandomPath(): Unit = {
          var curRunnable: Runnable = nextRunnableOrStopWithNull()

          while (curRunnable != null) {
            curRunnable.run()
            curRunnable = nextRunnableOrStopWithNull()
          }
        }

        @tailrec
        private def nextRunnableOrStopWithNull(): Runnable = {
          val oldState = state.get()

          val (nextRunnable, newState) =
            if (oldState.pendingRunnables.isEmpty) {
              (null, ExecutorState(Vector.empty, isRunning = false))
            } else {
              val randomIndex             = util.Random.nextInt(oldState.pendingRunnables.length)
              val (before, next +: after) = oldState.pendingRunnables.splitAt(randomIndex)

              (next, ExecutorState(before ++ after, isRunning = true))
            }

          if (state.compareAndSet(oldState, newState))
            nextRunnable
          else
            nextRunnableOrStopWithNull()
        }
      }
    }

  /** Inserts yields before every effect */
  private def yieldingEffects[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio match {
      // Introduce yields before every effect to be caught by the neverYieldingExecutor
      case effect: ZIO.EffectTotal[A] =>
        (ZIO.yieldNow *> effect): ZIO[Any, E, A]
      case effect: ZIO.EffectPartial[A] =>
        (ZIO.yieldNow *> effect): ZIO[Any, E, A]
      case effect: ZIO.EffectAsync[R, E, A] =>
        ZIO.yieldNow *> new ZIO.EffectAsync(
          register = reg => yieldingEffectsO(effect.register(yieldingEffectsFI(reg))),
          blockingOn = effect.blockingOn
        )
      case suspend: ZIO.EffectSuspendTotalWith[R, E, A] =>
        ZIO.yieldNow *> new ZIO.EffectSuspendTotalWith(yieldingEffectsF2(suspend.f))
      case suspend: ZIO.EffectSuspendPartialWith[R, A] =>
        ZIO.yieldNow *> new ZIO.EffectSuspendPartialWith(yieldingEffectsF2(suspend.f))

      // Drop other yields
      case ZIO.Yield => ZIO.unit

      // Recursively apply the rewrite of yielding effects
      case lock: ZIO.Lock[R, E, A] =>
        new ZIO.Lock(lock.executor, yieldingEffects(lock.zio))
      case value: ZIO.Succeed[A] => value
      case fork: ZIO.Fork[R, _, _] =>
        new ZIO.Fork(yieldingEffects(fork.value))
      case flatMap: ZIO.FlatMap[R, E, _, A] =>
        yieldingEffects(flatMap.zio).flatMap(yieldingEffectsF1(flatMap.k))
      case interrupt: ZIO.CheckInterrupt[R, E, A] =>
        new ZIO.CheckInterrupt[R, E, A](yieldingEffectsF1(interrupt.k))
      case value: ZIO.InterruptStatus[R, E, A] =>
        new ZIO.InterruptStatus[R, E, A](yieldingEffects(value.zio), value.flag)
      case fail: ZIO.Fail[E, A]       => fail
      case d: ZIO.Descriptor[R, E, A] => d
      case fold: ZIO.Fold[_, _, _, _, _] =>
        new ZIO.Fold(
          value = yieldingEffects(fold.value),
          failure = yieldingEffectsF1(fold.failure),
          success = yieldingEffectsF1(fold.success)
        )
      case provide: ZIO.Provide[_, E, A] =>
        yieldingEffects(provide.next).provide(provide.r)
      case read: ZIO.Read[R, E, A] =>
        ZIO.accessM(yieldingEffectsF1(read.k))
      case newFib: ZIO.FiberRefNew[_] =>
        newFib
      case modify: ZIO.FiberRefModify[_, A] =>
        modify
      case trace @ ZIO.Trace =>
        trace
      case status: ZIO.TracingStatus[R, E, A] =>
        new ZIO.TracingStatus(yieldingEffects(status.zio), status.flag)
      case check: ZIO.CheckTracing[R, E, A] =>
        new ZIO.CheckTracing(t => yieldingEffects(check.k(t)))
      case race: ZIO.RaceWith[R, _, _, E, _, _, A] =>
        new ZIO.RaceWith(
          left = yieldingEffects(race.left),
          right = yieldingEffects(race.right),
          leftWins = yieldingEffectsF2(race.leftWins),
          rightWins = yieldingEffectsF2(race.rightWins)
        )
      case daemon: ZIO.DaemonStatus[R, E, A] =>
        new ZIO.DaemonStatus[R, E, A](yieldingEffects(daemon.zio), daemon.flag)
      case daemon: ZIO.CheckDaemon[R, E, A] =>
        new ZIO.CheckDaemon[R, E, A](yieldingEffectsF1(daemon.k))
    }

  private def yieldingEffectsF1[P, R, E, A](f: P => ZIO[R, E, A]): P => ZIO[R, E, A] =
    p => yieldingEffects(f(p))

  private def yieldingEffectsF2[P1, P2, R, E, A](f: (P1, P2) => ZIO[R, E, A]): (P1, P2) => ZIO[R, E, A] =
    (p1: P1, p2: P2) => yieldingEffects(f(p1, p2))

  def yieldingEffectsO[R, E, A](optionalZio: Option[ZIO[R, E, A]]): Option[ZIO[R, E, A]] =
    optionalZio.map(yieldingEffects)

  def yieldingEffectsFI[R, E, A](callback: ZIO[R, E, A] => Unit): ZIO[R, E, A] => Unit = { argument =>
    callback(yieldingEffects(argument))
  }

}
