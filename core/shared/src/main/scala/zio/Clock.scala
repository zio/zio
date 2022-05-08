/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
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
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, OffsetDateTime}
import java.util.concurrent.TimeUnit

trait Clock extends Serializable {

  def currentTime(unit: => TimeUnit)(implicit trace: ZTraceElement): UIO[Long]

  def currentTime(unit: => ChronoUnit)(implicit trace: ZTraceElement, d: DummyImplicit): UIO[Long]

  def currentDateTime(implicit trace: ZTraceElement): UIO[OffsetDateTime]

  def instant(implicit trace: ZTraceElement): UIO[java.time.Instant]

  def localDateTime(implicit trace: ZTraceElement): UIO[java.time.LocalDateTime]

  def nanoTime(implicit trace: ZTraceElement): UIO[Long]

  def scheduler(implicit trace: ZTraceElement): UIO[Scheduler]

  def sleep(duration: => Duration)(implicit trace: ZTraceElement): UIO[Unit]

  final def driver[Env, In, Out](
    schedule: Schedule[Env, In, Out]
  )(implicit trace: ZTraceElement): UIO[Schedule.Driver[schedule.State, Env, In, Out]] =
    Ref.make[(Option[Out], schedule.State)]((None, schedule.initial)).map { ref =>
      val next = (in: In) =>
        for {
          state <- ref.get.map(_._2)
          now   <- currentDateTime
          dec   <- schedule.step(now, in, state)
          v <- dec match {
                 case (state, out, Done) => ref.set((Some(out), state)) *> ZIO.fail(None)
                 case (state, out, Continue(interval)) =>
                   ref.set((Some(out), state)) *> sleep(Duration.fromInterval(now, interval.start)) as out
               }
        } yield v

      val last = ref.get.flatMap {
        case (None, _)    => ZIO.fail(new NoSuchElementException("There is no value left"))
        case (Some(b), _) => ZIO.succeed(b)
      }

      val reset = ref.set((None, schedule.initial))

      val state = ref.get.map(_._2)

      Schedule.Driver(next, last, reset, state)
    }

  final def repeat[R, R1 <: R, E, A, B](zio: => ZIO[R, E, A])(schedule: => Schedule[R1, A, B])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E, B] =
    repeatOrElse[R, R1, E, E, A, B](zio)(schedule, (e, _) => ZIO.fail(e))

  final def repeatOrElse[R, R1 <: R, E, E2, A, B](
    zio: => ZIO[R, E, A]
  )(schedule: => Schedule[R1, A, B], orElse: (E, Option[B]) => ZIO[R1, E2, B])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E2, B] =
    repeatOrElseEither[R, R1, E, E2, A, B, B](zio)(schedule, orElse).map(_.merge)

  final def repeatOrElseEither[R, R1 <: R, E, E2, A, B, C](
    zio0: => ZIO[R, E, A]
  )(schedule0: => Schedule[R1, A, B], orElse: (E, Option[B]) => ZIO[R1, E2, C])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E2, Either[C, B]] =
    ZIO.suspendSucceed {
      val zio      = zio0
      val schedule = schedule0

      driver(schedule).flatMap { driver =>
        def loop(a: A): ZIO[R1, E2, Either[C, B]] =
          driver
            .next(a)
            .foldZIO(
              _ => driver.last.orDie.map(Right(_)),
              b =>
                zio.foldZIO(
                  e => orElse(e, Some(b)).map(Left(_)),
                  a => loop(a)
                )
            )

        zio.foldZIO(
          e => orElse(e, None).map(Left(_)),
          a => loop(a)
        )
      }
    }

  final def retry[R, R1 <: R, E, A, S](zio: => ZIO[R, E, A])(policy: Schedule[R1, E, S])(implicit
    ev: CanFail[E],
    trace: ZTraceElement
  ): ZIO[R1, E, A] =
    retryOrElse(zio)(policy, (e: E, _: S) => ZIO.fail(e))

  final def retryOrElse[R, R1 <: R, E, E1, A, A1 >: A, S](
    zio: => ZIO[R, E, A]
  )(policy: => Schedule[R1, E, S], orElse: (E, S) => ZIO[R1, E1, A1])(implicit
    ev: CanFail[E],
    trace: ZTraceElement
  ): ZIO[R1, E1, A1] =
    retryOrElseEither(zio)(policy, orElse).map(_.merge)

  final def retryOrElseEither[R, R1 <: R, E, E1, A, B, Out](
    zio0: => ZIO[R, E, A]
  )(schedule0: => Schedule[R1, E, Out], orElse: (E, Out) => ZIO[R1, E1, B])(implicit
    ev: CanFail[E],
    trace: ZTraceElement
  ): ZIO[R1, E1, Either[B, A]] =
    ZIO.suspendSucceed {
      val zio      = zio0
      val schedule = schedule0

      def loop(driver: Schedule.Driver[Any, R1, E, Out]): ZIO[R1, E1, Either[B, A]] =
        zio
          .map(Right(_))
          .catchAll(e =>
            driver
              .next(e)
              .foldZIO(
                _ => driver.last.orDie.flatMap(out => orElse(e, out).map(Left(_))),
                _ => loop(driver)
              )
          )

      driver(schedule).flatMap(loop(_))
    }

  final def schedule[R, R1 <: R, E, A, B](zio: => ZIO[R, E, A])(schedule: => Schedule[R1, Any, B])(implicit
    trace: ZTraceElement
  ): ZIO[R1, E, B] =
    scheduleFrom[R, R1, E, A, Any, B](zio)(())(schedule)

  final def scheduleFrom[R, R1 <: R, E, A, A1 >: A, B](
    zio0: => ZIO[R, E, A]
  )(a: => A1)(schedule0: => Schedule[R1, A1, B])(implicit trace: ZTraceElement): ZIO[R1, E, B] =
    ZIO.suspendSucceed {
      val zio      = zio0
      val schedule = schedule0

      driver(schedule).flatMap { driver =>
        def loop(a: A1): ZIO[R1, E, B] =
          driver.next(a).foldZIO(_ => driver.last.orDie, _ => zio.flatMap(loop))

        loop(a)
      }
    }
}

