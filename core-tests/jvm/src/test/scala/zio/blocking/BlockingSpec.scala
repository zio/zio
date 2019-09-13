package zio.blocking

import java.util.concurrent.atomic.AtomicBoolean

import zio.duration.Duration
import zio.{ UIO, ZIOBaseSpec }
import BlockingSpecUtil._
import zio.test._
import zio.test.Assertion._

class BlockingSpec
    extends ZIOBaseSpec(
      suite("BlockingSpec")(
        suite("Make a Blocking Service and verify that that")(
          testM("effectBlocking` completes successfully") {
            for {
              io <- effectBlocking(())
            } yield assert(io, equalTo(()))
          },
          testM("effectBlockingCancelable` completes successfully") {
            for {
              io <- effectBlockingCancelable(())(UIO.unit)
            } yield assert(io, equalTo(()))
          },
          testM("effectBlocking` can be interrupted") {
            for {
              io <- effectBlocking(Thread.sleep(50000)).timeout(Duration.Zero)
            } yield assert(io, isNone)
          },
          testM("effectBlockingCancelable` can be interrupted") {
            val release = new AtomicBoolean(false)
            val cancel  = UIO.effectTotal(release.set(true))
            for {
              io <- effectBlockingCancelable(blockingAtomic(release))(cancel)
                     .timeout(Duration.Zero)
            } yield assert(io, isNone)
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
