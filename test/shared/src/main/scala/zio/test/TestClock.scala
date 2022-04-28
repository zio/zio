/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio._
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.io.IOException
import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit
import scala.collection.immutable.SortedSet

/**
 * `TestClock` makes it easy to deterministically and efficiently test effects
 * involving the passage of time.
 *
 * Instead of waiting for actual time to pass, `sleep` and methods implemented
 * in terms of it schedule effects to take place at a given clock time. Users
 * can adjust the clock time using the `adjust` and `setTime` methods, and all
 * effects scheduled to take place on or before that time will automatically be
 * run in order.
 *
 * For example, here is how we can test `ZIO#timeout` using `TestClock`:
 *
 * {{{
 *   import zio.ZIO
 *   import zio.test.TestClock
 *
 *   for {
 *     fiber  <- ZIO.sleep(5.minutes).timeout(1.minute).fork
 *     _      <- TestClock.adjust(1.minute)
 *     result <- fiber.join
 *   } yield result == None
 * }}}
 *
 * Note how we forked the fiber that `sleep` was invoked on. Calls to `sleep`
 * and methods derived from it will semantically block until the time is set to
 * on or after the time they are scheduled to run. If we didn't fork the fiber
 * on which we called sleep we would never get to set the time on the line
 * below. Thus, a useful pattern when using `TestClock` is to fork the effect
 * being tested, then adjust the clock time, and finally verify that the
 * expected effects have been performed.
 *
 * For example, here is how we can test an effect that recurs with a fixed
 * delay:
 *
 * {{{
 *   import zio.Queue
 *   import zio.test.TestClock
 *
 *   for {
 *     q <- Queue.unbounded[Unit]
 *     _ <- q.offer(()).delay(60.minutes).forever.fork
 *     a <- q.poll.map(_.isEmpty)
 *     _ <- TestClock.adjust(60.minutes)
 *     b <- q.take.as(true)
 *     c <- q.poll.map(_.isEmpty)
 *     _ <- TestClock.adjust(60.minutes)
 *     d <- q.take.as(true)
 *     e <- q.poll.map(_.isEmpty)
 *   } yield a && b && c && d && e
 * }}}
 *
 * Here we verify that no effect is performed before the recurrence period, that
 * an effect is performed after the recurrence period, and that the effect is
 * performed exactly once. The key thing to note here is that after each
 * recurrence the next recurrence is scheduled to occur at the appropriate time
 * in the future, so when we adjust the clock by 60 minutes exactly one value is
 * placed in the queue, and when we adjust the clock by another 60 minutes
 * exactly one more value is placed in the queue.
 */
trait TestClock extends Clock with Restorable {
  def adjust(duration: Duration)(implicit trace: Trace): UIO[Unit]
  def adjustWith[R, E, A](duration: Duration)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]
  def setDateTime(dateTime: OffsetDateTime)(implicit trace: Trace): UIO[Unit]
  def setTime(duration: Duration)(implicit trace: Trace): UIO[Unit]
  def setTimeZone(zone: ZoneId)(implicit trace: Trace): UIO[Unit]
  def sleeps(implicit trace: Trace): UIO[List[Duration]]
  def timeZone(implicit trace: Trace): UIO[ZoneId]
}

object TestClock extends Serializable {

  final case class Test(
    clockState: Ref.Atomic[TestClock.Data],
    live: Live,
    annotations: Annotations,
    warningState: Ref.Synchronized[TestClock.WarningData],
    suspendedWarningState: Ref.Synchronized[TestClock.SuspendedWarningData]
  ) extends Clock
      with TestClock
      with TestClockPlatformSpecific {

    /**
     * Increments the current clock time by the specified duration. Any effects
     * that were scheduled to occur on or before the new time will be run in
     * order.
     */
    def adjust(duration: Duration)(implicit trace: Trace): UIO[Unit] =
      warningDone *> run(_ + duration)

    /**
     * Increments the current clock time by the specified duration. Any effects
     * that were scheduled to occur on or before the new time will be run in
     * order.
     */
    def adjustWith[R, E, A](duration: Duration)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      zio <& adjust(duration)

    /**
     * Returns the current clock time as an `OffsetDateTime`.
     */
    def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
      ZIO.succeed(unsafeCurrentDateTime())

    /**
     * Returns the current clock time in the specified time unit.
     */
    def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeCurrentTime(unit))

