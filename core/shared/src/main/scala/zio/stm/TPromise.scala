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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.Unsafe

/**
 * A `TPromise` is a promise that is completed inside STM transactions. It
 * starts empty and is completed once, with a success of type `A` or a failure
 * of type `E`. A transaction that awaits an empty promise retries, so the fiber
 * waits until another transaction completes the promise.
 *
 * {{{
 * import zio._
 *
 * val answer: IO[String, Int] =
 *   for {
 *     promise <- TPromise.make[String, Int].commit
 *     fiber   <- promise.await.commit.fork
 *     _       <- promise.succeed(42).commit
 *     result  <- fiber.join
 *   } yield result
 * // 42
 * }}}
 *
 * @param ref
 *   the `TRef` that holds the result: `None` until the promise is completed,
 *   then `Some(Right(a))` for a success or `Some(Left(e))` for a failure
 *
 * @see
 *   [[https://zio.dev/reference/stm/tpromise/ TPromise]]
 */
final class TPromise[E, A] private (val ref: TRef[Option[Either[E, A]]]) extends AnyVal {

  /**
   * Returns the value of the promise, or fails with its error when it was
   * completed with a failure. When the promise is not completed yet, the
   * transaction retries, so the fiber waits until another transaction completes
   * it; inside `orElse`, the alternative runs instead.
   */
  def await: STM[E, A] =
    ref.get.collect { case Some(e) =>
      STM.fromEither(e)
    }.flatten

  /**
   * Completes the promise with `v` (`Right` for a success, `Left` for a
   * failure) and returns `true`. When the promise is already completed, leaves
   * it unchanged and returns `false`.
   */
  def done(v: Either[E, A]): USTM[Boolean] =
    ref.get.flatMap {
      case Some(_) => STM.succeedNow(false)
      case None    => ref.set(Some(v)) *> STM.succeedNow(true)
    }

  /**
   * Completes the promise with the failure `e` and returns `true`. When the
   * promise is already completed, leaves it unchanged and returns `false`.
   */
  def fail(e: E): USTM[Boolean] =
    done(Left(e))

  /**
   * Returns the result of the promise, or `None` when it is not completed yet.
   * Unlike `await`, it does not retry.
   */
  def poll: USTM[Option[Either[E, A]]] =
    ref.get

  /**
   * Completes the promise with the success `a` and returns `true`. When the
   * promise is already completed, leaves it unchanged and returns `false`.
   */
  def succeed(a: A): USTM[Boolean] =
    done(Right(a))
}

/**
 * Constructors for [[TPromise]].
 */
object TPromise {

  /**
   * Makes a new empty `TPromise`.
   */
  def make[E, A]: USTM[TPromise[E, A]] =
    ZSTM.succeed(unsafe.make()(Unsafe.unsafe))

  /**
   * Constructors that make a `TPromise` outside a transaction. They need an
   * `Unsafe` capability.
   */
  object unsafe {

    /**
     * Makes a new empty `TPromise` without running a transaction.
     */
    def make[E, A]()(implicit unsafe: Unsafe): TPromise[E, A] =
      new TPromise(TRef.unsafeMake[Option[Either[E, A]]](None))
  }
}
