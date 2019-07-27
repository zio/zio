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

case class TestEnvironment(
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

object TestEnvironment {

  val Value: Managed[Nothing, TestEnvironment] =
    Managed.fromEffect {
      for {
        bootstrap <- ZIO.effectTotal(PlatformLive.fromExecutionContext(ExecutionContext.global))
        clock     <- Ref.make(TestClock.DefaultData).map(TestClock(_))
        console   <- Ref.make(TestConsole.DefaultData).map(TestConsole(_))
        random    <- Ref.make(TestRandom.DefaultData).map(TestRandom(_))
        scheduler = TestScheduler(clock.ref, Runtime(Clock(clock), bootstrap))
        system    <- Ref.make(TestSystem.DefaultData).map(TestSystem(_))
        blocking  = Blocking.Live.blocking
      } yield new TestEnvironment(clock, console, random, scheduler, system, blocking)
    }

  object testConsole {
    def feedLines(lines: String*): ZIO[TestEnvironment, Nothing, Unit] =
      ZIO.accessM(_.console.ref.update(data => data.copy(input = lines.toList ::: data.input)).unit)
    val output: ZIO[TestEnvironment, Nothing, Vector[String]] =
      ZIO.accessM(_.console.ref.get.map(_.output))
  }

  private def Clock(testClock: TestClock): Clock = new Clock {
    val clock = testClock
  }
}
