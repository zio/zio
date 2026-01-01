package zio

import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

object BlockingSpec extends ZIOBaseSpec {

  def spec =
    suite("BlockingSpec")(
      suite("attemptBlocking")(
        test("completes successfully") {
          assertZIO(ZIO.attemptBlocking(()))(isUnit)
        },
        test("runs on blocking thread pool") {
          for {
            name <- ZIO.attemptBlocking(Thread.currentThread.getName)
          } yield assert(name)(containsString("zio-default-blocking"))
        }
      ),
      suite("attemptBlockingCancelable")(
        test("completes successfully") {
          assertZIO(ZIO.attemptBlockingCancelable(())(ZIO.unit))(isUnit)
        },
        test("runs on blocking thread pool") {
          for {
            name <- ZIO.attemptBlockingCancelable(Thread.currentThread.getName)(ZIO.unit)
          } yield assert(name)(containsString("zio-default-blocking"))
        },
        test("can be interrupted") {
          val release = new AtomicBoolean(false)
          val cancel  = ZIO.succeed(release.set(true))
          assertZIO(ZIO.attemptBlockingCancelable(blockingAtomic(release))(cancel).timeout(Duration.Zero))(isNone)
        }
      ),
      suite("attemptBlockingInterrupt")(
        test("completes successfully") {
          assertZIO(ZIO.attemptBlockingInterrupt(()))(isUnit)
        },
        test("runs on blocking thread pool") {
          for {
            name <- ZIO.attemptBlockingInterrupt(Thread.currentThread.getName)
          } yield assert(name)(containsString("zio-default-blocking"))
        },
        test("can be interrupted") {
          assertZIO(ZIO.attemptBlockingInterrupt(Thread.sleep(50000)).timeout(Duration.Zero))(isNone)
        } @@ nonFlaky
      ),
      suite("ZIO.blocking")(
        test("runs on blocking thread pool") {
          for {
            name <- ZIO.blocking(ZIO.succeed(Thread.currentThread.getName))
          } yield assert(name)(containsString("zio-default-blocking"))
        },
        test("uses configured blocking executor") {
          for {
            blockingExec <- ZIO.blockingExecutor
            actualExec   <- ZIO.blocking(ZIO.executor)
          } yield assertTrue(actualExec == blockingExec)
        },
        test("restores executor after completion") {
          val default = Runtime.defaultExecutor
          for {
            _         <- ZIO.blocking(ZIO.unit)
            afterExec <- ZIO.executor
          } yield assertTrue(afterExec == default)
        },
        test("restores executor after error") {
          val default = Runtime.defaultExecutor
          for {
            _         <- ZIO.blocking(ZIO.fail("error")).ignore
            afterExec <- ZIO.executor
          } yield assertTrue(afterExec == default)
        },
        test("nested blocking stays on blocking pool") {
          for {
            blockingExec <- ZIO.blockingExecutor
            innerExec    <- ZIO.blocking(ZIO.blocking(ZIO.executor))
          } yield assertTrue(innerExec == blockingExec)
        },
        test("respects custom blocking executor") {
          val customExec = Executor.fromExecutionContext(scala.concurrent.ExecutionContext.global)
          for {
            actualExec <- FiberRef.currentBlockingExecutor.locally(customExec) {
                            ZIO.blocking(ZIO.executor)
                          }
          } yield assertTrue(actualExec == customExec)
        },
        test("blocking inside forked fiber") {
          for {
            blockingExec <- ZIO.blockingExecutor
            fiber        <- ZIO.blocking(ZIO.executor).fork
            result       <- fiber.join
          } yield assertTrue(result == blockingExec)
        },
        test("nested blocking skips yield when already on blocking executor") {
          for {
            tracking <- ZIO.succeed(new TrackingExecutor())
            _ <- FiberRef.currentBlockingExecutor.locally(tracking) {
                   ZIO.blocking {
                     ZIO.blocking(ZIO.unit)
                   }
                 }
          } yield assertTrue(tracking.submitted == 1)
        }
      ),
      suite("ZIO.onExecutor")(
        test("shifts to specified executor") {
          val global = Executor.fromExecutionContext(scala.concurrent.ExecutionContext.global)
          for {
            actualExec <- ZIO.executor.onExecutor(global)
          } yield assertTrue(actualExec == global)
        },
        test("restores executor after completion") {
          val global  = Executor.fromExecutionContext(scala.concurrent.ExecutionContext.global)
          val default = Runtime.defaultExecutor
          for {
            _         <- ZIO.unit.onExecutor(global)
            afterExec <- ZIO.executor
          } yield assertTrue(afterExec == default)
        },
        test("restores executor after error") {
          val global  = Executor.fromExecutionContext(scala.concurrent.ExecutionContext.global)
          val default = Runtime.defaultExecutor
          for {
            _         <- ZIO.fail("error").onExecutor(global).ignore
            afterExec <- ZIO.executor
          } yield assertTrue(afterExec == default)
        },
        test("nested blocking and onExecutor restore correctly") {
          val global  = Executor.fromExecutionContext(scala.concurrent.ExecutionContext.global)
          val default = Runtime.defaultExecutor
          for {
            blockingExec <- ZIO.blockingExecutor
            result <- ZIO.blocking {
                        for {
                          exec1 <- ZIO.executor
                          exec2 <- ZIO.unit.onExecutor(global) *> ZIO.executor
                          exec3 <- ZIO.blocking(ZIO.executor)
                        } yield (exec1, exec2, exec3)
                      }
            afterExec <- ZIO.executor
          } yield assertTrue(
            result._1 == blockingExec,
            result._2 == blockingExec,
            result._3 == blockingExec,
            afterExec == default
          )
        },
        test("multiple sequential blocking calls restore correctly") {
          val default = Runtime.defaultExecutor
          for {
            blockingExec <- ZIO.blockingExecutor
            exec1        <- ZIO.blocking(ZIO.executor)
            between1     <- ZIO.executor
            exec2        <- ZIO.blocking(ZIO.executor)
            between2     <- ZIO.executor
            exec3        <- ZIO.blocking(ZIO.executor)
            after        <- ZIO.executor
          } yield assertTrue(
            exec1 == blockingExec,
            between1 == default,
            exec2 == blockingExec,
            between2 == default,
            exec3 == blockingExec,
            after == default
          )
        },
        test("skips yield when already on target executor") {
          for {
            tracking <- ZIO.succeed(new TrackingExecutor())
            _ <- ZIO.unit.onExecutor(tracking).flatMap { _ =>
                   ZIO.unit.onExecutor(tracking)
                 }
          } yield assertTrue(tracking.submitted == 1)
        },
        test("yields when shifting to different executor") {
          for {
            tracking1 <- ZIO.succeed(new TrackingExecutor())
            tracking2 <- ZIO.succeed(new TrackingExecutor())
            _ <- ZIO.unit.onExecutor(tracking1).flatMap { _ =>
                   ZIO.unit.onExecutor(tracking2)
                 }
          } yield assertTrue(tracking1.submitted == 1, tracking2.submitted == 1)
        },
        test("with EagerShiftBack shifts back to original executor") {
          val global = Executor.fromExecutionContext(scala.concurrent.ExecutionContext.global)
          for {
            name <- (ZIO.unit.onExecutor(global) *> ZIO.succeed(Thread.currentThread().getName))
          } yield assertTrue(name.startsWith("ZScheduler-Worker"))
        }.provide(Runtime.enableFlags(RuntimeFlag.EagerShiftBack))
      )
    )

  def blockingAtomic(released: AtomicBoolean): Unit =
    while (!released.get()) {
      try {
        Thread.sleep(10L)
      } catch {
        case _: InterruptedException => ()
      }
    }

  final class TrackingExecutor extends Executor {
    private val submissionCount = new AtomicInteger(0)
    private val underlying      = zio.internal.Blocking.blockingExecutor

    def submitted: Int = submissionCount.get()

    override def submit(runnable: Runnable)(implicit unsafe: Unsafe): Boolean = {
      submissionCount.incrementAndGet()
      underlying.submit(runnable)
      true
    }

    override def metrics(implicit unsafe: Unsafe) = None
  }
}
