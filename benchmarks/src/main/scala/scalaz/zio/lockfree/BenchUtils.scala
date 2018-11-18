package scalaz.zio.lockfree

import scalaz.zio.lockfree.impls._

object BenchUtils {
  def queueByType[A](
    tpe: BenchQueueType,
    capacity: Int
  ): MutableConcurrentQueue[A] = tpe match {
    case RingBufferType    => new RingBuffer(capacity)
    case JucCLQType        => new JucCLQ
    case JucBlockingType   => new JucBlockingQueue
    case JCToolsType       => new JCToolsQueue(capacity)
    case NotThreadSafeType => new NotThreadSafeQueue(capacity)
  }

  def queueByType[A](tpe: String, capacity: Int): MutableConcurrentQueue[A] =
    BenchQueueType
      .lookup(tpe)
      .fold(sys.error(s"$tpe is not a valid BenchQueueType")) { parsedTpe =>
        queueByType(parsedTpe, capacity)
      }
}
