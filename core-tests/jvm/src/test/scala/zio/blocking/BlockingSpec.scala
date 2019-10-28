package zio.blocking

import java.util.concurrent.atomic.AtomicBoolean

import zio.blocking.BlockingSpecUtil._
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{ UIO, ZIOBaseSpec }

object BlockingSpec
    extends ZIOBaseSpec(
      suite("BlockingSpec")(
        suite("Make a Blocking Service and verify that")(
          testM("effectBlocking completes successfully") {
            assertM(effectBlocking(()), isUnit)
          },
          testM("effectBlockingCancelable completes successfully") {
            assertM(effectBlockingCancelable(())(UIO.unit), isUnit)
          },
          testM("effectBlocking can be interrupted") {
            assertM(effectBlocking(Thread.sleep(50000)).timeout(Duration.Zero), isNone)
          } @@ timeout(1.millis) @@ flaky, //todo fix in #1882 - shouldn't be flaky or require timeout
          testM("effectBlockingCancelable can be interrupted") {
            val release = new AtomicBoolean(false)
            val cancel  = UIO.effectTotal(release.set(true))
            assertM(effectBlockingCancelable(blockingAtomic(release))(cancel).timeout(Duration.Zero), isNone)
          }
        )
      )
    )

object BlockingSpecUtil {
  def blockingAtomic(released: AtomicBoolean): Unit =
    while (!released.get()) {
      try {
        Thread.sleep(10L)
      } catch {
        case _: InterruptedException => ()
      }
    }
}
