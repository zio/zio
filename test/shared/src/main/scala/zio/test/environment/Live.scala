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

package zio.test.environment

import zio.{ IO, UIO, ZIO }

/**
 * The `Live` trait provides access to the "live" environment from within the
 * test environment for effects such as printing test results to the console or
 * timing out tests where it is necessary to access the real environment.
 *
 * The easiest way to access the "live" environment is to use the `live` method
 * with an effect that would otherwise access the test environment.
 *
 * {{{
 * import zio.clock
 * import zio.test.environment._
 *
 * val realTime = live(clock.nanoTime)
 * }}}
 *
 * The `withLive` method can be used to apply a transformation to an effect
 * with the live environment while ensuring that the effect itself still runs
 * with the test environment, for example to time out a test. Both of these
 * methods are re-exported in the `environment` package for easy availability.
 */
trait Live[+R] {
  def live: Live.Service[R]
}

object Live {

  trait Service[+R] {
    def provide[E, A](zio: ZIO[R, E, A]): IO[E, A]
  }

  /**
   * Provides an effect with the "live" environment.
   */
  def live[R, E, A](zio: ZIO[R, E, A]): ZIO[Live[R], E, A] =
    ZIO.accessM[Live[R]](_.live.provide(zio))

  /**
   * Constructs a new `Live` instance with an environment `R`. This should
   * typically not be necessary as `TestEnvironment` provides access to live
   * versions of all the standard ZIO environmental effects but could be useful
   * if you are creating your own test interfaces and want to be able to access
   * the live version of these interfaces at the same time.
   */
  def make[R](r: R): UIO[Live[R]] =
    makeService(r).map { service =>
      new Live[R] {
        val live = service
      }
    }

  /**
   * Constructs a new `Live` service that implements the `Live` interface.
   * This typically should not be necessary as `TestEnvironment` provides
   * access to live versions of all the standard ZIO environment types but
   * could be useful if you are mixing in interfaces to create your own
   * environment type.
   */
  def makeService[R](r: R): UIO[Live.Service[R]] =
    UIO.succeed {
      new Live.Service[R] {
        def provide[E, A](zio: ZIO[R, E, A]): IO[E, A] =
          zio.provide(r)
      }
    }

  /**
   * Provides a transformation function with access to the live environment
   * while ensuring that the effect itself is provided with the test
   * environment.
   */
  def withLive[R, R1, E, E1, A, B](zio: ZIO[R, E, A])(f: IO[E, A] => ZIO[R1, E1, B]): ZIO[R with Live[R1], E1, B] =
    ZIO.environment[R].flatMap(r => live(f(zio.provide(r))))
}
