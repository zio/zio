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

import zio.{UIO, ZIO, ZManaged}

/**
 * A `TSemaphore` is a semaphore that can be composed transactionally. Because
 * of the extremely high performance of ZIO's implementation of software
 * transactional memory `TSemaphore` can support both controlling access to
 * some resource on a standalone basis as well as composing with other STM
 * data structures to solve more advanced concurrency problems.
 *
 * For basic use cases, the most idiomatic way to work with a semaphore is to
 * use the `withPermit` operator, which acquires a permit before executing
 * some `ZIO` effect and release the permit immediately afterward. The permit
 * is guaranteed to be released immediately after the effect completes
 * execution, whether by success, failure, or interruption. Attempting to
 * acquire a permit when a sufficient number of permits are not available will
 * semantically block until permits become available without blocking any
 * underlying operating system threads. If you want to acquire more than one
 * permit at a time you can use `withPermits`, which allows specifying a
 * number of permits to acquire. You can also use `withPermitManaged` or
 * `withPermitsManaged` to acquire and release permits within the context of
 * a managed effect for composing with other resources.
 *
 * For more advanced concurrency problems you can use the `acquire` and
 * `release` operators directly, or their variants `acquireN` and `releaseN`,
 * all of which return STM transactions. Thus, they can be composed to form
 * larger STM transactions, for example acquiring permits from two different
 * semaphores transactionally and later releasing them transactionally to
 * safely synchronize on access to two different mutable variables.
 */
final class TSemaphore private (val permits: TRef[Long]) extends Serializable {

  /**
   * Acquires a single permit in transactional context.
   */
  def acquire: USTM[Unit] =
    acquireN(1L)

  /**
   * Acquires the specified number of permits in a transactional context.
   */
  def acquireN(n: Long): USTM[Unit] =
    ZSTM.Effect { (journal, _, _) =>
      assertNonNegative(n)

      val value = permits.unsafeGet(journal)

      if (value < n) throw ZSTM.RetryException
      else {
        permits.unsafeSet(journal, value - n)
      }
    }

  /**
   * Returns the number of available permits in a transactional context.
   */
  def available: USTM[Long] =
    permits.get

  /**
   * Releases a single permit in a transactional context.
   */
  def release: USTM[Unit] =
    releaseN(1L)

  /**
   * Releases the specified number of permits in a transactional context
   */
  def releaseN(n: Long): USTM[Unit] =
    ZSTM.Effect { (journal, _, _) =>
      assertNonNegative(n)

      val current = permits.unsafeGet(journal)
      permits.unsafeSet(journal, current + n)
    }

  /**
   * Executes the specified effect, acquiring a permit immediately before the
   * effect begins execution and releasing it immediately after the effect
   * completes execution, whether by success, failure, or interruption.
   */
  def withPermit[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    withPermits(1L)(zio)

  /**
   * Returns a managed effect that describes acquiring a permit as the
   * `acquire` action and releasing it as the `release` action.
   */
  val withPermitManaged: ZManaged[Any, Nothing, Unit] =
    withPermitsManaged(1L)

  /**
   * Executes the specified effect, acquiring the specified number of permits
   * immediately before the effect begins execution and releasing them
   * immediately after the effect completes execution, whether by success,
   * failure, or interruption.
   */
  def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.uninterruptibleMask(restore => restore(acquireN(n).commit) *> restore(zio).ensuring(releaseN(n).commit))

  /**
   * Returns a managed effect that describes acquiring the specified number of
   * permits as the `acquire` action and releasing them as the `release`
   * action.
   */
  def withPermitsManaged(n: Long): ZManaged[Any, Nothing, Unit] =
    ZManaged.makeInterruptible_(acquireN(n).commit)(release.commit)

  private def assertNonNegative(n: Long): Unit =
    require(n >= 0, s"Unexpected negative value `$n` passed to acquireN or releaseN.")
}

object TSemaphore {

  /**
   * Constructs a new `TSemaphore` with the specified number of permits.
   */
  def make(permits: Long): USTM[TSemaphore] =
    TRef.make(permits).map(v => new TSemaphore(v))

  /**
   * Constructs a new `TSemaphore` with the specified number of permits,
   * immediately committing the transaction.
   */
  def makeCommit(permits: Long): UIO[TSemaphore] =
    make(permits).commit
}
