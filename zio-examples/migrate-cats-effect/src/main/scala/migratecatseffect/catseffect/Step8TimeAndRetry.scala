package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS, SECONDS}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Time, Timeouts, and Retries
 *
 * The "before" side of migratecatseffect.Step8TimeAndRetry. Only sleep/
 * timeout are shown here — retry policies live in the separate cats-retry
 * library, not cats-effect itself, so there is no compiled retry snippet
 * to mirror ZIO's Schedule-based retry.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step8TimeAndRetry"
 */
object Step8TimeAndRetry extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // sleep — replaces IO.sleep
      _ <- IO(println("Sleeping for 100 millis..."))
      _ <- IO.sleep(FiniteDuration(100, MILLISECONDS))

      // timeout — raises a TimeoutException on timeout; ZIO's variant returns an Option instead
      timed <- IO("this finishes fast").timeout(FiniteDuration(1, SECONDS))
      _     <- IO(println(s"Timeout result: $timed"))
    } yield ()
}
