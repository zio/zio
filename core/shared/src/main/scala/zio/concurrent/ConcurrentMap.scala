package zio.concurrent

import java.util.concurrent.ConcurrentHashMap
import zio.UIO

final class ConcurrentMap[K, V] private (private val underlying: ConcurrentHashMap[K, V]) extends AnyVal {

  /**
   * Retrieves the value associated with the given key.
   */
  def get(key: K): UIO[Option[V]] =
    UIO(Option(underlying.get(key)))

  /**
   * Associates the given key with a given value, unless the key was already
   * associated with some other value.
   */
  def putIfAbsent(key: K, value: V): UIO[Option[V]] =
    UIO(Option(underlying.putIfAbsent(key, value)))

  def remove(key: K): UIO[Option[V]]                          = ???
  def replace(key: K, value: V): UIO[Option[V]]               = ???
  def replace(key: K, oldValue: V, newValue: V): UIO[Boolean] = ???
  def update(key: K, value: V): UIO[Any]                      = ???
}

object ConcurrentMap {

  /**
   * Makes an empty `ConcurrentMap`.
   */
  def empty[K, V]: UIO[ConcurrentMap[K, V]] =
    UIO(new ConcurrentMap(new ConcurrentHashMap()))

  /**
   * Makes a new `ConcurrentMap` initialized with provided collection of key-value pairs.
   */
  def fromIterable[K, V](pairs: Iterable[(K, V)]): UIO[ConcurrentMap[K, V]] =
    UIO {
      val underlying = new ConcurrentHashMap[K, V]()

      pairs.foreach(kv => underlying.put(kv._1, kv._2))

      new ConcurrentMap(underlying)
    }

  /**
   * Makes a new `ConcurrentMap` initialized with provided key-value pairs.
   */
  def make[K, V](pairs: (K, V)*): UIO[ConcurrentMap[K, V]] =
    UIO {
      val underlying = new ConcurrentHashMap[K, V]()

      pairs.foreach(kv => underlying.put(kv._1, kv._2))

      new ConcurrentMap(underlying)
    }
}
