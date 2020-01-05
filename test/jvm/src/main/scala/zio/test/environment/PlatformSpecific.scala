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

import zio.{ Managed, ZEnv }
import zio.blocking.Blocking
import zio.test.Annotations
import zio.test.Sized

trait PlatformSpecific {
  type TestEnvironment =
    Annotations with Blocking with TestClock with TestConsole with Live with TestRandom with Sized with TestSystem

  val testEnvironmentManaged: Managed[Nothing, TestEnvironment] =
    (ZEnv.live >>>
      (Annotations.live ++ Blocking.live ++ (Live.make >>> TestClock.default) ++ TestConsole.default ++ Live.make ++ TestRandom.default ++ Sized
        .live(100) ++ TestSystem.live)).build
}
