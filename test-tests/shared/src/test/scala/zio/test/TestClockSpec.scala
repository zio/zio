package zio.test

import zio._

import java.time.Instant

object TestClockSpec extends ZIOBaseSpec {
  def spec = suite("TestClockSpec")(
    test("should accept any Instant") {
      check(Gen.instant) { instant =>
        for {
          _   <- TestClock.setInstant(instant)
          now <- Clock.instant
        } yield assertTrue(now == instant)
      }
    },
    test("should allow sleep") {
      for {
        queue <- Queue.unbounded[Int]
        fiber <- ZIO.foreachDiscard(1 to 3)(i => ZIO.sleep(1.second) *> queue.offer(i)).fork
        res   <- ZIO.foreach(Vector.fill(3)(0))(_ => TestClock.adjust(1.second) *> queue.take <*> queue.poll)
        _     <- fiber.interrupt
      } yield assertTrue(res == Vector((1, None), (2, None), (3, None)))
    },
    test("should allow multiple sleeps") {
      for {
        queue <- Queue.unbounded[Int]
        fiber <- ZIO.foreachDiscard(1 to 3)(i => ZIO.sleep(1.second) *> queue.offer(i)).fork
        _     <- TestClock.adjust(3.seconds)
        res   <- ZIO.foreach(Vector.fill(3)(0))(_ => queue.take)
        _     <- fiber.interrupt
      } yield assertTrue(res == Vector(1, 2, 3))
    },
    test("should allow long sleep") {
      val maxMillisDuration = Duration.fromMillis(Long.MaxValue)

      for {
        _       <- TestClock.adjust(maxMillisDuration)
        _       <- TestClock.adjust(maxMillisDuration)
        promise <- Promise.make[Nothing, Unit]
        _       <- (ZIO.sleep(maxMillisDuration) *> promise.succeed(())).fork
        _       <- TestClock.setInstant(Instant.MAX)
        _       <- promise.await
      } yield assertTrue(true)
    }
  )
}
