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

package zio

import java.util.concurrent.ScheduledExecutorService

import zio.duration.Duration
import zio.internal.IScheduler

package object scheduler {
  type Scheduler = Has[Scheduler.Service]

  object Scheduler extends PlatformSpecific {

    trait Service extends Serializable {
      def schedule[R, E, A](task: ZIO[R, E, A], duration: Duration): ZIO[R, E, A]
    }

    val any: ZLayer[Scheduler, Nothing, Scheduler] =
      ZLayer.requires[Scheduler]

    val defaultScheduler: Scheduler.Service =
      fromIScheduler(globalScheduler)

    val live: ZLayer.NoDeps[Nothing, Scheduler] =
      ZLayer.succeed(defaultScheduler)

    /**
     * Creates a new `Scheduler` from a Java `ScheduledExecutorService`.
     */
    final def fromScheduledExecutorService(service: ScheduledExecutorService): Scheduler.Service =
      fromIScheduler(IScheduler.fromScheduledExecutorService(service))

    private[zio] def fromIScheduler(scheduler: IScheduler): Scheduler.Service =
      new Scheduler.Service {
        def schedule[R, E, A](task: ZIO[R, E, A], duration: Duration): ZIO[R, E, A] =
          ZIO.effectAsyncInterrupt { cb =>
            val canceler = scheduler.schedule(() => cb(task), duration)
            Left(ZIO.effectTotal(canceler()))
          }
      }
  }

  def scheduler: ZIO[Scheduler, Nothing, Scheduler.Service] =
    ZIO.access(_.get)
}
