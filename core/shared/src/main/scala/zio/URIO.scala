/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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

package zio

import zio.internal.Platform
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.reflect.ClassTag

object URIO {

  /**
   * @see
   *   [[zio.ZIO.absolve]]
   */
  def absolve[R, A](v: => URIO[R, Either[Nothing, A]])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.absolve(v)

  /**
   * @see
   *   [[zio.ZIO.accessM]]
   */
  @deprecated("use environmentWith", "2.0.0")
  def accessM[R]: ZIO.EnvironmentWithPartiallyApplied[R] =
    ZIO.accessM[R]

  /**
   * @see
   *   [[zio.ZIO.accessZIO]]
   */
  @deprecated("use environmentWith", "2.0.0")
  def accessZIO[R]: ZIO.EnvironmentWithPartiallyApplied[R] =
    ZIO.accessZIO[R]

  /**
   * @see
   *   acquireReleaseWith in [[zio.ZIO]]
   */
  def acquireReleaseWith[R, A](acquire: => URIO[R, A]): ZIO.Acquire[R, Nothing, A] =
    ZIO.acquireReleaseWith(acquire)

  /**
   * @see
   *   acquireReleaseWith in [[zio.ZIO]]
   */
  def acquireReleaseWith[R, A, B](
    acquire: => URIO[R, A],
    release: A => URIO[R, Any],
    use: A => URIO[R, B]
  )(implicit trace: ZTraceElement): URIO[R, B] = ZIO.acquireReleaseWith(acquire, release, use)

  /**
   * @see
   *   acquireReleaseExitWith in [[zio.ZIO]]
   */
  def acquireReleaseExitWith[R, A](acquire: => URIO[R, A]): ZIO.AcquireExit[R, Nothing, A] =
    ZIO.acquireReleaseExitWith(acquire)

  /**
   * @see
   *   acquireReleaseExitWith in [[zio.ZIO]]
   */
  def acquireReleaseExitWith[R, A, B](
    acquire: => URIO[R, A],
    release: (A, Exit[Nothing, B]) => URIO[R, Any],
    use: A => URIO[R, B]
  )(implicit trace: ZTraceElement): URIO[R, B] = ZIO.acquireReleaseExitWith(acquire, release, use)

