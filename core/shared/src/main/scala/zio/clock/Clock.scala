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

package zio.clock

import java.time.{ Instant, OffsetDateTime, ZoneId }
import java.util.concurrent.TimeUnit

import zio._
import zio.duration._

object Clock extends PlatformSpecific with Serializable {

  val any: ZLayer[Clock, Nothing, Clock] =
    ZLayer.requires[Clock]

  val live: ZLayer.NoDeps[Nothing, Clock] = ZLayer.succeed {
    new Service {
      def currentTime(unit: TimeUnit): UIO[Long] =
        IO.effectTotal(System.currentTimeMillis).map(l => unit.convert(l, TimeUnit.MILLISECONDS))

      val nanoTime: UIO[Long] = IO.effectTotal(System.nanoTime)

      def sleep(duration: Duration): UIO[Unit] =
        UIO.effectAsyncInterrupt { cb =>
          val canceler = globalScheduler.schedule(() => cb(UIO.unit), duration)
          Left(UIO.effectTotal(canceler()))
        }

      def currentDateTime: ZIO[Any, Nothing, OffsetDateTime] =
        for {
          millis <- currentTime(TimeUnit.MILLISECONDS)
          zone   <- ZIO.effectTotal(ZoneId.systemDefault)
        } yield OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    }
  }
}
