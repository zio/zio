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

import java.time.{ Instant, OffsetDateTime, ZoneId }
import java.util.concurrent.TimeUnit

import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.internal.{ Scheduler => IScheduler }
import zio.internal.Scheduler.CancelToken
import zio.scheduler.Scheduler

/**
 * `MockClock` makes it easy to deterministially and efficiently test effects
 * involving the passage of time.
 *
 * Instead of waiting for actual time to pass, `sleep` and methods implemented
 * in terms of it schedule effects to take place at a given time. Users can
 * adjust the current clock time using the `adjust` and `setTime` methods, and
 * all effects scheduled to take place on or before that time will automically
 * be run.
 *
 * For example, here is how we can test [[ZIO.timeout]] using `MockClock:
 *
 * {{{
 *  import zio.ZIO
 *  import zio.duration._
 *  import zio.test.mock.MockClock
 *
 *  for {
 *    mockClock <- MockClock.makeMock(MockClock.DefaultData)
 *    fiber <- ZIO.sleep(5.minutes).timeout(1.minute).fork
 *    _ <- mockClock.setTime(1.minute)
 *    result <- fiber.join
 *   } yield result == None
 * }}}
 *
 * Note how we forked the fiber that `sleep` was invoked on. Calls to `sleep`
 * and methods derived from it will semantically block until the time is
 * set to on or after the time they are scheduled to run. If we didn't fork the
 * fiber on which we called sleep we would never get to set the the time on the
 * line below. Thus, a useful pattern when using `MockClock` is to fork the
 * effect being tested, then adjust the clock to the desired time, and finally
 * verify that the expected effects have been performed.
 *
 * One other thing to keep in mind is that `sleep` and related combinators
 * schedule events to occur at an absolute point in time (e.g. 10 seconds from
 * the `0` time), not relative time (e.g. 10 seconds from the current set clock
 * time). This is necessary to avoid race conditions between scheduling effects
 * and adjusting the time but means it sometimes may be necessary to use
 * `adjustTime` to reset the time between effects you are testing to achieve
 * the desired sequence of effects.
 */
trait MockClock extends Clock with Scheduler {
  val clock: MockClock.Service[Any]
  val scheduler: MockClock.Service[Any]
}

object MockClock {

  trait Service[R] extends Clock.Service[R] with Scheduler.Service[R] {
    def sleeps: UIO[List[Duration]]
    def adjust(duration: Duration): UIO[Unit]
    def setTime(duration: Duration): UIO[Unit]
    def setTimeZone(zone: ZoneId): UIO[Unit]
    def timeZone: UIO[ZoneId]
  }

  case class Mock(clockState: Ref[MockClock.Data]) extends MockClock.Service[Any] {

    /**
     * Increments the current clock time by the specified duration. Any effects
     * that were scheduled to occur on or before the new time will immediately
     * be run.
     */
    final def adjust(duration: Duration): UIO[Unit] =
      clockState.update { data =>
        Data(
          data.nanoTime + duration.toNanos,
          data.currentTimeMillis + duration.toMillis,
          data.sleeps,
          data.timeZone
        )
      } *> wakeUp

    /**
     * Returns the current clock time as an `OffsetDateTime`.
     */
    final def currentDateTime: UIO[OffsetDateTime] =
      clockState.get.map(data => offset(data.currentTimeMillis, data.timeZone))

    /**
     * Returns the current clock time in the specified time unit.
     */
    final def currentTime(unit: TimeUnit): UIO[Long] =
      clockState.get.map(data => unit.convert(data.currentTimeMillis, TimeUnit.MILLISECONDS))

    /**
     * Returns the current clock time in nanoseconds.
     */
    final val nanoTime: IO[Nothing, Long] =
      clockState.get.map(_.nanoTime)

    /**
     * Sets the current clock time to the specified time. Any effects that
     * were scheduled to occur on or before the new time will immediately
     * be run.
     */
    final def setTime(duration: Duration): UIO[Unit] =
      clockState.update { data =>
        data.copy(
          nanoTime = duration.toNanos,
          currentTimeMillis = duration.toMillis
        )
      } *> wakeUp

    /**
     * Sets the time zone to the specified time zone. The clock time in terms
     * of nanoseconds since the epoch will not be adjusted and no scheduled
     * effects will be run as a result of this method.
     */
    final def setTimeZone(zone: ZoneId): UIO[Unit] =
      clockState.update(_.copy(timeZone = zone)).unit

    /**
     * Returns an effect that creates a new `Scheduler` backed by this
     * `MockClock`.
     */
    val scheduler: ZIO[Any, Nothing, IScheduler] =
      ZIO.runtime[Any].flatMap { runtime =>
        ZIO.succeed {
          new IScheduler {
            final def schedule(task: Runnable, duration: Duration): CancelToken =
              duration match {
                case Duration.Infinity =>
                  ConstFalse
                case Duration.Zero =>
                  task.run()
                  ConstFalse
                case duration: Duration =>
                  runtime.unsafeRun {
                    for {
                      latch <- Promise.make[Nothing, Unit]
                      _     <- latch.await.flatMap(_ => ZIO.effect(task.run())).fork
                      _ <- clockState.update { data =>
                            data.copy(
                              sleeps =
                                (Duration.fromNanos(data.nanoTime) + duration, latch) ::
                                  data.sleeps
                            )
                          }
                    } yield () => runtime.unsafeRun(cancel(latch))
                  }
              }
            final def shutdown(): Unit =
              runtime.unsafeRunAsync_ {
                clockState.modify { data =>
                  if (data.sleeps.isEmpty)
                    (Nil, data)
                  else {
                    val duration = data.sleeps.map(_._1).max
                    (data.sleeps, Data(duration.toNanos, duration.toMillis, Nil, data.timeZone))
                  }
                }.flatMap(run)
              }
            final def size: Int =
              runtime.unsafeRun(clockState.get.map(_.sleeps.length))
            private val ConstFalse = () => false
          }
        }
      }

    /**
     * Semantically blocks the current fiber until the clock time is equal to
     * or greater than the specified duration. Once the clock time is adjusted
     * to on or after the duration, the fiber will automatically be resumed.
     */
    final def sleep(duration: Duration): UIO[Unit] =
      for {
        latch <- Promise.make[Nothing, Unit]
        await <- clockState.modify { data =>
                  if (duration > Duration.fromNanos(data.nanoTime))
                    (true, data.copy(sleeps = (duration, latch) :: data.sleeps))
                  else
                    (false, data)
                }
        _ <- if (await) latch.await else latch.succeed(())
      } yield ()

    /**
     * Returns a list of the times at which all queued effects are scheduled to
     * resume.
     */
    val sleeps: UIO[List[Duration]] = clockState.get.map(_.sleeps.map(_._1))

    /**
     * Returns the time zone.
     */
    val timeZone: UIO[ZoneId] =
      clockState.get.map(_.timeZone)

    private def cancel(p: Promise[Nothing, Unit]): UIO[Boolean] =
      clockState.modify { data =>
        val (cancels, sleeps) = data.sleeps.partition(_._2 == p)
        (cancels, data.copy(sleeps = sleeps))
      }.map(_.nonEmpty)

    private def run(wakes: List[(Duration, Promise[Nothing, Unit])]): UIO[Unit] =
      UIO.forkAll_(wakes.sortBy(_._1).map(_._2.succeed(()))).fork.unit

    private val wakeUp: UIO[Unit] =
      clockState.modify { data =>
        val (wakes, sleeps) =
          data.sleeps.partition(_._1 <= Duration.fromNanos(data.nanoTime))
        (wakes, data.copy(sleeps = sleeps))
      }.flatMap(run)
  }

