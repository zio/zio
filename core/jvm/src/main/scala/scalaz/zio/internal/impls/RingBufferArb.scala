package scalaz.zio.internal.impls

class RingBufferArb[A] private (capacity: Int) extends RingBuffer[A](capacity) {
  protected final def posToIdx(pos: Long, capacity: Int): Int = (pos % capacity.toLong).toInt
}

object RingBufferArb {
  def apply[A](requestedCapacity: Int): RingBufferArb[A] = {
    assert(requestedCapacity >= 2)

    new RingBufferArb(requestedCapacity)
  }
}
