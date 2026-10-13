package migratefrommonix

import zio._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Replacing the Application Entry Point
 *
 * Replaces: TaskApp { def run(args: List[String]): Task[ExitCode] }
 * With:     ZIOAppDefault { def run: Task[Unit] }
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step1EntryPoint"
 */
object Step1EntryPoint extends ZIOAppDefault {
  def run: Task[Unit] =
    ZIO.succeed(println("Hello from ZIOAppDefault"))
}
