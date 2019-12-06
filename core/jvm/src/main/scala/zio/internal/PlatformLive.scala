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

package zio.internal

import zio.Cause
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig

import scala.concurrent.ExecutionContext

object PlatformLive {
  lazy val Default = makeDefault()
  lazy val Global  = fromExecutionContext(ExecutionContext.global)

  /**
   * A Runtime with settings suitable for benchmarks, specifically
   * with Tracing & auto-yielding disabled.
   *
   * Tracing adds a constant ~2x overhead on FlatMaps, however, it's
   * an optional feature and it's not valid to compare the performance
   * of ZIO with enabled Tracing with effect types _without_ a comparable
   * feature.
   * */
  lazy val Benchmark = makeDefault(Int.MaxValue).withReportFailure(_ => ()).withTracing(Tracing.disabled)

  final def makeDefault(yieldOpCount: Int = defaultYieldOpCount): Platform =
    fromExecutor(Executor.makeDefault(yieldOpCount))

  final def fromExecutor(executor0: Executor) =
    new Platform {
      val executor = executor0

      val tracing = Tracing(Tracer.globallyCached(new AkkaLineNumbersTracer), TracingConfig.enabled)

      def fatal(t: Throwable): Boolean =
        t.isInstanceOf[VirtualMachineError]

      def reportFatal(t: Throwable): Nothing = {
        t.printStackTrace()
        try {
          System.exit(-1)
          throw t
        } catch { case _: Throwable => throw t }
      }

      def reportFailure(cause: Cause[Any]): Unit =
        if (cause.died)
          System.err.println(cause.prettyPrint)

    }

  final def fromExecutionContext(ec: ExecutionContext): Platform =
    fromExecutor(Executor.fromExecutionContext(defaultYieldOpCount)(ec))

  final val defaultYieldOpCount = 2048
}
