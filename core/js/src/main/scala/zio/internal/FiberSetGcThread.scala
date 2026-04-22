package zio.internal

import zio.Duration

/**
 * Stub implementation for Scala.js.
 *
 * Auto-GC is not supported on Scala.js as it lacks native thread support
 * and the Java reference queue API. GC must be triggered manually via
 * the `gc()` method.
 */
private object FiberSetGcThread {

  /**
   * No-op on Scala.js.
   *
   * @param set The FiberSet (ignored)
   * @param every The interval (ignored)
   */
  def start[A <: AnyRef](set: FiberSet[A], every: Duration): Unit = {
    // No-op: Auto-GC not supported on JS
    ()
  }
}
