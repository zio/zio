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

package zio.stream

import zio._
import zio.clock.Clock
import zio.duration.Duration

object Sink extends Serializable {

  /**
   * see [[ZSink.await]]
   */
  def await[A]: Sink[Unit, Nothing, A, A] =
    ZSink.await

  /**
   * see [[ZSink.collectAll]]
   */
  def collectAll[A]: Sink[Nothing, Nothing, A, List[A]] =
    ZSink.collectAll

  /**
   * see [[ZSink.collectAllN]]
   */
  def collectAllN[A](n: Long): ZSink[Any, Nothing, A, A, List[A]] =
    ZSink.collectAllN(n)

  /**
   * see [[ZSink.collectAllToSet]]
   */
  def collectAllToSet[A]: ZSink[Any, Nothing, Nothing, A, Set[A]] =
    ZSink.collectAllToSet

  /**
   * see [[ZSink.collectAllToSetN]]
   */
  def collectAllToSetN[A](n: Long): ZSink[Any, Nothing, A, A, Set[A]] =
    ZSink.collectAllToSetN(n)

  /**
   * see [[ZSink.collectAllToMap]]
   */
  def collectAllToMap[K, A](key: A => K)(f: (A, A) => A): ZSink[Any, Nothing, Nothing, A, Map[K, A]] =
    ZSink.collectAllToMap(key)(f)

  /**
   * see [[ZSink.collectAllToMapN]]
   */
  def collectAllToMapN[K, A](n: Long)(key: A => K)(f: (A, A) => A): ZSink[Any, Nothing, A, A, Map[K, A]] =
    ZSink.collectAllToMapN(n)(key)(f)

  /**
   * see [[ZSink.collectAllWhile]]
   */
  def collectAllWhile[A](p: A => Boolean): Sink[Nothing, A, A, List[A]] =
    collectAllWhileM(a => IO.succeedNow(p(a)))

  /**
   * see [[ZSink.collectAllWhileM]]
   */
  def collectAllWhileM[E, A](p: A => IO[E, Boolean]): Sink[E, A, A, List[A]] =
    ZSink.collectAllWhileM(p)

  /**
   * see [[ZSink.count]]
   */
  def count[A]: Sink[Nothing, Nothing, A, Long] = ZSink.count[A]

  /**
   * see [[ZSink.die]]
   */
  def die(e: => Throwable): Sink[Nothing, Nothing, Any, Nothing] =
    ZSink.die(e)

  /**
   * see [[ZSink.dieMessage]]
   */
  def dieMessage(m: => String): Sink[Nothing, Nothing, Any, Nothing] =
    ZSink.dieMessage(m)

  /**
   * see [[ZSink.drain]]
   */
  def drain: Sink[Nothing, Nothing, Any, Unit] =
    ZSink.drain

  /**
   * see [[ZSink.head]]
   */
  def head[A]: Sink[Nothing, A, A, Option[A]] =
    ZSink.head

  /**
   * see [[ZSink.last]]
   */
  def last[A]: Sink[Nothing, Nothing, A, Option[A]] =
    ZSink.last

  /**
   * see [[ZSink.fail]]
   */
  def fail[E](e: => E): Sink[E, Nothing, Any, Nothing] =
    ZSink.fail(e)

  /**
   * see [[ZSink.fold]]
   */
  def fold[A0, A, S](
    z: S
  )(contFn: S => Boolean)(f: (S, A) => (S, Chunk[A0])): Sink[Nothing, A0, A, S] =
    ZSink.fold(z)(contFn)(f)

  /**
   * see [[ZSink.foldLeft]]
   */
  def foldLeft[A, S](z: S)(f: (S, A) => S): Sink[Nothing, Nothing, A, S] =
    ZSink.foldLeft(z)(f)

  /**
   * see [[ZSink.foldLeftM]]
   */
  def foldLeftM[E, A, S](z: S)(f: (S, A) => IO[E, S]): Sink[E, Nothing, A, S] =
    ZSink.foldLeftM(z)(f)

  /**
   * see [[ZSink.foldM]]
   */
  def foldM[E, A0, A, S](
    z: S
  )(contFn: S => Boolean)(f: (S, A) => IO[E, (S, Chunk[A0])]): Sink[E, A0, A, S] =
    ZSink.foldM(z)(contFn)(f)

  /**
   * see [[ZSink.foldUntilM]]
   */
  def foldUntilM[E, S, A](z: S, max: Long)(f: (S, A) => IO[E, S]): Sink[E, A, A, S] =
    ZSink.foldUntilM(z, max)(f)

  /**
   * see [[ZSink.foldUntil]]
   */
  def foldUntil[S, A](z: S, max: Long)(f: (S, A) => S): Sink[Nothing, A, A, S] =
    ZSink.foldUntil(z, max)(f)

  /**
   * see [[ZSink.foldWeightedM]]
   */
  def foldWeightedM[E, E1 >: E, A, S](
    z: S
  )(costFn: A => IO[E, Long], max: Long)(
    f: (S, A) => IO[E1, S]
  ): Sink[E1, A, A, S] = ZSink.foldWeightedM[Any, Any, E, E1, A, S](z)(costFn, max)(f)

