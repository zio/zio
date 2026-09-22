package migratefrommonix

import zio._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Concurrency and Fibers
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step5Concurrency"
 */
object Step5Concurrency extends ZIOAppDefault {
  def run: Task[Unit] =
    for {
      // fork — replace .start
      fiber <- ZIO.attempt("background work").fork
      v     <- fiber.join

      // raceEither — true Monix Task.race equivalent returning Either[A, B]
      raced <- ZIO.attempt("left").raceEither(ZIO.sleep(10.millis).as("right"))

      // collectAllPar — replace parSequence
      results <- ZIO.collectAllPar(List(ZIO.succeed(1), ZIO.succeed(2), ZIO.succeed(3)))

      // withParallelism — replace parSequenceN
      bounded <- ZIO
                   .collectAllPar(List(ZIO.succeed(1), ZIO.succeed(2), ZIO.succeed(3)))
                   .withParallelism(2)

      // zipWithPar — replace parMap2
      pair <- ZIO.succeed("hello").zipWithPar(ZIO.succeed(42))((a, b) => (a, b))

      _ <- ZIO.succeed(println(s"background=$v race=$raced results=$results bounded=$bounded pair=$pair"))
      _ <- fiber.interrupt
    } yield ()
}
