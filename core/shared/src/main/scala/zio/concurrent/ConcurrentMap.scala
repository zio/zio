package zio.concurrent

import zio.{Chunk, ChunkBuilder, UIO}

import java.util.concurrent.ConcurrentHashMap

/**
 * Wrapper over [[java.util.concurrent.ConcurrentHashMap]].
 */
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

  /**
   * Removes binding for the given key, optionally returning value associated
   * with it.
   */
  def remove(key: K): UIO[Option[V]] = 
    UIO(Option(underlying.remove(key)))

  /**
   * Removes binding for the given key if it is mapped to a given value.
   */
  def remove(key: K, value: V): UIO[Boolean] =
    UIO(underlying.remove(key, value))

  def replace(key: K, value: V): UIO[Option[V]]               = ???
  def replace(key: K, oldValue: V, newValue: V): UIO[Boolean] = ???

  /**
   * Collects all bindings into a chunk.
   */
  def toChunk: UIO[Chunk[(K, V)]] =
    UIO {
      val builder = ChunkBuilder.make[(K, V)]()

      val it = underlying.entrySet().iterator()
      while (it.hasNext()) {
        val entry = it.next()
        builder += entry.getKey() -> entry.getValue()
      }

      builder.result()
    }

  /**
   * Collects all bindings into a list.
   */
  def toList: UIO[List[(K, V)]] =
    toChunk.map(_.toList)
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
