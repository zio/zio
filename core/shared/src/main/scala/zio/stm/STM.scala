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

<<<<<<< HEAD
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong }
import java.util.{ HashMap => MutableMap }

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }

import com.github.ghik.silencer.silent

import zio.internal.{ Platform, Stack, Sync }
import zio.{ CanFail, Fiber, IO, UIO }

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
final class STM[+E, +A] private[stm] (
  private val exec: (STM.internal.Journal, Fiber.Id, AtomicLong) => STM.internal.TExit[E, A]
) extends AnyVal { self =>
  import STM.internal.{ prepareResetJournal, TExit }

  /**
   * Sequentially zips this value with the specified one.
   */
  def <*>[E1 >: E, B](that: => STM[E1, B]): STM[E1, (A, B)] =
    self zip that

  /**
   * Sequentially zips this value with the specified one, discarding the
   * second element of the tuple.
   */
  def <*[E1 >: E, B](that: => STM[E1, B]): STM[E1, A] =
    self zipLeft that

  /**
   * Sequentially zips this value with the specified one, discarding the
   * first element of the tuple.
   */
  def *>[E1 >: E, B](that: => STM[E1, B]): STM[E1, B] =
    self zipRight that

  /**
   * Feeds the value produced by this effect to the specified function,
   * and then runs the returned effect as well to produce its results.
   */
  def >>=[E1 >: E, B](f: A => STM[E1, B]): STM[E1, B] =
    self flatMap f

  /**
   * Maps the success value of this effect to the specified constant value.
   */
  def as[B](b: => B): STM[E, B] = self map (_ => b)

  /**
   * Maps the error value of this effect to the specified constant value.
   */
  def asError[E1](e: => E1)(implicit ev: CanFail[E]): STM[E1, A] =
    self mapError (_ => e)

  /**
   * Simultaneously filters and maps the value produced by this effect.
   */
  def collect[B](pf: PartialFunction[A, B]): STM[E, B] =
    collectM(pf.andThen(STM.succeed(_)))

  /**
   * Simultaneously filters and flatMaps the value produced by this effect.
   * Continues on the effect returned from pf.
   */
  def collectM[E1 >: E, B](pf: PartialFunction[A, STM[E1, B]]): STM[E1, B] =
    self.continueWithM {
      case TExit.Fail(e)    => STM.fail(e)
      case TExit.Succeed(a) => if (pf.isDefinedAt(a)) pf(a) else STM.retry
      case TExit.Retry      => STM.retry
    }

  /**
   * Commits this transaction atomically.
   */
  def commit: IO[E, A] = STM.atomically(self)

  /**
   * Converts the failure channel into an `Either`.
   */
  def either(implicit ev: CanFail[E]): STM[Nothing, Either[E, A]] =
    fold(Left(_), Right(_))

  /**
   * Executes the specified finalization transaction whether or
   * not this effect succeeds. Note that as with all STM transactions,
   * if the full transaction fails, everything will be rolled back.
   */
  def ensuring(finalizer: STM[Nothing, Any]): STM[E, A] =
    foldM(e => finalizer *> STM.fail(e), a => finalizer *> STM.succeed(a))

  /**
   * Tries this effect first, and if it fails, succeeds with the specified
   * value.
   */
  def fallback[A1 >: A](a: => A1)(implicit ev: CanFail[E]): STM[Nothing, A1] =
    fold(_ => a, identity)

  /**
   * Filters the value produced by this effect, retrying the transaction until
   * the predicate returns true for the value.
   */
  def filter(f: A => Boolean): STM[E, A] =
    collect {
      case a if f(a) => a
    }

  /**
   * Feeds the value produced by this effect to the specified function,
   * and then runs the returned effect as well to produce its results.
   */
  def flatMap[E1 >: E, B](f: A => STM[E1, B]): STM[E1, B] =
    self.continueWithM {
      case TExit.Succeed(a) => f(a)
      case TExit.Fail(e)    => STM.fail(e)
      case TExit.Retry      => STM.retry
    }

  /**
   * Flattens out a nested `STM` effect.
   */
  def flatten[E1 >: E, B](implicit ev: A <:< STM[E1, B]): STM[E1, B] =
    self flatMap ev

  /**
   * Folds over the `STM` effect, handling both failure and success, but not
   * retry.
   */
  def fold[B](f: E => B, g: A => B)(implicit ev: CanFail[E]): STM[Nothing, B] =
    self.continueWithM {
      case TExit.Fail(e)    => STM.succeed(f(e))
      case TExit.Succeed(a) => STM.succeed(g(a))
      case TExit.Retry      => STM.retry
    }

  /**
   * Effectfully folds over the `STM` effect, handling both failure and
   * success.
   */
  def foldM[E1, B](f: E => STM[E1, B], g: A => STM[E1, B])(implicit ev: CanFail[E]): STM[E1, B] =
    self.continueWithM {
      case TExit.Fail(e)    => f(e)
      case TExit.Succeed(a) => g(a)
      case TExit.Retry      => STM.retry
    }

  /**
   * Returns a new effect that ignores the success or failure of this effect.
   */
  def ignore: STM[Nothing, Unit] = self.either.unit

  /**
   * Maps the value produced by the effect.
   */
  def map[B](f: A => B): STM[E, B] =
    self.continueWithM {
      case TExit.Succeed(a) => STM.succeed(f(a))
      case TExit.Fail(e)    => STM.fail(e)
      case TExit.Retry      => STM.retry
    }

  /**
   * Maps from one error type to another.
   */
  def mapError[E1](f: E => E1)(implicit ev: CanFail[E]): STM[E1, A] =
    self.continueWithM {
      case TExit.Succeed(a) => STM.succeed(a)
      case TExit.Fail(e)    => STM.fail(f(e))
      case TExit.Retry      => STM.retry
    }

  /**
   * Converts the failure channel into an `Option`.
   */
  def option(implicit ev: CanFail[E]): STM[Nothing, Option[A]] =
    fold(_ => None, Some(_))

  /**
   * Tries this effect first, and if it fails, tries the other effect.
   */
  def orElse[E1, A1 >: A](that: => STM[E1, A1])(implicit ev: CanFail[E]): STM[E1, A1] =
    new STM(
      (journal, fiberId, stackSize) => {
        val reset = prepareResetJournal(journal)

        val continueM: TExit[E, A] => STM[E1, A1] = {
          case TExit.Fail(_)    => { reset(); that }
          case TExit.Succeed(a) => STM.succeed(a)
          case TExit.Retry      => { reset(); that }
        }

        val framesCount = stackSize.incrementAndGet()

        if (framesCount > STM.MaxFrames) {
          throw new STM.Resumable(self, Stack(continueM))
        } else {
          val continued =
            try {
              continueM(self.exec(journal, fiberId, stackSize))
            } catch {
              case res: STM.Resumable[e, e1, a, b] =>
                res.ks.push(continueM.asInstanceOf[TExit[e, a] => STM[e1, b]])
                throw res
            }

          continued.exec(journal, fiberId, stackSize)
        }
      }
    )

  /**
   * Returns a transactional effect that will produce the value of this effect in left side, unless it
   * fails, in which case, it will produce the value of the specified effect in right side.
   */
  def orElseEither[E1 >: E, B](that: => STM[E1, B])(implicit ev: CanFail[E]): STM[E1, Either[A, B]] =
    (self map (Left[A, B](_))) orElse (that map (Right[A, B](_)))

  /**
   * Maps the success value of this effect to unit.
   */
  def unit: STM[E, Unit] = as(())

  /**
   * Same as [[filter]]
   */
  def withFilter(f: A => Boolean): STM[E, A] = filter(f)

  /**
   * Named alias for `<*>`.
   */
  def zip[E1 >: E, B](that: => STM[E1, B]): STM[E1, (A, B)] =
    (self zipWith that)((a, b) => a -> b)

  /**
   * Named alias for `<*`.
   */
  def zipLeft[E1 >: E, B](that: => STM[E1, B]): STM[E1, A] =
    (self zip that) map (_._1)

  /**
   * Named alias for `*>`.
   */
  def zipRight[E1 >: E, B](that: => STM[E1, B]): STM[E1, B] =
    (self zip that) map (_._2)

  /**
   * Sequentially zips this value with the specified one, combining the values
   * using the specified combiner function.
   */
  def zipWith[E1 >: E, B, C](that: => STM[E1, B])(f: (A, B) => C): STM[E1, C] =
    self flatMap (a => that map (b => f(a, b)))

  private def continueWithM[E1, B](continueM: TExit[E, A] => STM[E1, B]): STM[E1, B] =
    new STM(
      (journal, fiberId, stackSize) => {
        val framesCount = stackSize.incrementAndGet()

        if (framesCount > STM.MaxFrames) {
          throw new STM.Resumable(self, Stack(continueM))
        } else {
          val continued =
            try {
              continueM(self.exec(journal, fiberId, stackSize))
            } catch {
              case res: STM.Resumable[e, e1, a, b] =>
                res.ks.push(continueM.asInstanceOf[TExit[e, a] => STM[e1, b]])
                throw res
            }

          continued.exec(journal, fiberId, stackSize)
        }
      }
    )

  private def run(journal: STM.internal.Journal, fiberId: Fiber.Id): TExit[E, A] = {
    type Cont = TExit[Any, Any] => STM[Any, Any]

    val stackSize = new AtomicLong()
    val stack     = new Stack[Cont]()
    var current   = self.asInstanceOf[STM[Any, Any]]
    var result    = null: AnyRef

    while (result eq null) {
      try {
        val v = current.exec(journal, fiberId, stackSize)

        if (stack.isEmpty)
          result = v
        else {
          val next = stack.pop()
          current = next(v)
        }
      } catch {
        case cont: STM.Resumable[_, _, _, _] =>
          current = cont.stm

          while (!cont.ks.isEmpty) stack.push(cont.ks.pop().asInstanceOf[Cont])

          stackSize.set(0)
      }
    }

    result.asInstanceOf[TExit[E, A]]
  }
}
=======
import zio.{ Fiber, IO }
import scala.util.Try
>>>>>>> give stm the 'z' treatment

