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

package zio

object Managed {

  /**
   * See [[zio.ZManaged.absolve]]
   */
  def absolve[E, A](v: Managed[E, Either[E, A]]): Managed[E, A] =
    ZManaged.absolve(v)

  /**
   * See [[zio.ZManaged]]
   */
  def apply[E, A](reserve: IO[E, Reservation[Any, E, A]]): Managed[E, A] =
    ZManaged.apply(reserve)

  /**
   * See [[zio.ZManaged.collectAll]]
   */
  def collectAll[E, A1, A2](ms: Iterable[Managed[E, A2]]): Managed[E, List[A2]] =
    ZManaged.collectAll(ms)

  /**
   * See [[zio.ZManaged.collectAllPar]]
   */
  def collectAllPar[E, A](as: Iterable[Managed[E, A]]): Managed[E, List[A]] =
    ZManaged.collectAllPar(as)

  /**
   * See [[zio.ZManaged.collectAllParN]]
   */
  def collectAllParN[E, A](n: Int)(as: Iterable[Managed[E, A]]): Managed[E, List[A]] =
    ZManaged.collectAllParN(n)(as)

  /**
   * See [[zio.ZManaged.die]]
   */
  def die(t: Throwable): Managed[Nothing, Nothing] =
    ZManaged.die(t)

  /**
   * See [[zio.ZManaged.dieMessage]]
   */
  def dieMessage(message: String): Managed[Throwable, Nothing] =
    ZManaged.dieMessage(message)

  /**
   * See [[zio.ZManaged.done]]
   */
  def done[E, A](r: Exit[E, A]): Managed[E, A] =
    ZManaged.done(r)

  /**
   * See [[zio.ZManaged.effectTotal]]
   */
  def effectTotal[R, A](r: => A): ZManaged[R, Nothing, A] =
    ZManaged.effectTotal(r)

  /**
   * See [[zio.ZManaged.fail]]
   */
  def fail[E](error: E): Managed[E, Nothing] =
    ZManaged.fail(error)

  /**
   * See [[zio.ZManaged.finalizer]]
   */
  def finalizer(f: IO[Nothing, Any]): Managed[Nothing, Unit] =
    ZManaged.finalizer(f)

  /**
   * See [[zio.ZManaged.flatten]]
   */
  def flatten[E, A](m: Managed[E, Managed[E, A]]): Managed[E, A] =
    ZManaged.flatten(m)

  /**
   * See [[zio.ZManaged.foreach]]
   */
  def foreach[E, A1, A2](as: Iterable[A1])(f: A1 => Managed[E, A2]): Managed[E, List[A2]] =
    ZManaged.foreach(as)(f)

  /**
   * See [[zio.ZManaged.foreach_]]
   */
  def foreach_[E, A](as: Iterable[A])(f: A => Managed[E, Any]): Managed[E, Unit] =
    ZManaged.foreach_(as)(f)

  /**
   * See [[zio.ZManaged.foreachPar]]
   */
  def foreachPar[E, A1, A2](as: Iterable[A1])(f: A1 => Managed[E, A2]): Managed[E, List[A2]] =
    ZManaged.foreachPar(as)(f)

  /**
   * See [[zio.ZManaged.foreachPar_]]
   */
  def foreachPar_[E, A](as: Iterable[A])(f: A => Managed[E, Any]): Managed[E, Unit] =
    ZManaged.foreachPar_(as)(f)

  /**
   * See [[zio.ZManaged.foreachParN]]
   */
  def foreachParN[E, A1, A2](n: Int)(as: Iterable[A1])(f: A1 => Managed[E, A2]): Managed[E, List[A2]] =
    ZManaged.foreachParN(n)(as)(f)

  /**
   * See [[zio.ZManaged.foreachParN_]]
   */
  def foreachParN_[E, A](n: Int)(as: Iterable[A])(f: A => Managed[E, Any]): Managed[E, Unit] =
    ZManaged.foreachParN_(n)(as)(f)

  /**
   * See [[zio.ZManaged.fromAutoCloseable]]
   */
  def fromAutoCloseable[E, A <: AutoCloseable](fa: IO[E, A]): Managed[E, A] =
    ZManaged.fromAutoCloseable(fa)

  /**
   * See [[zio.ZManaged.fromEffect]]
   */
  def fromEffect[E, A](fa: IO[E, A]): Managed[E, A] =
    ZManaged.fromEffect(fa)

  /**
   * See [[zio.ZManaged.fromEither]]
   */
  def fromEither[E, A](v: => Either[E, A]): Managed[E, A] =
    ZManaged.fromEither(v)

  /**
   * See [[zio.ZManaged.halt]]
   */
  def halt[E](cause: Cause[E]): Managed[E, Nothing] =
    ZManaged.halt(cause)

  /**
   * See [[zio.ZManaged.interrupt]]
   */
  final val interrupt: Managed[Nothing, Nothing] = ZManaged.interrupt

