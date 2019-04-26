/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio.interop.stm

import cats.~>
import scalaz.zio.ZIO
import scalaz.zio.stm.{ STM => ZSTM }

import scala.util.Try

final class STM[F[+ _], +A] private[stm] (private[stm] val underlying: ZSTM[Throwable, A])(
  implicit liftIO: ZIO[Any, Throwable, ?] ~> F
) {
  self =>

  /**
   * Sequentially zips this value with the specified one.
   */
  def <*>[B](that: => STM[F, B]): STM[F, (A, B)] =
    self zip that

  /**
   * Sequentially zips this value with the specified one, discarding the
   * second element of the tuple.
   */
  def <*[B](that: => STM[F, B]): STM[F, A] =
    self zipLeft that

  /**
   * Sequentially zips this value with the specified one, discarding the
   * first element of the tuple.
   */
  def *>[B](that: => STM[F, B]): STM[F, B] =
    self zipRight that

  /**
   * Feeds the value produced by this effect to the specified function,
   * and then runs the returned effect as well to produce its results.
   */
  def >>=[B](f: A => STM[F, B]): STM[F, B] =
    self flatMap f

  /**
   * Simultaneously filters and maps the value produced by this effect.
   */
  def collect[B](pf: PartialFunction[A, B]): STM[F, B] = new STM(underlying.collect(pf))

  /**
   * Commits this transaction atomically.
   */
  def commit: F[A] = liftIO(underlying.commit)

  /**
   * Maps the success value of this effect to the specified constant value.
   */
  def const[B](b: => B): STM[F, B] = self map (_ => b)

  /**
   * Converts the failure channel into an `Either`.
   */
  def either: STM[F, Either[Throwable, A]] = new STM(underlying.either)

  /**
   * Filters the value produced by this effect, retrying the transaction until
   * the predicate returns true for the value.
   */
  def filter(f: A => Boolean): STM[F, A] =
    collect {
      case a if f(a) => a
    }

  /**
   * Feeds the value produced by this effect to the specified function,
   * and then runs the returned effect as well to produce its results.
   */
  def flatMap[B](f: A => STM[F, B]): STM[F, B] = new STM(underlying.flatMap(f.andThen(_.underlying)))

  /**
   * Flattens out a nested `STM` effect.
   */
  def flatten[B](implicit ev: A <:< STM[F, B]): STM[F, B] =
    self flatMap ev

  /**
   * Folds over the `STM` effect, handling both failure and success, but not
   * retry.
   */
  def fold[B](f: Throwable => B, g: A => B): STM[F, B] = new STM(underlying.fold(f, g))

  /**
   * Effectfully folds over the `STM` effect, handling both failure and
   * success.
   */
  def foldM[B](f: Throwable => STM[F, B], g: A => STM[F, B]): STM[F, B] =
    new STM(underlying.foldM(f.andThen(_.underlying), g.andThen(_.underlying)))

  /**
   * Maps the value produced by the effect.
   */
  def map[B](f: A => B): STM[F, B] = new STM(underlying.map(f))

  /**
   * Maps from one error type to another.
   */
  def mapError[E1 <: Throwable](f: Throwable => E1): STM[F, A] = new STM(underlying.mapError(f))

  /**
   * Converts the failure channel into an `Option`.
   */
  def option: STM[F, Option[A]] =
    fold[Option[A]](_ => None, Some(_))

  /**
   * Tries this effect first, and if it fails, tries the other effect.
   */
  def orElse[A1 >: A](that: => STM[F, A1]): STM[F, A1] = new STM(underlying.orElse(that.underlying))

  /**
   * Returns a transactional effect that will produce the value of this effect in left side, unless it
   * fails, in which case, it will produce the value of the specified effect in right side.
   */
  def orElseEither[B](that: => STM[F, B]): STM[F, Either[A, B]] =
    (self map (Left[A, B](_))) orElse (that map (Right[A, B](_)))

  /**
   * Maps the success value of this effect to unit.
   */
  def unit: STM[F, Unit] = const(())

  /**
   * Maps the success value of this effect to unit.
   */
  def void: STM[F, Unit] = unit

  /**
   * Same as [[filter]]
   */
  def withFilter(f: A => Boolean): STM[F, A] = filter(f)

  /**
   * Named alias for `<*>`.
   */
  def zip[B](that: => STM[F, B]): STM[F, (A, B)] =
    (self zipWith that)((a, b) => a -> b)

  /**
   * Named alias for `<*`.
   */
  def zipLeft[B](that: => STM[F, B]): STM[F, A] =
    (self zip that) map (_._1)

  /**
   * Named alias for `*>`.
   */
  def zipRight[B](that: => STM[F, B]): STM[F, B] =
    (self zip that) map (_._2)

  /**
   * Sequentially zips this value with the specified one, combining the values
   * using the specified combiner function.
   */
  def zipWith[B, C](that: => STM[F, B])(f: (A, B) => C): STM[F, C] =
    self flatMap (a => that map (b => f(a, b)))

  def mapK[G[+ _]](f: F ~> G): STM[G, A] = new STM(underlying)(f compose liftIO)
}

