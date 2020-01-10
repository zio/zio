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

import java.io.IOException

package object blocking {
  type Blocking = Has[Blocking.Service]

  def blocking[R <: Blocking, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.accessM[R](_.get.blocking(zio))

  def effectBlocking[A](effect: => A): ZIO[Blocking, Throwable, A] =
    ZIO.accessM[Blocking](_.get.effectBlocking(effect))

  def effectBlockingIO[A](effect: => A): ZIO[Blocking, IOException, A] =
    effectBlocking(effect).refineToOrDie[IOException]

  def effectBlockingCancelable[A](effect: => A)(cancel: UIO[Unit]): ZIO[Blocking, Throwable, A] =
    ZIO.accessM[Blocking](_.get.effectBlockingCancelable(effect)(cancel))
}
