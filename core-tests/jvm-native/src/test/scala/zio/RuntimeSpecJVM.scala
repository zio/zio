package zio

import zio.test._

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.duration.SECONDS

object RuntimeSpecJVM extends ZIOBaseSpec {
  def spec =
    suite("RuntimeSpecJVM")(
      test("Runtime.unsafe.run doesn't deadlock when run within a fiber") {
        val rtm                     = Runtime.default.unsafe
        implicit val unsafe: Unsafe = Unsafe.unsafe
        val promise                 = Promise.unsafe.make[Nothing, Unit](FiberId.None)
        val effects                 = List.fill(50)(ZIO.succeed(rtm.run(ZIO.yieldNow *> promise.await)))

        for {
          f <- ZIO.collectAllPar(effects).forkDaemon
          _ <- ZIO.yieldNow
          _ <- promise.succeed(())
          _ <- f.join
        } yield assertCompletes
      } @@ TestAspect.timeout(5.seconds) @@ TestAspect.nonFlaky,
      test("ZScheduler supports scala.concurrent.blocking and scala.concurrent.Await") {
        val promise = scala.concurrent.Promise[Unit]()
        val latch   = new CountDownLatch(50)
        val effects = List.fill(50)(ZIO.succeed {
          latch.countDown()
          // Await uses `blocking` underneath
          Await.result(promise.future, scala.concurrent.duration.Duration(5, SECONDS))
        })

        for {
          f <- ZIO.collectAllPar(effects).forkDaemon
          _ <- ZIO.attemptBlocking(latch.await())
          _ <- ZIO.succeed(promise.trySuccess(()))
          _ <- f.join
        } yield assertCompletes
      } @@ TestAspect.timeout(15.seconds) @@ TestAspect.nonFlaky,
      test("ZScheduler supports scala.concurrent.blocking") {
        val promise = scala.concurrent.Promise[Unit]()
        val latch   = new CountDownLatch(50)
        val effects = List.fill(50)(ZIO.succeed {
          scala.concurrent.blocking {
            latch.countDown()
            latch.await()
            while (!promise.isCompleted) {
              Thread.sleep(0L, 10)
            }
          }
        })

        for {
          f <- ZIO.collectAllPar(effects).forkDaemon
          _ <- ZIO.attemptBlocking(latch.await())
          _ <- ZIO.succeed(promise.trySuccess(()))
          _ <- f.join
        } yield assertCompletes
      } @@ TestAspect.timeout(15.seconds) @@ TestAspect.nonFlaky,
      test("Runtime.unsafe.run passes host thread's interruption to fork and waits for fork interruption to finish") {
        val rtm                     = Runtime.default.unsafe
        implicit val unsafe: Unsafe = Unsafe.unsafe
        val started                 = Promise.unsafe.make[Nothing, Unit](FiberId.None)
        val interrupted             = Promise.unsafe.make[Nothing, Unit](FiberId.None)
        val proceedInterrupt        = Promise.unsafe.make[Nothing, Unit](FiberId.None)
        val threadExited            = Promise.unsafe.make[Nothing, Unit](FiberId.None)
        val effect =
          ZIO.uninterruptibleMask(restore =>
            ZIO.yieldNow *> started.succeed(())
              *> restore(ZIO.never).onInterrupt {
                interrupted.succeed(()) *>
                  proceedInterrupt.await
              }
          )
        val threadWasBlockedUntilFinalizationDone = new AtomicReference[Boolean]()

        val thread = new Thread(() =>
          try {
            val _ = rtm.run(effect)
          } finally {
            threadWasBlockedUntilFinalizationDone.set(proceedInterrupt.unsafe.isDone)
            threadExited.unsafe.succeed(())
            ()
          }
        )
        thread.setUncaughtExceptionHandler((_, _) => ())

        for {
          _ <- ZIO.attempt(thread.start())
          _ <- started.await
          _ <- ZIO.attempt(thread.interrupt())
          _ <- interrupted.await
          _ <- proceedInterrupt.succeed(())
          _ <- threadExited.await
          r <- ZIO.succeed(threadWasBlockedUntilFinalizationDone.get())
        } yield assertTrue(r)
      } @@ TestAspect.timeout(10.seconds) @@ TestAspect.nonFlaky(5)
    )
}
