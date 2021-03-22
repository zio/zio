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

import zio.duration.Duration
import zio.internal.Scheduler

import scala.concurrent.duration._
import scala.scalanative.loop._

private[clock] trait PlatformSpecific {
  private[clock] val globalScheduler = new Scheduler {
    import Scheduler.CancelToken

    private[this] val ConstFalse = () => false

    override def schedule(task: Runnable, duration: Duration): CancelToken = (duration: @unchecked) match {
      case Duration.Infinity => ConstFalse
      case Duration.Finite(nanos) =>
        var completed = false

        val handle = Timer.timeout(nanos.nanos) { () =>
          completed = true

          task.run()
        }
        () => {
          handle.clear()
          !completed
        }
    }
  }
}
