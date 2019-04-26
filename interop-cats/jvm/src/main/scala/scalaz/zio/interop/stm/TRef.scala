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
   * See [[scalaz.zio.stm.TRef#get]]
   */
  final val get: STM[F, A] = new STM(underlying.get)

  /**
   * See [[scalaz.zio.stm.TRef#set]]
   */
  final def set(newValue: A): STM[F, Unit] = new STM(underlying.set(newValue))

  override final def toString = underlying.toString

  /**
   * See [[scalaz.zio.stm.TRef#update]]
   */
  final def update(f: A => A): STM[F, A] = new STM(underlying.update(f))

  /**
   * See [[scalaz.zio.stm.TRef#updateSome]]
   */
  final def updateSome(f: PartialFunction[A, A]): STM[F, A] = new STM(underlying.updateSome(f))

  /**
   * See [[scalaz.zio.stm.TRef#modify]]
   */
  final def modify[B](f: A => (B, A)): STM[F, B] = new STM(underlying.modify(f))

  /**
   * See [[scalaz.zio.stm.TRef#modifySome]]
   */
  final def modifySome[B](default: B)(f: PartialFunction[A, (B, A)]): STM[F, B] =
    new STM(underlying.modifySome(default)(f))

  /**
   * Switch from effect F to effect G using transformation `f`.
   */
  def mapK[G[+ _]](f: F ~> G): TRef[G, A] = new TRef(underlying)(f compose liftIO)
}

object TRef {

  final def make[F[+ _], A](a: => A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): STM[F, TRef[F, A]] =
    new STM(ZTRef.make(a).map(new TRef(_)))

  final def makeCommit[F[+ _], A](a: => A)(implicit liftIO: ZIO[Any, Throwable, ?] ~> F): F[TRef[F, A]] =
    STM.atomically(make(a))
}
