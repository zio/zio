package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Time, Timeouts, and Retries
 *
 * Replaces:
 *   IO.sleep(duration)                    -> ZIO.sleep(duration)
 *   Temporal[F].timeout(io, duration)     -> zio.timeout(duration)
 *   cats-retry retryingOnAllErrors(policy) -> zio.retry(schedule)
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step8TimeAndRetry"
 */
object Step8TimeAndRetry extends ZIOAppDefault {

  private var attempts = 0

  private def flaky: Task[String] =
    ZIO.attempt {
      attempts += 1
      if (attempts < 3) throw new RuntimeException(s"attempt $attempts failed")
      else s"succeeded on attempt $attempts"
    }

  def run: Task[Unit] =
    for {
      // sleep — replaces IO.sleep
      _ <- ZIO.succeed(println("Sleeping for 100 millis..."))
      _ <- ZIO.sleep(100.millis)

      // timeout — replaces Temporal#timeout; returns Option, None on timeout
      timedOut <- ZIO.succeed("this finishes fast").timeout(1.second)
      _        <- ZIO.succeed(println(s"Timeout result: $timedOut"))

      // retry with exponential backoff, capped at 5 attempts — replaces cats-retry
      retried <- flaky.retry(Schedule.exponential(10.millis) && Schedule.recurs(5))
      _       <- ZIO.succeed(println(s"Retry result: $retried"))
    } yield ()
}
