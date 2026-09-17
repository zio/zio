package migratefrommonix.monix

import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Managing Resource Lifecycles
 *
 * Monix bracket: resource.bracket(use)(release)
 * ZIO  bracket:  resource.bracket(release)(use)
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step4Resources"
 */
object Step4Resources extends TaskApp {
  case class DbConnection(id: Int) {
    def query(sql: String): Task[String] = Task.eval(s"conn-$id: $sql result")
    def close(): Task[Unit]              = Task.eval(println(s"Closing connection $id"))
  }

  def openConn(id: Int): Task[DbConnection] =
    Task.eval { println(s"Opening connection $id"); DbConnection(id) }

  def run(args: List[String]): Task[ExitCode] = {
    // Monix bracket(use)(release) — note argument order vs ZIO
    val program: Task[String] =
      openConn(1).bracket { conn1 =>
        openConn(2).bracket { conn2 =>
          conn1.query("SELECT 1")
        }(_.close())
      }(_.close())

    program.flatMap(r => Task.eval(println(s"Result: $r"))).as(ExitCode.Success)
  }
}