object STM {

  /**
   * Atomically performs a batch of operations in a single transaction.
   */
  def atomically[E, A](stm: STM[E, A]): IO[E, A] =
    ZSTM.atomically(stm)

  /**
   * Checks the condition, and if it's true, returns unit, otherwise, retries.
   */
  def check(p: Boolean): STM[Nothing, Unit] = ZSTM.check(p)

  /**
   * Collects all the transactional effects in a list, returning a single
   * transactional effect that produces a list of values.
   */
  def collectAll[E, A](i: Iterable[STM[E, A]]): STM[E, List[A]] =
    ZSTM.collectAll(i)

  /**
   * Kills the fiber running the effect.
   */
  def die(t: Throwable): STM[Nothing, Nothing] =
    ZSTM.die(t)

  /**
   * Kills the fiber running the effect with a `RuntimeException` that contains
   * the specified message.
   */
  def dieMessage(m: String): STM[Nothing, Nothing] =
    ZSTM.dieMessage(m)

  /**
   * Returns a value modelled on provided exit status.
   */
  def done[E, A](exit: ZSTM.internal.TExit[E, A]): STM[E, A] =
    ZSTM.done(exit)

  /**
   * Returns a value that models failure in the transaction.
   */
  def fail[E](e: E): STM[E, Nothing] =
    ZSTM.fail(e)

