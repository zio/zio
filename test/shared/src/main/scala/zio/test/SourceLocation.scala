package zio.test

private[test] final case class SourceLocation(path: String, line: Int)
object SourceLocation extends SourceLocationVariants
