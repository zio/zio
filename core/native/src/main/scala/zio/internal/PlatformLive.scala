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

package zio.internal

import zio.internal.stacktracer.Tracer
import zio.internal.tracing.TracingConfig
import zio.{Cause, Supervisor}

import scala.concurrent.ExecutionContext

object PlatformLive {
  lazy val Default          = Global
  lazy val Global: Platform = fromExecutionContext(ExecutionContext.global)

  def makeDefault(): Platform = fromExecutionContext(ExecutionContext.global)

  def fromExecutor(executor0: Executor): Platform =
    new Platform {
      val executor = executor0

      def fatal(t: Throwable): Boolean = false

      def reportFatal(t: Throwable): Nothing = {
        t.printStackTrace()
        throw t
      }

      def reportFailure(cause: Cause[Any]): Unit =
        if (cause.died)
          println(cause.prettyPrint)

      val tracing = Tracing(Tracer.Empty, TracingConfig.disabled)

      val supervisor = Supervisor.none
    }

  def fromExecutionContext(ec: ExecutionContext, yieldOpCount: Int = 2048): Platform =
    fromExecutor(Executor.fromExecutionContext(yieldOpCount)(ec))
}
