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
import zio.test.mock.TestClock.Data
import java.time.{ Instant, OffsetDateTime }

case class TestClock(private val ref: Ref[TestClock.Data]) extends Clock.Service[Any] {

  final def currentTime(unit: TimeUnit): UIO[Long] =
    ref.get.map(data => unit.convert(data.currentTimeMillis, TimeUnit.MILLISECONDS))

  final def currentDateTime: UIO[OffsetDateTime] =
    ref.get.map(data => TestClock.offset(data.currentTimeMillis, data.timeZone))

  final val nanoTime: IO[Nothing, Long] =
    ref.get.map(_.nanoTime)

  final def sleep(duration: Duration): UIO[Unit] =
    adjust(duration) *> ref.update(data => data.copy(sleeps0 = duration :: data.sleeps0)).unit

  val sleeps: UIO[List[Duration]] = ref.get.map(_.sleeps0.reverse)

  final def adjust(duration: Duration): UIO[Unit] =
    ref.update { data =>
      Data(
        data.nanoTime + duration.toNanos,
        data.currentTimeMillis + duration.toMillis,
        data.sleeps0,
        data.timeZone
      )
    }.unit

  final def setTime(duration: Duration): UIO[Unit] =
    ref.update(_.copy(nanoTime = duration.toNanos, currentTimeMillis = duration.toMillis)).unit

  final def setTimeZone(zone: ZoneId): UIO[Unit] =
    ref.update(_.copy(timeZone = zone)).unit

  val timeZone: UIO[ZoneId] =
    ref.get.map(_.timeZone)
}

object TestClock {

  def apply(data: Data): UIO[TestClock] =
    Ref.make(data).map(TestClock(_))

  val DefaultData = Data(0, 0, Nil, ZoneId.of("UTC"))

  def offset(millis: Long, timeZone: ZoneId): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), timeZone)

  case class Data(
    nanoTime: Long,
    currentTimeMillis: Long,
    sleeps0: List[Duration],
    timeZone: ZoneId
  )
}
