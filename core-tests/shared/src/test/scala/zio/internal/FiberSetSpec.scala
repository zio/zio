package zio.internal

import zio._
import zio.test._
import zio.test.TestAspect._

object FiberSetSpec extends ZIOSpecDefault {
  def spec = suite("FiberSetSpec")(
    test("add & foreach sees live fibers") {
      val set = FiberSet.make()
      for {
        f1 <- ZIO.never.fork
        f2 <- ZIO.unit.fork
        _   = set.add(f1)
        _   = set.add(f2)
        seen <- ZIO.succeed {
          val b = scala.collection.mutable.Set[Fiber.Runtime[_, _]]()
          set.foreach(b.add)
          b.size
        }
        _ <- f2.interrupt
        _  = set.remove(f2)
      } yield assertTrue(seen >= 2)
    } @@ eventually
  ) @@ timed

  private val eventually = TestAspect.eventually
}
