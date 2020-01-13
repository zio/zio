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

import java.util.concurrent.ConcurrentHashMap
import java.util.{ Collections, WeakHashMap, Map => JMap, Set => JSet }

import scala.concurrent.ExecutionContext

import zio.Cause
import zio.internal.stacktracer.Tracer
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.TracingConfig

private[internal] trait PlatformSpecific {

  /**
   * A Runtime with settings suitable for benchmarks, specifically with Tracing
   * and auto-yielding disabled.
   *
   * Tracing adds a constant ~2x overhead on FlatMaps, however, it's an
   * optional feature and it's not valid to compare the performance of ZIO with
   * enabled Tracing with effect types _without_ a comparable feature.
   * */
  lazy val benchmark = makeDefault(Int.MaxValue).withReportFailure(_ => ()).withTracing(Tracing.disabled)

  /**
   * The default platform, configured with settings designed to work well for
   * mainstream usage. Advanced users should consider making their own platform
   * customized for specific application requirements.
   */
  lazy val default: Platform = makeDefault()

  /**
   * The default number of operations the ZIO runtime should execute before
   * yielding to other fibers.
   */
  final val defaultYieldOpCount = 2048

  /**
   * A `Platform` created from Scala's global execution context.
   */
  lazy val global = fromExecutionContext(ExecutionContext.global)

  /**
   * Creates a platform from an `Executor`.
   */
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

  /**
   * Creates a Platform from an exeuction context.
   */
  final def fromExecutionContext(ec: ExecutionContext): Platform =
    fromExecutor(Executor.fromExecutionContext(defaultYieldOpCount)(ec))

  /**
   * Makes a new default platform. This is a side-effecting method.
   */
  def makeDefault(yieldOpCount: Int = defaultYieldOpCount): Platform =
    fromExecutor(Executor.makeDefault(yieldOpCount))

  final def newWeakHashMap[A, B](): JMap[A, B] =
    Collections.synchronizedMap(new WeakHashMap[A, B]())

  final def newConcurrentSet[A](): JSet[A] = ConcurrentHashMap.newKeySet[A]()

  /**
   * calling `initCause()` on [[java.lang.Throwable]] may fail on the JVM if `newCause != this`,
   * which may happen if the cause is set to null.
   * This works around this with reflection.
   */
  def forceThrowableCause(throwable: Throwable, newCause: Throwable): Unit = {
    import scala.util.control.Exception._
    ignoring(classOf[Throwable]) {
      val causeField = classOf[Throwable].getDeclaredField("cause")
      causeField.setAccessible(true)
      causeField.set(throwable, newCause)
    }
  }
}
