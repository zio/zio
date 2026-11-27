/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

import zio.{Scope, UIO, Unsafe, Trace, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `TSemaphore` is a semaphore that can be composed transactionally. It holds
 * a number of permits, and its `acquire` and `release` operations are STM
 * transactions. It can control access to a resource on its own, or be combined
 * with other STM data structures in one transaction.
 *
 * For basic use cases, use `withPermit`, which acquires a permit before running
 * a `ZIO` effect and releases it after the effect completes, whether by
 * success, failure, or interruption. When not enough permits are available, the
 * acquiring transaction retries, so the fiber suspends until permits are
 * released, without blocking an operating system thread; the wait can be
 * interrupted. `withPermits` acquires several permits at a time, and
 * `withPermitScoped` and `withPermitsScoped` release the permits when a scope
 * closes.
 *
 * For more advanced concurrency problems, use `acquire` and `release`, or
 * `acquireN` and `releaseN`, directly. They return STM transactions, so they
 * can be composed into larger transactions, for example to acquire permits from
 * two semaphores in one transaction: if either semaphore lacks permits, neither
 * is changed and the transaction retries.
 *
 * {{{
 * import zio._
 *
 * val program: UIO[(Long, Long)] =
 *   for {
 *     left   <- TSemaphore.makeCommit(1L)
 *     right  <- TSemaphore.makeCommit(2L)
 *     _      <- (left.acquire *> right.acquire).commit
 *     during <- (left.available zip right.available).commit
 *     _      <- (left.release *> right.release).commit
 *     _      <- right.withPermit(ZIO.succeed("work"))
 *   } yield during
 * // (0,1)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/stm/tsemaphore/ TSemaphore]]
 */
final class TSemaphore private (val permits: TRef[Long]) extends Serializable {

  /**
   * Acquires a single permit. When no permit is available, the transaction
   * retries, so the fiber suspends until a permit is released.
   */
  def acquire: USTM[Unit] =
    acquireBetween(1L, 1L).unit

  /**
   * Acquires `n` permits. When fewer than `n` are available, the transaction
   * retries without acquiring any, so the fiber suspends until enough permits
   * are released. A negative `n` dies with a
   * `java.lang.IllegalArgumentException`.
   */
  def acquireN(n: Long): USTM[Unit] =
    acquireBetween(n, n).unit

  /**
   * Acquires as many available permits as possible, at least `min` and at most
   * `max`, and succeeds with the number acquired. When fewer than `min` are
   * available, the transaction retries, so the fiber suspends until enough
   * permits are released. Dies with a `java.lang.IllegalArgumentException` when
   * `min` is greater than `max` or either is negative.
   */
  def acquireBetween(min: Long, max: Long): USTM[Long] =
    ZSTM.Effect { (journal, _, _) =>
      require(min <= max, s"Unexpected `$min` > `$max` passed to acquireRange.")
      assertNonNegative(min)
      assertNonNegative(max)

      val available: Long = permits.unsafeGet(journal)
      if (available < min) {
        throw ZSTM.RetryException
      }
      val requested: Long = available.min(max)
      permits.unsafeSet(journal, available - requested)
      requested
    }

  /**
   * Acquires as many available permits as possible, at most `max`, and succeeds
   * with the number acquired, which is 0 when none are available. A negative
   * `max` dies with a `java.lang.IllegalArgumentException`.
   */
  def acquireUpTo(max: Long): USTM[Long] =
    acquireBetween(0, max)

  /**
   * Returns the number of available permits.
   */
  def available: USTM[Long] =
    permits.get

  /**
   * Releases a single permit, adding it to the available permits.
   */
  def release: USTM[Unit] =
    releaseN(1L)

  /**
   * Releases `n` permits, adding them to the available permits. A negative `n`
   * dies with a `java.lang.IllegalArgumentException`.
   */
  def releaseN(n: Long): USTM[Unit] =
    ZSTM.Effect { (journal, _, _) =>
      assertNonNegative(n)

      val current = permits.unsafeGet(journal)
      permits.unsafeSet(journal, current + n)
    }

  /**
   * Tries to acquire a single permit without waiting. Returns `true` if the
   * permit was acquired, otherwise `false`.
   */
  def tryAcquire: USTM[Boolean] = tryAcquireN(1L)

  /**
   * Tries to acquire `n` permits without waiting. Returns `true` if the permits
   * were acquired, otherwise `false`, in which case no permit is acquired. A
   * negative `n` dies with a `java.lang.IllegalArgumentException`.
   */
  def tryAcquireN(n: Long): USTM[Boolean] =
    ZSTM.Effect { (journal, _, _) =>
      assertNonNegative(n)

      val available: Long = permits.unsafeGet(journal)
      if (available >= n) {
        permits.unsafeSet(journal, available - n)
        true
      } else false
    }

  /**
   * Executes the specified effect, acquiring 1 permit if available and
   * releasing it after execution. Returns `None` without running the effect if
   * no permit was available.
   */
  def tryWithPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    tryWithPermits(1L)(zio)

  /**
   * Executes the specified effect, acquiring `n` permits if available and
   * releasing them after execution. Returns `None` without running the effect
   * if fewer than `n` permits were available.
   */
  def tryWithPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, Option[A]] =
    ZSTM.acquireReleaseWith(tryAcquireN(n))(releaseN(n).commit.whenDiscard(_))(zio.when(_))

  /**
   * Executes the specified effect, acquiring a permit immediately before the
   * effect begins execution and releasing it immediately after the effect
   * completes execution, whether by success, failure, or interruption.
   */
  def withPermit[R, E, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    withPermits(1L)(zio)

  /**
   * Returns a scoped effect that acquires a permit, waiting until one is
   * available, and releases it when the scope is closed.
   */
  def withPermitScoped(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    withPermitsScoped(1L)

  /**
   * Executes the specified effect, acquiring the specified number of permits
   * immediately before the effect begins execution and releasing them
   * immediately after the effect completes execution, whether by success,
   * failure, or interruption.
   */
  def withPermits[R, E, A](n: Long)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZSTM.acquireReleaseWith(acquireN(n))(_ => releaseN(n).commit)(_ => zio)

  /**
   * Executes the specified effect, acquiring at least `min` and at most `max`
   * permits immediately before the effect begins execution and releasing them
   * immediately after the effect completes execution, whether by success,
   * failure, or interruption.
   */
  def withPermitsBetween[R, E, A](min: Long, max: Long)(zio: Long => ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R, E, A] =
    ZSTM.acquireReleaseWith(acquireBetween(min, max))((actualN: Long) => releaseN(actualN).commit)(zio)

  /**
   * Executes the specified effect, acquiring at most the specified number of
   * permits immediately before the effect begins execution and releasing them
   * immediately after the effect completes execution, whether by success,
   * failure, or interruption.
   */
  def withPermitsUpTo[R, E, A](max: Long)(zio: Long => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZSTM.acquireReleaseWith(acquireUpTo(max))((actualN: Long) => releaseN(actualN).commit)(zio)

  /**
   * Returns a scoped effect that describes acquiring the specified number of
   * permits and releasing them when the scope is closed.
   */
  def withPermitsScoped(n: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Unit] =
    ZSTM.acquireReleaseWith(acquireN(n))(_ => Scope.addFinalizer(releaseN(n).commit))(_ => ZIO.unit)

  /**
   * Returns a scoped effect that describes acquiring at least `min` and at most
   * `max` permits and releasing them when the scope is closed.
   */
  def withPermitsBetweenScoped(min: Long, max: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Long] =
    ZSTM.acquireReleaseWith(acquireBetween(min, max))((actualN: Long) => Scope.addFinalizer(releaseN(actualN).commit))(
      ZIO.succeed(_)
    )

  /**
   * Returns a scoped effect that describes acquiring at most `max` permits and
   * releasing them when the scope is closed.
   */
  def withPermitsUpToScoped(max: Long)(implicit trace: Trace): ZIO[Scope, Nothing, Long] =
    ZSTM.acquireReleaseWith(acquireUpTo(max))((actualN: Long) => Scope.addFinalizer(releaseN(actualN).commit))(
      ZIO.succeed(_)
    )

  private def assertNonNegative(n: Long): Unit =
    require(n >= 0, s"Unexpected negative `$n` permits requested.")
}

/**
 * Constructors for [[TSemaphore]].
 */
object TSemaphore {

  /**
   * Constructs a new `TSemaphore` with the specified number of permits.
   */
  def make(permits: => Long)(implicit trace: Trace): USTM[TSemaphore] =
    ZSTM.succeed(unsafe.make(permits)(Unsafe.unsafe))

  /**
   * Constructs a new `TSemaphore` with the specified number of permits,
   * immediately committing the transaction.
   */
  def makeCommit(permits: => Long)(implicit trace: Trace): UIO[TSemaphore] =
    make(permits).commit

  /**
   * Constructors that run outside of a transaction and need an implicit
   * `Unsafe`.
   */
  object unsafe {

    /**
     * Creates a `TSemaphore` with the specified number of permits right away,
     * outside of a transaction.
     */
    def make(permits: Long)(implicit unsafe: Unsafe): TSemaphore =
      new TSemaphore(TRef.unsafeMake(permits))
  }
}
