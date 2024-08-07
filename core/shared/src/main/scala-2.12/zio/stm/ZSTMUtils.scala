package zio.stm

import scala.collection.mutable

private object ZSTMUtils {

  def newMutableMap[K, V](expectedNumElements: Int): mutable.HashMap[K, V] = {
    val map = new mutable.HashMap[K, V]
    if (expectedNumElements > 0) map.sizeHint(expectedNumElements)
    map
  }

}