  /**
   * see [[ZSink.foldWeightedDecomposeM]]
   */
  def foldWeightedDecomposeM[E, E1 >: E, A, S](
    z: S
  )(costFn: A => IO[E, Long], max: Long, decompose: A => IO[E, Chunk[A]])(
    f: (S, A) => IO[E1, S]
  ): Sink[E1, A, A, S] =
    ZSink.foldWeightedDecomposeM[Any, Any, E, E1, A, S](z)(costFn, max, decompose)(f)

  /**
   * see [[ZSink.foldWeighted]]
   */
  def foldWeighted[A, S](
    z: S
  )(costFn: A => Long, max: Long)(
    f: (S, A) => S
  ): Sink[Nothing, A, A, S] =
    ZSink.foldWeighted(z)(costFn, max)(f)

  /**
   * see [[ZSink.foldWeighted]]
   */
  def foldWeightedDecompose[A, S](
    z: S
  )(costFn: A => Long, max: Long, decompose: A => Chunk[A])(
    f: (S, A) => S
  ): Sink[Nothing, A, A, S] =
    ZSink.foldWeightedDecompose(z)(costFn, max, decompose)(f)

  /**
   * see [[ZSink.fromEffect]]
   */
  def fromEffect[E, B](b: => IO[E, B]): Sink[E, Nothing, Any, B] =
    ZSink.fromEffect(b)

  /**
   * see [[ZSink.fromFunction]]
   */
  def fromFunction[A, B](f: A => B): Sink[Unit, Nothing, A, B] =
    ZSink.fromFunction(f)

  /**
   * see [[ZSink.fromFunctionM]]
   */
  def fromFunctionM[E, A, B](f: A => ZIO[Any, E, B]): Sink[Option[E], Nothing, A, B] =
    ZSink.fromFunctionM(f)

  /**
   * see [[ZSink.halt]]
   */
  def halt[E](e: => Cause[E]): Sink[E, Nothing, Any, Nothing] =
    ZSink.halt(e)

  /**
   * see [[ZSink.identity]]
   */
  def identity[A]: Sink[Unit, Nothing, A, A] =
    ZSink.identity

  /**
   * see [[ZSink.ignoreWhile]]
   */
  def ignoreWhile[A](p: A => Boolean): Sink[Nothing, A, A, Unit] =
    ZSink.ignoreWhile(p)

  /**
   * see [[ZSink.ignoreWhileM]]
   */
  def ignoreWhileM[E, A](p: A => IO[E, Boolean]): Sink[E, A, A, Unit] =
    ZSink.ignoreWhileM(p)

  /**
   * see [[ZSink.pull1]]
   */
  def pull1[E, A0, A, B](
    end: IO[E, B]
  )(input: A => Sink[E, A0, A, B]): Sink[E, A0, A, B] =
    ZSink.pull1(end)(input)

  /**
   * see [[ZSink.read1]]
   */
  def read1[E, A](e: Option[A] => E)(p: A => Boolean): Sink[E, A, A, A] =
    ZSink.read1(e)(p)

  /**
   * see [[ZSink.splitLines]]
   */
  val splitLines: Sink[Nothing, String, String, Chunk[String]] = ZSink.splitLines

  /**
   * see [[ZSink.splitLinesChunk]]
   */
  val splitLinesChunk: Sink[Nothing, Chunk[String], Chunk[String], Chunk[String]] = ZSink.splitLinesChunk

  /**
   * see [[ZSink.succeed]]
   */
  def succeed[A, B](b: => B): Sink[Nothing, A, A, B] =
    ZSink.succeed(b)

  /**
   * see [[ZSink.sum]]
   */
  def sum[A: Numeric]: Sink[Nothing, Nothing, A, A] = ZSink.sum[A]

  /**
   * see [[ZSink.throttleEnforce]]
   */
  def throttleEnforce[A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => Long
  ): ZManaged[Clock, Nothing, ZSink[Clock, Nothing, Nothing, A, Option[A]]] =
    ZSink.throttleEnforce(units, duration, burst)(costFn)

  /**
   * see [[ZSink.throttleEnforceM]]
   */
  def throttleEnforceM[E, A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => IO[E, Long]
  ): ZManaged[Clock, E, ZSink[Clock, E, Nothing, A, Option[A]]] =
    ZSink.throttleEnforceM[Any, E, A](units, duration, burst)(costFn)

  /**
   * see [[ZSink.throttleShape]]
   */
  def throttleShape[A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => Long
  ): ZManaged[Clock, Nothing, ZSink[Clock, Nothing, Nothing, A, A]] =
    ZSink.throttleShape(units, duration, burst)(costFn)

  /**
   * see [[ZSink.throttleShapeM]]
   */
  def throttleShapeM[E, A](units: Long, duration: Duration, burst: Long = 0)(
    costFn: A => IO[E, Long]
  ): ZManaged[Clock, E, ZSink[Clock, E, Nothing, A, A]] =
    ZSink.throttleShapeM[Any, E, A](units, duration, burst)(costFn)

  /**
   * see [[ZSink.utf8DecodeChunk]]
   */
  val utf8DecodeChunk: Sink[Nothing, Chunk[Byte], Chunk[Byte], String] =
    ZSink.utf8DecodeChunk

  private[zio] def succeedNow[A, B](b: B): Sink[Nothing, A, A, B] =
    ZSink.succeedNow(b)
}
