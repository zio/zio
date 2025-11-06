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

package zio

import zio.internal.NIOExecutor

/**
 * A ZIO `Clock` implementation that is backed by the `NIOExecutor`. This
 * ensures that time-based operations like `ZIO.sleep` are integrated with the
 * specialized executor's single-thread timer.
 */
object NIOClock {

  /**
   * A `ZLayer` that provides a `Clock` service that is powered by an underlying
   * `NIOExecutor`.
   */
  val live: ZLayer[NIOExecutor, Nothing, Clock] =
    ZLayer.fromZIO {
      ZIO.service[NIOExecutor].map { nioExecutor =>
        new Clock {

          /**
           * Schedules the specified effect for execution after the specified
           * duration, delegating to the NIOExecutor's timer.
           */
          def sleep(duration: => Duration)(implicit trace: Trace): UIO[Unit] =
            ZIO.asyncInterrupt { cb =>
              val canceler = nioExecutor.schedule(() => cb(ZIO.unit), duration)
              Left(ZIO.succeed(canceler()))
            }

          def scheduler(implicit trace: Trace): UIO[Scheduler] =
            ZIO.succeed {
              new Scheduler.Internal {

                /**
                 * Delegates task scheduling to the underlying NIOExecutor.
                 */
                override def schedule(task: Runnable, duration: Duration)(implicit
                  unsafe: Unsafe
                ): Scheduler.CancelToken =
                  nioExecutor.schedule(task, duration)

                override def asScheduledExecutorService: java.util.concurrent.ScheduledExecutorService =
                  throw new UnsupportedOperationException("NIOExecutor does not support asScheduledExecutorService")
              }
            }

          /**
           * Other datetime-related methods rely on Clock default
           * implementation.
           */
          def currentTime(unit: => java.util.concurrent.TimeUnit)(implicit trace: Trace): UIO[Long] =
            Clock.currentTime(unit)
          def currentTime(unit: => java.time.temporal.ChronoUnit)(implicit trace: Trace, d: DummyImplicit): UIO[Long] =
            Clock.currentTime(unit)
          def currentDateTime(implicit trace: Trace): UIO[java.time.OffsetDateTime] =
            Clock.currentDateTime
          def instant(implicit trace: Trace): UIO[java.time.Instant] =
            Clock.instant
          def localDateTime(implicit trace: Trace): UIO[java.time.LocalDateTime] =
            Clock.localDateTime
          def nanoTime(implicit trace: Trace): UIO[Long] =
            Clock.nanoTime
          def javaClock(implicit trace: Trace): UIO[java.time.Clock] =
            Clock.javaClock
        }
      }
    }
}