    /**
     * Returns the current clock time in nanoseconds.
     */
    def nanoTime(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeNanoTime())

    /**
     * Returns the current clock time as an `Instant`.
     */
    def instant(implicit trace: Trace): UIO[Instant] =
      ZIO.succeed(unsafeInstant())

    /**
     * Constructs a `java.time.Clock` backed by the `Clock` service.
     */
    def javaClock(implicit trace: Trace): UIO[java.time.Clock] = {

      final case class JavaClock(clockState: Ref.Atomic[TestClock.Data], zoneId: ZoneId) extends java.time.Clock {
        def getZone(): ZoneId =
          zoneId
        def instant(): Instant =
          toInstant(clockState.unsafeGet.duration)
        override def withZone(zoneId: ZoneId): JavaClock =
          copy(zoneId = zoneId)
      }

      clockState.get.map(data => JavaClock(clockState, data.timeZone))
    }

    /**
     * Returns the current clock time as a `LocalDateTime`.
     */
    def localDateTime(implicit trace: Trace): UIO[LocalDateTime] =
      ZIO.succeed(unsafeLocalDateTime())

    /**
     * Saves the `TestClock`'s current state in an effect which, when run, will
     * restore the `TestClock` state to the saved state
     */
    def save(implicit trace: Trace): UIO[UIO[Unit]] =
      for {
        clockData <- clockState.get
      } yield clockState.set(clockData)

    /**
     * Sets the current clock time to the specified `OffsetDateTime`. Any
     * effects that were scheduled to occur on or before the new time will be
     * run in order.
     */
    def setDateTime(dateTime: OffsetDateTime)(implicit trace: Trace): UIO[Unit] =
      setTime(fromDateTime(dateTime))

    /**
     * Sets the current clock time to the specified time in terms of duration
     * since the epoch. Any effects that were scheduled to occur on or before
     * the new time will immediately be run in order.
     */
    def setTime(duration: Duration)(implicit trace: Trace): UIO[Unit] =
      warningDone *> run(_ => duration)

    /**
     * Sets the time zone to the specified time zone. The clock time in terms of
     * nanoseconds since the epoch will not be adjusted and no scheduled effects
     * will be run as a result of this method.
     */
    def setTimeZone(zone: ZoneId)(implicit trace: Trace): UIO[Unit] =
      clockState.update(_.copy(timeZone = zone))

    /**
     * Semantically blocks the current fiber until the clock time is equal to or
     * greater than the specified duration. Once the clock time is adjusted to
     * on or after the duration, the fiber will automatically be resumed.
     */
    def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
      for {
        promise <- Promise.make[Nothing, Unit]
        shouldAwait <- clockState.modify { data =>
                         val end = data.duration + duration
                         if (end > data.duration)
                           (true, data.copy(sleeps = (end, promise) :: data.sleeps))
                         else
                           (false, data)
                       }
        _ <- if (shouldAwait) warningStart *> promise.await else promise.succeed(())
      } yield ()

    /**
     * Returns a list of the times at which all queued effects are scheduled to
     * resume.
     */
    def sleeps(implicit trace: Trace): UIO[List[Duration]] =
      clockState.get.map(_.sleeps.map(_._1))

    /**
     * Returns the time zone.
     */
    def timeZone(implicit trace: Trace): UIO[ZoneId] =
      clockState.get.map(_.timeZone)

    override private[zio] def unsafeCurrentTime(unit: TimeUnit): Long =
      unit.convert(clockState.unsafeGet.duration.toMillis, TimeUnit.MILLISECONDS)

    override private[zio] def unsafeCurrentDateTime(): OffsetDateTime = {
      val data = clockState.unsafeGet
      toDateTime(data.duration, data.timeZone)
    }

    override private[zio] def unsafeInstant(): Instant =
      toInstant(clockState.unsafeGet.duration)

    override private[zio] def unsafeLocalDateTime(): LocalDateTime = {
      val data = clockState.unsafeGet
      toLocalDateTime(data.duration, data.timeZone)
    }

