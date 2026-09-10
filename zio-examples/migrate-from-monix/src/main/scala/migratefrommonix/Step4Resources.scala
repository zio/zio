package migratefrommonix

import zio._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Managing Resource Lifecycles
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step4Resources"
 */
object Step4Resources extends ZIOAppDefault {
  case class DbConnection(id: Int) {
    def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
    def close: UIO[Unit]                 = ZIO.succeed(println(s"Closing connection $id"))
  }

  def openConn(id: Int): Task[DbConnection] =
    ZIO.attempt { println(s"Opening connection $id"); DbConnection(id) }

  def makeConn(id: Int): ZIO[Scope, Throwable, DbConnection] =
    ZIO.acquireRelease(openConn(id))(_.close)

  def run: Task[Unit] = {
    // Flat multi-resource pattern with ZIO.scoped
    val program: Task[String] =
      ZIO.scoped {
        for {
          conn1  <- makeConn(1)
          conn2  <- makeConn(2)
          result <- conn1.query("SELECT 1")
        } yield result
      }

    // ensuring — replace guarantee
    val withFinalizer: Task[String] =
      ZIO.attempt("work").ensuring(ZIO.succeed(println("finalizer always runs")))

    for {
      r1 <- program
      r2 <- withFinalizer
      _  <- ZIO.succeed(println(s"program=$r1 withFinalizer=$r2"))
    } yield ()
  }
}
