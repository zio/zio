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

import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong }
import java.util.{ HashMap => MutableMap }

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

import com.github.ghik.silencer.silent

import zio.internal.{ Platform, Stack, Sync }
import zio.{ CanFail, Fiber, IO, UIO, ZIO }

/**
 * `STM[E, A]` represents an effect that can be performed transactionally,
 * resulting in a failure `E` or a value `A`.
 *
 * {{{
 * def transfer(receiver: TRef[Int],
 *              sender: TRef[Int], much: Int): UIO[Int] =
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
 * Software Transactional Memory is a technique which allows composition
 *  of arbitrary atomic operations. It is the software analog of transactions in database systems.
 *
 * The API is lifted directly from the Haskell package Control.Concurrent.STM although the implementation does not
 *  resemble the Haskell one at all.
 *  [[http://hackage.haskell.org/package/stm-2.5.0.0/docs/Control-Concurrent-STM.html]]
 *
 *  STM in Haskell was introduced in:
 *  Composable memory transactions, by Tim Harris, Simon Marlow, Simon Peyton Jones, and Maurice Herlihy, in ACM
 *  Conference on Principles and Practice of Parallel Programming 2005.
 * [[https://www.microsoft.com/en-us/research/publication/composable-memory-transactions/]]
 *
 * See also:
 * Lock Free Data Structures using STMs in Haskell, by Anthony Discolo, Tim Harris, Simon Marlow, Simon Peyton Jones,
 * Satnam Singh) FLOPS 2006: Eighth International Symposium on Functional and Logic Programming, Fuji Susono, JAPAN,
 *  April 2006
 *  [[https://www.microsoft.com/en-us/research/publication/lock-free-data-structures-using-stms-in-haskell/]]
 *
 */
