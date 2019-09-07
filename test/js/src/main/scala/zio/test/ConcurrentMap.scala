package zio.test

import scala.collection.mutable.Map

private[test] final case class ConcurrentHashMap[K, V] private (private val map: Map[K, V]) {
  final def getOrElseUpdate(key: K, op: => V): V =
    map.getOrElseUpdate(key, op)
}

object ConcurrentHashMap {
  final def empty[K, V]: ConcurrentHashMap[K, V] =
    new ConcurrentHashMap[K, V](Map.empty[K, V])
}
