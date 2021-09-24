package zio.internal

trait FiberRunnable extends Runnable {
  def runUntil(maxOpCount: Int): Unit
}
