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

package scalaz.zio.interop.stream

import cats.effect.{ Effect, Sync }
import cats.implicits._
import scalaz.zio.clock.Clock
import scalaz.zio._
import scalaz.zio.stream.{ Take, ZStream }

/**
 * See [[scalaz.zio.stream.ZStream]]
 */
final class Stream[F[+ _], +A] private[stream] (private[stream] val underlying: ZStream[Any, Throwable, A])
    extends AnyVal {

  import Stream.Fold
  // import Stream.Env
  import Stream.liftF
  import Stream.liftZIO

  /**
   * See [[scalaz.zio.stream.ZStream#++]]
   */
  def ++[A1 >: A](that: => Stream[F, A1]): Stream[F, A1] =
    new Stream(underlying ++ that.underlying)

  /**
   * See [[scalaz.zio.stream.ZStream#collect]]
   */
  def collect[B](pf: PartialFunction[A, B]): Stream[F, B] =
    new Stream(underlying.collect(pf))

  /**
   * See [[scalaz.zio.stream.ZStream#collectWhile]]
   */
  def collectWhile[B](pred: PartialFunction[A, B]): Stream[F, B] =
    new Stream(underlying.collectWhile(pred))

  /**
   * See [[scalaz.zio.stream.ZStream#drain]]
   */
  def drain: Stream[F, Nothing] =
    new Stream(underlying.drain)

  /**
   * See [[scalaz.zio.stream.ZStream#drop]]
   */
  def drop(n: Int): Stream[F, A] =
    new Stream(underlying.drop(n))

  /**
   * See [[scalaz.zio.stream.ZStream#dropWhile]]
   */
  def dropWhile(pred: A => Boolean): Stream[F, A] =
    new Stream(underlying.dropWhile(pred))

  /**
   * See [[scalaz.zio.stream.ZStream#filter]]
   */
  def filter(pred: A => Boolean): Stream[F, A] =
    new Stream(underlying.filter(pred))

  /**
   * See [[scalaz.zio.stream.ZStream#filterM]]
   */
  def filterM(pred: A => F[Boolean])(implicit E: Effect[F]): Stream[F, A] =
    new Stream(underlying.filterM(pred.andThen(liftZIO(_))))

  /**
   * See [[scalaz.zio.stream.ZStream#filterNot]]
   */
  def filterNot(pred: A => Boolean): Stream[F, A] =
    filter(a => !pred(a))

  /**
   * See [[scalaz.zio.stream.ZStream#fold]]
   */
  def fold[A1 >: A, S](implicit E: Effect[F], R: Runtime[Any]): Fold[F, A1, S] =
    // cats: type Fold[F[+ _], +A, S] =   F[            (S, S => Boolean, (S, A) =>         F[S]) =>         F[S]]
    // zio:  type Fold[R, E,   +A, S] = ZIO[R, Nothing, (S, S => Boolean, (S, A) => ZIO[R, E, S]) => ZIO[R, E, S]]
    E.delay(
      (s, f, op) => liftF(underlying.fold[Any, Throwable, A1, S].flatMap(_(s, f, (s, a1) => liftZIO(op(s, a1)))))
    )

  /**
   * See [[scalaz.zio.stream.ZStream#foldLeft]]
   */
  def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => F[S])(implicit E: Effect[F], R: Runtime[Any]): F[S] =
    (fold[A, S]: F[(S, S => Boolean, (S, A1) => F[S]) => F[S]]).flatMap(_(s, _ => true, f))

  /**
   * See [[scalaz.zio.stream.ZStream#flatMap]]
   */
  def flatMap[B](f: A => Stream[F, B]): Stream[F, B] =
    new Stream(underlying.flatMap(f.andThen(_.underlying)))

  /**
   * See [[scalaz.zio.stream.ZStream#foreach]]
   */
  def foreach(f: A => F[Unit])(implicit R: Runtime[Any], E: Effect[F]): F[Unit] =
    liftF(underlying.foreach(f.andThen(liftZIO(_))))

  /**
   * See [[scalaz.zio.stream.ZStream#foreachWhile]]
   */
  def foreachWhile(f: A => F[Boolean])(implicit R: Runtime[Any], E: Effect[F]): F[Unit] =
    liftF(underlying.foreachWhile(f.andThen(liftZIO(_))))

  /**
   * See [[scalaz.zio.stream.ZStream#forever]]
   */
  def forever: Stream[F, A] =
    new Stream(underlying.forever)

  /**
   * See [[scalaz.zio.stream.ZStream#map]]
   */
  def map[B](f: A => B): Stream[F, B] =
    new Stream(underlying.map(f))

  /**
   * See [[scalaz.zio.stream.ZStream#mapConcat]]
   */
  def mapConcat[B](f: A => Chunk[B]): Stream[F, B] =
    new Stream(underlying.mapConcat(f))

  /**
   * Switches to a different effect type `G`
   */
  def mapK[G[+ _]]: Stream[G, A] =
    new Stream(underlying)

  /**
   * See [[scalaz.zio.stream.ZStream#mapM]]
   */
  def mapM[B](f: A => F[B])(implicit E: Effect[F]): Stream[F, B] =
    new Stream(underlying.mapM(f.andThen(liftZIO(_))))

  /**
   * See [[scalaz.zio.stream.ZStream#merge]]
   */
  def merge[A1 >: A](that: Stream[F, A1], capacity: Int = 1): Stream[F, A1] =
    new Stream(underlying.merge(that.underlying, capacity))

  /**
   * See [[scalaz.zio.stream.ZStream#mergeEither]]
   */
  def mergeEither[B](that: Stream[F, B], capacity: Int = 1): Stream[F, Either[A, B]] =
    new Stream(underlying.mergeEither(that.underlying, capacity))

  /**
   * See [[scalaz.zio.stream.ZStream#mergeWith]]
   */
  def mergeWith[B, C](that: Stream[F, B], capacity: Int = 1)(l: A => C, r: B => C): Stream[F, C] =
    new Stream(underlying.mergeWith(that.underlying, capacity)(l, r))

  /**
   * TODO
   *  - Managed
   * See [[scalaz.zio.stream.ZStream#peel]]
   */
  def peel[A1 >: A, B](sink: Sink[F, A1, A1, B]): ZManaged[Any, Throwable, (B, ZStream[Any, Throwable, A1])] =
    underlying.peel(sink.underlying)

  /**
   * TODO
   *  - Schedule #790
   *  - Find out how to deal correctly with "with Clock"
   * See [[scalaz.zio.stream.ZStream#]]
   */
  // cats: type Fold[F[+ _], +A, S] =   F[            (S, S => Boolean, (S, A) =>         F[S]) =>         F[S]]
  // zio:  type Fold[R, E,   +A, S] = ZIO[R, Nothing, (S, S => Boolean, (S, A) => ZIO[R, E, S]) => ZIO[R, E, S]]
  def repeat(schedule: ZSchedule[Any, Unit, _])(implicit R: Runtime[Clock]): Stream[F, A] =
    new Stream(R.unsafeRun(underlying.repeat(schedule).provide(R.Environment)))

  /**
   * TODO
   *  - Schedule #790
   * See [[scalaz.zio.stream.ZStream#repeatElems]]
   */
  def repeatElems[B](schedule: ZSchedule[Any, A, B])(implicit R: Runtime[Clock]): Stream[F, A] =
    new Stream(R.unsafeRun(underlying.repeatElems(schedule).provide(R.Environment)))

  /**
   * See [[scalaz.zio.stream.ZStream#run]]
   */
  def run[A0, A1 >: A, B](sink: Sink[F, A0, A1, B])(implicit R: Runtime[Any], sync: Sync[F]): F[B] =
    sync.delay(R.unsafeRun(underlying.run(sink.underlying)))

  /**
   * See [[scalaz.zio.stream.ZStream#mapAccum]]
   */
  def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): Stream[F, B] =
    new Stream(underlying.mapAccum(s1)(f1))

  /**
   * See [[scalaz.zio.stream.ZStream#mapAccumM]]
   */
  def mapAccumM[S1, B](s1: S1)(f1: (S1, A) => F[(S1, B)])(implicit E: Effect[F]): Stream[F, B] =
    new Stream(underlying.mapAccumM(s1)((s1, a) => liftZIO(f1(s1, a))))

  /**
   * See [[scalaz.zio.stream.ZStream#take]]
   */
  def take(n: Int): Stream[F, A] =
    new Stream(underlying.take(n))

  /**
   * See [[scalaz.zio.stream.ZStream#takeWhile]]
   */
  def takeWhile(pred: A => Boolean): Stream[F, A] =
    new Stream(underlying.takeWhile(pred))

  /**
   * TODO
   *  - ZManaged
   * See [[scalaz.zio.stream.ZStream#toQueue]]
   */
  def toQueue[A1 >: A](capacity: Int = 1): ZManaged[Any, Nothing, Queue[Take[Throwable, A1]]] =
    underlying.toQueue(capacity)

  /**
   * See [[scalaz.zio.stream.ZStream#transduce]]
   */
  def transduce[A1 >: A, C](sink: Sink[F, A1, A1, C]): Stream[F, C] =
    new Stream(underlying.transduce(sink.underlying))

  /**
   * See [[scalaz.zio.stream.ZStream#tap]]
   */
  def tap(f: A => F[_])(implicit E: Effect[F]): Stream[F, A] =
    new Stream(underlying.tap(a => liftZIO(f(a))))

  /**
   * See [[scalaz.zio.stream.ZStream#zip]]
   */
  def zip[B](that: Stream[F, B], lc: Int = 1, rc: Int = 1): Stream[F, (A, B)] =
    new Stream(underlying.zip(that.underlying, lc, rc))

  /**
   * See [[scalaz.zio.stream.ZStream#zipWith]]
   */
  def zipWith[B, C](that: Stream[F, B], lc: Int = 1, rc: Int = 1)(
    f: (Option[A], Option[B]) => Option[C]
  ): Stream[F, C] =
    new Stream(underlying.zipWith(that.underlying, lc, rc)(f))

  /**
   * See [[scalaz.zio.stream.ZStream#zipWithIndex]]
   */
  def zipWithIndex: Stream[F, (A, Int)] =
    new Stream(underlying.zipWithIndex)

}

