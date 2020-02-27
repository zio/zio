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

package zio.internal.stacktracer

import java.util.concurrent.ConcurrentHashMap

abstract class Tracer extends Serializable {
  def traceLocation(lambda: AnyRef): ZTraceElement
}

object Tracer {
  def globallyCached(tracer: Tracer): Tracer = cached(globalMutableSharedTracerCache)(tracer)

  def cached(tracerCache: TracerCache)(tracer: Tracer): Tracer =
    new Tracer {
      def traceLocation(lambda: AnyRef): ZTraceElement = {
        val clazz = lambda.getClass

        val res = tracerCache.get(clazz)
        if (res eq null) {
          val v = tracer.traceLocation(lambda)
          tracerCache.put(clazz, v)
          v
        } else {
          res
        }
      }
    }

  private[this] final type TracerCache = ConcurrentHashMap[Class[_], ZTraceElement]
  private[this] lazy val globalMutableSharedTracerCache = new TracerCache(10000)

  lazy val Empty = new Tracer {
    private[this] val res                                     = ZTraceElement.NoLocation("Tracer.Empty disables trace extraction")
    override def traceLocation(lambda: AnyRef): ZTraceElement = res
  }
}
