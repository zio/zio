package scalaz.zio.internal;

sealed abstract class BenchQueueType(val name: String) extends Product with Serializable

object BenchQueueType {
  def lookup(tpe: String): Option[BenchQueueType] = tpe match {
    case RingBufferType.name    => Some(RingBufferType)
    case LinkedQueueType.name   => Some(LinkedQueueType)
    case JucBlockingType.name   => Some(JucBlockingType)
    case JCToolsType.name       => Some(JCToolsType)
    case NotThreadSafeType.name => Some(NotThreadSafeType)
    case _                      => None
  }
}

case object RingBufferType    extends BenchQueueType("RingBuffer")
case object LinkedQueueType   extends BenchQueueType("LinkedQueue")
case object JucBlockingType   extends BenchQueueType("JucBlocking")
case object JCToolsType       extends BenchQueueType("JCTools")
case object NotThreadSafeType extends BenchQueueType("NotThreadSafe")
