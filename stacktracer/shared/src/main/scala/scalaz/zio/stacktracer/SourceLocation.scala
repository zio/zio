package scalaz.zio.stacktracer

final case class SourceLocation(file: String, clazz: String, method: Option[String], line: Int) {

  final def toStackTraceElement: StackTraceElement =
    new StackTraceElement(clazz, method getOrElse "apply", file, line)

  // FIXME:
  override final def toString: String = toStackTraceElement.toString

}
