/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

package zio

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.lang.{System => JSystem}
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit

/**
 * A service for reading the current time and for sleeping. ZIO workflows read
 * it from their default services: the accessors in the `Clock` object and
 * `ZIO.sleep` use the clock of the current workflow, which is
 * [[Clock.ClockLive]] unless it is replaced, for example with `ZIO.withClock`
 * or, in ZIO Test, with `TestClock`.
 *
 * Read the time and sleep through `Clock` rather than through
 * `java.lang.System`, `java.time` or `Thread.sleep`: a sleep suspends the fiber
 * without blocking a thread, and tests can move a `TestClock` forward instead
 * of waiting.
 *
 * {{{
 * import java.util.concurrent.TimeUnit
 *
 * val elapsedMillis: UIO[Long] =
 *   for {
 *     start <- Clock.currentTime(TimeUnit.MILLISECONDS)
 *     _     <- Clock.sleep(20.millis)
 *     end   <- Clock.currentTime(TimeUnit.MILLISECONDS)
 *   } yield end - start
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/services/clock/ Clock]]
 */
trait Clock extends Serializable { self =>

  /**
   * Returns the current time as the number of whole `unit`s since the Unix
   * epoch.
   */
  def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long]

  /**
   * Returns the current time as the number of whole `unit`s since the Unix
   * epoch. `ClockLive` and `ClockJava` accept units from `NANOS` to `DAYS`;
   * with a longer unit such as `ChronoUnit.WEEKS` the effect dies with
   * `java.time.temporal.UnsupportedTemporalTypeException`.
   */
  def currentTime(unit: => ChronoUnit)(implicit trace: Trace, d: DummyImplicit): UIO[Long]

  /**
   * Returns the current date and time with the offset of the clock's time zone.
   */
  def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime]

  /** Returns the current instant. */
  def instant(implicit trace: Trace): UIO[java.time.Instant]

  /**
   * Returns a `java.time.Clock` that reads the time of this service, for Java
   * APIs that take one.
   */
  def javaClock(implicit trace: Trace): UIO[java.time.Clock]

  /**
   * Returns the current date and time in the clock's time zone, without an
   * offset.
   */
  def localDateTime(implicit trace: Trace): UIO[java.time.LocalDateTime]

  /**
   * Returns a time reading in nanoseconds for measuring elapsed time. With
   * `ClockLive` it is `java.lang.System.nanoTime`, whose origin is arbitrary,
   * so only the difference between two readings is meaningful.
   */
  def nanoTime(implicit trace: Trace): UIO[Long]

  /** Returns the [[Scheduler]] that this clock uses to run delayed tasks. */
  def scheduler(implicit trace: Trace): UIO[Scheduler]

  /**
   * Returns an effect that completes after `duration` has passed. The live
   * clock suspends the fiber without blocking a thread.
   */
  def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit]

  /**
   * Synchronous versions of the clock's time readings, for code outside ZIO.
   * Each method returns its value directly instead of an effect, and needs an
   * implicit `Unsafe` value.
   */
  trait UnsafeAPI extends Serializable {

    /**
     * Returns the current time as the number of whole `unit`s since the Unix
     * epoch.
     */
    def currentTime(unit: TimeUnit)(implicit unsafe: Unsafe): Long

    /**
     * Returns the current time as the number of whole `unit`s since the Unix
     * epoch.
     */
    def currentTime(unit: ChronoUnit)(implicit unsafe: Unsafe): Long

    /**
     * Returns the current date and time with the offset of the clock's time
     * zone.
     */
    def currentDateTime()(implicit unsafe: Unsafe): OffsetDateTime

    /** Returns the current instant. */
    def instant()(implicit unsafe: Unsafe): Instant

    /**
     * Returns the current date and time in the clock's time zone, without an
     * offset.
     */
    def localDateTime()(implicit unsafe: Unsafe): LocalDateTime

    /** Returns a time reading in nanoseconds for measuring elapsed time. */
    def nanoTime()(implicit unsafe: Unsafe): Long
  }

  /**
   * Returns the synchronous API of this clock. This default runs each effect of
   * the clock on `Runtime.default` and throws a `zio.FiberFailure` when the
   * effect fails or dies; `ClockLive` and `ClockJava` override it to read the
   * time directly.
   */
  def unsafe: UnsafeAPI =
    new UnsafeAPI {
      def currentTime(unit: TimeUnit)(implicit unsafe: Unsafe): Long =
        Runtime.default.unsafe.run(self.currentTime(unit)(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def currentTime(unit: ChronoUnit)(implicit unsafe: Unsafe): Long =
        Runtime.default.unsafe
          .run(self.currentTime(unit)(Trace.empty, DummyImplicit.dummyImplicit))(Trace.empty, unsafe)
          .getOrThrowFiberFailure()

      def currentDateTime()(implicit unsafe: Unsafe): OffsetDateTime =
        Runtime.default.unsafe.run(self.currentDateTime(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def instant()(implicit unsafe: Unsafe): Instant =
        Runtime.default.unsafe.run(self.instant(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def localDateTime()(implicit unsafe: Unsafe): LocalDateTime =
        Runtime.default.unsafe.run(self.localDateTime(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()

      def nanoTime()(implicit unsafe: Unsafe): Long =
        Runtime.default.unsafe.run(self.nanoTime(Trace.empty))(Trace.empty, unsafe).getOrThrowFiberFailure()
    }
}

/**
 * Accessors for the `Clock` service of the current workflow, and the built-in
 * implementations [[Clock.ClockLive]] and [[Clock.ClockJava]].
 *
 * @see
 *   [[https://zio.dev/reference/services/clock/ Clock]]
 */
object Clock extends ClockPlatformSpecific with ClockSyntaxPlatformSpecific with Serializable {

  /** The `Tag` that identifies the `Clock` service in a ZIO environment. */
  implicit val tag: Tag[Clock] = Tag(EnvironmentTag.tagFromTagMacro[Clock])

  private[this] val rightExitUnit = Right(Exit.unit)

  /**
   * A `Clock` that reads the time from a `java.time.Clock`, for example a fixed
   * clock in a test: `ZIO.withClock(Clock.ClockJava(fixedClock))`. Only the
   * time readings use `clock`; `sleep` and `scheduler` are those of
   * [[Clock.ClockLive]] and wait in real time.
   *
   * @param clock
   *   the Java clock that supplies the instant and the time zone
   */
  final case class ClockJava(clock: java.time.Clock) extends Clock {

    /**
     * Returns `OffsetDateTime.now(clock)`: the instant of `clock` with the
     * offset of its time zone.
     */
    def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
      ZIO.succeed(unsafe.currentDateTime()(Unsafe.unsafe))

    /**
     * Returns the whole `unit`s from the Unix epoch to the instant of `clock`,
     * truncated toward zero.
     */
    def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.currentTime(unit)(Unsafe.unsafe))

    /**
     * Returns the whole `unit`s from the Unix epoch to the instant of `clock`,
     * truncated toward zero. With a unit longer than `DAYS` the effect dies
     * with `java.time.temporal.UnsupportedTemporalTypeException`.
     */
    def currentTime(unit: => ChronoUnit)(implicit trace: Trace, d: DummyImplicit): UIO[Long] =
      ZIO.succeed(unsafe.currentTime(unit)(Unsafe.unsafe))

    /** Returns `clock.instant()`. */
    def instant(implicit trace: Trace): UIO[Instant] =
      ZIO.succeed(unsafe.instant()(Unsafe.unsafe))

    /** Returns the wrapped `clock`. */
    def javaClock(implicit trace: Trace): UIO[java.time.Clock] =
      ZIO.succeed(clock)

    /** Returns `LocalDateTime.now(clock)`, in the time zone of `clock`. */
    def localDateTime(implicit trace: Trace): UIO[LocalDateTime] =
      ZIO.succeed(unsafe.localDateTime()(Unsafe.unsafe))

    /**
     * Returns the nanoseconds from the Unix epoch to the instant of `clock`.
     * Unlike `ClockLive.nanoTime`, it follows the time of `clock`.
     */
    def nanoTime(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nanoTime()(Unsafe.unsafe))

    /**
     * Sleeps with [[Clock.ClockLive]], in real time; `clock` does not affect
     * it.
     */
    def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
      ClockLive.sleep(duration)

    /** Returns the scheduler of [[Clock.ClockLive]]. */
    def scheduler(implicit trace: Trace): UIO[Scheduler] =
      ClockLive.scheduler

    /** Reads the time from `clock` directly, without running an effect. */
    override val unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def currentTime(unit: TimeUnit)(implicit unsafe: Unsafe): Long =
          currentTime(toChronoUnit(unit))

        override def currentTime(unit: ChronoUnit)(implicit unsafe: Unsafe): Long =
          unit.between(Instant.EPOCH, instant())

        override def currentDateTime()(implicit unsafe: Unsafe): OffsetDateTime =
          OffsetDateTime.now(clock)

        override def instant()(implicit unsafe: Unsafe): Instant =
          clock.instant()

        override def localDateTime()(implicit unsafe: Unsafe): LocalDateTime =
          LocalDateTime.now(clock)

        override def nanoTime()(implicit unsafe: Unsafe): Long =
          currentTime(TimeUnit.NANOSECONDS)
      }
  }

  /**
   * The default `Clock`. It reads the system clock (`Instant.now()`,
   * `java.lang.System.nanoTime`) in the system default time zone, and sleeps
   * with a global scheduler instead of blocking a thread. On the JVM and Scala
   * Native the scheduler runs on one daemon thread; on Scala.js it calls
   * `setTimeout` with the duration in whole milliseconds (`toMillis`).
   */
  object ClockLive extends Clock {

    /**
     * Returns the whole `unit`s from the Unix epoch to `Instant.now()`,
     * truncated toward zero.
     */
    def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.currentTime(unit)(Unsafe.unsafe))

    /**
     * Returns the whole `unit`s from the Unix epoch to `Instant.now()`,
     * truncated toward zero. With a unit longer than `DAYS` the effect dies
     * with `java.time.temporal.UnsupportedTemporalTypeException`.
     */
    def currentTime(unit: => ChronoUnit)(implicit trace: Trace, d: DummyImplicit): UIO[Long] =
      ZIO.succeed(unsafe.currentTime(unit)(Unsafe.unsafe))

    /**
     * Returns `java.lang.System.nanoTime`: nanoseconds from an arbitrary
     * origin, for measuring elapsed time rather than reading the time of day.
     */
    def nanoTime(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafe.nanoTime()(Unsafe.unsafe))

    /**
     * Suspends the fiber for `duration` without blocking a thread, by
     * registering a wake-up with the global scheduler. A zero or negative
     * duration resumes at once without scheduling anything, `Duration.Infinity`
     * never resumes, and interrupting the fiber cancels the wake-up.
     */
    def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
      ZIO.asyncInterrupt { cb =>
        val d = duration
        if (d.isZero || d.isNegative) {
          // No need to sleep, resume synchronously
          rightExitUnit
        } else {
          val canceler = globalScheduler.schedule(() => cb(ZIO.unit), d)(Unsafe.unsafe)
          Left(ZIO.succeed(canceler()))
        }
      }

    /** Returns `OffsetDateTime.now()`, in the system default time zone. */
    def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
      ZIO.succeed(unsafe.currentDateTime()(Unsafe.unsafe))

    /** Returns `Instant.now()`. */
    override def instant(implicit trace: Trace): UIO[Instant] =
      ZIO.succeed(unsafe.instant()(Unsafe.unsafe))

    /** Returns `LocalDateTime.now()`, in the system default time zone. */
    override def localDateTime(implicit trace: Trace): UIO[LocalDateTime] =
      ZIO.succeed(unsafe.localDateTime()(Unsafe.unsafe))

    /** Returns the global scheduler that `sleep` uses. */
    def scheduler(implicit trace: Trace): UIO[Scheduler] =
      ZIO.succeed(globalScheduler)

    /**
     * Returns a `java.time.Clock` in the system default time zone whose
     * `instant()` is `Instant.now()`.
     */
    def javaClock(implicit trace: Trace): UIO[java.time.Clock] = {

      final case class JavaClock(zoneId: ZoneId) extends java.time.Clock {
        def getZone(): ZoneId =
          zoneId
        def instant(): Instant =
          Instant.now
        override def withZone(zoneId: ZoneId): JavaClock =
          copy(zoneId = zoneId)
      }

      ZIO.succeed(JavaClock(ZoneId.systemDefault))
    }

    /** Reads the system clock directly, without running an effect. */
    override val unsafe: UnsafeAPI =
      new UnsafeAPI {
        override def currentTime(unit: TimeUnit)(implicit unsafe: Unsafe): Long =
          currentTime(toChronoUnit(unit))

        override def currentTime(unit: ChronoUnit)(implicit unsafe: Unsafe): Long =
          unit.between(Instant.EPOCH, instant())

        override def currentDateTime()(implicit unsafe: Unsafe): OffsetDateTime =
          OffsetDateTime.now()

        override def instant()(implicit unsafe: Unsafe): Instant =
          Instant.now()

        override def localDateTime()(implicit unsafe: Unsafe): LocalDateTime =
          LocalDateTime.now()

        override def nanoTime()(implicit unsafe: Unsafe): Long =
          JSystem.nanoTime
      }
  }

  /**
   * Returns the current time as the number of whole `unit`s since the Unix
   * epoch, from the `Clock` of the current workflow.
   */
  def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
    ZIO.clockWith(_.currentTime(unit))

  /**
   * Returns the current time as the number of whole `unit`s since the Unix
   * epoch, from the `Clock` of the current workflow. With `ClockLive`, a unit
   * longer than `DAYS` (such as `ChronoUnit.WEEKS`) makes the effect die with
   * `java.time.temporal.UnsupportedTemporalTypeException`.
   */
  def currentTime(unit: => ChronoUnit)(implicit trace: Trace, d: DummyImplicit): UIO[Long] =
    ZIO.clockWith(_.currentTime(unit))

  /**
   * Returns the current date and time with the offset of the clock's time zone,
   * from the `Clock` of the current workflow. `ClockLive` uses the system
   * default time zone.
   */
  def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
    ZIO.clockWith(_.currentDateTime)

  /** Returns the current instant from the `Clock` of the current workflow. */
  def instant(implicit trace: Trace): UIO[java.time.Instant] =
    ZIO.clockWith(_.instant)

  /**
   * Returns a `java.time.Clock` that reads the time of the `Clock` of the
   * current workflow, for Java APIs that take one.
   */
  def javaClock(implicit trace: Trace): UIO[java.time.Clock] =
    ZIO.clockWith(_.javaClock)

  /**
   * Returns the current date and time in the clock's time zone, without an
   * offset, from the `Clock` of the current workflow.
   */
  def localDateTime(implicit trace: Trace): UIO[java.time.LocalDateTime] =
    ZIO.clockWith(_.localDateTime)

  /**
   * Returns a time reading in nanoseconds from the `Clock` of the current
   * workflow, for measuring elapsed time. With `ClockLive` it is
   * `java.lang.System.nanoTime`, whose origin is arbitrary, so it does not give
   * a date; only the difference between two readings is meaningful.
   */
  def nanoTime(implicit trace: Trace): UIO[Long] =
    ZIO.clockWith(_.nanoTime)

  /**
   * Returns the [[Scheduler]] of the `Clock` of the current workflow. With
   * `ClockLive` it is the global scheduler that also wakes `sleep`.
   */
  def scheduler(implicit trace: Trace): UIO[Scheduler] =
    ZIO.clockWith(_.scheduler)

  /**
   * Returns an effect that completes after `duration` has passed, using the
   * `Clock` of the current workflow. With `ClockLive` the fiber is suspended
   * without blocking a thread; a zero or negative duration completes at once,
   * `Duration.Infinity` never completes, and interrupting the fiber cancels the
   * sleep.
   *
   * {{{
   * val delayed: UIO[String] = Clock.sleep(50.millis).as("done")
   * }}}
   */
  def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
    ZIO.clockWith(_.sleep(duration))

}
