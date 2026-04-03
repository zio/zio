package zio.internal

sealed abstract class BenchQueueType(val name: String) extends Product with Serializable

private[this] object BenchQueueType {
  def lookup(tpe: String): Option[BenchQueueType] = tpe match {
    case RingBufferPow2Type.name          => Some(RingBufferPow2Type)
    case RingBufferArbType.name           => Some(RingBufferArbType)
    case OneElementQueueType.name         => Some(OneElementQueueType)
    case OneElementQueueNoMetricType.name => Some(OneElementQueueNoMetricType)
    case LinkedQueueType.name             => Some(LinkedQueueType)
    case JucBlockingType.name             => Some(JucBlockingType)
    case JCToolsType.name                 => Some(JCToolsType)
    case NotThreadSafeType.name           => Some(NotThreadSafeType)
    case _                                => None
  }
}

private[this] case object RingBufferPow2Type          extends BenchQueueType("RingBufferPow2")
private[this] case object RingBufferArbType           extends BenchQueueType("RingBufferArb")
private[this] case object OneElementQueueType         extends BenchQueueType("OneElementQueue")
private[this] case object OneElementQueueNoMetricType extends BenchQueueType("OneElementQueueNoMetric")
private[this] case object LinkedQueueType             extends BenchQueueType("LinkedQueue")
private[this] case object JucBlockingType             extends BenchQueueType("JucBlocking")
private[this] case object JCToolsType                 extends BenchQueueType("JCTools")
private[this] case object NotThreadSafeType           extends BenchQueueType("NotThreadSafe")
