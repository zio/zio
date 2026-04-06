package zio.zioapp.apps

import zio._

object DefectExitApp extends ZIOAppDefault {
  val run = ZIO.succeed(println("APP_READY")) *> ZIO.die(new RuntimeException("fatal error"))
}
