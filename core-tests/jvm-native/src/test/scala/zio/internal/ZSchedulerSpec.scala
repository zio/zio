package zio.internal

import zio.ZIOBaseSpec
import zio.test._

object ZSchedulerSpec extends ZIOBaseSpec {

  def spec =
    suite("ZSchedulerSpec")(
      suite("least-loaded worker selection")(
        test("chooses the available worker with the smallest load") {
          val loads = Array(4L, 1L, 0L, 2L)

          val selected =
            ZScheduler.chooseLeastLoadedWorker(loads.length)(loads(_), _ != 2)

          assertTrue(selected == 1)
        },
        test("returns no worker when every worker is unavailable") {
          val selected =
            ZScheduler.chooseLeastLoadedWorker(4)(_ => 0L, _ => false)

          assertTrue(selected == -1)
        },
        test("clamps transient negative loads to zero") {
          val loads = Array(5L, -1L, 2L)

          val selected =
            ZScheduler.chooseLeastLoadedWorker(loads.length)(loads(_), _ => true)

          assertTrue(selected == 1)
        }
      )
    )
}
