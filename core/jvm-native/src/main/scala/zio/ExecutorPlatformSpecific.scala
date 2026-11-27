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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.{AbstractExecutorService, TimeUnit}
import java.{util => ju}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

/**
 * The platform-specific members of `Executor`:
 * `asExecutionContextExecutorService` on the JVM and Scala Native, and none on
 * Scala.js.
 */
private[zio] trait ExecutorPlatformSpecific { this: Executor =>

  /**
   * Views this `Executor` as a Scala `ExecutionContextExecutorService`. Tasks
   * submitted to the service run on this executor. `shutdown` does nothing and
   * `shutdownNow` returns an empty list; `isShutdown` and `isTerminated` return
   * `false` and `awaitTermination` returns `false`, and the service keeps
   * running tasks after `shutdown`. Every call returns the same instance.
   */
  lazy val asExecutionContextExecutorService: ExecutionContextExecutorService =
    new AbstractExecutorService with ExecutionContextExecutorService {
      override val prepare: ExecutionContext                               = asExecutionContext
      override val isShutdown: Boolean                                     = false
      override val isTerminated: Boolean                                   = false
      override val shutdown: Unit                                          = ()
      override val shutdownNow: ju.List[Runnable]                          = ju.Collections.emptyList[Runnable]
      override def execute(runnable: Runnable): Unit                       = asExecutionContext execute runnable
      override def reportFailure(t: Throwable): Unit                       = asExecutionContext reportFailure t
      override def awaitTermination(length: Long, unit: TimeUnit): Boolean = false
    }

}
