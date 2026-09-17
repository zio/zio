package migratefrommonix.monix

import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Replacing the Application Entry Point
 *
 * Replaces: TaskApp { def run(args: List[String]): Task[ExitCode] }
 * With:     ZIOAppDefault { def run: Task[Unit] }
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step1EntryPoint"
 */
object Step1EntryPoint extends TaskApp {
  def run(args: List[String]): Task[ExitCode] =
    Task.eval(println("Hello from Monix TaskApp")).as(ExitCode.Success)
}
