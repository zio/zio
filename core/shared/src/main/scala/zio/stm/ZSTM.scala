/*
 * Copyright 2019-2023 John A. De Goes and the ZIO Contributors
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

import zio._
import zio.internal.{Stack, Sync}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.util.{HashMap => MutableMap}
import scala.annotation.{nowarn, tailrec}
import scala.collection.mutable.Builder
import scala.util.{Failure, Success, Try}

/**
 * `STM[E, A]` represents an effect that can be performed transactionally,
 * resulting in a failure `E` or a value `A`.
 *
 * {{{
 * def transfer(receiver: TRef[Int],
 *               sender: TRef[Int], much: Int): UIO[Int] =
 *   STM.atomically {
 *     for {
 *       balance <- sender.get
 *       _       <- STM.check(balance >= much)
 *       _       <- receiver.update(_ + much)
 *       _       <- sender.update(_ - much)
 *       newAmnt <- receiver.get
 *     } yield newAmnt
 *   }
 *
 *   val action: UIO[Int] =
 *     for {
 *       t <- STM.atomically(TRef.make(0).zip(TRef.make(20000)))
 *       (receiver, sender) = t
 *       balance <- transfer(receiver, sender, 1000)
 *     } yield balance
 * }}}
 *
 * Software Transactional Memory is a technique which allows composition of
 * arbitrary atomic operations. It is the software analog of transactions in
 * database systems.
 *
 * The API is lifted directly from the Haskell package Control.Concurrent.STM
 * although the implementation does not resemble the Haskell one at all.
 * [[http://hackage.haskell.org/package/stm-2.5.0.0/docs/Control-Concurrent-STM.html]]
 *
 * STM in Haskell was introduced in: Composable memory transactions, by Tim
 * Harris, Simon Marlow, Simon Peyton Jones, and Maurice Herlihy, in ACM
 * Conference on Principles and Practice of Parallel Programming 2005.
 * [[https://www.microsoft.com/en-us/research/publication/composable-memory-transactions/]]
 *
 * See also: Lock Free Data Structures using STMs in Haskell, by Anthony
 * Discolo, Tim Harris, Simon Marlow, Simon Peyton Jones, Satnam Singh) FLOPS
 * 2006: Eighth International Symposium on Functional and Logic Programming,
 * Fuji Susono, JAPAN, April 2006
 * [[https://www.microsoft.com/en-us/research/publication/lock-free-data-structures-using-stms-in-haskell/]]
 */
sealed trait ZSTM[-R, +E, +A] extends Serializable { self =>
  import ZSTM.internal.{prepareResetJournal, Journal, Tags, TExit}
  import ZSTM._

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
   * Tries this effect first, and if it fails or retries, tries the other
   * effect.
   */
  def <>[R1 <: R, E1, A1 >: A](that: => ZSTM[R1, E1, A1]): ZSTM[R1, E1, A1] =
    orElse(that)

  /**
   * Tries this effect first, and if it enters retry, then it tries the other
   * effect. This is an equivalent of haskell's orElse.
   */
  def <|>[R1 <: R, E1 >: E, A1 >: A](that: => ZSTM[R1, E1, A1]): ZSTM[R1, E1, A1] =
    orTry(that)

  /**
   * Returns an effect that submerges the error case of an `Either` into the
   * `STM`. The inverse operation of `STM.either`.
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
   * Recovers from all errors.
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
   * Simultaneously filters and maps the value produced by this effect.
   */
  def collect[B](pf: PartialFunction[A, B]): ZSTM[R, E, B] =
    collectSTM(pf.andThen(ZSTM.succeedNow(_)))

  /**
   * Simultaneously filters and flatMaps the value produced by this effect.
   * Continues on the effect returned from pf.
   */
  def collectSTM[R1 <: R, E1 >: E, B](pf: PartialFunction[A, ZSTM[R1, E1, B]]): ZSTM[R1, E1, B] =
    foldSTM(ZSTM.fail(_), (a: A) => if (pf.isDefinedAt(a)) pf(a) else ZSTM.retry)

  /**
   * Commits this transaction atomically.
   */
  def commit(implicit trace: Trace): ZIO[R, E, A] = ZSTM.atomically(self)

  /**
   * Commits this transaction atomically, regardless of whether the transaction
   * is a success or a failure.
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
   * Converts the failure channel into an `Either`.
   */
  def either(implicit ev: CanFail[E]): URSTM[R, Either[E, A]] =
    fold(Left(_), Right(_))

  /**
   * Executes the specified finalization transaction whether or not this effect
   * succeeds. Note that as with all STM transactions, if the full transaction
   * fails, everything will be rolled back.
   */
  def ensuring[R1 <: R](finalizer: ZSTM[R1, Nothing, Any]): ZSTM[R1, E, A] =
    foldSTM(e => finalizer *> ZSTM.fail(e), a => finalizer *> ZSTM.succeedNow(a))

  /**
   * Returns an effect that ignores errors and runs repeatedly until it
   * eventually succeeds.
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
   * runs the returned effect as well to produce its results.
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
   * parameters back
   */
  def flipWith[R1, A1, E1](f: ZSTM[R, A, E] => ZSTM[R1, A1, E1]): ZSTM[R1, E1, A1] =
    f(flip).flip

  /**
   * Folds over the `STM` effect, handling both failure and success, but not
   * retry.
   */
  def fold[B](f: E => B, g: A => B)(implicit ev: CanFail[E]): URSTM[R, B] =
    foldSTM(f andThen ZSTM.succeedNow, g andThen ZSTM.succeedNow)

  /**
   * Effectfully folds over the `STM` effect, handling both failure and success.
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
   * Maps the value produced by the effect.
   */
  def map[B](f: A => B): ZSTM[R, E, B] = flatMap(f andThen ZSTM.succeedNow)

  /**
   * Maps the value produced by the effect with the specified function that may
   * throw exceptions but is otherwise pure, translating any thrown exceptions
   * into typed failed effects.
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
   * Named alias for `<>`.
   */
  def orElse[R1 <: R, E1, A1 >: A](that: => ZSTM[R1, E1, A1]): ZSTM[R1, E1, A1] =
    Effect[Any, Nothing, () => Any]((journal, _, _) => prepareResetJournal(journal)).flatMap { reset =>
      self.orTry(ZSTM.succeed(reset()) *> that).catchAll(_ => ZSTM.succeed(reset()) *> that)
    }

