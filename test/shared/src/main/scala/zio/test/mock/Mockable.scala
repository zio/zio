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

package zio.test.mock

import zio.Has
import zio.clock.Clock
import zio.console.Console
import zio.random.Random
import zio.system.System

/**
 * The `Mockable[R]` represents a mock environment builder used by the mock
 * framework to construct a mock implementation from a mock.
 */
trait Mockable[R <: Has[_]] {

  /**
   * Provided a mock constructs a mock implementation for environment `R`.
   */
  def environment(mock: Mock): R
}

object Mockable {

  implicit val mockableSystem: Mockable[System]   = MockSystem.mockable
  implicit val mockableClock: Mockable[Clock]     = MockClock.mockable
  implicit val mockableConsole: Mockable[Console] = MockConsole.mockable
  implicit val mockableRandom: Mockable[Random]   = MockRandom.mockable
}
