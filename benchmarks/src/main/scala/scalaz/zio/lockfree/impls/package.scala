package scalaz.zio.lockfree

package object impls {
  def queueByType(typ: String, capacity: Int): LockFreeQueue[Int] = typ match {
    case "RingBuffer"    => new RingBuffer[Int](capacity)
    case "JCTools"       => new JCToolsQueue[Int](capacity)
    case "JucConcurrent" => new JucConcurrentQueue[Int]
    case "JucBlocking"   => new JucBlockingQueue[Int]
    case "SingleLock"    => new SingleLockQueue[Int](capacity)
    case "Unsafe"        => new UnsafeQueue[Int](capacity)
  }
}
