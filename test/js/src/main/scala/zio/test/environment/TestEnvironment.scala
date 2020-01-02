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

package zio.test.environment

import java.io.IOException
import java.time.{ OffsetDateTime, ZoneId }
import java.util.concurrent.TimeUnit

import zio._
import zio.duration._
import zio.internal.{ Scheduler => IScheduler }
import zio.scheduler.{ Scheduler, SchedulerLive }
import zio.test.Annotations
import zio.test.Sized

trait TestEnvironment
    extends Annotations
    with Live[ZEnv]
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
      annotations,
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

  /**
   * Maps the [[TestClock]] implementation in the test environment to one that
   * uses the live environment, leaving all other test implementations the
   * same.
   */
  final def withLiveClock: TestEnvironment =
    mapTestClock { _ =>
      new TestClock.Service[Any] {
        def adjust(duration: Duration): UIO[Unit]            = UIO.unit
        val currentDateTime: UIO[OffsetDateTime]             = live.provide(zio.clock.currentDateTime)
        def currentTime(unit: TimeUnit): UIO[Long]           = live.provide(zio.clock.currentTime(unit))
        val fiberTime: UIO[Duration]                         = UIO.succeed(Duration.Zero)
        val nanoTime: UIO[Long]                              = live.provide(zio.clock.nanoTime)
        val save: UIO[UIO[Unit]]                             = UIO.succeed(UIO.unit)
        def setDateTime(dateTime: OffsetDateTime): UIO[Unit] = UIO.unit
        def setTime(duration: Duration): UIO[Unit]           = UIO.unit
        def setTimeZone(zone: ZoneId): UIO[Unit]             = UIO.unit
        val scheduler: UIO[IScheduler]                        = SchedulerLive.scheduler.scheduler
        def sleep(duration: Duration): UIO[Unit]             = live.provide(zio.clock.sleep(duration))
        val sleeps: UIO[List[Duration]]                      = UIO.succeed(List.empty)
        val timeZone: UIO[ZoneId]                            = UIO.succeed(ZoneId.of("UTC"))
      }
    }

  /**
   * Maps the [[TestConsole]] implementation in the test environment to one
   * that uses the live environment, leaving all other test implementations the
   * same.
   */
  final def withLiveConsole: TestEnvironment =
    mapTestConsole { _ =>
      new TestConsole.Service[Any] {
        val clearInput: UIO[Unit]                = UIO.unit
        val clearOutput: UIO[Unit]               = UIO.unit
        def feedLines(lines: String*): UIO[Unit] = UIO.unit
        val getStrLn: IO[IOException, String]    = live.provide(zio.console.getStrLn)
        val output: UIO[Vector[String]]          = UIO.succeed(Vector.empty)
        def putStr(line: String): UIO[Unit]      = live.provide(zio.console.putStr(line))
        def putStrLn(line: String): UIO[Unit]    = live.provide(zio.console.putStrLn(line))
        val save: UIO[UIO[Unit]]                 = UIO.succeed(UIO.unit)
      }
    }

  /**
   * Maps the [[TestRandom]] implementation in the test environment to one that
   * uses the live environment, leaving all other test implementations the
   * same.
   */
  final def withLiveRandom: TestEnvironment =
    mapTestRandom { _ =>
      new TestRandom.Service[Any] {
        val clearBooleans: UIO[Unit]                    = UIO.unit
        val clearBytes: UIO[Unit]                       = UIO.unit
        val clearChars: UIO[Unit]                       = UIO.unit
        val clearDoubles: UIO[Unit]                     = UIO.unit
        val clearFloats: UIO[Unit]                      = UIO.unit
        val clearInts: UIO[Unit]                        = UIO.unit
        val clearLongs: UIO[Unit]                       = UIO.unit
        val clearStrings: UIO[Unit]                     = UIO.unit
        def feedBooleans(booleans: Boolean*): UIO[Unit] = UIO.unit
        def feedBytes(bytes: Chunk[Byte]*): UIO[Unit]   = UIO.unit
        def feedChars(chars: Char*): UIO[Unit]          = UIO.unit
        def feedDoubles(doubles: Double*): UIO[Unit]    = UIO.unit
        def feedFloats(floats: Float*): UIO[Unit]       = UIO.unit
        def feedInts(ints: Int*): UIO[Unit]             = UIO.unit
        def feedLongs(longs: Long*): UIO[Unit]          = UIO.unit
        def feedStrings(strings: String*): UIO[Unit]    = UIO.unit
        val nextBoolean: UIO[Boolean]                   = live.provide(zio.random.nextBoolean)
        def nextBytes(length: Int): UIO[Chunk[Byte]]    = live.provide(zio.random.nextBytes(length))
        val nextDouble: UIO[Double]                     = live.provide(zio.random.nextDouble)
        val nextFloat: UIO[Float]                       = live.provide(zio.random.nextFloat)
        val nextGaussian: UIO[Double]                   = live.provide(zio.random.nextGaussian)
        def nextInt(n: Int): UIO[Int]                   = live.provide(zio.random.nextInt(n))
        val nextInt: UIO[Int]                           = live.provide(zio.random.nextInt)
        val nextLong: UIO[Long]                         = live.provide(zio.random.nextLong)
        def nextLong(n: Long): UIO[Long]                = live.provide(zio.random.nextLong(n))
        val nextPrintableChar: UIO[Char]                = live.provide(zio.random.nextPrintableChar)
        def nextString(length: Int): UIO[String]        = live.provide(zio.random.nextString(length))
        val save: UIO[UIO[Unit]]                        = UIO.succeed(UIO.unit)
        def setSeed(seed: Long): UIO[Unit]              = UIO.unit
        def shuffle[A](list: List[A]): UIO[List[A]]     = UIO.succeed(list)
      }
    }

  /**
   * Maps the [[TestSystem]] implementation in the test environment to one that
   * uses the live environment, leaving all other test implementations the
   * same.
   */
  final def withLiveSystem: TestEnvironment =
    mapTestSystem { _ =>
      new TestSystem.Service[Any] {
        def clearEnv(variable: String): UIO[Unit]                        = UIO.unit
        def clearProperty(prop: String): UIO[Unit]                       = UIO.unit
        def env(variable: String): IO[SecurityException, Option[String]] = live.provide(zio.system.env(variable))
        val lineSeparator: UIO[String]                                   = live.provide(zio.system.lineSeparator)
        def property(prop: String): Task[Option[String]]                 = live.provide(zio.system.property(prop))
        def putEnv(name: String, value: String): UIO[Unit]               = UIO.unit
        def putProperty(name: String, value: String): UIO[Unit]          = UIO.unit
        val save: UIO[UIO[Unit]]                                         = UIO.succeed(UIO.unit)
        def setLineSeparator(lineSep: String): UIO[Unit]                 = UIO.unit
      }
    }

  override final lazy val scheduler: Scheduler.Service[Any] = clock
}