object Clock extends ClockPlatformSpecific with Serializable {

  val any: ZLayer[Clock, Nothing, Clock] =
    ZLayer.service[Clock](Tag[Clock], IsNotIntersection[Clock], Tracer.newTrace)

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
    ZLayer.succeed[Clock](ClockLive)(Tag[Clock], IsNotIntersection[Clock], Tracer.newTrace)

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
    def currentTime(unit: => ChronoUnit)(implicit trace: ZTraceElement, d: DummyImplicit): UIO[Long] =
      ZIO.suspendSucceed(instant.map(unit.between(Instant.EPOCH, _)))
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

    def currentTime(unit: => ChronoUnit)(implicit trace: ZTraceElement, d: DummyImplicit): UIO[Long] =
      ZIO.suspendSucceed(instant.map(unit.between(Instant.EPOCH, _)))

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

  // Accessors

  /**
   * Returns the current time, relative to the Unix epoch.
   */
  def currentTime(unit: => TimeUnit)(implicit trace: ZTraceElement): URIO[Clock, Long] =
    ZIO.serviceWithZIO(_.currentTime(unit))

  def currentTime(unit: => ChronoUnit)(implicit trace: ZTraceElement, d: DummyImplicit): URIO[Clock, Long] =
    ZIO.serviceWithZIO(_.currentTime(unit))

  /**
   * Get the current time, represented in the current timezone.
   */
  def currentDateTime(implicit trace: ZTraceElement): URIO[Clock, OffsetDateTime] =
    ZIO.serviceWithZIO(_.currentDateTime)

  def driver[Env, In, Out](
    schedule: Schedule[Env, In, Out]
  )(implicit trace: ZTraceElement): URIO[Clock, Schedule.Driver[schedule.State, Env, In, Out]] =
    ZIO.serviceWithZIO(_.driver(schedule))

  def instant(implicit trace: ZTraceElement): ZIO[Clock, Nothing, java.time.Instant] =
    ZIO.serviceWithZIO(_.instant)

  def localDateTime(implicit trace: ZTraceElement): ZIO[Clock, Nothing, java.time.LocalDateTime] =
    ZIO.serviceWithZIO(_.localDateTime)

  /**
   * Returns the system nano time, which is not relative to any date.
   */
  def nanoTime(implicit trace: ZTraceElement): URIO[Clock, Long] =
    ZIO.serviceWithZIO(_.nanoTime)

  /**
   * Returns a new effect that repeats this effect according to the specified
   * schedule or until the first failure. Scheduled recurrences are in addition
   * to the first execution, so that `io.repeat(Schedule.once)` yields an effect
   * that executes `io`, and then if that succeeds, executes `io` an additional
   * time.
   */
  def repeat[R, R1 <: R, E, A, B](zio: => ZIO[R, E, A])(
    schedule: => Schedule[R1, A, B]
  )(implicit trace: ZTraceElement): ZIO[R1 with Clock, E, B] =
    ZIO.serviceWithZIO[Clock](_.repeat(zio)(schedule))

