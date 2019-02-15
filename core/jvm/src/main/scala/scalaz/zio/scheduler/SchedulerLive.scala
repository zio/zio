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

package scalaz.zio.scheduler

import scalaz.zio.ZIO
import scalaz.zio.duration.Duration
import scalaz.zio.internal.{ NamedThreadFactory, Scheduler => IScheduler }

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger

trait SchedulerLive extends Scheduler {
  private[this] val scheduler0 = new IScheduler {
    import IScheduler.CancelToken

    val service = Executors.newScheduledThreadPool(1, new NamedThreadFactory("zio-timer", true))

    val ConstFalse = () => false

    val _size = new AtomicInteger()

    override def schedule(task: Runnable, duration: Duration): CancelToken = duration match {
      case Duration.Infinity => ConstFalse
      case Duration.Zero =>
        task.run()

        ConstFalse
      case duration: Duration.Finite =>
        _size.incrementAndGet

        val future = service.schedule(new Runnable {
          def run: Unit =
            try task.run()
            finally {
              val _ = _size.decrementAndGet
            }
        }, duration.toNanos, TimeUnit.NANOSECONDS)

        () => {
          val canceled = future.cancel(true)

          if (canceled) _size.decrementAndGet

          canceled
        }
    }

    override def size: Int = _size.get

    override def shutdown(): Unit = service.shutdown()
  }

  object scheduler extends Scheduler.Service[Any] {
    val scheduler = ZIO.succeed(scheduler0)
  }
}
object SchedulerLive extends SchedulerLive
