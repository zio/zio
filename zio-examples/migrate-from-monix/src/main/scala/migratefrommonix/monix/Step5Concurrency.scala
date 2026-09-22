package migratefrommonix.monix

import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}

import scala.concurrent.duration._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Concurrency and Fibers
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step5Concurrency"
 */
object Step5Concurrency extends TaskApp {
  def run(args: List[String]): Task[ExitCode] =
    for {
      // start — fork; fiber.join to await result
      fiber <- Task.eval("background work").start
      v     <- fiber.join

      // race — returns Either[A, B] discriminating left vs right
      raced <- Task.race(Task.eval("left"), Task.sleep(10.millis).as("right"))

      // parSequence — parallel collection
      results <- Task.parSequence(List(Task.eval(1), Task.eval(2), Task.eval(3)))

      // parMap2 — parallel zip
      pair <- Task.parMap2(Task.eval("hello"), Task.eval(42))((a, b) => (a, b))

      _ <- Task.eval(println(s"background=$v race=$raced results=$results pair=$pair"))
    } yield ExitCode.Success
}
