package zio.concurrent

import zio.{Chunk, ChunkBuilder, UIO}

import java.util.concurrent.ConcurrentHashMap
import com.github.ghik.silencer.silent

import java.util.function.BiConsumer
import scala.collection.JavaConverters._

/**
 * Wrapper over `java.util.concurrent.ConcurrentHashMap`.
 */
final class ConcurrentMap[K, V] private (private val underlying: ConcurrentHashMap[K, V]) extends AnyVal {

  /**
   * Finds the first element of a map for which the partial function is defined
   * and applies the function to it.
   */
  def collectFirst[B](pf: PartialFunction[(K, V), B]): UIO[Option[B]] =
    UIO {
      var result = Option.empty[B]
      underlying.forEach {
        makeBiConsumer { (k: K, v: V) =>
          if (result.isEmpty && pf.isDefinedAt((k, v)))
            result = Some(pf((k, v)))
        }
      }
      result
    }

  /**
   * Attempts to compute a mapping for the given key and its current mapped
   * value.
   */
  def compute(key: K, remap: (K, V) => V): UIO[Option[V]] =
    UIO(Option(underlying.compute(key, remapWith(remap))))

  /**
   * Computes a value of a non-existing key.
   */
  def computeIfAbsent(key: K, map: K => V): UIO[V] =
    UIO(underlying.computeIfAbsent(key, mapWith(map)))

  /**
   * Attempts to compute a new mapping of an existing key.
   */

  def computeIfPresent(key: K, remap: (K, V) => V): UIO[Option[V]] =
    UIO(Option(underlying.computeIfPresent(key, remapWith(remap))))

  /**
   * Tests whether a given predicate holds true for at least one element in a
   * map.
   */

  def exists(p: (K, V) => Boolean): UIO[Boolean] =
    UIO {
      var result = false
      underlying.forEach {
        makeBiConsumer { (k: K, v: V) =>
          if (!result && p(k, v))
            result = true
        }
      }
      result
    }

  /**
   * Folds the elements of a map using the given binary operator.
   */
  def fold[S](zero: S)(f: (S, (K, V)) => S): UIO[S] =
    UIO {
      var result: S = zero
      underlying.forEach {
        makeBiConsumer { (k: K, v: V) =>
          result = f(result, (k, v))
        }
      }
      result
    }

  /**
   * Tests whether a predicate is satisfied by all elements of a map.
   */
  def forall(p: (K, V) => Boolean): UIO[Boolean] =
    UIO {
      var result = true
      underlying.forEach {
        makeBiConsumer { (k: K, v: V) =>
          if (result && !p(k, v))
            result = false
        }
      }
      result
    }

  /**
   * Retrieves the value associated with the given key.
   */
  def get(key: K): UIO[Option[V]] =
    UIO(Option(underlying.get(key)))

  /**
   * Adds a new key-value pair and optionally returns previously bound value.
   */
  def put(key: K, value: V): UIO[Option[V]] =
    UIO(Option(underlying.put(key, value)))

  /**
   * Adds all new key-value pairs
   */
  def putAll(keyValues: (K, V)*): UIO[Unit] =
    UIO(underlying.putAll(keyValues.toMap.asJava): @silent("JavaConverters"))

  /**
   * Adds a new key-value pair, unless the key is already bound to some other
   * value.
   */
  def putIfAbsent(key: K, value: V): UIO[Option[V]] =
    UIO(Option(underlying.putIfAbsent(key, value)))

  /**
   * Removes the entry for the given key, optionally returning value associated
   * with it.
   */
  def remove(key: K): UIO[Option[V]] =
    UIO(Option(underlying.remove(key)))

  /**
   * Removes the entry for the given key if it is mapped to a given value.
   */
  def remove(key: K, value: V): UIO[Boolean] =
    UIO(underlying.remove(key, value))

  /**
   * Removes all elements which do not satisfy the given predicate.
   */
  def removeIf(p: (K, V) => Boolean): UIO[Unit] =
    UIO(
      underlying.forEach {
        makeBiConsumer { (k: K, v: V) =>
          if (p(k, v)) {
            val _ = underlying.remove(k)
          }
        }
      }
    )

  /**
   * Removes all elements which do not satisfy the given predicate.
   */
  def retainIf(p: (K, V) => Boolean): UIO[Unit] =
    UIO(
      underlying.forEach {
        makeBiConsumer { (k: K, v: V) =>
          if (!p(k, v)) {
            val _ = underlying.remove(k)
          }
        }
      }
    )

  /**
   * Replaces the entry for the given key only if it is mapped to some value.
   */
  def replace(key: K, value: V): UIO[Option[V]] =
    UIO(Option(underlying.replace(key, value)))

  /**
   * Replaces the entry for the given key only if it was previously mapped to a
   * given value.
   */
  def replace(key: K, oldValue: V, newValue: V): UIO[Boolean] =
    UIO(underlying.replace(key, oldValue, newValue))

  /**
   * Collects all entries into a chunk.
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
   * Collects all entries into a list.
   */
  def toList: UIO[List[(K, V)]] =
    toChunk.map(_.toList)

  private[this] def mapWith(map: K => V): java.util.function.Function[K, V] =
    new java.util.function.Function[K, V] {
      def apply(k: K): V = map(k)
    }

  private[this] def remapWith(remap: (K, V) => V): java.util.function.BiFunction[K, V, V] =
    new java.util.function.BiFunction[K, V, V] {
      def apply(k: K, v: V): V = if (v == null) v else remap(k, v)
    }

  private[this] def makeBiConsumer(f: (K, V) => Unit): BiConsumer[K, V] =
    new BiConsumer[K, V] {
      override def accept(t: K, u: V): Unit = f(t, u)
    }
}

object ConcurrentMap {

  /**
   * Makes an empty `ConcurrentMap`.
   */
  def empty[K, V]: UIO[ConcurrentMap[K, V]] =
    UIO(new ConcurrentMap(new ConcurrentHashMap()))

  /**
   * Makes a new `ConcurrentMap` initialized with provided collection of
   * key-value pairs.
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
