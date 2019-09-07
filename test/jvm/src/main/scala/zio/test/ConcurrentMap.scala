package zio.test

import java.util.concurrent.{ ConcurrentHashMap => JConcurrentHashMap }

private[test] final case class ConcurrentHashMap[K, V] private (private val map: JConcurrentHashMap[K, V]) {
  final def getOrElseUpdate(key: K, op: => V): V =
    map.computeIfAbsent(key, _ => op)
}

object ConcurrentHashMap {
  final def empty[K, V]: ConcurrentHashMap[K, V] =
    new ConcurrentHashMap[K, V](new JConcurrentHashMap[K, V]())
}
