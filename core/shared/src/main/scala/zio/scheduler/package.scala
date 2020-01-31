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
      def shutdown: UIO[Unit]
    }

    val any: ZLayer[Scheduler, Nothing, Scheduler] =
      ZLayer.requires[Scheduler]

    val live: ZLayer.NoDeps[Nothing, Scheduler] =
      fromIScheduler(globalScheduler)

    val defaultScheduler: Managed[Nothing, Scheduler.Service] =
      live.build.map(_.get)

    /**
     * Creates a new `Scheduler` from a Java `ScheduledExecutorService`.
     */
    final def fromScheduledExecutorService(service: ScheduledExecutorService): ZLayer.NoDeps[Nothing, Scheduler] =
      fromIScheduler(IScheduler.fromScheduledExecutorService(service))

    private[zio] def fromIScheduler(scheduler: IScheduler): ZLayer.NoDeps[Nothing, Scheduler] = {

      val service = new Scheduler.Service {
        def schedule[R, E, A](task: ZIO[R, E, A], duration: Duration): ZIO[R, E, A] =
          ZIO.effectAsyncInterrupt { cb =>
            val canceler = scheduler.schedule(() => cb(task), duration)
            Left(ZIO.effectTotal(canceler()))
          }
        def shutdown: UIO[Unit] =
          UIO.effectTotal(scheduler.shutdown)
      }

      ZLayer.fromManaged(Managed.make(UIO.effectTotal(Has(service))) { scheduler =>
        UIO.effectTotal(scheduler.get.shutdown)
      })
    }
  }

  def scheduler: ZIO[Scheduler, Nothing, Scheduler.Service] =
    ZIO.access(_.get)
}
