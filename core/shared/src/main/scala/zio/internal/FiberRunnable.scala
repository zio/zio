package zio.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] trait FiberRunnable extends Runnable {
  def location: Trace
}
