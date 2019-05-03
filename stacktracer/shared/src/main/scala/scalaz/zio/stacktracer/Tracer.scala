package scalaz.zio.stacktracer

abstract class Tracer extends Serializable {
  def traceLocation(lambda: AnyRef): Option[SourceLocation]
}

object Tracer {
  def cachedTracer(tracer: Tracer, cache: SourceLocationCache): Tracer =
    new Tracer {
      val traceFn: AnyRef => SourceLocation =
        tracer.traceLocation(_).orNull

      def traceLocation(lambda: AnyRef): Option[SourceLocation] =
        Option(cache.getOrElseUpdate(lambda, traceFn))
    }
}
