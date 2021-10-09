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
    def unapply(trace: Type): Option[(String, String, Int, Int)] = {
      val regex = """(.*?)\((.*?),(.*?),(.*?)\)""".r
      trace match {
        case regex(location, file, line, column) => Some((location, file, line.toInt, column.toInt))
        case _                                   => None
      }
    }
  }

  private[internal] def createTrace(location: String, file: String, line: Int, column: Int): String =
    s"$location($file:$line:$column)".intern
}

sealed trait Tracer {
  type Type <: AnyRef
  val empty: Type
}
