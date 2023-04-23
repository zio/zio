package zio.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

private[zio] trait FiberRunnable extends Runnable {
  def getGreenThread()(implicit unsafe: Unsafe): Thread

  def location: Trace

  def run(depth: Int): Unit

  def setGreenThread(thread: Thread): Unit
}
