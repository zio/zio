package zio.blocking

import java.util.concurrent.atomic.AtomicBoolean

import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect.nonFlaky
import zio.test._
import zio.{ UIO, ZIOBaseSpec }

object BlockingSpec extends ZIOBaseSpec {

  def spec: ZSpec[Environment, Failure] = suite("BlockingSpec")(
    suite("Make a Blocking Service and verify that")(
      testM("effectBlocking completes successfully") {
        assertM(effectBlocking(()))(isUnit)
      },
      testM("effectBlocking runs on the blocking thread pool") {
        for {
          name <- effectBlocking(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("effectBlockingCancelable completes successfully") {
        assertM(effectBlockingCancelable(())(UIO.unit))(isUnit)
      },
      testM("effectBlockingCancelable runs on the blocking thread pool") {
        for {
          name <- effectBlockingCancelable(Thread.currentThread.getName)(UIO.unit)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("effectBlockingCancelable can be interrupted") {
        val release = new AtomicBoolean(false)
        val cancel  = UIO.effectTotal(release.set(true))
        assertM(effectBlockingCancelable(blockingAtomic(release))(cancel).timeout(Duration.Zero))(isNone)
      },
      testM("effectBlockingInterrupt completes successfully") {
        assertM(effectBlockingInterrupt(()))(isUnit)
      },
      testM("effectBlockingInterrupt runs on the blocking thread pool") {
        for {
          name <- effectBlockingInterrupt(Thread.currentThread.getName)
        } yield assert(name)(containsString("zio-default-blocking"))
      },
      testM("effectBlockingInterrupt can be interrupted") {
        assertM(effectBlockingInterrupt(Thread.sleep(50000)).timeout(Duration.Zero))(isNone)
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
