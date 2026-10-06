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
        def schedule(runnable: Runnable, duration: Duration)(implicit unsafe: Unsafe): Scheduler.CancelToken = {
          // The cancelled flag is flipped by whichever of the forked fiber or
          // the cancel token runs first. This allows cancellation to be
          // performed synchronously, without blocking the calling thread
          // waiting for the fiber to be interrupted, which is impossible on
          // Scala.js and would otherwise hang when this scheduler is used
          // from within an uninterruptible region (the runtime captured above
          // inherits the interruptibility of the fiber that created it, hence
          // the `.interruptible` below):
          val cancelled = new AtomicBoolean(false)
          val fiber =
            runtime.unsafe.fork(
              (sleep(duration) *> ZIO.suspendSucceed {
                if (cancelled.compareAndSet(false, true)) ZIO.succeed(runnable.run())
                else ZIO.unit
              }).interruptible
            )
          () =>
            if (cancelled.compareAndSet(false, true)) {
              fiber.unsafe.interrupt(Cause.interrupt(FiberId.None))(unsafe)
              true
            } else false
        }
      }
    }
}
