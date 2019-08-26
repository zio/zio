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

import zio.{ DefaultRuntime, IO, UIO, ZIO }

trait Live[+R] {
  def live: Live.Service[R]
}

object Live {

  trait Service[+R] {
    def provide[E, A](zio: ZIO[R, E, A]): IO[E, A]
  }

  def live[R, E, A](zio: ZIO[R, E, A]): ZIO[Live[R], E, A] =
    ZIO.accessM[Live[R]](_.live.provide(zio))

  def make: UIO[Live[DefaultRuntime#Environment]] =
    makeService.map { service =>
      new Live[DefaultRuntime#Environment] {
        val live = service
      }
    }

  def makeService: UIO[Live.Service[DefaultRuntime#Environment]] =
    UIO.succeed {
      val runtime     = new DefaultRuntime {}
      val environment = runtime.Environment
      new Live.Service[DefaultRuntime#Environment] {
        def provide[E, A](zio: ZIO[DefaultRuntime#Environment, E, A]): IO[E, A] =
          zio.provide(environment)
      }
    }
}
