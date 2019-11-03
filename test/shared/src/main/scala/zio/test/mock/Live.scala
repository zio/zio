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

package zio.test.mock

import zio.{ IO, UIO, ZIO }

trait Live[+R] {
  def live: Live.Service[R]
}

object Live {

  trait Service[+R] {
    def provide[E, A](zio: ZIO[R, E, A]): IO[E, A]
  }

  def live[R, E, A](zio: ZIO[R, E, A]): ZIO[Live[R], E, A] =
    ZIO.accessM[Live[R]](_.live.provide(zio))

  def make[R](r: R): UIO[Live[R]] =
    makeService(r).map { service =>
      new Live[R] {
        val live = service
      }
    }

  def makeService[R](r: R): UIO[Live.Service[R]] =
    UIO.succeed {
      new Live.Service[R] {
        def provide[E, A](zio: ZIO[R, E, A]): IO[E, A] =
          zio.provide(r)
      }
    }

  def withLive[R, R1, E, E1, A, B](zio: ZIO[R, E, A])(f: IO[E, A] => ZIO[R1, E1, B]): ZIO[R with Live[R1], E1, B] =
    ZIO.environment[R].flatMap(r => live(f(zio.provide(r))))
}
