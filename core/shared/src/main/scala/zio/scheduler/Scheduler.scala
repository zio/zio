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

import zio.{ Has, UIO, ZLayer }
import zio.duration.Duration
import zio.internal.Scheduler.CancelToken

object Scheduler extends PlatformSpecific {

  trait Service extends Serializable {
    def submit(task: Runnable, duration: Duration): UIO[Unit] =
      UIO.effectAsyncInterrupt { cb =>
        val canceler = schedule(() => cb(UIO.effectTotal(task.run())), duration)
        Left(UIO.effectTotal(canceler()))
      }
    private[zio] def schedule(task: Runnable, duration: Duration): CancelToken
    private[zio] def size: Int
    private[zio] def shutdown(): Unit
  }

  val live: ZLayer.NoDeps[Nothing, Has[Service]] = ZLayer.succeed {
    new Service {
      private[zio] def schedule(task: Runnable, duration: Duration): CancelToken =
        globalScheduler.schedule(task, duration)
      private[zio] def size: Int =
        globalScheduler.size
      private[zio] def shutdown(): Unit =
        globalScheduler.shutdown()
    }
  }
}
