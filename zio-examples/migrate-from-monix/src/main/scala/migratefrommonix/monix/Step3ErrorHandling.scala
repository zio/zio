package migratefrommonix.monix

import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Mapping the Error Channel
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step3ErrorHandling"
 */
object Step3ErrorHandling extends TaskApp {
  sealed abstract class AppError(msg: String) extends RuntimeException(msg)
  case class DbError(msg: String) extends AppError(msg)

  val failedQuery: Task[String] =
    Task.raiseError(new RuntimeException("connection refused"))

  def run(args: List[String]): Task[ExitCode] =
    for {
      // onErrorHandleWith — recover from any Throwable
      r1 <- failedQuery.onErrorHandleWith(e => Task.now(s"recovered: ${e.getMessage}"))

      // redeem — pure handlers on both branches
      r2 <- failedQuery.redeem(e => s"failed: ${e.getMessage}", a => s"ok: $a")

      // attempt — materialize failure
      r3 <- failedQuery.attempt

      // onErrorRestart — retry N times
      r4 <- Task
               .eval({ var n = 0; n += 1; if (n < 2) throw new RuntimeException("retry") else "ok" })
               .onErrorRestart(3)

      _ <- Task.eval(println(s"r1=$r1 r2=$r2 r3=$r3 r4=$r4"))
    } yield ExitCode.Success
}
