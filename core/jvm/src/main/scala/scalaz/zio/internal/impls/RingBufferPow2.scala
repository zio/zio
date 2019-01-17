package scalaz.zio.internal.impls

class RingBufferPow2[A](val requestedCapacity: Int) extends RingBuffer[A](RingBuffer.nextPow2(requestedCapacity)) {
  protected final def posToIdx(pos: Long, capacity: Int): Int =
    (pos & (capacity - 1).toLong).toInt
}

object RingBufferPow2 {
  def apply[A](requestedCapacity: Int): RingBufferPow2[A] = {
    assert(requestedCapacity > 0)

    new RingBufferPow2(requestedCapacity)
  }
}
