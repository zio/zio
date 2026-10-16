/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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

package zio.test

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Cause, Duration, FiberId, Scheduler, Trace, UIO, Unsafe, ZIO}

import java.util.concurrent.atomic.AtomicBoolean

private[test] trait TestClockPlatformSpecific { self: TestClock.Test =>

  def scheduler(implicit trace: Trace): UIO[Scheduler] =
    ZIO.runtime[Any].map { runtime =>
      new Scheduler.Internal {
        private[this] val ConstFalse = () => false

        def schedule(runnable: Runnable, duration: Duration)(implicit unsafe: Unsafe): Scheduler.CancelToken =
          duration match {
            case Duration.Infinity => ConstFalse
            case _ =>
              val completed = new AtomicBoolean(false)
              val fiber =
                runtime.unsafe.fork(
                  (sleep(duration) *> ZIO.succeed {
                    if (completed.compareAndSet(false, true)) runnable.run()
                  }).interruptible
                )
              () => {
                val cancelled = completed.compareAndSet(false, true)
                if (cancelled) fiber.unsafe.interrupt(Cause.interrupt(FiberId.None))
                cancelled
              }
          }
      }
    }
}
