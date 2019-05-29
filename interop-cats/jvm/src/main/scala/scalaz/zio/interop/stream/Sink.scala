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

import cats.effect.Effect
import scalaz.zio.{ Chunk, Runtime }
import scalaz.zio.stream.ZSink
import scalaz.zio.stream.ZSink.Step

final class Sink[F[+ _], +A0, -A, +B] private[stream] (private[stream] val underlying: ZSink[Any, Throwable, A0, A, B])
    extends AnyVal {

  type State = underlying.State

  import ZSink.Step
  import Stream.{ liftF, liftZIO }

  /**
   * See [[scalaz.zio.stream.ZSink#initial]]
   */
  def initial(implicit R: Runtime[Any], E: Effect[F]): F[Step[State, Nothing]] =
    liftF[F, Any, Step[State, Nothing]](underlying.initial)

  /**
   * See [[scalaz.zio.stream.ZSink#step]]
   */
  def step(state: State, a: A)(implicit R: Runtime[Any], E: Effect[F]): F[Step[State, A0]] =
    liftF(underlying.step(state, a))

  /**
   * See [[scalaz.zio.stream.ZSink#extract]]
   */
  def extract(state: State)(implicit R: Runtime[Any], E: Effect[F]): F[B] =
    liftF(underlying.extract(state))

  /**
   * See [[scalaz.zio.stream.ZSink#stepChunk]]
   */
  def stepChunk[A1 <: A](state: State, as: Chunk[A1])(implicit R: Runtime[Any], E: Effect[F]): F[Step[State, A0]] =
    liftF(underlying.stepChunk(state, as))

  /**
   * See [[scalaz.zio.stream.ZSink#update]]
   */
  def update(state: Step[State, Nothing]): Sink[F, A0, A, B] =
    new Sink(underlying.update(state))

  /**
   * See [[scalaz.zio.stream.ZSink#chunked]]
   */
  def chunked[A1 >: A0, A2 <: A]: Sink[F, A1, Chunk[A2], B] =
    new Sink(underlying.chunked)

  /**
   * See [[scalaz.zio.stream.ZSink#mapM]]
   */
  def mapM[C](f: B => F[C])(implicit E: Effect[F]): Sink[F, A0, A, C] =
    new Sink(underlying.mapM(f.andThen(liftZIO(_))))

  /**
   * See [[scalaz.zio.stream.ZSink#map]]
   */
  def map[C](f: B => C): Sink[F, A0, A, C] =
    new Sink(underlying.map(f))

  /**
   * See [[scalaz.zio.stream.ZSink#filter]]
   */
  def filter[A1 <: A](f: A1 => Boolean): Sink[F, A0, A1, B] =
    new Sink(underlying.filter(f))

  /**
   * See [[scalaz.zio.stream.ZSink#filterM]]
   */
  def filterM[A1 <: A](f: A1 => F[Boolean])(implicit E: Effect[F]): Sink[F, A0, A1, B] =
    new Sink(underlying.filterM(f.andThen(liftZIO(_))))

  /**
   * See [[scalaz.zio.stream.ZSink#filterNot]]
   */
  def filterNot[A1 <: A](f: A1 => Boolean): Sink[F, A0, A1, B] =
    new Sink(underlying.filterNot(f))

  /**
   * See [[scalaz.zio.stream.ZSink#filterNotM]]
   */
  def filterNotM[A1 <: A](f: A1 => F[Boolean])(implicit E: Effect[F]): Sink[F, A0, A1, B] =
    new Sink(underlying.filterNotM(f.andThen(liftZIO(_))))

  /**
   * See [[scalaz.zio.stream.ZSink#contramap]]
   */
  def contramap[C](f: C => A): Sink[F, A0, C, B] =
    new Sink(underlying.contramap(f))

  /**
   * See [[scalaz.zio.stream.ZSink#contramapM]]
   */
  def contramapM[C](f: C => F[A])(implicit E: Effect[F]): Sink[F, A0, C, B] =
    new Sink(underlying.contramapM(f.andThen(liftZIO(_))))

  /**
   * See [[scalaz.zio.stream.ZSink#dimap]]
   */
  def dimap[C, D](f: C => A)(g: B => D): Sink[F, A0, C, D] =
    new Sink(underlying.dimap(f)(g))

  /**
   * See [[scalaz.zio.stream.ZSink#mapError]]
   */
  def mapError[E1 <: Throwable](f: Throwable => E1): Sink[F, A0, A, B] =
    new Sink(underlying.mapError(f))

  /**
   * See [[scalaz.zio.stream.ZSink#mapRemainder]]
   */
  def mapRemainder[A1](f: A0 => A1): Sink[F, A1, A, B] =
    new Sink(underlying.mapRemainder(f))

  /**
   * See [[scalaz.zio.stream.ZSink#provideSome]]
   */
  // TODO Not sure this is useful for cats interop, probably should be deleted
  def provideSome(f: Any => Any): Sink[F, A0, A, B] =
    new Sink(underlying.provideSome(f))

  /**
   * See [[scalaz.zio.stream.ZSink#const]]
   */
  def const[C](c: => C): Sink[F, A0, A, C] =
    new Sink(underlying.const(c))

  /**
   * See [[scalaz.zio.stream.ZSink#unit]]
   */
  def unit: Sink[F, A0, A, Unit] =
    new Sink(underlying.unit)

  /**
   * See [[scalaz.zio.stream.ZSink#untilOutput]]
   */
  def untilOutput(f: B => Boolean): Sink[F, A0, A, B] =
    new Sink(underlying.untilOutput(f))

  /**
   * See [[scalaz.zio.stream.ZSink#?]]
   */
  def ? : Sink[F, A0, A, Option[B]] =
    new Sink(underlying.?)

  /**
   * See [[scalaz.zio.stream.ZSink#optional]]
   */
  def optional: Sink[F, A0, A, Option[B]] = ?

  /**
   * See [[scalaz.zio.stream.ZSink#race]]
   */
  def race[A2 >: A0, A1 <: A, B1 >: B](
    that: Sink[F, A2, A1, B1]
  ): Sink[F, A2, A1, B1] =
    new Sink(underlying.race(that.underlying))

  /**
   * See [[scalaz.zio.stream.ZSink#|]]
   */
  def |[A2 >: A0, A1 <: A, B1 >: B](
    that: Sink[F, A2, A1, B1]
  ): Sink[F, A2, A1, B1] = race(that)

  /**
   * See [[scalaz.zio.stream.ZSink#raceBoth]]
   */
  def raceBoth[A2 >: A0, A1 <: A, C](
    that: Sink[F, A2, A1, C]
  ): Sink[F, A2, A1, Either[B, C]] =
    new Sink(underlying.raceBoth(that.underlying))

  /**
   * See [[scalaz.zio.stream.ZSink#takeWhile]]
   */
  def takeWhile[A1 <: A](pred: A1 => Boolean): Sink[F, A0, A1, B] =
    new Sink(underlying.takeWhile(pred))

  /**
   * See [[scalaz.zio.stream.ZSink#dropWhile]]
   */
  def dropWhile[A1 <: A](pred: A1 => Boolean): Sink[F, A0, A1, B] =
    new Sink(underlying.dropWhile(pred))

}

