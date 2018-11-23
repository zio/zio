package scalaz.zio.internal;

sealed abstract class BenchQueueType(val name: String) extends Product with Serializable

object BenchQueueType {
  def lookup(tpe: String): Option[BenchQueueType] = tpe match {
    case RingBufferType.name    => Some(RingBufferType)
    case JucCLQType.name        => Some(JucCLQType)
    case JucBlockingType.name   => Some(JucBlockingType)
    case JCToolsType.name       => Some(JCToolsType)
    case NotThreadSafeType.name => Some(NotThreadSafeType)
    case _                      => None
  }
}

case object RingBufferType    extends BenchQueueType("RingBuffer")
case object JucCLQType        extends BenchQueueType("JucCLQ")
case object JucBlockingType   extends BenchQueueType("JucBlocking")
case object JCToolsType       extends BenchQueueType("JCTools")
case object NotThreadSafeType extends BenchQueueType("NotThreadSafe")
