package zio.stm

import scala.collection.mutable

trait ZSTMVersionSpecific {

  type MutableMap[K, V] = mutable.HashMap[K, V]

  protected def newMutableMap[K, V](expectedNumElements: Int): MutableMap[K, V] = {
    val size = if (expectedNumElements <= 3) 4 else Math.ceil(expectedNumElements / 0.75d).toInt
    new MutableMap[K, V](size, 0.75d)
  }
}