  /**
   * Returns a transactional effect that will produce the value of this effect
   * in left side, unless it fails or retries, in which case, it will produce
   * the value of the specified effect in right side.
   */
  def orElseEither[R1 <: R, E1, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, Either[A, B]] =
    (self map (Left[A, B](_))) orElse (that map (Right[A, B](_)))

  /**
   * Tries this effect first, and if it fails or retries, fails with the
   * specified error.
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
   * specified value.
   */
  def orElseSucceed[A1 >: A](a1: => A1): URSTM[R, A1] =
    orElse(ZSTM.succeedNow(a1))

  /**
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
   * the predicate returns true for the value.
   */
  def retryUntil(f: A => Boolean): ZSTM[R, E, A] =
    collect {
      case a if f(a) => a
    }

  /**
   * Filters the value produced by this effect, retrying the transaction while
   * the predicate returns true for the value.
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
   * Extracts the optional value, or returns the given 'default'.
   */
  def someOrElse[B](default: => B)(implicit ev: A <:< Option[B]): ZSTM[R, E, B] =
    map(_.getOrElse(default))

  /**
   * Extracts the optional value, or executes the effect 'default'.
   */
  def someOrElseSTM[B, R1 <: R, E1 >: E](default: ZSTM[R1, E1, B])(implicit ev: A <:< Option[B]): ZSTM[R1, E1, B] =
    self.flatMap(ev(_) match {
      case Some(value) => ZSTM.succeedNow(value)
      case None        => default
    })

  /**
   * Extracts the optional value, or fails with the given error 'e'.
   */
  def someOrFail[B, E1 >: E](e: => E1)(implicit ev: A <:< Option[B]): ZSTM[R, E1, B] =
    flatMap(_.fold[ZSTM[R, E1, B]](ZSTM.fail(e))(ZSTM.succeedNow))

  /**
   * Extracts the optional value, or fails with a
   * [[java.util.NoSuchElementException]]
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

  def tag: Int

  /**
   * "Peeks" at the success of transactional effect.
   */
  def tap[R1 <: R, E1 >: E](f: A => ZSTM[R1, E1, Any]): ZSTM[R1, E1, A] =
    flatMap(a => f(a).as(a))

  /**
   * "Peeks" at both sides of an transactional effect.
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
   * The moral equivalent of `if (!p) exp`
   */
  def unless(b: => Boolean): ZSTM[R, E, Option[A]] =
    ZSTM.unless(b)(self)

  /**
   * The moral equivalent of `if (!p) exp` when `p` has side-effects
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
   * The moral equivalent of `if (p) exp`
   */
  def when(b: => Boolean): ZSTM[R, E, Option[A]] =
    ZSTM.when(b)(self)

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  def whenSTM[R1 <: R, E1 >: E](b: ZSTM[R1, E1, Boolean]): ZSTM[R1, E1, Option[A]] =
    ZSTM.whenSTM(b)(self)

  /**
   * Same as [[retryUntil]].
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

    val contStack     = Stack[Cont]()
    val envStack      = Stack[ZEnvironment[Any]](r0)
    var onCommitStack = Stack[ZIO[Any, Nothing, Any]]()
    var exit          = null.asInstanceOf[TExit[Any, Any]]
    var curr          = self.asInstanceOf[Erased]
    var opCount       = 0

    def unwindStack(error: Any, isRetry: Boolean): Erased = {
      var result = null.asInstanceOf[Erased]

      while (!contStack.isEmpty && (result eq null)) {
        contStack.pop() match {
          case OnFailure(_, onFailure) => if (!isRetry) result = onFailure.asInstanceOf[Cont].apply(error)
          case OnRetry(_, onRetry)     => if (isRetry) result = onRetry
          case _                       =>
        }
      }

      result
    }

    while (exit eq null) {
      if (opCount == YieldOpCount) {
        if (isInvalid(journal)) exit = TExit.Retry
        else opCount = 0
      } else {
        try {
          (curr.tag: @annotation.switch) match {
            case Tags.Effect =>
              val effect = curr.asInstanceOf[Effect[Any, Any, Any]]
              val a      = effect.f(journal, fiberId, envStack.peek())
              if (contStack.isEmpty) exit = TExit.Succeed(a, onCommitStack.toList) else curr = contStack.pop()(a)

            case Tags.OnSuccess =>
              val onSuccess = curr.asInstanceOf[OnSuccess[Any, Any, Any, Any]]
              contStack.push(onSuccess.k)
              curr = onSuccess.stm

            case Tags.OnFailure =>
              val onFailure = curr.asInstanceOf[OnFailure[Any, Any, Any, Any]]
              contStack.push(onFailure)
              curr = onFailure.stm

            case Tags.OnRetry =>
              val onRetry = curr.asInstanceOf[OnRetry[Any, Any, Any]]
              contStack.push(onRetry)
              curr = onRetry.stm

            case Tags.Provide =>
              val provide = curr.asInstanceOf[Provide[Any, Any, Any, Any]]

              envStack.push(provide.f.asInstanceOf[ZEnvironment[Any] => ZEnvironment[Any]](envStack.peek()))

              val cleanup = ZSTM.succeed(envStack.pop())

              curr = provide.effect.ensuring(cleanup).asInstanceOf[Erased]

            case Tags.SucceedNow =>
              val a = curr.asInstanceOf[SucceedNow[Any]].a

              if (contStack.isEmpty) exit = TExit.Succeed(a, onCommitStack.toList) else curr = contStack.pop()(a)

            case Tags.Succeed =>
              val a = curr.asInstanceOf[Succeed[Any]].a()

              if (contStack.isEmpty) exit = TExit.Succeed(a, onCommitStack.toList) else curr = contStack.pop()(a)

            case Tags.OnCommit =>
              val onCommit = curr.asInstanceOf[OnCommit[Any]]
              onCommitStack.push(onCommit.zio.provideEnvironment(envStack.peek())(onCommit.trace))
              if (contStack.isEmpty) exit = TExit.Succeed((), onCommitStack.toList)
              else curr = contStack.pop()(())
          }
        } catch {
          case ZSTM.RetryException =>
            curr = unwindStack(null, true)
            if (curr eq null) exit = TExit.Retry
          case ZSTM.FailException(e) =>
            curr = unwindStack(e, false)
            if (curr eq null) exit = TExit.Fail(e, onCommitStack.toList)
          case ZSTM.DieException(t) =>
            exit = TExit.Die(t, onCommitStack.toList)
          case ZSTM.InterruptException(fiberId) =>
            exit = TExit.Interrupt(fiberId, onCommitStack.toList)
          case t: Throwable =>
            exit = TExit.Die(t, onCommitStack.toList)
        }

        opCount += 1
      }
    }

    exit.asInstanceOf[TExit[E, A]]
  }

}

object ZSTM {
  import internal._

  /**
   * Submerges the error case of an `Either` into the `STM`. The inverse
   * operation of `STM.either`.
   */
  def absolve[R, E, A](z: ZSTM[R, E, Either[E, A]]): ZSTM[R, E, A] =
    z.flatMap(fromEither(_))

