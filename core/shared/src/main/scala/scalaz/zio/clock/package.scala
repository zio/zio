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

package scalaz.zio

import scalaz.zio.duration.Duration

import java.util.concurrent.TimeUnit

package object clock extends Clock.Service[Clock] {
  final val clockService: ZIO[Clock, Nothing, Clock.Service[Any]] =
    ZIO.access(_.clock)

  /**
   * Returns the current time, relative to the Unix epoch.
   */
  final def currentTime(unit: TimeUnit): ZIO[Clock, Nothing, Long] =
    ZIO.accessM(_.clock currentTime unit)

  /**
   * Returns the system nano time, which is not relative to any date.
   */
  final val nanoTime: ZIO[Clock, Nothing, Long] =
    ZIO.accessM(_.clock.nanoTime)

  /**
   * Sleeps for the specified duration. This is always asynchronous.
   */
  final def sleep(duration: Duration): ZIO[Clock, Nothing, Unit] =
    ZIO.accessM(_.clock sleep duration)
}
