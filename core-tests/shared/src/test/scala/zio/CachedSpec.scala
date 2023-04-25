package zio

import zio.test._

object CachedSpec extends ZIOBaseSpec {
  def spec = suite("CachedSpec")(
    test("manual") {
      for {
        ref    <- Ref.make(0)
        cached <- Cached.manual(ref.get)
        value1 <- cached.get
        value2 <- ref.set(1) *> cached.refresh *> cached.get
      } yield assertTrue(value1 == 0) && assertTrue(value2 == 1)
    },
    test("auto") {
      for {
        ref    <- Ref.make(0)
        cached <- Cached.auto(ref.get, Schedule.spaced(1.second))
        value1 <- cached.get
        value2 <- ref.set(1) *> TestClock.adjust(10.seconds) *> cached.get
      } yield assertTrue(value1 == 0) && assertTrue(value2 == 1)
    },
    test("failed refresh doesn't affect cached value") {
      for {
        ref    <- Ref.make[Either[String, Int]](Right(0))
        cached <- Cached.auto(ref.get.absolve, Schedule.spaced(1.second))
        value1 <- cached.get
        value2 <- ref.set(Left("Uh oh!")) *> TestClock.adjust(10.seconds) *> cached.get
      } yield assertTrue(value1 == 0) && assertTrue(value2 == 0)
    }
  )
}
