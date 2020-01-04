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

import java.util.{ HashMap, HashSet, Map => JMap, Set => JSet }

import zio.Cause
import zio.internal.tracing.TracingConfig 
import zio.internal.stacktracer.Tracer
import scala.concurrent.ExecutionContext

private[internal] trait PlatformSpecific {
  lazy val default = makeDefault()
  lazy val global  = fromExecutionContext(ExecutionContext.global)

  final def fromExecutor(executor0: Executor): Platform =
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
    }

  final def fromExecutionContext(ec: ExecutionContext, yieldOpCount: Int = 2048): Platform =
    fromExecutor(Executor.fromExecutionContext(yieldOpCount)(ec))
  
  final def newConcurrentSet[A](): JSet[A] = new HashSet[A]()
  
  final def newWeakHashMap[A, B](): JMap[A, B] = new HashMap[A, B]()

  private final def makeDefault(): Platform = fromExecutionContext(ExecutionContext.global)
}
