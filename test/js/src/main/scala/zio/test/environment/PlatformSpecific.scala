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

import zio._
import zio.test.Annotations
import zio.test.Sized

trait PlatformSpecific {
  type TestEnvironment =
    Annotations with TestClock with TestConsole with Live with TestRandom with Sized with TestSystem

  val testEnvironmentManaged: Managed[Nothing, TestEnvironment] = {
    val annotations = Annotations.makeService
    val live        = Live.makeService
    val testClock   = live >>> TestClock.make(TestClock.DefaultData)
    val testConsole = TestConsole.makeTest(TestConsole.DefaultData)
    val testRandom  = TestRandom.make(TestRandom.DefaultData)
    val sized       = Sized.makeService(100)
    val testSystem  = TestSystem.make(TestSystem.DefaultData)

    val whole =
      ZEnv.live >>>
        (annotations ++ testClock ++ testConsole ++ live ++ testRandom ++ sized ++ testSystem)

    whole.build
  }
}