object Sink {

  import Stream.liftZIO

  final def more[F[+ _], A0, A, B](end: F[B])(input: A => Sink[F, A0, A, B])(implicit E: Effect[F]): Sink[F, A0, A, B] =
    new Sink(ZSink.more(liftZIO(end))(input.andThen(_.underlying)))

  final def succeedLazy[F[+ _], B](b: => B): Sink[F, Nothing, Any, B] =
    new Sink(ZSink.succeedLazy(b))

  final def drain[F[+ _]]: Sink[F, Nothing, Any, Unit] =
    new Sink(ZSink.drain)

  final def collect[F[+ _], A]: Sink[F, Nothing, A, List[A]] =
    new Sink(ZSink.collect)

  final def fromEffect[F[+ _], B](b: => F[B])(implicit E: Effect[F]): Sink[F, Nothing, Any, B] =
    new Sink(ZSink.fromEffect(liftZIO(b)))

  // TODO Not sure this is the right thing to do, what does the error () mean?
  final def fromFunction[F[+ _], A, B](f: A => B): Sink[F, Nothing, A, B] =
    new Sink(ZSink.fromFunction(f).mapError(_ => new IllegalStateException()))

  // TODO Not sure this is the right thing to do, what does the error () mean?
  final def identity[F[+ _], A]: Sink[F, A, A, A] =
    new Sink(ZSink.identity[A].mapError(_ => new IllegalStateException()))

  final def fail[F[+ _], E <: Throwable](e: E): Sink[F, Nothing, Any, Nothing] =
    new Sink(ZSink.fail[E](e))

  def fold[F[+ _], A0, A, S](z: S)(f: (S, A) => Step[S, A0]): Sink[F, A0, A, S] =
    new Sink(ZSink.fold(z)(f))

  def foldLeft[F[+ _], A0, A, S](z: S)(f: (S, A) => S): Sink[F, A0, A, S] =
    new Sink(ZSink.foldLeft(z)(f))

  def foldM[F[+ _], A0, A, S](z: F[S])(f: (S, A) => F[Step[S, A0]])(implicit E: Effect[F]): Sink[F, A0, A, S] =
    new Sink(ZSink.foldM(liftZIO(z))((s, a) => liftZIO(f(s, a))))

  def readWhileM[F[+ _], A](p: A => F[Boolean])(implicit E: Effect[F]): Sink[F, A, A, List[A]] =
    new Sink(ZSink.readWhileM(p.andThen(liftZIO(_))))

  def readWhile[F[+ _], A](p: A => Boolean): Sink[F, A, A, List[A]] =
    new Sink(ZSink.readWhile(p))

  def ignoreWhile[F[+ _], A](p: A => Boolean): Sink[F, A, A, Unit] =
    new Sink(ZSink.ignoreWhile(p))

  def ignoreWhileM[F[+ _], A](p: A => F[Boolean])(implicit E: Effect[F]): Sink[F, A, A, Unit] =
    new Sink(ZSink.ignoreWhileM(p.andThen(liftZIO(_))))

  // TODO Not sure this is the right thing to do, what does the error () mean?
  def await[F[+ _], A]: Sink[F, Nothing, A, A] =
    new Sink(ZSink.await.mapError(_ => new IllegalStateException()))

  def read1[F[+ _], E <: Throwable, A](e: Option[A] => E)(p: A => Boolean): Sink[F, A, A, A] =
    new Sink(ZSink.read1(e)(p))

}
