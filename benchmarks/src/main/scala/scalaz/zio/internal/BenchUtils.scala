package scalaz.zio.internal

import scalaz.zio.internal.impls._

object BenchUtils {
  def queueByType[A](
    tpe: BenchQueueType,
    capacity: Int
  ): MutableConcurrentQueue[A] = tpe match {
    case RingBufferType    => new RingBuffer(capacity)
    case LinkedQueueType   => new LinkedQueue
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
