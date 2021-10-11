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

object Task extends TaskPlatformSpecific {

  /**
   * @see See [[zio.ZIO.absolve]]
   */
  def absolve[A](v: => Task[Either[Throwable, A]])(implicit trace: ZTraceElement): Task[A] =
    ZIO.absolve(v)

  /**
   * @see See acquireReleaseWith [[zio.ZIO]]
   */
  def acquireReleaseWith[A](acquire: => Task[A]): ZIO.Acquire[Any, Throwable, A] =
    ZIO.acquireReleaseWith(acquire)

  /**
   * @see See acquireReleaseWith [[zio.ZIO]]
   */
  def acquireReleaseWith[A, B](acquire: => Task[A], release: A => UIO[Any], use: A => Task[B])(implicit
    trace: ZTraceElement
  ): Task[B] =
    ZIO.acquireReleaseWith(acquire, release, use)

  /**
   * @see See acquireReleaseExitWith [[zio.ZIO]]
   */
  def acquireReleaseExitWith[A](acquire: => Task[A]): ZIO.AcquireExit[Any, Throwable, A] =
    ZIO.acquireReleaseExitWith(acquire)

  /**
   * @see See acquireReleaseExitWith [[zio.ZIO]]
   */
  def acquireReleaseExitWith[A, B](
    acquire: => Task[A],
    release: (A, Exit[Throwable, B]) => UIO[Any],
    use: A => Task[B]
  )(implicit trace: ZTraceElement): Task[B] =
    ZIO.acquireReleaseExitWith(acquire, release, use)

