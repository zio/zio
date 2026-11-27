/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

import zio.Clock.ClockLive
import zio._
import zio.internal.FiberRuntime
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.collection.immutable.SortedSet
import scala.collection.{mutable => mu}

/**
 * `TestClock` is the test implementation of the `Clock` service. It lets a test
 * check effects that involve the passage of time without waiting for real time
 * to pass. In the test environment its clock time starts at the epoch
 * (1970-01-01T00:00:00Z) in the `UTC` time zone, and it changes when the test
 * calls `adjust` or `setTime`, or runs the restore effect returned by `save`.
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
 *   import zio._
 *   import zio.test._
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
 *   import zio._
 *   import zio.test._
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
 *
 * When a test reads the clock time or sleeps, and then does not call `adjust`
 * or `setTime` within 5 seconds of real time, `TestClock` prints a warning to
 * standard output that the test may hang.
 *
 * @see
 *   [[https://zio.dev/reference/test/services/clock/ TestClock]]
 * @see
 *   [[https://zio.dev/reference/test/aspects/restoring-state-of-test-services/ Restoring State of Test Services]]
 */
trait TestClock extends Clock with Restorable {

  /**
   * Moves the clock time forward by `duration` and resumes the sleeps that end
   * at or before the new time. Before it moves the clock, and again after it
   * resumes each fiber, it waits until the other fibers of the test are done or
   * suspended. A negative `duration` moves the clock time back.
   */
  def adjust(duration: Duration)(implicit trace: Trace): UIO[Unit]

  /**
   * Runs `zio` and, in parallel, moves the clock time forward by `duration` as
   * `adjust` does. Returns the result of `zio`.
   */
  def adjustWith[R, E, A](duration: Duration)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A]

  /**
   * Sets the clock time to `instant` and resumes the fibers whose sleeps end on
   * or before it, as `adjust` does. An instant earlier than the current clock
   * time moves the clock back and resumes no fiber.
   */
  def setTime(instant: Instant)(implicit trace: Trace): UIO[Unit]

  /**
   * Sets the time zone used by `currentDateTime`, `localDateTime` and
   * `javaClock`. The clock time does not change and no sleeping fiber is
   * resumed.
   */
  def setTimeZone(zone: ZoneId)(implicit trace: Trace): UIO[Unit]

  /**
   * Returns the end times of the sleeps that have not been resumed yet. Sleeps
   * scheduled since the last `adjust` or `setTime` come first, most recently
   * scheduled first; `adjust` and `setTime` sort the remaining ones by end
   * time. A sleep whose fiber was interrupted stays in the list until the clock
   * time reaches its end time.
   */
  def sleeps(implicit trace: Trace): UIO[List[Instant]]

  /**
   * Returns the time zone of this clock (`UTC` in the test environment unless
   * `setTimeZone` changed it).
   */
  def timeZone(implicit trace: Trace): UIO[ZoneId]
}

/**
 * The `TestClock` service: its `Test` implementation, the layers that build it,
 * and accessors that run a `TestClock` method on the current `TestClock`.
 */
object TestClock extends Serializable {

  /** The `Tag` of `TestClock`, used to find it in a `ZEnvironment`. */
  implicit val tag: Tag[TestClock] = Tag(EnvironmentTag.tagFromTagMacro[TestClock])

  private[test] object unsafe {
    def make(data: Data, live: Live, annotations: Annotations)(implicit unsafe: Unsafe): Test = {
      val warningState          = Ref.Synchronized.unsafe.make(WarningData.start)
      val suspendedWarningState = Ref.Synchronized.unsafe.make(SuspendedWarningData.start)
      val clockState            = Ref.unsafe.make(data)
      Test(clockState, live, annotations, warningState, suspendedWarningState)
    }

    def close(test: Test)(implicit trace: Trace): UIO[Unit] =
      test.warningDone *> test.suspendedWarningDone
  }