final class ZSTM[-R, +E, +A] private[stm] (
  private val exec: (ZSTM.internal.Journal, Fiber.Id, AtomicLong, R) => ZSTM.internal.TExit[E, A]
) extends AnyVal { self =>
  import ZSTM.internal.{ prepareResetJournal, TExit }

  /**
   * Sequentially zips this value with the specified one.
   */
  def <*>[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, (A, B)] =
    self zip that

  /**
   * Sequentially zips this value with the specified one, discarding the
   * second element of the tuple.
   */
  def <*[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, A] =
    self zipLeft that

  /**
   * Sequentially zips this value with the specified one, discarding the
   * first element of the tuple.
   */
  def *>[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, B] =
    self zipRight that

  /**
   * Feeds the value produced by this effect to the specified function,
   * and then runs the returned effect as well to produce its results.
   */
  def >>=[R1 <: R, E1 >: E, B](f: A => ZSTM[R1, E1, B]): ZSTM[R1, E1, B] =
    self flatMap f

  /**
   * Tries this effect first, and if it fails, tries the other effect.
   */
  def <>[R1 <: R, E1, A1 >: A](that: => ZSTM[R1, E1, A1])(implicit ev: CanFail[E]): ZSTM[R1, E1, A1] =
    orElse(that)

  /**
   * Propagates the given environment to self
   */
  def >>>[R1 >: A, E1 >: E, B](that: ZSTM[R1, E1, B]): ZSTM[R, E1, B] =
    flatMap(that.provide)

  /**
   * Propagates self environment to that
   */
  def <<<[R1, E1 >: E](that: ZSTM[R1, E1, R]): ZSTM[R1, E1, A] =
    that >>> self

  /**
   * Returns an effect that submerges the error case of an `Either` into the
   * `STM`. The inverse operation of `STM.either`.
   */
  def absolve[R1 <: R, E1, B](implicit ev1: ZSTM[R, E, A] <:< ZSTM[R1, E1, Either[E1, B]]): ZSTM[R1, E1, B] =
    ZSTM.absolve[R1, E1, B](ev1(self))

  /**
   * Name alias for >>>
   */
  def andThen[R1 >: A, E1 >: E, B](that: ZSTM[R1, E1, B]): ZSTM[R, E1, B] =
    self >>> that

  /**
   * Maps the success value of this effect to the specified constant value.
   */
  def as[B](b: => B): ZSTM[R, E, B] = self map (_ => b)

  /**
   * Maps the error value of this effect to the specified constant value.
   */
  def asError[E1](e: => E1)(implicit ev: CanFail[E]): ZSTM[R, E1, A] =
    self mapError (_ => e)

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
   * Returns an `STM` effect whose failure and success channels have been mapped by
   * the specified pair of functions, `f` and `g`.
   */
  def bimap[E2, B](f: E => E2, g: A => B)(implicit ev: CanFail[E]): ZSTM[R, E2, B] =
    foldM(e => ZSTM.failNow(f(e)), a => ZSTM.succeedNow(g(a)))

  /**
   * Recovers from all errors.
   */
  def catchAll[R1 <: R, E2, A1 >: A](h: E => ZSTM[R1, E2, A1])(implicit ev: CanFail[E]): ZSTM[R1, E2, A1] =
    foldM[R1, E2, A1](h, ZSTM.succeedNow)

  /**
   * Simultaneously filters and maps the value produced by this effect.
   */
  def collect[B](pf: PartialFunction[A, B]): ZSTM[R, E, B] =
    collectM(pf.andThen(ZSTM.succeedNow(_)))

  /**
   * Simultaneously filters and flatMaps the value produced by this effect.
   * Continues on the effect returned from pf.
   */
  def collectM[R1 <: R, E1 >: E, B](pf: PartialFunction[A, ZSTM[R1, E1, B]]): ZSTM[R1, E1, B] =
    self.continueWithM {
      case TExit.Fail(e)    => ZSTM.failNow(e)
      case TExit.Succeed(a) => if (pf.isDefinedAt(a)) pf(a) else ZSTM.retry
      case TExit.Retry      => ZSTM.retry
    }

  def compose[R1, E1 >: E](that: ZSTM[R1, E1, R]): ZSTM[R1, E1, A] =
    self <<< that

  /**
   * Commits this transaction atomically.
   */
  def commit: ZIO[R, E, A] = ZSTM.atomically(self)

  /**
   * Commits this transaction atomically, regardless of whether the transaction
   * is a success or a failure.
   */
  def commitEither: ZIO[R, E, A] =
    either.commit.absolve

  /**
   * Repeats this `STM` effect until its result satisfies the specified predicate.
   */
  def doUntil(f: A => Boolean): ZSTM[R, E, A] =
    flatMap(a => if (f(a)) ZSTM.succeedNow(a) else doUntil(f))

  /**
   * Repeats this `STM` effect while its result satisfies the specified predicate.
   */
  def doWhile(f: A => Boolean): ZSTM[R, E, A] =
    flatMap(a => if (f(a)) doWhile(f) else ZSTM.succeedNow(a))

  /**
   * Converts the failure channel into an `Either`.
   */
  def either(implicit ev: CanFail[E]): ZSTM[R, Nothing, Either[E, A]] =
    fold(Left(_), Right(_))

  /**
   * Executes the specified finalization transaction whether or
   * not this effect succeeds. Note that as with all STM transactions,
   * if the full transaction fails, everything will be rolled back.
   */
  def ensuring[R1 <: R](finalizer: ZSTM[R1, Nothing, Any]): ZSTM[R1, E, A] =
    foldM(e => finalizer *> ZSTM.failNow(e), a => finalizer *> ZSTM.succeedNow(a))

  /**
   * Returns an effect that ignores errors and runs repeatedly until it eventually succeeds.
   */
  def eventually(implicit ev: CanFail[E]): ZSTM[R, Nothing, A] =
    foldM(_ => eventually, ZSTM.succeedNow)

  /**
   * Tries this effect first, and if it fails, succeeds with the specified
   * value.
   */
  def fallback[A1 >: A](a: => A1)(implicit ev: CanFail[E]): ZSTM[R, Nothing, A1] =
    fold(_ => a, identity)

  /**
   * Feeds the value produced by this effect to the specified function,
   * and then runs the returned effect as well to produce its results.
   */
  def flatMap[R1 <: R, E1 >: E, B](f: A => ZSTM[R1, E1, B]): ZSTM[R1, E1, B] =
    self.continueWithM {
      case TExit.Succeed(a) => f(a)
      case TExit.Fail(e)    => ZSTM.failNow(e)
      case TExit.Retry      => ZSTM.retry
    }

  /**
   * Creates a composite effect that represents this effect followed by another
   * one that may depend on the error produced by this one.
   */
  def flatMapError[R1 <: R, E2](f: E => ZSTM[R1, Nothing, E2])(implicit ev: CanFail[E]): ZSTM[R1, E2, A] =
    foldM(e => f(e).flip, ZSTM.succeedNow)

  /**
   * Flattens out a nested `STM` effect.
   */
  def flatten[R1 <: R, E1 >: E, B](implicit ev: A <:< ZSTM[R1, E1, B]): ZSTM[R1, E1, B] =
    self flatMap ev

  /**
   * Unwraps the optional error, defaulting to the provided value.
   */
  def flattenErrorOption[E1, E2 <: E1](default: E2)(implicit ev: E <:< Option[E1]): ZSTM[R, E1, A] =
    mapError(e => ev(e).getOrElse(default))

  /**
   * Flips the success and failure channels of this transactional effect. This
   * allows you to use all methods on the error channel, possibly before
   * flipping back.
   */
  def flip(implicit ev: CanFail[E]): ZSTM[R, A, E] =
    foldM(ZSTM.succeedNow, ZSTM.failNow)

  /**
   *  Swaps the error/value parameters, applies the function `f` and flips the parameters back
   */
  def flipWith[R1, A1, E1](f: ZSTM[R, A, E] => ZSTM[R1, A1, E1]): ZSTM[R1, E1, A1] =
    f(flip).flip

  /**
   * Folds over the `STM` effect, handling both failure and success, but not
   * retry.
   */
  def fold[B](f: E => B, g: A => B)(implicit ev: CanFail[E]): ZSTM[R, Nothing, B] =
    self.continueWithM {
      case TExit.Fail(e)    => ZSTM.succeedNow(f(e))
      case TExit.Succeed(a) => ZSTM.succeedNow(g(a))
      case TExit.Retry      => ZSTM.retry
    }

  /**
   * Effectfully folds over the `STM` effect, handling both failure and
   * success.
   */
  def foldM[R1 <: R, E1, B](f: E => ZSTM[R1, E1, B], g: A => ZSTM[R1, E1, B])(
    implicit ev: CanFail[E]
  ): ZSTM[R1, E1, B] =
    self.continueWithM {
      case TExit.Fail(e)    => f(e)
      case TExit.Succeed(a) => g(a)
      case TExit.Retry      => ZSTM.retry
    }

  /**
   * Returns a new effect that ignores the success or failure of this effect.
   */
  def ignore: ZSTM[R, Nothing, Unit] = self.fold(_ => (), _ => ())

  /**
   * Maps the value produced by the effect.
   */
  def map[B](f: A => B): ZSTM[R, E, B] =
    self.continueWithM {
      case TExit.Succeed(a) => ZSTM.succeedNow(f(a))
      case TExit.Fail(e)    => ZSTM.failNow(e)
      case TExit.Retry      => ZSTM.retry
    }

  /**
   * Maps from one error type to another.
   */
  def mapError[E1](f: E => E1)(implicit ev: CanFail[E]): ZSTM[R, E1, A] =
    self.continueWithM {
      case TExit.Succeed(a) => ZSTM.succeedNow(a)
      case TExit.Fail(e)    => ZSTM.failNow(f(e))
      case TExit.Retry      => ZSTM.retry
    }

  /**
   * Converts the failure channel into an `Option`.
   */
  def option(implicit ev: CanFail[E]): ZSTM[R, Nothing, Option[A]] =
    fold(_ => None, Some(_))

  /**
   * Named alias for `<>`.
   */
  def orElse[R1 <: R, E1, A1 >: A](that: => ZSTM[R1, E1, A1])(implicit ev: CanFail[E]): ZSTM[R1, E1, A1] =
    new ZSTM(
      (journal, fiberId, stackSize, r) => {
        val reset = prepareResetJournal(journal)

        val continueM: TExit[E, A] => STM[E1, A1] = {
          case TExit.Fail(_)    => { reset(); that.provide(r) }
          case TExit.Succeed(a) => ZSTM.succeedNow(a)
          case TExit.Retry      => { reset(); that.provide(r) }
        }

        val framesCount = stackSize.incrementAndGet()

        if (framesCount > ZSTM.MaxFrames) {
          throw new ZSTM.Resumable(self.provide(r), Stack(continueM))
        } else {
          val continued =
            try {
              continueM(self.exec(journal, fiberId, stackSize, r))
            } catch {
              case res: ZSTM.Resumable[e, e1, a, b] =>
                res.ks.push(continueM.asInstanceOf[TExit[e, a] => STM[e1, b]])
                throw res
            }

          continued.exec(journal, fiberId, stackSize, r)
        }
      }
    )

  /**
   * Returns a transactional effect that will produce the value of this effect in left side, unless it
   * fails, in which case, it will produce the value of the specified effect in right side.
   */
  def orElseEither[R1 <: R, E1 >: E, B](
    that: => ZSTM[R1, E1, B]
  )(implicit ev: CanFail[E]): ZSTM[R1, E1, Either[A, B]] =
    (self map (Left[A, B](_))) orElse (that map (Right[A, B](_)))

  /**
   * Tries this effect first, and if it fails, fails with the specified error.
   */
  def orElseFail[E1](e1: => E1)(implicit ev: CanFail[E]): ZSTM[R, E1, A] =
    orElse(ZSTM.failNow(e1))

  /**
   * Tries this effect first, and if it fails, succeeds with the specified
   * value.
   */
  def orElseSucceed[A1 >: A](a1: => A1)(implicit ev: CanFail[E]): ZSTM[R, Nothing, A1] =
    orElse(ZSTM.succeedNow(a1))

  /**
   * Provides the transaction its required environment, which eliminates
   * its dependency on `R`.
   */
  def provide(r: R): ZSTM[Any, E, A] =
    provideSome(_ => r)

  /**
   * Provides some of the environment required to run this effect,
   * leaving the remainder `R0`.
   */
  def provideSome[R0](f: R0 => R): ZSTM[R0, E, A] =
    new ZSTM(
      (journal, fiberId, stackSize, r0) => {

        val framesCount = stackSize.incrementAndGet()

        if (framesCount > ZSTM.MaxFrames) {
          throw new ZSTM.Resumable(
            new ZSTM(
              (journal, fiberId, stackSize, _) => self.exec(journal, fiberId, stackSize, f(r0))
            ),
            Stack[TExit[E, A] => STM[E, A]]()
          )
        } else {
          // no need to catch resumable here
          self.exec(journal, fiberId, stackSize, f(r0))
        }
      }
    )

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
   * "Peeks" at the success of transactional effect.
   */
  def tap[R1 <: R, E1 >: E](f: A => ZSTM[R1, E1, Any]): ZSTM[R1, E1, A] =
    flatMap(a => f(a).as(a))

  /**
   * Maps the success value of this effect to unit.
   */
  def unit: ZSTM[R, E, Unit] = as(())

  /**
   * The moral equivalent of `if (p) exp`
   */
  def when(b: Boolean): ZSTM[R, E, Unit] = ZSTM.when(b)(self)

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  def whenM[R1 <: R, E1 >: E](b: ZSTM[R1, E1, Boolean]): ZSTM[R1, E1, Unit] = ZSTM.whenM(b)(self)

  /**
   * Same as [[retryUntil]].
   */
  def withFilter(f: A => Boolean): ZSTM[R, E, A] = retryUntil(f)

  /**
   * Named alias for `<*>`.
   */
  def zip[R1 <: R, E1 >: E, B](that: => ZSTM[R1, E1, B]): ZSTM[R1, E1, (A, B)] =
    (self zipWith that)((a, b) => a -> b)

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

  private def continueWithM[R1 <: R, E1, B](continueM: TExit[E, A] => ZSTM[R1, E1, B]): ZSTM[R1, E1, B] =
    new ZSTM(
      (journal, fiberId, stackSize, r) => {
        val framesCount = stackSize.incrementAndGet()

        if (framesCount > ZSTM.MaxFrames) {
          throw new ZSTM.Resumable(self.provide(r), Stack(continueM.andThen(_.provide(r))))
        } else {
          val continued =
            try {
              continueM(self.exec(journal, fiberId, stackSize, r))
            } catch {
              case res: ZSTM.Resumable[e, e1, a, b] =>
                res.ks.push(continueM.asInstanceOf[TExit[e, a] => STM[e1, b]])
                throw res
            }

          continued.exec(journal, fiberId, stackSize, r)
        }
      }
    )

  private def run(journal: ZSTM.internal.Journal, fiberId: Fiber.Id, r: R): TExit[E, A] = {
    type Cont = ZSTM.internal.TExit[Any, Any] => STM[Any, Any]

    val stackSize = new AtomicLong()
    val stack     = new Stack[Cont]()
    var current   = self.asInstanceOf[ZSTM[R, Any, Any]]
    var result    = null: TExit[Any, Any]

    while (result eq null) {
      try {
        val v = current.exec(journal, fiberId, stackSize, r)

        if (stack.isEmpty)
          result = v
        else {
          val next = stack.pop()
          current = next(v)
        }
      } catch {
        case cont: ZSTM.Resumable[_, _, _, _] =>
          current = cont.stm
          while (!cont.ks.isEmpty) stack.push(cont.ks.pop().asInstanceOf[Cont])
          stackSize.set(0)
      }
    }
    result.asInstanceOf[TExit[E, A]]
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
   * Accesses the environment of the transaction.
   */
  def access[R]: AccessPartiallyApplied[R] =
    new AccessPartiallyApplied

  /**
   * Accesses the environment of the transaction to perform a transaction.
   */
  def accessM[R]: AccessMPartiallyApplied[R] =
    new AccessMPartiallyApplied

  /**
   * Atomically performs a batch of operations in a single transaction.
   */
  def atomically[R, E, A](stm: ZSTM[R, E, A]): ZIO[R, E, A] =
    ZIO.accessM[R] { r =>
      ZIO.effectSuspendTotalWith { (platform, fiberId) =>
        tryCommit(platform, fiberId, stm, r) match {
          case TryCommit.Done(io) => io // TODO: Interruptible in Suspend
          case TryCommit.Suspend(journal) =>
            val txnId     = makeTxnId()
            val done      = new AtomicBoolean(false)
            val interrupt = UIO(Sync(done) { done.set(true) })
            val async     = ZIO.effectAsync(tryCommitAsync(journal, platform, fiberId, stm, txnId, done, r))

            async ensuring interrupt
        }
      }
    }

  /**
   * Checks the condition, and if it's true, returns unit, otherwise, retries.
   */
  def check[R](p: => Boolean): ZSTM[R, Nothing, Unit] =
    suspend(if (p) STM.unit else retry)

  /**
   * Collects all the transactional effects in a list, returning a single
   * transactional effect that produces a list of values.
   */
  def collectAll[R, E, A](i: Iterable[STM[E, A]]): ZSTM[R, E, List[A]] =
    i.foldRight[STM[E, List[A]]](STM.succeedNow(Nil))(_.zipWith(_)(_ :: _))

  /**
   * Kills the fiber running the effect.
   */
  def die(t: => Throwable): STM[Nothing, Nothing] =
    succeed(throw t)

  /**
   * Kills the fiber running the effect with a `RuntimeException` that contains
   * the specified message.
   */
  def dieMessage(m: => String): STM[Nothing, Nothing] =
    die(new RuntimeException(m))

  /**
   * Returns a value modelled on provided exit status.
   */
  def done[R, E, A](exit: => TExit[E, A]): ZSTM[R, E, A] =
    suspend(doneNow(exit))

  /**
   * Retrieves the environment inside an stm.
   */
  def environment[R]: ZSTM[R, Nothing, R] =
    new ZSTM((_, _, _, r) => TExit.Succeed(r))

  /**
   * Returns a value that models failure in the transaction.
   */
  def fail[E](e: => E): ZSTM[Any, E, Nothing] =
    new ZSTM((_, _, _, _) => TExit.Fail(e))

  /**
   * Returns the fiber id of the fiber committing the transaction.
   */
  val fiberId: ZSTM[Any, Nothing, Fiber.Id] = new ZSTM((_, fiberId, _, _) => TExit.Succeed(fiberId))

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns a transactional effect that produces a new `List[B]`.
   */
  def foreach[R, E, A, B](as: Iterable[A])(f: A => STM[E, B]): ZSTM[R, E, List[B]] =
    collectAll(as.map(f))

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns a transactional effect that produces `Unit`.
   *
   * Equivalent to `foreach(as)(f).unit`, but without the cost of building
   * the list of results.
   */
  def foreach_[R, E, A, B](as: Iterable[A])(f: A => STM[E, B]): ZSTM[R, E, Unit] =
    ZSTM.succeedNow(as.iterator).flatMap[R, E, Unit] { it =>
      def loop: ZSTM[R, E, Unit] =
        if (it.hasNext) f(it.next) *> loop
        else ZSTM.unit
      loop
    }

  /**
   * Creates an STM effect from an `Either` value.
   */
  def fromEither[E, A](e: => Either[E, A]): STM[E, A] =
    STM.suspend {
      e match {
        case Left(t)  => STM.failNow(t)
        case Right(a) => STM.succeedNow(a)
      }
    }

  /**
   * Creates an STM effect from a `Try` value.
   */
  def fromTry[A](a: => Try[A]): STM[Throwable, A] =
    STM.suspend {
      Try(a).flatten match {
        case Failure(t) => STM.failNow(t)
        case Success(a) => STM.succeedNow(a)
      }
    }

  /**
   * Runs `onTrue` if the result of `b` is `true` and `onFalse` otherwise.
   */
  def ifM[R, E](b: ZSTM[R, E, Boolean]): ZSTM.IfM[R, E] =
    new ZSTM.IfM(b)

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
  def loop_[R, E, S](initial: S)(cont: S => Boolean, inc: S => S)(body: S => ZSTM[R, E, Any]): ZSTM[R, E, Unit] =
    if (cont(initial)) body(initial) *> loop_(inc(initial))(cont, inc)(body)
    else ZSTM.unit

  /**
   * Creates an `STM` value from a partial (but pure) function.
   */
  def partial[A](a: => A): STM[Throwable, A] = fromTry(Try(a))

  /**
   * Abort and retry the whole transaction when any of the underlying
   * transactional variables have changed.
   */
  val retry: ZSTM[Any, Nothing, Nothing] = new ZSTM((_, _, _, _) => TExit.Retry)

  /**
   * Returns an `STM` effect that succeeds with the specified value.
   */
  def succeed[A](a: => A): ZSTM[Any, Nothing, A] =
    new ZSTM((_, _, _, _) => TExit.Succeed(a))

  /**
   * Suspends creation of the specified transaction lazily.
   */
  def suspend[R, E, A](stm: => ZSTM[R, E, A]): ZSTM[R, E, A] =
    STM.succeed(stm).flatten

  /**
   * Returns an `STM` effect that succeeds with `Unit`.
   */
  val unit: STM[Nothing, Unit] = succeedNow(())

  /**
   * The moral equivalent of `if (p) exp`
   */
  def when[R, E](b: => Boolean)(stm: ZSTM[R, E, Any]): ZSTM[R, E, Unit] =
    suspend(if (b) stm.unit else unit)

  /**
   * The moral equivalent of `if (p) exp` when `p` has side-effects
   */
  def whenM[R, E](b: ZSTM[R, E, Boolean])(stm: ZSTM[R, E, Any]): ZSTM[R, E, Unit] =
    b.flatMap(b => if (b) stm.unit else unit)

  private[zio] def dieNow(t: Throwable): STM[Nothing, Nothing] =
    succeedNow(throw t)

  private[zio] def doneNow[E, A](exit: TExit[E, A]): STM[E, A] =
    exit match {
      case TExit.Retry      => STM.retry
      case TExit.Fail(e)    => STM.failNow(e)
      case TExit.Succeed(a) => STM.succeedNow(a)
    }

  private[zio] def failNow[E](e: E): ZSTM[Any, E, Nothing] =
    fail(e)

  private[zio] def succeedNow[A](a: A): ZSTM[Any, Nothing, A] =
    succeed(a)

  final class AccessPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[A](f: R => A): ZSTM[R, Nothing, A] =
      ZSTM.environment.map(f)
  }

  final class AccessMPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    def apply[E, A](f: R => ZSTM[R, E, A]): ZSTM[R, E, A] =
      ZSTM.environment.flatMap(f)
  }

  final class IfM[R, E](private val b: ZSTM[R, E, Boolean]) {
    def apply[R1 <: R, E1 >: E, A](onTrue: ZSTM[R1, E1, A], onFalse: ZSTM[R1, E1, A]): ZSTM[R1, E1, A] =
      b.flatMap(b => if (b) onTrue else onFalse)
  }

  private final class Resumable[E, E1, A, B](
    val stm: STM[E, A],
    val ks: Stack[internal.TExit[E, A] => STM[E1, B]]
  ) extends Throwable(null, null, false, false)

  private val MaxFrames = 200

  private[stm] object internal {
    val DefaultJournalSize = 4

    class Versioned[A](val value: A)

    type TxnId = Long

    type Journal =
      MutableMap[TRef[_], ZSTM.internal.Entry]

    type Todo = () => Unit

    /**
     * Creates a function that can reset the journal.
     */
    def prepareResetJournal(journal: Journal): () => Unit = {
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
     * Allocates memory for the journal, if it is null, otherwise just clears it.
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
     * read only in a single pass. Note that information on whether the
     * journal is read only will only be accurate if the journal is valid, due
     * to short-circuiting that occurs on an invalid journal.
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

    sealed trait JournalAnalysis extends Serializable with Product
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

          if (!loop) allTodos.putAll(oldTodo.asJava): @silent("JavaConverters")
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
    def completeTodos[E, A](io: IO[E, A], journal: Journal, platform: Platform): TryCommit[E, A] = {
      val todos = collectTodos(journal)

      if (todos.size > 0) platform.executor.submitOrThrow(() => execTodos(todos))

      TryCommit.Done(io)
    }

    /**
     * Finds all the new todo targets that are not already tracked in the `oldJournal`.
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

    def tryCommitAsync[R, E, A](
      journal: Journal,
      platform: Platform,
      fiberId: Fiber.Id,
      stm: ZSTM[R, E, A],
      txnId: TxnId,
      done: AtomicBoolean,
      r: R
    )(
      k: ZIO[R, E, A] => Unit
    ): Unit = {
      def complete(io: IO[E, A]): Unit = { done.set(true); k(io) }

      @tailrec
      def suspend(accum: Journal, journal: Journal): Unit = {
        addTodo(txnId, journal, () => tryCommitAsync(null, platform, fiberId, stm, txnId, done, r)(k))

        if (isInvalid(journal)) tryCommit(platform, fiberId, stm, r) match {
          case TryCommit.Done(io) => complete(io)
          case TryCommit.Suspend(journal2) =>
            val untracked = untrackedTodoTargets(accum, journal2)

            if (untracked.size > 0) {
              accum.putAll(untracked)

              suspend(accum, untracked)
            }
        }
      }

      Sync(done) {
        if (!done.get) {
          if (journal ne null) suspend(journal, journal)
          else
            tryCommit(platform, fiberId, stm, r) match {
              case TryCommit.Done(io)         => complete(io)
              case TryCommit.Suspend(journal) => suspend(journal, journal)
            }
        }
      }
    }

    def tryCommit[R, E, A](platform: Platform, fiberId: Fiber.Id, stm: ZSTM[R, E, A], r: R): TryCommit[E, A] = {
      var journal = null.asInstanceOf[MutableMap[TRef[_], Entry]]
      var value   = null.asInstanceOf[TExit[E, A]]

      var loop = true

      while (loop) {
        journal = allocJournal(journal)
        value = stm.run(journal, fiberId, r)

        val analysis = analyzeJournal(journal)

        if (analysis ne JournalAnalysis.Invalid) {
          loop = false

          value match {
            case _: TExit.Succeed[_] =>
              if (analysis eq JournalAnalysis.ReadWrite) {
                Sync(globalLock) {
                  if (isValid(journal)) commitJournal(journal) else loop = true
                }
              } else {
                Sync(globalLock) {
                  if (isInvalid(journal)) loop = true
                }
              }

            case _ =>
          }
        }
      }

      value match {
        case TExit.Succeed(a) => completeTodos(IO.succeedNow(a), journal, platform)
        case TExit.Fail(e)    => completeTodos(IO.failNow(e), journal, platform)
        case TExit.Retry      => TryCommit.Suspend(journal)
      }
    }

    def makeTxnId(): Long = txnCounter.incrementAndGet()

    private[this] val txnCounter: AtomicLong = new AtomicLong()

    val globalLock = new AnyRef {}

    sealed trait TExit[+A, +B] extends Serializable with Product
    object TExit {
      final case class Fail[+A](value: A)    extends TExit[A, Nothing]
      final case class Succeed[+B](value: B) extends TExit[Nothing, B]
      case object Retry                      extends TExit[Nothing, Nothing]
    }

    abstract class Entry { self =>
      type A

      val tref: TRef[A]

      protected[this] val expected: Versioned[A]
      protected[this] var newValue: A

      val isNew: Boolean

      private[this] var _isChanged = false

      def unsafeSet(value: Any): Unit = {
        _isChanged = true
        newValue = value.asInstanceOf[A]
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
        type A = self.A
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
       * Determines if the entry is valid. That is, if the version of the
       * `TRef` is equal to the expected version.
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
      def apply[A0](tref0: TRef[A0], isNew0: Boolean): Entry = {
        val versioned = tref0.versioned

        new Entry {
          type A = A0
          val tref     = tref0
          val isNew    = isNew0
          val expected = versioned
          var newValue = versioned.value
        }
      }
    }

    sealed abstract class TryCommit[+E, +A]
    object TryCommit {
      final case class Done[+E, +A](io: IO[E, A]) extends TryCommit[E, A]
      final case class Suspend(journal: Journal)  extends TryCommit[Nothing, Nothing]
    }
  }
}
