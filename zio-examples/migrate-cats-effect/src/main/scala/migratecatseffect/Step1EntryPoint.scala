package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Replacing the Application Entry Point
 *
 * Replaces: IOApp.Simple { def run: IO[Unit] }
 * With:     ZIOAppDefault { def run: Task[Unit] }
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step1EntryPoint"
 */
object Step1EntryPoint extends ZIOAppDefault {
  def run: Task[Unit] =
    ZIO.succeed(println("Application started under ZIO runtime"))
}
