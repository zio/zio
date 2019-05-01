package scalaz.zio.stacktracer.impl

import scalaz.zio.stacktracer.{ AkkaLineNumbers, SourceExtractor, SourceLocation }

final class AkkaExtractorImpl() extends SourceExtractor {

  override def extractSourceLocation(lambda: AnyRef): Some[SourceLocation] = Some {
    AkkaLineNumbers(lambda) match {
      case AkkaLineNumbers.NoSourceInfo                     =>
        SourceLocation("<nosource>", "<unknown>", None, 0)

      case AkkaLineNumbers.UnknownSourceFormat(explanation) =>
        SourceLocation(explanation, "<unknown>", None, 0)

      case AkkaLineNumbers.SourceFile(filename)             =>
        SourceLocation(filename, "<unknown>", None, 0)

      case AkkaLineNumbers.SourceFileLines(filename, from, _, className, methodName) =>
        SourceLocation(filename, className.replace('/', '.'), Some(methodName), from)
    }
  }

}
