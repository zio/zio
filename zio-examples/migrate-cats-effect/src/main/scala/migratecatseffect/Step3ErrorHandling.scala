package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Typing Your Error Channel
 *
 * Replaces:
 *   IO.raiseError(e)        -> ZIO.fail(e)
 *   io.handleErrorWith(f)   -> zio.catchAll(f)
 *   io.recover { case ... } -> zio.catchSome { case ... }
 *   io.attempt              -> zio.either
 *   (no equivalent)         -> zio.mapError(f)
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step3ErrorHandling"
 */
object Step3ErrorHandling extends ZIOAppDefault {

  sealed trait AppError extends Throwable
  case class DbError(msg: String)      extends AppError
  case class TimeoutError(msg: String) extends AppError

  val failedQuery: IO[DbError, String] =
    ZIO.fail(DbError("connection refused"))

  val recovered: UIO[String] =
    failedQuery.catchAll(e => ZIO.succeed(s"recovered: ${e.msg}"))

  val rawQuery: Task[String] =
    ZIO.attempt(throw new RuntimeException("timeout"))

  val typed: IO[AppError, String] =
    rawQuery.mapError {
      case e: RuntimeException => TimeoutError(e.getMessage)
      case other               => DbError(other.getMessage)
    }

  val inspected: UIO[Either[AppError, String]] = typed.either

  def run: Task[Unit] =
    for {
      r1 <- recovered
      _  <- ZIO.succeed(println(r1))
      r2 <- inspected
      _  <- ZIO.succeed(println(r2))
    } yield ()
}
