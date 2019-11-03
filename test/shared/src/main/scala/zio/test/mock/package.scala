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

package zio.test

import zio.{ IO, ZIO }

import zio.Managed

package object mock {

  /**
   * Provides an effect with the "real" environment as opposed to the mock
   * environment. This is useful for performing effects such as timing out
   * tests, accessing the real time, or printing to the real console.
   */
  def live[R, E, A](zio: ZIO[R, E, A]): ZIO[Live[R], E, A] =
    Live.live(zio)

  /**
   * Transforms this effect with the specified function. The mock environment
   * will be provided to this effect, but the live environment will be provided
   * to the transformation function. This can be useful for applying
   * transformations to an effect that require access to the "real" environment
   * while ensuring that the effect itself uses the mock environment.
   *
   * {{{
   *  withLive(test)(_.timeout(duration))
   * }}}
   */
  def withLive[R, R1, E, E1, A, B](zio: ZIO[R, E, A])(f: IO[E, A] => ZIO[R1, E1, B]): ZIO[R with Live[R1], E1, B] =
    Live.withLive(zio)(f)

  val mockEnvironmentManaged: Managed[Nothing, MockEnvironment] = MockEnvironment.Value
}
