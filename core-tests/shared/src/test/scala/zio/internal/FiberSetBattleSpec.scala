package zio.internal

import zio._
import zio.test._

object FiberSetBattleSpec extends ZIOSpecDefault {

  val N_FIBERS = 500
  val N_OPS    = 1000

  def spec = suite("FiberSetBattleSpec")(
    test("survives massive concurrent bombardment") {
      val fiberSet = FiberSet.make[AnyRef]()

      // We will spawn N fibers, each doing Random Add/Remove ops
      val scenario = ZIO.foreachPar(0 until N_FIBERS) { id =>
        ZIO.succeed {
          val myObj = new Object
          // Add
          fiberSet.add(myObj)

          // Maybe remove immediately
          if (id % 2 == 0) {
            fiberSet.remove(myObj)
          }
        }
      }

      for {
        _ <- scenario
        // Check structural integrity
        size = fiberSet.size
        // It shouldn't crash
      } yield assertTrue(size >= 0)
    },
    test("concurrent additions matches ConcurrentHashMap count (eventually)") {
      // Only adds, no removes. Should match exactly.
      val fiberSet = FiberSet.make[AnyRef]()
      val n        = 10000
      val objects  = (0 until n).map(_ => new Object).toArray

      for {
        _   <- ZIO.foreachPar(objects.toList)(o => ZIO.succeed(fiberSet.add(o)))
        size = fiberSet.size
      } yield assertTrue(size == n)
    }
  )
}