  /**
   * The `TestClock` implementation that the test environment installs. It keeps
   * the clock time, the pending sleeps and the time zone in `clockState`, and
   * reads the fibers of the current test from `annotations` to wait for them to
   * suspend. `warningState` and `suspendedWarningState` track the warnings it
   * prints when a test uses time without adjusting the clock, or adjusts the
   * clock while a fiber keeps running.
   */
  final case class Test(
    clockState: Ref.Atomic[TestClock.Data],
    live: Live,
    annotations: Annotations,
    warningState: Ref.Synchronized[TestClock.WarningData],
    suspendedWarningState: Ref.Synchronized[TestClock.SuspendedWarningData]
  ) extends TestClock
      with TestClockPlatformSpecific {

    /**
     * Makes the waits for the fibers of the test to suspend run one at a time.
     * See https://github.com/zio/zio/issues/9385
     */
    private[this] val freezeLock = Semaphore.unsafe.make(1)(Unsafe)

    /**
     * Increments the current clock time by the specified duration. Any effects
     * that were scheduled to occur on or before the new time will be run in
     * order. Before it moves the clock, and again after it resumes each
     * sleeping fiber, it waits until the other fibers of the test are done or
     * suspended. A negative `duration` moves the clock time back.
     */
    def adjust(duration: Duration)(implicit trace: Trace): UIO[Unit] =
      warningDone *> run(_.plus(duration))

    /**
     * Runs `zio` and, in parallel, increments the current clock time by the
     * specified duration as `adjust` does. Returns the result of `zio`.
     */
    def adjustWith[R, E, A](duration: Duration)(zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
      zio <& adjust(duration)

    /**
     * Returns the current clock time as an `OffsetDateTime`.
     */
    def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
      warningStart *> ZIO.succeed(unsafe.currentDateTime()(Unsafe.unsafe))

    /**
     * Returns the current clock time in the specified time unit.
     */
    def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
      warningStart *> ZIO.succeed(unsafe.currentTime(unit)(Unsafe.unsafe))

    /**
     * Returns the current clock time as the number of whole `unit`s since the
     * epoch.
     */
    def currentTime(unit: => ChronoUnit)(implicit trace: Trace, d: DummyImplicit): UIO[Long] =
      warningStart *> ZIO.succeed(unsafe.currentTime(unit)(Unsafe.unsafe))

    /**
     * Returns the current clock time in nanoseconds.
     */
    def nanoTime(implicit trace: Trace): UIO[Long] =
      warningStart *> ZIO.succeed(unsafe.nanoTime()(Unsafe.unsafe))

    /**
     * Returns the current clock time as an `Instant`.
     */
    def instant(implicit trace: Trace): UIO[Instant] =
      warningStart *> ZIO.succeed(unsafe.instant()(Unsafe.unsafe))

    /**
     * Constructs a `java.time.Clock` backed by this `TestClock`. Its `instant`
     * reads the current clock time each time it is called, so it follows later
     * adjustments; its zone is the time zone at the moment `javaClock` runs.
     */
    def javaClock(implicit trace: Trace): UIO[java.time.Clock] = {

      final case class JavaClock(clockState: Ref.Atomic[TestClock.Data], zoneId: ZoneId) extends java.time.Clock {
        def getZone(): ZoneId =
          zoneId
        def instant(): Instant =
          clockState.unsafe.get(Unsafe.unsafe).instant
        override def withZone(zoneId: ZoneId): JavaClock =
          copy(zoneId = zoneId)
      }

      clockState.get.map(data => JavaClock(clockState, data.timeZone))
    }

    /**
     * Returns the current clock time as a `LocalDateTime`.
     */
    def localDateTime(implicit trace: Trace): UIO[LocalDateTime] =
      warningStart *> ZIO.succeed(unsafe.localDateTime()(Unsafe.unsafe))

    /**
     * Saves the `TestClock`'s current state (clock time, time zone and pending
     * sleeps) in an effect which, when run, will restore the `TestClock` state
     * to the saved state. A sleep that started after the save is dropped from
     * the pending sleeps when the state is restored, and later adjustments do
     * not resume its fiber.
     */
    def save(implicit trace: Trace): UIO[UIO[Unit]] =
      for {
        clockData <- clockState.get
      } yield clockState.set(clockData)

    /**
     * Sets the current clock time to the specified `Instant`. Any effects that
     * were scheduled to occur on or before the new time will be run in order,
     * as `adjust` runs them. An instant earlier than the current clock time
     * moves the clock back and runs no effect.
     */
    def setTime(instant: Instant)(implicit trace: Trace): UIO[Unit] =
      warningDone *> run(_ => instant)

    /**
     * Sets the time zone to the specified time zone. The clock time in terms of
     * nanoseconds since the epoch will not be adjusted and no scheduled effects
     * will be run as a result of this method.
     */
    def setTimeZone(zone: ZoneId)(implicit trace: Trace): UIO[Unit] =
      clockState.update(_.copy(timeZone = zone))

    /**
     * Semantically blocks the current fiber until the clock time is equal to or
     * greater than the current clock time plus the specified duration. Once
     * `adjust` or `setTime` moves the clock time to or past that time, the
     * fiber is resumed. Returns at once when the duration is zero or negative,
     * and never returns when it is `Duration.Infinity`.
     */
    def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
      ZIO.suspendSucceedUnsafe { implicit unsafe =>
        val duration0 = duration
        if (duration0 <= Duration.Zero) Exit.unit
        else if (duration0 == Duration.Infinity) ZIO.never
        else {
          val promise = Promise.unsafe.make[Nothing, Unit](FiberId.None)
          val f       = warningStart *> promise.await
          clockState.unsafe.modify { data =>
            val end = data.instant.plus(duration0)
            (f, data.withWaiter(end, promise))
          }
        }
      }

    /**
     * Returns a list of the times at which the queued effects are scheduled to
     * resume. Sleeps scheduled since the last `adjust` or `setTime` come first,
     * most recently scheduled first; `adjust` and `setTime` sort the remaining
     * ones by end time. A sleep whose fiber was interrupted stays in the list
     * until the clock time reaches its end time.
     */
    def sleeps(implicit trace: Trace): UIO[List[Instant]] =
      clockState.get.map(_.sleeps.map(_._1))

    /**
     * Returns the time zone.
     */
    def timeZone(implicit trace: Trace): UIO[ZoneId] =
      clockState.get.map(_.timeZone)

    override def unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def currentTime(unit: TimeUnit)(implicit unsafe: Unsafe): Long =
          unit.convert(clockState.unsafe.get.instant.toEpochMilli, TimeUnit.MILLISECONDS)

        override def currentTime(unit: ChronoUnit)(implicit unsafe: Unsafe): Long =
          unit.between(Instant.EPOCH, clockState.unsafe.get.instant)

        override def currentDateTime()(implicit unsafe: Unsafe): OffsetDateTime = {
          val data = clockState.unsafe.get
          OffsetDateTime.ofInstant(data.instant, data.timeZone)
        }

        override def instant()(implicit unsafe: Unsafe): Instant =
          clockState.unsafe.get.instant

        override def localDateTime()(implicit unsafe: Unsafe): LocalDateTime = {
          val data = clockState.unsafe.get
          LocalDateTime.ofInstant(data.instant, data.timeZone)
        }

        override def nanoTime()(implicit unsafe: Unsafe): Long =
          currentTime(ChronoUnit.NANOS)
      }

    /**
     * Cancels the pending warning message that is displayed if a test is
     * advancing the `TestClock` but a fiber is not suspending, and resets its
     * state to `Start`.
     */
    private[TestClock] def suspendedWarningDone(implicit trace: Trace): UIO[Unit] =
      suspendedWarningState.updateSomeZIO { case SuspendedWarningData.Pending(cancel) =>
        ZIO.succeed {
          cancel()
          SuspendedWarningData.start
        }
      }

    /**
     * Cancels the warning message that is displayed if a test is using time but
     * is not advancing the `TestClock`, and sets its state to `Done` so that
     * the warning is not scheduled again.
     */
    private[TestClock] def warningDone(implicit trace: Trace): UIO[Unit] =
      warningState.updateSomeZIO {
        case WarningData.Pending(cancelWarning) =>
          ZIO.succeed {
            cancelWarning()
            WarningData.done
          }
        case _: WarningData.Start.type => Exit.succeed(WarningData.done)
      }

    /**
     * Polls until the fibers of this test other than the current fiber are done
     * or suspended, with the same suspended statuses in two snapshots taken
     * `waitFor` apart in real time.
     */
    private def awaitSuspended(waitFor: Duration)(implicit trace: Trace): UIO[Unit] =
      freezeLock.withPermit(ZIO.suspendSucceed {
        val ref = new AtomicBoolean(false)

        def allSuspendedUnchanged(
          l: collection.Map[FiberId, Fiber.Status.Suspended],
          r: collection.Map[FiberId, Fiber.Status.Suspended]
        ): Boolean = {
          val it = r.iterator
          while (it.hasNext) {
            val rkv = it.next()
            val lv  = l.getOrElse(rkv._1, null) // If missing, it wasn't previously created, so we have to return false
            if (lv != rkv._2) return false
          }
          true
        }

        // Sleep to give suspended fibers a chance to resume
        val freeze = this.freeze
        val f      = ClockLive.sleep(waitFor) *> freeze
        f.zipWith(f)(allSuspendedUnchanged)
          .flatMap {
            if (_) Exit.boolean(ref.get)
            else if (ref.compareAndSet(false, true)) suspendedWarningStart *> Exit.failUnit
            else Exit.failUnit
          }
          .eventually
          .flatMap(if (_) suspendedWarningDone else Exit.unit)
      })

    /**
     * Captures a "snapshot" of the identifier and status of all fibers in this
     * test other than the current fiber that are currently suspended. Fails
     * with the `Unit` value if any of these fibers are not done or suspended.
     * Note that because we cannot synchronize on the status of multiple fibers
     * at the same time, this snapshot may not be fully consistent.
     */
    private def freeze: IO[Unit, collection.Map[FiberId, Fiber.Status.Suspended]] = {
      implicit val trace: Trace = Trace.empty

      def collectSuspended(
        refs: Chunk[AtomicReference[SortedSet[Fiber.Runtime[Any, Any]]]],
        fiberId: FiberId.Runtime
      ): Exit[Unit, collection.Map[FiberId, Fiber.Status.Suspended]] = {
        val map = mu.HashMap.empty[FiberId, Fiber.Status.Suspended]
        val it  = refs.iterator.flatMap(_.get())

        while (it.hasNext) {
          val f = it.next().asInstanceOf[FiberRuntime[Any, Any]]
          if (f.id ne fiberId) f.getStatus() match {
            case s: Fiber.Status.Suspended => map.update(f.id, s)
            case _: Fiber.Status.Running   => return Exit.failUnit
            case _                         => ()
          }
        }
        Exit.succeed(map)
      }

      annotations.get(TestAnnotation.fibers).flatMap {
        case Right(refs) => ZIO.fiberIdWith(collectSuspended(refs, _))
        case _           => Exit.succeed(Map.empty)
      }
    }

    /**
     * Returns the set of fibers in this test, other than the calling fiber.
     */
    def supervisedFibers(implicit trace: Trace): UIO[SortedSet[Fiber.Runtime[Any, Any]]] =
      annotations.supervisedFibers

    /**
     * Runs all effects scheduled to occur on or before the specified instant,
     * which may depend on the current time, in order.
     */
    private def run(f: Instant => Instant)(implicit trace: Trace): UIO[Unit] = {
      // Long wait for fibers to settle down
      def loop(f: Instant => Instant, waitFor: Duration): UIO[Unit] =
        awaitSuspended(waitFor) *>
          clockState.modify { data =>
            val end = f(data.instant)
            data.sleeps.sortBy(_._1) match {
              case (instant, promise) :: sleeps if !end.isBefore(instant) =>
                (
                  Some((end, promise)),
                  Data(instant, sleeps, data.timeZone)
                )
              case sleeps =>
                (
                  None,
                  Data(end, sleeps, data.timeZone)
                )
            }
          }.flatMap {
            case Some((end, promise)) =>
              promise.unsafe.succeedUnit(implicitly, trace, Unsafe)
              // We don't need to await for long here because we're waiting for a single fiber and it will resume instantly
              loop(_ => end, 2.millis)
            case _ => Exit.unit
          }
      loop(f, 5.millis)
    }

    /**
     * Schedules a task on the global scheduler that prints a warning message to
     * standard output after 5 seconds of real time, because a test is advancing
     * the `TestClock` but a fiber is not suspending.
     */
    private def suspendedWarningStart(implicit trace: Trace): UIO[Unit] =
      suspendedWarningState.updateSomeZIO { case SuspendedWarningData.Start =>
        ZIO.succeedUnsafe { implicit unsafe =>
          val cancel = Clock.globalScheduler.schedule(
            () => {
              val f = ZIO.suspendSucceed {
                logMessageWithTrace(suspendedWarning)
                suspendedWarningState.set(SuspendedWarningData.done)
              }
              Runtime.default.unsafe.run(f)
            },
            5.seconds
          )
          SuspendedWarningData.pending(cancel)
        }
      }

    /**
     * Schedules a task on the global scheduler that prints a warning message to
     * standard output after 5 seconds of real time, because the test is using
     * time; `adjust` and `setTime` cancel it. Does nothing unless the warning
     * state is `Start`.
     */
    private def warningStart(implicit trace: Trace): UIO[Unit] =
      warningState.updateSomeZIO { case _: WarningData.Start.type =>
        ZIO.succeed {
          val cancel = Clock.globalScheduler.schedule(() => logMessageWithTrace(warning), 5.seconds)(Unsafe)
          WarningData.pending(cancel)
        }
      }

  }