  /**
   * See [[zio.ZManaged.make]]
   */
  def make[E, A](acquire: IO[E, A])(release: A => UIO[Any]): Managed[E, A] =
    ZManaged.make(acquire)(release)

  /**
   * See [[zio.ZManaged.makeEffect]]
   */
  def makeEffect[A](acquire: => A)(release: A => Any): Managed[Throwable, A] =
    ZManaged.makeEffect(acquire)(release)

  /**
   * See [[zio.ZManaged.makeExit]]
   */
  def makeExit[E, A](acquire: IO[E, A])(release: (A, Exit[Any, Any]) => UIO[Any]): Managed[E, A] =
    ZManaged.makeExit(acquire)(release)

  /**
   * See [[zio.ZManaged.makeInterruptible]]
   */
  def makeInterruptible[R, E, A](acquire: IO[E, A])(release: A => UIO[Any]): Managed[E, A] =
    ZManaged.makeInterruptible(acquire)(release)

  /**
   *  @see [[zio.ZManaged.mapN[R,E,A,B,C]*]]
   */
  def mapN[E, A, B, C](managed1: Managed[E, A], managed2: Managed[E, B])(f: (A, B) => C): Managed[E, C] =
    ZManaged.mapN(managed1, managed2)(f)

  /**
   *  @see [[zio.ZManaged.mapN[R,E,A,B,C,D]*]]
   */
  def mapN[E, A, B, C, D](managed1: Managed[E, A], managed2: Managed[E, B], managed3: Managed[E, C])(
    f: (A, B, C) => D
  ): Managed[E, D] =
    ZManaged.mapN(managed1, managed2, managed3)(f)

  /**
   *  @see [[zio.ZManaged.mapN[R,E,A,B,C,D,F]*]]
   */
  def mapN[E, A, B, C, D, F](
    managed1: Managed[E, A],
    managed2: Managed[E, B],
    managed3: Managed[E, C],
    managed4: Managed[E, D]
  )(
    f: (A, B, C, D) => F
  ): Managed[E, F] =
    ZManaged.mapN(managed1, managed2, managed3, managed4)(f)

  /**
   *  @see [[zio.ZManaged.mapParN[R,E,A,B,C]*]]
   */
  def mapParN[E, A, B, C](managed1: Managed[E, A], managed2: Managed[E, B])(f: (A, B) => C): Managed[E, C] =
    ZManaged.mapParN(managed1, managed2)(f)

  /**
   *  @see [[zio.ZManaged.mapParN[R,E,A,B,C,D]*]]
   */
  def mapParN[E, A, B, C, D](managed1: Managed[E, A], managed2: Managed[E, B], managed3: Managed[E, C])(
    f: (A, B, C) => D
  ): Managed[E, D] =
    ZManaged.mapParN(managed1, managed2, managed3)(f)

  /**
   *  @see [[zio.ZManaged.mapParN[R,E,A,B,C,D,F]*]]
   */
  def mapParN[E, A, B, C, D, F](
    managed1: Managed[E, A],
    managed2: Managed[E, B],
    managed3: Managed[E, C],
    managed4: Managed[E, D]
  )(
    f: (A, B, C, D) => F
  ): Managed[E, F] =
    ZManaged.mapParN(managed1, managed2, managed3, managed4)(f)

  /**
   * See [[zio.ZManaged.mergeAll]]
   */
  def mergeAll[E, A, B](in: Iterable[Managed[E, A]])(zero: B)(f: (B, A) => B): Managed[E, B] =
    ZManaged.mergeAll(in)(zero)(f)

  /**
   * See [[zio.ZManaged.mergeAllPar]]
   */
  def mergeAllPar[E, A, B](in: Iterable[Managed[E, A]])(zero: B)(f: (B, A) => B): Managed[E, B] =
    ZManaged.mergeAllPar(in)(zero)(f)

  /**
   * See [[zio.ZManaged.mergeAllParN]]
   */
  def mergeAllParN[E, A, B](n: Int)(in: Iterable[Managed[E, A]])(zero: B)(f: (B, A) => B): Managed[E, B] =
    ZManaged.mergeAllParN(n)(in)(zero)(f)

  /**
   * See [[zio.ZManaged.never]]
   */
  val never: Managed[Nothing, Nothing] = ZManaged.never

  /**
   * See [[zio.ZManaged.reduceAll]]
   */
  def reduceAll[E, A](a: Managed[E, A], as: Iterable[Managed[E, A]])(f: (A, A) => A): Managed[E, A] =
    ZManaged.reduceAll(a, as)(f)

  /**
   * See [[zio.ZManaged.reduceAllPar]]
   */
  def reduceAllPar[E, A](a: Managed[E, A], as: Iterable[Managed[E, A]])(f: (A, A) => A): Managed[E, A] =
    ZManaged.reduceAllPar(a, as)(f)

