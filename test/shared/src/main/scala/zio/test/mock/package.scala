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

/**
 * The `mock` package contains testable versions of all the standard ZIO
 * environment types through the [[MockClock]], [[MockConsole]],
 * [[MockSystem]], and [[MockRandom]] modules. See the documentation on the
 * individual modules for more detail about using each of them.
 *
 * If you are using ZIO Test and extending `DefaultRunnableSpec` a
 * `MockEnvironment` containing all of them will be automatically provided to
 * each of your tests. Otherwise, the easiest way to use the mocking
 * functionality in ZIO Test is by providing the `MockEnvironment` to your
 * program.
 *
 * {{{
 * import zio.test.mock._
 *
 * myProgram.provideManaged(mockEnvironmentManaged)
 * }}}
 *
 * Then all environmental effects, such as printing to the console or
 * generating random numbers, will be implemented by the `MockEnvironment` and
 * will be fully testable. When you do need to access the "live" environment,
 * for example to print debugging information to the close, just use the `live`
 * combinator along with the effect as your normally would.
 *
 * If you are only interested in one of the mocking modules for your
 * application, you can also access them a la carte through the `make` method
 * on each module. Each mock module requires some data on initialization.
 * Default data is included for each as `DefaultData`.
 *
 * {{{
 * import zio.test.mock._
 *
 * myProgram.provideM(MockConsole.make(MockConsole.DefaultData))
 * }}}
 *
 * Finally, you can create a `Mock` object that implements the mock interface
 * directly using the `makeMock` method. This can be useful when you want to
 * access some mocking functionality without using the environment type.
 *
 * {{{
 * import zio.test.mock._
 *
 * for {
 *   mockRandom <- MockRandom.makeMock(MockRandom.DefaultData)
 *   n          <- mockRandom.nextInt
 * } yield n
 * }}}
 *
 * This can also be useful when you are creating a more complex environment
 * to provide the implementation for mock services that you mix in.
 */
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

  /**
   * A managed version of the `MockEnvironment` containing testable versions of
   * all the standard ZIO environmental effects.
   */
  val mockEnvironmentManaged: Managed[Nothing, MockEnvironment] = MockEnvironment.Value
}
