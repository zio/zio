package scalaz.zio.internal.impls

import scalaz.zio.internal.MutableConcurrentQueue

object RingBuffer {
  def build[A](capacity: Int): MutableConcurrentQueue[A] = {
    val pow2Cap = nextPow2(capacity)
    if (capacity == pow2Cap) new RingBufferPow2(capacity)
    else new RingBufferArb(capacity)
  }

  /*
   * Used only once during queue creation. Doesn't need to be
   * performant or anything.
   */
  def nextPow2(n: Int): Int = {
    val nextPow = (Math.log(n.toDouble) / Math.log(2.0)).ceil.toInt
    Math.pow(2.0, nextPow.toDouble).toInt.max(2)
  }
}