  /**
   * Returns a layer that constructs a new `Test` object from `data`, installs
   * it as the `Clock` service for the layer's scope, and provides it as the
   * `TestClock`. When the scope closes, any pending warning message is
   * cancelled.
   */
  def live(
    data: Data
  )(implicit
    trace: Trace
  ): ZLayer[Annotations with Live, Nothing, TestClock] =
    ZLayer.scoped {
      ZIO.environmentWithZIO { env =>
        val scope = env.getScope
        val test  = unsafe.make(data, env.get[Live], env.get[Annotations])(Unsafe)
        ZIO.withClockScoped(test) *> scope.addFinalizer(unsafe.close(test)).as(test)
      }
    }

  /** A layer that passes on the `TestClock` that its input already holds. */
  val any: ZLayer[TestClock, Nothing, TestClock] =
    ZLayer.environment[TestClock](Tracer.newTrace)

  private[test] val DefaultData: Data =
    Data(Instant.EPOCH, Nil, ZoneId.of("UTC"))

  /**
   * A layer built with `live` whose clock time starts at the epoch
   * (1970-01-01T00:00:00Z) in the `UTC` time zone, with no pending sleeps.
   */
  val default: ZLayer[Live with Annotations, Nothing, TestClock] =
    live(DefaultData)(Tracer.newTrace)

