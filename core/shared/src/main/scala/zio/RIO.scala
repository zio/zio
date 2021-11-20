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

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object RIO {

  /**
   * @see
   *   See [[zio.ZIO.absolve]]
   */
  def absolve[R, A](v: => RIO[R, Either[Throwable, A]])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.absolve(v)

  /**
   * @see
   *   See [[zio.ZIO.allowInterrupt]]
   */
  def allowInterrupt(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.allowInterrupt

  /**
   * @see
   *   See [[zio.ZIO.apply]]
   */
  def apply[A](a: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.apply(a)

  /**
   * @see
   *   See [[zio.ZIO.access]]
   */
  @deprecated("use environmentWith", "2.0.0")
  def access[R]: ZIO.EnvironmentWithPartiallyApplied[R] =
    ZIO.access

  /**
   * @see
   *   See [[zio.ZIO.accessM]]
   */
  @deprecated("use environmentWithZIO", "2.0.0")
  def accessM[R]: ZIO.EnvironmentWithZIOPartiallyApplied[R] =
    ZIO.accessM

  /**
   * @see
   *   See [[zio.ZIO.accessZIO]]
   */
  @deprecated("use environmentWithZIO", "2.0.0")
  def accessZIO[R]: ZIO.EnvironmentWithZIOPartiallyApplied[R] =
    ZIO.accessZIO

  /**
   * @see
   *   See acquireReleaseWith [[zio.ZIO]]
   */
  def acquireReleaseWith[R, A](acquire: => RIO[R, A]): ZIO.Acquire[R, Throwable, A] =
    ZIO.acquireReleaseWith(acquire)

  /**
   * @see
   *   See acquireReleaseWith [[zio.ZIO]]
   */
  def acquireReleaseWith[R, A, B](
    acquire: => RIO[R, A],
    release: A => URIO[R, Any],
    use: A => RIO[R, B]
  )(implicit trace: ZTraceElement): RIO[R, B] =
    ZIO.acquireReleaseWith(acquire, release, use)

  /**
   * @see
   *   See acquireReleaseExitWith [[zio.ZIO]]
   */
  def acquireReleaseExitWith[R, A](acquire: => RIO[R, A]): ZIO.AcquireExit[R, Throwable, A] =
    ZIO.acquireReleaseExitWith(acquire)

  /**
   * @see
   *   See acquireReleaseExitWith [[zio.ZIO]]
   */
  def acquireReleaseExitWith[R, A, B](
    acquire: => RIO[R, A],
    release: (A, Exit[Throwable, B]) => URIO[R, Any],
    use: A => RIO[R, B]
  )(implicit trace: ZTraceElement): RIO[R, B] =
    ZIO.acquireReleaseExitWith(acquire, release, use)

  /**
   * @see
   *   See [[zio.ZIO.async]]
   */
  def async[R, A](register: (RIO[R, A] => Unit) => Any, blockingOn: => FiberId = FiberId.None)(implicit
    trace: ZTraceElement
  ): RIO[R, A] =
    ZIO.async(register, blockingOn)

  /**
   * @see
   *   See [[zio.ZIO.asyncMaybe]]
   */
  def asyncMaybe[R, A](
    register: (RIO[R, A] => Unit) => Option[RIO[R, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.asyncMaybe(register, blockingOn)

  /**
   * @see
   *   See [[zio.ZIO.asyncZIO]]
   */
  def asyncZIO[R, A](register: (RIO[R, A] => Unit) => RIO[R, Any])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.asyncZIO(register)

  /**
   * @see
   *   See [[zio.ZIO.asyncInterrupt]]
   */
  def asyncInterrupt[R, A](
    register: (RIO[R, A] => Unit) => Either[Canceler[R], RIO[R, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.asyncInterrupt(register, blockingOn)

  /**
   * @see
   *   See [[zio.ZIO.attempt]]
   */
  def attempt[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.attempt(effect)

  /**
   * @see
   *   See [[zio.ZIO.attemptBlocking]]
   */
  def attemptBlocking[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.attemptBlocking(effect)

  /**
   * @see
   *   See [[zio.ZIO.attemptBlockingCancelable]]
   */
  def attemptBlockingCancelable[A](effect: => A)(cancel: => UIO[Any])(implicit trace: ZTraceElement): Task[A] =
    ZIO.attemptBlockingCancelable(effect)(cancel)

  /**
   * @see
   *   See [[zio.ZIO.attemptBlockingInterrupt]]
   */
  def attemptBlockingInterrupt[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.attemptBlockingInterrupt(effect)

  /**
   * @see
   *   See [[zio.ZIO.blocking]]
   */
  def blocking[R, A](zio: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.blocking(zio)

  /**
   * @see
   *   See [[zio.ZIO.blockingExecutor]]
   */
  def blockingExecutor(implicit trace: ZTraceElement): UIO[Executor] =
    ZIO.blockingExecutor

  /**
   * @see
   *   See bracket [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseWith", "2.0.0")
  def bracket[R, A](acquire: => RIO[R, A]): ZIO.Acquire[R, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * @see
   *   See bracket [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseWith", "2.0.0")
  def bracket[R, A, B](
    acquire: => RIO[R, A],
    release: A => URIO[R, Any],
    use: A => RIO[R, B]
  )(implicit trace: ZTraceElement): RIO[R, B] = ZIO.bracket(acquire, release, use)

  /**
   * @see
   *   See bracketExit [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseExitWith", "2.0.0")
  def bracketExit[R, A](acquire: => RIO[R, A]): ZIO.AcquireExit[R, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see
   *   See bracketExit [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseExitWith", "2.0.0")
  def bracketExit[R, A, B](
    acquire: => RIO[R, A],
    release: (A, Exit[Throwable, B]) => URIO[R, Any],
    use: A => RIO[R, B]
  )(implicit trace: ZTraceElement): RIO[R, B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see
   *   See [[zio.ZIO.checkInterruptible]]
   */
  def checkInterruptible[R, A](f: InterruptStatus => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.checkInterruptible(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.collect[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collect[R, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, Option[Throwable], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.collect(in)(f)

  /**
   * @see
   *   See [[[zio.ZIO.collect[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def collect[R, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => ZIO[R, Option[Throwable], (Key2, Value2)])(implicit
    trace: ZTraceElement
  ): RIO[R, Map[Key2, Value2]] =
    ZIO.collect(map)(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.collectAll[R,E,A,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collectAll[R, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[RIO[R, A]]
  )(implicit bf: BuildFrom[Collection[RIO[R, A]], A, Collection[A]], trace: ZTraceElement): RIO[R, Collection[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[[zio.ZIO.collectAll[R,E,A](in:Set*]]]
   */
  def collectAll[R, A](in: Set[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Set[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[[zio.ZIO.collectAll[R,E,A](in:Array*]]]
   */
  def collectAll[R, A: ClassTag](in: Array[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Array[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[[zio.ZIO.collectAll[R,E,A](in:Option*]]]
   */
  def collectAll[R, A](in: Option[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Option[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[[zio.ZIO.collectAll[R,E,A](in:zio\.NonEmptyChunk*]]]
   */
  def collectAll[R, A](in: NonEmptyChunk[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, NonEmptyChunk[A]] =
    ZIO.collectAll(in)

  /**
   * @see
   *   See [[zio.ZIO.collectAll_]]
   */
  @deprecated("use collectAllDiscard", "2.0.0")
  def collectAll_[R, A](in: => Iterable[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.collectAll_(in)

  /**
   * @see
   *   See [[zio.ZIO.collectAllDiscard]]
   */
  def collectAllDiscard[R, A](in: => Iterable[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.collectAllDiscard(in)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.collectAllPar[R,E,A,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collectAllPar[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[RIO[R, A]]
  )(implicit bf: BuildFrom[Collection[RIO[R, A]], A, Collection[A]], trace: ZTraceElement): RIO[R, Collection[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see
   *   See [[[zio.ZIO.collectAllPar[R,E,A](as:Set*]]]
   */
  def collectAllPar[R, A](as: Set[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Set[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see
   *   See [[[zio.ZIO.collectAllPar[R,E,A](as:Array*]]]
   */
  def collectAllPar[R, A: ClassTag](as: Array[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Array[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see
   *   See [[[zio.ZIO.collectAllPar[R,E,A](as:zio\.NonEmptyChunk*]]]
   */
  def collectAllPar[R, A](as: NonEmptyChunk[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, NonEmptyChunk[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see
   *   See [[zio.ZIO.collectAllPar_]]
   */
  @deprecated("use collectAllParDiscard", "2.0.0")
  def collectAllPar_[R, A](in: => Iterable[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.collectAllPar_(in)

  /**
   * @see
   *   See [[zio.ZIO.collectAllParDiscard]]
   */
  def collectAllParDiscard[R, A](in: => Iterable[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.collectAllParDiscard(in)

  /**
   * @see
   *   See [[zio.ZIO.collectAllParN]]
   */
  @deprecated("use collectAllPar", "2.0.0")
  def collectAllParN[R, A, Collection[+Element] <: Iterable[Element]](
    n: => Int
  )(
    as: Collection[RIO[R, A]]
  )(implicit bf: BuildFrom[Collection[RIO[R, A]], A, Collection[A]], trace: ZTraceElement): RIO[R, Collection[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see
   *   See [[zio.ZIO.collectAllParNDiscard]]
   */
  @deprecated("use collectAllParDiscard", "2.0.0")
  def collectAllParN_[R, A](n: => Int)(as: => Iterable[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.collectAllParN_(n)(as)

  /**
   * @see
   *   See [[zio.ZIO.collectAllParN_]]
   */
  @deprecated("use collectAllParDiscard", "2.0.0")
  def collectAllParNDiscard[R, A](n: => Int)(as: => Iterable[RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.collectAllParNDiscard(n)(as)

  /**
   * @see
   *   See [[zio.ZIO.collectAllSuccesses]]
   */
  def collectAllSuccesses[R, A, Collection[+Element] <: Iterable[Element]](
    in: Collection[RIO[R, A]]
  )(implicit bf: BuildFrom[Collection[RIO[R, A]], A, Collection[A]], trace: ZTraceElement): RIO[R, Collection[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see
   *   See [[zio.ZIO.collectAllSuccessesPar]]
   */
  def collectAllSuccessesPar[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[RIO[R, A]]
  )(implicit bf: BuildFrom[Collection[RIO[R, A]], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see
   *   See [[zio.ZIO.collectAllSuccessesParN]]
   */
  @deprecated("use collectAllSuccessesPar", "2.0.0")
  def collectAllSuccessesParN[R, A, Collection[+Element] <: Iterable[Element]](n: => Int)(
    as: Collection[RIO[R, A]]
  )(implicit bf: BuildFrom[Collection[RIO[R, A]], A, Collection[A]], trace: ZTraceElement): URIO[R, Collection[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see
   *   See [[zio.ZIO.collectAllWith]]
   */
  def collectAllWith[R, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[RIO[R, A]]
  )(
    f: PartialFunction[A, B]
  )(implicit bf: BuildFrom[Collection[RIO[R, A]], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.collectAllWith(in)(f)

  /**
   * @see
   *   See [[zio.ZIO.collectAllWithPar]]
   */
  def collectAllWithPar[R, A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[RIO[R, A]]
  )(
    f: PartialFunction[A, B]
  )(implicit bf: BuildFrom[Collection[RIO[R, A]], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.collectAllWithParN]]
   */
  @deprecated("use collectAllWithPar", "2.0.0")
  def collectAllWithParN[R, A, B, Collection[+Element] <: Iterable[Element]](n: => Int)(
    as: Collection[RIO[R, A]]
  )(
    f: PartialFunction[A, B]
  )(implicit bf: BuildFrom[Collection[RIO[R, A]], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.collectFirst]]
   */
  def collectFirst[R, A, B](as: => Iterable[A])(f: A => RIO[R, Option[B]])(implicit
    trace: ZTraceElement
  ): RIO[R, Option[B]] =
    ZIO.collectFirst(as)(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.collectPar[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collectPar[R, A, B, Collection[+Element] <: Iterable[Element]](in: Collection[A])(
    f: A => ZIO[R, Option[Throwable], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.collectPar(in)(f)

  /**
   * @see
   *   See [[[zio.ZIO.collectPar[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def collectPar[R, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => ZIO[R, Option[Throwable], (Key2, Value2)])(implicit
    trace: ZTraceElement
  ): RIO[R, Map[Key2, Value2]] =
    ZIO.collectPar(map)(f)

  /**
   * @see
   *   See [[zio.ZIO.collectParN]]
   */
  @deprecated("use collectPar", "2.0.0")
  def collectParN[R, A, B, Collection[+Element] <: Iterable[Element]](n: => Int)(in: Collection[A])(
    f: A => ZIO[R, Option[Throwable], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.collectParN(n)(in)(f)

  /**
   * @see
   *   See [[zio.ZIO.cond]]
   */
  def cond[A](predicate: => Boolean, result: => A, error: => Throwable)(implicit trace: ZTraceElement): Task[A] =
    ZIO.cond(predicate, result, error)

  /**
   * @see
   *   See [[zio.ZIO.debug]]
   */
  def debug(value: => Any)(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.debug(value)

  /**
   * @see
   *   See [[zio.ZIO.descriptor]]
   */
  def descriptor(implicit trace: ZTraceElement): UIO[Fiber.Descriptor] =
    ZIO.descriptor

  /**
   * @see
   *   See [[zio.ZIO.descriptorWith]]
   */
  def descriptorWith[R, A](f: Fiber.Descriptor => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.descriptorWith(f)

  /**
   * @see
   *   See [[zio.ZIO.die]]
   */
  def die(t: => Throwable)(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.die(t)

  /**
   * @see
   *   See [[zio.ZIO.dieMessage]]
   */
  def dieMessage(message: => String)(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.dieMessage(message)

  /**
   * @see
   *   See [[zio.ZIO.done]]
   */
  def done[A](r: => Exit[Throwable, A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.done(r)

  /**
   * @see
   *   See [[zio.ZIO.effect]]
   */
  @deprecated("use attempt", "2.0.0")
  def effect[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.effect(effect)

  /**
   * @see
   *   See [[zio.ZIO.effectAsync]]
   */
  @deprecated("use async", "2.0.0")
  def effectAsync[R, A](register: (RIO[R, A] => Unit) => Any, blockingOn: => FiberId = FiberId.None)(implicit
    trace: ZTraceElement
  ): RIO[R, A] =
    ZIO.effectAsync(register, blockingOn)

  /**
   * @see
   *   See [[zio.ZIO.effectAsyncMaybe]]
   */
  @deprecated("use asyncMaybe", "2.0.0")
  def effectAsyncMaybe[R, A](
    register: (RIO[R, A] => Unit) => Option[RIO[R, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.effectAsyncMaybe(register, blockingOn)

  /**
   * @see
   *   See [[zio.ZIO.effectAsyncM]]
   */
  @deprecated("use asyncZIO", "2.0.0")
  def effectAsyncM[R, A](register: (RIO[R, A] => Unit) => RIO[R, Any])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.effectAsyncM(register)

  /**
   * @see
   *   See [[zio.ZIO.effectAsyncInterrupt]]
   */
  @deprecated("use asyncInterrupt", "2.0.0")
  def effectAsyncInterrupt[R, A](
    register: (RIO[R, A] => Unit) => Either[Canceler[R], RIO[R, A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.effectAsyncInterrupt(register, blockingOn)

  /**
   * @see
   *   See [[zio.ZIO.effectBlocking]]
   */
  @deprecated("use attemptBlocking", "2.0.0")
  def effectBlocking[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectBlocking(effect)

  /**
   * @see
   *   See [[zio.ZIO.effectBlockingCancelable]]
   */
  @deprecated("use attemptBlockingCancelable", "2.0.0")
  def effectBlockingCancelable[A](effect: => A)(cancel: => UIO[Any])(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectBlockingCancelable(effect)(cancel)

  /**
   * @see
   *   See [[zio.ZIO.effectBlockingInterrupt]]
   */
  @deprecated("use attemptBlockingInterrupt", "2.0.0")
  def effectBlockingInterrupt[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectBlockingInterrupt(effect)

  /**
   * Returns a lazily constructed effect, whose construction may itself require
   * effects. When no environment is required (i.e., when R == Any) it is
   * conceptually equivalent to `flatten(effect(io))`.
   */
  @deprecated("use suspend", "2.0.0")
  def effectSuspend[R, A](rio: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.effectSuspend(rio)

  /**
   * @see
   *   See [[zio.ZIO.effectSuspendTotal]]
   */
  @deprecated("use suspendSucceed", "2.0.0")
  def effectSuspendTotal[R, A](rio: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.effectSuspendTotal(rio)

  /**
   * @see
   *   See [[zio.ZIO.effectSuspendTotalWith]]
   */
  @deprecated("use suspendSucceedWith", "2.0.0")
  def effectSuspendTotalWith[R, A](f: (Platform, Fiber.Id) => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.effectSuspendTotalWith(f)

  /**
   * @see
   *   See [[zio.ZIO.effectSuspendWith]]
   */
  @deprecated("use suspendWith", "2.0.0")
  def effectSuspendWith[R, A](f: (Platform, Fiber.Id) => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.effectSuspendWith(f)

  /**
   * @see
   *   See [[zio.ZIO.effectTotal]]
   */
  @deprecated("use succeed", "2.0.0")
  def effectTotal[A](effect: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.effectTotal(effect)

  /**
   * @see
   *   See [[zio.ZIO.environment]]
   */
  def environment[R](implicit trace: ZTraceElement): URIO[R, ZEnvironment[R]] =
    ZIO.environment

  /**
   * @see
   *   See [[zio.ZIO.environmentWith]]
   */
  def environmentWith[R]: ZIO.EnvironmentWithPartiallyApplied[R] =
    ZIO.environmentWith

  /**
   * @see
   *   See [[zio.ZIO.environmentWithZIO]]
   */
  def environmentWithZIO[R]: ZIO.EnvironmentWithZIOPartiallyApplied[R] =
    ZIO.environmentWithZIO

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
  def exists[R, A](as: => Iterable[A])(f: A => RIO[R, Boolean])(implicit trace: ZTraceElement): RIO[R, Boolean] =
    ZIO.exists(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.fail]]
   */
  def fail(error: => Throwable)(implicit trace: ZTraceElement): Task[Nothing] =
    ZIO.fail(error)

  /**
   * @see
   *   See [[zio.ZIO.failCause]]
   */
  def failCause(cause: => Cause[Throwable])(implicit trace: ZTraceElement): Task[Nothing] =
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
    f: A => RIO[R, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): RIO[R, Collection[A]] =
    ZIO.filter(as)(f)

  /**
   * @see
   *   [[[zio.ZIO.filter[R,E,A](as:Set*]]]
   */
  def filter[R, A](as: Set[A])(f: A => RIO[R, Boolean])(implicit trace: ZTraceElement): RIO[R, Set[A]] =
    ZIO.filter(as)(f)

  /**
   * @see
   *   [[zio.ZIO.filterPar[R,E,A,Collection*]]
   */
  def filterPar[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => RIO[R, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): RIO[R, Collection[A]] =
    ZIO.filterPar(as)(f)

  /**
   * @see
   *   [[[zio.ZIO.filterPar[R,E,A](as:Set*]]]
   */
  def filterPar[R, A](as: Set[A])(f: A => RIO[R, Boolean])(implicit trace: ZTraceElement): RIO[R, Set[A]] =
    ZIO.filterPar(as)(f)

  /**
   * @see
   *   [[zio.ZIO.filterNot[R,E,A,Collection*]]
   */
  def filterNot[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => RIO[R, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): RIO[R, Collection[A]] =
    ZIO.filterNot(as)(f)

  /**
   * @see
   *   [[[zio.ZIO.filterNot[R,E,A](as:Set*]]]
   */
  def filterNot[R, A](as: Set[A])(f: A => RIO[R, Boolean])(implicit trace: ZTraceElement): RIO[R, Set[A]] =
    ZIO.filterNot(as)(f)

  /**
   * @see
   *   [[zio.ZIO.filterNotPar[R,E,A,Collection*]]
   */
  def filterNotPar[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => RIO[R, Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): RIO[R, Collection[A]] =
    ZIO.filterNotPar(as)(f)

  /**
   * @see
   *   [[[zio.ZIO.filterNotPar[R,E,A](as:Set*]]]
   */
  def filterNotPar[R, A](as: Set[A])(f: A => RIO[R, Boolean])(implicit trace: ZTraceElement): RIO[R, Set[A]] =
    ZIO.filterNotPar(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.firstSuccessOf]]
   */
  def firstSuccessOf[R, A](
    rio: => RIO[R, A],
    rest: => Iterable[RIO[R, A]]
  )(implicit trace: ZTraceElement): RIO[R, A] = ZIO.firstSuccessOf(rio, rest)

  /**
   * @see
   *   See [[zio.ZIO.flatten]]
   */
  def flatten[R, A](taskr: => RIO[R, RIO[R, A]])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.flatten(taskr)

  /**
   * @see
   *   See [[zio.ZIO.foldLeft]]
   */
  def foldLeft[R, S, A](in: => Iterable[A])(zero: => S)(f: (S, A) => RIO[R, S])(implicit
    trace: ZTraceElement
  ): RIO[R, S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see
   *   See [[zio.ZIO.foldRight]]
   */
  def foldRight[R, S, A](in: => Iterable[A])(zero: => S)(f: (A, S) => RIO[R, S])(implicit
    trace: ZTraceElement
  ): RIO[R, S] =
    ZIO.foldRight(in)(zero)(f)

  /**
   * @see
   *   See [[zio.ZIO.forall]]
   */
  def forall[R, A](as: => Iterable[A])(f: A => RIO[R, Boolean])(implicit trace: ZTraceElement): RIO[R, Boolean] =
    ZIO.forall(as)(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.foreach[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def foreach[R, A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(
    f: A => RIO[R, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   See [[[zio.ZIO.foreach[R,E,A,B](in:Set*]]]
   */
  def foreach[R, A, B](in: Set[A])(f: A => RIO[R, B])(implicit trace: ZTraceElement): RIO[R, Set[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   See [[[zio.ZIO.foreach[R,E,A,B](in:Array*]]]
   */
  def foreach[R, A, B: ClassTag](in: Array[A])(f: A => RIO[R, B])(implicit trace: ZTraceElement): RIO[R, Array[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   See [[[zio.ZIO.foreach[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def foreach[R, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => RIO[R, (Key2, Value2)])(implicit trace: ZTraceElement): RIO[R, Map[Key2, Value2]] =
    ZIO.foreach(map)(f)

  /**
   * @see
   *   See [[[zio.ZIO.foreach[R,E,A,B](in:Option*]]]
   */
  def foreach[R, A, B](in: Option[A])(f: A => RIO[R, B])(implicit trace: ZTraceElement): RIO[R, Option[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   See [[[zio.ZIO.foreach[R,E,A,B](in:zio\.NonEmptyChunk*]]]
   */
  def foreach[R, A, B](in: NonEmptyChunk[A])(f: A => RIO[R, B])(implicit
    trace: ZTraceElement
  ): RIO[R, NonEmptyChunk[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see
   *   See [[zio.ZIO.foreachExec]]
   */
  final def foreachExec[R, A, B, Collection[+Element] <: Iterable[Element]](as: Collection[A])(
    exec: => ExecutionStrategy
  )(
    f: A => RIO[R, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.foreachExec(as)(exec)(f)

  /**
   * @see
   *   See
   *   [[[zio.ZIO.foreachPar[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def foreachPar[R, A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    fn: A => RIO[R, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see
   *   See [[[zio.ZIO.foreachPar[R,E,A,B](as:Set*]]]
   */
  def foreachPar[R, A, B](as: Set[A])(fn: A => RIO[R, B])(implicit trace: ZTraceElement): RIO[R, Set[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see
   *   See [[[zio.ZIO.foreachPar[R,E,A,B](as:Array*]]]
   */
  def foreachPar[R, A, B: ClassTag](as: Array[A])(fn: A => RIO[R, B])(implicit trace: ZTraceElement): RIO[R, Array[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see
   *   See [[[zio.ZIO.foreachPar[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def foreachPar[R, Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => RIO[R, (Key2, Value2)])(implicit trace: ZTraceElement): RIO[R, Map[Key2, Value2]] =
    ZIO.foreachPar(map)(f)

  /**
   * @see
   *   See [[[zio.ZIO.foreachPar[R,E,A,B](as:zio\.NonEmptyChunk*]]]
   */
  def foreachPar[R, A, B](as: NonEmptyChunk[A])(fn: A => RIO[R, B])(implicit
    trace: ZTraceElement
  ): RIO[R, NonEmptyChunk[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see
   *   See [[zio.ZIO.foreachParN]]
   */
  @deprecated("use foreachPar", "2.0.0")
  def foreachParN[R, A, B, Collection[+Element] <: Iterable[Element]](n: => Int)(
    as: Collection[A]
  )(
    fn: A => RIO[R, B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): RIO[R, Collection[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see
   *   See [[zio.ZIO.foreach_]]
   */
  @deprecated("use foreachDiscard", "2.0.0")
  def foreach_[R, A](as: => Iterable[A])(f: A => RIO[R, Any])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.foreachDiscard]]
   */
  def foreachDiscard[R, A](as: => Iterable[A])(f: A => RIO[R, Any])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.foreachDiscard(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.foreachPar_]]
   */
  @deprecated("use foreachParDiscard", "2.0.0")
  def foreachPar_[R, A, B](as: => Iterable[A])(f: A => RIO[R, Any])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.foreachParDiscard]]
   */
  def foreachParDiscard[R, A, B](as: => Iterable[A])(f: A => RIO[R, Any])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.foreachParDiscard(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.foreachParN_]]
   */
  @deprecated("use foreachParDiscard", "2.0.0")
  def foreachParN_[R, A, B](n: => Int)(as: => Iterable[A])(f: A => RIO[R, Any])(implicit
    trace: ZTraceElement
  ): RIO[R, Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.foreachParNDiscard]]
   */
  @deprecated("use foreachParDiscard", "2.0.0")
  def foreachParNDiscard[R, A, B](n: => Int)(as: => Iterable[A])(f: A => RIO[R, Any])(implicit
    trace: ZTraceElement
  ): RIO[R, Unit] =
    ZIO.foreachParNDiscard(n)(as)(f)

  /**
   * @see
   *   See [[zio.ZIO.forkAll]]
   */
  def forkAll[R, A, Collection[+Element] <: Iterable[Element]](
    as: Collection[RIO[R, A]]
  )(implicit
    bf: BuildFrom[Collection[RIO[R, A]], A, Collection[A]],
    trace: ZTraceElement
  ): URIO[R, Fiber[Throwable, Collection[A]]] =
    ZIO.forkAll(as)

  /**
   * @see
   *   See [[zio.ZIO.forkAll_]]
   */
  @deprecated("use forkAllDiscard", "2.0.0")
  def forkAll_[R, A](as: => Iterable[RIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.forkAll_(as)

  /**
   * @see
   *   See [[zio.ZIO.forkAllDiscard]]
   */
  def forkAllDiscard[R, A](as: => Iterable[RIO[R, A]])(implicit trace: ZTraceElement): URIO[R, Unit] =
    ZIO.forkAllDiscard(as)

  /**
   * Constructs a `RIO` value of the appropriate type for the specified input.
   */
  def from[Input](input: => Input)(implicit
    constructor: ZIO.ZIOConstructor[Nothing, Throwable, Input],
    trace: ZTraceElement
  ): ZIO[constructor.OutEnvironment, constructor.OutError, constructor.OutSuccess] =
    constructor.make(input)

  /**
   * @see
   *   See [[zio.ZIO.fromEither]]
   */
  def fromEither[A](v: => Either[Throwable, A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromEither(v)

  /**
   * @see
   *   See [[zio.ZIO.fromFiber]]
   */
  def fromFiber[A](fiber: => Fiber[Throwable, A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see
   *   See [[zio.ZIO.fromFiberM]]
   */
  @deprecated("use fromFiberZIO", "2.0.0")
  def fromFiberM[A](fiber: => Task[Fiber[Throwable, A]])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see
   *   See [[zio.ZIO.fromFiberZIO]]
   */
  def fromFiberZIO[A](fiber: => Task[Fiber[Throwable, A]])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromFiberZIO(fiber)

  /**
   * @see
   *   See [[zio.ZIO.fromFunction]]
   */
  @deprecated("use access", "2.0.0")
  def fromFunction[R, A](f: ZEnvironment[R] => A)(implicit trace: ZTraceElement): URIO[R, A] =
    ZIO.fromFunction(f)

  /**
   * @see
   *   See [[zio.ZIO.fromFunctionM]]
   */
  @deprecated("use environmentWithZIO", "2.0.0")
  def fromFunctionM[R, A](f: ZEnvironment[R] => Task[A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.fromFunctionM(f)

  /**
   * @see
   *   See [[zio.ZIO.fromFuture]]
   */
  def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromFuture(make)

  /**
   * @see
   *   See [[zio.ZIO.fromFutureInterrupt]]
   */
  def fromFutureInterrupt[A](make: ExecutionContext => scala.concurrent.Future[A])(implicit
    trace: ZTraceElement
  ): Task[A] =
    ZIO.fromFutureInterrupt(make)

  /**
   * @see
   *   See [[zio.ZIO.fromTry]]
   */
  def fromTry[A](value: => scala.util.Try[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromTry(value)

  /**
   * @see
   *   See [[zio.ZIO.getOrFail]]
   */
  def getOrFail[A](v: => Option[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.getOrFail(v)

  /**
   * @see
   *   See [[zio.ZIO.getState]]
   */
  def getState[S: Tag](implicit trace: ZTraceElement): ZIO[ZState[S], Nothing, S] =
    ZIO.getState

  /**
   * @see
   *   See [[zio.ZIO.getStateWith]]
   */
  def getStateWith[S]: ZIO.GetStateWithPartiallyApplied[S] =
    ZIO.getStateWith

  /**
   * @see
   *   See [[zio.ZIO.halt]]
   */
  @deprecated("use failCause", "2.0.0")
  def halt(cause: => Cause[Throwable])(implicit trace: ZTraceElement): Task[Nothing] =
    ZIO.halt(cause)

  /**
   * @see
   *   [[zio.ZIO.ifM]]
   */
  @deprecated("use ifZIO", "2.0.0")
  def ifM[R](b: => RIO[R, Boolean]): ZIO.IfZIO[R, Throwable] =
    ZIO.ifM(b)

  /**
   * @see
   *   [[zio.ZIO.ifZIO]]
   */
  def ifZIO[R](b: => RIO[R, Boolean]): ZIO.IfZIO[R, Throwable] =
    ZIO.ifZIO(b)

  /**
   * @see
   *   [[zio.ZIO.infinity]]
   */
  def infinity(implicit trace: ZTraceElement): URIO[Clock, Nothing] =
    ZIO.infinity

  /**
   * @see
   *   See [[zio.ZIO.interrupt]]
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
   *   See [[zio.ZIO.interruptible]]
   */
  def interruptible[R, A](taskr: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.interruptible(taskr)

  /**
   * @see
   *   See [[zio.ZIO.interruptibleMask]]
   */
  def interruptibleMask[R, A](k: ZIO.InterruptStatusRestore => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.interruptibleMask(k)

  /**
   * @see
   *   See [[zio.ZIO.iterate]]
   */
  def iterate[R, S](initial: => S)(cont: S => Boolean)(body: S => RIO[R, S])(implicit trace: ZTraceElement): RIO[R, S] =
    ZIO.iterate(initial)(cont)(body)

  /**
   * @see
   *   See [[zio.ZIO.lock]]
   */
  @deprecated("use onExecutor", "2.0.0")
  def lock[R, A](executor: => Executor)(taskr: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.onExecutor(executor)(taskr)

  /**
   * @see
   *   See [[zio.ZIO.loop]]
   */
  def loop[R, A, S](initial: => S)(cont: S => Boolean, inc: S => S)(body: S => RIO[R, A])(implicit
    trace: ZTraceElement
  ): RIO[R, List[A]] =
    ZIO.loop(initial)(cont, inc)(body)

  /**
   * @see
   *   See [[zio.ZIO.loop_]]
   */
  @deprecated("use loopDiscard", "2.0.0")
  def loop_[R, S](initial: => S)(cont: S => Boolean, inc: S => S)(body: S => RIO[R, Any])(implicit
    trace: ZTraceElement
  ): RIO[R, Unit] =
    ZIO.loop_(initial)(cont, inc)(body)

  /**
   * @see
   *   See [[zio.ZIO.loopDiscard]]
   */
  def loopDiscard[R, S](initial: => S)(cont: S => Boolean, inc: S => S)(body: S => RIO[R, Any])(implicit
    trace: ZTraceElement
  ): RIO[R, Unit] =
    ZIO.loopDiscard(initial)(cont, inc)(body)

  /**
   * @see
   *   See [[zio.ZIO.left]]
   */
  def left[R, A](a: => A)(implicit trace: ZTraceElement): RIO[R, Either[A, Nothing]] =
    ZIO.left(a)

  /**
   * @see
   *   [[zio.ZIO.mapN[R,E,A,B,C]*]]
   */
  @deprecated("use zip", "2.0.0")
  def mapN[R, A, B, C](rio1: => RIO[R, A], rio2: => RIO[R, B])(f: (A, B) => C)(implicit
    trace: ZTraceElement
  ): RIO[R, C] =
    ZIO.mapN(rio1, rio2)(f)

  /**
   * @see
   *   [[zio.ZIO.mapN[R,E,A,B,C,D]*]]
   */
  @deprecated("use zip", "2.0.0")
  def mapN[R, A, B, C, D](rio1: => RIO[R, A], rio2: => RIO[R, B], rio3: => RIO[R, C])(
    f: (A, B, C) => D
  )(implicit trace: ZTraceElement): RIO[R, D] =
    ZIO.mapN(rio1, rio2, rio3)(f)

  /**
   * @see
   *   [[zio.ZIO.mapN[R,E,A,B,C,D,F]*]]
   */
  @deprecated("use zip", "2.0.0")
  def mapN[R, A, B, C, D, F](rio1: => RIO[R, A], rio2: => RIO[R, B], rio3: => RIO[R, C], rio4: => RIO[R, D])(
    f: (A, B, C, D) => F
  )(implicit trace: ZTraceElement): RIO[R, F] =
    ZIO.mapN(rio1, rio2, rio3, rio4)(f)

  /**
   * @see
   *   [[zio.ZIO.mapParN[R,E,A,B,C]*]]
   */
  @deprecated("use zipPar", "2.0.0")
  def mapParN[R, A, B, C](rio1: => RIO[R, A], rio2: => RIO[R, B])(f: (A, B) => C)(implicit
    trace: ZTraceElement
  ): RIO[R, C] =
    ZIO.mapParN(rio1, rio2)(f)

  /**
   * @see
   *   [[zio.ZIO.mapParN[R,E,A,B,C,D]*]]
   */
  @deprecated("use zipPar", "2.0.0")
  def mapParN[R, A, B, C, D](rio1: => RIO[R, A], rio2: => RIO[R, B], rio3: => RIO[R, C])(f: (A, B, C) => D)(implicit
    trace: ZTraceElement
  ): RIO[R, D] =
    ZIO.mapParN(rio1, rio2, rio3)(f)

  /**
   * @see
   *   [[zio.ZIO.mapParN[R,E,A,B,C,D,F]*]]
   */
  @deprecated("use zipPar", "2.0.0")
  def mapParN[R, A, B, C, D, F](rio1: => RIO[R, A], rio2: => RIO[R, B], rio3: => RIO[R, C], rio4: => RIO[R, D])(
    f: (A, B, C, D) => F
  )(implicit trace: ZTraceElement): RIO[R, F] =
    ZIO.mapParN(rio1, rio2, rio3, rio4)(f)

  /**
   * @see
   *   See [[zio.ZIO.memoize]]
   */
  def memoize[R, A, B](f: A => RIO[R, B])(implicit trace: ZTraceElement): UIO[A => RIO[R, B]] =
    ZIO.memoize(f)

  /**
   * @see
   *   See [[zio.ZIO.mergeAll]]
   */
  def mergeAll[R, A, B](in: => Iterable[RIO[R, A]])(zero: => B)(f: (B, A) => B)(implicit
    trace: ZTraceElement
  ): RIO[R, B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see
   *   See [[zio.ZIO.mergeAllPar]]
   */
  def mergeAllPar[R, A, B](in: => Iterable[RIO[R, A]])(zero: => B)(f: (B, A) => B)(implicit
    trace: ZTraceElement
  ): RIO[R, B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see
   *   See [[zio.ZIO.never]]
   */
  def never(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.never

  /**
   * @see
   *   See [[zio.ZIO.none]]
   */
  val none: UIO[Option[Nothing]] =
    ZIO.none

  /**
   * @see
   *   See [[zio.ZIO.noneOrFail]]
   */
  def noneOrFail(o: => Option[Throwable])(implicit trace: ZTraceElement): RIO[Nothing, Unit] =
    ZIO.noneOrFail(o)

  /**
   * @see
   *   See [[zio.ZIO.noneOrFailWith]]
   */
  def noneOrFailWith[O](o: => Option[O])(f: O => Throwable)(implicit trace: ZTraceElement): RIO[Nothing, Unit] =
    ZIO.noneOrFailWith(o)(f)

  /**
   * @see
   *   See [[zio.ZIO.not]]
   */
  def not[R](effect: => RIO[R, Boolean])(implicit trace: ZTraceElement): RIO[R, Boolean] =
    ZIO.not(effect)

  /**
   * @see
   *   See [[zio.ZIO.onExecutor]]
   */
  def onExecutor[R, A](executor: => Executor)(taskr: RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.onExecutor(executor)(taskr)

  /**
   * @see
   *   See [[zio.ZIO.partition]]
   */
  def partition[R, A, B](in: => Iterable[A])(f: A => RIO[R, B])(implicit
    trace: ZTraceElement
  ): RIO[R, (Iterable[Throwable], Iterable[B])] =
    ZIO.partition(in)(f)

  /**
   * @see
   *   See [[zio.ZIO.partitionPar]]
   */
  def partitionPar[R, A, B](in: => Iterable[A])(f: A => RIO[R, B])(implicit
    trace: ZTraceElement
  ): RIO[R, (Iterable[Throwable], Iterable[B])] =
    ZIO.partitionPar(in)(f)

  /**
   * @see
   *   See [[zio.ZIO.partitionParN]]
   */
  @deprecated("use partitionPar", "2.0.0")
  def partitionParN[R, A, B](n: => Int)(in: => Iterable[A])(
    f: A => RIO[R, B]
  )(implicit trace: ZTraceElement): RIO[R, (Iterable[Throwable], Iterable[B])] =
    ZIO.partitionParN(n)(in)(f)

  /**
   * @see
   *   See [[zio.ZIO.manuallyProvide]]
   */
  def provideEnvironment[R, A](r: => ZEnvironment[R])(implicit trace: ZTraceElement): RIO[R, A] => Task[A] =
    ZIO.provideEnvironment(r)

  /**
   * @see
   *   See [[zio.ZIO.raceAll]]
   */
  def raceAll[R, R1 <: R, A](taskr: => RIO[R, A], taskrs: => Iterable[RIO[R1, A]])(implicit
    trace: ZTraceElement
  ): RIO[R1, A] =
    ZIO.raceAll(taskr, taskrs)

  /**
   * @see
   *   See [[zio.ZIO.reduceAll]]
   */
  def reduceAll[R, R1 <: R, A](a: => RIO[R, A], as: => Iterable[RIO[R1, A]])(f: (A, A) => A)(implicit
    trace: ZTraceElement
  ): RIO[R1, A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see
   *   See [[zio.ZIO.reduceAllPar]]
   */
  def reduceAllPar[R, R1 <: R, A](a: => RIO[R, A], as: => Iterable[RIO[R1, A]])(f: (A, A) => A)(implicit
    trace: ZTraceElement
  ): RIO[R1, A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see
   *   See [[zio.ZIO.replicate]]
   */
  def replicate[R, A](n: => Int)(effect: => RIO[R, A])(implicit trace: ZTraceElement): Iterable[RIO[R, A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see
   *   See [[zio.ZIO.replicateM]]
   */
  @deprecated("use replicateZIO", "2.0.0")
  def replicateM[R, A](n: => Int)(effect: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, Iterable[A]] =
    ZIO.replicateM(n)(effect)

  /**
   * @see
   *   See [[zio.ZIO.replicateM_]]
   */
  @deprecated("use replicateZIODiscard", "2.0.0")
  def replicateM_[R, A](n: => Int)(effect: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.replicateM_(n)(effect)

  /**
   * @see
   *   See [[zio.ZIO.replicateZIO]]
   */
  def replicateZIO[R, A](n: => Int)(effect: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, Iterable[A]] =
    ZIO.replicateZIO(n)(effect)

  /**
   * @see
   *   See [[zio.ZIO.replicateZIODiscard]]
   */
  def replicateZIODiscard[R, A](n: => Int)(effect: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, Unit] =
    ZIO.replicateZIODiscard(n)(effect)

  /**
   * @see
   *   See [[zio.ZIO.require]]
   */
  @deprecated("use someOrFail", "2.0.0")
  def require[A](error: => Throwable)(implicit trace: ZTraceElement): IO[Throwable, Option[A]] => IO[Throwable, A] =
    ZIO.require[Any, Throwable, A](error)

  /**
   * @see
   *   See [[zio.ZIO.reserve]]
   */
  def reserve[R, A, B](reservation: => RIO[R, Reservation[R, Throwable, A]])(use: A => RIO[R, B])(implicit
    trace: ZTraceElement
  ): RIO[R, B] =
    ZIO.reserve(reservation)(use)

  /**
   * @see
   *   [[zio.ZIO.right]]
   */
  def right[R, B](b: => B)(implicit trace: ZTraceElement): RIO[R, Either[Nothing, B]] =
    ZIO.right(b)

  /**
   * @see
   *   See [[zio.ZIO.runtime]]
   */
  def runtime[R](implicit trace: ZTraceElement): ZIO[R, Nothing, Runtime[R]] =
    ZIO.runtime

  /**
   * @see
   *   See [[zio.ZIO.runtimeConfig]]
   */
  def runtimeConfig(implicit trace: ZTraceElement): UIO[RuntimeConfig] =
    ZIO.runtimeConfig

  /**
   * @see
   *   See [[zio.ZIO.setState]]
   */
  def setState[S: Tag](s: => S)(implicit trace: ZTraceElement): ZIO[ZState[S], Nothing, Unit] =
    ZIO.setState(s)

  /**
   * @see
   *   See [[zio.ZIO.service]]
   */
  def service[A: Tag: IsNotIntersection](implicit trace: ZTraceElement): URIO[A, A] =
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
  def services[A: Tag: IsNotIntersection, B: Tag: IsNotIntersection](implicit
    trace: ZTraceElement
  ): URIO[A with B, (A, B)] =
    ZIO.services[A, B]

  /**
   * @see
   *   See [[zio.ZIO.services[A,B,C]*]]
   */
  @deprecated("use service", "2.0.0")
  def services[A: Tag: IsNotIntersection, B: Tag: IsNotIntersection, C: Tag: IsNotIntersection](implicit
    trace: ZTraceElement
  ): URIO[A with B with C, (A, B, C)] =
    ZIO.services[A, B, C]

  /**
   * @see
   *   See [[zio.ZIO.services[A,B,C,D]*]]
   */
  @deprecated("use service", "2.0.0")
  def services[
    A: Tag: IsNotIntersection,
    B: Tag: IsNotIntersection,
    C: Tag: IsNotIntersection,
    D: Tag: IsNotIntersection
  ](implicit
    trace: ZTraceElement
  ): URIO[A with B with C with D, (A, B, C, D)] =
    ZIO.services[A, B, C, D]

  /**
   * @see
   *   See [[zio.ZIO.serviceWith]]
   */
  def serviceWith[Service]: ZIO.ServiceWithPartiallyApplied[Service] =
    ZIO.serviceWith[Service]

  /**
   * @see
   *   See [[zio.ZIO.serviceWithZIO]]
   */
  def serviceWithZIO[Service]: ZIO.ServiceWithZIOPartiallyApplied[Service] =
    new ZIO.ServiceWithZIOPartiallyApplied[Service]

  /**
   * @see
   *   See [[zio.ZIO.sleep]]
   */
  def sleep(duration: => Duration)(implicit trace: ZTraceElement): RIO[Clock, Unit] =
    ZIO.sleep(duration)

  /**
   * @see
   *   [[zio.ZIO.some]]
   */
  def some[R, A](a: => A)(implicit trace: ZTraceElement): RIO[R, Option[A]] =
    ZIO.some(a)

  /**
   * @see
   *   See [[zio.ZIO.succeed]]
   */
  def succeed[A](a: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.succeed(a)

  /**
   * @see
   *   See [[zio.ZIO.succeedBlocking]]
   */
  def succeedBlocking[A](a: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.succeedBlocking(a)

  /**
   * @see
   *   See [[zio.ZIO.suspend]]
   */
  def suspend[R, A](rio: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.suspend(rio)

  /**
   * @see
   *   See [[zio.ZIO.suspendSucceed]]
   */
  def suspendSucceed[R, A](rio: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.suspendSucceed(rio)

  /**
   * @see
   *   See [[zio.ZIO.suspendSucceedWith]]
   */
  def suspendSucceedWith[R, A](p: (RuntimeConfig, FiberId) => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.suspendSucceedWith(p)

  /**
   * @see
   *   See [[zio.ZIO.suspendWith]]
   */
  def suspendWith[R, A](p: (RuntimeConfig, FiberId) => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.suspendWith(p)

  /**
   * @see
   *   See [[zio.ZIO.trace]]
   */
  def trace(implicit trace: ZTraceElement): UIO[ZTrace] =
    ZIO.trace

  /**
   * @see
   *   See [[zio.ZIO.unit]]
   */
  val unit: UIO[Unit] =
    ZIO.unit

  /**
   * @see
   *   See [[zio.ZIO.uninterruptible]]
   */
  def uninterruptible[R, A](taskr: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.uninterruptible(taskr)

  /**
   * @see
   *   See [[zio.ZIO.uninterruptibleMask]]
   */
  def uninterruptibleMask[R, A](k: ZIO.InterruptStatusRestore => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see
   *   See [[zio.ZIO.unless]]
   */
  def unless[R, A](b: => Boolean)(zio: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, Option[A]] =
    ZIO.unless(b)(zio)

  /**
   * @see
   *   See [[zio.ZIO.unlessM]]
   */
  @deprecated("use unlessZIO", "2.0.0")
  def unlessM[R](b: => RIO[R, Boolean]): ZIO.UnlessZIO[R, Throwable] =
    ZIO.unlessM(b)

  /**
   * @see
   *   See [[zio.ZIO.unlessZIO]]
   */
  def unlessZIO[R](b: => RIO[R, Boolean]): ZIO.UnlessZIO[R, Throwable] =
    ZIO.unlessZIO(b)

  /**
   * @see
   *   See [[zio.ZIO.unsandbox]]
   */
  def unsandbox[R, A](v: => IO[Cause[Throwable], A])(implicit trace: ZTraceElement): RIO[R, A] =
    ZIO.unsandbox(v)

  /**
   * @see
   *   See [[zio.ZIO.updateState]]
   */
  def updateState[S: Tag](f: S => S)(implicit trace: ZTraceElement): ZIO[ZState[S], Nothing, Unit] =
    ZIO.updateState(f)

  /**
   * @see
   *   See [[zio.ZIO.when]]
   */
  def when[R, A](b: => Boolean)(rio: => RIO[R, A])(implicit trace: ZTraceElement): RIO[R, Option[A]] =
    ZIO.when(b)(rio)

  /**
   * @see
   *   See [[zio.ZIO.whenCase]]
   */
  def whenCase[R, A, B](a: => A)(pf: PartialFunction[A, RIO[R, B]])(implicit trace: ZTraceElement): RIO[R, Option[B]] =
    ZIO.whenCase(a)(pf)

  /**
   * @see
   *   See [[zio.ZIO.whenCaseM]]
   */
  @deprecated("use whenCaseZIO", "2.0.0")
  def whenCaseM[R, A, B](a: => RIO[R, A])(pf: PartialFunction[A, RIO[R, B]])(implicit
    trace: ZTraceElement
  ): RIO[R, Option[B]] =
    ZIO.whenCaseM(a)(pf)

  /**
   * @see
   *   See [[zio.ZIO.whenCaseZIO]]
   */
  def whenCaseZIO[R, A, B](a: => RIO[R, A])(pf: PartialFunction[A, RIO[R, B]])(implicit
    trace: ZTraceElement
  ): RIO[R, Option[B]] =
    ZIO.whenCaseZIO(a)(pf)

  /**
   * @see
   *   See [[zio.ZIO.whenM]]
   */
  @deprecated("use whenZIO", "2.0.0")
  def whenM[R](b: => RIO[R, Boolean]): ZIO.WhenZIO[R, Throwable] =
    ZIO.whenM(b)

  /**
   * @see
   *   See [[zio.ZIO.whenZIO]]
   */
  def whenZIO[R](b: => RIO[R, Boolean]): ZIO.WhenZIO[R, Throwable] =
    ZIO.whenZIO(b)

  /**
   * @see
   *   See [[zio.ZIO.withRuntimeConfig]]
   */
  def withRuntimeConfig[R, A](runtimeConfig: => RuntimeConfig)(rio: => RIO[R, A])(implicit
    trace: ZTraceElement
  ): RIO[R, A] =
    ZIO.withRuntimeConfig(runtimeConfig)(rio)

  /**
   * @see
   *   See [[zio.ZIO.yieldNow]]
   */
  def yieldNow(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.yieldNow

  private[zio] def succeedNow[A](a: A): UIO[A] =
    ZIO.succeedNow(a)
}
