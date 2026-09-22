package migratefrommonix

import zio._
import zio.test._
import zio.test.Assertion._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Testing — TestScheduler → TestClock
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step10Testing"
 */
object Step10Testing extends ZIOSpecDefault {
  def spec: Spec[Any, Nothing] =
    suite("TestClock")(
      test("effect completes after delay") {
        for {
          fiber  <- ZIO.sleep(1.second).as("done").fork // 1. fork first
          _      <- TestClock.adjust(1.second)          // 2. advance clock
          result <- fiber.join                          // 3. then join
        } yield assertTrue(result == "done")
      }
    )
}
