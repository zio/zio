package scalaz.zio.stacktracer

abstract class SourceExtractor extends Serializable {
  def extractSourceLocation(lambda: AnyRef): Option[SourceLocation]
}
