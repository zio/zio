package zio

import zio.test._
import zio.test.TestAspect._

object ZLoggerSpec extends ZIOBaseSpec {
  trait Animal
  trait Dog     extends Animal
  object Scotty extends Dog

  def spec =
    suite("ZLoggerSpec") {
      suite("Set") {
        test("simple lookup") {
          val logger = ZLogger.simple[String, Unit](_ => ())

          val set = ZLogger.Set(logger)

          val loggers = set.getAll[String]

          val test = loggers.exists(_ eq logger) // TODO: Fix assertTrue

          assertTrue(test)
        } +
          test("supertype lookup 1") {
            val logger = ZLogger.simple[Animal, Unit](_ => ())

            val set = ZLogger.Set(logger)

            val loggers = set.getAll[Scotty.type]

            val test = loggers.exists(_ eq logger) // TODO: Fix assertTrue

            assertTrue(test)
          } +
          test("supertype lookup 2") {
            val logger = ZLogger.simple[Any, Unit](_ => ())

            val set = ZLogger.Set(logger)

            val loggers = set.getAll[Int]

            println(set)

            val test = loggers.exists(_ eq logger) // TODO: Fix assertTrue

            assertTrue(test)
          } @@ exceptScala3 +
          test("supertype lookup 3") {
            val logger = ZLogger.simple[Cause[Any], Unit](_ => ())

            val set = ZLogger.Set(logger)

            val loggers = set.getAll[Cause[Dog]]

            val test = loggers.exists(_ eq logger) // TODO: Fix assertTrue

            assertTrue(test)
          }
      }
    }
}