    override private[zio] def unsafeNanoTime(): Long =
      clockState.unsafeGet.duration.toNanos

    /**
     * Cancels the warning message that is displayed if a test is advancing the
     * `TestClock` but a fiber is not suspending.
     */
    private[TestClock] def suspendedWarningDone(implicit trace: Trace): UIO[Unit] =
      suspendedWarningState.updateSomeZIO[Any, Nothing] { case SuspendedWarningData.Pending(fiber) =>
        fiber.interrupt.as(SuspendedWarningData.start)
      }

    /**
     * Cancels the warning message that is displayed if a test is using time but
     * is not advancing the `TestClock`.
     */
    private[TestClock] def warningDone(implicit trace: Trace): UIO[Unit] =
      warningState.updateSomeZIO[Any, Nothing] {
        case WarningData.Start          => ZIO.succeedNow(WarningData.done)
        case WarningData.Pending(fiber) => fiber.interrupt.as(WarningData.done)
      }

    /**
     * Polls until all descendants of this fiber are done or suspended.
     */
    private def awaitSuspended(implicit trace: Trace): UIO[Unit] =
      suspendedWarningStart *>
        suspended
          .zipWith(live.provide(ZIO.sleep(10.milliseconds)) *> suspended)(_ == _)
          .filterOrFail(identity)(())
          .eventually *>
        suspendedWarningDone

    /**
     * Delays for a short period of time.
     */
    private def delay(implicit trace: Trace): UIO[Unit] =
      live.provide(ZIO.sleep(5.milliseconds))

    /**
     * Captures a "snapshot" of the identifier and status of all fibers in this
     * test other than the current fiber. Fails with the `Unit` value if any of
     * these fibers are not done or suspended. Note that because we cannot
     * synchronize on the status of multiple fibers at the same time this
     * snapshot may not be fully consistent.
     */
    private def freeze(implicit trace: Trace): IO[Unit, Map[FiberId, Fiber.Status]] =
      supervisedFibers.flatMap { fibers =>
        ZIO.foldLeft(fibers)(Map.empty[FiberId, Fiber.Status]) { (map, fiber) =>
          fiber.status.flatMap {
            case done @ Fiber.Status.Done                          => ZIO.succeedNow(map + (fiber.id -> done))
            case suspended @ Fiber.Status.Suspended(_, _, _, _, _) => ZIO.succeedNow(map + (fiber.id -> suspended))
            case _                                                 => ZIO.fail(())
          }
        }
      }

    /**
     * Returns a set of all fibers in this test.
     */
    def supervisedFibers(implicit trace: Trace): UIO[SortedSet[Fiber.Runtime[Any, Any]]] =
      ZIO.descriptorWith { descriptor =>
        annotations.get(TestAnnotation.fibers).flatMap {
          case Left(_) => ZIO.succeedNow(SortedSet.empty[Fiber.Runtime[Any, Any]])
          case Right(refs) =>
            ZIO
              .foreach(refs)(ref => ZIO.succeed(ref.get))
              .map(_.foldLeft(SortedSet.empty[Fiber.Runtime[Any, Any]])(_ ++ _))
              .map(_.filter(_.id != descriptor.id))
        }
      }

    /**
     * Constructs a `Duration` from an `OffsetDateTime`.
     */
    private def fromDateTime(dateTime: OffsetDateTime)(implicit trace: Trace): Duration =
      Duration(dateTime.toInstant.toEpochMilli, TimeUnit.MILLISECONDS)

    /**
     * Runs all effects scheduled to occur on or before the specified duration,
     * which may depend on the current time, in order.
     */
    private def run(f: Duration => Duration)(implicit trace: Trace): UIO[Unit] =
      awaitSuspended *>
        clockState.modify { data =>
          val end = f(data.duration)
          data.sleeps.sortBy(_._1) match {
            case (duration, promise) :: sleeps if duration <= end =>
              (Some((end, promise)), Data(duration, sleeps, data.timeZone))
            case _ => (None, Data(end, data.sleeps, data.timeZone))
          }
        }.flatMap {
          case None => ZIO.unit
          case Some((end, promise)) =>
            promise.succeed(()) *>
              ZIO.yieldNow *>
              run(_ => end)
        }

