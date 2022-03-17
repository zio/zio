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
import java.time.{Instant, LocalDateTime, OffsetDateTime}
import java.util.concurrent.TimeUnit

trait Clock extends Serializable {

  def currentTime(unit: => TimeUnit)(implicit trace: ZTraceElement): UIO[Long]

  def currentDateTime(implicit trace: ZTraceElement): UIO[OffsetDateTime]

  def instant(implicit trace: ZTraceElement): UIO[java.time.Instant]

  def localDateTime(implicit trace: ZTraceElement): UIO[java.time.LocalDateTime]

  def nanoTime(implicit trace: ZTraceElement): UIO[Long]

  def scheduler(implicit trace: ZTraceElement): UIO[Scheduler]

  def sleep(duration: => Duration)(implicit trace: ZTraceElement): UIO[Unit]
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
    def currentDateTime(implicit trace: ZTraceElement): UIO[OffsetDateTime] =
      ZIO.succeed(OffsetDateTime.now(clock))
    def currentTime(unit0: => TimeUnit)(implicit trace: ZTraceElement): UIO[Long] =
      ZIO.suspendSucceed {
        val unit = unit0
        instant.map { instant =>
          unit match {
            case TimeUnit.NANOSECONDS =>
              instant.getEpochSecond * 1000000000 + instant.getNano
            case TimeUnit.MICROSECONDS =>
              instant.getEpochSecond * 1000000 + instant.getNano / 1000
            case _ => unit.convert(instant.toEpochMilli, TimeUnit.MILLISECONDS)
          }
        }
      }
    def instant(implicit trace: ZTraceElement): UIO[Instant] =
      ZIO.succeed(clock.instant())
    def localDateTime(implicit trace: ZTraceElement): UIO[LocalDateTime] =
      ZIO.succeed(LocalDateTime.now(clock))
    def nanoTime(implicit trace: ZTraceElement): UIO[Long] =
      currentTime(TimeUnit.NANOSECONDS)
    def sleep(duration: => Duration)(implicit trace: ZTraceElement): UIO[Unit] =
      ZIO.asyncInterrupt { cb =>
        val canceler = globalScheduler.unsafeSchedule(() => cb(UIO.unit), duration)
        Left(UIO.succeed(canceler()))
      }
    def scheduler(implicit trace: ZTraceElement): UIO[Scheduler] =
      ZIO.succeed(globalScheduler)
  }

  object ClockLive extends Clock {
    def currentTime(unit0: => TimeUnit)(implicit trace: ZTraceElement): UIO[Long] =
      ZIO.suspendSucceed {
        val unit = unit0

        instant.map { inst =>
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
      }

    def nanoTime(implicit trace: ZTraceElement): UIO[Long] = IO.succeed(JSystem.nanoTime)

    def sleep(duration: => Duration)(implicit trace: ZTraceElement): UIO[Unit] =
      UIO.asyncInterrupt { cb =>
        val canceler = globalScheduler.unsafeSchedule(() => cb(UIO.unit), duration)
        Left(UIO.succeed(canceler()))
      }

    def currentDateTime(implicit trace: ZTraceElement): UIO[OffsetDateTime] =
      ZIO.succeed(OffsetDateTime.now())

    override def instant(implicit trace: ZTraceElement): UIO[Instant] =
      ZIO.succeed(Instant.now())

    override def localDateTime(implicit trace: ZTraceElement): UIO[LocalDateTime] =
      ZIO.succeed(LocalDateTime.now())

    def scheduler(implicit trace: ZTraceElement): UIO[Scheduler] =
      ZIO.succeed(globalScheduler)

  }

  /**
   * Returns the current time, relative to the Unix epoch.
   */
  def currentTime(unit: => TimeUnit)(implicit trace: ZTraceElement): UIO[Long] =
    ZIO.clockWith(_.currentTime(unit))

  /**
   * Get the current time, represented in the current timezone.
   */
  def currentDateTime(implicit trace: ZTraceElement): UIO[OffsetDateTime] =
    ZIO.clockWith(_.currentDateTime)

  def instant(implicit trace: ZTraceElement): UIO[java.time.Instant] =
    ZIO.clockWith(_.instant)

  def localDateTime(implicit trace: ZTraceElement): UIO[java.time.LocalDateTime] =
    ZIO.clockWith(_.localDateTime)

  /**
   * Returns the system nano time, which is not relative to any date.
   */
  def nanoTime(implicit trace: ZTraceElement): UIO[Long] =
    ZIO.clockWith(_.nanoTime)

  /**
   * Returns the scheduler used for scheduling effects.
   */
  def scheduler(implicit trace: ZTraceElement): UIO[Scheduler] =
    ZIO.clockWith(_.scheduler)

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  def sleep(duration: => Duration)(implicit trace: ZTraceElement): UIO[Unit] =
    ZIO.clockWith(_.sleep(duration))

}
