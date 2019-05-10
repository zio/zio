package scalaz.zio.internal.stacktracer

sealed abstract class ZTraceElement extends Product with Serializable {
  def prettyPrint: String
}

object ZTraceElement {

  final case class NoLocation(error: String) extends ZTraceElement {
    final def prettyPrint = s"<couldn't get location, error: $error>"
  }

  final case class SourceLocation(file: String, clazz: String, method: String, line: Int) extends ZTraceElement {
    final def toStackTraceElement: StackTraceElement = new StackTraceElement(clazz, method, file, line)
    final def prettyPrint: String = toStackTraceElement.toString
  }

}
