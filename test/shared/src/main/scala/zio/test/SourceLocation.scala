package zio.test

final case class SourceLocation(path: String, line: Int)
object SourceLocation extends SourceLocationVariants
