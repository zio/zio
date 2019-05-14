package scalaz.zio.internal.stacktracer

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

  final type TracerCache = ConcurrentHashMap[Class[_], ZTraceElement]
  private[this] lazy val globalMutableSharedTracerCache = new TracerCache(10000)
}
