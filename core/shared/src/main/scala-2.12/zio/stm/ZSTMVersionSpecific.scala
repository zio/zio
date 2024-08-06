package zio.stm

import scala.collection.mutable

trait ZSTMVersionSpecific {

  type MutableMap[K, V] = mutable.HashMap[K, V]

  protected def newMutableMap[K, V](expectedNumElements: Int): MutableMap[K, V] = {
    val map = new MutableMap[K, V]
    if (expectedNumElements > 12) {
      val size = Math.ceil(expectedNumElements / 0.75d).toInt
      map.sizeHint(size)
    }
    map
  }
}
