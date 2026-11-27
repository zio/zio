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
import zio.{Duration, Scheduler, Trace, UIO, Unsafe, ZIO}

/**
 * The members of `TestClock.Test` whose implementation differs by platform.
 */
private[test] trait TestClockPlatformSpecific { self: TestClock.Test =>

  /**
   * Returns a `Scheduler` driven by this `TestClock`. A task scheduled with a
   * delay runs on a forked fiber once `adjust` or `setTime` moves the clock
   * time to or past the clock time at scheduling plus the delay; a zero or
   * negative delay runs it without an adjustment. The cancel token interrupts
   * that fiber and returns `true` when the task had not run yet, and `false`
   * after it ran. On the JVM and Scala Native, the scheduler's
   * `asScheduledExecutorService` also runs its tasks on this clock; on Scala.js
   * a `Scheduler` has no `asScheduledExecutorService`.
   */
  def scheduler(implicit trace: Trace): UIO[Scheduler] =
    ZIO.runtime[Any].map { runtime =>
      new Scheduler.Internal {
        def schedule(runnable: Runnable, duration: Duration)(implicit unsafe: Unsafe): Scheduler.CancelToken = {
          val fiber =
            runtime.unsafe.fork(sleep(duration) *> ZIO.succeed(runnable.run()))
          () => runtime.unsafe.run(fiber.interruptAs(zio.FiberId.None)).getOrThrowFiberFailure().isInterrupted
        }
      }
    }
}
