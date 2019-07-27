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
import zio.clock.Clock
import zio.console.Console
import zio.internal.PlatformLive
import zio.random.Random
import zio.scheduler.Scheduler
import zio.system.System

case class MockEnvironment(
  clock: TestClock,
  console: TestConsole,
  random: TestRandom,
  scheduler: TestScheduler,
  system: TestSystem,
  blocking: Blocking.Service[Any]
) extends Blocking
    with Clock
    with Console
    with Random
    with Scheduler
    with System

object MockEnvironment {

  val Value: Managed[Nothing, MockEnvironment] =
    Managed.fromEffect {
      for {
        bootstrap <- ZIO.effectTotal(PlatformLive.fromExecutionContext(ExecutionContext.global))
        clock     <- TestClock.make(TestClock.DefaultData)
        console   <- TestConsole.make(TestConsole.DefaultData)
        random    <- TestRandom.make(TestRandom.DefaultData)
        scheduler = TestScheduler(clock.clockState, Runtime(Clock(clock), bootstrap))
        system    <- TestSystem.make(TestSystem.DefaultData)
        blocking  = Blocking.Live.blocking
      } yield new MockEnvironment(clock, console, random, scheduler, system, blocking)
    }

  private def Clock(testClock: TestClock): Clock = new Clock {
    val clock = testClock
  }
}
