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
import zio._

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.collection.{SortedSet, immutable}
import scala.collection.mutable.TreeMap
import scala.util.control.ControlThrowable
import scala.util.{Failure, Success, Try}

/**
 * A `ZSTM[R, E, A]` is a transaction: a description of reads and writes of
 * transactional values such as [[TRef]] that needs an environment `R`, and that
 * fails with an `E`, succeeds with an `A`, or retries. `STM[E, A]`, `USTM[A]`
 * and the other aliases in the `zio.stm` package fix some of the type
 * parameters.
 *
 * Nothing runs until the transaction is committed with `commit` or
 * `STM.atomically`, which turn it into a `ZIO`. The writes of a successful
 * transaction are committed together, so other transactions see all of them or
 * none of them; `orElse` describes a case where the writes made before it are
 * not committed. A transaction that fails or dies commits none of its writes.
 * When another transaction changes a `TRef` that this one has read before this
 * one commits, this one runs again from the start, so code inside `STM.succeed`
 * can run more than once. The iterators that `ZSTM.foreachDiscard`,
 * `ZSTM.collectAllDiscard`, `ZSTM.replicateSTMDiscard`, `ZSTM.exists`,
 * `ZSTM.forall` and `ZSTM.collectFirst` create when they are called are the
 * exception: a new run goes on from where the previous run stopped.
 *
 * `STM.retry`, and the operators built on it such as `STM.check`, `retryUntil`
 * and `retryWhile`, abandon the current run. The fiber that commits the
 * transaction then waits, without holding a thread, until one of the `TRef`s
 * the transaction has read changes, and runs the transaction again. `orTry` and
 * `orElse` run an alternative instead of waiting.
 *
 * {{{
 * import zio._
 *
 * def transfer(from: TRef[Int], to: TRef[Int], amount: Int): USTM[Unit] =
 *   for {
 *     balance <- from.get
 *     _       <- STM.check(balance >= amount)
 *     _       <- from.update(_ - amount)
 *     _       <- to.update(_ + amount)
 *   } yield ()
 *
 * val balances: UIO[(Int, Int)] =
 *   for {
 *     alice <- TRef.make(100).commit
 *     bob   <- TRef.make(0).commit
 *     _     <- transfer(alice, bob, 30).commit
 *     a     <- alice.get.commit
 *     b     <- bob.get.commit
 *   } yield (a, b)
 * // (70,30)
 * }}}
 *
 * When `from` holds less than `amount`, `transfer(...).commit` waits until
 * another fiber adds enough to `from`.
 *
 * The API follows the Haskell package `Control.Concurrent.STM`; the
 * implementation is different.
 * [[http://hackage.haskell.org/package/stm-2.5.0.0/docs/Control-Concurrent-STM.html]]
 *
 * STM in Haskell was introduced in: Composable memory transactions, by Tim
 * Harris, Simon Marlow, Simon Peyton Jones, and Maurice Herlihy, in ACM
 * Conference on Principles and Practice of Parallel Programming 2005.
 * [[https://www.microsoft.com/en-us/research/publication/composable-memory-transactions/]]
 *
 * See also: Lock Free Data Structures using STMs in Haskell, by Anthony
 * Discolo, Tim Harris, Simon Marlow, Simon Peyton Jones and Satnam Singh, FLOPS
 * 2006: Eighth International Symposium on Functional and Logic Programming,
 * Fuji Susono, Japan, April 2006.
 * [[https://www.microsoft.com/en-us/research/publication/lock-free-data-structures-using-stms-in-haskell/]]
 *
 * @see
 *   [[https://zio.dev/reference/stm/ Software Transactional Memory]]
 */
sealed trait ZSTM[-R, +E, +A] extends Serializable { self =>
  import ZSTM._
  import ZSTM.internal.{Journal, TExit}

  /**
   * A symbolic alias for `orDie`.
   */
  final def !(implicit ev: E <:< Throwable, ev2: CanFail[E]): ZSTM[R, Nothing, A] =
    self.orDie

  /**
   * Sequentially zips this value with the specified one, discarding the first
   * element of the tuple.
   */
  def *>[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, B] =
    self zipRight that

  /**
   * Sequentially zips this value with the specified one, discarding the second
   * element of the tuple.
   */
  def <*[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, A] =
    self zipLeft that

