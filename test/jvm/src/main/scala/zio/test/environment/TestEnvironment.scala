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
import zio.scheduler.Scheduler
import zio.test.Annotated
import zio.test.Sized

case class TestEnvironment(
  annotated: Annotated.Service[Any],
  blocking: Blocking.Service[Any],
  clock: TestClock.Service[Any],
  console: TestConsole.Service[Any],
  live: Live.Service[ZEnv],
  random: TestRandom.Service[Any],
  sized: Sized.Service[Any],
  system: TestSystem.Service[Any]
) extends Annotated
    with Blocking
    with Live[ZEnv]
    with Scheduler
    with Sized
    with TestClock
    with TestConsole
    with TestRandom
    with TestSystem {

  /**
   * Maps all test implementations in the test environment individually.
   */
  final def mapAll(
    mapTestClock: TestClock.Service[Any] => TestClock.Service[Any] = identity,
    mapTestConsole: TestConsole.Service[Any] => TestConsole.Service[Any] = identity,
    mapTestRandom: TestRandom.Service[Any] => TestRandom.Service[Any] = identity,
    mapTestSystem: TestSystem.Service[Any] => TestSystem.Service[Any] = identity
  ): TestEnvironment =
    TestEnvironment(
      annotated,
      blocking,
      mapTestClock(clock),
      mapTestConsole(console),
      live,
      mapTestRandom(random),
      sized,
      mapTestSystem(system)
    )

  /**
   * Maps the [[TestClock]] implementation in the test environment, leaving
   * all other test implementations the same.
   */
  final def mapTestClock(f: TestClock.Service[Any] => TestClock.Service[Any]): TestEnvironment =
    mapAll(mapTestClock = f)

  /**
   * Maps the [[TestConsole]] implementation in the test environment, leaving
   * all other test implementations the same.
   */
  final def mapTestConsole(f: TestConsole.Service[Any] => TestConsole.Service[Any]): TestEnvironment =
    mapAll(mapTestConsole = f)

  /**
   * Maps the [[TestRandom]] implementation in the test environment, leaving
   * all other test implementations the same.
   */
  final def mapTestRandom(f: TestRandom.Service[Any] => TestRandom.Service[Any]): TestEnvironment =
    mapAll(mapTestRandom = f)

  /**
   * Maps the [[TestSystem]] implementation in the test environment, leaving
   * all other test implementations the same.
   */
  final def mapTestSystem(f: TestSystem.Service[Any] => TestSystem.Service[Any]): TestEnvironment =
    mapAll(mapTestSystem = f)

  val scheduler = clock
}

object TestEnvironment extends Serializable {

  val Value: Managed[Nothing, TestEnvironment] =
    for {
      live      <- Live.makeService(LiveEnvironment).toManaged_
      annotated <- Annotated.makeService.toManaged_
      blocking  = Blocking.Live.blocking
      clock     <- TestClock.makeTest(TestClock.DefaultData, Some(live))
      console   <- TestConsole.makeTest(TestConsole.DefaultData).toManaged_
      random    <- TestRandom.makeTest(TestRandom.DefaultData).toManaged_
      sized     <- Sized.makeService(100).toManaged_
      system    <- TestSystem.makeTest(TestSystem.DefaultData).toManaged_
      time      <- live.provide(zio.clock.nanoTime).toManaged_
      _         <- random.setSeed(time).toManaged_
    } yield new TestEnvironment(annotated, blocking, clock, console, live, random, sized, system)
}
