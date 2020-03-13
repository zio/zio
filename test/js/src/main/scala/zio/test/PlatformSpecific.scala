/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import zio._
import zio.test.environment._

private[test] trait PlatformSpecific {
  type TestEnvironment =
    ZEnv with Annotations with TestClock with TestConsole with Live with TestRandom with Sized with TestSystem

  object TestEnvironment {
    val any: ZLayer[TestEnvironment, Nothing, TestEnvironment] =
      ZLayer.requires[TestEnvironment]
    val live: ZLayer[ZEnv, Nothing, TestEnvironment] =
      Annotations.live ++
        (Live.default >>> TestClock.default) ++
        (Live.default >>> TestConsole.debug) ++
        Live.default ++
        TestRandom.deterministic ++
        Sized.live(100) ++
        TestSystem.default
  }
}