  /**
   * Sequentially zips this value with the specified one.
   */
  def <*>[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B])(implicit
    zippable: Zippable[A, B]
  ): ZSTM[R1, E1, zippable.Out] =
    self zip that

  /**
   * A symbolic alias for `orElseEither`.
   */
  def <+>[R1 <: R, E1, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, Either[A, B]] =
    self.orElseEither(that)

  /**
   * Tries this effect first, and if it fails or retries, discards its writes
   * and tries the other effect. A symbolic alias for `orElse`, which describes
   * when the writes made before it are not committed.
   */
  def <>[R1 <: R, E1, A1 >: A](that: => ZSTM[R1, E1, A1]): ZSTM[R1, E1, A1] =
    orElse(that)

  /**
   * Tries this effect first, and if it retries, tries the other effect. A
   * failure of this effect is not caught. The writes this effect made before it
   * retried are kept, so the other effect sees them. A symbolic alias for
   * `orTry`.
   */
  def <|>[R1 <: R, E1 >: E, A1 >: A](that: => ZSTM[R1, E1, A1]): ZSTM[R1, E1, A1] =
    orTry(that)

  /**
   * Returns an effect that submerges the error case of an `Either` into the
   * `STM`. The inverse operation of `either`.
   */
  def absolve[E1 >: E, B](implicit ev: A <:< Either[E1, B]): ZSTM[R, E1, B] =
    ZSTM.absolve(self.map(ev))

  /**
   * Maps the success value of this effect to the specified constant value.
   */
  def as[B](b: => B): ZSTM[R, E, B] = self map (_ => b)

  /**
   * Maps the success value of this effect to an optional value.
   */
  def asSome: ZSTM[R, E, Option[A]] =
    map(Some(_))

  /**
   * Maps the error value of this effect to an optional value.
   */
  def asSomeError: ZSTM[R, Option[E], A] =
    mapError(Some(_))

  /**
   * Recovers from a failure of this transaction (a value passed to `STM.fail`)
   * by running the transaction that `h` returns for it. It does not catch
   * `STM.retry` or defects (`STM.die`, or an exception thrown inside `map` or
   * `STM.succeed`). The writes made before the failure are kept, so `h` sees
   * them; `orElse` discards them instead.
   *
   * {{{
   * val recovered: USTM[String] =
   *   STM.fail("boom").catchAll(e => STM.succeed("recovered from " + e))
   * // commit returns "recovered from boom"
   * }}}
   */
  def catchAll[R1 <: R, E2, A1 >: A](h: E => ZSTM[R1, E2, A1])(implicit ev: CanFail[E]): ZSTM[R1, E2, A1] =
    OnFailure(self, h)

  /**
   * Recovers from some or all of the error cases.
   */
  def catchSome[R1 <: R, E1 >: E, A1 >: A](
    pf: PartialFunction[E, ZSTM[R1, E1, A1]]
  )(implicit ev: CanFail[E]): ZSTM[R1, E1, A1] =
    catchAll(pf.applyOrElse(_, (e: E) => ZSTM.fail(e)))

  /**
   * Simultaneously filters and maps the value produced by this effect. When
   * `pf` is not defined for the value, the transaction retries.
   */
  def collect[B](pf: PartialFunction[A, B]): ZSTM[R, E, B] =
    collectSTM(pf.andThen(ZSTM.succeedNow(_)))

  /**
   * Simultaneously filters and flatMaps the value produced by this effect.
   * Continues on the effect returned from `pf`. When `pf` is not defined for
   * the value, the transaction retries.
   */
  def collectSTM[R1 <: R, E1 >: E, B](pf: PartialFunction[A, ZSTM[R1, E1, B]]): ZSTM[R1, E1, B] =
    foldSTM(ZSTM.fail(_), (a: A) => if (pf.isDefinedAt(a)) pf(a) else ZSTM.retry)

  /**
   * Returns a `ZIO` that runs this transaction and commits its writes together,
   * the same as `STM.atomically(this)`. When the transaction fails or dies,
   * none of its writes are committed. When it retries, the fiber waits until
   * one of the `TRef`s it has read changes, then runs it again. After `orElse`
   * runs its alternative, the writes made before the `orElse` can be left out
   * of the commit: see `orElse`.
   */
  def commit(implicit trace: Trace): ZIO[R, E, A] = ZSTM.atomically(self)

  /**
   * Commits this transaction atomically, regardless of whether the transaction
   * is a success or a failure. Unlike `commit`, the writes made before a
   * failure are committed, and then the returned `ZIO` fails with the error.
   */
  def commitEither(implicit trace: Trace): ZIO[R, E, A] =
    either.commit.absolve

  /**
   * Repeats this `STM` effect until its result satisfies the specified
   * predicate. '''WARNING''': `repeatUntil` uses a busy loop to repeat the
   * effect and will consume a thread until it completes (it cannot yield). This
   * is because STM describes a single atomic transaction which must either
   * complete, retry or fail a transaction before yielding back to the ZIO
   * Runtime.
   *   - Use [[retryUntil]] instead if you don't need to maintain transaction
   *     state for repeats.
   *   - Ensure repeating the STM effect will eventually satisfy the predicate.
   *   - Consider using the Blocking thread pool for execution of the
   *     transaction.
   */
  def repeatUntil(f: A => Boolean): ZSTM[R, E, A] =
    flatMap(a => if (f(a)) ZSTM.succeedNow(a) else repeatUntil(f))

  /**
   * Repeats this `STM` effect while its result satisfies the specified
   * predicate. '''WARNING''': `repeatWhile` uses a busy loop to repeat the
   * effect and will consume a thread until it completes (it cannot yield). This
   * is because STM describes a single atomic transaction which must either
   * complete, retry or fail a transaction before yielding back to the ZIO
   * Runtime.
   *   - Use [[retryWhile]] instead if you don't need to maintain transaction
   *     state for repeats.
   *   - Ensure repeating the STM effect will eventually not satisfy the
   *     predicate.
   *   - Consider using the Blocking thread pool for execution of the
   *     transaction.
   */
  def repeatWhile(f: A => Boolean): ZSTM[R, E, A] =
    flatMap(a => if (f(a)) repeatWhile(f) else ZSTM.succeedNow(a))

  /**
   * Converts the failure channel into an `Either`: a failure `e` becomes
   * `Left(e)` and a success `a` becomes `Right(a)`. `STM.retry` and defects are
   * not converted.
   */
  def either(implicit ev: CanFail[E]): URSTM[R, Either[E, A]] =
    fold(Left(_), Right(_))

  /**
   * Runs the specified finalization transaction after this effect succeeds or
   * fails. It does not run when this effect retries or dies. As with every STM
   * transaction, when the whole transaction ends in a failure, `commit`
   * discards all of its writes, including those of the finalizer.
   */
  def ensuring[R1 <: R](finalizer: ZSTM[R1, Nothing, Any]): ZSTM[R1, E, A] =
    foldSTM(e => finalizer *> ZSTM.fail(e), a => finalizer *> ZSTM.succeedNow(a))

  /**
   * Returns an effect that ignores errors and runs repeatedly until it
   * eventually succeeds. The repeats happen inside the same transaction without
   * waiting, and the writes made by a failed run are kept. `STM.retry` is not
   * caught.
   */
  def eventually(implicit ev: CanFail[E]): URSTM[R, A] =
    foldSTM(_ => eventually, ZSTM.succeedNow)

  /**
   * Dies with specified `Throwable` if the predicate fails.
   */
  def filterOrDie(p: A => Boolean)(t: => Throwable): ZSTM[R, E, A] =
    filterOrElse(p)(ZSTM.die(t))

  /**
   * Dies with a [[java.lang.RuntimeException]] having the specified text
   * message if the predicate fails.
   */
  def filterOrDieMessage(p: A => Boolean)(msg: => String): ZSTM[R, E, A] =
    filterOrElse(p)(ZSTM.dieMessage(msg))

  /**
   * Supplies `zstm` if the predicate fails.
   */
  def filterOrElse[R1 <: R, E1 >: E, A1 >: A](p: A => Boolean)(zstm: => ZSTM[R1, E1, A1]): ZSTM[R1, E1, A1] =
    filterOrElseWith[R1, E1, A1](p)(_ => zstm)

  /**
   * Applies `f` if the predicate fails.
   */
  def filterOrElseWith[R1 <: R, E1 >: E, A1 >: A](p: A => Boolean)(f: A => ZSTM[R1, E1, A1]): ZSTM[R1, E1, A1] =
    flatMap(v => if (!p(v)) f(v) else ZSTM.succeedNow(v))

  /**
   * Fails with `e` if the predicate fails.
   */
  def filterOrFail[E1 >: E](p: A => Boolean)(e: => E1): ZSTM[R, E1, A] =
    filterOrElse[R, E1, A](p)(ZSTM.fail(e))

  /**
   * Feeds the value produced by this effect to the specified function, and then
   * runs the returned effect as well to produce its results. A failure or retry
   * of this effect skips `f`.
   *
   * {{{
   * def withdraw(account: TRef[Int], amount: Int): STM[String, Int] =
   *   account.get.flatMap { balance =>
   *     if (balance >= amount) account.update(_ - amount).as(balance - amount)
   *     else STM.fail("insufficient funds")
   *   }
   * // with 50 in the account, withdraw(account, 20).commit returns 30
   * }}}
   */
  def flatMap[R1 <: R, E1 >: E, B](f: A => ZSTM[R1, E1, B]): ZSTM[R1, E1, B] =
    OnSuccess(self, f)

  /**
   * Creates a composite effect that represents this effect followed by another
   * one that may depend on the error produced by this one.
   */
  def flatMapError[R1 <: R, E2](f: E => ZSTM[R1, Nothing, E2])(implicit ev: CanFail[E]): ZSTM[R1, E2, A] =
    foldSTM(e => f(e).flip, ZSTM.succeedNow)

  /**
   * Flattens out a nested `STM` effect.
   */
  def flatten[R1 <: R, E1 >: E, B](implicit ev: A <:< ZSTM[R1, E1, B]): ZSTM[R1, E1, B] =
    self flatMap ev

  /**
   * Unwraps the optional error, defaulting to the provided value.
   */
  def flattenErrorOption[E1, E2 <: E1](default: => E2)(implicit ev: E <:< Option[E1]): ZSTM[R, E1, A] =
    mapError(e => ev(e).getOrElse(default))

  /**
   * Flips the success and failure channels of this transactional effect. This
   * allows you to use all methods on the error channel, possibly before
   * flipping back.
   */
  def flip: ZSTM[R, A, E] =
    foldSTM(ZSTM.succeedNow, ZSTM.fail(_))

  /**
   * Swaps the error/value parameters, applies the function `f` and flips the
   * parameters back.
   */
  def flipWith[R1, A1, E1](f: ZSTM[R, A, E] => ZSTM[R1, A1, E1]): ZSTM[R1, E1, A1] =
    f(flip).flip

  /**
   * Folds over the `STM` effect, handling both failure and success, but not
   * retry. The result is `f(e)` for a failure `e` and `g(a)` for a success `a`;
   * `STM.retry` and defects pass through.
   */
  def fold[B](f: E => B, g: A => B)(implicit ev: CanFail[E]): URSTM[R, B] =
    foldSTM(f andThen ZSTM.succeedNow, g andThen ZSTM.succeedNow)

  /**
   * Effectfully folds over the `STM` effect, handling both failure and success,
   * but not retry.
   */
  def foldSTM[R1 <: R, E1, B](f: E => ZSTM[R1, E1, B], g: A => ZSTM[R1, E1, B])(implicit
    ev: CanFail[E]
  ): ZSTM[R1, E1, B] =
    self
      .map(Right(_))
      .catchAll(f(_).map(Left(_)))
      .flatMap {
        case Left(b)  => ZSTM.succeedNow(b)
        case Right(a) => g(a)
      }

  /**
   * Returns a successful effect with the head of the list if the list is
   * non-empty or fails with the error `None` if the list is empty.
   */
  def head[B](implicit ev: A <:< List[B]): ZSTM[R, Option[E], B] =
    foldSTM(
      e => ZSTM.fail(Some(e)),
      ev(_).headOption.fold[ZSTM[R, Option[E], B]](ZSTM.fail(None))(ZSTM.succeedNow)
    )

  /**
   * Returns a new effect that ignores the success or failure of this effect.
   */
  def ignore: URSTM[R, Unit] = self.fold(ZIO.unitFn, ZIO.unitFn)

  /**
   * Returns whether this transactional effect is a failure.
   */
  def isFailure: ZSTM[R, Nothing, Boolean] =
    fold(_ => true, _ => false)

  /**
   * Returns whether this transactional effect is a success.
   */
  def isSuccess: ZSTM[R, Nothing, Boolean] =
    fold(_ => false, _ => true)

  /**
   * "Zooms in" on the value in the `Left` side of an `Either`, moving the
   * possibility that the value is a `Right` to the error channel.
   */
  def left[B, C](implicit ev: A IsSubtypeOfOutput Either[B, C]): ZSTM[R, Either[E, C], B] =
    self.foldSTM(
      e => ZSTM.fail(Left(e)),
      a => ev(a).fold(b => ZSTM.succeedNow(b), c => ZSTM.fail(Right(c)))
    )

  /**
   * Maps the value produced by the effect with `f`. Failures and retries pass
   * through unchanged. An exception thrown by `f` makes the transaction die.
   */
  def map[B](f: A => B): ZSTM[R, E, B] = flatMap(f andThen ZSTM.succeedNow)

  /**
   * Maps the value produced by the effect with the specified function that may
   * throw exceptions but is otherwise pure, translating non-fatal thrown
   * exceptions into typed failed effects.
   */
  def mapAttempt[B](f: A => B)(implicit ev: E IsSubtypeOfError Throwable): ZSTM[R, Throwable, B] =
    foldSTM(e => ZSTM.fail(ev(e)), a => ZSTM.attempt(f(a)))

  /**
   * Returns an `STM` effect whose failure and success channels have been mapped
   * by the specified pair of functions, `f` and `g`.
   */
  def mapBoth[E2, B](f: E => E2, g: A => B)(implicit ev: CanFail[E]): ZSTM[R, E2, B] =
    foldSTM(e => ZSTM.fail(f(e)), a => ZSTM.succeedNow(g(a)))

  /**
   * Maps from one error type to another.
   */
  def mapError[E1](f: E => E1)(implicit ev: CanFail[E]): ZSTM[R, E1, A] =
    foldSTM(e => ZSTM.fail(f(e)), ZSTM.succeedNow)

  /**
   * Returns a new effect where the error channel has been merged into the
   * success channel to their common combined type.
   */
  def merge[A1 >: A](implicit ev1: E <:< A1, ev2: CanFail[E]): URSTM[R, A1] =
    foldSTM(e => ZSTM.succeedNow(ev1(e)), ZSTM.succeedNow)

  /**
   * Requires the option produced by this value to be `None`.
   */
  def none[B](implicit ev: A <:< Option[B]): ZSTM[R, Option[E], Unit] =
    self.foldSTM(
      e => ZSTM.fail(Some(e)),
      _.fold[ZSTM[R, Option[E], Unit]](ZSTM.unit)(_ => ZSTM.fail(None))
    )

  /**
   * Converts the failure channel into an `Option`.
   */
  def option(implicit ev: CanFail[E]): URSTM[R, Option[A]] =
    fold(_ => None, Some(_))

  /**
   * Translates `STM` effect failure into death of the fiber, making all
   * failures unchecked and not a part of the type of the effect.
   */
  def orDie(implicit ev1: E IsSubtypeOfError Throwable, ev2: CanFail[E]): URSTM[R, A] =
    orDieWith(ev1)

  /**
   * Keeps none of the errors, and terminates the fiber running the `STM` effect
   * with them, using the specified function to convert the `E` into a
   * `Throwable`.
   */
  def orDieWith(f: E => Throwable)(implicit ev: CanFail[E]): URSTM[R, A] =
    mapError(f).catchAll(ZSTM.die(_))

  /**
   * Runs this transaction, and runs `that` instead when this one fails or
   * retries. The writes this transaction made are discarded first, so `that`
   * starts from the same state. When `that` also fails or retries, so does the
   * result. When this transaction retries and `that` then fails, `that` runs a
   * second time before the failure is returned. Named alias for `<>`.
   *
   * After `that` starts, the writes that the transaction made before this
   * `orElse` are not committed unless a `TRef` is later set to a value that is
   * not the same object (`eq`) as the value it holds, and nothing signals that
   * they were left out. This happens when the transaction reads or writes two
   * or more `TRef`s, or when it commits after the fiber waited on a retry for a
   * `TRef` to change. A transaction with a single `TRef` that commits without
   * having waited keeps the writes. For example, with an empty `TQueue` `queue`
   * and a `TRef` `c` holding 0, `(c.set(5) *>
   * queue.take.orElseSucceed(-1)).commit` returns -1 and leaves `c` at 0.
   *
   * {{{
   * def takeEither(first: TQueue[Int], second: TQueue[Int]): USTM[Int] =
   *   first.take orElse second.take
   * // with `first` empty and 7 in `second`, commit returns 7
   * }}}
   */
  def orElse[R1 <: R, E1, A1 >: A](that: => ZSTM[R1, E1, A1]): ZSTM[R1, E1, A1] =
    Effect[Any, Nothing, () => Unit]((journal, _, _) => journal.resetFn()).flatMap { reset =>
      self.orTry(ZSTM.succeed(reset()) *> that).catchAll(_ => ZSTM.succeed(reset()) *> that)
    }

  /**
   * Returns a transactional effect that will produce the value of this effect
   * in left side, unless it fails or retries, in which case, it will produce
   * the value of the specified effect in right side. It is built on `orElse`,
   * which describes when the writes made before it are not committed.
   */
  def orElseEither[R1 <: R, E1, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, Either[A, B]] =
    (self map (Left[A, B](_))) orElse (that map (Right[A, B](_)))

  /**
   * Tries this effect first, and if it fails or retries, fails with the
   * specified error. It is built on `orElse`, which describes when the writes
   * made before it are not committed.
   */
  def orElseFail[E1](e1: => E1): ZSTM[R, E1, A] =
    orElse(ZSTM.fail(e1))

  /**
   * Returns an effect that will produce the value of this effect, unless it
   * fails with the `None` value, in which case it will produce the value of the
   * specified effect.
   */
  def orElseOptional[R1 <: R, E1, A1 >: A](that: => ZSTM[R1, Option[E1], A1])(implicit
    ev: E <:< Option[E1]
  ): ZSTM[R1, Option[E1], A1] =
    catchAll(ev(_).fold(that)(e => ZSTM.fail(Some(e))))

  /**
   * Tries this effect first, and if it fails or retries, succeeds with the
   * specified value. It is built on `orElse`, which describes when the writes
   * made before it are not committed.
   */
  def orElseSucceed[A1 >: A](a1: => A1): URSTM[R, A1] =
    orElse(ZSTM.succeedNow(a1))

  /**
   * Runs this transaction, and runs `that` instead when this one retries. A
   * failure of this transaction is not caught. The writes this transaction made
   * before it retried are kept: `that` sees them, and they are committed when
   * `that` succeeds. When `that` also retries, the result retries. For example,
   * `(STM.retry orTry STM.succeed("fallback")).commit` returns `"fallback"`.
   * Named alias for `<|>`.
   */
  def orTry[R1 <: R, E1 >: E, A1 >: A](that: => ZSTM[R1, E1, A1]): ZSTM[R1, E1, A1] =
    OnRetry(self, that)

  /**
   * Provides the transaction its required environment, which eliminates its
   * dependency on `R`.
   */
  def provideEnvironment(r: ZEnvironment[R]): STM[E, A] =
    provideSomeEnvironment(_ => r)

  /**
   * Transforms the environment being provided to this effect with the specified
   * function.
   */
  def provideSomeEnvironment[R0](f: ZEnvironment[R0] => ZEnvironment[R]): ZSTM[R0, E, A] =
    Provide(self, f)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest.
   */
  def refineOrDie[E1](
    pf: PartialFunction[E, E1]
  )(implicit ev1: E IsSubtypeOfError Throwable, ev2: CanFail[E]): ZSTM[R, E1, A] =
    refineOrDieWith(pf)(ev1)

  /**
   * Keeps some of the errors, and terminates the fiber with the rest, using the
   * specified function to convert the `E` into a `Throwable`.
   */
  def refineOrDieWith[E1](pf: PartialFunction[E, E1])(f: E => Throwable)(implicit ev: CanFail[E]): ZSTM[R, E1, A] =
    self.catchAll(err => (pf.lift(err)).fold[ZSTM[R, E1, A]](ZSTM.die(f(err)))(ZSTM.fail(_)))

  /**
   * Fail with the returned value if the `PartialFunction` matches, otherwise
   * continue with our held value.
   */
  def reject[E1 >: E](pf: PartialFunction[A, E1]): ZSTM[R, E1, A] =
    rejectSTM(pf.andThen(ZSTM.fail(_)))

  /**
   * Continue with the returned computation if the `PartialFunction` matches,
   * translating the successful match into a failure, otherwise continue with
   * our held value.
   */
  def rejectSTM[R1 <: R, E1 >: E](pf: PartialFunction[A, ZSTM[R1, E1, E1]]): ZSTM[R1, E1, A] =
    self.flatMap { v =>
      pf.andThen[ZSTM[R1, E1, A]](_.flatMap(ZSTM.fail(_)))
        .applyOrElse[A, ZSTM[R1, E1, A]](v, ZSTM.succeedNow)
    }

  /**
   * Filters the value produced by this effect, retrying the transaction until
   * the predicate returns true for the value. Each time the predicate returns
   * false, the transaction retries (see `ZSTM.retry`).
   *
   * {{{
   * def awaitAtLeast(counter: TRef[Int], n: Int): USTM[Int] =
   *   counter.get.retryUntil(_ >= n)
   * }}}
   */
  def retryUntil(f: A => Boolean): ZSTM[R, E, A] =
    collect {
      case a if f(a) => a
    }

  /**
   * Filters the value produced by this effect, retrying the transaction while
   * the predicate returns true for the value. The result is the first value for
   * which the predicate returns false.
   */
  def retryWhile(f: A => Boolean): ZSTM[R, E, A] =
    collect {
      case a if !f(a) => a
    }

  /**
   * "Zooms in" on the value in the `Right` side of an `Either`, moving the
   * possibility that the value is a `Left` to the error channel.
   */
  final def right[B, C](implicit ev: A IsSubtypeOfOutput Either[B, C]): ZSTM[R, Either[B, E], C] =
    self.foldSTM(
      e => ZSTM.fail(Right(e)),
      a => ev(a).fold(b => ZSTM.fail(Left(b)), c => ZSTM.succeedNow(c))
    )

  /**
   * Converts an option on values into an option on errors.
   */
  def some[B](implicit ev: A <:< Option[B]): ZSTM[R, Option[E], B] =
    self.foldSTM(
      e => ZSTM.fail(Some(e)),
      _.fold[ZSTM[R, Option[E], B]](ZSTM.fail(Option.empty[E]))(ZSTM.succeedNow)
    )

  /**
   * Extracts the optional value, or returns the given 'default'. Superseded by
   * `someOrElse` with better type inference. This method was left for binary
   * compatibility.
   */
  protected def someOrElse[B](default: => B)(implicit ev: A <:< Option[B]): ZSTM[R, E, B] =
    map(_.getOrElse(default))

  /**
   * Extracts the optional value, or returns the given 'default'.
   */
  def someOrElse[B, C](default: => C)(implicit ev0: A <:< Option[B], ev1: C <:< B): ZSTM[R, E, B] =
    map(_.getOrElse(default))

  /**
   * Extracts the optional value, or executes the effect 'default'. Superseded
   * by `someOrElseSTM` with better type inference. This method was left for
   * binary compatibility.
   */
  protected def someOrElseSTM[B, R1 <: R, E1 >: E](
    default: ZSTM[R1, E1, B]
  )(implicit ev: A <:< Option[B]): ZSTM[R1, E1, B] =
    self.flatMap(ev(_) match {
      case Some(value) => ZSTM.succeedNow(value)
      case None        => default
    })

  /**
   * Extracts the optional value, or executes the effect 'default'.
   */
  def someOrElseSTM[B, R1 <: R, E1 >: E, C](
    default: ZSTM[R1, E1, C]
  )(implicit ev0: A <:< Option[B], ev1: C <:< B): ZSTM[R1, E1, B] =
    self.flatMap(ev0(_) match {
      case Some(value) => ZSTM.succeedNow(value)
      case None        => default.map(ev1)
    })

  /**
   * Extracts the optional value, or fails with the given error 'e'.
   */
  def someOrFail[B, E1 >: E](e: => E1)(implicit ev: A <:< Option[B]): ZSTM[R, E1, B] =
    flatMap(_.fold[ZSTM[R, E1, B]](ZSTM.fail(e))(ZSTM.succeedNow))

  /**
   * Extracts the optional value, or fails with a
   * [[java.util.NoSuchElementException]].
   */
  def someOrFailException[B, E1 >: E](implicit
    ev: A <:< Option[B],
    ev2: NoSuchElementException <:< E1
  ): ZSTM[R, E1, B] =
    foldSTM(
      ZSTM.fail(_),
      _.fold[ZSTM[R, E1, B]](ZSTM.fail(ev2(new NoSuchElementException("None.get"))))(ZSTM.succeedNow)
    )

  /**
   * Summarizes a `STM` effect by computing a provided value before and after
   * execution, and then combining the values to produce a summary, together
   * with the result of execution.
   */
  def summarized[R1 <: R, E1 >: E, B, C](summary: ZSTM[R1, E1, B])(f: (B, B) => C): ZSTM[R1, E1, (C, A)] =
    for {
      start <- summary
      value <- self
      end   <- summary
    } yield (f(start, end), value)

  /**
   * Returns the internal number that identifies the kind of node this
   * transaction is built from. Deprecated: match on the type instead.
   */
  @deprecated("use pattern-matching on types instead", "2.1.8")
  def tag: Int

  /**
   * "Peeks" at the success of transactional effect.
   */
  def tap[R1 <: R, E1 >: E](f: A => ZSTM[R1, E1, Any]): ZSTM[R1, E1, A] =
    flatMap(a => f(a).as(a))

  /**
   * "Peeks" at both sides of a transactional effect.
   */
  def tapBoth[R1 <: R, E1 >: E](f: E => ZSTM[R1, E1, Any], g: A => ZSTM[R1, E1, Any])(implicit
    ev: CanFail[E]
  ): ZSTM[R1, E1, A] =
    foldSTM(e => f(e) *> ZSTM.fail(e), a => g(a) as a)

  /**
   * "Peeks" at the error of the transactional effect.
   */
  def tapError[R1 <: R, E1 >: E](f: E => ZSTM[R1, E1, Any])(implicit ev: CanFail[E]): ZSTM[R1, E1, A] =
    foldSTM(e => f(e) *> ZSTM.fail(e), ZSTM.succeedNow)

  /**
   * Maps the success value of this effect to unit.
   */
  def unit: ZSTM[R, E, Unit] = as(())

  /**
   * The moral equivalent of `if (!b) exp`: runs this effect only when `b` is
   * false and returns its result in `Some`, or returns `None` without running
   * it when `b` is true.
   */
  def unless(b: => Boolean): ZSTM[R, E, Option[A]] =
    ZSTM.unless(b)(self)

  /**
   * The moral equivalent of `if (!b) exp` when `b` is a transaction: runs `b`
   * first, then runs this effect only when `b` returned false, returning its
   * result in `Some`, or `None` otherwise.
   */
  def unlessSTM[R1 <: R, E1 >: E](b: ZSTM[R1, E1, Boolean]): ZSTM[R1, E1, Option[A]] =
    ZSTM.unlessSTM(b)(self)

  /**
   * Converts a `ZSTM[R, Either[E, B], A]` into a `ZSTM[R, E, Either[A, B]]`.
   * The inverse of `left`.
   */
  final def unleft[E1, B](implicit ev: E IsSubtypeOfError Either[E1, B]): ZSTM[R, E1, Either[A, B]] =
    self.foldSTM(
      e => ev(e).fold(e1 => ZSTM.fail(e1), b => ZSTM.succeedNow(Right(b))),
      a => ZSTM.succeedNow(Left(a))
    )

  /**
   * Converts a `ZSTM[R, Either[B, E], A]` into a `ZSTM[R, E, Either[B, A]]`.
   * The inverse of `right`.
   */
  final def unright[E1, B](implicit ev: E IsSubtypeOfError Either[B, E1]): ZSTM[R, E1, Either[B, A]] =
    self.foldSTM(
      e => ev(e).fold(b => ZSTM.succeedNow(Left(b)), e1 => ZSTM.fail(e1)),
      a => ZSTM.succeedNow(Right(a))
    )

  /**
   * Converts an option on errors into an option on values.
   */
  def unsome[E1](implicit ev: E <:< Option[E1]): ZSTM[R, E1, Option[A]] =
    foldSTM(
      _.fold[ZSTM[R, E1, Option[A]]](ZSTM.succeedNow(Option.empty[A]))(ZSTM.fail(_)),
      a => ZSTM.succeedNow(Some(a))
    )

  /**
   * Updates a service in the environment of this effect.
   */
  def updateService[M] =
    new ZSTM.UpdateService[R, E, A, M](self)

  /**
   * Updates a service at the specified key in the environment of this effect.
   */
  final def updateServiceAt[Service]: ZSTM.UpdateServiceAt[R, E, A, Service] =
    new ZSTM.UpdateServiceAt[R, E, A, Service](self)

  /**
   * The moral equivalent of `if (b) exp`: runs this effect only when `b` is
   * true and returns its result in `Some`, or returns `None` without running it
   * when `b` is false.
   */
  def when(b: => Boolean): ZSTM[R, E, Option[A]] =
    ZSTM.when(b)(self)

  /**
   * The moral equivalent of `if (b) exp` when `b` is a transaction: runs `b`
   * first, then runs this effect only when `b` returned true, returning its
   * result in `Some`, or `None` otherwise.
   */
  def whenSTM[R1 <: R, E1 >: E](b: ZSTM[R1, E1, Boolean]): ZSTM[R1, E1, Option[A]] =
    ZSTM.whenSTM(b)(self)

  /**
   * Same as [[retryUntil]]. It makes an `if` guard in a for-comprehension retry
   * the transaction while the guard is false.
   */
  def withFilter(f: A => Boolean): ZSTM[R, E, A] = retryUntil(f)

  /**
   * Named alias for `<*>`.
   */
  def zip[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B])(implicit
    zippable: Zippable[A, B]
  ): ZSTM[R1, E1, zippable.Out] =
    (self zipWith that)((a, b) => zippable.zip(a, b))

  /**
   * Named alias for `<*`.
   */
  def zipLeft[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, A] =
    (self zip that) map (_._1)

  /**
   * Named alias for `*>`.
   */
  def zipRight[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, B] =
    (self zip that) map (_._2)

  /**
   * Sequentially zips this value with the specified one, combining the values
   * using the specified combiner function.
   */
  def zipWith[R1 <: R, E1 >: E, B, C](that: => ZSTM[R1, E1, B])(f: (A, B) => C): ZSTM[R1, E1, C] =
    self flatMap (a => that map (b => f(a, b)))

  private def run(journal: Journal, fiberId: FiberId, r0: ZEnvironment[R]): TExit[E, A] = {
    import internal._

    type Erased = ZSTM[Any, Any, Any]
    type Cont   = Any => Erased

    var contStack     = List.empty[Cont]
    var onCommitStack = List.empty[ZIO[Any, Nothing, Any]]
    var env           = r0.asInstanceOf[ZEnvironment[Any]]
    var exit          = null.asInstanceOf[TExit[Any, Any]]
    var curr          = self.asInstanceOf[Erased]
    var opCount       = 0

    def unwindStack(stack: List[Cont], error: Any, isRetry: Boolean): (List[Cont], Erased) = {
      var result = null.asInstanceOf[Erased]
      var rem    = stack

      while ((rem ne Nil) && (result eq null)) {
        rem.head match {
          case OnFailure(_, onFailure) => if (!isRetry) result = onFailure.asInstanceOf[Cont].apply(error)
          case OnRetry(_, onRetry)     => if (isRetry) result = onRetry
          case _                       =>
        }
        rem = rem.tail
      }

      (rem, result)
    }

    while (exit eq null) {
      if (opCount == YieldOpCount) {
        if (journal.isInvalid) exit = TExit.Retry
        else opCount = 0
      } else {
        try {
          curr match {
            case effect: Effect[Any, Any, Any] =>
              val a = effect.f(journal, fiberId, env)
              if (contStack eq Nil) exit = TExit.Succeed(a, onCommitStack)
              else {
                curr = contStack.head(a)
                contStack = contStack.tail
              }

            case onSuccess: OnSuccess[Any, Any, Any, Any] =>
              contStack ::= onSuccess.k
              curr = onSuccess.stm

            case onFailure: OnFailure[Any, Any, Any, Any] =>
              contStack ::= onFailure
              curr = onFailure.stm

            case onRetry: OnRetry[Any, Any, Any] =>
              contStack ::= onRetry
              curr = onRetry.stm

            case provide: Provide[Any, Any, Any, Any] =>
              val oldEnv = env
              env = provide.f(oldEnv)

              val cleanup = ZSTM.succeed({ env = oldEnv })

              curr = provide.effect.ensuring(cleanup)

            case succeedNow0: SucceedNow[Any] =>
              val a = succeedNow0.a

              if (contStack eq Nil) exit = TExit.Succeed(a, onCommitStack)
              else {
                curr = contStack.head(a)
                contStack = contStack.tail
              }

            case succeed0: Succeed[Any] =>
              val a = succeed0.a()

              if (contStack eq Nil) exit = TExit.Succeed(a, onCommitStack)
              else {
                curr = contStack.head(a)
                contStack = contStack.tail
              }

            case onCommit: OnCommit[Any] =>
              onCommitStack ::= onCommit.zio.provideEnvironment(env)(onCommit.trace)
              if (contStack eq Nil) exit = TExit.Succeed((), onCommitStack)
              else {
                curr = contStack.head(())
                contStack = contStack.tail
              }
          }
        } catch {
          case ZSTM.RetryException =>
            val (newStack, newCurr) = unwindStack(contStack, null, true)
            curr = newCurr
            contStack = newStack
            if (curr eq null) exit = TExit.Retry
          case ZSTM.FailException(e) =>
            val (newStack, newCurr) = unwindStack(contStack, e, false)
            curr = newCurr
            contStack = newStack
            if (curr eq null) exit = TExit.Fail(e, onCommitStack)
          case ZSTM.DieException(t) =>
            exit = TExit.Die(t, onCommitStack)
          case ZSTM.InterruptException(fiberId) =>
            exit = TExit.Interrupt(fiberId, onCommitStack)
          case t: Throwable =>
            exit = TExit.Die(t, onCommitStack)
        }

        opCount += 1
      }
    }

    exit.asInstanceOf[TExit[E, A]]
  }
}

