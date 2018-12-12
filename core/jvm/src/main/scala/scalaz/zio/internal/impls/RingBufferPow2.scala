package scalaz.zio.internal.impls

class RingBufferPow2[A](val desiredCapacity: Int) extends RingBuffer[A](RingBuffer.nextPow2(desiredCapacity)) {
  protected final def posToIdx(pos: Long, capacity: Int): Int =
    (pos & (capacity - 1).toLong).toInt
}
