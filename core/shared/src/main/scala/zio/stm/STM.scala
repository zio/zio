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

import scala.util.Try

import zio.{ Fiber, IO }

object STM {

  /**
   * @see See [[zio.stm.ZSTM.absolve]]
   */
  def absolve[R, E, A](e: STM[E, Either[E, A]]): STM[E, A] =
    ZSTM.absolve(e)

  /**
   * @see See [[zio.stm.ZSTM.atomically]]
   */
  def atomically[E, A](stm: STM[E, A]): IO[E, A] =
    ZSTM.atomically(stm)

  /**
   * @see See [[zio.stm.ZSTM.check]]
   */
  def check(p: => Boolean): STM[Nothing, Unit] = ZSTM.check(p)

  /**
   * @see See [[zio.stm.ZSTM.collectAll]]
   */
  def collectAll[E, A](i: Iterable[STM[E, A]]): STM[E, List[A]] =
    ZSTM.collectAll(i)

  /**
   * @see See [[zio.stm.ZSTM.die]]
   */
  def die(t: => Throwable): STM[Nothing, Nothing] =
    ZSTM.die(t)

  /**
   * @see See [[zio.stm.ZSTM.dieMessage]]
   */
  def dieMessage(m: => String): STM[Nothing, Nothing] =
    ZSTM.dieMessage(m)

  /**
   * @see See [[zio.stm.ZSTM.done]]
   */
  def done[E, A](exit: => ZSTM.internal.TExit[E, A]): STM[E, A] =
    ZSTM.done(exit)

  /**
   * @see See [[zio.stm.ZSTM.fail]]
   */
  def fail[E](e: => E): STM[E, Nothing] =
    ZSTM.fail(e)

  /**
   * @see See [[zio.stm.ZSTM.fiberId]]
   */
  val fiberId: STM[Nothing, Fiber.Id] =
    ZSTM.fiberId

  /**
   * @see [[zio.stm.ZSTM.filter]]
   */
  def filter[E, A](as: Iterable[A])(f: A => STM[E, Boolean]): STM[E, List[A]] =
    ZSTM.filter(as)(f)

  /**
   * @see See [[zio.stm.ZSTM.flatten]]
   */
  def flatten[E, A](task: STM[E, STM[E, A]]): STM[E, A] =
    ZSTM.flatten(task)

  /**
   * @see See [[zio.stm.ZSTM.foldLeft]]
   */
  def foldLeft[E, S, A](in: Iterable[A])(zero: S)(f: (S, A) => STM[E, S]): STM[E, S] =
    ZSTM.foldLeft(in)(zero)(f)

  /**
   * @see See [[zio.stm.ZSTM.foldRight]]
   */
  def foldRight[E, S, A](in: Iterable[A])(zero: S)(f: (A, S) => STM[E, S]): STM[E, S] =
    ZSTM.foldRight(in)(zero)(f)

  /**
   * @see See [[zio.stm.ZSTM.foreach]]
   */
  def foreach[E, A, B](as: Iterable[A])(f: A => STM[E, B]): STM[E, List[B]] =
    ZSTM.foreach(as)(f)

  /**
   * @see See [[zio.stm.ZSTM.foreach_]]
   */
  def foreach_[E, A, B](as: Iterable[A])(f: A => STM[E, B]): STM[E, Unit] =
    ZSTM.foreach_(as)(f)

  /**
   * @see See [[zio.stm.ZSTM.fromEither]]
   */
  def fromEither[E, A](e: => Either[E, A]): STM[E, A] =
    ZSTM.fromEither(e)

  /**
   * @see See [[zio.stm.ZSTM.fromFunction]]
   */
  def fromFunction[A](f: Any => A): STM[Nothing, A] =
    ZSTM.fromFunction(f)

  /**
   * @see See [[zio.stm.ZSTM.fromFunctionM]]
   */
  def fromFunctionM[R, E, A](f: Any => STM[E, A]): STM[E, A] =
    ZSTM.fromFunctionM(f)

  /**
   * @see See [[zio.stm.ZSTM.fromOption]]
   */
  def fromOption[A](v: => Option[A]): STM[Unit, A] =
    ZSTM.fromOption(v)

  /**
   * @see See [[zio.stm.ZSTM.fromTry]]
   */
  def fromTry[A](a: => Try[A]): STM[Throwable, A] =
    ZSTM.fromTry(a)

  /**
   * @see See [[zio.stm.ZSTM.identity]]
   */
  def identity: STM[Nothing, Any] = ZSTM.identity

