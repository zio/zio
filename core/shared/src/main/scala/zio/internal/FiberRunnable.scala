package zio.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] trait FiberRunnable extends Runnable {
  def location: Trace

  def run(depth: Int): Unit

  def setCurrentThread(thread: Thread): Unit
}
