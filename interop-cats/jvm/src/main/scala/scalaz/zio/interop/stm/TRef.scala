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
import scalaz.zio.stm.{ TRef => ZTRef }

class TRef[F[+ _], A] private (underlying: ZTRef[A])(implicit liftIO: ZIO[Any, Throwable, ?] ~> F) {
  self =>

  /**
   * Retrieves the value of the `TRef`.
   */
  final val get: STM[F, A] = new STM(underlying.get)

  /**
   * Sets the value of the `TRef`.
   */
  final def set(newValue: A): STM[F, Unit] = new STM(underlying.set(newValue))

  override final def toString = underlying.toString

  /**
   * Updates the value of the variable.
   */
  final def update(f: A => A): STM[F, A] = new STM(underlying.update(f))

  /**
   * Updates some values of the variable but leaves others alone.
   */
  final def updateSome(f: PartialFunction[A, A]): STM[F, A] = new STM(underlying.updateSome(f))

  /**
   * Updates the value of the variable, returning a function of the specified
   * value.
   */
  final def modify[B](f: A => (B, A)): STM[F, B] = new STM(underlying.modify(f))

  /**
   * Updates some values of the variable, returning a function of the specified
   * value or the default.
   */
  final def modifySome[B](default: B)(f: PartialFunction[A, (B, A)]): STM[F, B] =
    new STM(underlying.modifySome(default)(f))

  def mapK[G[+ _]](f: F ~> G): TRef[G, A] = new TRef(underlying)(f compose liftIO)
}

object TRef {

  /**
   * Makes a new `TRef` that is initialized to the specified value.
   */
  final def make[F[+ _], A](a: => A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, TRef[F, A]] =
    new STM(ZTRef.make(a).map(new TRef(_)))

  /**
   * A convenience method that makes a `TRef` and immediately commits the
   * transaction to extract the value out.
   */
  final def makeCommit[F[+ _], A](a: => A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): F[TRef[F, A]] =
    STM.atomically(make(a))
}
