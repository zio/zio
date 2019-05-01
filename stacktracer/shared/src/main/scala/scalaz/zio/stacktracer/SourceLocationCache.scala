package scalaz.zio.stacktracer

abstract class SourceLocationCache extends Serializable {
  def getOrElseUpdate(lambda: AnyRef, f: AnyRef => SourceLocation): SourceLocation
}
