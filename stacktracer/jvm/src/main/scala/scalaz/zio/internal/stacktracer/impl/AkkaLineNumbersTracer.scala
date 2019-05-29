package scalaz.zio.internal.stacktracer.impl

import scalaz.zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import scalaz.zio.internal.stacktracer.{ Tracer, ZTraceElement }

import scala.util.matching.Regex

/**
 * A [[Tracer]] implementation powered by Akka's `LineNumbers` bytecode parser (shipped with ZIO, no dependency on Akka)
 * */
final class AkkaLineNumbersTracer extends Tracer {

  final def traceLocation(lambda: AnyRef): ZTraceElement =
    AkkaLineNumbers(lambda) match {
      case AkkaLineNumbers.NoSourceInfo =>
        NoLocation(s"couldn't find class file for lambda:$lambda")

      case AkkaLineNumbers.UnknownSourceFormat(explanation) =>
        NoLocation(s"couldn't parse class file for lambda:$lambda, error: $explanation")

      case AkkaLineNumbers.SourceFile(filename) =>
        SourceLocation(filename, "<unknown>", "<unknown>", 0)

      case AkkaLineNumbers.SourceFileLines(filename, from, _, classNameSlashes, methodAnonfun) =>
        val className = classNameSlashes.replace('/', '.')
        val methodName = lambdaNamePattern
          .findFirstMatchIn(methodAnonfun)
          .flatMap(Option apply _.group(1))
          .getOrElse(methodAnonfun)

        SourceLocation(filename.intern(), className.intern(), methodName.intern(), from)
    }

  private[this] final val lambdaNamePattern: Regex = """\$anonfun\$(.+?)\$\d""".r
}