  /**
   * @see See [[zio.stm.ZSTM.ifM]]
   */
  def ifM[E](b: STM[E, Boolean]): ZSTM.IfM[Any, E] =
    new ZSTM.IfM(b)

  /**
   * @see See [[zio.stm.ZSTM.iterate]]
   */
  def iterate[E, S](initial: S)(cont: S => Boolean)(body: S => STM[E, S]): STM[E, S] =
    ZSTM.iterate(initial)(cont)(body)

  /**
   * @see See [[zio.stm.ZSTM.left]]
   */
  def left[A](a: => A): STM[Nothing, Either[A, Nothing]] =
    ZSTM.left(a)

  /**
   * @see See [[zio.stm.ZSTM.loop]]
   */
  def loop[E, A, S](initial: S)(cont: S => Boolean, inc: S => S)(body: S => STM[E, A]): STM[E, List[A]] =
    ZSTM.loop(initial)(cont, inc)(body)

  /**
   * @see See [[zio.stm.ZSTM.loop_]]
   */
  def loop_[E, S](initial: S)(cont: S => Boolean, inc: S => S)(body: S => STM[E, Any]): STM[E, Unit] =
    ZSTM.loop_(initial)(cont, inc)(body)

  /**
   * @see See [[zio.stm.ZSTM.none]]
   */
  val none: STM[Nothing, Option[Nothing]] = ZSTM.none

  /**
   * @see See [[zio.stm.ZSTM.mergeAll]]
   */
  def mergeAll[E, A, B](
    in: Iterable[STM[E, A]]
  )(zero: B)(f: (B, A) => B): STM[E, B] = ZSTM.mergeAll(in)(zero)(f)

  /**
   * @see See [[zio.stm.ZSTM.partial]]
   */
  def partial[A](a: => A): STM[Throwable, A] =
    ZSTM.partial(a)

  /**
   * @see See [[zio.stm.ZSTM.partition]]
   */
  def partition[E, A, B](in: Iterable[A])(f: A => STM[E, B]): STM[Nothing, (List[E], List[B])] =
    ZSTM.partition(in)(f)

  /**
   * @see See [[zio.stm.ZSTM.reduceAll]]
   */
  def reduceAll[E, A](a: STM[E, A], as: Iterable[STM[E, A]])(
    f: (A, A) => A
  ): STM[E, A] = ZSTM.reduceAll(a, as)(f)

  /**
   * @see See [[zio.stm.ZSTM.retry]]
   */
  val retry: STM[Nothing, Nothing] =
    ZSTM.retry

  /**
   * @see See [[zio.stm.ZSTM.right]]
   */
  def right[A](a: => A): STM[Nothing, Either[Nothing, A]] =
    ZSTM.right(a)

  /**
   * @see See [[zio.stm.ZSTM.some]]
   */
  def some[A](a: => A): STM[Nothing, Option[A]] =
    ZSTM.some(a)

  /**
   * @see See [[zio.stm.ZSTM.succeed]]
   */
  def succeed[A](a: => A): STM[Nothing, A] =
    ZSTM.succeed(a)

  /**
   * @see See [[zio.stm.ZSTM.suspend]]
   */
  def suspend[E, A](stm: => STM[E, A]): STM[E, A] =
    ZSTM.suspend(stm)

  /**
   * @see See [[zio.stm.ZSTM.unit]]
   */
  val unit: STM[Nothing, Unit] =
    ZSTM.unit

  /**
   * @see See [[zio.stm.ZSTM.when]]
   */
  def when[E](b: => Boolean)(stm: STM[E, Any]): STM[E, Unit] = ZSTM.when(b)(stm)

  /**
   * @see See [[zio.stm.ZSTM.whenM]]
   */
  def whenM[E](b: STM[E, Boolean])(stm: STM[E, Any]): STM[E, Unit] = ZSTM.whenM(b)(stm)

  private[zio] def dieNow(t: Throwable): STM[Nothing, Nothing] =
    ZSTM.dieNow(t)

  private[zio] def doneNow[E, A](exit: => ZSTM.internal.TExit[E, A]): STM[E, A] =
    ZSTM.doneNow(exit)

  private[zio] def failNow[E](e: E): STM[E, Nothing] =
    ZSTM.failNow(e)

  private[zio] def succeedNow[A](a: A): STM[Nothing, A] =
    ZSTM.succeedNow(a)
}
