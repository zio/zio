package scalaz.zio.internal.impls

class RingBufferPow2[A](val desiredCapacity: Int) extends RingBufferBase[A](RingBufferPow2.nextPow2(desiredCapacity)) {
  protected final def posToIdx(pos: Long, capacity: Int): Int =
    (pos & (capacity - 1).toLong).toInt //(pos % capacity.toLong).toInt //(pos & mask).toInt
}

object RingBufferPow2 {
  /*
   * Used only once during queue creation. Doesn't need to be
   * performant or anything.
   */
  def nextPow2(n: Int): Int = {
    val nextPow = (Math.log(n.toDouble) / Math.log(2.0)).ceil.toInt
    Math.pow(2.0, nextPow.toDouble).toInt.max(2)
  }
}