/**
 * Constructors and combinators for [[ZSTM]] transactions: lifting values,
 * errors and defects into a transaction, retrying, running transactions over
 * collections, reading the environment, and committing a transaction with
 * `atomically`. `STM` has versions of most of them with an `Any` environment.
 *
 * @see
 *   [[https://zio.dev/reference/stm/ Software Transactional Memory]]
 */
object ZSTM {
  import internal._

  /**
   * Submerges the error case of an `Either` into the transaction: a `Left(e)`
   * result fails with `e` and a `Right(a)` result succeeds with `a`. The
   * inverse of `ZSTM#either`.
   */
  def absolve[R, E, A](z: ZSTM[R, E, Either[E, A]]): ZSTM[R, E, A] =
    z.flatMap(fromEither(_))

  /**
   * Treats the `acquire` transaction as the acquisition of a resource, and
   * returns a `ZSTM.Acquire` that takes the `release` workflow and then the
   * `use` workflow: `acquireReleaseWith(acquire)(release)(use)`.
   *
   * The `acquire` transaction is committed interruptibly, so a fiber waiting on
   * its retry can be interrupted. When `acquire` commits successfully, `use`
   * runs interruptibly, and `release` runs uninterruptibly after `use`
   * succeeds, fails or is interrupted. When `acquire` fails, or the fiber is
   * interrupted while `acquire` waits, `release` does not run.
   *
   * {{{
   * import zio._
   *
   * def withPermit[A](permits: TRef[Int])(work: UIO[A]): UIO[A] =
   *   ZSTM.acquireReleaseWith(
   *     permits.get.flatMap(n => ZSTM.check(n > 0) *> permits.set(n - 1))
   *   )(_ => permits.update(_ + 1).commit)(_ => work)
   * }}}
   */
  def acquireReleaseWith[R, E, A](acquire: ZSTM[R, E, A]): ZSTM.Acquire[R, E, A] =
    new ZSTM.Acquire[R, E, A](() => acquire)

