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

package zio.testkit

import java.util.concurrent.TimeUnit
import java.time.ZoneId

import zio._
import zio.duration.Duration
import zio.clock.Clock
import zio.testkit.TestClock.Data

case class TestClock(ref: Ref[TestClock.Data]) extends Clock.Service[Any] {

  final def currentTime(unit: TimeUnit): UIO[Long] =
    ref.get.map(data => unit.convert(data.currentTimeMillis, TimeUnit.MILLISECONDS))

  final val nanoTime: IO[Nothing, Long] =
    ref.get.map(_.nanoTime)

  final def sleep(duration: Duration): UIO[Unit] =
    adjust(duration) *> ref.update(data => data.copy(sleeps0 = duration :: data.sleeps0)).unit

  val sleeps: UIO[List[Duration]] = ref.get.map(_.sleeps0.reverse)

  final def adjust(duration: Duration): UIO[Unit] =
    ref.update { data =>
      Data(data.nanoTime + duration.toNanos, data.currentTimeMillis + duration.toMillis, data.sleeps0, data.zoneId)
    }.unit
  final val timeZone: UIO[ZoneId] =
    ref.get.map(_.zoneId)

}

object TestClock {
  val Zero = Data(0, 0, Nil, ZoneId.of("ACT"))

  case class Data(nanoTime: Long, currentTimeMillis: Long, sleeps0: List[Duration], zoneId: ZoneId)
}
