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

import java.time.{ Instant, OffsetDateTime, ZoneId }
import java.util.concurrent.TimeUnit

import zio._
import zio.clock.Clock
import zio.duration._
import zio.internal.{ Scheduler => IScheduler }
import zio.internal.Scheduler.CancelToken
import zio.scheduler.Scheduler

/**
 * `TestClock` makes it easy to deterministically and efficiently test effects
 * involving the passage of time.
 *
 * Instead of waiting for actual time to pass, `sleep` and methods implemented
 * in terms of it schedule effects to take place at a given clock time. Users
 * can adjust the clock time using the `adjust` and `setTime` methods, and all
 * effects scheduled to take place on or before that time will automically be
 * run.
 *
 * For example, here is how we can test [[ZIO.timeout]] using `TestClock:
 *
 * {{{
 *  import zio.ZIO
 *  import zio.duration._
 *  import zio.test.environment.TestClock
 *
 *  for {
 *    fiber  <- ZIO.sleep(5.minutes).timeout(1.minute).fork
 *    _      <- TestClock.adjust(1.minute)
 *    result <- fiber.join
 *  } yield result == None
 * }}}
 *
 * Note how we forked the fiber that `sleep` was invoked on. Calls to `sleep`
 * and methods derived from it will semantically block until the time is
 * set to on or after the time they are scheduled to run. If we didn't fork the
 * fiber on which we called sleep we would never get to set the the time on the
 * line below. Thus, a useful pattern when using `TestClock` is to fork the
 * effect being tested, then adjust the clock to the desired time, and finally
 * verify that the expected effects have been performed.
 *
 * Sleep and related combinators schedule events to occur at a specified
 * duration in the future relative to the current fiber time (e.g. 10 seconds
 * from the current fiber time). The fiber time is backed by a `FiberRef` and
 * is incremented for the duration each fiber is sleeping. Child fibers inherit
 * the fiber time of their parent so methods that rely on repeated `sleep`
 * calls work as you would expect.
 *
 * For example, here is how we can test an effect that recurs with a fixed
 * delay:
 *
 * {{{
 *  import zio.Queue
 *  import zio.duration._
 *  import zio.test.environment.TestClock
 *
 *  for {
 *    q <- Queue.unbounded[Unit]
 *    _ <- (q.offer(()).delay(60.minutes)).forever.fork
 *    a <- q.poll.map(_.isEmpty)
 *    _ <- TestClock.adjust(60.minutes)
 *    b <- q.take.as(true)
 *    c <- q.poll.map(_.isEmpty)
 *    _ <- TestClock.adjust(60.minutes)
 *    d <- q.take.as(true)
 *    e <- q.poll.map(_.isEmpty)
 *  } yield a && b && c && d && e
 * }}}
 *
 * Here we verify that no effect is performed before the recurrence period,
 * that an effect is performed after the recurrence period, and that the effect
 * is performed exactly once. The key thing to note here is that after each
 * recurrence the next recurrence is scheduled to occur at the appropriate time
 * in the future, so when we adjust the clock by 60 minutes exactly one value
 * is placed in the queue, and when we adjust the clock by another 60 minutes
 * exactly one more value is placed in the queue.
 */
trait TestClock extends Clock with Scheduler {
  val clock: TestClock.Service[Any]
  val scheduler: TestClock.Service[Any]
}

object TestClock extends Serializable {

  trait Service[R] extends Clock.Service[R] with Scheduler.Service[R] {
    def adjust(duration: Duration): UIO[Unit]
    def fiberTime: UIO[Duration]
    def setTime(duration: Duration): UIO[Unit]
    def setTimeZone(zone: ZoneId): UIO[Unit]
    def sleeps: UIO[List[Duration]]
    def timeZone: UIO[ZoneId]
  }