object Stream {

  type Fold[F[+ _], +A, S] = F[(S, S => Boolean, (S, A) => F[S]) => F[S]]

  final def empty[F[+ _]]: Stream[F, Nothing] =
    new Stream[F, Nothing](ZStream.empty)

  final def never[F[+ _]]: Stream[F, Nothing] =
    new Stream[F, Nothing](ZStream.never)

  final def succeed[F[+ _], A](a: A): Stream[F, A] =
    new Stream[F, A](ZStream.succeed(a))

  final def succeedLazy[F[+ _], A](a: => A): Stream[F, A] =
    new Stream[F, A](ZStream.succeedLazy(a))

  final def fail[F[+ _], E <: Throwable](error: E): Stream[F, Nothing] =
    new Stream[F, Nothing](ZStream.fail(error))

  final def fromEffect[F[+ _], A](fa: F[A])(implicit E: Effect[F]): Stream[F, A] =
    new Stream(ZStream.fromEffect[Any, Throwable, A](liftZIO(fa)))

  final def flatten[F[+ _], A](fa: Stream[F, Stream[F, A]]): Stream[F, A] =
    new Stream(ZStream.flatten(fa.map(_.underlying).underlying))

  final def unwrap[F[+ _], A](stream: F[Stream[F, A]])(implicit E: Effect[F]): Stream[F, A] =
    new Stream(ZStream.unwrap(liftZIO(stream.map(_.underlying))))

