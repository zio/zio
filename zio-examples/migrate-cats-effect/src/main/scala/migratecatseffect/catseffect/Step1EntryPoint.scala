package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Replacing the Application Entry Point
 *
 * The "before" side of migratecatseffect.Step1EntryPoint.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step1EntryPoint"
 */
object Step1EntryPoint extends IOApp.Simple {
  def run: IO[Unit] =
    IO(println("Application started under cats-effect runtime"))
}
