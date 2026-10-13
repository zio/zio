package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Typing Your Error Channel
 *
 * The "before" side of migratecatseffect.Step3ErrorHandling.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step3ErrorHandling"
 */
object Step3ErrorHandling extends IOApp.Simple {

  sealed abstract class AppError(msg: String) extends RuntimeException(msg)
  case class DbError(msg: String)      extends AppError(msg)
  case class TimeoutError(msg: String) extends AppError(msg)

  val failedQuery: IO[String] =
    IO.raiseError(DbError("connection refused"))

  val recovered: IO[String] =
    failedQuery.handleErrorWith(e => IO(s"recovered: ${e.getMessage}"))

  val rawQuery: IO[String] =
    IO(throw new RuntimeException("timeout"))

  val typed: IO[String] =
    rawQuery.adaptError {
      case e: RuntimeException => TimeoutError(e.getMessage)
      case other                => DbError(other.getMessage)
    }

  val inspected: IO[Either[Throwable, String]] = typed.attempt

  def run: IO[Unit] =
    for {
      r1 <- recovered
      _  <- IO(println(r1))
      r2 <- inspected
      _  <- IO(println(r2))
    } yield ()
}