  /**
   * See [[zio.ZManaged.reduceAllParN]]
   */
  def reduceAllParN[E, A](
    n: Long
  )(a1: Managed[E, A], as: Iterable[Managed[E, A]])(f: (A, A) => A): Managed[E, A] =
    ZManaged.reduceAllParN(n)(a1, as)(f)

  /**
   * See [[zio.ZManaged.require]]
   */
  def require[E, A](error: E): Managed[E, Option[A]] => Managed[E, A] =
    ZManaged.require[Any, E, A](error)

  /**
   * See [[zio.ZManaged.reserve]]
   */
  def reserve[E, A](reservation: Reservation[Any, E, A]): Managed[E, A] =
    ZManaged.reserve(reservation)

  /**
   * See [[zio.ZManaged.sandbox]]
   */
  def sandbox[E, A](v: Managed[E, A]): Managed[Cause[E], A] =
    ZManaged.sandbox(v)

  /**
   * See [[zio.ZManaged.sequence]]
   */
  def sequence[E, A1, A2](ms: Iterable[Managed[E, A2]]): Managed[E, List[A2]] =
    ZManaged.sequence(ms)

  /**
   * See [[zio.ZManaged.sequencePar]]
   */
  def sequencePar[E, A](as: Iterable[Managed[E, A]]): Managed[E, List[A]] =
    ZManaged.sequencePar(as)

  /**
   * See [[zio.ZManaged.sequenceParN]]
   */
  def sequenceParN[E, A](n: Int)(as: Iterable[Managed[E, A]]): Managed[E, List[A]] =
    ZManaged.sequenceParN(n)(as)

  /**
   * See [[zio.ZManaged.succeed]]
   */
  def succeed[A](r: A): Managed[Nothing, A] =
    ZManaged.succeed(r)

  /**
   * See [[zio.ZManaged.suspend]]
   */
  def suspend[E, A](managed: => Managed[E, A]): Managed[E, A] =
    ZManaged.suspend(managed)

  /**
   * See [[zio.ZManaged.traverse]]
   */
  def traverse[E, A1, A2](as: Iterable[A1])(f: A1 => Managed[E, A2]): Managed[E, List[A2]] =
    ZManaged.traverse(as)(f)

  /**
   * See [[zio.ZManaged.traverse_]]
   */
  def traverse_[E, A](as: Iterable[A])(f: A => Managed[E, Any]): Managed[E, Unit] =
    ZManaged.traverse_(as)(f)

  /**
   * See [[zio.ZManaged.traversePar]]
   */
  def traversePar[E, A1, A2](as: Iterable[A1])(f: A1 => Managed[E, A2]): Managed[E, List[A2]] =
    ZManaged.traversePar(as)(f)

  /**
   * See [[zio.ZManaged.traversePar_]]
   */
  def traversePar_[E, A](as: Iterable[A])(f: A => Managed[E, Any]): Managed[E, Unit] =
    ZManaged.traversePar_(as)(f)

  /**
   * See [[zio.ZManaged.traverseParN]]
   */
  def traverseParN[E, A1, A2](n: Int)(as: Iterable[A1])(f: A1 => Managed[E, A2]): Managed[E, List[A2]] =
    ZManaged.traverseParN(n)(as)(f)

  /**
   * See [[zio.ZManaged.traverseParN_]]
   */
  def traverseParN_[E, A](n: Int)(as: Iterable[A])(f: A => Managed[E, Any]): Managed[E, Unit] =
    ZManaged.traverseParN_(n)(as)(f)

  /**
   * See [[zio.ZManaged.unit]]
   */
  final val unit: Managed[Nothing, Unit] = ZManaged.unit

  /**
   * See [[zio.ZManaged.unsandbox]]
   */
  def unsandbox[E, A](v: Managed[Cause[E], A]): Managed[E, A] =
    ZManaged.unsandbox(v)

  /**
   * See [[zio.ZManaged.unwrap]]
   */
  def unwrap[E, A](fa: IO[E, Managed[E, A]]): Managed[E, A] =
    ZManaged.unwrap(fa)

  /**
   * See [[zio.ZManaged.when]]
   */
  def when[E](b: Boolean)(managed: Managed[E, Any]): Managed[E, Unit] =
    ZManaged.when(b)(managed)

  /**
   * See [[zio.ZManaged.whenCase]]
   */
  def whenCase[R, E, A](a: A)(pf: PartialFunction[A, ZManaged[R, E, Any]]): ZManaged[R, E, Unit] =
    ZManaged.whenCase(a)(pf)

  /**
   * See [[zio.ZManaged.whenCaseM]]
   */
  def whenCaseM[R, E, A](
    a: ZManaged[R, E, A]
  )(pf: PartialFunction[A, ZManaged[R, E, Any]]): ZManaged[R, E, Unit] =
    ZManaged.whenCaseM(a)(pf)

  /**
   * See [[zio.ZManaged.whenM]]
   */
  def whenM[E](b: Managed[E, Boolean])(managed: Managed[E, Any]): Managed[E, Unit] =
    ZManaged.whenM(b)(managed)

}
