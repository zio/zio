/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

import zio.clock.Clock
import zio.duration.Duration
import zio.{ Has, UIO }

object MockClock {

  object currentTime     extends Method[Clock.Service, TimeUnit, Long]
  object currentDateTime extends Method[Clock.Service, Unit, OffsetDateTime]
  object nanoTime        extends Method[Clock.Service, Unit, Long]
  object sleep           extends Method[Clock.Service, Duration, Unit]

  implicit val mockableClock: Mockable[Clock.Service] = (mock: Mock) =>
    Has(new Clock.Service {
      def currentTime(unit: TimeUnit): UIO[Long] = mock(MockClock.currentTime, unit)
      def currentDateTime: UIO[OffsetDateTime]   = mock(MockClock.currentDateTime)
      val nanoTime: UIO[Long]                    = mock(MockClock.nanoTime)
      def sleep(duration: Duration): UIO[Unit]   = mock(MockClock.sleep, duration)
    })
}
