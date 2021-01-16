package zio.test

private[zio] final case class SourceLocation(path: String, line: Int)
object SourceLocation extends SourceLocationVariants
