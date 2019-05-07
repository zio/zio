package scalaz.zio.internal

private[zio] final class SingleThreadedRingBuffer[A <: AnyRef](capacity: Int) {
  private[this] val array = new Array[AnyRef](capacity)
  private[this] var idx   = 0

  def put(value: A): Unit = {
    array(currentIndex) = value
    increment()
  }

  def pop(): Unit = {
    // TODO: remove
//    if (currentIndex == 0) throw new Exception("empty!")
    //

    array(currentIndex) = null
    decrement()
  }

  def toList: List[A] = {
    val cIdx = currentIndex

    val newArray = if (idx <= capacity) {
      array.slice(0, idx)
    } else if (cIdx == 0) {
      array.slice(cIdx, capacity)
    } else {
      array.slice(cIdx, capacity) ++ array.slice(0, cIdx)
    }

    newArray.toList.asInstanceOf[List[A]]
  }

  @inline private[this] def increment(): Unit = {
    idx += 1
    if (idx == 2 * capacity) { // to avoid infinite increments
      idx = capacity
    }
  }

  @inline private[this] def decrement(): Unit =
    if (idx != 0) {
      idx = idx - 1
    }

  @inline private[this] def currentIndex: Int =
    idx % capacity
}

object SingleThreadedRingBuffer {
  def apply[A <: AnyRef](capacity: Int): SingleThreadedRingBuffer[A] = new SingleThreadedRingBuffer[A](capacity)
}
