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

import scala.concurrent.ExecutionContext

import zio._
import zio.blocking.Blocking
import zio.internal.PlatformLive
import zio.scheduler.Scheduler

case class MockEnvironment(
  clock: MockClock.Mock,
  console: MockConsole.Mock,
  random: MockRandom.Mock,
  scheduler: MockScheduler,
  system: MockSystem.Mock,
  blocking: Blocking.Service[Any]
) extends Blocking
    with MockClock
    with MockConsole
    with MockRandom
    with Scheduler
    with MockSystem

object MockEnvironment {

  val Value: Managed[Nothing, MockEnvironment] =
    Managed.fromEffect {
      for {
        bootstrap <- ZIO.effectTotal(PlatformLive.fromExecutionContext(ExecutionContext.global))
        clock     <- MockClock.makeMock(MockClock.DefaultData)
        console   <- MockConsole.makeMock(MockConsole.DefaultData)
        random    <- MockRandom.makeMock(MockRandom.DefaultData)
        scheduler = MockScheduler(clock.clockState, Runtime(Clock(clock), bootstrap))
        system    <- MockSystem.makeMock(MockSystem.DefaultData)
        blocking  = Blocking.Live.blocking
      } yield new MockEnvironment(clock, console, random, scheduler, system, blocking)
    }

  private def Clock(mockClock: MockClock.Mock): MockClock = new MockClock {
    val clock = mockClock
  }
}
