package zio.internal

import zio.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Background garbage collection thread for FiberSet.
 *
 * This daemon thread wakes up on a specified interval and triggers
 * garbage collection of dead references in the FiberSet. It runs
 * at low priority to minimize impact on application threads.
 *
 * @param set The FiberSet to collect
 * @param interval The interval between GC runs
 */
private final class FiberSetGcThread[A <: AnyRef](
  set: FiberSet[A],
  interval: Duration
) extends Thread(s"zio.internal.FiberSet.GcThread-${FiberSetGcThread.idGen.incrementAndGet()}") {

  // Run as daemon so it doesn't prevent JVM shutdown
  setDaemon(true)

  // Set low priority to minimize impact on application threads
  setPriority(Thread.MIN_PRIORITY)

  override def run(): Unit = {
    try {
      while (!isInterrupted) {
        // Sleep for the specified interval
        Thread.sleep(interval.toMillis)

        // Trigger GC (polls reference queue and removes dead refs)
        set.gc()
      }
    } catch {
      case _: InterruptedException =>
        // Thread was interrupted, exit gracefully
        Thread.currentThread().interrupt()
    }
  }
}

private object FiberSetGcThread {
  // Atomic counter for generating unique thread names
  private val idGen = new AtomicInteger(0)

  /**
   * Start a background GC thread for the given FiberSet.
   *
   * @param set The FiberSet to collect
   * @param every The interval between GC runs
   */
  def start[A <: AnyRef](set: FiberSet[A], every: Duration): Unit = {
    val thread = new FiberSetGcThread(set, every)
    thread.start()
  }
}
