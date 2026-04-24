package zio.test

import zio.{UIO, ZIO}

trait ZTestEventHandler {
  def handle(event: ExecutionEvent): UIO[Unit]
}
object ZTestEventHandler {
  val silent: ZTestEventHandler = _ => ZIO.unit
}
