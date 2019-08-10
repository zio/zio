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

    final def currentTime(unit: TimeUnit): UIO[Long] =
      clockState.get.map(data => unit.convert(data.currentTimeMillis, TimeUnit.MILLISECONDS))

    final def currentDateTime: UIO[OffsetDateTime] =
      clockState.get.map(data => MockClock.offset(data.currentTimeMillis, data.timeZone))

    final val nanoTime: IO[Nothing, Long] =
      clockState.get.map(_.nanoTime)

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

    val sleeps: UIO[List[Duration]] = clockState.get.map(_.sleeps.map(_._1))

    final def adjust(duration: Duration): UIO[Unit] =
      clockState.update { data =>
        Data(
          data.nanoTime + duration.toNanos,
          data.currentTimeMillis + duration.toMillis,
          data.sleeps,
          data.timeZone
        )
      } *> wakeUp

    final def setTime(duration: Duration): UIO[Unit] =
      clockState.update { data =>
        data.copy(
          nanoTime = duration.toNanos,
          currentTimeMillis = duration.toMillis
        )
      } *> wakeUp

    final def setTimeZone(zone: ZoneId): UIO[Unit] =
      clockState.update(_.copy(timeZone = zone)).unit

    val timeZone: UIO[ZoneId] =
      clockState.get.map(_.timeZone)

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

    private val wakeUp: UIO[Unit] =
      clockState.modify { data =>
        val (wakes, sleeps) =
          data.sleeps.partition(_._1 <= Duration.fromNanos(data.nanoTime))
        (wakes, data.copy(sleeps = sleeps))
      }.flatMap(run)

    private def run(wakes: List[(Duration, Promise[Nothing, Unit])]): UIO[Unit] =
      UIO.forkAll_(wakes.sortBy(_._1).map(_._2.succeed(()))).fork.unit

    private def cancel(p: Promise[Nothing, Unit]): UIO[Boolean] =
      clockState.modify { data =>
        val (cancels, sleeps) = data.sleeps.partition(_._2 == p)
        (cancels, data.copy(sleeps = sleeps))
      }.map(_.nonEmpty)
  }

  def make(data: Data): UIO[MockClock] =
    makeMock(data).map { mock =>
      new MockClock {
        val clock     = mock
        val scheduler = mock
      }
    }

  def makeMock(data: Data): UIO[Mock] =
    Ref.make(data).map(Mock(_))

  def offset(millis: Long, timeZone: ZoneId): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), timeZone)

  val sleeps: ZIO[MockClock, Nothing, List[Duration]] =
    ZIO.accessM(_.clock.sleeps)

  def adjust(duration: Duration): ZIO[MockClock, Nothing, Unit] =
    ZIO.accessM(_.clock.adjust(duration))

  def setTime(duration: Duration): ZIO[MockClock, Nothing, Unit] =
    ZIO.accessM(_.clock.setTime(duration))

  def setTimeZone(zone: ZoneId): ZIO[MockClock, Nothing, Unit] =
    ZIO.accessM(_.clock.setTimeZone(zone))

  val timeZone: ZIO[MockClock, Nothing, ZoneId] =
    ZIO.accessM(_.clock.timeZone)

  val DefaultData = Data(0, 0, Nil, ZoneId.of("UTC"))

  case class Data(
    nanoTime: Long,
    currentTimeMillis: Long,
    sleeps: List[(Duration, Promise[Nothing, Unit])],
    timeZone: ZoneId
  )
}
