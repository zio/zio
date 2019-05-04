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

import scalaz.zio.stm.{ TQueue => ZTQueue }

/**
 * See [[scalaz.zio.stm.TQueue]]
 */
class TQueue[F[+ _], A] private (val underlying: ZTQueue[A]) extends AnyVal {

  /**
   * Switch from effect F to effect G.
   */
  def mapK[G[+ _]]: TQueue[G, A] = new TQueue(underlying)

  /**
   * See [[scalaz.zio.stm.TQueue#offer]]
   */
  final def offer(a: A): STM[F, Unit] = new STM(underlying.offer(a))

  /**
   * See [[scalaz.zio.stm.TQueue#offerAll]]
   */
  final def offerAll(as: List[A]): STM[F, Unit] = new STM(underlying.offerAll(as))

  /**
   * See [[scalaz.zio.stm.TQueue#poll]]
   */
  final def poll: STM[F, Option[A]] = new STM(underlying.poll)

  /**
   * See [[scalaz.zio.stm.TQueue#size]]
   */
  final def size: STM[F, Int] = new STM(underlying.size)

  /**
   * See [[scalaz.zio.stm.TQueue#take]]
   */
  final def take: STM[F, A] = new STM(underlying.take)

  /**
   * See [[scalaz.zio.stm.TQueue#takeAll]]
   */
  final def takeAll: STM[F, List[A]] = new STM(underlying.takeAll)

  /**
   * See [[scalaz.zio.stm.TQueue#takeUpTo]]
   */
  final def takeUpTo(max: Int): STM[F, List[A]] = new STM(underlying.takeUpTo(max))
}

object TQueue {
  final def make[F[+ _], A](capacity: Int): STM[F, TQueue[F, A]] =
    new STM(ZTQueue.make[A](capacity).map(new TQueue(_)))
}
