package scalaz.zio.internal.impls

class RingBufferArb[A](capacity: Int) extends RingBuffer[A](capacity) {
  protected final def posToIdx(pos: Long, capacity: Int): Int = (pos % capacity.toLong).toInt
}
