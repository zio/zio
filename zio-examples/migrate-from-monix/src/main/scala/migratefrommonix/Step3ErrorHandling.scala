package migratefrommonix

import zio._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Mapping the Error Channel
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step3ErrorHandling"
 */
object Step3ErrorHandling extends ZIOAppDefault {
  sealed trait AppError extends Throwable { def msg: String }
  case class DbError(msg: String)      extends AppError
  case class TimeoutError(msg: String) extends AppError

  val failedQuery: IO[DbError, String] =
    ZIO.fail(DbError("connection refused"))

  def run: Task[Unit] =
    for {
      // catchAll — replace onErrorHandleWith
      r1 <- failedQuery.catchAll(e => ZIO.succeed(s"recovered: ${e.msg}"))

      // fold — replace redeem
      r2 <- failedQuery.fold(e => s"failed: ${e.msg}", a => s"ok: $a")

      // either — replace attempt/materialize
      r3 <- failedQuery.either

      // mapError — unique to ZIO, narrows Throwable to domain type
      raw = ZIO.attempt(throw new RuntimeException("timeout"))
      r4 <- raw
               .mapError {
                 case e: RuntimeException => TimeoutError(e.getMessage)
                 case other               => DbError(other.getMessage)
               }
               .fold(e => s"typed error: ${e.msg}", a => s"ok: $a")

      // retryN — replace onErrorRestart
      attempts = new java.util.concurrent.atomic.AtomicInteger(0)
      r5 <- ZIO
               .attempt({
                 val n = attempts.incrementAndGet(); if (n < 3) throw new RuntimeException("retry") else "ok"
               })
               .retryN(3)

      _ <- ZIO.succeed(println(s"r1=$r1 r2=$r2 r3=$r3 r4=$r4 r5=$r5"))
    } yield ()
}
