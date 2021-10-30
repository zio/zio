/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.concurrent

import zio.UIO
import zio.stm.TReentrantLock
import zio.UManaged
import zio.ZManaged

/**
 * A `ReentrantLock` is a reentrant read/write lock. Multiple readers may all
 * concurrently acquire read locks. Only one writer is allowed to acquire a
 * write lock at any given time. Read locks may be upgraded into write locks.
 * A fiber that has a write lock may acquire other write locks or read locks.
 *
 * The two primary methods of this structure are `readLock`, which acquires a
 * read lock in a managed context, and `writeLock`, which acquires a write lock
 * in a managed context.
 */
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
