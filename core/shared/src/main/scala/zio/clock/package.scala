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

import zio.Schedule.Decision._
import zio.Schedule._
import zio.duration.Duration

import java.time.{Instant, LocalDateTime, OffsetDateTime}
import java.util.concurrent.TimeUnit

package object clock {

  type Clock = Has[Clock.Service]

  object Clock extends PlatformSpecific with Serializable {
    trait Service extends Serializable {

      def currentTime(unit: TimeUnit): UIO[Long]

      def currentDateTime: UIO[OffsetDateTime]

      def instant: UIO[java.time.Instant]

      def localDateTime: UIO[java.time.LocalDateTime]

      def nanoTime: UIO[Long]

      def sleep(duration: Duration): UIO[Unit]

      final def driver[Env, In, Out](schedule: Schedule[Env, In, Out]): UIO[Schedule.Driver[Env, In, Out]] =
        Ref.make[(Option[Out], Schedule.StepFunction[Env, In, Out])]((None, schedule.step)).map { ref =>
          val next = (in: In) =>
            for {
              step <- ref.get.map(_._2)
              now  <- currentDateTime
              dec  <- step(now, in)
              v <- dec match {
                     case Done(out) => ref.set((Some(out), StepFunction.done(out))) *> ZIO.fail(None)
                     case Continue(out, interval, next) =>
                       ref.set((Some(out), next)) *> sleep(Duration.fromInterval(now, interval)) as out
                   }
            } yield v

          val last = ref.get.flatMap {
            case (None, _)    => ZIO.fail(new NoSuchElementException("There is no value left"))
            case (Some(b), _) => ZIO.succeed(b)
          }

          val reset = ref.set((None, schedule.step))

          Schedule.Driver(next, last, reset)
        }

      final def repeat[R, R1 <: R, E, A, B](zio: ZIO[R, E, A])(schedule: Schedule[R1, A, B]): ZIO[R1, E, B] =
        repeatOrElse[R, R1, E, E, A, B](zio)(schedule, (e, _) => ZIO.fail(e))

      final def repeatOrElse[R, R1 <: R, E, E2, A, B](
        zio: ZIO[R, E, A]
      )(schedule: Schedule[R1, A, B], orElse: (E, Option[B]) => ZIO[R1, E2, B]): ZIO[R1, E2, B] =
        repeatOrElseEither[R, R1, E, E2, A, B, B](zio)(schedule, orElse).map(_.merge)

      final def repeatOrElseEither[R, R1 <: R, E, E2, A, B, C](
        zio: ZIO[R, E, A]
      )(schedule: Schedule[R1, A, B], orElse: (E, Option[B]) => ZIO[R1, E2, C]): ZIO[R1, E2, Either[C, B]] =
        driver(schedule).flatMap { driver =>
          def loop(a: A): ZIO[R1, E2, Either[C, B]] =
            driver
              .next(a)
              .foldM(
                _ => driver.last.orDie.map(Right(_)),
                b =>
                  zio.foldM(
                    e => orElse(e, Some(b)).map(Left(_)),
                    a => loop(a)
                  )
              )

          zio.foldM(
            e => orElse(e, None).map(Left(_)),
            a => loop(a)
          )
        }

      final def retry[R, R1 <: R, E, A, S](zio: ZIO[R, E, A])(policy: Schedule[R1, E, S])(implicit
        ev: CanFail[E]
      ): ZIO[R1, E, A] =
        retryOrElse(zio)(policy, (e: E, _: S) => ZIO.fail(e))

      final def retryOrElse[R, R1 <: R, E, E1, A, A1 >: A, S](
        zio: ZIO[R, E, A]
      )(policy: Schedule[R1, E, S], orElse: (E, S) => ZIO[R1, E1, A1])(implicit ev: CanFail[E]): ZIO[R1, E1, A1] =
        retryOrElseEither(zio)(policy, orElse).map(_.merge)

      final def retryOrElseEither[R, R1 <: R, E, E1, A, B, Out](
        zio: ZIO[R, E, A]
      )(schedule: Schedule[R1, E, Out], orElse: (E, Out) => ZIO[R1, E1, B])(implicit
        ev: CanFail[E]
      ): ZIO[R1, E1, Either[B, A]] = {
        def loop(driver: Schedule.Driver[R1, E, Out]): ZIO[R1, E1, Either[B, A]] =
          zio
            .map(Right(_))
            .catchAll(e =>
              driver
                .next(e)
                .foldM(
                  _ => driver.last.orDie.flatMap(out => orElse(e, out).map(Left(_))),
                  _ => loop(driver)
                )
            )

        driver(schedule).flatMap(loop(_))
      }

      final def schedule[R, R1 <: R, E, A, B](zio: ZIO[R, E, A])(schedule: Schedule[R1, Any, B]): ZIO[R1, E, B] =
        scheduleFrom[R, R1, E, A, Any, B](zio)(())(schedule)

      final def scheduleFrom[R, R1 <: R, E, A, A1 >: A, B](
        zio: ZIO[R, E, A]
      )(a: A1)(schedule: Schedule[R1, A1, B]): ZIO[R1, E, B] =
        driver(schedule).flatMap { driver =>
          def loop(a: A1): ZIO[R1, E, B] =
            driver.next(a).foldM(_ => driver.last.orDie, _ => zio.flatMap(loop))

          loop(a)
        }
    }

    object Service {
      val live: Service = new Service {
        def currentTime(unit: TimeUnit): UIO[Long] =
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

        val nanoTime: UIO[Long] = IO.effectTotal(System.nanoTime)

        def sleep(duration: Duration): UIO[Unit] =
          UIO.effectAsyncInterrupt { cb =>
            val canceler = globalScheduler.schedule(() => cb(UIO.unit), duration)
            Left(UIO.effectTotal(canceler()))
          }

        def currentDateTime: UIO[OffsetDateTime] =
          ZIO.effectTotal(OffsetDateTime.now())

        override def instant: UIO[Instant] =
          ZIO.effectTotal(Instant.now())

        override def localDateTime: UIO[LocalDateTime] =
          ZIO.effectTotal(LocalDateTime.now())

      }
    }

    val any: ZLayer[Clock, Nothing, Clock] =
      ZLayer.requires[Clock]

    val live: Layer[Nothing, Clock] =
      ZLayer.succeed(Service.live)
  }

  /**
   * Returns the current time, relative to the Unix epoch.
   */
  def currentTime(unit: => TimeUnit): URIO[Clock, Long] =
    ZIO.accessM(_.get.currentTime(unit))

  /**
   * Get the current time, represented in the current timezone.
   */
  val currentDateTime: URIO[Clock, OffsetDateTime] =
    ZIO.accessM(_.get.currentDateTime)

  def driver[Env, In, Out](schedule: Schedule[Env, In, Out]): URIO[Clock, Schedule.Driver[Env, In, Out]] =
    ZIO.accessM(_.get.driver(schedule))

  val instant: ZIO[Clock, Nothing, java.time.Instant] =
    ZIO.accessM(_.get.instant)

  /**
   * Returns the system nano time, which is not relative to any date.
   */
  val nanoTime: URIO[Clock, Long] =
    ZIO.accessM(_.get.nanoTime)

  /**
   * Returns a new effect that repeats this effect according to the specified
   * schedule or until the first failure. Scheduled recurrences are in addition
   * to the first execution, so that `io.repeat(Schedule.once)` yields an
   * effect that executes `io`, and then if that succeeds, executes `io` an
   * additional time.
   */
  def repeat[R, R1 <: R, E, A, B](zio: ZIO[R, E, A])(schedule: Schedule[R1, A, B]): ZIO[R1 with Clock, E, B] =
    ZIO.accessM(_.get.repeat(zio)(schedule))

  /**
   * Returns a new effect that repeats this effect according to the specified
   * schedule or until the first failure, at which point, the failure value
   * and schedule output are passed to the specified handler.
   *
   * Scheduled recurrences are in addition to the first execution, so that
   * `io.repeat(Schedule.once)` yields an effect that executes `io`, and then
   * if that succeeds, executes `io` an additional time.
   */
  final def repeatOrElse[R, R1 <: R, E, E2, A, B](
    zio: ZIO[R, E, A]
  )(schedule: Schedule[R1, A, B], orElse: (E, Option[B]) => ZIO[R1, E2, B]): ZIO[R1 with Clock, E2, B] =
    ZIO.accessM(_.get.repeatOrElse(zio)(schedule, orElse))

  /**
   * Returns a new effect that repeats this effect according to the specified
   * schedule or until the first failure, at which point, the failure value
   * and schedule output are passed to the specified handler.
   *
   * Scheduled recurrences are in addition to the first execution, so that
   * `io.repeat(Schedule.once)` yields an effect that executes `io`, and then
   * if that succeeds, executes `io` an additional time.
   */
  final def repeatOrElseEither[R, R1 <: R, E, E2, A, B, C](
    zio: ZIO[R, E, A]
  )(schedule: Schedule[R1, A, B], orElse: (E, Option[B]) => ZIO[R1, E2, C]): ZIO[R1 with Clock, E2, Either[C, B]] =
    ZIO.accessM(_.get.repeatOrElseEither(zio)(schedule, orElse))

  /**
   * Retries with the specified retry policy.
   * Retries are done following the failure of the original `io` (up to a fixed maximum with
   * `once` or `recurs` for example), so that that `io.retry(Schedule.once)` means
   * "execute `io` and in case of failure, try again once".
   */
  final def retry[R, R1 <: R, E, A, S](zio: ZIO[R, E, A])(policy: Schedule[R1, E, S])(implicit
    ev: CanFail[E]
  ): ZIO[R1 with Clock, E, A] =
    ZIO.accessM(_.get.retry(zio)(policy))

  /**
   * Retries with the specified schedule, until it fails, and then both the
   * value produced by the schedule together with the last error are passed to
   * the recovery function.
   */
  final def retryOrElse[R, R1 <: R, E, E1, A, A1 >: A, S](zio: ZIO[R, E, A])(
    policy: Schedule[R1, E, S],
    orElse: (E, S) => ZIO[R1, E1, A1]
  )(implicit ev: CanFail[E]): ZIO[R1 with Clock, E1, A1] =
    ZIO.accessM(_.get.retryOrElse[R, R1, E, E1, A, A1, S](zio)(policy, orElse))

  /**
   * Returns an effect that retries this effect with the specified schedule when it fails, until
   * the schedule is done, then both the value produced by the schedule together with the last
   * error are passed to the specified recovery function.
   */
  final def retryOrElseEither[R, R1 <: R, E, E1, A, B, Out](zio: ZIO[R, E, A])(
    schedule: Schedule[R1, E, Out],
    orElse: (E, Out) => ZIO[R1, E1, B]
  )(implicit ev: CanFail[E]): ZIO[R1 with Clock, E1, Either[B, A]] =
    ZIO.accessM(_.get.retryOrElseEither(zio)(schedule, orElse))

  /**
   * Runs this effect according to the specified schedule.
   *
   * See [[scheduleFrom]] for a variant that allows the schedule's decision to
   * depend on the result of this effect.
   */
  final def schedule[R, R1 <: R, E, A, B](zio: ZIO[R, E, A])(schedule: Schedule[R1, Any, B]): ZIO[R1 with Clock, E, B] =
    ZIO.accessM(_.get.schedule(zio)(schedule))

  /**
   * Runs this effect according to the specified schedule starting from the
   * specified input value.
   */
  final def scheduleFrom[R, R1 <: R, E, A, A1 >: A, B](
    zio: ZIO[R, E, A]
  )(a: A1)(schedule: Schedule[R1, A1, B]): ZIO[R1 with Clock, E, B] =
    ZIO.accessM(_.get.scheduleFrom[R, R1, E, A, A1, B](zio)(a)(schedule))

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  def sleep(duration: => Duration): URIO[Clock, Unit] =
    ZIO.accessM(_.get.sleep(duration))
}
