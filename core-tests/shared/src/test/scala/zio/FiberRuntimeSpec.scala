package zio

import zio.Random.RandomLive
import zio.internal.FiberScope
import zio.metrics.Metric
import zio.test.TestAspect.{nonFlaky, timeout}
import zio.test._

import java.util.concurrent.atomic.AtomicInteger

object FiberRuntimeSpec extends ZIOBaseSpec {
  private implicit val unsafe: Unsafe = Unsafe.unsafe

  def spec = suite("FiberRuntimeSpec")(
    suite("whileLoop")(
      test("auto-yields every 10280 operations when no other yielding is performed") {
        ZIO.suspendSucceed {
          val nIters       = 50000
          val nOpsPerYield = 1024 * 10
          val nOps         = new AtomicInteger(0)
          val latch        = Promise.unsafe.make[Nothing, Unit](FiberId.None)
          val supervisor   = new YieldTrackingSupervisor(latch, nOps)
          val f            = ZIO.whileLoop(nOps.getAndIncrement() < nIters)(Exit.unit)(_ => ())
          ZIO
            .withFiberRuntime[Any, Nothing, Unit] { (parentFib, status) =>
              val fiber = ZIO.unsafe.makeChildFiber(Trace.empty, f, parentFib, status.runtimeFlags, FiberScope.global)
              fiber.setFiberRef(FiberRef.currentSupervisor, supervisor)
              fiber.startConcurrently(f)
              latch.await
            }
            .as {
              val yieldedAt = supervisor.yieldedAt
              assertTrue(
                yieldedAt == List(
                  nIters + 1,
                  nOpsPerYield * 4 - 3,
                  nOpsPerYield * 3 - 2,
                  nOpsPerYield * 2 - 1,
                  nOpsPerYield
                )
              )
            }
        }
      },
      test("doesn't auto-yield when effect itself yields") {
        ZIO.suspendSucceed {
          val nIters     = 50000
          val nOps       = new AtomicInteger(0)
          val latch      = Promise.unsafe.make[Nothing, Unit](FiberId.None)
          val supervisor = new YieldTrackingSupervisor(latch, nOps)
          val f =
            ZIO.whileLoop(nOps.getAndIncrement() < nIters)(ZIO.when(nOps.get() % 10000 == 0)(ZIO.yieldNow))(_ => ())
          ZIO
            .withFiberRuntime[Any, Nothing, Unit] { (parentFib, status) =>
              val fiber = ZIO.unsafe.makeChildFiber(Trace.empty, f, parentFib, status.runtimeFlags, FiberScope.global)
              fiber.setFiberRef(FiberRef.currentSupervisor, supervisor)
              fiber.startConcurrently(f)
              latch.await
            }
            .as {
              val yieldedAt = supervisor.yieldedAt
              assertTrue(
                yieldedAt == List(nIters + 1, 50000, 40000, 30000, 20000, 10000)
              )
            }
        }
      }
    ),
    suite("async")(
      test("async callback after interruption is ignored") {
        ZIO.suspendSucceed {
          val executed = Ref.unsafe.make(0)
          val cb       = Ref.unsafe.make[Option[ZIO[Any, Nothing, Unit] => Unit]](None)
          val latch    = Promise.unsafe.make[Nothing, Unit](FiberId.None)
          val async = ZIO.async[Any, Nothing, Unit] { k =>
            cb.unsafe.set(Some(k))
            latch.unsafe.done(Exit.unit)
          }
          val increment = executed.update(_ + 1)
          for {
            fiber          <- async.fork
            _              <- latch.await
            exit           <- fiber.interrupt
            callback       <- cb.get.some
            state1         <- fiber.poll
            _              <- ZIO.succeed(callback(increment))
            state2         <- fiber.poll
            executedBefore <- executed.get
            _              <- ZIO.succeed(callback(increment))
            state3         <- fiber.poll
            executedAfter  <- executed.get
          } yield assertTrue(
            state1 == Some(exit),
            state2 == Some(exit),
            state3 == Some(exit),
            executedBefore == 0,
            executedAfter == 0,
            exit.isInterrupted
          )
        }
      } @@ TestAspect.nonFlaky(10)
    ),
    suite("runtime metrics")(
      test("Failures are counted once for the fiber that caused them and exits are not") {
        val nullErrors = ZIO.foreachParDiscard(1 to 2)(_ => ZIO.attempt(throw new NullPointerException))

        val customErrors =
          ZIO.foreachParDiscard(1 to 5)(_ => ZIO.fail("Custom application error"))

        val exitErrors =
          ZIO.foreachParDiscard(1 to 5)(_ => Exit.fail(new IllegalArgumentException("Foo")))

        (nullErrors <&> exitErrors <&> customErrors).uninterruptible
          .foldCauseZIO(
            _ =>
              Metric.runtime.fiberFailureCauses.value
                .map(_.occurrences)
                // NOTE: Fibers in foreachParDiscard register metrics at the very end of the fiber's life which might be after we check them
                // so we might need to retry until they are registered
                .repeatUntil { oc =>
                  oc.size >= 2 &&
                  oc.getOrElse("java.lang.String", 0L) >= 5L &&
                  oc.getOrElse("java.lang.NullPointerException", 0L) >= 2L
                }
                .map { oc =>
                  assertTrue(
                    oc.size == 2,
                    oc.get("java.lang.String").contains(5),
                    oc.get("java.lang.NullPointerException").contains(2)
                  )
                },
            _ => ZIO.succeed(assertNever("Effect did not fail"))
          )
          .provide(Runtime.enableRuntimeMetrics) @@
          // Need to tag them to extract metrics from this specific effect
          ZIOAspect.tagged("FiberRuntimeSpec" -> RandomLive.unsafe.nextString(20))
      } @@ nonFlaky(1000) @@ timeout(10.seconds)
    )
  )

  private final class YieldTrackingSupervisor(
    latch: Promise[Nothing, Unit],
    nOps: AtomicInteger
  ) extends Supervisor[Unit] {
    @volatile var yieldedAt           = List.empty[Int]
    @volatile private var onEndCalled = false

    def value(implicit trace: Trace): UIO[Unit] = ZIO.unit

    def onStart[R, E, A](
      environment: ZEnvironment[R],
      effect: ZIO[R, E, A],
      parent: Option[Fiber.Runtime[Any, Any]],
      fiber: Fiber.Runtime[E, A]
    )(implicit unsafe: Unsafe): Unit = ()

    override def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
      onEndCalled = true
      ()
    }

    override def onSuspend[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
      yieldedAt ::= nOps.get()
      if (onEndCalled) latch.unsafe.done(Exit.unit) // onEnd gets called before onSuspend
      ()
    }
  }

}
