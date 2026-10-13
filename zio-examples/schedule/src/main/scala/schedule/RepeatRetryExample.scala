package schedule

import zio._

/**
 * Tutorial: Schedule Step by Step — Retry and Repeat Policies in ZIO
 * Concept: Using a Schedule with repeat and retry
 *
 * Demonstrates how to plug a Schedule into ZIO#repeat and ZIO#retry.
 * `repeat` runs an effect repeatedly as long as the schedule says Continue;
 * `retry` reruns a failing effect as long as the schedule says Continue.
 *
 * sbt "runMain schedule.RepeatRetryExample"
 */
object RepeatRetryExample extends App {

  val runtime = Runtime.default

  // --- repeat example: count how many times the effect runs ---
  val repeatDemo: ZIO[Any, Nothing, Unit] =
    for {
      counter <- Ref.make(0)
      _ <- counter
             .update(_ + 1)
             .repeat(Schedule.recurs(3)) // 1 initial run + 3 repeats = 4 total
      count <- counter.get
      _     <- Console.printLine(s"[repeat] Effect ran $count times (expected 4)").orDie
      _ <-
        ZIO.unit
          .repeat(Schedule.recurs(4))
          .flatMap(n => Console.printLine(s"[repeat] Final schedule output: $n (expected 4)").orDie)
    } yield ()

  // --- retry example: always-failing effect retried once ---
  def alwaysFail(ref: Ref[Int]): IO[String, Nothing] =
    ref.updateAndGet(_ + 1).flatMap(n => ZIO.fail(s"Error: $n"))

  val retryDemo: ZIO[Any, Nothing, Unit] =
    for {
      ref    <- Ref.make(0)
      result <- alwaysFail(ref)
                  .retry(Schedule.once) // allow exactly 1 retry → 2 total attempts
                  .catchAll(err => ZIO.succeed(err))
      _ <- Console.printLine(s"[retry]  Final error after retries: $result (expected 'Error: 2')").orDie
    } yield ()

  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(repeatDemo *> retryDemo).getOrThrowFiberFailure()
  }
}
