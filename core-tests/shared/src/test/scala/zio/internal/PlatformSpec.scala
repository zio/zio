package zio.internal

import zio.test.TestAspect.exceptJS
import zio.test._
import zio.{Unsafe, ZIO, ZIOBaseSpec}

object PlatformSpec extends ZIOBaseSpec {
  private final class Colliding(val value: Int) {
    override def equals(that: Any): Boolean =
      that match {
        case that: Colliding => value == that.value
        case _               => false
      }

    override def hashCode(): Int = 0
  }

  def spec =
    suite("PlatformSpec")(
      test("newConcurrentSet handles many colliding keys") {
        val set = Platform.newConcurrentSet[Colliding](32)(Unsafe.unsafe)

        for {
          _ <- ZIO.foreachParDiscard(1 to 1024)(i => ZIO.succeed(set.add(new Colliding(i))))
        } yield assertTrue(set.size == 1024)
      } @@ exceptJS
    )
}
