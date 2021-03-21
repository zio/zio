package zio.stream.compression

case class CompressionParameters(
  level: CompressionLevel,
  strategy: CompressionStrategy,
  flushMode: FlushMode
)

sealed abstract class CompressionLevel(val jValue: Int)

object CompressionLevel {
  case object DefaultCompression extends CompressionLevel(-1)
  case object NoCompression      extends CompressionLevel(0)
  case object BestSpeed          extends CompressionLevel(1)
  case object CompressionLevel2  extends CompressionLevel(2)
  case object CompressionLevel3  extends CompressionLevel(3)
  case object CompressionLevel4  extends CompressionLevel(4)
  case object CompressionLevel5  extends CompressionLevel(5)
  case object CompressionLevel6  extends CompressionLevel(6)
  case object CompressionLevel7  extends CompressionLevel(7)
  case object CompressionLevel8  extends CompressionLevel(8)
  case object BestCompression    extends CompressionLevel(9)
}

sealed abstract class CompressionStrategy(val jValue: Int)
object CompressionStrategy {
  case object DefaultStrategy extends CompressionStrategy(0)
  case object Filtered        extends CompressionStrategy(1)
  case object HuffmanOnly     extends CompressionStrategy(2)
}

sealed abstract class FlushMode(val jValue: Int)
object FlushMode {
  case object NoFlush   extends FlushMode(0)
  case object SyncFlush extends FlushMode(2)
  case object FullFlush extends FlushMode(3)
}
