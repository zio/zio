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

object Schedule {

  /**
   * See [[ZSchedule.forever]]
   */
  final val forever: Schedule[Any, Int] = ZSchedule.forever

  /**
   * See [[ZSchedule.never]]
   */
  final val never: Schedule[Any, Nothing] =
    ZSchedule.never

  /**
   * See [[ZSchedule.once]]
   */
  final val once: Schedule[Any, Unit] = ZSchedule.once

  final def apply[S, A, B](
    initial0: UIO[S],
    update0: (A, S) => UIO[S],
    extract0: (A, S) => B
  ): Schedule[A, B] =
    ZSchedule(initial0, update0, extract0)

  /**
   * See [[ZSchedule.collectAll]]
   */
  final def collectAll[A]: Schedule[A, List[A]] = ZSchedule.collectAll

  /**
   * See [[ZSchedule.collectWhile]]
   */
  final def collectWhile[A](f: A => Boolean): Schedule[A, List[A]] = ZSchedule.collectWhile(f)

  /**
   * See [[ZSchedule.collectWhileM]]
   */
  final def collectWhileM[A](f: A => UIO[Boolean]): Schedule[A, List[A]] = ZSchedule.collectWhileM(f)

  /**
   * See [[ZSchedule.collectUntil]]
   */
  final def collectUntil[A](f: A => Boolean): Schedule[A, List[A]] = ZSchedule.collectUntil(f)

  /**
   * See [[ZSchedule.collectUntilM]]
   */
  final def collectUntilM[A](f: A => UIO[Boolean]): Schedule[A, List[A]] = ZSchedule.collectUntilM(f)

  /**
   * See [[ZSchedule.doWhile]]
   */
  final def doWhile[A](f: A => Boolean): Schedule[A, A] =
    ZSchedule.doWhile(f)

  /**
   * See [[ZSchedule.doWhileM]]
   */
  final def doWhileM[A](f: A => UIO[Boolean]): Schedule[A, A] =
    ZSchedule.doWhileM(f)

  /**
   * See [[ZSchedule.doWhileEquals]]
   */
  final def doWhileEquals[A](a: A): Schedule[A, A] =
    ZSchedule.doWhileEquals(a)

  /**
   * See [[[ZSchedule.doUntil[A](f:* ZSchedule.doUntil]]]
   */
  final def doUntil[A](f: A => Boolean): Schedule[A, A] =
    ZSchedule.doUntil(f)

  /**
   * See [[ZSchedule.doUntilM]]
   */
  final def doUntilM[A](f: A => UIO[Boolean]): Schedule[A, A] =
    ZSchedule.doUntilM(f)

  /**
   * See [[ZSchedule.doUntilEquals]]
   */
  final def doUntilEquals[A](a: A): Schedule[A, A] =
    ZSchedule.doUntilEquals(a)

  /**
   * See [[ZSchedule.doUntil[A,B](pf:* ZSchedule.doUntil]]]
   */
  final def doUntil[A, B](pf: PartialFunction[A, B]): Schedule[A, Option[B]] =
    ZSchedule.doUntil(pf)

  /**
   * See [[ZSchedule.fromFunction]]
   */
  final def fromFunction[A, B](f: A => B): Schedule[A, B] = ZSchedule.fromFunction(f)

  /**
   * See [[ZSchedule.identity]]
   */
  final def identity[A]: Schedule[A, A] =
    ZSchedule.identity

  /**
   * See [[ZSchedule.recurs]]
   */
  final def recurs(n: Int): Schedule[Any, Int] = ZSchedule.recurs(n)

  /**
   * See [[ZSchedule.stop]]
   */
  final val stop: Schedule[Any, Unit] =
    ZSchedule.stop

  /**
   * See [[ZSchedule.succeed]]
   */
  final def succeed[A](a: A): Schedule[Any, A] = ZSchedule.succeed(a)

  @deprecated("use succeed", "1.0.0")
  final def succeedLazy[A](a: => A): Schedule[Any, A] =
    succeed(a)

  /**
   * See [[ZSchedule.tapInput]]
   */
  final def tapInput[A](f: A => UIO[Unit]): Schedule[A, A] =
    ZSchedule.tapInput(f)

  /**
   * See [[ZSchedule.tapOutput]]
   */
  final def tapOutput[A](f: A => UIO[Unit]): Schedule[A, A] =
    ZSchedule.tapOutput(f)

  /**
   * See [[ZSchedule.unfold]]
   */
  final def unfold[A](a: => A)(f: A => A): Schedule[Any, A] =
    ZSchedule.unfold(a)(f)

  /**
   * See [[ZSchedule.unfoldM]]
   */
  final def unfoldM[A](a: UIO[A])(f: A => UIO[A]): Schedule[Any, A] =
    ZSchedule.unfoldM(a)(f)

}
