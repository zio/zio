package zio

import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._

import java.util.concurrent.atomic.AtomicBoolean

object BlockingSpec extends ZIOBaseSpec {

  def spec = suite("BlockingSpec")(
    suite("Make a Blocking Service and verify that")(
      test("attemptBlocking completes successfully") {
        assertM(ZIO.attemptBlocking(()))(isUnit)
      },
      test("attemptBlocking runs on the blocking thread pool") {
        for {
          name <- ZIO.attemptBlocking(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      test("attemptBlockingCancelable completes successfully") {
        assertM(ZIO.attemptBlockingCancelable(())(ZIO.unit))(isUnit)
      },
      test("attemptBlockingCancelable runs on the blocking thread pool") {
        for {
          name <- ZIO.attemptBlockingCancelable(Thread.currentThread.getName)(ZIO.unit)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      test("attemptBlockingCancelable can be interrupted") {
        val release = new AtomicBoolean(false)
        val cancel  = ZIO.succeed(release.set(true))
        assertM(ZIO.attemptBlockingCancelable(blockingAtomic(release))(cancel).timeout(Duration.Zero))(isNone)
      },
      test("attemptBlockingInterrupt completes successfully") {
        assertM(ZIO.attemptBlockingInterrupt(()))(isUnit)
      },
      test("attemptBlockingInterrupt runs on the blocking thread pool") {
        for {
          name <- ZIO.attemptBlockingInterrupt(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      test("attemptBlockingInterrupt can be interrupted") {
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
