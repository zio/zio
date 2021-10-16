package zio.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace

trait FiberRunnable extends Runnable {
  def runUntil(maxOpCount: Int): Unit
}
