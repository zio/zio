package zio.stream

import zio._
import zio.test._

object ZStreamGroupedWithinSpec extends ZIOBaseSpec {

  def spec =
    suite("ZStreamGroupedWithinSpec")(
      test("groupedWithin completes on infinite stream filtered down to finite elements (#8686)") {
        for {
          fiber <- ZStream
                     .fromIterable(Seq(1, 2))
                     .forever
                     .mapAccum(Set.empty[Int]) { (acc, i) =>
                       if (acc.contains(i)) (acc, None)
                       else (acc + i, Some(i))
                     }
                     .collectSome
                     .groupedWithin(3, 5.seconds)
                     .take(1)
                     .runCollect
                     .fork
          _      <- TestClock.adjust(5.seconds)
          result <- fiber.join
        } yield assertTrue(result == Chunk(Chunk(1, 2)))
      }
    )
}
