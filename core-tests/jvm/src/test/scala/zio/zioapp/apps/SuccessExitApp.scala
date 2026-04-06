package zio.zioapp.apps

import zio._

/**
 * Simplest happy path: prints a marker and exits successfully.
 */
object SuccessExitApp extends ZIOAppDefault {
  val run: ZIO[Any, Nothing, Unit] =
    Console.printLine("APP_READY").orDie
}
