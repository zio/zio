package scalaz.zio.internal

private[zio]
final class SingleThreadedRingBuffer[A <: AnyRef](capacity: Int) {
  private[this] val array = new Array[AnyRef](capacity)
  private[this] var size = 0
  private[this] var current = 0

  def put(value: A): Unit = {
    array(current) = value
    increment()
  }

  def dropLast(): Unit = {
    if (size > 0) {
      decrement()
      array(current) = null
    }
  }

  def toList: List[A] = {
    val begin = current - size

    val newArray = if (begin < 0) {
      array.slice(capacity + begin, capacity) ++ array.slice(0, current)
    } else {
      array.slice(begin, current)
    }

    newArray.toList.asInstanceOf[List[A]]
  }

  @inline private[this] def increment(): Unit = {
    if (size < capacity) {
      size = size + 1
    }
    current = (current + 1) % capacity
  }

  @inline private[this] def decrement(): Unit = {
    assert(size > 0 && current >= 0 && current < capacity)
    size = size - 1
    if (current > 0) {
      current = current - 1
    } else {
      current = capacity - 1
    }
  }
}


object SingleThreadedRingBuffer {
  def apply[A <: AnyRef](capacity: Int): SingleThreadedRingBuffer[A] = new SingleThreadedRingBuffer[A](capacity)
}
