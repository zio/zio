package zio

import zio.test._
import Assertion._

object FiberPoolSpec extends ZIOBaseSpec {
  def spec = suite("FiberPool")(
    testM("executes tasks") {
      checkM(Gen.int(10, 250).noShrink, Gen.int(10, 250).noShrink) { (target, limit) =>
        for {
          ref    <- Ref.make(0)
          pool   <- FiberPool.make(limit.toLong)
          tasks  = ZIO.replicate(target)(ref.updateAndGet(_ + 1))
          _      <- ZIO.foreach(tasks)(task => pool.submit(task))
          _      <- pool.shutdown(FiberPool.Shutdown.DrainPending)
          result <- ref.get
        } yield assert(result)(equalTo(target))
      }
    },
    testM("interrupts stuck fibers") {
      checkM(Gen.int(10, 250), Gen.int(10, 250)) { (limit, parallelism) =>
        for {
          pool      <- FiberPool.make(limit.toLong)
          ref       <- Ref.make(0)
          tasks      = ZIO.replicate(parallelism)(random.nextBoolean.flatMap(if (_) ZIO.never else ref.update(_ + 1)))
          submitter <- ZIO.foreachPar(tasks)(pool.submit(_)).fork
          _         <- pool.shutdown(FiberPool.Shutdown.Immediate)
          _         <- submitter.await
        } yield assertCompletes
      }
    }
  )
}
