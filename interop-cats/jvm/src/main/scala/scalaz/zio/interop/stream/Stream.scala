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

import cats.~>
import cats.effect.Effect
import cats.implicits._
import scalaz.zio.{Chunk, Queue, ZIO, ZManaged, ZSchedule}
import scalaz.zio.stream.{Take, ZSink, ZStream}

final class Stream[F[+ _], +A] private[stream] (private[stream] val underlying: ZStream[Any, Throwable, A])(
  implicit liftIO: ZIO[Any, Throwable, ?] ~> F,
  liftZIO: F ~> ZIO[Any, Throwable, ?],
  effect: Effect[F]
) { self =>

  import Stream.Fold

  def fold[A1 >: A, S]: Fold[F, A1, S] = ???

  def foldLeft[A1 >: A, S](s: S)(f: (S, A1) => F[S]): F[S] = {
    (fold[A, S]: F[(S, S => Boolean, (S, A) => F[S]) => F[S]]).flatMap(_(s, _ => false, f))
  }

  def ++[A1 >: A](that: => Stream[F, A1]): Stream[F, A1] =
    new Stream(underlying ++ that.underlying)

  def drain: Stream[F, Nothing] =
    new Stream(underlying.drain)

  def filter(pred: A => Boolean): Stream[F, A] =
    new Stream(underlying.filter(pred))

  def filterM(pred: A => F[Boolean]): Stream[F, A] =
    new Stream(underlying.filterM(pred.andThen(liftZIO.apply)))

  def filterNot(pred: A => Boolean): Stream[F, A] =
    filter(a => !pred(a))

  def foreachWhile(f: A => F[Boolean]): F[Unit] =
    liftIO.apply(underlying.foreachWhile(f.andThen(liftZIO.apply)))

  def collect[B](pf: PartialFunction[A, B]): Stream[F, B] =
    new Stream(underlying.collect(pf))

  def drop(n: Int): Stream[F, A] =
    new Stream(underlying.drop(n))

  def dropWhile(pred: A => Boolean): Stream[F, A] =
    new Stream(underlying.dropWhile(pred))

  def flatMap[B](f: A => Stream[F, B]): Stream[F, B] =
    new Stream(underlying.flatMap(f.andThen(_.underlying)))

  def foreach(f: A => F[Unit]): F[Unit] =
    liftIO(underlying.foreach(f.andThen(liftZIO.apply)))

  def forever: Stream[F, A] =
    new Stream(underlying.forever)

  def map[B](f: A => B): Stream[F, B] =
    new Stream(underlying.map(f))

  // TODO Should we expose Chunk to clients?
  def mapConcat[B](f: A => Chunk[B]): Stream[F, B] =
    new Stream(underlying.mapConcat(f))

  def mapM[B](f: A => F[B]): Stream[F, B] =
    new Stream(underlying.mapM(f.andThen(liftZIO.apply)))

  def merge[A1 >: A](that: Stream[F, A1], capacity: Int = 1): Stream[F, A1] =
    new Stream(underlying.merge(that.underlying, capacity))

  def mergeEither[B](that: Stream[F, B], capacity: Int = 1): Stream[F, Either[A, B]] =
    new Stream(underlying.mergeEither(that.underlying, capacity))

  def mergeWith[B, C](that: Stream[F, B], capacity: Int = 1)(l: A => C, r: B => C): Stream[F, C] =
    new Stream(underlying.mergeWith(that.underlying, capacity)(l, r))

  // TODO Sink, Managed
  def peel[A1 >: A, B](sink: ZSink[Any, Throwable, A1, A1, B]): ZManaged[Any, Throwable, (B, Stream[F, A1])] = ???

  // TODO Schedule #790
  //  Find out how to deal correctly with "with Clock"
  def repeat(schedule: ZSchedule[Any, Unit, _]): Stream[F, A] = ???
    // new Stream(underlying.repeat(schedule))

  // TODO Schedule #790
  //  Find out how to deal correctly with "with Clock"
  def repeatElems[B](schedule: ZSchedule[Any, A, B]): Stream[F, A] = ???
    // new Stream(underlying.repeatElems(schedule))

  // TODO Sink
  def run[A0, A1 >: A, B](sink: ZSink[Any, Throwable, A0, A1, B]): F[B] =
    liftIO(underlying.run(sink))

  def mapAccum[S1, B](s1: S1)(f1: (S1, A) => (S1, B)): Stream[F, B] =
    new Stream(underlying.mapAccum(s1)(f1))

  def mapAccumM[S1, B](s1: S1)(f1: (S1, A) => F[(S1, B)]): Stream[F, B] =
    new Stream(underlying.mapAccumM(s1) { (s1, a) => liftZIO(f1(s1, a)) })

  def take(n: Int): Stream[F, A] =
    new Stream(underlying.take(n))

  def takeWhile(pred: A => Boolean): Stream[F, A] =
    new Stream(underlying.takeWhile(pred))

  def collectWhile[B](pred: PartialFunction[A, B]): Stream[F, B] =
    new Stream(underlying.collectWhile(pred))

  // TODO ZManaged -> Managed
  def toQueue[A1 >: A](capacity: Int = 1): ZManaged[Any, Nothing, Queue[Take[Throwable, A1]]] = ???

  // TODO ZSink -> Sink
  def transduce[A1 >: A, C](sink: ZSink[Any, Throwable, A1, A1, C]): Stream[F, C] =
    new Stream(underlying.transduce(sink))

  def tap(f: A => F[_]): Stream[F, A] =
    new Stream(underlying.tap { a =>
      liftZIO(f(a))
    })

  def zip[B](that: Stream[F, B], lc: Int = 1, rc: Int = 1): Stream[F, (A, B)] =
    new Stream(underlying.zip(that.underlying, lc, rc))

  def zipWith[B, C](that: Stream[F, B], lc: Int = 1, rc: Int = 1)(f: (Option[A], Option[B]) => Option[C]): Stream[F, C] =
    new Stream(underlying.zipWith(that.underlying, lc, rc)(f))

  def zipWithIndex: Stream[F, (A, Int)] =
    new Stream(underlying.zipWithIndex)

  // TODO
  //   def mapK[G[+ _]](f: F ~> G): Stream[G, A] =
  //     new Stream(underlying)(f compose liftIO, effect)

}

object Stream {

  type Fold[F[+ _], +A, S] = F[(S, S => Boolean, (S, A) => F[S]) => F[S]]

}
