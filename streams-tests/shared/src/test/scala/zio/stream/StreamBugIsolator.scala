package zio.stream

import zio._

object StreamBugIsolator extends ZIOAppDefault {
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    val N = 100000
    (1 to N)
      .foldLeft(ZChannel.write(1L)) { case (channel, n) =>
        channel.mapOut(_ + n)
      }
      .runCollect
      .debug("Result")
  }
}
