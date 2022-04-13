/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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

import zio.internal.stacktracer.Tracer
import zio.Scheduler
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.Schedule.Decision._

import java.lang.{System => JSystem}
import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneId}
import java.util.concurrent.TimeUnit

trait Clock extends Serializable { self =>

  def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long]

  def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime]

  def instant(implicit trace: Trace): UIO[java.time.Instant]

  def javaClock(implicit trace: Trace): UIO[java.time.Clock]

  def localDateTime(implicit trace: Trace): UIO[java.time.LocalDateTime]

  def nanoTime(implicit trace: Trace): UIO[Long]

  def scheduler(implicit trace: Trace): UIO[Scheduler]

  def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit]

  private[zio] def unsafeCurrentTime(unit: TimeUnit): Long =
    Runtime.default.unsafeRun(currentTime(unit)(Trace.empty))(Trace.empty)

  private[zio] def unsafeCurrentDateTime(): OffsetDateTime =
    Runtime.default.unsafeRun(currentDateTime(Trace.empty))(Trace.empty)

  private[zio] def unsafeInstant(): Instant =
    Runtime.default.unsafeRun(instant(Trace.empty))(Trace.empty)

  private[zio] def unsafeLocalDateTime(): LocalDateTime =
    Runtime.default.unsafeRun(localDateTime(Trace.empty))(Trace.empty)

  private[zio] def unsafeNanoTime(): Long =
    Runtime.default.unsafeRun(nanoTime(Trace.empty))(Trace.empty)
}

object Clock extends ClockPlatformSpecific with Serializable {

  val any: ZLayer[Clock, Nothing, Clock] =
    ZLayer.service[Clock](Tag[Clock], Tracer.newTrace)

  /**
   * Constructs a `Clock` service from a `java.time.Clock`.
   */
  val javaClock: ZLayer[java.time.Clock, Nothing, Clock] = {
    implicit val trace = Tracer.newTrace
    ZLayer[java.time.Clock, Nothing, Clock] {
      for {
        clock <- ZIO.service[java.time.Clock]
      } yield ClockJava(clock)
    }
  }

  val live: Layer[Nothing, Clock] =
    ZLayer.succeed[Clock](ClockLive)(Tag[Clock], Tracer.newTrace)

  /**
   * An implementation of the `Clock` service backed by a `java.time.Clock`.
   */
  final case class ClockJava(clock: java.time.Clock) extends Clock {
    def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
      ZIO.succeed(unsafeCurrentDateTime())
    def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeCurrentTime(unit))
    def instant(implicit trace: Trace): UIO[Instant] =
      ZIO.succeed(unsafeInstant())
    def javaClock(implicit trace: Trace): UIO[java.time.Clock] =
      ZIO.succeed(clock)
    def localDateTime(implicit trace: Trace): UIO[LocalDateTime] =
      ZIO.succeed(unsafeLocalDateTime())
    def nanoTime(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeNanoTime())
    def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
      ZIO.asyncInterrupt { cb =>
        val canceler = globalScheduler.unsafeSchedule(() => cb(UIO.unit), duration)
        Left(UIO.succeed(canceler()))
      }
    def scheduler(implicit trace: Trace): UIO[Scheduler] =
      ZIO.succeed(globalScheduler)
    override private[zio] def unsafeCurrentTime(unit: TimeUnit): Long = {
      val instant = unsafeInstant()
      unit match {
        case TimeUnit.NANOSECONDS =>
          instant.getEpochSecond * 1000000000 + instant.getNano
        case TimeUnit.MICROSECONDS =>
          instant.getEpochSecond * 1000000 + instant.getNano / 1000
        case _ => unit.convert(instant.toEpochMilli, TimeUnit.MILLISECONDS)
      }
    }
    override private[zio] def unsafeCurrentDateTime(): OffsetDateTime =
      OffsetDateTime.now(clock)
    override private[zio] def unsafeInstant(): Instant =
      clock.instant()
    override private[zio] def unsafeLocalDateTime(): LocalDateTime =
      LocalDateTime.now(clock)
    override private[zio] def unsafeNanoTime(): Long =
      unsafeCurrentTime(TimeUnit.NANOSECONDS)
  }

  object ClockLive extends Clock {
    def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeCurrentTime(unit))

    def nanoTime(implicit trace: Trace): UIO[Long] =
      ZIO.succeed(unsafeNanoTime())

    def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
      UIO.asyncInterrupt { cb =>
        val canceler = globalScheduler.unsafeSchedule(() => cb(UIO.unit), duration)
        Left(UIO.succeed(canceler()))
      }

    def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
      ZIO.succeed(unsafeCurrentDateTime())

    override def instant(implicit trace: Trace): UIO[Instant] =
      ZIO.succeed(unsafeInstant())

    override def localDateTime(implicit trace: Trace): UIO[LocalDateTime] =
      ZIO.succeed(unsafeLocalDateTime())

    def scheduler(implicit trace: Trace): UIO[Scheduler] =
      ZIO.succeed(globalScheduler)

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

    override private[zio] def unsafeCurrentTime(unit: TimeUnit): Long = {
      val inst = unsafeInstant()
      // A nicer solution without loss of precision or range would be
      // unit.toChronoUnit.between(Instant.EPOCH, inst)
      // However, ChronoUnit is not available on all platforms
      unit match {
        case TimeUnit.NANOSECONDS =>
          inst.getEpochSecond() * 1000000000 + inst.getNano()
        case TimeUnit.MICROSECONDS =>
          inst.getEpochSecond() * 1000000 + inst.getNano() / 1000
        case _ => unit.convert(inst.toEpochMilli(), TimeUnit.MILLISECONDS)
      }
    }

    override private[zio] def unsafeCurrentDateTime(): OffsetDateTime =
      OffsetDateTime.now()

    override private[zio] def unsafeInstant(): Instant =
      Instant.now()

    override private[zio] def unsafeLocalDateTime(): LocalDateTime =
      LocalDateTime.now()

    override private[zio] def unsafeNanoTime(): Long =
      JSystem.nanoTime
  }

  /**
   * Returns the current time, relative to the Unix epoch.
   */
  def currentTime(unit: => TimeUnit)(implicit trace: Trace): UIO[Long] =
    ZIO.clockWith(_.currentTime(unit))

  /**
   * Get the current time, represented in the current timezone.
   */
  def currentDateTime(implicit trace: Trace): UIO[OffsetDateTime] =
    ZIO.clockWith(_.currentDateTime)

  def instant(implicit trace: Trace): UIO[java.time.Instant] =
    ZIO.clockWith(_.instant)

  /**
   * Constructs a `java.time.Clock` backed by the `Clock` service.
   */
  def javaClock(implicit trace: Trace): UIO[java.time.Clock] =
    ZIO.clockWith(_.javaClock)

  def localDateTime(implicit trace: Trace): UIO[java.time.LocalDateTime] =
    ZIO.clockWith(_.localDateTime)

  /**
   * Returns the system nano time, which is not relative to any date.
   */
  def nanoTime(implicit trace: Trace): UIO[Long] =
    ZIO.clockWith(_.nanoTime)

  /**
   * Returns the scheduler used for scheduling effects.
   */
  def scheduler(implicit trace: Trace): UIO[Scheduler] =
    ZIO.clockWith(_.scheduler)

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
    ZIO.clockWith(_.sleep(duration))

}
