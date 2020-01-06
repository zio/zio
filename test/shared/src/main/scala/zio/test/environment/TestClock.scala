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
import zio.duration._
import zio.internal.{ Scheduler => IScheduler }
import zio.internal.Scheduler.CancelToken
import zio.clock.Clock

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
object TestClock extends Serializable {

  trait Service extends Restorable with Clock.Service {
    def adjust(duration: Duration): UIO[Unit]
    def fiberTime: UIO[Duration]
    def setDateTime(dateTime: OffsetDateTime): UIO[Unit]
    def setTime(duration: Duration): UIO[Unit]
    def setTimeZone(zone: ZoneId): UIO[Unit]
    def sleeps: UIO[List[Duration]]
    def timeZone: UIO[ZoneId]
  }

  case class Test(
    clockState: Ref[TestClock.Data],
    fiberState: FiberRef[TestClock.FiberData],
    live: Live.Service,
    warningState: RefM[TestClock.WarningData]
  ) extends TestClock.Service {

    /**
     * Increments the current clock time by the specified duration. Any effects
     * that were scheduled to occur on or before the new time will immediately
     * be run.
     */
    final def adjust(duration: Duration): UIO[Unit] =
      warningDone *> clockState.modify { data =>
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
      clockState.get.map(data => toDateTime(data.currentTimeMillis, data.timeZone))

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
    final val nanoTime: UIO[Long] =
      clockState.get.map(_.nanoTime)

    /**
     * Saves the `TestClock`'s current state in an effect which, when run, will restore the `TestClock`
     * state to the saved state
     */
    val save: UIO[UIO[Unit]] =
      for {
        fState <- fiberState.get
        cState <- clockState.get
        wState <- warningState.get
      } yield {
        fiberState.set(fState) *>
          clockState.set(cState) *>
          warningState.set(wState)
      }

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
                warningDone *> clockState.modify { data =>
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
     * Sets the current clock time to the specified `OffsetDateTime`. Any
     * effects that were scheduled to occur on or before the new time will
     * immediately be run.
     */
    final def setDateTime(dateTime: OffsetDateTime): UIO[Unit] =
      setTime(fromDateTime(dateTime))

    /**
     * Sets the current clock time to the specified time in terms of duration
     * since the epoch. Any effects that were scheduled to occur on or before
     * the new time will immediately be run.
     */
    final def setTime(duration: Duration): UIO[Unit] =
      warningDone *> clockState.modify { data =>
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
        _ <- if (await) warningStart *> latch.await else latch.succeed(())
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

    private[TestClock] val warningDone: UIO[Unit] =
      warningState
        .updateSome[Any, Nothing] {
          case WarningData.Start          => ZIO.succeed(WarningData.done)
          case WarningData.Pending(fiber) => fiber.interrupt.as(WarningData.done)
        }
        .unit

    private val warningStart: UIO[Unit] =
      warningState.updateSome {
        case WarningData.Start =>
          for {
            fiber <- live.provide(console.putStrLn(warning).delay(5.seconds)).interruptible.fork
          } yield WarningData.pending(fiber)
      }.unit

  }

  /**
   * Accesses a `TestClock` instance in the environment and increments the time
   * by the specified duration, running any actions scheduled for on or before
   * the new time.
   */
  def adjust(duration: Duration): ZIO[TestClock, Nothing, Unit] =
    ZIO.accessM(_.get.adjust(duration))

  /**
   * Accesses a `TestClock` instance in the environment and returns the current
   * fiber time for this fiber.
   */
  val fiberTime: ZIO[TestClock, Nothing, Duration] =
    ZIO.accessM(_.get.fiberTime)

  /**
   * Constructs a new `Test` object that implements the `TestClock` interface.
   * This can be useful for mixing in with implementations of other interfaces.
   */
  def live(data: Data): ZLayer[Live, Nothing, TestClock] =
    ZLayer.fromFunctionManaged { (live: Live) =>
      for {
        ref      <- Ref.make(data).toManaged_
        fiberRef <- FiberRef.make(FiberData(data.nanoTime), FiberData.combine).toManaged_
        refM     <- RefM.make(WarningData.start).toManaged_
        test     <- Managed.make(UIO(Test(ref, fiberRef, live.get, refM)))(_.warningDone)
      } yield Has(test)
    }

  val default: ZLayer[Live, Nothing, TestClock] =
    live(Data(0, 0, Nil, ZoneId.of("UTC")))

  /**
   * Accesses a `TestClock` instance in the environment and saves the clock state in an effect which, when run,
   * will restore the `TestClock` to the saved state
   */
  val save: ZIO[TestClock, Nothing, UIO[Unit]] = ZIO.accessM[TestClock](_.get.save)

  /**
   * Accesses a `TestClock` instance in the environment and sets the clock time
   * to the specified `OffsetDateTime`, running any actions scheduled for on or
   * before the new time.
   */
  def setDateTime(dateTime: OffsetDateTime): ZIO[TestClock, Nothing, Unit] =
    ZIO.accessM(_.get.setDateTime(dateTime))

  /**
   * Accesses a `TestClock` instance in the environment and sets the clock time
   * to the specified time in terms of duration since the epoch, running any
   * actions scheduled for on or before the new time.
   */
  def setTime(duration: Duration): ZIO[TestClock, Nothing, Unit] =
    ZIO.accessM(_.get.setTime(duration))

  /**
   * Accesses a `TestClock` instance in the environment, setting the time zone
   * to the specified time zone. The clock time in terms of nanoseconds since
   * the epoch will not be altered and no scheduled actions will be run as a
   * result of this effect.
   */
  def setTimeZone(zone: ZoneId): ZIO[TestClock, Nothing, Unit] =
    ZIO.accessM(_.get.setTimeZone(zone))

  /**
   * Accesses a `TestClock` instance in the environment and returns a list of
   * times that effects are scheduled to run.
   */
  val sleeps: ZIO[TestClock, Nothing, List[Duration]] =
    ZIO.accessM(_.get.sleeps)

  /**
   * Accesses a `TestClock` instance in the environment and returns the current
   * time zone.
   */
  val timeZone: ZIO[TestClock, Nothing, ZoneId] =
    ZIO.accessM(_.get.timeZone)

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

  sealed trait WarningData

  object WarningData {
    case object Start                                     extends WarningData
    final case class Pending(fiber: Fiber[Nothing, Unit]) extends WarningData
    case object Done                                      extends WarningData

    val start: WarningData                                = Start
    def pending(fiber: Fiber[Nothing, Unit]): WarningData = Pending(fiber)
    val done: WarningData                                 = Done
  }

  private def toDateTime(millis: Long, timeZone: ZoneId): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), timeZone)

  private def fromDateTime(dateTime: OffsetDateTime): Duration =
    Duration(dateTime.toInstant.toEpochMilli, TimeUnit.MILLISECONDS)

  private val warning =
    "Warning: A test is using time, but is not advancing the test clock, " +
      "which may result in the test hanging. Use TestClock.adjust to " +
      "manually advance the time."
}
