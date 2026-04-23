package zio.internal

import zio.internal.FiberSet.IsAlive
import zio.Duration

/**
 * A lock-free concurrent weak set optimized for fiber tracking.
 *
 * Entries are wrapped in [[java.lang.ref.WeakReference]] immediately on
 * [[add]], enabling GC reclamation of completed or unreachable fibers without
 * explicit [[remove]] calls.
 *
 * No [[java.util.concurrent.locks.ReentrantLock]] or monitor-based locking is
 * used.
 */
private[zio] abstract class FiberSetPlatformSpecific[A <: AnyRef](
  initialCapacity: Int,
  isAlive: IsAlive[A],
  autoGcEvery: Option[Duration]
) {

  /**
   * Adds `a` to the set, wrapping it in a weak reference immediately.
   * Idempotent for identical references.
   */
  def add(a: A): Unit

  /** Removes `a` from the set by identity. No-op if not present. */
  def remove(a: A): Unit

  /**
   * Returns a weakly-consistent iterator. Skips GC'd entries and tombstoned
   * slots. Never throws [[java.util.ConcurrentModificationException]].
   */
  def iterator: Iterator[A]

  def isEmpty: Boolean

  def size: Int

  /**
   * Drains the dead-reference queue and sweeps tombstoned slots. When `force`
   * is true, drains without batch cap (used in tests and shutdown).
   */
  def gc(force: Boolean): Unit

  def withAutoGc(every: Duration): FiberSet[A]
}
