package zio.zioapp.apps

import zio._

object FailureExitApp extends ZIOAppDefault {
  val run = ZIO.succeed(println("APP_READY")) *> ZIO.fail("something went wrong")
}