object TestEnvironment extends Serializable {

  def apply(
    annotationsService: Annotations.Service[Any],
    clockService: TestClock.Service[Any],
    consoleService: TestConsole.Service[Any],
    liveService: Live.Service[ZEnv],
    randomService: TestRandom.Service[Any],
    sizedService: Sized.Service[Any],
    systemService: TestSystem.Service[Any]
  ): TestEnvironment =
    new TestEnvironment {
      override val annotations = annotationsService
      override val clock       = clockService
      override val console     = consoleService
      override val live        = liveService
      override val random      = randomService
      override val sized       = sizedService
      override val system      = systemService
    }

  val Value: Managed[Nothing, TestEnvironment] =
    for {
      live        <- Live.makeService(LiveEnvironment).toManaged_
      annotations <- Annotations.makeService.toManaged_
      clock       <- TestClock.makeTest(TestClock.DefaultData, Some(live))
      console     <- TestConsole.makeTest(TestConsole.DefaultData).toManaged_
      random      <- TestRandom.makeTest(TestRandom.DefaultData).toManaged_
      sized       <- Sized.makeService(100).toManaged_
      system      <- TestSystem.makeTest(TestSystem.DefaultData).toManaged_
      time        <- live.provide(zio.clock.nanoTime).toManaged_
      _           <- random.setSeed(time).toManaged_
    } yield TestEnvironment(annotations, clock, console, live, random, sized, system)
}
