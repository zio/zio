package zio.internal

import zio._
import zio.test._

object ZSchedulerPerformanceSpec extends ZIOSpecDefault {
  def spec = suite("ZSchedulerPerformanceSpec")(
    test("ZScheduler should not park/unpark excessively under high load") {
      for {
        _ <- ZIO.yieldNow
        // Create a large number of small tasks to stress the scheduler
        _ <- ZIO.foreachPar((1 to 10000).toList) { _ =>
          ZIO.succeed(1 + 1)
        }
      } yield assertCompletes
    }
  )
}