  final def bracket[F[+ _], A, B](
    acquire: F[A]
  )(release: A => F[Unit])(read: A => F[Option[B]])(implicit E: Effect[F]): Stream[F, B] =
    new Stream(
      ZStream.bracket(liftZIO(acquire))(release.andThen(fa => UIO.apply(E.toIO(fa).unsafeRunSync())))(
        read.andThen(liftZIO(_))
      )
    )

  final def managed[F[+ _], R, E, A, B](
    m: ZManaged[Any, Throwable, A]
  )(read: A => ZIO[Any, Throwable, Option[B]]): Stream[F, B] =
    new Stream(ZStream.managed[Any, Throwable, A, B](m)(read))

  // TODO Adding both of those leads to "have same type after erasure"
  //  Should we add one of them, or both? Is it possible?

  //final def fromQueue[F[+ _], A](queue: Queue[A]): Stream[F, A] =
  //  new Stream(ZStream.fromQueue(queue))

  //final def fromQueue[F[+ _], B](queue: ZQueue[_, _, Any, Throwable, _, B]): Stream[F, B] =
  //  new Stream(ZStream.fromQueue(queue))

  final def unfold[F[+ _], S, A](s: S)(f0: S => Option[(A, S)]): Stream[F, A] =
    new Stream[F, A](ZStream.unfold(s)(f0))

  final def unfoldM[F[+ _], S, A](s: S)(f0: S => ZIO[Any, Throwable, Option[(A, S)]]): Stream[F, A] =
    new Stream[F, A](ZStream.unfoldM[Any, S, Throwable, A](s)(f0))

  final def range[F[+ _]](min: Int, max: Int): Stream[F, Int] =
    new Stream[F, Int](ZStream.range(min, max))

  /*
  private[stream] implicit def liftZIO[F[+ _]](implicit E: Effect[F]): F ~> ZIO[Any, Throwable, ?] =
    new cats.arrow.FunctionK[F, ZIO[Any, Throwable, ?]] {
      override def apply[X](fx: F[X]): ZIO[Any, Throwable, X] =
        ZIO(E.toIO(fx).unsafeRunSync())
    }

  private[stream] implicit def liftIO[F[+ _]](implicit R: Runtime[Any], E: Effect[F]): ZIO[Any, Throwable, ?] ~> F =
    new cats.arrow.FunctionK[ZIO[Any, Throwable, ?], F] {
      override def apply[X](fx: ZIO[Any, Throwable, X]): F[X] =
        E.delay(R.unsafeRun(fx))
    }
   */

  // FIXME - duplicate code in #790 (shamelessly copied it)

  import scalaz.zio.interop.catz.taskEffectInstances

  type Env = Clock

  private[stream] def liftF[F[+ _], R, A](zio: TaskR[R, A])(implicit R: Runtime[R], E: Effect[F]): F[A] =
    E.liftIO(taskEffectInstances.toIO(zio))

  private[stream] def liftZIO[F[+ _], R, A](eff: F[A])(implicit E: Effect[F]): TaskR[R, A] =
    ZIO(E.toIO(eff).unsafeRunSync())

}
