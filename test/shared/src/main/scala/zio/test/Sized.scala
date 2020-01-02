/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

import zio.{ FiberRef, UIO, ZIO }

trait Sized {
  def sized: Sized.Service[Any]
}

object Sized {

  trait Service[R] {
    val size: UIO[Int]
    def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A]
  }

  def make(size: Int): UIO[Sized] =
    makeService(size).map { service =>
      new Sized {
        val sized = service
      }
    }

  def makeService(size: Int): UIO[Sized.Service[Any]] =
    FiberRef.make(size).map { fiberRef =>
      new Sized.Service[Any] {
        val size: UIO[Int] =
          fiberRef.get
        def withSize[R, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
          fiberRef.locally(size)(zio)
      }
    }

  val size: ZIO[Sized, Nothing, Int] =
    ZIO.accessM[Sized](_.sized.size)

  def withSize[R <: Sized, E, A](size: Int)(zio: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.accessM[R](_.sized.withSize(size)(zio))
}
