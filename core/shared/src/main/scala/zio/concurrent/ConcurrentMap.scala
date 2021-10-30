package zio.concurrent

import java.util.concurrent.ConcurrentHashMap
import zio.UIO

final class ConcurrentMap[K, V] private (private val underlying: ConcurrentHashMap[K, V]) extends AnyVal {
  def get(key: K): UIO[Option[V]]                             = ???
  def putIfAbsent(key: K, value: V): UIO[Option[V]]           = ???
  def remove(key: K): UIO[Option[V]]                          = ???
  def replace(key: K, value: V): UIO[Option[V]]               = ???
  def replace(key: K, oldValue: V, newValue: V): UIO[Boolean] = ???
  def update(key: K, value: V): UIO[Any]                      = ???
}

object ConcurrentMap {
  def empty[K, V]: UIO[ConcurrentMap[K, V]] = ???

  def make[K, V](pairs: (K, V)*): UIO[ConcurrentMap[K, V]] = ???
}
