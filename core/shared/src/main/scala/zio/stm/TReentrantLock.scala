/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.stm

import TReentrantLock._

import zio.{ Fiber, Managed, UManaged, UIO }

/**
 * A `TReentrantLock` is a reentrant read/write lock. Multiple readers may all
 * concurrently acquire read locks. Only one writer is allowed to acquire a
 * write lock at any given time. Read locks may be upgraded into write locks.
 * A fiber that has a write lock may acquire other write locks or read locks.
 *
 * The two primary methods of this structure are `readLock`, which acquires a
 * read lock in a managed context, and `writeLock`, which acquires a write lock
 * in a managed context.
 *
 * Although located in the STM package, there is no need for locks within
 * STM transactions. However, this lock can be quite useful in effectful code,
 * to provide consistent read/write access to mutable state; and being in STM
 * allows this structure to be composed into more complicated concurrent
 * structures that are consumed from effectful code.
 */
final class TReentrantLock private (data: TRef[Either[ReadLock, WriteLock]]) {

  /**
   * Acquires a read lock. The transaction will suspend until no other fiber
   * is holding a write lock. Succeeds with the number of read locks held by
   * this fiber.
   */
  lazy val acquireRead: USTM[Int] =
    STM.fiberId.flatMap(fiberId => adjustRead(fiberId, 1))

  /**
   * Acquires a write lock. The transaction will suspend until no other
   * fibers are holding read or write locks. Succeeds with the number of
   * write locks held by this fiber.
   */
  lazy val acquireWrite: USTM[Int] =
    for {
      fiberId <- STM.fiberId
      w <- data.get.collect {
            case Left(readLock) if (readLock.noOtherHolder(fiberId)) =>
              WriteLock(1, readLock.readLocks(fiberId), fiberId)

            case Right(WriteLock(n, m, `fiberId`)) =>
              WriteLock(n + 1, m, fiberId)
          }
      _ <- data.set(Right(w))
    } yield w.writeLocks

  /**
   * Just a convenience method for applications that only need reentrant locks,
   * without needing a distinction between readers / writers.
   *
   * See [[writeLock]].
   */
  lazy val lock: UManaged[Int] = writeLock

  /**
   * Determines if any fiber has a read or write lock.
   */
  def locked: USTM[Boolean] =
    (readLocked zipWith writeLocked)(_ || _)

  /**
   * Obtains a read lock in a managed context.
   */
  lazy val readLock: UManaged[Int] =
    Managed.make(acquireRead.commit)(_ => releaseRead.commit)

  /**
   * Retrieves the number of acquired read locks.
   */
  def readLocks: USTM[Int] = data.get.map(_.fold(_.readLocks, _.readLocks))

  /**
   * Determines if any fiber has a read lock.
   */
  def readLocked: USTM[Boolean] = readLocks.map(_ > 0)

  /**
   * Releases a read lock held by this fiber. Succeeds with the outstanding
   * number of read locks held by this fiber.
   */
  lazy val releaseRead: USTM[Int] =
    STM.fiberId.flatMap(fiberId => adjustRead(fiberId, -1))

  /**
   * Releases a write lock held by this fiber. Succeeds with the outstanding
   * number of write locks held by this fiber.
   */
  lazy val releaseWrite: USTM[Int] =
    STM.fiberId.flatMap(fiberId =>
      data.modify {
        case Right(WriteLock(1, m, `fiberId`)) =>
          0 -> Left(ReadLock(fiberId, m))

        case Right(WriteLock(n, m, `fiberId`)) if n > 1 =>
          val newCount = n - 1

          newCount -> Right(WriteLock(newCount, m, fiberId))

        case s => die(s"Defect: Fiber ${fiberId} releasing write lock it does not hold: ${s}")
      }
    )

  /**
   * Obtains a write lock in a managed context.
   */
  lazy val writeLock: UManaged[Int] =
    Managed.make(acquireWrite.commit)(_ => releaseWrite.commit)

  /**
   * Determines if a write lock is held by some fiber.
   */
  def writeLocked: USTM[Boolean] = writeLocks.map(_ > 0)

  /**
   * Computes the number of write locks held by fibers.
   */
  def writeLocks: USTM[Int] = data.get.map(_.fold(_ => 0, _.writeLocks))

  private def adjustRead(fiberId: Fiber.Id, delta: Int): USTM[Int] =
    (data.get.collect {
      case Left(readLock) => Left(readLock.adjust(fiberId, delta))
      case Right(wl @ WriteLock(w, r, `fiberId`)) =>
        val newTotal = r + delta

        if (newTotal < 0) die(s"Defect: Fiber ${fiberId} releasing read locks it does not hold: ${wl}")
        else Right(WriteLock(w, newTotal, fiberId))
    }.flatMap(data.set(_)) *> data.get.map(_.fold(_.readLocks, _.readLocks)))
}
object TReentrantLock {

  /**
   * This data structure describes the state of the lock when a single fiber
   * has a write lock. The fiber has an identity, and may also have acquired
   * a certain number of read locks.
   */
  private[stm] final case class WriteLock(writeLocks: Int, readLocks: Int, fiberId: Fiber.Id)

  /**
   * This data structure describes the state of the lock when multiple fibers
   * have acquired read locks. The state is tracked as a map from fiber identity
   * to number of read locks acquired by the fiber. This level of detail permits
   * upgrading a read lock to a write lock.
   */
  private[stm] final class ReadLock private (readers: Map[Fiber.Id, Int]) {

    /**
     * Computes the total number of read locks acquired.
     */
    def readLocks: Int = readers.values.sum

    /**
     * Determines if there is no other holder of read locks aside from the
     * specified fiber id. If there are no other holders of read locks
     * aside from the specified fiber id, then it is safe to upgrade the
     * read lock into a write lock.
     */
    def noOtherHolder(fiberId: Fiber.Id): Boolean =
      readers.isEmpty || (readers.size == 1 && readers.contains(fiberId))

    /**
     * Computes the number of read locks held by the specified fiber id.
     */
    def readLocks(fiberId: Fiber.Id): Int = readers.get(fiberId).getOrElse(0)

    /**
     * Adjusts the number of read locks held by the specified fiber id.
     */
    def adjust(fiberId: Fiber.Id, adjust: Int): ReadLock = {
      val total = readLocks(fiberId)

      val newTotal = total + adjust

      new ReadLock(
        if (newTotal < 0) die(s"Defect: Fiber ${fiberId} releasing read lock it does not hold: ${readers}")
        else if (newTotal == 0) readers - fiberId
        else readers.updated(fiberId, newTotal)
      )
    }

  }
  private[stm] object ReadLock {

    /**
     * An empty read lock state, in which no fiber holds any read locks.
     */
    val empty: ReadLock = new ReadLock(Map())

    /**
     * Creates a new read lock where the specified fiber holds the
     * specified number of read locks.
     */
    def apply(fiberId: Fiber.Id, count: Int): ReadLock =
      if (count <= 0) empty else new ReadLock(Map(fiberId -> count))
  }

  /**
   * Makes a new reentrant read/write lock.
   */
  def make: USTM[TReentrantLock] =
    TRef.make[Either[ReadLock, WriteLock]](Left(ReadLock.empty)).map(tref => new TReentrantLock(tref))

  /**
   * Makes a new reentrant read/write lock.
   */
  val makeCommit: UIO[TReentrantLock] =
    make.commit

  private def die(message: String): Nothing =
    throw new RuntimeException(message)
}
