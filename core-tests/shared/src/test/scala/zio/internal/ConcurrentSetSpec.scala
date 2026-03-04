package zio.internal

import zio.test.TestAspect.{nativeOnly, nonFlaky}
import zio.test._
import zio.{Unsafe, ZIO, ZIOBaseSpec}

object ConcurrentSetSpec extends ZIOBaseSpec {
  private final case class Colliding(id: Int) {
    override def hashCode(): Int = 1
  }

  def spec =
    suite("ConcurrentSetSpec")(
      test("handles colliding hash codes under concurrent inserts") {
        val set         = Platform.newConcurrentSet[Colliding](16)(Unsafe.unsafe)
        val elementSize = 20000

        for {
          _ <- ZIO.foreachParDiscard(1 to elementSize) { id =>
            ZIO.succeed(set.add(Colliding(id)))
          }
        } yield assert(set.size())(Assertion.equalTo(elementSize))
      } @@ nonFlaky(25)
    ) @@ nativeOnly
}
