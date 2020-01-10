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

package zio.scheduler

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

import zio.duration.Duration
import zio.{ ZIO, ZLayer }

object Scheduler extends PlatformSpecific {
  private[zio] type CancelToken = () => Boolean

  trait Service extends Serializable {
    def schedule[R, E, A](task: ZIO[R, E, A], duration: Duration): ZIO[R, E, A]
  }

  val live: ZLayer.NoDeps[Nothing, Scheduler] =
    ZLayer.succeed(defaultScheduler)

  /**
   * Creates a new `Scheduler` from a Java `ScheduledExecutorService`.
   */
  final def fromScheduledExecutorService(service: ScheduledExecutorService): Scheduler.Service =
    new Scheduler.Service {
      val ConstFalse = () => false

      override def schedule[R, E, A](task: ZIO[R, E, A], duration: Duration): ZIO[R, E, A] =
        ZIO.effectAsyncInterrupt { cb =>
          val canceler = _schedule(() => cb(task), duration)
          Left(ZIO.effectTotal(canceler()))
        }

      private[this] def _schedule(task: Runnable, duration: Duration): CancelToken = duration match {
        case Duration.Infinity => ConstFalse
        case Duration.Zero =>
          task.run()

          ConstFalse
        case duration: Duration.Finite =>
          val future = service.schedule(new Runnable {
            def run: Unit =
              task.run()
          }, duration.toNanos, TimeUnit.NANOSECONDS)

          () => future.cancel(true)
      }
    }
}