  case class Test(clockState: Ref[TestClock.Data], fiberState: FiberRef[TestClock.FiberData])
      extends TestClock.Service[Any] {

    /**
     * Increments the current clock time by the specified duration. Any effects
     * that were scheduled to occur on or before the new time will immediately
     * be run.
     */
    final def adjust(duration: Duration): UIO[Unit] =
      clockState.modify { data =>
        val nanoTime          = data.nanoTime + duration.toNanos
        val currentTimeMillis = data.currentTimeMillis + duration.toMillis
        val (wakes, sleeps)   = data.sleeps.partition(_._1 <= Duration.fromNanos(nanoTime))
        val updated = data.copy(
          nanoTime = nanoTime,
          currentTimeMillis = currentTimeMillis,
          sleeps = sleeps
        )
        (wakes, updated)
      }.flatMap(run)

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
     * Returns the current fiber time for this fiber. The fiber time is backed
     * by a `FiberRef` and is incremented for the duration each fiber is
     * sleeping. When a fiber is joined the fiber time will be set to the
     * maximum of the fiber time of the parent and child fibers. Thus, the
     * fiber time reflects the duration of sleeping that has occurred for this
     * fiber to reach its current state, properly reflecting forks and joins.
     *
     * {{{
     * for {
     *   _      <- TestClock.set(Duration.Infinity)
     *   _      <- ZIO.sleep(2.millis).zipPar(ZIO.sleep(1.millis))
     *   result <- TestClock.fiberTime
     * } yield result.toNanos == 2000000L
     * }}}
     */
    final val fiberTime: UIO[Duration] =
      fiberState.get.map(_.nanoTime.nanos)

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
      clockState.modify { data =>
        val (wakes, sleeps) = data.sleeps.partition(_._1 <= duration)
        val updated = data.copy(
          nanoTime = duration.toNanos,
          currentTimeMillis = duration.toMillis,
          sleeps = sleeps
        )
        (wakes, updated)
      }.flatMap(run)

    /**
     * Sets the time zone to the specified time zone. The clock time in terms
     * of nanoseconds since the epoch will not be adjusted and no scheduled
     * effects will be run as a result of this method.
     */
    final def setTimeZone(zone: ZoneId): UIO[Unit] =
      clockState.update(_.copy(timeZone = zone)).unit

    /**
     * Returns an effect that creates a new `Scheduler` backed by this
     * `TestClock`.
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
        start <- fiberState.modify { data =>
                  (data.nanoTime, data.copy(nanoTime = data.nanoTime + duration.toNanos))
                }
        await <- clockState.modify { data =>
                  val end = Duration.fromNanos(start) + duration
                  if (end > Duration.fromNanos(data.nanoTime))
                    (true, data.copy(sleeps = (end, latch) :: data.sleeps))
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
  }

  /**
   * Accesses a `TestClock` instance in the environment and increments the time
   * by the specified duration, running any actions scheduled for on or before
   * the new time.
   */
  def adjust(duration: Duration): ZIO[TestClock, Nothing, Unit] =
    ZIO.accessM(_.clock.adjust(duration))

  /**
   * Accesses a `TestClock` instance in the environment and returns the current
   * fiber time for this fiber.
   */
  val fiberTime: ZIO[TestClock, Nothing, Duration] =
    ZIO.accessM(_.clock.fiberTime)

  /**
   * Constructs a new `TestClock` with the specified initial state. This can
   * be useful for providing the required environment to an effect that
   * requires a `Clock`, such as with [[ZIO!.provide]].
   */
  def make(data: Data): UIO[TestClock] =
    makeTest(data).map { test =>
      new TestClock {
        val clock     = test
        val scheduler = test
      }
    }

  /**
   * Constructs a new `Test` object that implements the `TestClock` interface.
   * This can be useful for mixing in with implementations of other interfaces.
   */
  def makeTest(data: Data): UIO[Test] =
    for {
      ref      <- Ref.make(data)
      fiberRef <- FiberRef.make(FiberData(data.nanoTime), FiberData.combine)
    } yield Test(ref, fiberRef)

  /**
   * Accesses a `TestClock` instance in the environment and sets the clock time
   * to the specified time, running any actions scheduled for on or before the
   * new time.
   */
  def setTime(duration: Duration): ZIO[TestClock, Nothing, Unit] =
    ZIO.accessM(_.clock.setTime(duration))

  /**
   * Accesses a `TestClock` instance in the environment, setting the time zone
   * to the specified time zone. The clock time in terms of nanoseconds since
   * the epoch will not be altered and no scheduled actions will be run as a
   * result of this effect.
   */
  def setTimeZone(zone: ZoneId): ZIO[TestClock, Nothing, Unit] =
    ZIO.accessM(_.clock.setTimeZone(zone))

  /**
   * Accesses a `TestClock` instance in the environment and returns a list of
   * times that effects are scheduled to run.
   */
  val sleeps: ZIO[TestClock, Nothing, List[Duration]] =
    ZIO.accessM(_.clock.sleeps)

  /**
   * Accesses a `TestClock` instance in the environemtn and returns the current
   * time zone.
   */
  val timeZone: ZIO[TestClock, Nothing, ZoneId] =
    ZIO.accessM(_.clock.timeZone)

  /**
   * The default initial state of the `TestClock` with the clock time set to
   * `0` and no effects scheduled to run.
   */
  val DefaultData = Data(0, 0, Nil, ZoneId.of("UTC"))

  /**
   * The state of the `TestClock`.
   */
  case class Data(
    nanoTime: Long,
    currentTimeMillis: Long,
    sleeps: List[(Duration, Promise[Nothing, Unit])],
    timeZone: ZoneId
  )

  case class FiberData(nanoTime: Long)

  object FiberData {
    def combine(first: FiberData, last: FiberData): FiberData =
      FiberData(first.nanoTime max last.nanoTime)
  }

  private def offset(millis: Long, timeZone: ZoneId): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), timeZone)
}
