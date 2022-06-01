package zio.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

// FIXME: NOt using this at present!
trait FiberRunnable extends Runnable {
  def location: Trace
  def runUntil(maxOpCount: Int): Unit
}