  /**
   * Returns the fiber id of the fiber committing the transaction.
   */
  val fiberId: STM[Nothing, Fiber.Id] =
    ZSTM.fiberId

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns a transactional effect that produces a new `List[B]`.
   */
  def foreach[E, A, B](as: Iterable[A])(f: A => STM[E, B]): STM[E, List[B]] =
    ZSTM.foreach(as)(f)

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns a transactional effect that produces `Unit`.
   *
   * Equivalent to `foreach(as)(f).unit`, but without the cost of building
   * the list of results.
   */
  def foreach_[E, A, B](as: Iterable[A])(f: A => STM[E, B]): STM[E, Unit] =
    STM.succeed(as.iterator).flatMap { it =>
      def loop: STM[E, Unit] =
        if (it.hasNext) f(it.next) *> loop
        else STM.unit

      loop
    }

  /**
   * Creates an STM effect from an `Either` value.
   */
  def fromEither[E, A](e: => Either[E, A]): STM[E, A] =
    ZSTM.fromEither(e)

  /**
   * Creates an STM effect from a `Try` value.
   */
  def fromTry[A](a: => Try[A]): STM[Throwable, A] =
    ZSTM.fromTry(a)

  /**
   * Creates an `STM` value from a partial (but pure) function.
   */
  def partial[A](a: => A): STM[Throwable, A] =
    ZSTM.partial(a)

  /**
   * Abort and retry the whole transaction when any of the underlying
   * transactional variables have changed.
   */
  val retry: STM[Nothing, Nothing] =
    ZSTM.retry

  /**
   * Returns an `STM` effect that succeeds with the specified value.
   */
  def succeed[A](a: A): STM[Nothing, A] =
    ZSTM.succeed(a)

  /**
   * Suspends creation of the specified transaction lazily.
   */
  def suspend[E, A](stm: => STM[E, A]): STM[E, A] =
    ZSTM.suspend(stm)

  /**
   * Returns an `STM` effect that succeeds with `Unit`.
   */
  val unit: STM[Nothing, Unit] =
    ZSTM.unit
}
