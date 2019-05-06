package scalaz.zio.stacktracer.impl

import scalaz.zio.stacktracer.{ AkkaLineNumbers, SourceLocation, Tracer }

final class AkkaTracer extends Tracer {

  override def traceLocation(lambda: AnyRef): Some[SourceLocation] = Some {
    AkkaLineNumbers(lambda) match {
      case AkkaLineNumbers.NoSourceInfo =>
        SourceLocation("<nosource>", "<unknown>", None, 0)

      case AkkaLineNumbers.UnknownSourceFormat(explanation) =>
        SourceLocation(explanation, "<unknown>", None, 0)

      case AkkaLineNumbers.SourceFile(filename) =>
        SourceLocation(filename, "<unknown>", None, 0)

      case AkkaLineNumbers.SourceFileLines(filename, from, _, className, methodName) =>
        SourceLocation(filename.intern(), className.intern(), Some(methodName.intern()), from)
    }
  }

}
