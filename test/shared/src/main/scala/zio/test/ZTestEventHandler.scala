package zio.test

import zio.UIO

trait ZTestEventHandler {
  def handle(event: ExecutionEvent.Test[_]): UIO[Unit]
}
