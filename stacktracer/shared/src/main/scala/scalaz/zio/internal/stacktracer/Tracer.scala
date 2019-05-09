package scalaz.zio.internal.stacktracer

import java.util.concurrent.ConcurrentHashMap

abstract class Tracer extends Serializable {
  def traceLocation(lambda: AnyRef): ZTraceElement
}

object Tracer {
  def cachedTracer(tracer: Tracer, jmapCache: ConcurrentHashMap[Class[_], ZTraceElement]): Tracer =
    new Tracer {
      def traceLocation(lambda: AnyRef): ZTraceElement = {
        val clazz = lambda.getClass

        val res = jmapCache.get(clazz)
        if (res eq null) {
          val v = tracer.traceLocation(lambda)
          jmapCache.put(clazz, v)
          v
        } else {
          res
        }
      }
    }
}
