package scalaz.zio.internal.impls

class RingBufferArb[A] private (capacity: Int) extends RingBuffer[A](capacity) {
  protected final def posToIdx(pos: Long, capacity: Int): Int = (pos % capacity.toLong).toInt
}

object RingBufferArb {
  final def apply[A](capacity: Int): RingBufferArb[A] = {
    assert(capacity >= 2)

    new RingBufferArb(capacity)
  }
}
