package zio

import zio.test._

import java.util.concurrent.CountDownLatch
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
      } @@ TestAspect.timeout(15.seconds) @@ TestAspect.nonFlaky
    )
}
