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

import zio.{ DefaultRuntime, Managed }
import zio.blocking.Blocking
import zio.scheduler.Scheduler
import zio.test.Sized

case class MockEnvironment(
  blocking: Blocking.Service[Any],
  clock: MockClock.Mock,
  console: MockConsole.Mock,
  live: Live.Service[DefaultRuntime#Environment],
  random: MockRandom.Mock,
  scheduler: MockClock.Mock,
  sized: Sized.Service[Any],
  system: MockSystem.Mock
) extends Blocking
    with Live[DefaultRuntime#Environment]
    with MockClock
    with MockConsole
    with MockRandom
    with MockSystem
    with Scheduler
    with Sized

object MockEnvironment {

  val Value: Managed[Nothing, MockEnvironment] =
    Managed.fromEffect {
      for {
        clock    <- MockClock.makeMock(MockClock.DefaultData)
        console  <- MockConsole.makeMock(MockConsole.DefaultData)
        live     <- Live.makeService(new DefaultRuntime {}.Environment)
        random   <- MockRandom.makeMock(MockRandom.DefaultData)
        size     <- Sized.makeService(100)
        system   <- MockSystem.makeMock(MockSystem.DefaultData)
        blocking = Blocking.Live.blocking
      } yield new MockEnvironment(blocking, clock, console, live, random, clock, size, system)
    }
}
