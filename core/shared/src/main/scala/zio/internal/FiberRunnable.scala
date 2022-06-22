package zio.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

trait FiberRunnable extends Runnable {
  def location: Trace
}
