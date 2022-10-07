package zio.internal

sealed abstract class BenchQueueType(val name: String) extends Product with Serializable {
  def get[A](capacity: Int): MutableConcurrentQueue[A]
}

object BenchQueueType {
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

  def queueByType[A](tpe: String, capacity: Int): MutableConcurrentQueue[A] =
    lookup(tpe).fold(sys.error(s"$tpe is not a valid BenchQueueType"))(_.get(capacity))
}

private[this] case object RingBufferPow2Type extends BenchQueueType("RingBufferPow2") {
  def get[A](capacity: Int): MutableConcurrentQueue[A] = RingBufferPow2(capacity)
}
private[this] case object RingBufferArbType extends BenchQueueType("RingBufferArb") {
  override def get[A](capacity: Int): MutableConcurrentQueue[A] = RingBufferArb(capacity)
}
private[this] case object OneElementQueueType extends BenchQueueType("OneElementQueue") {
  override def get[A](capacity: Int): MutableConcurrentQueue[A] = new OneElementConcurrentQueue()
}
private[this] case object OneElementQueueNoMetricType extends BenchQueueType("OneElementQueueNoMetric") {
  override def get[A](capacity: Int): MutableConcurrentQueue[A] = new OneElementConcQueueNoMetric()
}
private[this] case object LinkedQueueType extends BenchQueueType("LinkedQueue") {
  override def get[A](capacity: Int): MutableConcurrentQueue[A] = new LinkedQueue
}
private[this] case object JucBlockingType extends BenchQueueType("JucBlocking") {
  override def get[A](capacity: Int): MutableConcurrentQueue[A] = new JucBlockingQueue
}
private[this] case object JCToolsType extends BenchQueueType("JCTools") {
  override def get[A](capacity: Int): MutableConcurrentQueue[A] = new JCToolsQueue(capacity)
}
private[this] case object NotThreadSafeType extends BenchQueueType("NotThreadSafe") {
  override def get[A](capacity: Int): MutableConcurrentQueue[A] = new NotThreadSafeQueue(capacity)
}