    /**
     * Returns whether all descendants of this fiber are done or suspended.
     */
    private def suspended(implicit trace: Trace): IO[Unit, Map[FiberId, Fiber.Status]] =
      freeze.zip(delay *> freeze).flatMap { case (first, last) =>
        if (first == last) ZIO.succeedNow(first)
        else ZIO.fail(())
      }

    /**
     * Constructs an `OffsetDateTime` from a `Duration` and a `ZoneId`.
     */
    private def toDateTime(duration: Duration, timeZone: ZoneId): OffsetDateTime =
      OffsetDateTime.ofInstant(toInstant(duration), timeZone)

    /**
     * Constructs a `LocalDateTime` from a `Duration` and a `ZoneId`.
     */
    private def toLocalDateTime(duration: Duration, timeZone: ZoneId): LocalDateTime =
      LocalDateTime.ofInstant(toInstant(duration), timeZone)

    /**
     * Constructs an `Instant` from a `Duration`.
     */
    private def toInstant(duration: Duration): Instant =
      Instant.ofEpochMilli(duration.toMillis)

    /**
     * Forks a fiber that will display a warning message if a test is advancing
     * the `TestClock` but a fiber is not suspending.
     */
    private def suspendedWarningStart(implicit trace: Trace): UIO[Unit] =
      suspendedWarningState.updateSomeZIO { case SuspendedWarningData.Start =>
        for {
          fiber <- live.provide {
                     ZIO
                       .logWarning(suspendedWarning)
                       .zipRight(suspendedWarningState.set(SuspendedWarningData.done))
                       .delay(5.seconds)
                   }.interruptible.fork
        } yield SuspendedWarningData.pending(fiber)
      }

