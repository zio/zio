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

/**
 * See [[scalaz.zio.stm.STM]]
 */
final class STM[F[+ _], +A] private[stm] (private[stm] val underlying: ZSTM[Throwable, A])(
  implicit liftIO: ZIO[Any, Throwable, ?] ~> F
) {
  self =>

  /**
   * See `<*>` [[scalaz.zio.stm.STM]] `<*>`
   */
  def <*>[B](that: => STM[F, B]): STM[F, (A, B)] =
    self zip that

  /**
   * See [[scalaz.zio.stm.STM]] `<*`
   */
  def <*[B](that: => STM[F, B]): STM[F, A] =
    self zipLeft that

  /**
   * See [[scalaz.zio.stm.STM]] `*>`
   */
  def *>[B](that: => STM[F, B]): STM[F, B] =
    self zipRight that

  /**
   * See [[scalaz.zio.stm.STM]] `>>=`
   */
  def >>=[B](f: A => STM[F, B]): STM[F, B] =
    self flatMap f

  /**
   * See [[scalaz.zio.stm.STM#collect]]
   */
  def collect[B](pf: PartialFunction[A, B]): STM[F, B] = new STM(underlying.collect(pf))

  /**
   * See [[scalaz.zio.stm.STM#commit]]
   */
  def commit: F[A] = liftIO(underlying.commit)

  /**
   * See [[scalaz.zio.stm.STM#const]]
   */
  def const[B](b: => B): STM[F, B] = self map (_ => b)

  /**
   * See [[scalaz.zio.stm.STM#either]]
   */
  def either: STM[F, Either[Throwable, A]] = new STM(underlying.either)

  /**
   * See [[scalaz.zio.stm.STM#filter]]
   */
  def filter(f: A => Boolean): STM[F, A] =
    collect {
      case a if f(a) => a
    }

  /**
   * See [[scalaz.zio.stm.STM#flatMap]]
   */
  def flatMap[B](f: A => STM[F, B]): STM[F, B] = new STM(underlying.flatMap(f.andThen(_.underlying)))

  /**
   * See [[scalaz.zio.stm.STM#flatten]]
   */
  def flatten[B](implicit ev: A <:< STM[F, B]): STM[F, B] =
    self flatMap ev

  /**
   * See [[scalaz.zio.stm.STM#fold]]
   */
  def fold[B](f: Throwable => B, g: A => B): STM[F, B] = new STM(underlying.fold(f, g))

  /**
   * See [[scalaz.zio.stm.STM#foldM]]
   */
  def foldM[B](f: Throwable => STM[F, B], g: A => STM[F, B]): STM[F, B] =
    new STM(underlying.foldM(f.andThen(_.underlying), g.andThen(_.underlying)))

  /**
   * See [[scalaz.zio.stm.STM#map]]
   */
  def map[B](f: A => B): STM[F, B] = new STM(underlying.map(f))

  /**
   * See [[scalaz.zio.stm.STM#mapError]]
   */
  def mapError[E1 <: Throwable](f: Throwable => E1): STM[F, A] = new STM(underlying.mapError(f))

  /**
   * See [[scalaz.zio.stm.STM#option]]
   */
  def option: STM[F, Option[A]] =
    fold[Option[A]](_ => None, Some(_))

  /**
   * See [[scalaz.zio.stm.STM#orElse]]
   */
  def orElse[A1 >: A](that: => STM[F, A1]): STM[F, A1] = new STM(underlying.orElse(that.underlying))

  /**
   * See [[scalaz.zio.stm.STM#orElseEither]]
   */
  def orElseEither[B](that: => STM[F, B]): STM[F, Either[A, B]] =
    (self map (Left[A, B](_))) orElse (that map (Right[A, B](_)))

  /**
   * See scalaz.zio.stm.STM.unit
   */
  def unit: STM[F, Unit] = const(())

  /**
   * See scalaz.zio.stm.STM.unit
   */
  def void: STM[F, Unit] = unit

  /**
   * Same as [[filter]]
   */
  def withFilter(f: A => Boolean): STM[F, A] = filter(f)

  /**
   * See [[scalaz.zio.stm.STM#zip]]
   */
  def zip[B](that: => STM[F, B]): STM[F, (A, B)] =
    (self zipWith that)((a, b) => a -> b)

  /**
   * See [[scalaz.zio.stm.STM#zipLeft]]
   */
  def zipLeft[B](that: => STM[F, B]): STM[F, A] =
    (self zip that) map (_._1)

  /**
   * See [[scalaz.zio.stm.STM#zipRight]]
   */
  def zipRight[B](that: => STM[F, B]): STM[F, B] =
    (self zip that) map (_._2)

  /**
   * See [[scalaz.zio.stm.STM#zipWith]]
   */
  def zipWith[B, C](that: => STM[F, B])(f: (A, B) => C): STM[F, C] =
    self flatMap (a => that map (b => f(a, b)))

  /**
   * Switch from effect F to effect G using transformation `f`.
   */
  def mapK[G[+ _]](f: F ~> G): STM[G, A] = new STM(underlying)(f compose liftIO)
}

object STM {

  def succeed[F[+ _], A](a: A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] = new STM(ZSTM.succeed(a))

  def atomically[F[+ _], A](stm: STM[F, A])(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): F[A] =
    liftIO(ZSTM.atomically(stm.underlying))

  final def check[F[+ _]](p: Boolean)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Unit] =
    if (p) STM.unit else retry

  final def collectAll[F[+ _], A](
    i: Iterable[STM[F, A]]
  )(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, List[A]] =
    i.foldRight[STM[F, List[A]]](STM.succeed(Nil)) {
      case (stm, acc) =>
        acc.zipWith(stm)((xs, x) => x :: xs)
    }

  final def die[F[+ _]](t: Throwable)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Nothing] =
    succeedLazy(throw t)

  final def dieMessage[F[+ _]](m: String)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Nothing] =
    die(new RuntimeException(m))

  final def fail[F[+ _]](e: Throwable)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Nothing] =
    new STM(ZSTM.fail(e))

  final def foreach[F[+ _], A, B](
    as: Iterable[A]
  )(f: A => STM[F, B])(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, List[B]] =
    collectAll(as.map(f))

  final def fromEither[F[+ _], A](e: Either[Throwable, A])(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] =
    new STM(ZSTM.fromEither(e))

  final def fromTry[F[+ _], A](a: => Try[A])(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] =
    new STM(ZSTM.fromTry(a))

  final def partial[F[+ _], A](a: => A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] =
    fromTry(Try(a))

  final def retry[F[+ _]](implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Nothing] = new STM(ZSTM.retry)

  final def succeedLazy[F[+ _], A](a: => A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, A] =
    new STM(ZSTM.succeedLazy(a))

  final def unit[F[+ _]](implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, Unit] = succeed(())
}
