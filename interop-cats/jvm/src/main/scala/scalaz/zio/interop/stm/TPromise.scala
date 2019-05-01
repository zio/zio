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

import scalaz.zio.stm.{ TPromise => ZTPromise }

class TPromise[F[+ _], E <: Throwable, A] private (underlying: ZTPromise[E, A]) {
  final def await: STM[F, A] = new STM(underlying.await)

  final def done(v: Either[E, A]): STM[F, Boolean] = new STM(underlying.done(v))

  final def fail(e: E): STM[F, Boolean] =
    done(Left(e))

  /**
   * Switch from effect F to effect G.
   */
  def mapK[G[+ _]]: TPromise[G, E, A] = new TPromise(underlying)

  final def poll: STM[F, Option[STM[F, A]]] = new STM(underlying.poll.map(_.map(new STM(_))))

  final def succeed(a: A): STM[F, Boolean] =
    done(Right(a))
}

object TPromise {
  final def make[F[+ _], E <: Throwable, A]: STM[F, TPromise[F, E, A]] =
    new STM(ZTPromise.make[E, A].map(new TPromise(_)))
}
