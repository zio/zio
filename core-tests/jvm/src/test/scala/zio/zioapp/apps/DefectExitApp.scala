package zio.zioapp.apps

import zio._

/**
 * App that dies (unrecoverable defect) after printing a marker.
 * Expected: exit code 1.
 */
object DefectExitApp extends ZIOAppDefault {
  val run: ZIO[Any, Nothing, Nothing] =
    Console.printLine("APP_READY").orDie *> ZIO.die(new RuntimeException("fatal error"))
}