  /**
   * Accesses a `MockClock` instance in the environment and increments the time
   * by the specified duration, running any actions scheduled for on or before
   * the new time.
   */
  def adjust(duration: Duration): ZIO[MockClock, Nothing, Unit] =
    ZIO.accessM(_.clock.adjust(duration))

  /**
   * Constructs a new `MockClock` with the specified initial state. This can
   * be useful for providing the required environment to an effect that
   * requires a `Clock`, such as with [[ZIO.provide]].
   */
  def make(data: Data): UIO[MockClock] =
    makeMock(data).map { mock =>
      new MockClock {
        val clock     = mock
        val scheduler = mock
      }
    }

  /**
   * Constructs a new `Mock` object that implements the `MockClock` interface.
   * This can be useful for mixing in with implementations of other interfaces.
   */
  def makeMock(data: Data): UIO[Mock] =
    Ref.make(data).map(Mock(_))

  /**
   * Accesses a `MockClock` instance in the environment and sets the clock time
   * to the specified time, running any actions scheduled for on or before the
   * new time.
   */
  def setTime(duration: Duration): ZIO[MockClock, Nothing, Unit] =
    ZIO.accessM(_.clock.setTime(duration))

  /**
   * Accesses a `MockClock` instance in the environment, setting the time zone
   * to the specified time zone. The clock time in terms of nanoseconds since
   * the epoch will not be altered and no scheduled actions will be run as a
   * result of this effect.
   */
  def setTimeZone(zone: ZoneId): ZIO[MockClock, Nothing, Unit] =
    ZIO.accessM(_.clock.setTimeZone(zone))

  /**
   * Accesses a `MockClock` instance in the environment and returns a list of
   * times that effects are scheduled to run.
   */
  val sleeps: ZIO[MockClock, Nothing, List[Duration]] =
    ZIO.accessM(_.clock.sleeps)

  /**
   * Accesses a `MockClock` instance in the environemtn and returns the current
   * time zone.
   */
  val timeZone: ZIO[MockClock, Nothing, ZoneId] =
    ZIO.accessM(_.clock.timeZone)

  /**
   * The default initial state of the `MockClock` with the clock time set to
   * `0` and no effects scheduled to run.
   */
  val DefaultData = Data(0, 0, Nil, ZoneId.of("UTC"))

  /**
   * The state of the `MockClock`.
   */
  case class Data(
    nanoTime: Long,
    currentTimeMillis: Long,
    sleeps: List[(Duration, Promise[Nothing, Unit])],
    timeZone: ZoneId
  )

  private def offset(millis: Long, timeZone: ZoneId): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), timeZone)
}
