package scalaz.zio.lockfree

import scala.reflect.ClassTag

package object impls {
  def queueByType(typ: String, capacity: Int): MutableConcurrentQueue[Int] = typ match {
    case "RingBuffer"    => new RingBuffer[Int](capacity)
    case "JCTools"       => new JCToolsQueue[Int](capacity)
    case "JucConcurrent" => new JucConcurrentQueue[Int]
    case "JucBlocking"   => new JucBlockingQueue[Int]
    case "SingleLock"    => new SingleLockQueue[Int](capacity)
    case "Unsafe"        => new UnsafeQueue[Int](capacity)
  }

  def queueByTypeA[A: ClassTag](typ: String, capacity: Int): MutableConcurrentQueue[A] = typ match {
    case "RingBuffer"    => new RingBuffer(capacity)
    case "JCTools"       => new JCToolsQueue(capacity)
    case "JucConcurrent" => new JucConcurrentQueue
    case "JucBlocking"   => new JucBlockingQueue
    case "SingleLock"    => new SingleLockQueue(capacity)
    case "Unsafe"        => new UnsafeQueue(capacity)
  }
}