  /**
   * @see
   *   [[zio.ZIO.allowInterrupt]]
   */
  def allowInterrupt(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.allowInterrupt

  /**
   * @see
   *   [[zio.ZIO.apply]]
   */
  def apply[A](a: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.succeed(a)

  /**
   * @see
   *   [[zio.ZIO.async]]
   */
  def async[R, A](register: (URIO[R, A] => Unit) => Any, blockingOn: => FiberId = FiberId.None)(implicit
    trace: ZTraceElement
  ): URIO[R, A] =
    ZIO.async(register, blockingOn)

  /**
   * @see
   *   [[zio.ZIO.asyncMaybe]]
   */
  def asyncMaybe[R, A](
    register: (URIO[R, A] => Unit) => Option[URIO[R, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.asyncMaybe(register, blockingOn)

  /**
   * @see
   *   [[zio.ZIO.asyncZIO]]
   */
  def asyncZIO[R, A](register: (URIO[R, A] => Unit) => URIO[R, Any])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.asyncZIO(register)

  /**
   * @see
   *   [[zio.ZIO.asyncInterrupt]]
   */
  def asyncInterrupt[R, A](
    register: (URIO[R, A] => Unit) => Either[Canceler[R], URIO[R, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.asyncInterrupt(register, blockingOn)

  /**
   * @see
   *   See [[zio.ZIO.blocking]]
   */
  def blocking[R, A](zio: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.blocking(zio)

  /**
   * @see
   *   See [[zio.ZIO.blockingExecutor]]
   */
  def blockingExecutor(implicit trace: ZTraceElement): UIO[Executor] =
    ZIO.blockingExecutor

  /**
   * @see
   *   bracket in [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseWith", "2.0.0")
  def bracket[R, A](acquire: => URIO[R, A]): ZIO.Acquire[R, Nothing, A] =
    ZIO.bracket(acquire)

  /**
   * @see
   *   bracket in [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseWith", "2.0.0")
  def bracket[R, A, B](
    acquire: => URIO[R, A],
    release: A => URIO[R, Any],
    use: A => URIO[R, B]
  )(implicit trace: ZTraceElement): URIO[R, B] = ZIO.bracket(acquire, release, use)

  /**
   * @see
   *   bracketExit in [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseExitWith", "2.0.0")
  def bracketExit[R, A](acquire: => URIO[R, A]): ZIO.AcquireExit[R, Nothing, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see
   *   bracketExit in [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseExitWith", "2.0.0")
  def bracketExit[R, A, B](
    acquire: => URIO[R, A],
    release: (A, Exit[Nothing, B]) => URIO[R, Any],
    use: A => URIO[R, B]
  )(implicit trace: ZTraceElement): URIO[R, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see
   *   [[zio.ZIO.checkInterruptible]]
   */
  def checkInterruptible[R, A](f: InterruptStatus => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.checkInterruptible(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.collect[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collect[R, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, Option[Nothing], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.collect(in)(f)

  /**
   * @see
   *   See [[[zio.ZIO.collect[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def collect[R, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => ZIO[R, Option[Nothing], (Key2, Value2)])(implicit
    trace: ZTraceElement
  ): URIO[R, Map[Key2, Value2]] =
    ZIO.collect(map)(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.collectAll[R,E,A,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collectAll[R, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[URIO[R, A]]
  )(implicit bf: BuildFrom[Collection[URIO[R, A]], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[[zio.ZIO.collectAll[R,E,A](in:Set*]]]
   */
  def collectAll[R, A](in: Set[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Set[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[[zio.ZIO.collectAll[R,E,A](in:Array*]]]
   */
  def collectAll[R, A: ClassTag](in: Array[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Array[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[[zio.ZIO.collectAll[R,E,A](in:Option*]]]
   */
  def collectAll[R, A](in: Option[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Option[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[[zio.ZIO.collectAll[R,E,A](in:zio\.NonEmptyChunk*]]]
   */
  def collectAll[R, A](in: NonEmptyChunk[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, NonEmptyChunk[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[zio.ZIO.collectAll_]]
   */
  @deprecated("use collectAllDiscard", "2.0.0")
  def collectAll_[R, A](in: => Iterable[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.collectAll_(in)

  /**
   * @see
   *   See [[zio.ZIO.collectAllDiscard]]
   */
  def collectAllDiscard[R, A](in: => Iterable[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.collectAllDiscard(in)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.collectAllPar[R,E,A,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collectAllPar[R, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[URIO[R, A]]
  )(implicit bf: BuildFrom[Collection[URIO[R, A]], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.collectAllPar(in)

  /**
   * @see
   *   See [[[zio.ZIO.collectAllPar[R,E,A](as:Set*]]]
   */
  def collectAllPar[R, A](as: Set[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Set[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see
   *   See [[[zio.ZIO.collectAllPar[R,E,A](as:Array*]]]
   */
  def collectAllPar[R, A: ClassTag](as: Array[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Array[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see
   *   See [[[zio.ZIO.collectAllPar[R,E,A](as:zio\.NonEmptyChunk*]]]
   */
  def collectAllPar[R, A](as: NonEmptyChunk[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, NonEmptyChunk[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see
   *   See [[zio.ZIO.collectAllPar_]]
   */
  @deprecated("use collectAllParDiscard", "2.0.0")
  def collectAllPar_[R, A](in: => Iterable[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.collectAllPar_(in)

  /**
   * @see
   *   See [[zio.ZIO.collectAllParDiscard]]
   */
  def collectAllParDiscard[R, A](in: => Iterable[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.collectAllParDiscard(in)

  /**
   * @see
   *   See [[zio.ZIO.collectAllParN]]
   */
  @deprecated("use collectAllPar", "2.0.0")
  def collectAllParN[R, A, Collection[+Element] <: Iterable[Element]](n: => Int)(
    as: Collection[URIO[R, A]]
  )(implicit bf: BuildFrom[Collection[URIO[R, A]], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see
   *   See [[zio.ZIO.collectAllParN_]]
   */
  @deprecated("use collectAllParDiscard", "2.0.0")
  def collectAllParN_[R, A](n: => Int)(as: => Iterable[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.collectAllParN_(n)(as)

  /**
   * @see
   *   See [[zio.ZIO.collectAllParNDiscard]]
   */
  @deprecated("use collectAllParDiscard", "2.0.0")
  def collectAllParNDiscard[R, A](n: => Int)(as: => Iterable[URIO[R, A]])(implicit
    trace: ZTraceElement
  ): URIO[R, Unit] =
    ZIO.collectAllParNDiscard(n)(as)

  /**
   * @see
   *   [[zio.ZIO.collectAllSuccesses]]
   */
  def collectAllSuccesses[R, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[URIO[R, A]]
  )(implicit bf: BuildFrom[Collection[URIO[R, A]], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see
   *   [[zio.ZIO.collectAllSuccessesPar]]
   */
  def collectAllSuccessesPar[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[URIO[R, A]]
  )(implicit bf: BuildFrom[Collection[URIO[R, A]], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see
   *   [[zio.ZIO.collectAllSuccessesParN]]
   */
  @deprecated("use collectAllSuccessesPar", "2.0.0")
  def collectAllSuccessesParN[R, A, Collection[+Element] <: Iterable[Element]](n: => Int)(
    as: Collection[URIO[R, A]]
  )(implicit bf: BuildFrom[Collection[URIO[R, A]], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see
   *   [[zio.ZIO.collectAllWith]]
   */
  def collectAllWith[R, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[URIO[R, A]])(
    f: PartialFunction[A, B]
  )(implicit bf: BuildFrom[Collection[URIO[R, A]], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see
   *   [[zio.ZIO.collectAllWithPar]]
   */
  def collectAllWithPar[R, A, B, Collection[+Element] <: Iterable[Element]](as: Collection[URIO[R, A]])(
    f: PartialFunction[A, B]
  )(implicit bf: BuildFrom[Collection[URIO[R, A]], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see
   *   [[zio.ZIO.collectAllWithParN]]
   */
  @deprecated("use collectAllWithPar", "2.0.0")
  def collectAllWithParN[R, A, B, Collection[+Element] <: Iterable[Element]](n: => Int)(as: Collection[URIO[R, A]])(
    f: PartialFunction[A, B]
  )(implicit bf: BuildFrom[Collection[URIO[R, A]], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.collectFirst]]
   */
  def collectFirst[R, A, B](as: => Iterable[A])(f: A => URIO[R, Option[B]])(implicit
    trace: ZTraceElement
  ): URIO[R, Option[B]] =
    ZIO.collectFirst(as)(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.collectPar[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collectPar[R, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, Option[Nothing], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.collectPar(in)(f)

  /**
   * @see
   *   See [[[zio.ZIO.collectPar[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def collectPar[R, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => ZIO[R, Option[Nothing], (Key2, Value2)])(implicit
    trace: ZTraceElement
  ): URIO[R, Map[Key2, Value2]] =
    ZIO.collectPar(map)(f)

  /**
   * @see
   *   See [[zio.ZIO.collectParN]]
   */
  @deprecated("use collectPar", "2.0.0")
  def collectParN[R, A, B, Collection[+Element] <: Iterable[Element]](n: => Int)(in: Collection[A])(
    f: A => ZIO[R, Option[Nothing], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.collectParN(n)(in)(f)

  /**
   * @see
   *   See [[zio.ZIO.debug]]
   */
  def debug(value: => Any)(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.debug(value)

  /**
   * @see
   *   [[zio.ZIO.descriptor]]
   */
  def descriptor(implicit trace: ZTraceElement): UIO[Fiber.Descriptor] =
    ZIO.descriptor

  /**
   * @see
   *   [[zio.ZIO.descriptorWith]]
   */
  def descriptorWith[R, A](f: Fiber.Descriptor => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.descriptorWith(f)

  /**
   * @see
   *   [[zio.ZIO.die]]
   */
  def die(t: => Throwable)(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.die(t)

  /**
   * @see
   *   [[zio.ZIO.dieMessage]]
   */
  def dieMessage(message: => String)(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.dieMessage(message)

  /**
   * @see
   *   [[zio.ZIO.done]]
   */
  def done[A](r: => Exit[Nothing, A])(implicit trace: ZTraceElement): UIO[A] =
    ZIO.done(r)

  /**
   * @see
   *   [[zio.ZIO.effectAsync]]
   */
  @deprecated("use async", "2.0.0")
  def effectAsync[R, A](register: (URIO[R, A] => Unit) => Any, blockingOn: => FiberId = FiberId.None)(implicit
    trace: ZTraceElement
  ): URIO[R, A] =
    ZIO.effectAsync(register, blockingOn)

  /**
   * @see
   *   [[zio.ZIO.effectAsyncMaybe]]
   */
  @deprecated("use asyncMaybe", "2.0.0")
  def effectAsyncMaybe[R, A](
    register: (URIO[R, A] => Unit) => Option[URIO[R, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.effectAsyncMaybe(register, blockingOn)

  /**
   * @see
   *   [[zio.ZIO.effectAsyncM]]
   */
  @deprecated("use asyncZIO", "2.0.0")
  def effectAsyncM[R, A](register: (URIO[R, A] => Unit) => URIO[R, Any])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.effectAsyncM(register)

  /**
   * @see
   *   [[zio.ZIO.effectAsyncInterrupt]]
   */
  @deprecated("use asyncInterrupt", "2.0.0")
  def effectAsyncInterrupt[R, A](
    register: (URIO[R, A] => Unit) => Either[Canceler[R], URIO[R, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.effectAsyncInterrupt(register, blockingOn)

  /**
   * @see
   *   [[zio.ZIO.effectSuspendTotal]]
   */
  @deprecated("use suspendSucceed", "2.0.0")
  def effectSuspendTotal[R, A](rio: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.effectSuspendTotal(rio)

  /**
   * @see
   *   [[zio.ZIO.effectSuspendTotalWith]]
   */
  @deprecated("use suspendSucceedWith", "2.0.0")
  def effectSuspendTotalWith[R, A](p: (Platform, Fiber.Id) => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.effectSuspendTotalWith(p)

  /**
   * @see
   *   [[zio.ZIO.effectTotal]]
   */
  @deprecated("use succeed", "2.0.0")
  def effectTotal[A](effect: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.effectTotal(effect)

  /**
   * @see
   *   [[zio.ZIO.environment]]
   */
  def environment[R](implicit trace: ZTraceElement): URIO[R, ZEnvironment[R]] =
    ZIO.environment

  /**
   * @see
   *   [[zio.ZIO.environmentWith]]
   */
  def environmentWith[R]: ZIO.EnvironmentWithPartiallyApplied[R] =
    ZIO.environmentWith[R]

  /**
   * @see
   *   See [[zio.ZIO.executor]]
   */
  def executor(implicit trace: ZTraceElement): UIO[Executor] =
    ZIO.executor

  /**
   * @see
   *   See [[zio.ZIO.exists]]
   */
  def exists[R, A](as: => Iterable[A])(f: A => URIO[R, Boolean])(implicit trace: ZTraceElement): URIO[R, Boolean] =
    ZIO.exists(as)(f)

  /**
   * @see
   *   [[zio.ZIO.failCause]]
   */
  def failCause(cause: => Cause[Nothing])(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.failCause(cause)

  /**
   * @see
   *   [[zio.ZIO.fiberId]]
   */
  def fiberId(implicit trace: ZTraceElement): UIO[FiberId] =
    ZIO.fiberId

  /**
   * @see
   *   [[zio.ZIO.filter[R,E,A,Collection*]]
   */
  def filter[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => URIO[R, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.filter(as)(f)

  /**
   * @see
   *   [[[zio.ZIO.filter[R,E,A](as:Set*]]]
   */
  def filter[R, A](as: Set[A])(f: A => URIO[R, Boolean])(implicit trace: ZTraceElement): URIO[R, Set[A]] =
    ZIO.filter(as)(f)

  /**
   * @see
   *   [[zio.ZIO.filterPar[R,E,A,Collection*]]
   */
  def filterPar[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => URIO[R, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.filterPar(as)(f)

  /**
   * @see
   *   [[[zio.ZIO.filterPar[R,E,A](as:Set*]]]
   */
  def filterPar[R, A](as: Set[A])(f: A => URIO[R, Boolean])(implicit trace: ZTraceElement): URIO[R, Set[A]] =
    ZIO.filterPar(as)(f)

  /**
   * @see
   *   [[zio.ZIO.filterNot[R,E,A,Collection*]]
   */
  def filterNot[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => URIO[R, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.filterNot(as)(f)

  /**
   * @see
   *   [[[zio.ZIO.filterNot[R,E,A](as:Set*]]]
   */
  def filterNot[R, A](as: Set[A])(f: A => URIO[R, Boolean])(implicit trace: ZTraceElement): URIO[R, Set[A]] =
    ZIO.filterNot(as)(f)

  /**
   * @see
   *   [[zio.ZIO.filterNotPar[R,E,A,Collection*]]
   */
  def filterNotPar[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => URIO[R, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.filterNotPar(as)(f)

  /**
   * @see
   *   [[[zio.ZIO.filterNotPar[R,E,A](as:Set*]]]
   */
  def filterNotPar[R, A](as: Set[A])(f: A => URIO[R, Boolean])(implicit trace: ZTraceElement): URIO[R, Set[A]] =
    ZIO.filterNotPar(as)(f)

  /**
   * @see
   *   [[zio.ZIO.firstSuccessOf]]
   */
  def firstSuccessOf[R, A](
    rio: => URIO[R, A],
    rest: => Iterable[URIO[R, A]]
  )(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.firstSuccessOf(rio, rest)

  /**
   * @see
   *   [[zio.ZIO.flatten]]
   */
  def flatten[R, A](taskr: => URIO[R, URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.flatten(taskr)

  /**
   * @see
   *   [[zio.ZIO.foldLeft]]
   */
  def foldLeft[R, S, A](in: => Iterable[A])(zero: => S)(f: (S, A) => URIO[R, S])(implicit
    trace: ZTraceElement
  ): URIO[R, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see
   *   [[zio.ZIO.foldRight]]
   */
  def foldRight[R, S, A](in: => Iterable[A])(zero: => S)(f: (A, S) => URIO[R, S])(implicit
    trace: ZTraceElement
  ): URIO[R, S] =
    ZIO.foldRight(in)(zero)(f)

  /**
   * @see
   *   See [[zio.ZIO.forall]]
   */
  def forall[R, A](as: => Iterable[A])(f: A => URIO[R, Boolean])(implicit trace: ZTraceElement): URIO[R, Boolean] =
    ZIO.forall(as)(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.foreach[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def foreach[R, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(
    f: A => URIO[R, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   [[[zio.ZIO.foreach[R,E,A,B](in:Set*]]]
   */
  final def foreach[R, A, B](in: Set[A])(f: A => URIO[R, B])(implicit trace: ZTraceElement): URIO[R, Set[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   [[[zio.ZIO.foreach[R,E,A,B](in:Array*]]]
   */
  final def foreach[R, A, B: ClassTag](in: Array[A])(f: A => URIO[R, B])(implicit
    trace: ZTraceElement
  ): URIO[R, Array[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   See [[[zio.ZIO.foreach[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def foreach[R, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => URIO[R, (Key2, Value2)])(implicit trace: ZTraceElement): URIO[R, Map[Key2, Value2]] =
    ZIO.foreach(map)(f)

  /**
   * @see
   *   [[[zio.ZIO.foreach[R,E,A,B](in:Option*]]]
   */
  def foreach[R, A, B](in: Option[A])(f: A => URIO[R, B])(implicit trace: ZTraceElement): URIO[R, Option[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   [[[zio.ZIO.foreach[R,E,A,B](in:zio\.NonEmptyChunk*]]]
   */
  def foreach[R, A, B](in: NonEmptyChunk[A])(f: A => URIO[R, B])(implicit
    trace: ZTraceElement
  ): URIO[R, NonEmptyChunk[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   See [[zio.ZIO.foreachExec]]
   */
  final def foreachExec[R, A, B, Collection[+Element] <: Iterable[Element]](as: Collection[A])(
    exec: => ExecutionStrategy
  )(
    f: A => URIO[R, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.foreachExec(as)(exec)(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.foreachPar[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def foreachPar[R, A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    fn: A => URIO[R, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see
   *   [[[zio.ZIO.foreachPar[R,E,A,B](as:Set*]]]
   */
  final def foreachPar[R, A, B](as: Set[A])(fn: A => URIO[R, B])(implicit trace: ZTraceElement): URIO[R, Set[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see
   *   [[[zio.ZIO.foreachPar[R,E,A,B](as:Array*]]]
   */
  final def foreachPar[R, A, B: ClassTag](as: Array[A])(fn: A => URIO[R, B])(implicit
    trace: ZTraceElement
  ): URIO[R, Array[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see
   *   See [[[zio.ZIO.foreachPar[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def foreachPar[R, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => URIO[R, (Key2, Value2)])(implicit trace: ZTraceElement): URIO[R, Map[Key2, Value2]] =
    ZIO.foreachPar(map)(f)

  /**
   * @see
   *   [[[zio.ZIO.foreachPar[R,E,A,B](as:zio\.NonEmptyChunk*]]]
   */
  def foreachPar[R, A, B](as: NonEmptyChunk[A])(fn: A => URIO[R, B])(implicit
    trace: ZTraceElement
  ): URIO[R, NonEmptyChunk[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see
   *   [[zio.ZIO.foreachParN]]
   */
  @deprecated("use foreachPar", "2.0.0")
  def foreachParN[R, A, B, Collection[+Element] <: Iterable[Element]](n: => Int)(
    as: Collection[A]
  )(
    fn: A => URIO[R, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): URIO[R, Collection[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see
   *   [[zio.ZIO.foreach_]]
   */
  @deprecated("use foreachDiscard", "2.0.0")
  def foreach_[R, A](as: => Iterable[A])(f: A => URIO[R, Any])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see
   *   [[zio.ZIO.foreachDiscard]]
   */
  def foreachDiscard[R, A](as: => Iterable[A])(f: A => URIO[R, Any])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.foreachDiscard(as)(f)

  /**
   * @see
   *   [[zio.ZIO.foreachPar_]]
   */
  @deprecated("use foreachParDiscard", "2.0.0")
  def foreachPar_[R, A, B](as: => Iterable[A])(f: A => URIO[R, Any])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see
   *   [[zio.ZIO.foreachParDiscard]]
   */
  def foreachParDiscard[R, A, B](as: => Iterable[A])(f: A => URIO[R, Any])(implicit
    trace: ZTraceElement
  ): URIO[R, Unit] =
    ZIO.foreachParDiscard(as)(f)

  /**
   * @see
   *   [[zio.ZIO.foreachParN_]]
   */
  @deprecated("use foreachParDiscard", "2.0.0")
  def foreachParN_[R, A, B](n: => Int)(as: => Iterable[A])(f: A => URIO[R, Any])(implicit
    trace: ZTraceElement
  ): URIO[R, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see
   *   [[zio.ZIO.foreachParNDiscard]]
   */
  @deprecated("use foreachParDiscard", "2.0.0")
  def foreachParNDiscard[R, A, B](n: => Int)(as: => Iterable[A])(f: A => URIO[R, Any])(implicit
    trace: ZTraceElement
  ): URIO[R, Unit] =
    ZIO.foreachParNDiscard(n)(as)(f)

  /**
   * @see
   *   [[zio.ZIO.forkAll]]
   */
  def forkAll[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[URIO[R, A]]
  )(implicit
    bf: BuildFrom[Collection[URIO[R, A]], A, Collection[A]],
    trace: ZTraceElement
  ): URIO[R, Fiber[Nothing, Collection[A]]] =
    ZIO.forkAll(as)

  /**
   * @see
   *   [[zio.ZIO.forkAll_]]
   */
  @deprecated("use forkAllDiscard", "2.0.0")
  def forkAll_[R, A](as: => Iterable[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.forkAll_(as)

  /**
   * @see
   *   [[zio.ZIO.forkAllDiscard]]
   */
  def forkAllDiscard[R, A](as: => Iterable[URIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.forkAllDiscard(as)

  /**
   * Constructs a `URIO` value of the appropriate type for the specified input.
   */
  def from[Input](input: => Input)(implicit
    constructor: ZIO.ZIOConstructor[Nothing, Nothing, Input],
    trace: ZTraceElement
  ): ZIO[constructor.OutEnvironment, constructor.OutError, constructor.OutSuccess] =
    constructor.make(input)

  /**
   * @see
   *   [[zio.ZIO.fromEither]]
   */
  def fromEither[A](v: => Either[Nothing, A])(implicit trace: ZTraceElement): UIO[A] =
    ZIO.fromEither(v)

  /**
   * @see
   *   [[zio.ZIO.fromFiber]]
   */
  def fromFiber[A](fiber: => Fiber[Nothing, A])(implicit trace: ZTraceElement): UIO[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see
   *   [[zio.ZIO.fromFiberM]]
   */
  @deprecated("use fromFiberZIO", "2.0.0")
  def fromFiberM[A](fiber: => UIO[Fiber[Nothing, A]])(implicit trace: ZTraceElement): UIO[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see
   *   [[zio.ZIO.fromFiberZIO]]
   */
  def fromFiberZIO[A](fiber: => UIO[Fiber[Nothing, A]])(implicit trace: ZTraceElement): UIO[A] =
    ZIO.fromFiberZIO(fiber)

  /**
   * @see
   *   [[zio.ZIO.fromFunction]]
   */
  @deprecated("use access", "2.0.0")
  def fromFunction[R, A](f: ZEnvironment[R] => A)(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.fromFunction(f)

  /**
   * @see
   *   [[zio.ZIO.fromFunctionM]]
   */
  @deprecated("use environmentWith", "2.0.0")
  def fromFunctionM[R, A](f: ZEnvironment[R] => UIO[A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.fromFunctionM(f)

  /**
   * @see
   *   [[zio.ZIO.getState]]
   */
  def getState[S: Tag](implicit trace: ZTraceElement): ZIO[ZState[S], Nothing, S] =
    ZIO.getState

  /**
   * @see
   *   [[zio.ZIO.getStateWith]]
   */
  def getStateWith[S]: ZIO.GetStateWithPartiallyApplied[S] =
    ZIO.getStateWith

  /**
   * @see
   *   [[zio.ZIO.halt]]
   */
  @deprecated("use failCause", "2.0.0")
  def halt(cause: => Cause[Nothing])(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.halt(cause)

  /**
   * @see
   *   [[zio.ZIO.ifM]]
   */
  @deprecated("use ifZIO", "2.0.0")
  def ifM[R](b: => URIO[R, Boolean]): ZIO.IfZIO[R, Nothing] =
    ZIO.ifM(b)

  /**
   * @see
   *   [[zio.ZIO.ifZIO]]
   */
  def ifZIO[R](b: => URIO[R, Boolean]): ZIO.IfZIO[R, Nothing] =
    ZIO.ifZIO(b)

  /**
   * @see
   *   [[zio.ZIO.infinity]]
   */
  def infinity(implicit trace: ZTraceElement): URIO[Clock, Nothing] =
    ZIO.infinity

  /**
   * @see
   *   [[zio.ZIO.interrupt]]
   */
  def interrupt(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.interrupt

  /**
   * @see
   *   See [[zio.ZIO.interruptAs]]
   */
  def interruptAs(fiberId: => FiberId)(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.interruptAs(fiberId)

  /**
   * @see
   *   [[zio.ZIO.interruptible]]
   */
  def interruptible[R, A](taskr: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.interruptible(taskr)

  /**
   * @see
   *   [[zio.ZIO.interruptibleMask]]
   */
  def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.interruptibleMask(k)

  /**
   * @see
   *   See [[zio.ZIO.iterate]]
   */
  def iterate[R, S](initial: => S)(cont: S => Boolean)(body: S => URIO[R, S])(implicit
    trace: ZTraceElement
  ): URIO[R, S] =
    ZIO.iterate(initial)(cont)(body)

  /**
   * @see
   *   [[zio.ZIO.lock]]
   */
  @deprecated("use onExecutor", "2.0.0")
  def lock[R, A](executor: => Executor)(taskr: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.lock(executor)(taskr)

  /**
   * @see
   *   See [[zio.ZIO.loop]]
   */
  def loop[R, A, S](initial: => S)(cont: S => Boolean, inc: S => S)(body: S => URIO[R, A])(implicit
    trace: ZTraceElement
  ): URIO[R, List[A]] =
    ZIO.loop(initial)(cont, inc)(body)

  /**
   * @see
   *   See [[zio.ZIO.loop_]]
   */
  @deprecated("use loopDiscard", "2.0.0")
  def loop_[R, S](initial: => S)(cont: S => Boolean, inc: S => S)(body: S => URIO[R, Any])(implicit
    trace: ZTraceElement
  ): URIO[R, Unit] =
    ZIO.loop_(initial)(cont, inc)(body)

  /**
   * @see
   *   See [[zio.ZIO.loopDiscard]]
   */
  def loopDiscard[R, S](initial: => S)(cont: S => Boolean, inc: S => S)(body: S => URIO[R, Any])(implicit
    trace: ZTraceElement
  ): URIO[R, Unit] =
    ZIO.loopDiscard(initial)(cont, inc)(body)

  /**
   * @see
   *   [[zio.ZIO.left]]
   */
  def left[R, A](a: => A)(implicit trace: ZTraceElement): URIO[R, Either[A, Nothing]] =
    ZIO.left(a)

  /**
   * @see
   *   [[zio.ZIO.mapN[R,E,A,B,C]*]]
   */
  @deprecated("use zip", "2.0.0")
  def mapN[R, A, B, C](urio1: => URIO[R, A], urio2: => URIO[R, B])(f: (A, B) => C)(implicit
    trace: ZTraceElement
  ): URIO[R, C] =
    ZIO.mapN(urio1, urio2)(f)

  /**
   * @see
   *   [[zio.ZIO.mapN[R,E,A,B,C,D]*]]
   */
  @deprecated("use zip", "2.0.0")
  def mapN[R, A, B, C, D](urio1: => URIO[R, A], urio2: => URIO[R, B], urio3: => URIO[R, C])(
    f: (A, B, C) => D
  )(implicit trace: ZTraceElement): URIO[R, D] =
    ZIO.mapN(urio1, urio2, urio3)(f)

  /**
   * @see
   *   [[zio.ZIO.mapN[R,E,A,B,C,D,F]*]]
   */
  @deprecated("use zip", "2.0.0")
  def mapN[R, A, B, C, D, F](urio1: => URIO[R, A], urio2: => URIO[R, B], urio3: => URIO[R, C], urio4: => URIO[R, D])(
    f: (A, B, C, D) => F
  )(implicit trace: ZTraceElement): URIO[R, F] =
    ZIO.mapN(urio1, urio2, urio3, urio4)(f)

  /**
   * @see
   *   [[zio.ZIO.mapParN[R,E,A,B,C]*]]
   */
  @deprecated("use zipPar", "2.0.0")
  def mapParN[R, A, B, C](urio1: => URIO[R, A], urio2: => URIO[R, B])(f: (A, B) => C)(implicit
    trace: ZTraceElement
  ): URIO[R, C] =
    ZIO.mapParN(urio1, urio2)(f)

  /**
   * @see
   *   [[zio.ZIO.mapParN[R,E,A,B,C,D]*]]
   */
  @deprecated("use zipPar", "2.0.0")
  def mapParN[R, A, B, C, D](urio1: => URIO[R, A], urio2: => URIO[R, B], urio3: => URIO[R, C])(
    f: (A, B, C) => D
  )(implicit trace: ZTraceElement): URIO[R, D] =
    ZIO.mapParN(urio1, urio2, urio3)(f)

  /**
   * @see
   *   [[zio.ZIO.mapParN[R,E,A,B,C,D,F]*]]
   */
  @deprecated("use zipPar", "2.0.0")
  def mapParN[R, A, B, C, D, F](urio1: => URIO[R, A], urio2: => URIO[R, B], urio3: => URIO[R, C], urio4: => URIO[R, D])(
    f: (A, B, C, D) => F
  )(implicit trace: ZTraceElement): URIO[R, F] =
    ZIO.mapParN(urio1, urio2, urio3, urio4)(f)

  /**
   * @see
   *   See [[zio.ZIO.memoize]]
   */
  def memoize[R, A, B](f: A => URIO[R, B])(implicit trace: ZTraceElement): UIO[A => URIO[R, B]] =
    ZIO.memoize(f)

  /**
   * @see
   *   [[zio.ZIO.mergeAll]]
   */
  def mergeAll[R, A, B](in: => Iterable[URIO[R, A]])(zero: => B)(f: (B, A) => B)(implicit
    trace: ZTraceElement
  ): URIO[R, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see
   *   [[zio.ZIO.mergeAllPar]]
   */
  def mergeAllPar[R, A, B](in: => Iterable[URIO[R, A]])(zero: => B)(f: (B, A) => B)(implicit
    trace: ZTraceElement
  ): URIO[R, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see
   *   [[zio.ZIO.never]]
   */
  def never(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.never

  /**
   * @see
   *   [[zio.ZIO.none]]
   */
  val none: UIO[Option[Nothing]] =
    ZIO.none

  /**
   * @see
   *   See [[zio.ZIO.not]]
   */
  def not[R](effect: => URIO[R, Boolean])(implicit trace: ZTraceElement): URIO[R, Boolean] =
    ZIO.not(effect)

  /**
   * @see
   *   [[zio.ZIO.onExecutor]]
   */
  def onExecutor[R, A](executor: => Executor)(taskr: URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.onExecutor(executor)(taskr)

  /**
   * @see
   *   [[zio.ZIO.provide]]
   */
  def provideAll[R, A](r: => ZEnvironment[R])(implicit trace: ZTraceElement): URIO[R, A] => UIO[A] =
    ZIO.provideAll(r)

  /**
   * @see
   *   [[zio.ZIO.raceAll]]
   */
  def raceAll[R, R1 <: R, A](taskr: => URIO[R, A], taskrs: => Iterable[URIO[R1, A]])(implicit
    trace: ZTraceElement
  ): URIO[R1, A] =
    ZIO.raceAll(taskr, taskrs)

  /**
   * @see
   *   [[zio.ZIO.reduceAll]]
   */
  def reduceAll[R, R1 <: R, A](a: => URIO[R, A], as: => Iterable[URIO[R1, A]])(f: (A, A) => A)(implicit
    trace: ZTraceElement
  ): URIO[R1, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see
   *   [[zio.ZIO.reduceAllPar]]
   */
  def reduceAllPar[R, R1 <: R, A](a: => URIO[R, A], as: => Iterable[URIO[R1, A]])(f: (A, A) => A)(implicit
    trace: ZTraceElement
  ): URIO[R1, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see
   *   [[zio.ZIO.replicate]]
   */
  def replicate[R, A](n: => Int)(effect: => URIO[R, A])(implicit trace: ZTraceElement): Iterable[URIO[R, A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see
   *   See [[zio.ZIO.replicateM]]
   */
  @deprecated("use replicateZIO", "2.0.0")
  def replicateM[R, A](n: => Int)(effect: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, Iterable[A]] =
    ZIO.replicateM(n)(effect)

  /**
   * @see
   *   See [[zio.ZIO.replicateM_]]
   */
  @deprecated("use replicateZIODiscard", "2.0.0")
  def replicateM_[R, A](n: => Int)(effect: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.replicateM_(n)(effect)

  /**
   * @see
   *   See [[zio.ZIO.replicateZIO]]
   */
  def replicateZIO[R, A](n: => Int)(effect: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, Iterable[A]] =
    ZIO.replicateZIO(n)(effect)

  /**
   * @see
   *   See [[zio.ZIO.replicateZIODiscard]]
   */
  def replicateZIODiscard[R, A](n: => Int)(effect: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.replicateZIODiscard(n)(effect)

  /**
   * @see
   *   [[zio.ZIO.reserve]]
   */
  def reserve[R, A, B](reservation: => URIO[R, Reservation[R, Nothing, A]])(use: A => URIO[R, B])(implicit
    trace: ZTraceElement
  ): URIO[R, B] =
    ZIO.reserve(reservation)(use)

  /**
   * @see
   *   [[zio.ZIO.right]]
   */
  def right[R, B](b: => B)(implicit trace: ZTraceElement): RIO[R, Either[Nothing, B]] =
    ZIO.right(b)

  /**
   * @see
   *   [[zio.ZIO.runtime]]
   */
  def runtime[R](implicit trace: ZTraceElement): URIO[R, Runtime[R]] =
    ZIO.runtime

  /**
   * @see
   *   See [[zio.ZIO.runtimeConfig]]
   */
  def runtimeConfig(implicit trace: ZTraceElement): UIO[RuntimeConfig] =
    ZIO.runtimeConfig

  /**
   * @see
   *   [[zio.ZIO.setState]]
   */
  def setState[S: Tag](s: => S)(implicit trace: ZTraceElement): ZIO[ZState[S], Nothing, Unit] =
    ZIO.setState(s)

  /**
   * @see
   *   See [[zio.ZIO.service]]
   */
  def service[A: Tag](implicit trace: ZTraceElement): URIO[A, A] =
    ZIO.service[A]

  /**
   * @see
   *   See [[zio.ZIO.serviceAt]]
   */
  def serviceAt[Service]: ZIO.ServiceAtPartiallyApplied[Service] =
    ZIO.serviceAt[Service]

  /**
   * @see
   *   See [[zio.ZIO.services[A,B]*]]
   */
  @deprecated("use service", "2.0.0")
  def services[A: Tag, B: Tag](implicit trace: ZTraceElement): URIO[A with B, (A, B)] =
    ZIO.services[A, B]

  /**
   * @see
   *   See [[zio.ZIO.services[A,B,C]*]]
   */
  @deprecated("use service", "2.0.0")
  def services[A: Tag, B: Tag, C: Tag](implicit trace: ZTraceElement): URIO[A with B with C, (A, B, C)] =
    ZIO.services[A, B, C]

  /**
   * @see
   *   See [[zio.ZIO.services[A,B,C,D]*]]
   */
  @deprecated("use service", "2.0.0")
  def services[A: Tag, B: Tag, C: Tag, D: Tag](implicit
    trace: ZTraceElement
  ): URIO[A with B with C with D, (A, B, C, D)] =
    ZIO.services[A, B, C, D]

  /**
   * @see
   *   See [[zio.ZIO.serviceWith]]
   */
  def serviceWith[Service]: ZIO.ServiceWithPartiallyApplied[Service] =
    new ZIO.ServiceWithPartiallyApplied[Service]

  /**
   * @see
   *   [[zio.ZIO.sleep]]
   */
  def sleep(duration: => Duration)(implicit trace: ZTraceElement): URIO[Clock, Unit] =
    ZIO.sleep(duration)

  /**
   * @see
   *   [[zio.ZIO.some]]
   */
  def some[R, A](a: => A)(implicit trace: ZTraceElement): URIO[R, Option[A]] =
    ZIO.some(a)

  /**
   * @see
   *   [[zio.ZIO.suspendSucceed]]
   */
  def suspendSucceed[R, A](rio: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.suspendSucceed(rio)

  /**
   * @see
   *   [[zio.ZIO.suspendSucceedWith]]
   */
  def suspendSucceedWith[R, A](f: (RuntimeConfig, FiberId) => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.suspendSucceedWith(f)

  /**
   * @see
   *   [[zio.ZIO.succeed]]
   */
  def succeed[A](effect: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.succeed(effect)

  /**
   * @see
   *   See [[zio.ZIO.succeedBlocking]]
   */
  def succeedBlocking[A](a: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.succeedBlocking(a)

  /**
   * @see
   *   [[zio.ZIO.trace]]
   */
  def trace(implicit trace: ZTraceElement): UIO[ZTrace] =
    ZIO.trace

  /**
   * @see
   *   [[zio.ZIO.unit]]
   */
  val unit: UIO[Unit] =
    ZIO.unit

  /**
   * @see
   *   [[zio.ZIO.uninterruptible]]
   */
  def uninterruptible[R, A](taskr: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.uninterruptible(taskr)

  /**
   * @see
   *   [[zio.ZIO.uninterruptibleMask]]
   */
  def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => URIO[R, A])(implicit
    trace: ZTraceElement
  ): URIO[R, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see
   *   See [[zio.ZIO.unless]]
   */
  def unless[R, A](b: => Boolean)(zio: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, Option[A]] =
    ZIO.unless(b)(zio)

  /**
   * @see
   *   See [[zio.ZIO.unlessM]]
   */
  @deprecated("use unlessZIO", "2.0.0")
  def unlessM[R](b: => URIO[R, Boolean]): ZIO.UnlessZIO[R, Nothing] =
    ZIO.unlessM(b)

  /**
   * @see
   *   See [[zio.ZIO.unlessZIO]]
   */
  def unlessZIO[R](b: => URIO[R, Boolean]): ZIO.UnlessZIO[R, Nothing] =
    ZIO.unlessZIO(b)

  /**
   * @see
   *   [[zio.ZIO.unsandbox]]
   */
  def unsandbox[R, A](v: => IO[Cause[Nothing], A])(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.unsandbox(v)

  /**
   * @see
   *   [[zio.ZIO.updateState]]
   */
  def updateState[S: Tag](f: S => S)(implicit trace: ZTraceElement): ZIO[ZState[S], Nothing, Unit] =
    ZIO.updateState(f)

  /**
   * @see
   *   [[zio.ZIO.when]]
   */
  def when[R, A](b: => Boolean)(rio: => URIO[R, A])(implicit trace: ZTraceElement): URIO[R, Option[A]] =
    ZIO.when(b)(rio)

  /**
   * @see
   *   [[zio.ZIO.whenCase]]
   */
  def whenCase[R, A, B](a: => A)(pf: PartialFunction[A, URIO[R, B]])(implicit
    trace: ZTraceElement
  ): URIO[R, Option[B]] =
    ZIO.whenCase(a)(pf)

  /**
   * @see
   *   [[zio.ZIO.whenCaseM]]
   */
  @deprecated("use whenCaseZIO", "2.0.0")
  def whenCaseM[R, A, B](a: => URIO[R, A])(pf: PartialFunction[A, URIO[R, B]])(implicit
    trace: ZTraceElement
  ): URIO[R, Option[B]] =
    ZIO.whenCaseM(a)(pf)

  /**
   * @see
   *   [[zio.ZIO.whenCaseZIO]]
   */
  def whenCaseZIO[R, A, B](a: => URIO[R, A])(pf: PartialFunction[A, URIO[R, B]])(implicit
    trace: ZTraceElement
  ): URIO[R, Option[B]] =
    ZIO.whenCaseZIO(a)(pf)

  /**
   * @see
   *   [[zio.ZIO.whenM]]
   */
  @deprecated("use whenZIO", "2.0.0")
  def whenM[R](b: => URIO[R, Boolean]): ZIO.WhenZIO[R, Nothing] =
    ZIO.whenM(b)

  /**
   * @see
   *   [[zio.ZIO.whenZIO]]
   */
  def whenZIO[R](b: => URIO[R, Boolean]): ZIO.WhenZIO[R, Nothing] =
    ZIO.whenZIO(b)

  /**
   * @see
   *   See [[zio.ZIO.withRuntimeConfig]]
   */
  def withRuntimeConfig[R, A](runtimeConfig: => RuntimeConfig)(urio: => URIO[R, A])(implicit
    trace: ZTraceElement
  ): URIO[R, A] =
    ZIO.withRuntimeConfig(runtimeConfig)(urio)

  /**
   * @see
   *   [[zio.ZIO.yieldNow]]
   */
  def yieldNow(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.yieldNow

  private[zio] def succeedNow[A](a: A): UIO[A] =
    ZIO.succeedNow(a)
}
