/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.Scheduler.CancelToken
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

/**
 * A `Scheduler` runs a task after a delay. The live `Clock` uses its scheduler
 * to implement `ZIO.sleep`; the scheduler is available from `Clock#scheduler`.
 * On the JVM and Scala Native a scheduler is backed by a
 * `java.util.concurrent.ScheduledExecutorService`.
 */
sealed abstract class Scheduler extends SchedulerPlatformSpecific {

  /**
   * Runs `task` after `duration` and returns a token that cancels it. A
   * `Duration.Infinity` never runs the task. See `CancelToken` for what
   * cancelling returns.
   */
  def schedule(task: Runnable, duration: Duration)(implicit unsafe: Unsafe): CancelToken
}

/** Constructors and types of [[Scheduler]]. */
object Scheduler {

  /**
   * A function that cancels a scheduled task. It returns `true` when the task
   * was cancelled before it ran, and `false` when it was not: the task had
   * already run, the delay was infinite, or (on the JVM and Scala Native) the
   * task had already been cancelled.
   */
  type CancelToken = () => Boolean
  private final val ConstFalse = () => false
  private final val MaxMillis  = Long.MaxValue / 1000000L

  private[zio] abstract class Internal extends Scheduler

  /**
   * Creates a `Scheduler` backed by `service`. The task runs on a thread of
   * `service` after the delay, and cancelling interrupts the task if it is
   * running. When the delay is zero or negative, `schedule` runs the task on
   * the calling thread before it returns, and the returned token returns
   * `false`; an exception thrown by the task is thrown by `schedule`. When the
   * delay is `Duration.Infinity`, the task never runs.
   */
  def fromScheduledExecutorService(service: ScheduledExecutorService): Scheduler =
    new Scheduler {
      def asScheduledExecutorService: ScheduledExecutorService =
        service

      def schedule(task: Runnable, duration: Duration)(implicit unsafe: Unsafe): CancelToken =
        (duration: @unchecked) match {
          case Duration.Infinity => ConstFalse
          case d if d.isZero || d.isNegative =>
            task.run()
            ConstFalse
          case d =>
            val millis = d.toMillis
            val future =
              if (millis < MaxMillis)
                service.schedule(
                  task,
                  d.toNanos,
                  TimeUnit.NANOSECONDS
                )
              else
                service.schedule(
                  task,
                  millis,
                  TimeUnit.MILLISECONDS
                )

            () => future.cancel(true)
        }

      override def toString() = s"Scheduler($service)"
    }
}
