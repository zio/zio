package zio

import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._

import java.util.concurrent.atomic.AtomicBoolean

object BlockingSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("BlockingSpec")(
    suite("Make a Blocking Service and verify that")(
      testM("attemptBlocking completes successfully") {
        assertM(ZIO.attemptBlocking(()))(isUnit)
      },
      testM("attemptBlocking runs on the blocking thread pool") {
        for {
          name <- ZIO.attemptBlocking(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("attemptBlockingCancelable completes successfully") {
        assertM(ZIO.attemptBlockingCancelable(())(UIO.unit))(isUnit)
      },
      testM("attemptBlockingCancelable runs on the blocking thread pool") {
        for {
          name <- ZIO.attemptBlockingCancelable(Thread.currentThread.getName)(UIO.unit)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("attemptBlockingCancelable can be interrupted") {
        val release = new AtomicBoolean(false)
        val cancel  = UIO.succeed(release.set(true))
        assertM(ZIO.attemptBlockingCancelable(blockingAtomic(release))(cancel).timeout(Duration.Zero))(isNone)
      },
      testM("attemptBlockingInterrupt completes successfully") {
        assertM(ZIO.attemptBlockingInterrupt(()))(isUnit)
      },
      testM("attemptBlockingInterrupt runs on the blocking thread pool") {
        for {
          name <- ZIO.attemptBlockingInterrupt(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("attemptBlockingInterrupt can be interrupted") {
        assertM(ZIO.attemptBlockingInterrupt(Thread.sleep(50000)).timeout(Duration.Zero))(isNone)
      } @@ nonFlaky
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
}
