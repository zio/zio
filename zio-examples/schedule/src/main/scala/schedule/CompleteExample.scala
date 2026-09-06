package schedule

import zio._

/**
 * Tutorial: Schedule Step by Step — Retry and Repeat Policies in ZIO
 * Concept: Putting It All Together — realistic HTTP retry policy
 *
 * Combines exponential backoff with a hard retry cap using `&&`, logs each
 * failure with `tapError`, and handles eventual exhaustion with `catchAll`.
 * Also demonstrates adding jitter to reduce retry storms in distributed systems.
 *
 * sbt "runMain schedule.CompleteExample"
 */
object CompleteExample extends App {

  val runtime = Runtime.default

  // Simulates an HTTP request: fails the first three attempts, then succeeds
  def httpRequest(counter: Ref[Int]): IO[String, String] =
    counter.updateAndGet(_ + 1).flatMap { attempt =>
      if (attempt <= 3) ZIO.fail(s"503 Service Unavailable (attempt $attempt)")
      else ZIO.succeed("200 OK")
    }

  // Policy: exponential backoff with a hard cap of 5 additional attempts.
  // && requires both schedules to say Continue; the earlier to stop wins.
  val retryPolicy: Schedule[Any, Any, (Duration, Long)] =
    Schedule.exponential(100.millis) && Schedule.recurs(5)

  val program: ZIO[Any, Nothing, Unit] =
    for {
      _       <- Console.printLine("=== HTTP Retry with exponential backoff ===").orDie
      counter <- Ref.make(0)
      result <- httpRequest(counter)
                  .tapError(err => Console.printLine(s"  Request failed: $err").orDie)
                  .retry(retryPolicy)
                  .catchAll(err => ZIO.succeed(s"Exhausted retries: $err"))
      _ <- Console.printLine(s"Final result: $result").orDie

      // Demonstrate exhaustion: always-failing effect hits the retry cap
      _ <- Console.printLine("\n=== Retry exhaustion demo ===").orDie
      exhaustCounter <- Ref.make(0)
      exhaustResult <- httpRequest(exhaustCounter) // won't succeed — reset to >5 initial failures
                         .tapError(err => Console.printLine(s"  Attempt failed: $err").orDie)
                         .retry(Schedule.exponential(1.millis) && Schedule.recurs(2))
                         .catchAll(err => ZIO.succeed(s"Exhausted: $err"))
      _ <- Console.printLine(s"Exhaustion result: $exhaustResult").orDie

      // Jitter variant: each delay is scaled by a random 0.8-1.2 factor
      _ <- Console.printLine("\n=== Jittered policy (verify via schedule.run) ===").orDie
      now <- Clock.currentDateTime
      jitteredDelays <- (Schedule.exponential(100.millis).jittered && Schedule.recurs(3))
                          .run(now, List.fill(4)(()))
      _ <- ZIO.foreach(jitteredDelays) { case (d, n) =>
             Console.printLine(s"  retry $n: delay ~${d.render}").orDie
           }
    } yield ()

  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(program).getOrThrowFiberFailure()
  }
}
