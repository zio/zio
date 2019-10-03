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

import zio.{ DefaultRuntime, Managed }
import zio.blocking.Blocking
import zio.scheduler.Scheduler
import zio.test.Sized

case class TestEnvironment(
  blocking: Blocking.Service[Any],
  clock: TestClock.Test,
  console: TestConsole.Test,
  live: Live.Service[DefaultRuntime#Environment],
  random: TestRandom.Test,
  scheduler: TestClock.Test,
  sized: Sized.Service[Any],
  system: TestSystem.Test
) extends Blocking
    with Live[DefaultRuntime#Environment]
    with TestClock
    with TestConsole
    with TestRandom
    with TestSystem
    with Scheduler
    with Sized

object TestEnvironment {

  val Value: Managed[Nothing, TestEnvironment] =
    Managed.fromEffect {
      for {
        clock    <- TestClock.makeTest(TestClock.DefaultData)
        console  <- TestConsole.makeTest(TestConsole.DefaultData)
        live     <- Live.makeService(new DefaultRuntime {}.Environment)
        random   <- TestRandom.makeTest(TestRandom.DefaultData)
        time     <- live.provide(zio.clock.nanoTime)
        _        <- random.setSeed(time)
        size     <- Sized.makeService(100)
        system   <- TestSystem.makeTest(TestSystem.DefaultData)
        blocking = Blocking.Live.blocking
      } yield new TestEnvironment(blocking, clock, console, live, random, clock, size, system)
    }
}
