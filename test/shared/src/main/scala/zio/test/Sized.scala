/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.{ FiberRef, Has, UIO, ZIO, ZLayer }

object Sized {
  trait Service extends Serializable {
    val size: UIO[Int]
    def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A]
  }

  def live(size: Int): ZLayer.NoDeps[Nothing, Sized] =
    ZLayer.fromEffect(FiberRef.make(size).map { fiberRef =>
      Has(new Sized.Service {
        val size: UIO[Int] =
          fiberRef.get
        def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
          fiberRef.locally(size)(zio)
      })
    })

  val size: ZIO[Sized, Nothing, Int] =
    ZIO.accessM[Sized](_.get.size)

  def withSize[R <: Sized, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.accessM[R](_.get.withSize(size)(zio))
}
