package zio.zioapp.apps

import zio._

object FailureApp extends ZIOAppDefault {
  def run = ZIO.fail("boom")
}
