package zio.internal

import zio.Duration

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

private final class FiberSetGc private (
  set: FiberSet,
  sleepFor: Duration
) extends Thread {
  override def run(): Unit = {
    val sleepForNanos = sleepFor.toNanos
    while (!isInterrupted) {
      LockSupport.parkNanos(sleepForNanos)
      set.gc()
    }
  }
}

private object FiberSetGc {
  private val i = new AtomicInteger(0)

  def start(set: FiberSet, every: Duration): Unit = {
    assert(every.toMillis >= 1000, "Auto-gc interval must be >= 1 second")

    val thread = new FiberSetGc(set, every)
    thread.setName(s"zio.internal.FiberSet.GcThread-${i.getAndIncrement()}")
    thread.setPriority(4)
    thread.setDaemon(true)
    thread.start()
  }
}
