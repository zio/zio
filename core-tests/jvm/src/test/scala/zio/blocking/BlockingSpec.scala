package zio.blocking

import java.util.concurrent.atomic.AtomicBoolean

import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect.ignore
import zio.test._
import zio.{ UIO, ZIOBaseSpec }

object BlockingSpec extends ZIOBaseSpec {

  def spec = suite("BlockingSpec")(
    suite("Make a Blocking Service and verify that")(
      testM("effectBlocking completes successfully") {
        assertM(effectBlocking(()))(isUnit)
      },
      testM("effectBlockingCancelable completes successfully") {
        assertM(effectBlockingCancelable(())(UIO.unit))(isUnit)
      },
      testM("effectBlockingInterrupt completes successfully") {
        assertM(effectBlockingInterrupt(()))(isUnit)
      },
      testM("effectBlockingInterrupt can be interrupted") {
        assertM(effectBlockingInterrupt(Thread.sleep(50000)).timeout(Duration.Zero))(isNone)
      } @@ ignore,
      testM("effectBlockingCancelable can be interrupted") {
        val release = new AtomicBoolean(false)
        val cancel  = UIO.effectTotal(release.set(true))
        assertM(effectBlockingCancelable(blockingAtomic(release))(cancel).timeout(Duration.Zero))(isNone)
      }
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