    /**
     * Forks a fiber that will display a warning message if a test is using time
     * but is not advancing the `TestClock`.
     */
    private def warningStart(implicit trace: Trace): UIO[Unit] =
      warningState.updateSomeZIO { case WarningData.Start =>
        for {
          fiber <- live.provide(ZIO.logWarning(warning).delay(5.seconds)).interruptible.fork
        } yield WarningData.pending(fiber)
      }

  }

  /**
   * Constructs a new `Test` object that implements the `TestClock` interface.
   * This can be useful for mixing in with implementations of other interfaces.
   */
  def live(
    data: Data
  )(implicit
    trace: Trace
  ): ZLayer[Annotations with Live, Nothing, TestClock] =
    ZLayer.scoped {
      for {
        live                  <- ZIO.service[Live]
        annotations           <- ZIO.service[Annotations]
        clockState            <- ZIO.succeedNow(Ref.unsafeMake(data))
        warningState          <- Ref.Synchronized.make(WarningData.start)
        suspendedWarningState <- Ref.Synchronized.make(SuspendedWarningData.start)
        test                   = Test(clockState, live, annotations, warningState, suspendedWarningState)
        _                     <- ZEnv.services.locallyScopedWith(_.add(test))
        _                     <- ZIO.addFinalizer(test.warningDone *> test.suspendedWarningDone)
      } yield test
    }

  val any: ZLayer[TestClock, Nothing, TestClock] =
    ZLayer.environment[TestClock](Tracer.newTrace)

  val default: ZLayer[Live with Annotations, Nothing, TestClock] =
    live(Data(Duration.Zero, Nil, ZoneId.of("UTC")))(Tracer.newTrace)

  /**
   * Accesses a `TestClock` instance in the environment and increments the time
   * by the specified duration, running any actions scheduled for on or before
   * the new time in order.
   */
  def adjust(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
    testClockWith(_.adjust(duration))

  def adjustWith[R, E, A](duration: => Duration)(zio: ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R, E, A] =
    testClockWith(_.adjustWith(duration)(zio))

  /**
   * Accesses a `TestClock` instance in the environment and saves the clock
   * state in an effect which, when run, will restore the `TestClock` to the
   * saved state.
   */
  def save(implicit trace: Trace): UIO[UIO[Unit]] =
    testClockWith(_.save)

  /**
   * Accesses a `TestClock` instance in the environment and sets the clock time
   * to the specified `OffsetDateTime`, running any actions scheduled for on or
   * before the new time in order.
   */
  def setDateTime(dateTime: => OffsetDateTime)(implicit trace: Trace): UIO[Unit] =
    testClockWith(_.setDateTime(dateTime))

  /**
   * Accesses a `TestClock` instance in the environment and sets the clock time
   * to the specified time in terms of duration since the epoch, running any
   * actions scheduled for on or before the new time in order.
   */
  def setTime(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
    testClockWith(_.setTime(duration))

  /**
   * Accesses a `TestClock` instance in the environment, setting the time zone
   * to the specified time zone. The clock time in terms of nanoseconds since
   * the epoch will not be altered and no scheduled actions will be run as a
   * result of this effect.
   */
  def setTimeZone(zone: => ZoneId)(implicit trace: Trace): UIO[Unit] =
    testClockWith(_.setTimeZone(zone))

  /**
   * Accesses a `TestClock` instance in the environment and returns a list of
   * times that effects are scheduled to run.
   */
  def sleeps(implicit trace: Trace): UIO[List[Duration]] =
    testClockWith(_.sleeps)

  /**
   * Accesses a `TestClock` instance in the environment and returns the current
   * time zone.
   */
  def timeZone(implicit trace: Trace): UIO[ZoneId] =
    testClockWith(_.timeZone)

  /**
   * `Data` represents the state of the `TestClock`, including the clock time
   * and time zone.
   */
  final case class Data(
    duration: Duration,
    sleeps: List[(Duration, Promise[Nothing, Unit])],
    timeZone: ZoneId
  )

  /**
   * `Sleep` represents the state of a scheduled effect, including the time the
   * effect is scheduled to run, a promise that can be completed to resume
   * execution of the effect, and the fiber executing the effect.
   */
  final case class Sleep(duration: Duration, promise: Promise[Nothing, Unit], fiberId: FiberId)

  /**
   * `WarningData` describes the state of the warning message that is displayed
   * if a test is using time by is not advancing the `TestClock`. The possible
   * states are `Start` if a test has not used time, `Pending` if a test has
   * used time but has not adjusted the `TestClock`, and `Done` if a test has
   * adjusted the `TestClock` or the warning message has already been displayed.
   */
  sealed abstract class WarningData

  object WarningData {

    case object Start                                         extends WarningData
    final case class Pending(fiber: Fiber[IOException, Unit]) extends WarningData
    case object Done                                          extends WarningData

    /**
     * State indicating that a test has not used time.
     */
    val start: WarningData = Start

    /**
     * State indicating that a test has used time but has not adjusted the
     * `TestClock` with a reference to the fiber that will display the warning
     * message.
     */
    def pending(fiber: Fiber[IOException, Unit]): WarningData = Pending(fiber)

    /**
     * State indicating that a test has used time or the warning message has
     * already been displayed.
     */
    val done: WarningData = Done
  }

  sealed abstract class SuspendedWarningData

  object SuspendedWarningData {

    case object Start                                         extends SuspendedWarningData
    final case class Pending(fiber: Fiber[IOException, Unit]) extends SuspendedWarningData
    case object Done                                          extends SuspendedWarningData

    /**
     * State indicating that a test has not adjusted the clock.
     */
    val start: SuspendedWarningData = Start

    /**
     * State indicating that a test has adjusted the clock but a fiber is still
     * running with a reference to the fiber that will display the warning
     * message.
     */
    def pending(fiber: Fiber[IOException, Unit]): SuspendedWarningData = Pending(fiber)

    /**
     * State indicating that the warning message has already been displayed.
     */
    val done: SuspendedWarningData = Done
  }

  /**
   * The warning message that will be displayed if a test is using time but is
   * not advancing the `TestClock`.
   */
  private val warning =
    "Warning: A test is using time, but is not advancing the test clock, " +
      "which may result in the test hanging. Use TestClock.adjust to " +
      "manually advance the time."

  /**
   * The warning message that will be displayed if a test is advancing the clock
   * but a fiber is still running.
   */
  private val suspendedWarning =
    "Warning: A test is advancing the test clock, but a fiber is not " +
      "suspending, which may result in the test hanging. Use " +
      "TestAspect.diagnose to identity the fiber that is not suspending."
}