  /**
   * Treats the specified `acquire` transaction as the acquisition of a
   * resource. The `acquire` transaction will be executed interruptibly. If it
   * is a success and is committed the specified `release` workflow will be
   * executed uninterruptibly as soon as the `use` workflow completes execution.
   */
  def acquireReleaseWith[R, E, A](acquire: ZSTM[R, E, A]): ZSTM.Acquire[R, E, A] =
    new ZSTM.Acquire[R, E, A](() => acquire)

  /**
   * Atomically performs a batch of operations in a single transaction.
   */
  def atomically[R, E, A](stm: ZSTM[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    unsafeAtomically(stm)(_ => (), () => ())

  private def unsafeAtomically[R, E, A](
    stm: ZSTM[R, E, A]
  )(onDone: Exit[E, A] => Any, onInterrupt: () => Any)(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.withFiberRuntime[R, E, A] { (fiberState, _) =>
      implicit val u = Unsafe.unsafe

      val r        = fiberState.getFiberRef(FiberRef.currentEnvironment).asInstanceOf[ZEnvironment[R]]
      val fiberId  = fiberState.id
      val executor = fiberState.getCurrentExecutor()

      tryCommitSync(executor, fiberId, stm, r) match {
        case TryCommit.Done(exit, onCommit) =>
          onDone(exit)
          ZIO.collectAll(onCommit.reverse) *> ZIO.done(exit)(trace)
        case TryCommit.Suspend(journal) =>
          val txnId = makeTxnId()
          val state = new AtomicReference[State[E, A]](State.Running)
          val async = ZIO.async(tryCommitAsync(journal, executor, fiberId, stm, txnId, state, r))

          ZIO.uninterruptibleMask { restore =>
            restore(async).catchAllCause { cause =>
              state.compareAndSet(State.Running, State.Interrupted)
              state.get match {
                case State.Done(exit, onCommit) =>
                  onDone(exit)
                  ZIO.collectAll(onCommit.reverse) *> ZIO.done(exit)
                case _ =>
                  onInterrupt()
                  ZIO.refailCause(cause)
              }
            }
          }
      }
    }

  /**
   * Creates an `STM` value from a partial (but pure) function.
   */
  def attempt[A](a: => A): STM[Throwable, A] =
    fromTry(Try(a))

  /**
   * Checks the condition, and if it's true, returns unit, otherwise, retries.
   */
  def check[R](p: => Boolean): URSTM[R, Unit] =
    suspend(if (p) STM.unit else retry)

  /**
   * Evaluate each effect in the structure from left to right, collecting the
   * the successful values and discarding the empty cases.
   */
  def collect[R, E, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(f: A => ZSTM[R, Option[E], B])(implicit bf: BuildFrom[Collection[A], B, Collection[B]]): ZSTM[R, E, Collection[B]] =
    foreach[R, E, A, Option[B], Iterable](in)(a => f(a).unsome).map(_.flatten).map(bf.fromSpecific(in))

  /**
   * Collects all the transactional effects in a collection, returning a single
   * transactional effect that produces a collection of values.
   */
  def collectAll[R, E, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[ZSTM[R, E, A]]
  )(implicit bf: BuildFrom[Collection[ZSTM[R, E, A]], A, Collection[A]]): ZSTM[R, E, Collection[A]] =
    foreach(in)(ZIO.identityFn)

  /**
   * Collects all the transactional effects in a set, returning a single
   * transactional effect that produces a set of values.
   */
  def collectAll[R, E, A](in: Set[ZSTM[R, E, A]]): ZSTM[R, E, Set[A]] =
    foreach(in)(ZIO.identityFn)

  /**
   * Collects all the transactional effects, returning a single transactional
   * effect that produces `Unit`.
   *
   * Equivalent to `collectAll(i).unit`, but without the cost of building the
   * list of results.
   */
  def collectAllDiscard[R, E, A](in: Iterable[ZSTM[R, E, A]]): ZSTM[R, E, Unit] =
    foreachDiscard(in)(ZIO.identityFn)

  /**
   * Collects the first element of the `Iterable[A]` for which the effectual
   * function `f` returns `Some`.
   */
  def collectFirst[R, E, A, B](as: Iterable[A])(f: A => ZSTM[R, E, Option[B]]): ZSTM[R, E, Option[B]] =
    succeedNow(as.iterator).flatMap { iterator =>
      def loop: ZSTM[R, E, Option[B]] =
        if (iterator.hasNext) f(iterator.next()).flatMap(_.fold(loop)(some(_)))
        else none
      loop
    }

  /**
   * Similar to Either.cond, evaluate the predicate, return the given A as
   * success if predicate returns true, and the given E as error otherwise
   */
  def cond[E, A](predicate: Boolean, result: => A, error: => E): STM[E, A] =
    if (predicate) succeed(result) else fail(error)

  /**
   * Kills the fiber running the effect.
   */
  def die(t: => Throwable): USTM[Nothing] =
    Effect((_, _, _) => throw DieException(t))

  /**
   * Kills the fiber running the effect with a `RuntimeException` that contains
   * the specified message.
   */
  def dieMessage(m: => String): USTM[Nothing] =
    die(new RuntimeException(m))

  /**
   * Returns a value modelled on provided exit status.
   */
  def done[R, E, A](exit: => TExit[E, A]): ZSTM[R, E, A] =
    suspend(done(exit))

  /**
   * Retrieves the environment inside an stm.
   */
  def environment[R]: URSTM[R, ZEnvironment[R]] = Effect((_, _, r) => r)

  /**
   * Accesses the environment of the transaction to perform a transaction.
   */
  def environmentWith[R]: EnvironmentWithPartiallyApplied[R] =
    new EnvironmentWithPartiallyApplied

  /**
   * Accesses the environment of the transaction to perform a transaction.
   */
  def environmentWithSTM[R]: EnvironmentWithSTMPartiallyApplied[R] =
    new EnvironmentWithSTMPartiallyApplied

  /**
   * Determines whether any element of the `Iterable[A]` satisfies the effectual
   * predicate `f`.
   */
  def exists[R, E, A](as: Iterable[A])(f: A => ZSTM[R, E, Boolean]): ZSTM[R, E, Boolean] =
    succeedNow(as.iterator).flatMap { iterator =>
      def loop: ZSTM[R, E, Boolean] =
        if (iterator.hasNext) f(iterator.next()).flatMap(b => if (b) succeedNow(b) else loop)
        else succeedNow(false)
      loop
    }

  /**
   * Returns a value that models failure in the transaction.
   */
  def fail[E](e: => E): STM[E, Nothing] = Effect((_, _, _) => throw FailException(e))

  /**
   * Returns the fiber id of the fiber committing the transaction.
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
   * Folds an Iterable[A] using an effectual function f, working sequentially
   * from left to right.
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
   * Folds an Iterable[A] using an effectual function f, working sequentially
   * from right to left.
   */
  def foldRight[R, E, S, A](
    in: Iterable[A]
  )(zero: S)(f: (A, S) => ZSTM[R, E, S]): ZSTM[R, E, S] =
    foldLeft(in.toSeq.reverse)(zero)((s, a) => f(a, s))

  /**
   * Determines whether all elements of the `Iterable[A]` satisfy the effectual
   * predicate `f`.
   */
  def forall[R, E, A](as: Iterable[A])(f: A => ZSTM[R, E, Boolean]): ZSTM[R, E, Boolean] =
    succeedNow(as.iterator).flatMap { iterator =>
      def loop: ZSTM[R, E, Boolean] =
        if (iterator.hasNext) f(iterator.next()).flatMap(b => if (b) loop else succeedNow(b))
        else succeedNow(true)
      loop
    }

  /**
   * Applies the function `f` to each element of the `Collection[A]` and returns
   * a transactional effect that produces a new `Collection[B]`.
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
   * transactional effect that produces a new `Set[B]`.
   */
  def foreach[R, E, A, B](in: Set[A])(f: A => ZSTM[R, E, B]): ZSTM[R, E, Set[B]] =
    foreach[R, E, A, B, Iterable](in)(f).map(_.toSet)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and returns a
   * transactional effect that produces `Unit`.
   *
   * Equivalent to `foreach(as)(f).unit`, but without the cost of building the
   * list of results.
   */
  def foreachDiscard[R, E, A](in: Iterable[A])(f: A => ZSTM[R, E, Any]): ZSTM[R, E, Unit] =
    ZSTM.succeedNow(in.iterator).flatMap[R, E, Unit] { it =>
      def loop: ZSTM[R, E, Unit] =
        if (it.hasNext) f(it.next()) *> loop
        else ZSTM.unit
      loop
    }

  /**
   * Lifts an `Either` into a `STM`.
   */
  def fromEither[E, A](e: => Either[E, A]): STM[E, A] =
    STM.suspend {
      e match {
        case Left(t)  => STM.fail(t)
        case Right(a) => STM.succeedNow(a)
      }
    }

  /**
   * Lifts an `Option` into a `STM`.
   */
  def fromOption[A](v: => Option[A]): STM[Option[Nothing], A] =
    STM.suspend(v.fold[STM[Option[Nothing], A]](STM.fail(None))(STM.succeedNow))

  /**
   * Lifts a `Try` into a `STM`.
   */
  def fromTry[A](a: => Try[A]): TaskSTM[A] =
    STM.suspend {
      a match {
        case Failure(t) => STM.fail(t)
        case Success(a) => STM.succeedNow(a)
      }
    }

  /**
   * Runs `onTrue` if the result of `b` is `true` and `onFalse` otherwise.
   */
  def ifSTM[R, E](b: ZSTM[R, E, Boolean]): ZSTM.IfSTM[R, E] =
    new ZSTM.IfSTM(b)

  /**
   * Interrupts the fiber running the effect.
   */
  val interrupt: USTM[Nothing] =
    ZSTM.fiberId.flatMap(fiberId => interruptAs(fiberId))

  /**
   * Interrupts the fiber running the effect with the specified fiber id.
   */
  def interruptAs(fiberId: => FiberId): USTM[Nothing] =
    Effect((_, _, _) => throw InterruptException(fiberId))

  /**
   * Iterates with the specified transactional function. The moral equivalent
   * of:
   *
   * {{{
   * var s = initial
   *
   * while (cont(s)) {
   *   s = body(s)
   * }
   *
   * s
   * }}}
   */
  def iterate[R, E, S](initial: S)(cont: S => Boolean)(body: S => ZSTM[R, E, S]): ZSTM[R, E, S] =
    if (cont(initial)) body(initial).flatMap(iterate(_)(cont)(body))
    else ZSTM.succeedNow(initial)

  /**
   * Returns an effect with the value on the left part.
   */
  def left[A](a: => A): USTM[Either[A, Nothing]] =
    succeed(Left(a))

  /**
   * Loops with the specified transactional function, collecting the results
   * into a list. The moral equivalent of:
   *
   * {{{
   * var s  = initial
   * var as = List.empty[A]
   *
   * while (cont(s)) {
   *   as = body(s) :: as
   *   s  = inc(s)
   * }
   *
   * as.reverse
   * }}}
   */
  def loop[R, E, A, S](initial: S)(cont: S => Boolean, inc: S => S)(body: S => ZSTM[R, E, A]): ZSTM[R, E, List[A]] =
    if (cont(initial))
      body(initial).flatMap(a => loop(inc(initial))(cont, inc)(body).map(as => a :: as))
    else
      ZSTM.succeedNow(List.empty[A])

  /**
   * Loops with the specified transactional function purely for its
   * transactional effects. The moral equivalent of:
   *
   * {{{
   * var s = initial
   *
   * while (cont(s)) {
   *   body(s)
   *   s = inc(s)
   * }
   * }}}
   */
  def loopDiscard[R, E, S](initial: S)(cont: S => Boolean, inc: S => S)(body: S => ZSTM[R, E, Any]): ZSTM[R, E, Unit] =
    if (cont(initial)) body(initial) *> loopDiscard(inc(initial))(cont, inc)(body)
    else ZSTM.unit

  /**
   * Merges an `Iterable[ZSTM]` to a single ZSTM, working sequentially.
   */
  def mergeAll[R, E, A, B](
    in: Iterable[ZSTM[R, E, A]]
  )(zero: B)(f: (B, A) => B): ZSTM[R, E, B] =
    ZSTM.foldLeft(in)(zero)((b, stm) => stm.map(a => f(b, a)))

  /**
   * Returns an effect with the empty value.
   */
  val none: USTM[Option[Nothing]] = succeedNow(None)

  /**
   * Executes the specified workflow when this transaction is committed.
   */
  def onCommit[R](zio: ZIO[R, Nothing, Any])(implicit trace: Trace): ZSTM[R, Nothing, Unit] =
    ZSTM.OnCommit(zio, trace)

  /**
   * Feeds elements of type `A` to a function `f` that returns an effect.
   * Collects all successes and failures in a tupled fashion.
   */
  def partition[R, E, A, B](
    in: Iterable[A]
  )(f: A => ZSTM[R, E, B])(implicit ev: CanFail[E]): ZSTM[R, Nothing, (Iterable[E], Iterable[B])] =
    ZSTM.foreach(in)(f(_).either).map(ZIO.partitionMap(_)(ZIO.identityFn))

  /**
   * Reduces an `Iterable[ZSTM]` to a single `ZSTM`, working sequentially.
   */
  def reduceAll[R, R1 <: R, E, A](a: ZSTM[R, E, A], as: Iterable[ZSTM[R1, E, A]])(
    f: (A, A) => A
  ): ZSTM[R1, E, A] =
    a.flatMap(ZSTM.mergeAll(as)(_)(f))

  /**
   * Replicates the given effect n times. If 0 or negative numbers are given, an
   * empty `Iterable` will return.
   */
  def replicate[R, E, A](n: Int)(tx: ZSTM[R, E, A]): Iterable[ZSTM[R, E, A]] =
    new Iterable[ZSTM[R, E, A]] {
      override def iterator: Iterator[ZSTM[R, E, A]] = Iterator.range(0, n).map(_ => tx)
    }

  /**
   * Performs this transaction the specified number of times and collects the
   * results.
   */
  def replicateSTM[R, E, A](n: Int)(transaction: ZSTM[R, E, A]): ZSTM[R, E, Iterable[A]] =
    ZSTM.collectAll(ZSTM.replicate(n)(transaction))

  /**
   * Performs this transaction the specified number of times, discarding the
   * results.
   */
  def replicateSTMDiscard[R, E, A](n: Int)(transaction: ZSTM[R, E, A]): ZSTM[R, E, Unit] =
    ZSTM.collectAllDiscard(ZSTM.replicate(n)(transaction))

  /**
   * Abort and retry the whole transaction when any of the underlying
   * transactional variables have changed.
   */
  val retry: USTM[Nothing] = Effect((_, _, _) => throw RetryException)

  /**
   * Returns an effect with the value on the right part.
   */
  def right[A](a: => A): USTM[Either[Nothing, A]] =
    succeed(Right(a))

  /**
   * Accesses the specified service in the environment of the effect.
   */
  def service[A: Tag]: ZSTM[A, Nothing, A] =
    ZSTM.environmentWith(_.get[A])

  /**
   * Accesses the service corresponding to the specified key in the environment.
   */
  def serviceAt[Service]: ZSTM.ServiceAtPartiallyApplied[Service] =
    new ZSTM.ServiceAtPartiallyApplied[Service]

  /**
   * Effectfully accesses the specified service in the environment of the
   * effect.
   */
  def serviceWith[Service]: ServiceWithPartiallyApplied[Service] =
    new ServiceWithPartiallyApplied[Service]

  /**
   * Effectfully accesses the specified service in the environment of the
   * effect.
   */
  def serviceWithSTM[Service]: ServiceWithSTMPartiallyApplied[Service] =
    new ServiceWithSTMPartiallyApplied[Service]

  /**
   * Returns an effect with the optional value.
   */
  def some[A](a: => A): USTM[Option[A]] =
    succeed(Some(a))

  /**
   * Returns an `STM` effect that succeeds with the specified value.
   */
  def succeed[A](a: => A): USTM[A] = Succeed(() => a)

  /**
   * Suspends creation of the specified transaction lazily.
   */
  def suspend[R, E, A](stm: => ZSTM[R, E, A]): ZSTM[R, E, A] =
    STM.succeed(stm).flatten

  /**
   * Returns an `STM` effect that succeeds with `Unit`.
   */
  val unit: USTM[Unit] = succeedNow(())

  /**
   * The moral equivalent of `if (!p) exp`
   */
  def unless[R, E, A](b: => Boolean)(stm: => ZSTM[R, E, A]): ZSTM[R, E, Option[A]] =
    suspend(if (b) none else stm.asSome)

  /**
   * The moral equivalent of `if (!p) exp` when `p` has side-effects
   */
  def unlessSTM[R, E](b: ZSTM[R, E, Boolean]): ZSTM.UnlessSTM[R, E] =
    new ZSTM.UnlessSTM(b)

  /**
   * Feeds elements of type `A` to `f` and accumulates all errors in error
   * channel or successes in success channel.
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
   * Feeds elements of type `A` to `f` and accumulates all errors in error
   * channel or successes in success channel.
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
   * Feeds elements of type `A` to `f` until it succeeds. Returns first success
   * or the accumulation of all errors.
   */
  def validateFirst[R, E, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZSTM[R, E, B]
  )(implicit bf: BuildFrom[Collection[A], E, Collection[E]], ev: CanFail[E]): ZSTM[R, Collection[E], B] =
    ZSTM.foreach(in)(f(_).flip).flip

  /**
   * The moral equivalent of `if (p) exp`
   */
  def when[R, E, A](b: => Boolean)(stm: => ZSTM[R, E, A]): ZSTM[R, E, Option[A]] =
    suspend(if (b) stm.asSome else none)

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * value, otherwise does nothing.
   */
  def whenCase[R, E, A, B](a: => A)(pf: PartialFunction[A, ZSTM[R, E, B]]): ZSTM[R, E, Option[B]] =
    suspend(pf.andThen(_.asSome).applyOrElse(a, (_: A) => none))

  /**
   * Runs an effect when the supplied `PartialFunction` matches for the given
   * effectful value, otherwise does nothing.
   */
  def whenCaseSTM[R, E, A, B](a: ZSTM[R, E, A])(pf: PartialFunction[A, ZSTM[R, E, B]]): ZSTM[R, E, Option[B]] =
    a.flatMap(whenCase(_)(pf))

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  def whenSTM[R, E](b: ZSTM[R, E, Boolean]): ZSTM.WhenSTM[R, E] =
    new ZSTM.WhenSTM(b)

  final class Acquire[-R, +E, +A](private val acquire: () => ZSTM[R, E, A]) extends AnyVal {
    def apply[R1](release: A => URIO[R1, Any]): Release[R with R1, E, A] =
      new Release[R with R1, E, A](acquire, release)
  }
  final class Release[-R, +E, +A](acquire: () => ZSTM[R, E, A], release: A => URIO[R, Any]) {
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
                    cause2 => ZIO.refailCause(cause ++ cause2),
                    _ => ZIO.refailCause(cause)
                  )
                case _ => ZIO.refailCause(cause)
              }
            },
            a =>
              restore(use(a)).foldCauseZIO(
                cause =>
                  release(a).foldCauseZIO(
                    cause2 => ZIO.refailCause(cause ++ cause2),
                    _ => ZIO.refailCause(cause)
                  ),
                b => release(a) *> ZIO.succeed(b)
              )
          )
      }
  }

  final class EnvironmentWithPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](f: ZEnvironment[R] => A): ZSTM[R, Nothing, A] =
      ZSTM.environment.map(f)
  }

  final class EnvironmentWithSTMPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[R1 <: R, E, A](f: ZEnvironment[R] => ZSTM[R1, E, A]): ZSTM[R with R1, E, A] =
      ZSTM.environment.flatMap(f)
  }

  final class ServiceAtPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[Key](
      key: => Key
    )(implicit tag: EnvironmentTag[Map[Key, Service]]): ZSTM[Map[Key, Service], Nothing, Option[Service]] =
      ZSTM.environmentWith(_.getAt(key))
  }

  final class ServiceWithPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](f: Service => A)(implicit
      tag: Tag[Service]
    ): ZSTM[Service, Nothing, A] =
      ZSTM.service[Service].map(f)
  }

  final class ServiceWithSTMPartiallyApplied[Service](private val dummy: Boolean = true) extends AnyVal {
    def apply[R <: Service, E, A](f: Service => ZSTM[R, E, A])(implicit
      tag: Tag[Service]
    ): ZSTM[R with Service, E, A] =
      ZSTM.service[Service].flatMap(f)
  }

  final class IfSTM[R, E](private val b: ZSTM[R, E, Boolean]) {
    def apply[R1 <: R, E1 >: E, A](onTrue: => ZSTM[R1, E1, A], onFalse: => ZSTM[R1, E1, A]): ZSTM[R1, E1, A] =
      b.flatMap(b => if (b) onTrue else onFalse)
  }

  final class UnlessSTM[R, E](private val b: ZSTM[R, E, Boolean]) {
    def apply[R1 <: R, E1 >: E, A](stm: => ZSTM[R1, E1, A]): ZSTM[R1, E1, Option[A]] =
      b.flatMap(b => if (b) none else stm.asSome)
  }

  final class UpdateService[-R, +E, +A, M](private val self: ZSTM[R, E, A]) {
    def apply[R1 <: R with M](f: M => M)(implicit tag: Tag[M]): ZSTM[R1, E, A] =
      self.provideSomeEnvironment(_.update(f))
  }

  final class UpdateServiceAt[-R, +E, +A, Service](private val self: ZSTM[R, E, A]) extends AnyVal {
    def apply[R1 <: R with Map[Key, Service], Key](key: => Key)(
      f: Service => Service
    )(implicit tag: Tag[Map[Key, Service]]): ZSTM[R1, E, A] =
      self.provideSomeEnvironment(_.updateAt(key)(f))
  }

  final class WhenSTM[R, E](private val b: ZSTM[R, E, Boolean]) {
    def apply[R1 <: R, E1 >: E, A](stm: => ZSTM[R1, E1, A]): ZSTM[R1, E1, Option[A]] =
      b.flatMap(b => if (b) stm.asSome else none)
  }

  private[stm] final case class FailException[E](e: E) extends Throwable(null, null, false, false)

  private[stm] final case class DieException(t: Throwable) extends Throwable(null, null, false, false)

  private[stm] final case class InterruptException(fiberId: FiberId) extends Throwable(null, null, false, false)

  private[stm] case object RetryException extends Throwable(null, null, false, false)

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
    val DefaultJournalSize = 4
    val MaxRetries         = 10
    val YieldOpCount       = 2048

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

    class Versioned[A](val value: A) extends Serializable

    type TxnId = Long

    type Journal = MutableMap[TRef[_], Entry]

    type Todo = () => Any

    /**
     * Creates a function that can reset the journal.
     */
    def prepareResetJournal(journal: Journal): () => Any = {
      val saved = new MutableMap[TRef[_], Entry](journal.size)

      val it = journal.entrySet.iterator
      while (it.hasNext) {
        val entry = it.next
        saved.put(entry.getKey, entry.getValue.copy())
      }

      () => { journal.clear(); journal.putAll(saved); () }
    }

    /**
     * Commits the journal.
     */
    def commitJournal(journal: Journal): Unit = {
      val it = journal.entrySet.iterator
      while (it.hasNext) it.next.getValue.commit()
    }

    /**
     * Allocates memory for the journal, if it is null, otherwise just clears
     * it.
     */
    def allocJournal(journal: Journal): Journal =
      if (journal eq null) new MutableMap[TRef[_], Entry](DefaultJournalSize)
      else {
        journal.clear()
        journal
      }

    /**
     * Determines if the journal is valid.
     */
    def isValid(journal: Journal): Boolean = {
      var valid = true
      val it    = journal.entrySet.iterator
      while (valid && it.hasNext) valid = it.next.getValue.isValid
      valid
    }

    /**
     * Analyzes the journal, determining whether it is valid and whether it is
     * read only in a single pass. Note that information on whether the journal
     * is read only will only be accurate if the journal is valid, due to
     * short-circuiting that occurs on an invalid journal.
     */
    def analyzeJournal(journal: Journal): JournalAnalysis = {
      var result = JournalAnalysis.ReadOnly: JournalAnalysis
      val it     = journal.entrySet.iterator
      while ((result ne JournalAnalysis.Invalid) && it.hasNext) {
        val value = it.next.getValue
        if (value.isInvalid) result = JournalAnalysis.Invalid
        else if (value.isChanged) result = JournalAnalysis.ReadWrite
      }
      result
    }

    sealed abstract class JournalAnalysis extends Serializable with Product
    object JournalAnalysis {
      case object Invalid   extends JournalAnalysis
      case object ReadOnly  extends JournalAnalysis
      case object ReadWrite extends JournalAnalysis
    }

    /**
     * Determines if the journal is invalid.
     */
    def isInvalid(journal: Journal): Boolean = !isValid(journal)

    /**
     * Atomically collects and clears all the todos from any `TRef` that
     * participated in the transaction.
     */
    def collectTodos(journal: Journal): MutableMap[TxnId, Todo] = {
      import collection.JavaConverters._

      val allTodos  = new MutableMap[TxnId, Todo](DefaultJournalSize)
      val emptyTodo = Map.empty[TxnId, Todo]

      val it = journal.entrySet.iterator
      while (it.hasNext) {
        val tref = it.next.getValue.tref
        val todo = tref.todo

        var loop = true
        while (loop) {
          val oldTodo = todo.get

          loop = !todo.compareAndSet(oldTodo, emptyTodo)

          if (!loop) allTodos.putAll(oldTodo.asJava): @nowarn("msg=JavaConverters")
        }
      }

      allTodos
    }

    /**
     * Executes the todos in the current thread, sequentially.
     */
    def execTodos(todos: MutableMap[TxnId, Todo]): Unit = {
      val it = todos.entrySet.iterator
      while (it.hasNext) it.next.getValue.apply()
    }

    /**
     * For the given transaction id, adds the specified todo effect to all
     * `TRef` values.
     */
    def addTodo(txnId: TxnId, journal: Journal, todoEffect: Todo): Boolean = {
      var added = false

      val it = journal.entrySet.iterator
      while (it.hasNext) {
        val tref = it.next.getValue.tref

        var loop = true
        while (loop) {
          val oldTodo = tref.todo.get

          if (!oldTodo.contains(txnId)) {
            val newTodo = oldTodo.updated(txnId, todoEffect)

            loop = !tref.todo.compareAndSet(oldTodo, newTodo)

            if (!loop) added = true
          } else loop = false
        }
      }

      added
    }

    /**
     * Runs all the todos.
     */
    def completeTodos[E, A](
      exit: Exit[E, A],
      journal: Journal,
      executor: Executor,
      onCommit: List[ZIO[Any, Nothing, Any]]
    )(implicit
      unsafe: Unsafe
    ): TryCommit[E, A] = {
      val todos = collectTodos(journal)

      if (todos.size > 0) executor.submitOrThrow(() => execTodos(todos))

      TryCommit.Done(exit, onCommit)
    }

    /**
     * Finds all the new todo targets that are not already tracked in the
     * `oldJournal`.
     */
    def untrackedTodoTargets(oldJournal: Journal, newJournal: Journal): Journal = {
      val untracked = new MutableMap[TRef[_], Entry](newJournal.size)

      untracked.putAll(newJournal)

      val it = newJournal.entrySet.iterator
      while (it.hasNext) {
        val entry = it.next
        val key   = entry.getKey
        val value = entry.getValue
        if (oldJournal.containsKey(key)) {
          // We already tracked this one, remove it:
          untracked.remove(key)
        } else if (value.isNew) {
          // This `TRef` was created in the current transaction, so no need to
          // add any todos to it, because it cannot be modified from the outside
          // until the transaction succeeds; so any todo added to it would never
          // succeed.
          untracked.remove(key)
        }
      }

      untracked
    }

    def tryCommitSync[R, E, A](
      executor: Executor,
      fiberId: FiberId,
      stm: ZSTM[R, E, A],
      r: ZEnvironment[R]
    )(implicit unsafe: Unsafe): TryCommit[E, A] = {
      var journal = null.asInstanceOf[MutableMap[TRef[_], Entry]]
      var value   = null.asInstanceOf[TExit[E, A]]

      var loop    = true
      var retries = 0

      while (loop) {
        journal = allocJournal(journal)

        if (retries > MaxRetries) {
          Sync(globalLock) {
            value = stm.run(journal, fiberId, r)

            value match {
              case _: TExit.Succeed[_] =>
                commitJournal(journal)
                loop = false

              case _ =>
                retries = 0
            }
          }

        } else {
          value = stm.run(journal, fiberId, r)

          val analysis = analyzeJournal(journal)

          if (analysis ne JournalAnalysis.Invalid) {
            loop = false

            value match {
              case _: TExit.Succeed[_] =>
                if (analysis eq JournalAnalysis.ReadWrite) {
                  Sync(globalLock) {
                    if (isValid(journal)) {
                      commitJournal(journal)
                    } else {
                      loop = true
                    }
                  }
                } else {
                  Sync(globalLock) {
                    if (isInvalid(journal)) loop = true
                  }
                }

              case _ =>
                Sync(globalLock) {
                  if (isInvalid(journal)) loop = true
                }
            }
          }
        }

        retries += 1
      }

      value match {
        case TExit.Succeed(a, onCommit)         => completeTodos(Exit.succeed(a), journal, executor, onCommit)
        case TExit.Fail(e, onCommit)            => completeTodos(Exit.fail(e), journal, executor, onCommit)
        case TExit.Die(t, onCommit)             => completeTodos(Exit.die(t), journal, executor, onCommit)
        case TExit.Interrupt(fiberId, onCommit) => completeTodos(Exit.interrupt(fiberId), journal, executor, onCommit)
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
      def complete(exit: Exit[E, A]): Unit = { k(ZIO.done(exit)); () }

      @tailrec
      def suspend(accum: Journal, journal: Journal): Unit = {
        addTodo(txnId, journal, () => tryCommitAsync(null, executor, fiberId, stm, txnId, state, r)(k))

        if (isInvalid(journal)) tryCommit(executor, fiberId, stm, state, r) match {
          case TryCommit.Done(exit, onCommit) => complete(exit)
          case TryCommit.Suspend(journal2) =>
            val untracked = untrackedTodoTargets(accum, journal2)

            if (untracked.size > 0) {
              accum.putAll(untracked)

              suspend(accum, untracked)
            }
        }
      }

      Sync(state) {
        if (state.get.isRunning) {
          if (journal ne null) suspend(journal, journal)
          else
            tryCommit(executor, fiberId, stm, state, r) match {
              case TryCommit.Done(io, onCommit) => complete(io)
              case TryCommit.Suspend(journal)   => suspend(journal, journal)
            }
        }
      }
    }

    def tryCommit[R, E, A](
      executor: Executor,
      fiberId: FiberId,
      stm: ZSTM[R, E, A],
      state: AtomicReference[State[E, A]],
      r: ZEnvironment[R]
    )(implicit unsafe: Unsafe): TryCommit[E, A] = {
      var journal = null.asInstanceOf[MutableMap[TRef[_], Entry]]
      var value   = null.asInstanceOf[TExit[E, A]]

      var loop    = true
      var retries = 0

      while (loop) {
        journal = allocJournal(journal)

        if (retries > MaxRetries) {
          Sync(globalLock) {
            value = stm.run(journal, fiberId, r)

            value match {
              case _: TExit.Succeed[_] =>
                val isRunning = state.compareAndSet(State.Running, State.done(value))
                if (isRunning) {
                  commitJournal(journal)
                }
                loop = false

              case _ =>
                retries = 0
            }
          }

        } else {
          value = stm.run(journal, fiberId, r)

          val analysis = analyzeJournal(journal)

          if (analysis ne JournalAnalysis.Invalid) {
            loop = false

            value match {
              case _: TExit.Succeed[_] =>
                if (analysis eq JournalAnalysis.ReadWrite) {
                  Sync(globalLock) {
                    if (isValid(journal)) {
                      val isRunning = state.compareAndSet(State.Running, State.done(value))
                      if (isRunning) {
                        commitJournal(journal)
                      }
                    } else {
                      loop = true
                    }
                  }
                } else {
                  Sync(globalLock) {
                    if (isInvalid(journal)) loop = true
                  }
                }

              case _ =>
                Sync(globalLock) {
                  if (isInvalid(journal)) loop = true
                }
            }
          }
        }

        retries += 1
      }

      value match {
        case TExit.Succeed(a, onCommit)         => completeTodos(Exit.succeed(a), journal, executor, onCommit)
        case TExit.Fail(e, onCommit)            => completeTodos(Exit.fail(e), journal, executor, onCommit)
        case TExit.Die(t, onCommit)             => completeTodos(Exit.die(t), journal, executor, onCommit)
        case TExit.Interrupt(fiberId, onCommit) => completeTodos(Exit.interrupt(fiberId), journal, executor, onCommit)
        case TExit.Retry                        => TryCommit.Suspend(journal)
      }
    }

    def makeTxnId(): Long = txnCounter.incrementAndGet()

    private[this] val txnCounter: AtomicLong = new AtomicLong()

    val globalLock: AnyRef = new AnyRef {}

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
      type S

      val tref: TRef[S]

      private[stm] val expected: Versioned[S]

      protected[this] var newValue: S

      val isNew: Boolean

      private[this] var _isChanged = false

      def unsafeSet(value: Any): Unit = {
        _isChanged = true
        newValue = value.asInstanceOf[S]
      }

      def unsafeGet[B]: B = newValue.asInstanceOf[B]

      /**
       * Commits the new value to the `TRef`.
       */
      def commit(): Unit = tref.versioned = new Versioned(newValue)

      /**
       * Creates a copy of the Entry.
       */
      def copy(): Entry = new Entry {
        type S = self.S
        val tref     = self.tref
        val expected = self.expected
        val isNew    = self.isNew
        var newValue = self.newValue
        _isChanged = self.isChanged
      }

      /**
       * Determines if the entry is invalid. This is the negated version of
       * `isValid`.
       */
      def isInvalid: Boolean = !isValid

      /**
       * Determines if the entry is valid. That is, if the version of the `TRef`
       * is equal to the expected version.
       */
      def isValid: Boolean = tref.versioned eq expected

      /**
       * Determines if the variable has been set in a transaction.
       */
      def isChanged: Boolean = _isChanged

      override def toString: String =
        s"Entry(expected.value = ${expected.value}, newValue = $newValue, tref = $tref, isChanged = $isChanged)"
    }

    object Entry {

      /**
       * Creates an entry for the journal, given the `TRef` being untracked, the
       * new value of the `TRef`, and the expected version of the `TRef`.
       */
      private[stm] def apply[A0](tref0: TRef[A0], isNew0: Boolean): Entry = {
        val versioned = tref0.versioned

        new Entry {
          type S = A0
          val tref     = tref0
          val isNew    = isNew0
          val expected = versioned
          var newValue = versioned.value
        }
      }
    }

    sealed abstract class TryCommit[+E, +A]
    object TryCommit {
      final case class Done[+E, +A](exit: Exit[E, A], onCommit: List[ZIO[Any, Nothing, Any]]) extends TryCommit[E, A]
      final case class Suspend(journal: Journal)                                              extends TryCommit[Nothing, Nothing]
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
