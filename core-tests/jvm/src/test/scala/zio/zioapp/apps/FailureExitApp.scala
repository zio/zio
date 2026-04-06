package zio.zioapp.apps

import zio._

/**
 * App that fails after printing a marker. Expected: exit code 1.
 */
object FailureExitApp extends ZIOAppDefault {
  val run: ZIO[Any, String, Nothing] =
    Console.printLine("APP_READY").orDie *> ZIO.fail("something went wrong")
}