  /**
   * Commits the transaction: returns a `ZIO` effect that runs it. The changes
   * the transaction makes to `TRef`s become visible to other fibers together
   * when it succeeds, and none of them are committed when it fails or dies.
   * When the alternative of an `orElse` runs and no `TRef` is later set to a
   * value that is not the same object (`eq`) as its current value, the writes
   * made before the `orElse` can be left out of the commit (see `ZSTM#orElse`).
   * When the transaction retries, the fiber waits until one of the `TRef`s it
   * read changes, then runs it again. The transaction can run several times
   * before it commits, so it should not perform side effects. `stm.commit` is
   * the same operation.
   *
   * {{{
   * import zio._
   *
   * val balances: UIO[(Int, Int)] =
   *   for {
   *     from <- TRef.makeCommit(100)
   *     to   <- TRef.makeCommit(0)
   *     _    <- ZSTM.atomically(from.update(_ - 30) *> to.update(_ + 30))
   *     both <- ZSTM.atomically(from.get.zip(to.get))
   *   } yield both
   * // (70,30)
   * }}}
   */
  def atomically[R, E, A](stm: ZSTM[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    unsafeAtomically(stm)(_ => (), () => ())

  private def commitEffects(onCommit: List[UIO[Any]])(implicit trace: Trace): UIO[Any] =
    onCommit match {
      case Nil        => Exit.unit
      case one :: Nil => one
      case many =>
        val it = many.reverseIterator
        ZIO.whileLoop(it.hasNext)(it.next())(_ => ())
    }

  private def unsafeAtomically[R, E, A](
    stm: ZSTM[R, E, A]
  )(onDone: Exit[E, A] => Any, onInterrupt: () => Any)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withFiberRuntime[R, E, A] { (fiberState, _) =>
      implicit val unsafe: Unsafe = Unsafe

      val executor = fiberState.getCurrentExecutor()
      val r        = fiberState.getFiberRef(FiberRef.currentEnvironment).asInstanceOf[ZEnvironment[R]]
      val fiberId  = fiberState.id

      tryCommitSync(fiberId, stm, null, r, executor) match {
        case TryCommit.Done(exit, onCommit) =>
          onDone(exit)
          commitEffects(onCommit) *> exit
        case TryCommit.Suspend(journal) =>
          val txId  = TxnId.make()
          val state = new AtomicReference[State[E, A]](State.Running)
          val async = ZIO.async(tryCommitAsync(journal, executor, fiberId, stm, txId, state, r))

          ZIO.uninterruptibleMask { restore =>
            restore(async).foldCauseZIO(
              cause => {
                state.compareAndSet(State.Running, State.Interrupted)
                state.get match {
                  case State.Done(exit, onCommit) =>
                    onDone(exit)
                    commitEffects(onCommit) *> exit
                  case _ =>
                    onInterrupt()
                    Exit.failCause(cause)
                }
              },
              s => {
                val exit = Exit.succeed(s)
                onDone(exit)
                exit
              }
            )
          }
      }
    }

  /**
   * Returns a transaction that evaluates `a` each time it runs, and succeeds
   * with its value or fails with the non-fatal exception it throws (caught with
   * `scala.util.Try`). `a` should not have side effects.
   */
  def attempt[A](a: => A): STM[Throwable, A] =
    fromTry(Try(a))

  /**
   * Succeeds with `Unit` when `p` is `true`, and retries the transaction
   * otherwise (see `retry`). `p` is evaluated each time the transaction runs,
   * so the fiber that commits the transaction waits until a `TRef` it read
   * changes and `p` becomes `true`.
   *
   * {{{
   * def withdraw(balance: TRef[Int], amount: Int): USTM[Unit] =
   *   for {
   *     current <- balance.get
   *     _       <- ZSTM.check(current >= amount)
   *     _       <- balance.set(current - amount)
   *   } yield ()
   * }}}
   */
  def check[R](p: => Boolean): URSTM[R, Unit] =
    suspend(if (p) STM.unit else retry)

  /**
   * Applies `f` to each element from left to right and collects the results of
   * the elements where `f` succeeds, skipping the elements where `f` fails with
   * `None`. When `f` fails with `Some(e)`, the transaction fails with `e`.
   */
  def collect[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(f: A => ZSTM[R, Option[E], B])(implicit bf: BuildFrom[Collection[A], B, Collection[B]]): ZSTM[R, E, Collection[B]] =
    foreach[R, E, A, Option[B], Iterable](in)(a => f(a).unsome).map(_.flatten).map(bf.fromSpecific(in))

  /**
   * Runs the transactions in the collection from left to right and collects
   * their results into a collection of the same type.
   *
   * {{{
   * import zio._
   *
   * val values: UIO[List[Int]] =
   *   for {
   *     a   <- TRef.makeCommit(1)
   *     b   <- TRef.makeCommit(2)
   *     all <- ZSTM.collectAll(List(a.get, b.get, a.get)).commit
   *   } yield all
   * // List(1, 2, 1)
   * }}}
   */
  def collectAll[R, E, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[ZSTM[R, E, A]]
  )(implicit bf: BuildFrom[Collection[ZSTM[R, E, A]], A, Collection[A]]): ZSTM[R, E, Collection[A]] =
    foreach(in)(ZIO.identityFn)

  /**
   * Runs the transactions in the set and collects their results into a `Set`,
   * so equal results appear once.
   */
  def collectAll[R, E, A](in: Set[ZSTM[R, E, A]]): ZSTM[R, E, Set[A]] =
    foreach(in)(ZIO.identityFn)

  /**
   * Runs the transactions from left to right and discards their results,
   * without the cost of building the collection of results.
   *
   * Like `foreachDiscard`, it creates its iterator over `in` when it is called,
   * and every run of the transaction shares it: a run after a retry or a
   * conflict, or a second commit of the same value, goes on from where the
   * previous run stopped. `collectAll(in).unit` starts from the first
   * transaction on every run.
   */
  def collectAllDiscard[R, E, A](in: Iterable[ZSTM[R, E, A]]): ZSTM[R, E, Unit] =
    foreachDiscard(in)(ZIO.identityFn)

  /**
   * Applies `f` to the elements from left to right and succeeds with the first
   * `Some` it returns, without applying `f` to the remaining elements. Succeeds
   * with `None` when `f` returns `None` for every element or `as` is empty.
   *
   * The iterator over `as` is created when `collectFirst` is called and every
   * run of the transaction shares it: a run after a retry or a conflict, or a
   * second commit of the same value, goes on from where the previous run
   * stopped, not from the first element.
   */
  def collectFirst[R, E, A, B](as: Iterable[A])(f: A => ZSTM[R, E, Option[B]]): ZSTM[R, E, Option[B]] =
    succeedNow(as.iterator).flatMap { iterator =>
      def loop: ZSTM[R, E, Option[B]] =
        if (iterator.hasNext) f(iterator.next()).flatMap(_.fold(loop)(some(_)))
        else none
      loop
    }

  /**
   * Like `Either.cond`: succeeds with `result` when `predicate` is `true`, and
   * fails with `error` otherwise. `result` and `error` are evaluated when the
   * transaction runs.
   */
  def cond[E, A](predicate: Boolean, result: => A, error: => E): STM[E, A] =
    if (predicate) succeed(result) else fail(error)

  /**
   * Returns a transaction that dies with the defect `t`. Committing it gives an
   * effect that dies with `t`, and none of the transaction's changes are
   * committed. `orElse`, `catchAll` and `either` do not recover from it.
   */
  def die(t: => Throwable): USTM[Nothing] =
    Effect((_, _, _) => throw DieException(t))

  /**
   * Returns a transaction that dies with a `java.lang.RuntimeException` whose
   * message is `m` (see `die`).
   */
  def dieMessage(m: => String): USTM[Nothing] =
    die(new RuntimeException(m))

  /**
   * Returns a transaction for the given exit value. `TExit` is private to the
   * `zio.stm` package. Committing the transaction does not complete: it builds
   * itself again each time it runs, so the committing fiber keeps its thread
   * busy, and `timeout` does not stop it.
   */
  def done[R, E, A](exit: => TExit[E, A]): ZSTM[R, E, A] =
    suspend(done(exit))

  /**
   * Returns a transaction that succeeds with the environment it runs in.
   */
  def environment[R]: URSTM[R, ZEnvironment[R]] = Effect((_, _, r) => r)

  /**
   * Accesses the environment of the transaction and applies the function `f` to
   * it: `ZSTM.environmentWith[R](f)`.
   */
  def environmentWith[R]: EnvironmentWithPartiallyApplied[R] =
    new EnvironmentWithPartiallyApplied

  /**
   * Accesses the environment of the transaction and runs the transaction that
   * `f` returns for it: `ZSTM.environmentWithSTM[R](f)`.
   */
  def environmentWithSTM[R]: EnvironmentWithSTMPartiallyApplied[R] =
    new EnvironmentWithSTMPartiallyApplied

  /**
   * Determines whether any element of `as` satisfies the transactional
   * predicate `f`. Applies `f` from left to right and stops at the first
   * element for which it returns `true`. Succeeds with `false` when `as` is
   * empty.
   *
   * The iterator over `as` is created when `exists` is called and every run of
   * the transaction shares it: a run after a retry or a conflict, or a second
   * commit of the same value, goes on from where the previous run stopped, not
   * from the first element.
   */
  def exists[R, E, A](as: Iterable[A])(f: A => ZSTM[R, E, Boolean]): ZSTM[R, E, Boolean] =
    succeedNow(as.iterator).flatMap { iterator =>
      def loop: ZSTM[R, E, Boolean] =
        if (iterator.hasNext) f(iterator.next()).flatMap(b => if (b) succeedNow(b) else loop)
        else succeedNow(false)
      loop
    }

  /**
   * Returns a transaction that fails with the error `e`. When the error reaches
   * the end of the transaction, none of the transaction's changes are
   * committed. `orElse` runs its alternative with the changes of the failed
   * side undone, while `catchAll` keeps the changes made before the failure.
   *
   * {{{
   * import zio._
   *
   * val result: UIO[(Either[String, Unit], Int)] =
   *   for {
   *     ref <- TRef.makeCommit(5)
   *     res <- (ref.set(0) *> ZSTM.fail("invalid")).commit.either
   *     now <- ref.get.commit
   *   } yield (res, now)
   * // (Left(invalid),5)
   * }}}
   */
  def fail[E](e: => E): STM[E, Nothing] = Effect((_, _, _) => throw FailException(e))

  /**
   * Returns the id of the fiber that commits the transaction.
   */
  val fiberId: USTM[FiberId] = Effect((_, fiberId, _) => fiberId)

  /**
   * Filters the collection using the specified effectual predicate.
   */
  def filter[R, E, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(f: A => ZSTM[R, E, Boolean])(implicit bf: BuildFrom[Collection[A], A, Collection[A]]): ZSTM[R, E, Collection[A]] =
    ZSTM.suspend {
      ZSTM
        .foldLeft(as)(bf.newBuilder(as)) { (builder, a) =>
          f(a).map(b => if (b) builder += a else builder)
        }
        .map(_.result())
    }

  /**
   * Filters the set using the specified effectual predicate.
   */
  def filter[R, E, A](as: Set[A])(f: A => ZSTM[R, E, Boolean]): ZSTM[R, E, Set[A]] =
    filter[R, E, A, Iterable](as)(f).map(_.toSet)

  /**
   * Filters the collection using the specified effectual predicate, removing
   * all elements that satisfy the predicate.
   */
  def filterNot[R, E, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(f: A => ZSTM[R, E, Boolean])(implicit bf: BuildFrom[Collection[A], A, Collection[A]]): ZSTM[R, E, Collection[A]] =
    filter(as)(f(_).map(!_))

  /**
   * Filters the set using the specified effectual predicate, removing all
   * elements that satisfy the predicate.
   */
  def filterNot[R, E, A](as: Set[A])(f: A => ZSTM[R, E, Boolean]): ZSTM[R, E, Set[A]] =
    filterNot[R, E, A, Iterable](as)(f).map(_.toSet)

  /**
   * Returns an effect that first executes the outer effect, and then executes
   * the inner effect, returning the value from the inner effect, and
   * effectively flattening a nested effect.
   */
  def flatten[R, E, A](tx: ZSTM[R, E, ZSTM[R, E, A]]): ZSTM[R, E, A] =
    tx.flatMap(ZIO.identityFn)

  /**
   * Folds `in` with the transactional function `f`, starting from `zero` and
   * working sequentially from left to right.
   */
  def foldLeft[R, E, S, A](
    in: Iterable[A]
  )(zero: S)(f: (S, A) => ZSTM[R, E, S]): ZSTM[R, E, S] =
    ZSTM.suspend {
      val iterator = in.iterator

      def loop(s: S): ZSTM[R, E, S] =
        if (iterator.hasNext) f(s, iterator.next()).flatMap(loop)
        else ZSTM.succeed(s)

      loop(zero)
    }

  /**
   * Folds `in` with the transactional function `f`, starting from `zero` and
   * working sequentially from right to left (the last element first).
   */
  def foldRight[R, E, S, A](
    in: Iterable[A]
  )(zero: S)(f: (A, S) => ZSTM[R, E, S]): ZSTM[R, E, S] =
    foldLeft(in.toSeq.reverse)(zero)((s, a) => f(a, s))

  /**
   * Determines whether all elements of `as` satisfy the transactional predicate
   * `f`. Applies `f` from left to right and stops at the first element for
   * which it returns `false`. Succeeds with `true` when `as` is empty.
   *
   * The iterator over `as` is created when `forall` is called and every run of
   * the transaction shares it: a run after a retry or a conflict, or a second
   * commit of the same value, goes on from where the previous run stopped, not
   * from the first element.
   */
  def forall[R, E, A](as: Iterable[A])(f: A => ZSTM[R, E, Boolean]): ZSTM[R, E, Boolean] =
    succeedNow(as.iterator).flatMap { iterator =>
      def loop: ZSTM[R, E, Boolean] =
        if (iterator.hasNext) f(iterator.next()).flatMap(b => if (b) loop else succeedNow(b))
        else succeedNow(true)
      loop
    }

  /**
   * Applies the function `f` to each element of the `Collection[A]` from left
   * to right and returns a transactional effect that produces a new
   * `Collection[B]`.
   *
   * {{{
   * def depositAll(account: TRef[Int], amounts: List[Int]): USTM[List[Int]] =
   *   ZSTM.foreach(amounts)(amount => account.updateAndGet(_ + amount))
   * // with the account at 0 and List(10, 20, 30): List(10, 30, 60)
   * }}}
   */
  def foreach[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(f: A => ZSTM[R, E, B])(implicit bf: BuildFrom[Collection[A], B, Collection[B]]): ZSTM[R, E, Collection[B]] =
    ZSTM.suspend {
      val iterator = in.iterator
      val builder  = bf.newBuilder(in)

      def loop: ZSTM[R, E, Collection[B]] =
        if (iterator.hasNext) f(iterator.next()).flatMap { b => builder += b; loop }
        else ZSTM.succeed(builder.result())

      loop
    }

  /**
   * Applies the function `f` to each element of the `Set[A]` and returns a
   * transactional effect that produces a new `Set[B]`, so equal results appear
   * once.
   */
  def foreach[R, E, A, B](in: Set[A])(f: A => ZSTM[R, E, B]): ZSTM[R, E, Set[B]] =
    foreach[R, E, A, B, Iterable](in)(f).map(_.toSet)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` from left to
   * right and returns a transactional effect that produces `Unit`, without the
   * cost of building the collection of results.
   *
   * The iterator over `in` is created when `foreachDiscard` is called and every
   * run of the transaction shares it: a run after a retry or a conflict, or a
   * second commit of the same value, goes on from where the previous run
   * stopped, not from the first element. `foreach` starts from the first
   * element on every run.
   */
  def foreachDiscard[R, E, A](in: Iterable[A])(f: A => ZSTM[R, E, Any]): ZSTM[R, E, Unit] =
    ZSTM.succeedNow(in.iterator).flatMap[R, E, Unit] { it =>
      def loop: ZSTM[R, E, Unit] =
        if (it.hasNext) f(it.next()) *> loop
        else ZSTM.unit
      loop
    }

  /**
   * Lifts an `Either` into a `STM`: fails with `e` for `Left(e)` and succeeds
   * with `a` for `Right(a)`. `e` is evaluated when the transaction runs.
   */
  def fromEither[E, A](e: => Either[E, A]): STM[E, A] =
    STM.suspend {
      e match {
        case Left(t)  => STM.fail(t)
        case Right(a) => STM.succeedNow(a)
      }
    }

  /**
   * Lifts an `Option` into a `STM`: succeeds with the value of a `Some` and
   * fails with `None` for `None`.
   */
  def fromOption[A](v: => Option[A]): STM[Option[Nothing], A] =
    STM.suspend(v.fold[STM[Option[Nothing], A]](STM.fail(None))(STM.succeedNow))

  /**
   * Lifts a `Try` into a `STM`: succeeds with the value of a `Success` and
   * fails with the exception of a `Failure`.
   */
  def fromTry[A](a: => Try[A]): TaskSTM[A] =
    STM.suspend {
      a match {
        case Failure(t) => STM.fail(t)
        case Success(a) => STM.succeedNow(a)
      }
    }

  /**
   * Runs `onTrue` if the result of `b` is `true` and `onFalse` otherwise:
   * `ZSTM.ifSTM(b)(onTrue, onFalse)`.
   */
  def ifSTM[R, E](b: ZSTM[R, E, Boolean]): ZSTM.IfSTM[R, E] =
    new ZSTM.IfSTM(b)

  /**
   * Returns a transaction that ends as interrupted by the fiber that commits
   * it. Committing it gives an effect that fails with an interruption cause
   * carrying that fiber's id, and none of the transaction's changes are
   * committed. `orElse` does not recover from it.
   */
  val interrupt: USTM[Nothing] =
    ZSTM.fiberId.flatMap(fiberId => interruptAs(fiberId))

  /**
   * Returns a transaction that ends as interrupted by the fiber with the id
   * `fiberId` (see `interrupt`).
   */
  def interruptAs(fiberId: => FiberId): USTM[Nothing] =
    Effect((_, _, _) => throw InterruptException(fiberId))

  /**
   * Iterates with the specified transactional function: starting from
   * `initial`, runs `body` on the state while `cont` holds, and succeeds with
   * the final state. The moral equivalent of a `while` loop:
   *
   * {{{
   * val powerOfTwo: USTM[Int] =
   *   ZSTM.iterate(1)(_ < 100)(s => ZSTM.succeed(s * 2))
   * // commits to 128, the same result as:
   * def powerOfTwoLoop(): Int = {
   *   var s = 1
   *   while (s < 100) {
   *     s = s * 2
   *   }
   *   s
   * }
   * }}}
   */
  def iterate[R, E, S](initial: S)(cont: S => Boolean)(body: S => ZSTM[R, E, S]): ZSTM[R, E, S] =
    if (cont(initial)) body(initial).flatMap(iterate(_)(cont)(body))
    else ZSTM.succeedNow(initial)

  /**
   * Returns a transaction that succeeds with `Left(a)`.
   */
  def left[A](a: => A): USTM[Either[A, Nothing]] =
    succeed(Left(a))

  /**
   * Loops with the specified transactional function: starting from `initial`,
   * runs `body` on the state while `cont` holds, moving to the next state with
   * `inc`, and collects the results of `body` into a list, in order. The moral
   * equivalent of a `while` loop:
   *
   * {{{
   * val tens: USTM[List[Int]] =
   *   ZSTM.loop(1)(_ <= 3, _ + 1)(s => ZSTM.succeed(s * 10))
   * // commits to List(10, 20, 30), the same result as:
   * def tensLoop(): List[Int] = {
   *   var s  = 1
   *   var as = List.empty[Int]
   *   while (s <= 3) {
   *     as = (s * 10) :: as
   *     s = s + 1
   *   }
   *   as.reverse
   * }
   * }}}
   */
  def loop[R, E, A, S](initial: S)(cont: S => Boolean, inc: S => S)(body: S => ZSTM[R, E, A]): ZSTM[R, E, List[A]] =
    if (cont(initial))
      body(initial).flatMap(a => loop(inc(initial))(cont, inc)(body).map(as => a :: as))
    else
      ZSTM.succeedNow(List.empty[A])

  /**
   * Loops with the specified transactional function purely for its
   * transactional effects: starting from `initial`, runs `body` on the state
   * while `cont` holds, moving to the next state with `inc`. The moral
   * equivalent of a `while` loop:
   *
   * {{{
   * def addUpTo4(total: TRef[Int]): USTM[Unit] =
   *   ZSTM.loopDiscard(1)(_ <= 4, _ + 1)(s => total.update(_ + s))
   * // adds 1 + 2 + 3 + 4 = 10 to total, like:
   * def addUpTo4Loop(start: Int): Int = {
   *   var total = start
   *   var s     = 1
   *   while (s <= 4) {
   *     total = total + s
   *     s = s + 1
   *   }
   *   total
   * }
   * }}}
   */
  def loopDiscard[R, E, S](initial: S)(cont: S => Boolean, inc: S => S)(body: S => ZSTM[R, E, Any]): ZSTM[R, E, Unit] =
    if (cont(initial)) body(initial) *> loopDiscard(inc(initial))(cont, inc)(body)
    else ZSTM.unit

  /**
   * Merges an `Iterable[ZSTM]` to a single `ZSTM`, working sequentially: runs
   * the transactions from left to right and combines their results with `f`,
   * starting from `zero`.
   */
  def mergeAll[R, E, A, B](
    in: Iterable[ZSTM[R, E, A]]
  )(zero: B)(f: (B, A) => B): ZSTM[R, E, B] =
    ZSTM.foldLeft(in)(zero)((b, stm) => stm.map(a => f(b, a)))

  /**
   * Returns a transaction that succeeds with `None`.
   */
  val none: USTM[Option[Nothing]] = succeedNow(None)

  /**
   * Registers the workflow `zio` to run after the transaction ends, before the
   * committing effect returns. Workflows registered in one transaction run in
   * the order they were registered.
   *
   * A registered workflow also runs when the transaction ends with a failure
   * (`fail`), a defect (`die`) or `interrupt`, and when it was registered on a
   * side of `orElse` that retried or on a side whose failure `catchAll`
   * handled. It does not run, whatever the outcome, when the transaction
   * retried and waited for a `TRef` to change before it ended.
   */
  def onCommit[R](zio: ZIO[R, Nothing, Any])(implicit trace: Trace): ZSTM[R, Nothing, Unit] =
    ZSTM.OnCommit(zio, trace)

  /**
   * Feeds elements of type `A` to a function `f` that returns an effect, from
   * left to right. Collects all failures and successes as a pair `(failures,
   * successes)`; the transaction itself does not fail.
   */
  def partition[R, E, A, B](
    in: Iterable[A]
  )(f: A => ZSTM[R, E, B])(implicit ev: CanFail[E]): ZSTM[R, Nothing, (Iterable[E], Iterable[B])] =
    ZSTM.foreach(in)(f(_).either).map(ZIO.partitionMap(_)(ZIO.identityFn))

  /**
   * Reduces an `Iterable[ZSTM]` to a single `ZSTM`, working sequentially: runs
   * `a` and then the transactions in `as` from left to right, combining the
   * results with `f`. Succeeds with the result of `a` when `as` is empty.
   */
  def reduceAll[R, R1 <: R, E, A](a: ZSTM[R, E, A], as: Iterable[ZSTM[R1, E, A]])(
    f: (A, A) => A
  ): ZSTM[R1, E, A] =
    a.flatMap(ZSTM.mergeAll(as)(_)(f))

  /**
   * Returns an `Iterable` that holds the transaction `tx` `n` times, without
   * running it. The `Iterable` is empty when `n` is 0 or negative.
   */
  def replicate[R, E, A](n: Int)(tx: ZSTM[R, E, A]): Iterable[ZSTM[R, E, A]] =
    new Iterable[ZSTM[R, E, A]] {
      override def iterator: Iterator[ZSTM[R, E, A]] = Iterator.range(0, n).map(_ => tx)
    }

  /**
   * Performs the transaction `n` times and collects the results. Succeeds with
   * an empty `Iterable` when `n` is 0 or negative.
   */
  def replicateSTM[R, E, A](n: Int)(transaction: ZSTM[R, E, A]): ZSTM[R, E, Iterable[A]] =
    ZSTM.collectAll(ZSTM.replicate(n)(transaction))

  /**
   * Performs the transaction `n` times, discarding the results. Does nothing
   * when `n` is 0 or negative.
   *
   * Like `foreachDiscard`, every run of the transaction shares one iterator
   * over the `n` repetitions: a run after a retry or a conflict, or a second
   * commit of the same value, goes on from where the previous run stopped.
   */
  def replicateSTMDiscard[R, E, A](n: Int)(transaction: ZSTM[R, E, A]): ZSTM[R, E, Unit] =
    ZSTM.collectAllDiscard(ZSTM.replicate(n)(transaction))

  /**
   * Signals that the transaction cannot go on yet. When the retry reaches the
   * top of the transaction, none of the transaction's changes are committed,
   * and the committing fiber waits until one of the `TRef`s the transaction
   * read changes, then runs the transaction again. A transaction that read no
   * `TRef` keeps waiting until the fiber is interrupted.
   *
   * `orElse` and `orElseEither` catch a retry and run their alternative with
   * the changes of the retried side undone. `orTry` also runs its alternative,
   * but keeps the changes the retried side made. When the alternative of
   * `orElse` runs and no `TRef` is later set to a value that is not the same
   * object (`eq`) as its current value, the writes made before the `orElse` can
   * be left out of the commit (see `ZSTM#orElse`).
   *
   * {{{
   * def takePositive(ref: TRef[Int]): USTM[Int] =
   *   ref.get.flatMap(n => if (n > 0) ZSTM.succeed(n) else ZSTM.retry)
   *
   * // succeeds with -1 instead of waiting when ref holds 0 or less
   * def takePositiveOrDefault(ref: TRef[Int]): USTM[Int] =
   *   takePositive(ref) orElse ZSTM.succeed(-1)
   * }}}
   */
  val retry: USTM[Nothing] = Effect((_, _, _) => throw RetryException)

  /**
   * Returns a transaction that succeeds with `Right(a)`.
   */
  def right[A](a: => A): USTM[Either[Nothing, A]] =
    succeed(Right(a))

  /**
   * Accesses the service of type `A` in the environment of the transaction.
   */
  def service[A: Tag]: ZSTM[A, Nothing, A] =
    ZSTM.environmentWith(_.get[A])

  /**
   * Accesses the service corresponding to the specified key in the `Map[Key,
   * Service]` of the environment: `ZSTM.serviceAt[Service](key)`. Succeeds with
   * `None` when the map has no entry for the key.
   */
  def serviceAt[Service]: ZSTM.ServiceAtPartiallyApplied[Service] =
    new ZSTM.ServiceAtPartiallyApplied[Service]

  /**
   * Accesses the specified service in the environment of the transaction and
   * applies the function `f` to it: `ZSTM.serviceWith[Service](f)`.
   */
  def serviceWith[Service]: ServiceWithPartiallyApplied[Service] =
    new ServiceWithPartiallyApplied[Service]

  /**
   * Accesses the specified service in the environment of the transaction and
   * runs the transaction that `f` returns for it:
   * `ZSTM.serviceWithSTM[Service](f)`.
   */
  def serviceWithSTM[Service]: ServiceWithSTMPartiallyApplied[Service] =
    new ServiceWithSTMPartiallyApplied[Service]

  /**
   * Returns a transaction that succeeds with `Some(a)`.
   */
  def some[A](a: => A): USTM[Option[A]] =
    succeed(Some(a))

  /**
   * Returns an `STM` effect that succeeds with the specified value. `a` is
   * evaluated each time the transaction runs, which can be more than once when
   * the transaction retries or conflicts with another fiber, so it should not
   * have side effects. An exception thrown by `a` makes the transaction die.
   *
   * {{{
   * import zio._
   *
   * val total: UIO[Int] = ZSTM.succeed(List(1, 2, 3)).map(_.sum).commit
   * // 6
   * }}}
   */
  def succeed[A](a: => A): USTM[A] = Succeed(() => a)

  /**
   * Suspends creation of the specified transaction lazily: `stm` is built when
   * the transaction runs, and built again each time it runs, for example after
   * a retry. Useful for recursive transactions.
   *
   * {{{
   * def countdown(ref: TRef[Int]): USTM[Unit] =
   *   ZSTM.suspend {
   *     ref.get.flatMap { n =>
   *       if (n <= 0) ZSTM.unit else ref.set(n - 1) *> countdown(ref)
   *     }
   *   }
   * }}}
   */
  def suspend[R, E, A](stm: => ZSTM[R, E, A]): ZSTM[R, E, A] =
    STM.succeed(stm).flatten

  /**
   * Returns an `STM` effect that succeeds with `Unit`.
   */
  val unit: USTM[Unit] = succeedNow(())

  /**
   * The moral equivalent of `if (!b) stm`: runs `stm` and wraps its result in
   * `Some` when `b` is `false`, and succeeds with `None` without running `stm`
   * when `b` is `true`. `b` is evaluated when the transaction runs.
   */
  def unless[R, E, A](b: => Boolean)(stm: => ZSTM[R, E, A]): ZSTM[R, E, Option[A]] =
    suspend(if (b) none else stm.asSome)

  /**
   * The moral equivalent of `if (!b) stm` when `b` is a transaction:
   * `ZSTM.unlessSTM(b)(stm)` runs `b`, then runs `stm` and wraps its result in
   * `Some` when `b` succeeds with `false`, and succeeds with `None` otherwise.
   */
  def unlessSTM[R, E](b: ZSTM[R, E, Boolean]): ZSTM.UnlessSTM[R, E] =
    new ZSTM.UnlessSTM(b)

  /**
   * Feeds elements of type `A` to `f` from left to right and accumulates all
   * errors in error channel, in element order, or successes in success channel.
   *
   * This combinator is lossy meaning that if there are errors all successes
   * will be lost. To retain all information please use [[partition]].
   */
  def validate[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZSTM[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], ev: CanFail[E]): ZSTM[R, ::[E], Collection[B]] =
    partition(in)(f).flatMap {
      case (e :: es, _) => fail(::(e, es))
      case (_, bs)      => succeedNow(bf.fromSpecific(in)(bs))
    }

  /**
   * Feeds elements of type `A` to `f` from left to right and accumulates all
   * errors in error channel, in element order, or successes in success channel.
   *
   * This combinator is lossy meaning that if there are errors all successes
   * will be lost. To retain all information please use [[partition]].
   */
  def validate[R, E, A, B](in: NonEmptyChunk[A])(
    f: A => ZSTM[R, E, B]
  )(implicit ev: CanFail[E]): ZSTM[R, ::[E], NonEmptyChunk[B]] =
    partition(in)(f).flatMap {
      case (e :: es, _) => fail(::(e, es))
      case (_, bs)      => succeedNow(NonEmptyChunk.nonEmpty(Chunk.fromIterable(bs)))
    }

  /**
   * Feeds elements of type `A` to `f`, from left to right, until it succeeds.
   * Returns first success, without feeding the remaining elements, or the
   * accumulation of all errors when `f` fails for every element.
   */
  def validateFirst[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZSTM[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], E, Collection[E]], ev: CanFail[E]): ZSTM[R, Collection[E], B] =
    ZSTM.foreach(in)(f(_).flip).flip

  /**
   * The moral equivalent of `if (b) stm`: runs `stm` and wraps its result in
   * `Some` when `b` is `true`, and succeeds with `None` without running `stm`
   * when `b` is `false`. `b` is evaluated when the transaction runs.
   */
  def when[R, E, A](b: => Boolean)(stm: => ZSTM[R, E, A]): ZSTM[R, E, Option[A]] =
    suspend(if (b) stm.asSome else none)

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * value and wraps its result in `Some`; otherwise succeeds with `None`.
   */
  def whenCase[R, E, A, B](a: => A)(pf: PartialFunction[A, ZSTM[R, E, B]]): ZSTM[R, E, Option[B]] =
    suspend(pf.andThen(_.asSome).applyOrElse(a, (_: A) => none))

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * effectful value and wraps its result in `Some`; otherwise succeeds with
   * `None`.
   */
  def whenCaseSTM[R, E, A, B](a: ZSTM[R, E, A])(pf: PartialFunction[A, ZSTM[R, E, B]]): ZSTM[R, E, Option[B]] =
    a.flatMap(whenCase(_)(pf))

  /**
   * The moral equivalent of `if (b) stm` when `b` is a transaction:
   * `ZSTM.whenSTM(b)(stm)` runs `b`, then runs `stm` and wraps its result in
   * `Some` when `b` succeeds with `true`, and succeeds with `None` otherwise.
   */
  def whenSTM[R, E](b: ZSTM[R, E, Boolean]): ZSTM.WhenSTM[R, E] =
    new ZSTM.WhenSTM(b)

  /**
   * The first step of `ZSTM.acquireReleaseWith`: holds the `acquire`
   * transaction and takes the `release` workflow.
   */
  final class Acquire[-R, +E, +A](private val acquire: () => ZSTM[R, E, A]) extends AnyVal {

    /**
     * Sets the workflow that releases the acquired value, and returns a
     * `ZSTM.Release` that takes the `use` workflow.
     */
    def apply[R1](release: A => URIO[R1, Any]): Release[R with R1, E, A] =
      new Release[R with R1, E, A](acquire, release)
  }

  /**
   * The second step of `ZSTM.acquireReleaseWith`: holds the `acquire`
   * transaction and the `release` workflow, and takes the `use` workflow.
   */
  final class Release[-R, +E, +A](acquire: () => ZSTM[R, E, A], release: A => URIO[R, Any]) {

    /**
     * Commits `acquire`, runs `use` with the acquired value, then runs
     * `release`. See `ZSTM.acquireReleaseWith` for what is interruptible and
     * when `release` runs.
     */
    def apply[R1 <: R, E1 >: E, B](use: A => ZIO[R1, E1, B])(implicit trace: Trace): ZIO[R1, E1, B] =
      ZIO.uninterruptibleMask { restore =>
        var state: State[E, A] = State.Running

        restore(
          unsafeAtomically(acquire())(exit => state = State.Done(exit, List.empty), () => state = State.Interrupted)
        )
          .foldCauseZIO(
            cause => {
              state match {
                case State.Done(Exit.Success(a), _) =>
                  release(a).foldCauseZIO(
                    cause2 => Exit.failCause(cause ++ cause2),
                    _ => Exit.failCause(cause)
                  )
                case _ => Exit.failCause(cause)
              }
            },
            a =>
              restore(use(a)).foldCauseZIO(
                cause =>
                  release(a).foldCauseZIO(
                    cause2 => Exit.failCause(cause ++ cause2),
                    _ => Exit.failCause(cause)
                  ),
                b => release(a) *> ZIO.succeed(b)
              )
          )
      }
  }

  /**
   * Returned by `ZSTM.environmentWith`: fixes the environment type `R` so that
   * `apply` takes only the function.
   */
  final class EnvironmentWithPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /** Accesses the environment of the transaction and applies `f` to it. */
    def apply[A](f: ZEnvironment[R] => A): ZSTM[R, Nothing, A] =
      ZSTM.environment.map(f)
  }

  /**
   * Returned by `ZSTM.environmentWithSTM`: fixes the environment type `R` so
   * that `apply` takes only the function.
   */
  final class EnvironmentWithSTMPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Accesses the environment of the transaction and runs the transaction that
     * `f` returns for it.
     */
    def apply[R1 <: R, E, A](f: ZEnvironment[R] => ZSTM[R1, E, A]): ZSTM[R with R1, E, A] =
      ZSTM.environment.flatMap(f)
  }

  /**
   * Returned by `ZSTM.serviceAt`: fixes the service type so that `apply` takes
   * only the key.
   */
  final class ServiceAtPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Accesses the service at `key` in the `Map[Key, Service]` of the
     * environment, or `None` when the map has no entry for `key`.
     */
    def apply[Key](
      key: => Key
    )(implicit tag: EnvironmentTag[Map[Key, Service]]): ZSTM[Map[Key, Service], Nothing, Option[Service]] =
      ZSTM.environmentWith(_.getAt(key))
  }

  /**
   * Returned by `ZSTM.serviceWith`: fixes the service type so that `apply`
   * takes only the function.
   */
  final class ServiceWithPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /** Accesses the service in the environment and applies `f` to it. */
    def apply[A](f: Service => A)(implicit
      tag: Tag[Service]
    ): ZSTM[Service, Nothing, A] =
      ZSTM.service[Service].map(f)
  }

  /**
   * Returned by `ZSTM.serviceWithSTM`: fixes the service type so that `apply`
   * takes only the function.
   */
  final class ServiceWithSTMPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {

    /**
     * Accesses the service in the environment and runs the transaction that `f`
     * returns for it.
     */
    def apply[R <: Service, E, A](f: Service => ZSTM[R, E, A])(implicit
      tag: Tag[Service]
    ): ZSTM[R with Service, E, A] =
      ZSTM.service[Service].flatMap(f)
  }

  /**
   * Returned by `ZSTM.ifSTM`: holds the condition `b` and takes the two
   * branches.
   */
  final class IfSTM[R, E](private val b: ZSTM[R, E, Boolean]) {

    /**
     * Runs the condition, then `onTrue` if it succeeds with `true` and
     * `onFalse` otherwise.
     */
    def apply[R1 <: R, E1 >: E, A](onTrue: => ZSTM[R1, E1, A], onFalse: => ZSTM[R1, E1, A]): ZSTM[R1, E1, A] =
      b.flatMap(b => if (b) onTrue else onFalse)
  }

  /**
   * Returned by `ZSTM.unlessSTM`: holds the condition `b` and takes the
   * transaction to run.
   */
  final class UnlessSTM[R, E](private val b: ZSTM[R, E, Boolean]) {

    /**
     * Runs the condition, then runs `stm` and wraps its result in `Some` when
     * the condition succeeds with `false`; succeeds with `None` otherwise.
     */
    def apply[R1 <: R, E1 >: E, A](stm: => ZSTM[R1, E1, A]): ZSTM[R1, E1, Option[A]] =
      b.flatMap(b => if (b) none else stm.asSome)
  }

  /**
   * Returned by `ZSTM#updateService`: holds the transaction and takes the
   * function that updates the service of type `M`.
   */
  final class UpdateService[-R, +E, +A, M](private val self: ZSTM[R, E, A]) {

    /**
     * Runs the transaction with the service of type `M` in its environment
     * replaced by the result of `f`.
     */
    def apply[R1 <: R with M](f: M => M)(implicit tag: Tag[M]): ZSTM[R1, E, A] =
      self.provideSomeEnvironment(_.update(f))
  }

  /**
   * Returned by `ZSTM#updateServiceAt`: holds the transaction and takes the key
   * and the function that updates the service at that key.
   */
  final class UpdateServiceAt[-R, +E, +A, Service](private val self: ZSTM[R, E, A]) extends AnyVal {

    /**
     * Runs the transaction with the entry for `key` in the `Map[Key, Service]`
     * of its environment replaced by the result of `f`.
     */
    def apply[R1 <: R with Map[Key, Service], Key](key: => Key)(
      f: Service => Service
    )(implicit tag: Tag[Map[Key, Service]]): ZSTM[R1, E, A] =
      self.provideSomeEnvironment(_.updateAt(key)(f))
  }

  /**
   * Returned by `ZSTM.whenSTM`: holds the condition `b` and takes the
   * transaction to run.
   */
  final class WhenSTM[R, E](private val b: ZSTM[R, E, Boolean]) {

    /**
     * Runs the condition, then runs `stm` and wraps its result in `Some` when
     * the condition succeeds with `true`; succeeds with `None` otherwise.
     */
    def apply[R1 <: R, E1 >: E, A](stm: => ZSTM[R1, E1, A]): ZSTM[R1, E1, Option[A]] =
      b.flatMap(b => if (b) stm.asSome else none)
  }

  private[stm] final case class FailException[E](e: E) extends ControlThrowable

  private[stm] final case class DieException(t: Throwable) extends ControlThrowable

  private[stm] final case class InterruptException(fiberId: FiberId) extends ControlThrowable

  private[stm] case object RetryException extends ControlThrowable

  private[stm] final case class Effect[R, E, A](f: (Journal, FiberId, ZEnvironment[R]) => A) extends ZSTM[R, E, A] {
    def tag: Int = Tags.Effect
  }

  private[stm] final case class OnFailure[R, E1, E2, A](stm: ZSTM[R, E1, A], k: E1 => ZSTM[R, E2, A])
      extends ZSTM[R, E2, A]
      with Function[A, ZSTM[R, E2, A]] {
    def tag: Int = Tags.OnFailure

    def apply(a: A): ZSTM[R, E2, A] = succeedNow(a)
  }

  private[stm] final case class OnRetry[R, E, A](stm: ZSTM[R, E, A], onRetry: ZSTM[R, E, A])
      extends ZSTM[R, E, A]
      with Function[A, ZSTM[R, E, A]] {
    def tag: Int = Tags.OnRetry

    def apply(a: A): ZSTM[R, E, A] = succeedNow(a)
  }

  private[stm] final case class OnSuccess[R, E, A, B](stm: ZSTM[R, E, A], k: A => ZSTM[R, E, B]) extends ZSTM[R, E, B] {
    def tag: Int = Tags.OnSuccess
  }

  private[stm] final case class Provide[R1, R2, E, A](
    effect: ZSTM[R1, E, A],
    f: ZEnvironment[R2] => ZEnvironment[R1]
  ) extends ZSTM[R2, E, A] {
    def tag: Int = Tags.Provide
  }

  private[stm] final case class SucceedNow[A](a: A) extends ZSTM[Any, Nothing, A] {
    def tag: Int = Tags.SucceedNow
  }

  private[stm] final case class Succeed[A](a: () => A) extends ZSTM[Any, Nothing, A] {
    def tag: Int = Tags.Succeed
  }

  private[stm] final case class OnCommit[R](zio: ZIO[R, Nothing, Any], trace: Trace) extends ZSTM[R, Nothing, Unit] {
    def tag: Int = Tags.OnCommit
  }

  private[zio] def succeedNow[A](a: A): USTM[A] = SucceedNow(a)

  private[stm] object internal {
    // Using 3 because that will size the underlying map to 4 (due to loadFactor = 0.75d)
    final val DefaultJournalSize   = 3
    final val MaxRetries           = 10
    final val YieldOpCount         = 2048
    final val LockTimeoutMinMicros = 1L
    final val LockTimeoutMaxMicros = 10L

    @deprecated("Do not use, scheduled to be removed", "2.1.8")
    object Tags {
      final val Effect     = 0
      final val OnSuccess  = 1
      final val SucceedNow = 2
      final val Succeed    = 3
      final val OnFailure  = 4
      final val Provide    = 5
      final val OnRetry    = 6
      final val OnCommit   = 7
    }

    @deprecated
    class Versioned[A](val value: A) extends Serializable

    type TxnId = Long

    object TxnId {
      private[this] val txnCounter = new AtomicLong()

      def make(): TxnId = txnCounter.incrementAndGet()
    }

    final class Journal(
      private val map: TreeMap[TRef[?], Entry] = new TreeMap
    ) {

      // -- Map API --
      def clear(): Unit = if (map.nonEmpty) map.clear()

      def contains(key: TRef[?]): Boolean = map.contains(key)

      def getOrElseUpdate(key: TRef[?], entry: => Entry): Entry =
        map.getOrElseUpdate(key, entry)

      def keys: SortedSet[TRef[?]] = map.keySet

      // -- Transactional API --

      /**
       * Analyzes the journal, determining whether it is valid and whether it is
       * read only in a single pass. Note that information on whether the
       * journal is read only will only be accurate if the journal is valid, due
       * to short-circuiting that occurs on an invalid journal.
       *
       * In the case that there is only a single entry in the journal, we can
       * further shortcut and attempt to commit the transaction in a single pass
       * if the `attemptCommit` parameter is set to `true`
       *
       * '''NOTE''': This method MUST be invoked while we hold the lock on the
       * journal
       */
      private[internal] def analyze(attemptCommit: Boolean): JournalAnalysis =
        if (map.size == 1) analyzeJournal1(map.head._2, attemptCommit)
        else analyzeJournalN()

      private[this] def analyzeJournal1(value: Entry, attemptCommit: Boolean): JournalAnalysis =
        if (attemptCommit) {
          if (value.attemptCommit()) JournalAnalysis.Committed
          else JournalAnalysis.Invalid
        } else if (value.isInvalid) JournalAnalysis.Invalid
        else if (value.isChanged) JournalAnalysis.ReadWrite
        else JournalAnalysis.ReadOnly

      private[this] def analyzeJournalN(): JournalAnalysis = {
        var changed = false

        val it = map.valuesIterator
        while (it.hasNext) {
          val value = it.next()
          if (value.isInvalid) return JournalAnalysis.Invalid
          else if (value.isChanged) changed = true
        }
        if (changed) JournalAnalysis.ReadWrite else JournalAnalysis.ReadOnly
      }

      /**
       * Commit all changes in the journal.
       *
       * '''NOTE''': This method MUST be invoked while we hold the lock on the
       * journal
       */
      private[internal] def commit(): Unit = {
        val it = map.valuesIterator
        while (it.hasNext) it.next.commit()
      }

      /**
       * Collects all todos and submits them to the executor.
       *
       * '''NOTE''': This method MUST be invoked while we hold the lock on the
       * journal
       */
      private[internal] def completeTodos(executor: Executor)(implicit unsafe: Unsafe): Unit = {
        val todos = collectTodos()
        if (todos.nonEmpty) executor.submitOrThrow(() => execTodos(todos))
      }

      /**
       * Executes the todos in the current thread, sequentially.
       */
      private[this] def execTodos(todos: Map[TxnId, Todo]): Unit = {
        val it = todos.valuesIterator
        while (it.hasNext) it.next.apply()
      }

      /**
       * Flag indicating whether the journal is invalid
       *
       * '''NOTE''': This method MUST be invoked while we hold the lock on the
       * journal
       */
      private[ZSTM] def isInvalid: Boolean = !isValid

      /**
       * Flag indicating whether the journal is valid
       *
       * '''NOTE''': This method MUST be invoked while we hold the lock on the
       * journal
       */
      private[ZSTM] def isValid: Boolean = {
        val it = map.valuesIterator
        while (it.hasNext) if (!it.next().isValid) return false
        true
      }

      /**
       * Creates a function that can reset the journal.
       */
      private[ZSTM] def resetFn(): () => Unit = {
        val currentNewValues = ZSTMUtils.newMutableMap[TRef[_], Any](map.size)
        val itCapture        = map.iterator
        while (itCapture.hasNext) {
          val (key, value) = itCapture.next()
          currentNewValues.update(key, value.unsafeGet[Any])
        }

        () => {
          val saved = ZSTMUtils.newMutableMap[TRef[_], Entry](map.size)
          val it    = map.iterator
          while (it.hasNext) {
            val (key, value) = it.next()
            val resetValue = currentNewValues.getOrElse(key, null) match {
              case null => value.expected
              case v    => v
            }
            saved.update(key, value.copy().reset(resetValue))
          }
          map.clear()
          map ++= saved
          ()
        }
      }

      /**
       * Collect and clear all todos in the journal
       *
       * '''NOTE''': This method MUST be invoked while we hold the lock on the
       * journal
       */
      private[this] def collectTodos(): Map[TxnId, Todo] = {
        var allTodos = Map.empty[TxnId, Todo]

        val it = map.valuesIterator
        while (it.hasNext) {
          val tref    = it.next.tref
          val oldTodo = tref.todo

          if (oldTodo.nonEmpty) {
            tref.todo = Map.empty
            allTodos ++= oldTodo
          }
        }

        allTodos
      }

      /**
       * For the given transaction id, adds the specified todo effect to every
       * `TRef` in the journal that has no todo for that id yet.
       *
       * '''NOTE''': This method MUST be invoked while we hold the lock on the
       * journal
       */
      private[internal] def addTodo(txnId: TxnId, todo: Todo): Unit = {
        val it = map.keysIterator
        while (it.hasNext) {
          val tref    = it.next()
          val oldTodo = tref.todo

          if (!oldTodo.contains(txnId)) {
            val newTodo = oldTodo.updated(txnId, todo)
            tref.todo = newTodo
          }
        }
      }
    }

    type Todo = () => Any

    type JournalAnalysis = Int
    object JournalAnalysis {
      final val Invalid   = 0
      final val ReadWrite = 1
      final val ReadOnly  = 2
      final val Committed = 3
    }

    def tryCommitSync[R, E, A](
      fiberId: FiberId,
      stm: ZSTM[R, E, A],
      state: AtomicReference[State[E, A]],
      r: ZEnvironment[R],
      executor: Executor
    )(implicit unsafe: Unsafe): TryCommit[E, A] = {
      val journal     = new Journal
      val stateIsNull = state eq null

      /**
       * Checks that the value is a success, and commits the changes if the
       * journal is valid and values have changed. When the journal is valid and
       * the value is not a retry, also submits the todos of the journal's
       * `TRef`s to the executor. Returns whether the journal is valid.
       *
       * '''NOTE''': This method MUST be invoked while we hold the lock on the
       * journal
       */
      def checkAndMaybeCommit(value: TExit[E, A]) = {
        val isSuccess      = value.isInstanceOf[TExit.Succeed[_]]
        val analysis       = journal.analyze(attemptCommit = isSuccess && stateIsNull)
        val isJournalValid = analysis != JournalAnalysis.Invalid
        if (isJournalValid) {
          if (
            analysis == JournalAnalysis.ReadWrite &&
            isSuccess &&
            (stateIsNull || state.compareAndSet(State.Running, State.done(value)))
          ) journal.commit()
          if (value ne TExit.Retry) journal.completeTodos(executor)
        }
        isJournalValid
      }

      var value = null.asInstanceOf[TExit[E, A]]

      // Used to store the previous snapshot of TRefs
      var tRefs = immutable.TreeSet.empty[TRef[?]]
      // Mutable, changes when journal keys are modified!
      // Use `ZSTMUtils.newImmutableTreeSet` to extract the current snapshot
      val tRefsUnsafe = journal.keys

      var loop    = true
      var retries = 0

      while (loop) {
        journal.clear()

        if (retries > MaxRetries) {
          ZSTMLockSupport.lock(tRefs) {
            value = stm.run(journal, fiberId, r)

            /*
              Ensure we have the lock on all the tRefs in the current Journal. They might have changed for a few reasons:
                1. The transaction created new ZSTMs
                2. The transaction was retried, and a different path was taken

               In case they changed, try acquiring the lock on them otherwise retry the entire transaction.
             */
            val missing = tRefsUnsafe.diff(tRefs)
            ZSTMLockSupport.tryLock(missing) {
              loop = !checkAndMaybeCommit(value)
            }
            if (loop) tRefs = ZSTMUtils.newImmutableTreeSet(tRefsUnsafe)
          }
        } else {
          value = stm.run(journal, fiberId, r)
          ZSTMLockSupport.tryLock(tRefsUnsafe) {
            loop = !checkAndMaybeCommit(value)
          }
          if (loop && retries >= MaxRetries) tRefs = ZSTMUtils.newImmutableTreeSet(tRefsUnsafe)
        }

        retries += 1
      }

      value match {
        case TExit.Succeed(a, onCommit)         => TryCommit.Done(Exit.succeed(a), onCommit)
        case TExit.Fail(e, onCommit)            => TryCommit.Done(Exit.fail(e), onCommit)
        case TExit.Die(t, onCommit)             => TryCommit.Done(Exit.die(t), onCommit)
        case TExit.Interrupt(fiberId, onCommit) => TryCommit.Done(Exit.interrupt(fiberId), onCommit)
        case TExit.Retry                        => TryCommit.Suspend(journal)
      }
    }

    def tryCommitAsync[R, E, A](
      journal: Journal,
      executor: Executor,
      fiberId: FiberId,
      stm: ZSTM[R, E, A],
      txnId: TxnId,
      state: AtomicReference[State[E, A]],
      r: ZEnvironment[R]
    )(
      k: ZIO[R, E, A] => Any
    )(implicit trace: Trace, unsafe: Unsafe): Unit = {
      def exec(journal: Journal) = {
        val keys = journal.keys
        ZSTMLockSupport.lock(keys) {
          if (journal.isInvalid)
            executor.submitOrThrow(() => tryCommitAsync(null, executor, fiberId, stm, txnId, state, r)(k))
          else
            journal.addTodo(txnId, () => tryCommitAsync(null, executor, fiberId, stm, txnId, state, r)(k))
        }
      }

      state.get match {
        case State.Done(exit, _)              => k(exit)
        case State.Interrupted                => k(Exit.interrupt(FiberId.None))
        case State.Running if journal ne null => exec(journal)
        case State.Running =>
          tryCommitSync(fiberId, stm, state, r, executor) match {
            case TryCommit.Done(io, _)         => k(io)
            case TryCommit.Suspend(newJournal) => exec(newJournal)
          }
      }
    }

    sealed abstract class TExit[+A, +B] extends Serializable with Product
    object TExit {
      val unit: TExit[Nothing, Unit] = Succeed((), List.empty)

      final case class Fail[+A](value: A, onCommit: List[ZIO[Any, Nothing, Any]])    extends TExit[A, Nothing]
      final case class Die(error: Throwable, onCommit: List[ZIO[Any, Nothing, Any]]) extends TExit[Nothing, Nothing]
      final case class Interrupt(fiberId: FiberId, onCommit: List[ZIO[Any, Nothing, Any]])
          extends TExit[Nothing, Nothing]
      final case class Succeed[+B](value: B, onCommit: List[ZIO[Any, Nothing, Any]]) extends TExit[Nothing, B]
      case object Retry                                                              extends TExit[Nothing, Nothing]
    }

    abstract class Entry { self =>
      type S <: AnyRef

      val tref: TRef[S]

      private[stm] val expected: S

      protected[this] var newValue: S

      protected[this] var _isChanged: Boolean

      def unsafeSet(value: Any): Unit = {
        val value0 = value.asInstanceOf[S]
        if (value0 ne newValue) {
          if (!_isChanged) _isChanged = true
          newValue = value0
        }
      }

      def unsafeUpdate(f: Any => Any): Unit = unsafeSet(f(newValue))

      def unsafeGet[B]: B = newValue.asInstanceOf[B]

      /**
       * Commits the new value to the `TRef`.
       */
      def commit(): Unit = tref.versioned.set(newValue)

      def attemptCommit(): Boolean = tref.versioned.compareAndSet(expected, newValue)

      /**
       * Creates a copy of the Entry.
       */
      def copy(): Entry = new Entry {
        type S = self.S
        val tref       = self.tref
        val expected   = self.expected
        var newValue   = self.newValue
        var _isChanged = self.isChanged
      }

      /**
       * Resets the Entry with a given value.
       */
      private[stm] def reset(resetValue: Any): Entry = new Entry {
        type S = self.S
        val tref       = self.tref
        val expected   = self.expected
        var newValue   = resetValue.asInstanceOf[S]
        var _isChanged = false
      }

      /**
       * Determines if the entry is invalid. This is the negated version of
       * `isValid`.
       */
      def isInvalid: Boolean = !isValid

      /**
       * Determines if the entry is valid. That is, if the current value of the
       * `TRef` is the same reference (`eq`) as the expected value read when the
       * entry was created.
       */
      def isValid: Boolean = tref.versioned.get.asInstanceOf[AnyRef] eq expected

      /**
       * Determines if the variable has been set to a value that is not the same
       * reference as its previous new value since the entry was created or last
       * reset by `orElse`.
       */
      def isChanged: Boolean = _isChanged

      override def toString: String =
        s"Entry(expected.value = ${expected}, newValue = $newValue, tref = $tref, isChanged = $isChanged)"
    }

    object Entry {

      /**
       * Creates an entry for the journal for the given `TRef`. The expected
       * value and the new value both start as the `TRef`'s current value.
       */
      private[stm] def apply[A0 <: AnyRef](tref0: TRef[A0]): Entry =
        new Entry {
          type S = A0
          val tref       = tref0
          val expected   = tref.versioned.get
          var newValue   = expected
          var _isChanged = false
        }
    }

    sealed abstract class TryCommit[+E, +A]
    object TryCommit {
      final case class Done[+E, +A](exit: Exit[E, A], onCommit: List[ZIO[Any, Nothing, Any]]) extends TryCommit[E, A]
      case class Suspend(journal: Journal)                                                    extends TryCommit[Nothing, Nothing]
    }

    sealed abstract class State[+E, +A] { self =>
      final def isRunning: Boolean =
        self match {
          case State.Running => true
          case _             => false
        }
    }

    object State {
      final case class Done[+E, +A](exit: Exit[E, A], onCommit: List[ZIO[Any, Nothing, Any]]) extends State[E, A]
      case object Interrupted                                                                 extends State[Nothing, Nothing]
      case object Running                                                                     extends State[Nothing, Nothing]

      def done[E, A](exit: TExit[E, A]): State[E, A] =
        exit match {
          case TExit.Succeed(a, onCommit)         => State.Done(Exit.succeed(a), onCommit)
          case TExit.Die(t, onCommit)             => State.Done(Exit.die(t), onCommit)
          case TExit.Fail(e, onCommit)            => State.Done(Exit.fail(e), onCommit)
          case TExit.Interrupt(fiberId, onCommit) => State.Done(Exit.interrupt(fiberId), onCommit)
          case TExit.Retry                        => throw new Error("Defect: done being called on TExit.Retry")
        }
    }
  }
}
