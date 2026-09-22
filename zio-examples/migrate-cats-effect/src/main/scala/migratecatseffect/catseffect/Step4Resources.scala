package migratecatseffect.catseffect

import cats.effect.{IO, IOApp, Resource}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Managing Resource Lifecycles
 *
 * The "before" side of migratecatseffect.Step4Resources — note the nested
 * .use calls, which the ZIO version flattens into one for-comprehension
 * inside a single ZIO.scoped block.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step4Resources"
 */
object Step4Resources extends IOApp.Simple {

  case class DbConnection(id: Int) {
    def query(sql: String): IO[String] = IO(s"conn-$id: $sql result")
    def close(): IO[Unit]              = IO(println(s"Closing connection $id"))
  }

  def makeDbConnection(id: Int): Resource[IO, DbConnection] =
    Resource.make(
      IO(println(s"Opening connection $id")).as(DbConnection(id))
    )(conn => conn.close())

  def run: IO[Unit] =
    makeDbConnection(1).use { conn1 =>
      makeDbConnection(2).use { conn2 =>
        conn1.query("SELECT 1").flatMap(result => IO(println(s"Query result: $result")))
      }
    }
}
