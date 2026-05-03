package zio.concurrent

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A synchronization aid that allows a set of fibers to all wait for each other
 * to reach a common barrier point.
 *
 * CyclicBarriers are useful in programs involving a fixed sized party of fibers
 * that must occasionally wait for each other. The barrier is called cyclic
 * because it can be re-used after the waiting fibers are released.
 *
 * A CyclicBarrier supports an optional action command that is run once per
 * barrier point, after the last fiber in the party arrives, but before any
 * fibers are released. This barrier action is useful for updating shared-state
 * before any of the parties continue.
 */
final class CyclicBarrier private (
  /** The number of parties required to trip this barrier. */
  val parties: Int,
  _action: UIO[Any]
) {
  import CyclicBarrier.{newPromise, trace}

  @volatile private var _broken: Boolean           = false
  @volatile private var _lock: Promise[Unit, Unit] = newPromise()
  @volatile private var _waiting: Int              = 0

  private val semaphore = Semaphore.unsafe.make(1)(Unsafe)

  private def break: UIO[Unit] =
    semaphore.withPermit(ZIO.succeed {
      _broken = true
      _lock.unsafe.done(Exit.failUnit)(Unsafe)
      ()
    })

  private val succeedAndReset =
    semaphore.withPermit(ZIO.succeed {
      _lock.unsafe.done(Exit.unit)(Unsafe)
      resetUnsafe()
    })

  /**
   * Unsafely resets the barrier to its initial state. Breaks any waiting party.
   *
   * '''NOTE''': This method must only be called when holding a permit!
   */
  private def resetUnsafe(): Unit = {
    if (_waiting != 0) {
      _lock.unsafe.done(Exit.failUnit)(Unsafe)
      _waiting = 0
    }
    _lock = newPromise()
    _broken = false
  }

  /**
   * Waits until all parties have invoked await on this barrier. Fails if the
   * barrier is broken.
   */
  val await: IO[Unit, Int] =
    ZIO.uninterruptibleMask { restore =>
      semaphore.withPermit {
        ZIO.succeed {
          if (_broken) Exit.failUnit
          else {
            val nParties = parties
            val waiting = {
              val waiting0 = _waiting + 1
              if (waiting0 == nParties) _waiting = 0
              else _waiting = waiting0
              waiting0
            }
            val f =
              if (waiting == nParties) restore(_action) *> succeedAndReset
              else {
                val lock = _lock
                restore(lock.await).onInterrupt(break)
              }
            val remaining = nParties - waiting
            f.as(remaining)
          }
        }
      }.flatten
    }

  /** Queries if this barrier is in a broken state. */
  def isBroken: UIO[Boolean] =
    ZIO.succeed(_broken)

  /** The number of parties currently waiting at the barrier. */
  def waiting: UIO[Int] =
    ZIO.succeed(_waiting)

  /** Resets the barrier to its initial state. Breaks any waiting party. */
  def reset: UIO[Unit] =
    semaphore.withPermit(ZIO.succeed(resetUnsafe()))

}

object CyclicBarrier {
  private implicit def trace: Trace = Trace.empty

  def make(parties: Int): UIO[CyclicBarrier] =
    make(parties, Exit.unit)

  def make(parties: Int, action: UIO[Any]): UIO[CyclicBarrier] =
    ZIO.succeed(new CyclicBarrier(parties, action))

  private def newPromise(): Promise[Unit, Unit] =
    Promise.unsafe.make(FiberId.None)(Unsafe)
}
