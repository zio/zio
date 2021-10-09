package zio.internal.stacktracer

object Tracer {

  /**
   * This implicit is always in scope and will generate a new ZTraceElement any time one is
   * implicitly required (or the method is explicitly called)
   *
   * This can be disabled by importing
   * `import zio.stacktracer.TracingImplicits.disableAutoTrace``
   */
  inline given autoTrace: Tracer.instance.Type =
    ${Macros.autoTraceImpl}

  /**
   * Explicitly generate a new trace
   */
  inline def newTrace: Tracer.instance.Type =
    ${Macros.newTraceImpl}

  val instance: Tracer = new Tracer {
    type Type = String
    val empty = ""
  }

  private[internal] def createTrace(location: String, file: String, line: Int, column: Int): String =
    s"$location($file:$line:$column)".intern
}

sealed trait Tracer {
  type Type <: AnyRef
  val empty: Type
}
