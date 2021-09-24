/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import zio.{PlatformSpecific => _, _}

/**
 * The `environment` package contains testable versions of all the standard ZIO
 * environment types through the [[TestClock]], [[TestConsole]],
 * [[TestSystem]], and [[TestRandom]] modules. See the documentation on the
 * individual modules for more detail about using each of them.
 *
 * If you are using ZIO Test and extending `RunnableSpec` a
 * `TestEnvironment` containing all of them will be automatically provided to
 * each of your tests. Otherwise, the easiest way to use the test implementations
 * in ZIO Test is by providing the `TestEnvironment` to your program.
 *
 * {{{
 * import zio.test.environment._
 *
 * myProgram.provideLayer(testEnvironment)
 * }}}
 *
 * Then all environmental effects, such as printing to the console or
 * generating random numbers, will be implemented by the `TestEnvironment` and
 * will be fully testable. When you do need to access the "live" environment,
 * for example to print debugging information to the console, just use the
 * `live` combinator along with the effect as your normally would.
 *
 * If you are only interested in one of the test implementations for your
 * application, you can also access them a la carte through the `make` method
 * on each module. Each test module requires some data on initialization.
 * Default data is included for each as `DefaultData`.
 *
 * {{{
 * import zio.test.environment._
 *
 * myProgram.provideM(TestConsole.make(TestConsole.DefaultData))
 * }}}
 *
 * Finally, you can create a `Test` object that implements the test interface
 * directly using the `makeTest` method. This can be useful when you want to
 * access some testing functionality without using the environment type.
 *
 * {{{
 * import zio.test.environment._
 *
 * for {
 *   testRandom <- TestRandom.makeTest(TestRandom.DefaultData)
 *   n          <- testRandom.nextInt
 * } yield n
 * }}}
 *
 * This can also be useful when you are creating a more complex environment
 * to provide the implementation for test services that you mix in.
 */
package object environment extends PlatformSpecific {
  val liveEnvironment: Layer[Nothing, ZEnv] = ZEnv.live

  val testEnvironment: Layer[Nothing, TestEnvironment] =
    ZEnv.live >>> TestEnvironment.live

  /**
   * Provides an effect with the "real" environment as opposed to the test
   * environment. This is useful for performing effects such as timing out
   * tests, accessing the real time, or printing to the real console.
   */
  def live[E, A](zio: ZIO[ZEnv, E, A]): ZIO[Has[Live], E, A] =
    Live.live(zio)

  /**
   * Transforms this effect with the specified function. The test environment
   * will be provided to this effect, but the live environment will be provided
   * to the transformation function. This can be useful for applying
   * transformations to an effect that require access to the "real" environment
   * while ensuring that the effect itself uses the test environment.
   *
   * {{{
   *  withLive(test)(_.timeout(duration))
   * }}}
   */
  def withLive[R, E, E1, A, B](
    zio: ZIO[R, E, A]
  )(f: IO[E, A] => ZIO[ZEnv, E1, B]): ZIO[R with Has[Live], E1, B] =
    Live.withLive(zio)(f)
}
