package zio.zioapp.apps

import zio._

object SuccessExitApp extends ZIOAppDefault {
  val run = ZIO.succeed(println("APP_READY")) *> ZIO.succeed(42)
}
