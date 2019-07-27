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

import java.util.concurrent.TimeUnit
import java.time.ZoneId

import zio._
import zio.duration.Duration
import zio.clock.Clock
import java.time.{ Instant, OffsetDateTime }

trait MockClock extends Clock {
  val clock: MockClock.Service[Any]
}

object MockClock {

  trait Service[R] extends Clock.Service[R] {
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
      adjust(duration) *> clockState.update(data => data.copy(sleeps0 = duration :: data.sleeps0)).unit

    val sleeps: UIO[List[Duration]] = clockState.get.map(_.sleeps0.reverse)

    final def adjust(duration: Duration): UIO[Unit] =
      clockState.update { data =>
        Data(
          data.nanoTime + duration.toNanos,
          data.currentTimeMillis + duration.toMillis,
          data.sleeps0,
          data.timeZone
        )
      }.unit

    final def setTime(duration: Duration): UIO[Unit] =
      clockState.update(_.copy(nanoTime = duration.toNanos, currentTimeMillis = duration.toMillis)).unit

    final def setTimeZone(zone: ZoneId): UIO[Unit] =
      clockState.update(_.copy(timeZone = zone)).unit

    val timeZone: UIO[ZoneId] =
      clockState.get.map(_.timeZone)
  }

  def make(data: Data): UIO[MockClock] =
    makeMock(data).map { mock =>
      new MockClock {
        val clock = mock
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
    sleeps0: List[Duration],
    timeZone: ZoneId
  )
}
