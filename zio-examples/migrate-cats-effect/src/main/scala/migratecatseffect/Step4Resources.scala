package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Managing Resource Lifecycles
 *
 * Replaces:
 *   Resource.make(acq)(rel)       -> ZIO.acquireRelease(acq)(rel)
 *   resource.use(f)               -> ZIO.scoped { acquired.flatMap(f) }
 *   Resource.fromAutoCloseable    -> ZIO.fromAutoCloseable
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step4Resources"
 */
object Step4Resources extends ZIOAppDefault {

  case class DbConnection(id: Int) {
    def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
    def close: UIO[Unit]                 = ZIO.succeed(println(s"Closing connection $id"))
  }

  def makeDbConnection(id: Int): ZIO[Scope, Nothing, DbConnection] =
    ZIO.acquireRelease(
      ZIO.succeed { println(s"Opening connection $id"); DbConnection(id) }
    )(conn => conn.close)

  def run: Task[Unit] =
    ZIO.scoped {
      for {
        conn1  <- makeDbConnection(1)
        conn2  <- makeDbConnection(2)
        result <- conn1.query("SELECT 1")
        _      <- ZIO.succeed(println(s"Query result: $result"))
      } yield ()
    }
}
