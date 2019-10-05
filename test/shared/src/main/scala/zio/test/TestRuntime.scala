package zio.test

import zio.Runtime
import zio.Exit
import zio.ZIO
import zio.internal.Executor
import zio.random.Random
import zio.stream.ZStream

object TestRuntime {

  /**
   * Returns multiple possible outcomes of the given `zio`.
   */
  def paths[R <: Random, E, A](zio: ZIO[R, E, A]): ZStream[R, Nothing, Option[Exit[E, A]]] = {

    val yielding = yieldingEffects(zio)

    val singleRun = runOnce(yielding)

    ZStream.repeatEffect(singleRun)
  }

  private def runOnce[R <: Random, E, A](zio: ZIO[R, E, A]) =
    for {
      env              <- ZIO.environment[R]
      platform         <- ZIO.effectSuspendTotalWith(ZIO.succeed)
      pendingRunnables = collection.mutable.Buffer.empty[Runnable]
      testRuntime <- ZIO.effectTotal {
                      val neverYieldingExecutor = new Executor {
                        def here         = true
                        def metrics      = None
                        def yieldOpCount = Int.MaxValue
                        def submit(runnable: Runnable): Boolean = {
                          pendingRunnables += runnable
                          true
                        }
                      }

                      Runtime(env, platform.withExecutor(neverYieldingExecutor))
                    }
      result <- ZIO.effectTotal {
                 var result: Option[Exit[E, A]] = Option.empty

                 testRuntime.unsafeRunAsync(zio)(r => result = Option(r))

                 while (pendingRunnables.nonEmpty && result.isEmpty) {
                   val randomIndex = util.Random.nextInt(pendingRunnables.length)

                   pendingRunnables.remove(randomIndex).run()
                 }

                 result
               }
    } yield result

  private def yieldingEffects[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    zio match {
      // Introduce yields before every effect to be catched by the neverYieldingExecutor
      case effect: ZIO.EffectTotal[A]       => (ZIO.yieldNow *> effect): ZIO[Any, E, A]
      case effect: ZIO.EffectPartial[A]     => (ZIO.yieldNow *> effect): ZIO[Any, E, A]
      case effect: ZIO.EffectAsync[R, E, A] => ZIO.yieldNow *> effect

      // Don't allow to change executor
      case lock: ZIO.Lock[R, E, A] => yieldingEffects(lock.zio)
      // Drop other yields
      case ZIO.Yield => ZIO.unit
      // Recursively apply the rewrite
      case value: ZIO.Succeed[A] => value
      case fork: ZIO.Fork[R, _, _] =>
        new ZIO.Fork(yieldingEffects(fork.value))
      case value: ZIO.FlatMap[R, E, _, A] =>
        yieldingEffects(value.zio).flatMap(x => yieldingEffects(value.k(x)))
      case value: ZIO.CheckInterrupt[R, E, A] =>
        new ZIO.CheckInterrupt[R, E, A](value.k.andThen(yieldingEffects))
      case value: ZIO.InterruptStatus[R, E, A] =>
        new ZIO.InterruptStatus[R, E, A](yieldingEffects(value.zio), value.flag)
      case status: ZIO.SuperviseStatus[R, E, A] =>
        new ZIO.SuperviseStatus(yieldingEffects(status.value), status.status)
      case fail: ZIO.Fail[E, A]       => fail
      case d: ZIO.Descriptor[R, E, A] => d
      case fold: ZIO.Fold[_, _, _, _, _] =>
        new ZIO.Fold(
          yieldingEffects(fold.value),
          fold.failure.andThen(yieldingEffects),
          fold.success.andThen(yieldingEffects)
        )
      case provide: ZIO.Provide[_, E, A] =>
        yieldingEffects(provide.next).provide(provide.r)
      case read: ZIO.Read[R, E, A] =>
        ZIO.accessM(read.k.andThen(yieldingEffects))
      case suspend: ZIO.EffectSuspendTotalWith[R, E, A] =>
        new ZIO.EffectSuspendTotalWith(p => yieldingEffects(suspend.f(p)))
      case suspend: ZIO.EffectSuspendPartialWith[R, A] =>
        new ZIO.EffectSuspendPartialWith(p => yieldingEffects(suspend.f(p)))
      case newFib: ZIO.FiberRefNew[_] =>
        newFib
      case modify: ZIO.FiberRefModify[_, A] =>
        modify
      case ZIO.Trace =>
        ZIO.Trace
      case status: ZIO.TracingStatus[R, E, A] =>
        new ZIO.TracingStatus(yieldingEffects(status.zio), status.flag)
      case check: ZIO.CheckTracing[R, E, A] =>
        new ZIO.CheckTracing(t => yieldingEffects(check.k(t)))
    }

}