  /**
   * Returns a new effect that repeats this effect according to the specified
   * schedule or until the first failure, at which point, the failure value and
   * schedule output are passed to the specified handler.
   *
   * Scheduled recurrences are in addition to the first execution, so that
   * `io.repeat(Schedule.once)` yields an effect that executes `io`, and then if
   * that succeeds, executes `io` an additional time.
   */
  final def repeatOrElse[R, R1 <: R, E, E2, A, B](
    zio: => ZIO[R, E, A]
  )(schedule: => Schedule[R1, A, B], orElse: (E, Option[B]) => ZIO[R1, E2, B])(implicit
    trace: ZTraceElement
  ): ZIO[R1 with Clock, E2, B] =
    ZIO.serviceWithZIO[Clock](_.repeatOrElse(zio)(schedule, orElse))

  /**
   * Returns a new effect that repeats this effect according to the specified
   * schedule or until the first failure, at which point, the failure value and
   * schedule output are passed to the specified handler.
   *
   * Scheduled recurrences are in addition to the first execution, so that
   * `io.repeat(Schedule.once)` yields an effect that executes `io`, and then if
   * that succeeds, executes `io` an additional time.
   */
  final def repeatOrElseEither[R, R1 <: R, E, E2, A, B, C](
    zio: => ZIO[R, E, A]
  )(
    schedule: => Schedule[R1, A, B],
    orElse: (E, Option[B]) => ZIO[R1, E2, C]
  )(implicit trace: ZTraceElement): ZIO[R1 with Clock, E2, Either[C, B]] =
    ZIO.serviceWithZIO[Clock](_.repeatOrElseEither(zio)(schedule, orElse))

  /**
   * Retries with the specified retry policy. Retries are done following the
   * failure of the original `io` (up to a fixed maximum with `once` or `recurs`
   * for example), so that that `io.retry(Schedule.once)` means "execute `io`
   * and in case of failure, try again once".
   */
  final def retry[R, R1 <: R, E, A, S](zio: => ZIO[R, E, A])(policy: => Schedule[R1, E, S])(implicit
    ev: CanFail[E],
    trace: ZTraceElement
  ): ZIO[R1 with Clock, E, A] =
    ZIO.serviceWithZIO[Clock](_.retry(zio)(policy))

  /**
   * Retries with the specified schedule, until it fails, and then both the
   * value produced by the schedule together with the last error are passed to
   * the recovery function.
   */
  final def retryOrElse[R, R1 <: R, E, E1, A, A1 >: A, S](zio: => ZIO[R, E, A])(
    policy: => Schedule[R1, E, S],
    orElse: (E, S) => ZIO[R1, E1, A1]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZIO[R1 with Clock, E1, A1] =
    ZIO.serviceWithZIO[Clock](_.retryOrElse[R, R1, E, E1, A, A1, S](zio)(policy, orElse))

  /**
   * Returns an effect that retries this effect with the specified schedule when
   * it fails, until the schedule is done, then both the value produced by the
   * schedule together with the last error are passed to the specified recovery
   * function.
   */
  final def retryOrElseEither[R, R1 <: R, E, E1, A, B, Out](zio: => ZIO[R, E, A])(
    schedule: => Schedule[R1, E, Out],
    orElse: (E, Out) => ZIO[R1, E1, B]
  )(implicit ev: CanFail[E], trace: ZTraceElement): ZIO[R1 with Clock, E1, Either[B, A]] =
    ZIO.serviceWithZIO[Clock](_.retryOrElseEither(zio)(schedule, orElse))

  /**
   * Runs this effect according to the specified schedule.
   *
   * See [[scheduleFrom]] for a variant that allows the schedule's decision to
   * depend on the result of this effect.
   */
  final def schedule[R, R1 <: R, E, A, B](zio: => ZIO[R, E, A])(
    schedule: => Schedule[R1, Any, B]
  )(implicit trace: ZTraceElement): ZIO[R1 with Clock, E, B] =
    ZIO.serviceWithZIO[Clock](_.schedule(zio)(schedule))

  /**
   * Runs this effect according to the specified schedule starting from the
   * specified input value.
   */
  final def scheduleFrom[R, R1 <: R, E, A, A1 >: A, B](
    zio: => ZIO[R, E, A]
  )(a: => A1)(schedule: => Schedule[R1, A1, B])(implicit trace: ZTraceElement): ZIO[R1 with Clock, E, B] =
    ZIO.serviceWithZIO[Clock](_.scheduleFrom[R, R1, E, A, A1, B](zio)(a)(schedule))

  /**
   * Returns the scheduler used for scheduling effects.
   */
  def scheduler(implicit trace: ZTraceElement): URIO[Clock, Scheduler] =
    ZIO.serviceWithZIO(_.scheduler)

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  def sleep(duration: => Duration)(implicit trace: ZTraceElement): URIO[Clock, Unit] =
    ZIO.serviceWithZIO(_.sleep(duration))

}