  /**
   * @see See [[zio.ZIO.allowInterrupt]]
   */
  def allowInterrupt(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.allowInterrupt

  /**
   * @see See [[zio.ZIO.apply]]
   */
  def apply[A](a: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.apply(a)

  /**
   * @see See [[zio.ZIO.async]]
   */
  def async[A](register: (Task[A] => Unit) => Any, blockingOn: => FiberId = FiberId.None)(implicit
    trace: ZTraceElement
  ): Task[A] =
    ZIO.async(register, blockingOn)

  /**
   * @see See [[zio.ZIO.asyncMaybe]]
   */
  def asyncMaybe[A](
    register: (Task[A] => Unit) => Option[Task[A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): Task[A] =
    ZIO.asyncMaybe(register, blockingOn)

  /**
   * @see See [[zio.ZIO.asyncZIO]]
   */
  def asyncZIO[A](register: (Task[A] => Unit) => Task[Any])(implicit trace: ZTraceElement): Task[A] =
    ZIO.asyncZIO(register)

  /**
   * @see See [[zio.ZIO.asyncInterrupt]]
   */
  def asyncInterrupt[A](
    register: (Task[A] => Unit) => Either[Canceler[Any], Task[A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): Task[A] =
    ZIO.asyncInterrupt(register, blockingOn)

  /**
   * @see See [[zio.ZIO.attempt]]
   */
  def attempt[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.attempt(effect)

  /**
   * @see See [[zio.ZIO.attemptBlocking]]
   */
  def attemptBlocking[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.attemptBlocking(effect)

  /**
   * @see See [[zio.ZIO.attemptBlockingCancelable]]
   */
  def attemptBlockingCancelable[A](effect: => A)(cancel: => UIO[Any])(implicit trace: ZTraceElement): Task[A] =
    ZIO.attemptBlockingCancelable(effect)(cancel)

  /**
   * @see See [[zio.ZIO.attemptBlockingInterrupt]]
   */
  def attemptBlockingInterrupt[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.attemptBlockingInterrupt(effect)

  /**
   * @see See [[zio.ZIO.blocking]]
   */
  def blocking[A](zio: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.blocking(zio)

  /**
   * @see See [[zio.ZIO.blockingExecutor]]
   */
  def blockingExecutor(implicit trace: ZTraceElement): UIO[Executor] =
    ZIO.blockingExecutor

  /**
   * @see See bracket [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseWith", "2.0.0")
  def bracket[A](acquire: => Task[A]): ZIO.Acquire[Any, Throwable, A] =
    ZIO.bracket(acquire)

  /**
   * @see See bracket [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseWith", "2.0.0")
  def bracket[A, B](acquire: => Task[A], release: A => UIO[Any], use: A => Task[B])(implicit
    trace: ZTraceElement
  ): Task[B] =
    ZIO.bracket(acquire, release, use)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseExitWith", "2.0.0")
  def bracketExit[A](acquire: => Task[A]): ZIO.AcquireExit[Any, Throwable, A] =
    ZIO.bracketExit(acquire)

  /**
   * @see See bracketExit [[zio.ZIO]]
   */
  @deprecated("use acquireReleaseExitWith", "2.0.0")
  def bracketExit[A, B](
    acquire: => Task[A],
    release: (A, Exit[Throwable, B]) => UIO[Any],
    use: A => Task[B]
  )(implicit trace: ZTraceElement): Task[B] =
    ZIO.bracketExit(acquire, release, use)

  /**
   * @see See [[zio.ZIO.checkInterruptible]]
   */
  def checkInterruptible[A](f: InterruptStatus => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.checkInterruptible(f)

  /**
   * @see See [[zio.ZIO.checkTraced]]
   */
  def checkTraced[A](f: TracingStatus => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.checkTraced(f)

  /**
   * @see See [[[zio.ZIO.collect[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collect[A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(
    f: A => IO[Option[Throwable], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.collect(in)(f)

  /**
   * @see See [[[zio.ZIO.collect[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def collect[Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => IO[Option[Throwable], (Key2, Value2)])(implicit trace: ZTraceElement): Task[Map[Key2, Value2]] =
    ZIO.collect(map)(f)

  /**
   * @see See [[[zio.ZIO.collectAll[R,E,A,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collectAll[A, Collection[+Element] <: Iterable[Element]](
    in: Collection[Task[A]]
  )(implicit bf: BuildFrom[Collection[Task[A]], A, Collection[A]], trace: ZTraceElement): Task[Collection[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[[zio.ZIO.collectAll[R,E,A](in:Set*]]]
   */
  def collectAll[A](in: Set[Task[A]])(implicit trace: ZTraceElement): Task[Set[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[[zio.ZIO.collectAll[R,E,A](in:Array*]]]
   */
  def collectAll[A: ClassTag](in: Array[Task[A]])(implicit trace: ZTraceElement): Task[Array[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[[zio.ZIO.collectAll[R,E,A](in:Option*]]]
   */
  def collectAll[A](in: Option[Task[A]])(implicit trace: ZTraceElement): Task[Option[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[[zio.ZIO.collectAll[R,E,A](in:zio\.NonEmptyChunk*]]]
   */
  def collectAll[A](in: NonEmptyChunk[Task[A]])(implicit trace: ZTraceElement): Task[NonEmptyChunk[A]] =
    ZIO.collectAll(in)

  /**
   * @see See [[zio.ZIO.collectAll_]]
   */
  @deprecated("use collectAllDiscard", "2.0.0")
  def collectAll_[A](in: => Iterable[Task[A]])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.collectAll_(in)

  /**
   * @see See [[zio.ZIO.collectAllDiscard]]
   */
  def collectAllDiscard[A](in: => Iterable[Task[A]])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.collectAllDiscard(in)

  /**
   * @see See [[[zio.ZIO.collectAllPar[R,E,A,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collectAllPar[A, Collection[+Element] <: Iterable[Element]](
    as: Collection[Task[A]]
  )(implicit bf: BuildFrom[Collection[Task[A]], A, Collection[A]], trace: ZTraceElement): Task[Collection[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[[zio.ZIO.collectAllPar[R,E,A](as:Set*]]]
   */
  def collectAllPar[A](as: Set[Task[A]])(implicit trace: ZTraceElement): Task[Set[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[[zio.ZIO.collectAllPar[R,E,A](as:Array*]]]
   */
  def collectAllPar[A: ClassTag](as: Array[Task[A]])(implicit trace: ZTraceElement): Task[Array[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[[zio.ZIO.collectAllPar[R,E,A](as:zio\.NonEmptyChunk*]]]
   */
  def collectAllPar[A](as: NonEmptyChunk[Task[A]])(implicit trace: ZTraceElement): Task[NonEmptyChunk[A]] =
    ZIO.collectAllPar(as)

  /**
   * @see See [[zio.ZIO.collectAllPar_]]
   */
  @deprecated("use collectAllParDiscard", "2.0.0")
  def collectAllPar_[A](in: => Iterable[Task[A]])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.collectAllPar_(in)

  /**
   * @see See [[zio.ZIO.collectAllParDiscard]]
   */
  def collectAllParDiscard[A](in: => Iterable[Task[A]])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.collectAllParDiscard(in)

  /**
   * @see See [[zio.ZIO.collectAllParN]]
   */
  def collectAllParN[A, Collection[+Element] <: Iterable[Element]](
    n: => Int
  )(
    as: Collection[Task[A]]
  )(implicit bf: BuildFrom[Collection[Task[A]], A, Collection[A]], trace: ZTraceElement): Task[Collection[A]] =
    ZIO.collectAllParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllParN_]]
   */
  @deprecated("use collectAllParNDiscard", "2.0.0")
  def collectAllParN_[A](n: => Int)(as: => Iterable[Task[A]])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.collectAllParN_(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllParNDiscard]]
   */
  def collectAllParNDiscard[A](n: => Int)(as: => Iterable[Task[A]])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.collectAllParNDiscard(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccesses]]
   */
  def collectAllSuccesses[A, Collection[+Element] <: Iterable[Element]](
    in: Collection[Task[A]]
  )(implicit bf: BuildFrom[Collection[Task[A]], A, Collection[A]], trace: ZTraceElement): UIO[Collection[A]] =
    ZIO.collectAllSuccesses(in)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesPar]]
   */
  def collectAllSuccessesPar[A, Collection[+Element] <: Iterable[Element]](
    as: Collection[Task[A]]
  )(implicit bf: BuildFrom[Collection[Task[A]], A, Collection[A]], trace: ZTraceElement): UIO[Collection[A]] =
    ZIO.collectAllSuccessesPar(as)

  /**
   * @see See [[zio.ZIO.collectAllSuccessesParN]]
   */
  def collectAllSuccessesParN[A, Collection[+Element] <: Iterable[Element]](
    n: => Int
  )(
    as: Collection[Task[A]]
  )(implicit bf: BuildFrom[Collection[Task[A]], A, Collection[A]], trace: ZTraceElement): UIO[Collection[A]] =
    ZIO.collectAllSuccessesParN(n)(as)

  /**
   * @see See [[zio.ZIO.collectAllWith]]
   */
  def collectAllWith[A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[Task[A]]
  )(
    f: PartialFunction[A, B]
  )(implicit bf: BuildFrom[Collection[Task[A]], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.collectAllWith(as)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithPar]]
   */
  def collectAllWithPar[A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[Task[A]]
  )(
    f: PartialFunction[A, B]
  )(implicit bf: BuildFrom[Collection[Task[A]], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.collectAllWithPar(as)(f)

  /**
   * @see See [[zio.ZIO.collectAllWithParN]]
   */
  def collectAllWithParN[A, B, Collection[+Element] <: Iterable[Element]](n: => Int)(
    as: Collection[Task[A]]
  )(
    f: PartialFunction[A, B]
  )(implicit bf: BuildFrom[Collection[Task[A]], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.collectAllWithParN(n)(as)(f)

  /**
   * @see See [[zio.ZIO.collectFirst]]
   */
  def collectFirst[A, B](as: => Iterable[A])(f: A => Task[Option[B]])(implicit trace: ZTraceElement): Task[Option[B]] =
    ZIO.collectFirst(as)(f)

  /**
   * @see See [[[zio.ZIO.collectPar[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def collectPar[A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(
    f: A => IO[Option[Throwable], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.collectPar(in)(f)

  /**
   * @see See [[[zio.ZIO.collectPar[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def collectPar[Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => IO[Option[Throwable], (Key2, Value2)])(implicit trace: ZTraceElement): Task[Map[Key2, Value2]] =
    ZIO.collectPar(map)(f)

  /**
   * @see See [[zio.ZIO.collectParN]]
   */
  def collectParN[A, B, Collection[+Element] <: Iterable[Element]](n: => Int)(
    in: Collection[A]
  )(
    f: A => IO[Option[Throwable], B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.collectParN(n)(in)(f)

  /**
   * @see See [[zio.ZIO.cond]]
   */
  def cond[A](predicate: => Boolean, result: => A, error: => Throwable)(implicit trace: ZTraceElement): Task[A] =
    ZIO.cond(predicate, result, error)

  /**
   * @see See [[zio.ZIO.debug]]
   */
  def debug(value: => Any)(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.debug(value)

  /**
   * @see See [[zio.ZIO.die]]
   */
  def die(t: => Throwable)(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.die(t)

  /**
   * @see See [[zio.ZIO.dieMessage]]
   */
  def dieMessage(message: => String)(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.dieMessage(message)

  /**
   * @see See [[zio.ZIO.done]]
   */
  def done[A](r: => Exit[Throwable, A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.done(r)

  /**
   * @see See [[zio.ZIO.descriptor]]
   */
  def descriptor(implicit trace: ZTraceElement): UIO[Fiber.Descriptor] =
    ZIO.descriptor

  /**
   * @see See [[zio.ZIO.descriptorWith]]
   */
  def descriptorWith[A](f: Fiber.Descriptor => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.descriptorWith(f)

  /**
   * @see See [[zio.ZIO.effect]]
   */
  @deprecated("use attempt", "2.0.0")
  def effect[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.effect(effect)

  /**
   * @see See [[zio.ZIO.effectAsync]]
   */
  @deprecated("use async", "2.0.0")
  def effectAsync[A](register: (Task[A] => Unit) => Any, blockingOn: => FiberId = FiberId.None)(implicit
    trace: ZTraceElement
  ): Task[A] =
    ZIO.effectAsync(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncMaybe]]
   */
  @deprecated("use asyncMaybe", "2.0.0")
  def effectAsyncMaybe[A](
    register: (Task[A] => Unit) => Option[Task[A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectAsyncMaybe(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectAsyncM]]
   */
  @deprecated("use asyncZIO", "2.0.0")
  def effectAsyncM[A](register: (Task[A] => Unit) => Task[Any])(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectAsyncM(register)

  /**
   * @see See [[zio.ZIO.effectAsyncInterrupt]]
   */
  @deprecated("use asyncInterrupt", "2.0.0")
  def effectAsyncInterrupt[A](
    register: (Task[A] => Unit) => Either[Canceler[Any], Task[A]],
    blockingOn: => FiberId = FiberId.None
  )(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectAsyncInterrupt(register, blockingOn)

  /**
   * @see See [[zio.ZIO.effectBlocking]]
   */
  @deprecated("use attemptBlocking", "2.0.0")
  def effectBlocking[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectBlocking(effect)

  /**
   * @see See [[zio.ZIO.effectBlockingCancelable]]
   */
  @deprecated("use attemptBlockingCancelable", "2.0.0")
  def effectBlockingCancelable[A](effect: => A)(cancel: => UIO[Any])(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectBlockingCancelable(effect)(cancel)

  /**
   * @see See [[zio.ZIO.effectBlockingInterrupt]]
   */
  @deprecated("use attemptBlockingInterrupt", "2.0.0")
  def effectBlockingInterrupt[A](effect: => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectBlockingInterrupt(effect)

  /**
   * @see See [[zio.RIO.effectSuspend]]
   */
  @deprecated("use suspend", "2.0.0")
  def effectSuspend[A](task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectSuspend(task)

  /**
   * @see See [[zio.ZIO.effectSuspendTotal]]
   */
  @deprecated("use suspendSucceed", "2.0.0")
  def effectSuspendTotal[A](task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectSuspendTotal(task)

  /**
   * @see See [[zio.ZIO.effectSuspendTotalWith]]
   */
  @deprecated("use suspendSucceedWith", "2.0.0")
  def effectSuspendTotalWith[A](p: (Platform, Fiber.Id) => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectSuspendTotalWith(p)

  /**
   * @see See [[zio.RIO.effectSuspendWith]]
   */
  @deprecated("use suspendWith", "2.0.0")
  def effectSuspendWith[A](p: (Platform, Fiber.Id) => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.effectSuspendWith(p)

  /**
   * @see See [[zio.ZIO.effectTotal]]
   */
  @deprecated("use succeed", "2.0.0")
  def effectTotal[A](effect: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.effectTotal(effect)

  /**
   * @see See [[zio.ZIO.executor]]
   */
  def executor(implicit trace: ZTraceElement): UIO[Executor] =
    ZIO.executor

  /**
   * @see See [[zio.ZIO.exists]]
   */
  def exists[A](as: => Iterable[A])(f: A => Task[Boolean])(implicit trace: ZTraceElement): Task[Boolean] =
    ZIO.exists(as)(f)

  /**
   * @see See [[zio.ZIO.fail]]
   */
  def fail(error: => Throwable)(implicit trace: ZTraceElement): Task[Nothing] =
    ZIO.fail(error)

  /**
   * @see See [[zio.ZIO.failCause]]
   */
  def failCause(cause: => Cause[Throwable])(implicit trace: ZTraceElement): Task[Nothing] =
    ZIO.failCause(cause)

  /**
   * @see See [[zio.ZIO.failCauseWith]]
   */
  def failCauseWith[E <: Throwable](function: (() => ZTrace) => Cause[E])(implicit
    trace: ZTraceElement
  ): Task[Nothing] =
    ZIO.failCauseWith(function)

  /**
   * @see [[zio.ZIO.fiberId]]
   */
  def fiberId(implicit trace: ZTraceElement): UIO[FiberId] =
    ZIO.fiberId

  /**
   * @see [[zio.ZIO.filter[R,E,A,Collection*]]
   */
  def filter[A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => Task[Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): Task[Collection[A]] =
    ZIO.filter(as)(f)

  /**
   * @see [[zio.ZIO.filter[R,E,A](as:Set*]]
   */
  def filter[A](as: Set[A])(f: A => Task[Boolean])(implicit trace: ZTraceElement): Task[Set[A]] =
    ZIO.filter(as)(f)

  /**
   * @see [[zio.ZIO.filterPar[R,E,A,Collection*]]
   */
  def filterPar[A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => Task[Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): Task[Collection[A]] =
    ZIO.filterPar(as)(f)

  /**
   * @see [[[zio.ZIO.filterPar[R,E,A](as:Set*]]]
   */
  def filterPar[A](as: Set[A])(f: A => Task[Boolean])(implicit trace: ZTraceElement): Task[Set[A]] =
    ZIO.filterPar(as)(f)

  /**
   * @see [[zio.ZIO.filterNot[R,E,A,Collection*]]
   */
  def filterNot[A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => Task[Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): Task[Collection[A]] =
    ZIO.filterNot(as)(f)

  /**
   * @see [[[zio.ZIO.filterNot[R,E,A](as:Set*]]]
   */
  def filterNot[A](as: Set[A])(f: A => Task[Boolean])(implicit trace: ZTraceElement): Task[Set[A]] =
    ZIO.filterNot(as)(f)

  /**
   * @see [[zio.ZIO.filterNotPar[R,E,A,Collection*]]
   */
  def filterNotPar[A, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    f: A => Task[Boolean]
  )(implicit bf: BuildFrom[Collection[A], A, Collection[A]], trace: ZTraceElement): Task[Collection[A]] =
    ZIO.filterNotPar(as)(f)

  /**
   * @see [[[zio.ZIO.filterNotPar[R,E,A](as:Set*]]]
   */
  def filterNotPar[A](as: Set[A])(f: A => Task[Boolean])(implicit trace: ZTraceElement): Task[Set[A]] =
    ZIO.filterNotPar(as)(f)

  /**
   * @see See [[zio.ZIO.firstSuccessOf]]
   */
  def firstSuccessOf[A](
    task: => Task[A],
    rest: => Iterable[Task[A]]
  )(implicit trace: ZTraceElement): Task[A] =
    ZIO.firstSuccessOf(task, rest)

  /**
   * @see See [[zio.ZIO.flatten]]
   */
  def flatten[A](task: => Task[Task[A]])(implicit trace: ZTraceElement): Task[A] =
    ZIO.flatten(task)

  /**
   * @see See [[zio.ZIO.foldLeft]]
   */
  def foldLeft[S, A](in: => Iterable[A])(zero: => S)(f: (S, A) => Task[S])(implicit trace: ZTraceElement): Task[S] =
    ZIO.foldLeft(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.foldRight]]
   */
  def foldRight[S, A](in: => Iterable[A])(zero: => S)(f: (A, S) => Task[S])(implicit trace: ZTraceElement): Task[S] =
    ZIO.foldRight(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.forall]]
   */
  def forall[A](as: => Iterable[A])(f: A => Task[Boolean])(implicit trace: ZTraceElement): Task[Boolean] =
    ZIO.forall(as)(f)

  /**
   * @see See [[[zio.ZIO.foreach[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def foreach[A, B, Collection[+Element] <: Iterable[Element]](
    in: Collection[A]
  )(
    f: A => Task[B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[[zio.ZIO.foreach[R,E,A,B](in:Set*]]]
   */
  def foreach[A, B](in: Set[A])(f: A => Task[B])(implicit trace: ZTraceElement): Task[Set[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[[zio.ZIO.foreach[R,E,A,B](in:Array*]]]
   */
  def foreach[A, B: ClassTag](in: Array[A])(f: A => Task[B])(implicit trace: ZTraceElement): Task[Array[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[[zio.ZIO.foreach[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def foreach[Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => Task[(Key2, Value2)])(implicit trace: ZTraceElement): Task[Map[Key2, Value2]] =
    ZIO.foreach(map)(f)

  /**
   * @see See [[[zio.ZIO.foreach[R,E,A,B](in:Option*]]]
   */
  def foreach[A, B](in: Option[A])(f: A => Task[B])(implicit trace: ZTraceElement): Task[Option[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[[zio.ZIO.foreach[R,E,A,B](in:zio\.NonEmptyChunk*]]]
   */
  def foreach[A, B](in: NonEmptyChunk[A])(f: A => Task[B])(implicit trace: ZTraceElement): Task[NonEmptyChunk[B]] =
    ZIO.foreach(in)(f)

  /**
   * @see See [[zio.ZIO.foreachExec]]
   */
  final def foreachExec[A, B, Collection[+Element] <: Iterable[Element]](as: Collection[A])(
    exec: => ExecutionStrategy
  )(
    f: A => Task[B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.foreachExec(as)(exec)(f)

  /**
   * @see See [[[zio.ZIO.foreachPar[R,E,A,B,Collection[+Element]<:Iterable[Element]]*]]]
   */
  def foreachPar[A, B, Collection[+Element] <: Iterable[Element]](
    as: Collection[A]
  )(
    fn: A => Task[B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[[zio.ZIO.foreachPar[R,E,A,B](as:Set*]]]
   */
  def foreachPar[A, B](as: Set[A])(fn: A => Task[B])(implicit trace: ZTraceElement): Task[Set[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[[zio.ZIO.foreachPar[R,E,A,B](as:Array*]]]
   */
  def foreachPar[A, B: ClassTag](as: Array[A])(fn: A => Task[B])(implicit trace: ZTraceElement): Task[Array[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[[zio.ZIO.foreachPar[R,E,Key,Key2,Value,Value2](map:Map*]]]
   */
  def foreachPar[Key, Key2, Value, Value2](
    map: Map[Key, Value]
  )(f: (Key, Value) => Task[(Key2, Value2)])(implicit trace: ZTraceElement): Task[Map[Key2, Value2]] =
    ZIO.foreachPar(map)(f)

  /**
   * @see See [[[zio.ZIO.foreachPar[R,E,A,B](as:zio\.NonEmptyChunk*]]]
   */
  def foreachPar[A, B](as: NonEmptyChunk[A])(fn: A => Task[B])(implicit trace: ZTraceElement): Task[NonEmptyChunk[B]] =
    ZIO.foreachPar(as)(fn)

  /**
   * @see See [[zio.ZIO.foreachParN]]
   */
  def foreachParN[A, B, Collection[+Element] <: Iterable[Element]](
    n: => Int
  )(as: Collection[A])(
    fn: A => Task[B]
  )(implicit bf: BuildFrom[Collection[A], B, Collection[B]], trace: ZTraceElement): Task[Collection[B]] =
    ZIO.foreachParN(n)(as)(fn)

  /**
   * @see See [[zio.ZIO.foreach_]]
   */
  @deprecated("use foreachDiscard", "2.0.0")
  def foreach_[A](as: => Iterable[A])(f: A => Task[Any])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.foreach_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachDiscard]]
   */
  def foreachDiscard[A](as: => Iterable[A])(f: A => Task[Any])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.foreachDiscard(as)(f)

  /**
   * @see See [[zio.ZIO.foreachPar_]]
   */
  @deprecated("use foreachParDiscard", "2.0.0")
  def foreachPar_[A, B](as: => Iterable[A])(f: A => Task[Any])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.foreachPar_(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParDiscard]]
   */
  def foreachParDiscard[A, B](as: => Iterable[A])(f: A => Task[Any])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.foreachParDiscard(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParN_]]
   */
  @deprecated("use foreachParNDiscard", "2.0.0")
  def foreachParN_[A, B](n: => Int)(as: => Iterable[A])(f: A => Task[Any])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.foreachParN_(n)(as)(f)

  /**
   * @see See [[zio.ZIO.foreachParNDiscard]]
   */
  def foreachParNDiscard[A, B](n: => Int)(as: => Iterable[A])(f: A => Task[Any])(implicit
    trace: ZTraceElement
  ): Task[Unit] =
    ZIO.foreachParNDiscard(n)(as)(f)

  /**
   * @see See [[zio.ZIO.forkAll]]
   */
  def forkAll[A, Collection[+Element] <: Iterable[Element]](
    as: Collection[Task[A]]
  )(implicit
    bf: BuildFrom[Collection[Task[A]], A, Collection[A]],
    trace: ZTraceElement
  ): UIO[Fiber[Throwable, Collection[A]]] =
    ZIO.forkAll(as)

  /**
   * @see See [[zio.ZIO.forkAll_]]
   */
  @deprecated("use forkAllDiscard", "2.0.0")
  def forkAll_[A](as: => Iterable[Task[A]])(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.forkAll_(as)

  /**
   * @see See [[zio.ZIO.forkAllDiscard]]
   */
  def forkAllDiscard[A](as: => Iterable[Task[A]])(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.forkAllDiscard(as)

  /**
   * Constructs a `Task` value of the appropriate type for the specified input.
   */
  def from[Input](input: => Input)(implicit
    constructor: ZIO.ZIOConstructor[Any, Throwable, Input],
    trace: ZTraceElement
  ): ZIO[constructor.OutEnvironment, constructor.OutError, constructor.OutSuccess] =
    constructor.make(input)

  /**
   * @see See [[zio.ZIO.fromEither]]
   */
  def fromEither[A](v: => Either[Throwable, A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromEither(v)

  /**
   * @see See [[zio.ZIO.fromFiber]]
   */
  def fromFiber[A](fiber: => Fiber[Throwable, A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromFiber(fiber)

  /**
   * @see See [[zio.ZIO.fromFiberM]]
   */
  @deprecated("use fromFiberZIO", "2.0.0")
  def fromFiberM[A](fiber: => Task[Fiber[Throwable, A]])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromFiberM(fiber)

  /**
   * @see See [[zio.ZIO.fromFiberZIO]]
   */
  def fromFiberZIO[A](fiber: => Task[Fiber[Throwable, A]])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromFiberZIO(fiber)

  /**
   * @see See [[zio.ZIO.fromFuture]]
   */
  def fromFuture[A](make: ExecutionContext => scala.concurrent.Future[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromFuture(make)

  /**
   * @see See [[zio.ZIO.fromFutureInterrupt]]
   */
  def fromFutureInterrupt[A](make: ExecutionContext => scala.concurrent.Future[A])(implicit
    trace: ZTraceElement
  ): Task[A] =
    ZIO.fromFutureInterrupt(make)

  /**
   * @see See [[zio.ZIO.fromTry]]
   */
  def fromTry[A](value: => scala.util.Try[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.fromTry(value)

  /**
   * @see See [[zio.ZIO.getOrFail]]
   */
  final def getOrFail[A](v: => Option[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.getOrFail(v)

  /**
   * @see See [[zio.ZIO.halt]]
   */
  @deprecated("use failCause", "2.0.0")
  def halt(cause: => Cause[Throwable])(implicit trace: ZTraceElement): Task[Nothing] =
    ZIO.halt(cause)

  /**
   * @see See [[zio.ZIO.haltWith]]
   */
  @deprecated("use failCauseWith", "2.0.0")
  def haltWith[E <: Throwable](function: (() => ZTrace) => Cause[E])(implicit trace: ZTraceElement): Task[Nothing] =
    ZIO.haltWith(function)

  /**
   * @see [[zio.ZIO.ifM]]
   */
  @deprecated("use ifZIO", "2.0.0")
  def ifM(b: => Task[Boolean]): ZIO.IfZIO[Any, Throwable] =
    ZIO.ifM(b)

  /**
   * @see [[zio.ZIO.ifZIO]]
   */
  def ifZIO(b: => Task[Boolean]): ZIO.IfZIO[Any, Throwable] =
    ZIO.ifZIO(b)

  /**
   * @see See [[zio.ZIO.interrupt]]
   */
  def interrupt(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.interrupt

  /**
   * @see See [[zio.ZIO.interruptAs]]
   */
  def interruptAs(fiberId: => FiberId)(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.interruptAs(fiberId)

  /**
   * @see See [[zio.ZIO.interruptible]]
   */
  def interruptible[A](task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.interruptible(task)

  /**
   * @see See [[zio.ZIO.interruptibleMask]]
   */
  def interruptibleMask[A](k: ZIO.InterruptStatusRestore => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.interruptibleMask(k)

  /**
   * @see See [[zio.ZIO.iterate]]
   */
  def iterate[S](initial: => S)(cont: S => Boolean)(body: S => Task[S])(implicit trace: ZTraceElement): Task[S] =
    ZIO.iterate(initial)(cont)(body)

  /**
   *  @see See [[zio.ZIO.left]]
   */
  def left[A](a: => A)(implicit trace: ZTraceElement): Task[Either[A, Nothing]] =
    ZIO.left(a)

  /**
   * @see See [[zio.ZIO.lock]]
   */
  @deprecated("use onExecutor", "2.0.0")
  def lock[A](executor: => Executor)(task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.lock(executor)(task)

  /**
   *  @see See [[zio.ZIO.loop]]
   */
  def loop[A, S](initial: => S)(cont: S => Boolean, inc: S => S)(body: S => Task[A])(implicit
    trace: ZTraceElement
  ): Task[List[A]] =
    ZIO.loop(initial)(cont, inc)(body)

  /**
   *  @see See [[zio.ZIO.loop_]]
   */
  @deprecated("use loopDiscard", "2.0.0")
  def loop_[S](initial: => S)(cont: S => Boolean, inc: S => S)(body: S => Task[Any])(implicit
    trace: ZTraceElement
  ): Task[Unit] =
    ZIO.loop_(initial)(cont, inc)(body)

  /**
   *  @see See [[zio.ZIO.loopDiscard]]
   */
  def loopDiscard[S](initial: => S)(cont: S => Boolean, inc: S => S)(body: S => Task[Any])(implicit
    trace: ZTraceElement
  ): Task[Unit] =
    ZIO.loopDiscard(initial)(cont, inc)(body)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C]*]]
   */
  @deprecated("use zip", "2.0.0")
  def mapN[A, B, C](task1: => Task[A], task2: => Task[B])(f: (A, B) => C)(implicit trace: ZTraceElement): Task[C] =
    ZIO.mapN(task1, task2)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D]*]]
   */
  @deprecated("use zip", "2.0.0")
  def mapN[A, B, C, D](task1: => Task[A], task2: => Task[B], task3: => Task[C])(f: (A, B, C) => D)(implicit
    trace: ZTraceElement
  ): Task[D] =
    ZIO.mapN(task1, task2, task3)(f)

  /**
   *  @see [[zio.ZIO.mapN[R,E,A,B,C,D,F]*]]
   */
  @deprecated("use zip", "2.0.0")
  def mapN[A, B, C, D, F](task1: => Task[A], task2: => Task[B], task3: => Task[C], task4: => Task[D])(
    f: (A, B, C, D) => F
  )(implicit trace: ZTraceElement): Task[F] =
    ZIO.mapN(task1, task2, task3, task4)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C]*]]
   */
  @deprecated("use zipPar", "2.0.0")
  def mapParN[A, B, C](task1: => Task[A], task2: => Task[B])(f: (A, B) => C)(implicit trace: ZTraceElement): Task[C] =
    ZIO.mapParN(task1, task2)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D]*]]
   */
  @deprecated("use zipPar", "2.0.0")
  def mapParN[A, B, C, D](task1: => Task[A], task2: => Task[B], task3: => Task[C])(f: (A, B, C) => D)(implicit
    trace: ZTraceElement
  ): Task[D] =
    ZIO.mapParN(task1, task2, task3)(f)

  /**
   *  @see [[zio.ZIO.mapParN[R,E,A,B,C,D,F]*]]
   */
  @deprecated("use zipPar", "2.0.0")
  def mapParN[A, B, C, D, F](task1: => Task[A], task2: => Task[B], task3: => Task[C], task4: => Task[D])(
    f: (A, B, C, D) => F
  )(implicit trace: ZTraceElement): Task[F] =
    ZIO.mapParN(task1, task2, task3, task4)(f)

  /**
   * @see See [[zio.ZIO.memoize]]
   */
  def memoize[A, B](f: A => Task[B])(implicit trace: ZTraceElement): UIO[A => Task[B]] =
    ZIO.memoize(f)

  /**
   * @see See [[zio.ZIO.mergeAll]]
   */
  def mergeAll[A, B](in: Iterable[Task[A]])(zero: => B)(f: (B, A) => B)(implicit trace: ZTraceElement): Task[B] =
    ZIO.mergeAll(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.mergeAllPar]]
   */
  def mergeAllPar[A, B](in: => Iterable[Task[A]])(zero: => B)(f: (B, A) => B)(implicit trace: ZTraceElement): Task[B] =
    ZIO.mergeAllPar(in)(zero)(f)

  /**
   * @see See [[zio.ZIO.never]]
   */
  def never(implicit trace: ZTraceElement): UIO[Nothing] =
    ZIO.never

  /**
   * @see See [[zio.ZIO.none]]
   */
  val none: Task[Option[Nothing]] =
    ZIO.none

  /**
   * @see See [[zio.ZIO.noneOrFail]]
   */
  def noneOrFail(o: => Option[Throwable])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.noneOrFail(o)

  /**
   * @see See [[zio.ZIO.noneOrFailWith]]
   */
  def noneOrFailWith[O](o: => Option[O])(f: O => Throwable)(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.noneOrFailWith(o)(f)

  /**
   *  @see See [[zio.ZIO.not]]
   */
  def not(effect: => Task[Boolean])(implicit trace: ZTraceElement): Task[Boolean] =
    ZIO.not(effect)

  /**
   * @see See [[zio.ZIO.onExecutor]]
   */
  def onExecutor[A](executor: => Executor)(task: Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.onExecutor(executor)(task)

  /**
   * @see See [[zio.ZIO.partition]]
   */
  def partition[A, B](in: => Iterable[A])(f: A => Task[B])(implicit
    trace: ZTraceElement
  ): Task[(Iterable[Throwable], Iterable[B])] =
    ZIO.partition(in)(f)

  /**
   * @see See [[zio.ZIO.partitionPar]]
   */
  def partitionPar[A, B](in: => Iterable[A])(f: A => Task[B])(implicit
    trace: ZTraceElement
  ): Task[(Iterable[Throwable], Iterable[B])] =
    ZIO.partitionPar(in)(f)

  /**
   * @see See [[zio.ZIO.partitionParN]]
   */
  def partitionParN[A, B](n: => Int)(in: => Iterable[A])(f: A => Task[B])(implicit
    trace: ZTraceElement
  ): Task[(Iterable[Throwable], Iterable[B])] =
    ZIO.partitionParN(n)(in)(f)

  /**
   * @see See [[zio.ZIO.raceAll]]
   */
  def raceAll[A](task: => Task[A], ios: => Iterable[Task[A]])(implicit trace: ZTraceElement): Task[A] =
    ZIO.raceAll(task, ios)

  /**
   * @see See [[zio.ZIO.reduceAll]]
   */
  def reduceAll[A](a: => Task[A], as: => Iterable[Task[A]])(f: (A, A) => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.reduceAll(a, as)(f)

  /**
   * @see See [[zio.ZIO.reduceAllPar]]
   */
  def reduceAllPar[A](a: => Task[A], as: => Iterable[Task[A]])(f: (A, A) => A)(implicit trace: ZTraceElement): Task[A] =
    ZIO.reduceAllPar(a, as)(f)

  /**
   * @see See [[zio.ZIO.replicate]]
   */
  def replicate[A](n: => Int)(effect: => Task[A])(implicit trace: ZTraceElement): Iterable[Task[A]] =
    ZIO.replicate(n)(effect)

  /**
   * @see See [[zio.ZIO.replicateM]]
   */
  @deprecated("use replicateZIO", "2.0.0")
  def replicateM[A](n: => Int)(effect: => Task[A])(implicit trace: ZTraceElement): Task[Iterable[A]] =
    ZIO.replicateM(n)(effect)

  /**
   * @see See [[zio.ZIO.replicateM_]]
   */
  @deprecated("use replicateZIODiscard", "2.0.0")
  def replicateM_[A](n: => Int)(effect: => Task[A])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.replicateM_(n)(effect)

  /**
   * @see See [[zio.ZIO.replicateZIO]]
   */
  def replicateZIO[A](n: => Int)(effect: => Task[A])(implicit trace: ZTraceElement): Task[Iterable[A]] =
    ZIO.replicateZIO(n)(effect)

  /**
   * @see See [[zio.ZIO.replicateZIODiscard]]
   */
  def replicateZIODiscard[A](n: => Int)(effect: => Task[A])(implicit trace: ZTraceElement): Task[Unit] =
    ZIO.replicateZIODiscard(n)(effect)

  /**
   * @see See [[zio.ZIO.require]]
   */
  @deprecated("use someOrFail", "2.0.0")
  def require[A](error: => Throwable)(implicit trace: ZTraceElement): Task[Option[A]] => Task[A] =
    ZIO.require[Any, Throwable, A](error)

  /**
   * @see See [[zio.ZIO.reserve]]
   */
  def reserve[A, B](reservation: => Task[Reservation[Any, Throwable, A]])(use: A => Task[B])(implicit
    trace: ZTraceElement
  ): Task[B] =
    ZIO.reserve(reservation)(use)

  /**
   *  @see [[zio.ZIO.right]]
   */
  def right[B](b: => B)(implicit trace: ZTraceElement): Task[Either[Nothing, B]] =
    ZIO.right(b)

  /**
   * @see See [[zio.ZIO.runtime]]
   */
  def runtime(implicit trace: ZTraceElement): UIO[Runtime[Any]] =
    ZIO.runtime

  /**
   * @see See [[zio.ZIO.runtimeConfig]]
   */
  def runtimeConfig(implicit trace: ZTraceElement): UIO[RuntimeConfig] =
    ZIO.runtimeConfig

  /**
   *  @see [[zio.ZIO.some]]
   */
  def some[A](a: => A)(implicit trace: ZTraceElement): Task[Option[A]] =
    ZIO.some(a)

  /**
   * @see See [[zio.ZIO.succeed]]
   */
  def succeed[A](a: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.succeed(a)

  /**
   * @see See [[zio.ZIO.succeedBlocking]]
   */
  def succeedBlocking[A](a: => A)(implicit trace: ZTraceElement): UIO[A] =
    ZIO.succeedBlocking(a)

  /**
   * @see See [[zio.ZIO.suspend]]
   */
  def suspend[A](task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.suspend(task)

  /**
   * @see See [[zio.ZIO.suspendSucceed]]
   */
  def suspendSucceed[A](task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.suspendSucceed(task)

  /**
   * @see See [[zio.ZIO.suspendSucceedWith]]
   */
  def suspendSucceedWith[A](f: (RuntimeConfig, FiberId) => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.suspendSucceedWith(f)

  /**
   * @see See [[zio.RIO.suspendWith]]
   */
  def suspendWith[A](f: (RuntimeConfig, FiberId) => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.suspendWith(f)

  /**
   * @see See [[zio.ZIO.trace]]
   */
  def trace(implicit trace: ZTraceElement): UIO[ZTrace] =
    ZIO.trace

  /**
   * @see See [[zio.ZIO.traced]]
   */
  def traced[A](task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.traced(task)

  /**
   * @see See [[zio.ZIO.unit]]
   */
  val unit: UIO[Unit] =
    ZIO.unit

  /**
   * @see See [[zio.ZIO.uninterruptible]]
   */
  def uninterruptible[A](task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.uninterruptible(task)

  /**
   * @see See [[zio.ZIO.uninterruptibleMask]]
   */
  def uninterruptibleMask[A](k: ZIO.InterruptStatusRestore => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.uninterruptibleMask(k)

  /**
   * @see See [[zio.ZIO.unless]]
   */
  def unless[A](b: => Boolean)(zio: => Task[A])(implicit trace: ZTraceElement): Task[Option[A]] =
    ZIO.unless(b)(zio)

  /**
   * @see See [[zio.ZIO.unlessM]]
   */
  @deprecated("use unlessZIO", "2.0.0")
  def unlessM(b: => Task[Boolean]): ZIO.UnlessZIO[Any, Throwable] =
    ZIO.unlessM(b)

  /**
   * @see See [[zio.ZIO.unlessZIO]]
   */
  def unlessZIO(b: => Task[Boolean]): ZIO.UnlessZIO[Any, Throwable] =
    ZIO.unlessZIO(b)

  /**
   * @see [[zio.ZIO.unsandbox]]
   */
  def unsandbox[A](v: => IO[Cause[Throwable], A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.unsandbox(v)

  /**
   * @see See [[zio.ZIO.untraced]]
   */
  def untraced[A](task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.untraced(task)

  /**
   * @see See [[zio.ZIO.when]]
   */
  def when[A](b: => Boolean)(task: => Task[A])(implicit trace: ZTraceElement): Task[Option[A]] =
    ZIO.when(b)(task)

  /**
   * @see See [[zio.ZIO.whenCase]]
   */
  def whenCase[A, B](a: => A)(pf: PartialFunction[A, Task[B]])(implicit trace: ZTraceElement): Task[Option[B]] =
    ZIO.whenCase(a)(pf)

  /**
   * @see See [[zio.ZIO.whenCaseM]]
   */
  @deprecated("use whenCaseZIO", "2.0.0")
  def whenCaseM[A, B](a: => Task[A])(pf: PartialFunction[A, Task[B]])(implicit trace: ZTraceElement): Task[Option[B]] =
    ZIO.whenCaseM(a)(pf)

  /**
   * @see See [[zio.ZIO.whenCaseZIO]]
   */
  def whenCaseZIO[A, B](a: => Task[A])(pf: PartialFunction[A, Task[B]])(implicit
    trace: ZTraceElement
  ): Task[Option[B]] =
    ZIO.whenCaseZIO(a)(pf)

  /**
   * @see See [[zio.ZIO.whenM]]
   */
  @deprecated("use whenZIO", "2.0.0")
  def whenM(b: => Task[Boolean]): ZIO.WhenZIO[Any, Throwable] =
    ZIO.whenM(b)

  /**
   * @see See [[zio.ZIO.whenZIO]]
   */
  def whenZIO(b: => Task[Boolean]): ZIO.WhenZIO[Any, Throwable] =
    ZIO.whenZIO(b)

  /**
   *  @see See [[zio.ZIO.withRuntimeConfig]]
   */
  def withRuntimeConfig[A](runtimeConfig: => RuntimeConfig)(task: => Task[A])(implicit trace: ZTraceElement): Task[A] =
    ZIO.withRuntimeConfig(runtimeConfig)(task)

  /**
   * @see See [[zio.ZIO.yieldNow]]
   */
  def yieldNow(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.yieldNow

  private[zio] def succeedNow[A](a: A): UIO[A] =
    ZIO.succeedNow(a)
}
