package zio.concurrent

import zio.UIO
import zio.stm.TReentrantLock
import zio.UManaged
import zio.ZManaged

trait ReentrantLock {

  /**
   * Acquires a read lock. This fiber will be suspended until no other fiber
   * is holding a write lock. Succeeds with the number of read locks held by this fiber.
   */
  def acquireRead: UIO[Int]

  /**
   * Acquires a write lock. This fiber will be suspended until no other
   * fibers are holding read or write locks. Succeeds with the number of
   * write locks held by this fiber.
   */
  def acquireWrite: UIO[Int]

  /**
   * Just a convenience method for applications that only need reentrant locks,
   * without needing a distinction between readers / writers.
   *
   * See [[writeLock]].
   */
  def lock: UManaged[Int] = writeLock

  /**
   * Determines if any fiber has a read or write lock.
   */
  def locked: UIO[Boolean]

  /**
   * Obtains a read lock in a managed context.
   */
  def readLock: UManaged[Int] = ZManaged.make(acquireRead)(_ => releaseRead)

  /**
   * Retrieves the total number of acquired read locks.
   */
  def readLocks: UIO[Int]

  /**
   * Retrieves the number of acquired read locks for this fiber.
   */
  def fiberReadLocks: UIO[Int]

  /**
   * Retrieves the number of acquired write locks for this fiber.
   */
  def fiberWriteLocks: UIO[Int]

  /**
   * Determines if any fiber has a read lock.
   */
  def readLocked: UIO[Boolean]

  /**
   * Releases a read lock held by this fiber. Succeeds with the outstanding
   * number of read locks held by this fiber.
   */
  def releaseRead: UIO[Int]

  /**
   * Releases a write lock held by this fiber. Succeeds with the outstanding
   * number of write locks held by this fiber.
   */
  def releaseWrite: UIO[Int]

  /**
   * Obtains a write lock in a managed context.
   */
  def writeLock: UManaged[Int] = ZManaged.make(acquireWrite)(_ => releaseWrite)

  /**
   * Determines if a write lock is held by some fiber.
   */
  def writeLocked: UIO[Boolean]

  /**
   * Computes the number of write locks held by fibers.
   */
  def writeLocks: UIO[Int]
}

object ReentrantLock {
  val make: UIO[ReentrantLock] = TReentrantLock.make
    .map(wrapped =>
      new ReentrantLock {
        override def acquireRead: UIO[Int]     = wrapped.acquireRead.commit
        override def acquireWrite: UIO[Int]    = wrapped.acquireWrite.commit
        override def fiberReadLocks: UIO[Int]  = wrapped.fiberReadLocks.commit
        override def fiberWriteLocks: UIO[Int] = wrapped.fiberWriteLocks.commit
        override def locked: UIO[Boolean]      = wrapped.locked.commit
        override def readLocks: UIO[Int]       = wrapped.readLocks.commit
        override def readLocked: UIO[Boolean]  = wrapped.readLocked.commit
        override def releaseRead: UIO[Int]     = wrapped.releaseRead.commit
        override def releaseWrite: UIO[Int]    = wrapped.releaseWrite.commit
        override def writeLocked: UIO[Boolean] = wrapped.writeLocked.commit
        override def writeLocks: UIO[Int]      = wrapped.writeLocks.commit
      }
    )
    .commit
}
