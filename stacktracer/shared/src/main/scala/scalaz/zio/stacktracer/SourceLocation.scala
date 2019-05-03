package scalaz.zio.stacktracer

import scala.util.matching.Regex

final case class SourceLocation(file: String, clazz: String, method: Option[String], line: Int) {

  final def toStackTraceElement: StackTraceElement = {
    val className = clazz.replace('/', '.')
    val methodName =
      method.flatMap(SourceLocation.lambdaNamePattern.findFirstMatchIn(_).map(_.group(1))).getOrElse("apply")

    new StackTraceElement(className, methodName, file, line)
  }

  // FIXME:
  final def prettyPrint: String = toStackTraceElement.toString

}

object SourceLocation {
  val lambdaNamePattern: Regex = """\$anonfun\$(.+?)\$\d""".r
}