  /**
   * Accesses the current `TestClock` and increments the time by the specified
   * duration, running any actions scheduled for on or before the new time in
   * order.
   *
   * {{{
   * import zio._
   * import zio.test._
   *
   * for {
   *   fiber  <- ZIO.sleep(10.seconds).as("done").fork
   *   _      <- TestClock.adjust(10.seconds)
   *   result <- fiber.join
   * } yield assertTrue(result == "done")
   * }}}
   */
  def adjust(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
    testClockWith(_.adjust(duration))

  /**
   * Accesses the current `TestClock`, runs `zio` and, in parallel, increments
   * the time by the specified duration. Returns the result of `zio`.
   */
  def adjustWith[R, E, A](duration: => Duration)(zio: ZIO[R, E, A])(implicit
    trace: Trace
  ): ZIO[R, E, A] =
    testClockWith(_.adjustWith(duration)(zio))

  /**
   * Accesses the current `TestClock` and saves the clock state (clock time,
   * time zone and pending sleeps) in an effect which, when run, will restore
   * the `TestClock` to the saved state.
   */
  def save(implicit trace: Trace): UIO[UIO[Unit]] =
    testClockWith(_.save)

  /**
   * Accesses the current `TestClock` and sets the clock time to the specified
   * `Instant`, running any actions scheduled for on or before the new time in
   * order.
   */
  def setTime(instant: => Instant)(implicit trace: Trace): UIO[Unit] =
    testClockWith(_.setTime(instant))

  /**
   * Accesses the current `TestClock`, setting the time zone to the specified
   * time zone. The clock time in terms of nanoseconds since the epoch will not
   * be altered and no scheduled actions will be run as a result of this effect.
   */
  def setTimeZone(zone: => ZoneId)(implicit trace: Trace): UIO[Unit] =
    testClockWith(_.setTimeZone(zone))

  /**
   * Accesses the current `TestClock` and returns the end times of its pending
   * sleeps: those scheduled since the last `adjust` or `setTime` first, most
   * recently scheduled first, then the rest sorted by end time.
   */
  def sleeps(implicit trace: Trace): UIO[List[Instant]] =
    testClockWith(_.sleeps)

  /**
   * Accesses the current `TestClock` and returns the current time zone.
   */
  def timeZone(implicit trace: Trace): UIO[ZoneId] =
    testClockWith(_.timeZone)

  /**
   * `Data` represents the state of the `TestClock`: the clock time, the pending
   * sleeps (each an end time and the promise that resumes the sleeping fiber)
   * and the time zone.
   */
  final case class Data(
    instant: Instant,
    sleeps: List[(Instant, Promise[Nothing, Unit])],
    timeZone: ZoneId
  ) {

    /**
     * Returns a copy of this state with a sleep that ends at `instant` and
     * completes `promise` added to the front of `sleeps`.
     */
    def withWaiter(instant: Instant, promise: Promise[Nothing, Unit]): Data =
      copy(sleeps = (instant, promise) :: sleeps)
  }

  /**
   * `Sleep` represents a scheduled effect: a duration, a promise that can be
   * completed to resume execution of the effect, and the id of the fiber
   * executing the effect. `TestClock` keeps its own pending sleeps in
   * `Data.sleeps` and does not use this type.
   */
  final case class Sleep(duration: Duration, promise: Promise[Nothing, Unit], fiberId: FiberId)

  /**
   * `WarningData` describes the state of the warning message that is displayed
   * if a test is using time but is not advancing the `TestClock`. The possible
   * states are `Start` if a test has not used time, `Pending` if a test has
   * used time but has not adjusted the `TestClock` (it stays `Pending` after
   * the warning message is displayed), and `Done` if a test has adjusted the
   * `TestClock`.
   */
  private[TestClock] sealed abstract class WarningData

  private object WarningData {

    case object Start                                      extends WarningData
    final case class Pending(cancelWarning: () => Boolean) extends WarningData
    case object Done                                       extends WarningData

    /**
     * State indicating that a test has not used time.
     */
    val start: WarningData = Start

    /**
     * State indicating that a test has used time but has not adjusted the
     * `TestClock`, with the function that cancels the scheduled task that will
     * display the warning message.
     */
    def pending(cancel: () => Boolean): WarningData = Pending(cancel)

    /**
     * State indicating that a test has adjusted or set the `TestClock` time, so
     * the warning message will not be displayed.
     */
    val done: WarningData = Done
  }

  private[TestClock] sealed abstract class SuspendedWarningData

  private object SuspendedWarningData {

    case object Start                                      extends SuspendedWarningData
    final case class Pending(cancelWarning: () => Boolean) extends SuspendedWarningData
    case object Done                                       extends SuspendedWarningData

    /**
     * State indicating that no warning message is pending: none was scheduled,
     * or the scheduled one was cancelled because the fibers suspended.
     */
    val start: SuspendedWarningData = Start

    /**
     * State indicating that a test has adjusted the clock but a fiber is still
     * running, with the function that cancels the scheduled task that will
     * display the warning message.
     */
    def pending(cancelToken: () => Boolean): SuspendedWarningData = Pending(cancelToken)

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