object STM {

  /**
   * Returns an `STM` effect that succeeds with the specified value.
   */
  def succeed[F[+ _], A](a: A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] = new STM(ZSTM.succeed(a))

  /**
   * Atomically performs a batch of operations in a single transaction.
   */
  def atomically[F[+ _], A](stm: STM[F, A])(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): F[A] =
    liftIO(ZSTM.atomically(stm.underlying))

  /**
   * Checks the condition, and if it's true, returns unit, otherwise, retries.
   */
  final def check[F[+ _]](p: Boolean)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Unit] =
    if (p) STM.unit else retry

  /**
   * Collects all the transactional effects in a list, returning a single
   * transactional effect that produces a list of values.
   */
  final def collectAll[F[+ _], A](
    i: Iterable[STM[F, A]]
  )(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, List[A]] =
    i.foldRight[STM[F, List[A]]](STM.succeed(Nil)) {
      case (stm, acc) =>
        acc.zipWith(stm)((xs, x) => x :: xs)
    }

  /**
   * Kills the fiber running the effect.
   */
  final def die[F[+ _]](t: Throwable)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Nothing] =
    succeedLazy(throw t)

  /**
   * Kills the fiber running the effect with a `RuntimeException` that contains
   * the specified message.
   */
  final def dieMessage[F[+ _]](m: String)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Nothing] =
    die(new RuntimeException(m))

  /**
   * Returns a value that models failure in the transaction.
   */
  final def fail[F[+ _]](e: Throwable)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Nothing] =
    new STM(ZSTM.fail(e))

  /**
   * Applies the function `f` to each element of the `Iterable[A]` and
   * returns a transactional effect that produces a new `List[B]`.
   */
  final def foreach[F[+ _], A, B](
    as: Iterable[A]
  )(f: A => STM[F, B])(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, List[B]] =
    collectAll(as.map(f))

  /**
   * Creates an STM effect from an `Either` value.
   */
  final def fromEither[F[+ _], A](e: Either[Throwable, A])(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] =
    new STM(ZSTM.fromEither(e))

  /**
   * Creates an STM effect from a `Try` value.
   */
  final def fromTry[F[+ _], A](a: => Try[A])(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] =
    new STM(ZSTM.fromTry(a))

  /**
   * Creates an `STM` value from a partial (but pure) function.
   */
  final def partial[F[+ _], A](a: => A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] =
    fromTry(Try(a))

  /**
   * Abort and retry the whole transaction when any of the underlying
   * transactional variables have changed.
   */
  final def retry[F[+ _]](implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Nothing] = new STM(ZSTM.retry)

  /**
   * Returns an `STM` effect that succeeds with the specified (lazily
   * evaluated) value.
   */
  final def succeedLazy[F[+ _], A](a: => A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] =
    new STM(ZSTM.succeedLazy(a))

  /**
   * Returns an `STM` effect that succeeds with `Unit`.
   */
  final def unit[F[+ _]](implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Unit] = succeed(())
}
