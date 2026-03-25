package zio.zioapp.apps

import zio._

object DieApp extends ZIOAppDefault {
  def run = ZIO.die(new RuntimeException("defect"))
}
