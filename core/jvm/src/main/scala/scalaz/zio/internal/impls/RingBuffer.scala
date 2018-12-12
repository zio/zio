package scalaz.zio.internal.impls

import scalaz.zio.internal.MutableConcurrentQueue

object RingBuffer {
  def build[A](requiredCapacity: Int): MutableConcurrentQueue[A] = new RingBufferPow2(requiredCapacity)
}
