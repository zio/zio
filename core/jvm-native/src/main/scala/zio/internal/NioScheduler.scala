/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

package zio.internal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

/**
 * A `NioScheduler` provides temporal scheduling backed by a 
 * high-performance ScheduledExecutorService, while providing 
 * hooks for the NioExecutor.
 */
private[zio] final class NioScheduler extends Scheduler.Internal {
  private[this] val service = Executors.newSingleThreadScheduledExecutor(
    new NamedThreadFactory("ZIO-NioScheduler", true)
  )

  override def schedule(task: Runnable, duration: Duration)(implicit unsafe: Unsafe): Scheduler.CancelToken = {
    if (duration.isZero) {
      task.run()
      () => false
    } else {
      val future = service.schedule(task, duration.toNanos, TimeUnit.NANOSECONDS)
      () => future.cancel(true)
    }
  }

  def shutdown(): Unit = service.shutdown()
}
