package zio.internal.stacktracer

object Tracer {
  type Traced = Any

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
      trace match {
        case regex(location, file, line, column) => Some((location, file, line.toInt, column.toInt))
        case _                                   => None
      }
    }
    def fromString(trace: String): Type = trace
  }

  private[internal] def createTrace(location: String, file: String, line: Int, column: Int): String =
    s"$location($file:$line:$column)".intern

  private val regex = """(.*?)\((.*?):(.*?):(.*?)\)""".r
}

sealed trait Tracer {
  type Type <: AnyRef
  val empty: Type
  def unapply(trace: Type): Option[(String, String, Int, Int)]
  def fromString(trace: String): Type
}
