package zio.internal

import java.lang.ref.{ ReferenceQueue, WeakReference }
import java.util.AbstractMap.SimpleImmutableEntry
import java.util.Map.Entry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ ConcurrentHashMap, ConcurrentMap }
import java.util.{ Collection, HashMap, HashSet, Iterator, Map, Set }

import zio.internal.WeakMap.{ WeakIterator, WeakKey }

/**
 * A `WeakMap[K, V]` is a `java.util.concurrent.ConcurrentMap` where the keys
 * are weak references. This allows the weak map to be used in situations
 * requiring concurrent access while still preventing memory leaks by allowing
 * the keys to be garbage collected if there are no references to them.
 */
final class WeakMap[K, V] private (
  private val map: ConcurrentHashMap[WeakKey[K], V],
  private val queue: ReferenceQueue[Any],
  private val garbageCollecting: AtomicBoolean
) extends ConcurrentMap[K, V] {

  /**
   * Constructs a new map.
   */
  def this() =
    this(new ConcurrentHashMap[WeakKey[K], V](), new ReferenceQueue[Any], new AtomicBoolean(false))

  /**
   * Removes all entries from the map.
   */
  def clear(): Unit = {
    garbageCollect()
    map.clear()
  }

  /**
   * Returns whether the map contains the specified key.
   */
  def containsKey(key: Any): Boolean = {
    garbageCollect()
    map.containsKey(newWeakKey(key))
  }

  /**
   * Returns whether the map contains the specified value.
   */
  def containsValue(value: Any): Boolean = {
    garbageCollect()
    map.containsValue(value)
  }

  /**
   * Returns the keys and the values in the map.
   */
  def entrySet(): Set[Entry[K, V]] = {
    garbageCollect()
    val iterator = weakIterator()
    val result   = new HashSet[Entry[K, V]]()
    while (iterator.hasNext) {
      val entry = iterator.next()
      result.add(entry)
    }
    result
  }

  /**
   * Gets the value associated with the specified key from the map or returns
   * `null` if it does not exist.
   */
  def get(key: Any): V = {
    garbageCollect()
    map.get(newWeakKey(key))
  }

  /**
   * Returns whether the map is empty.
   */
  def isEmpty(): Boolean = {
    garbageCollect()
    map.isEmpty()
  }

  /**
   * Returns the keys in the map.
   */
  def keySet(): Set[K] = {
    garbageCollect()
    val iterator = weakIterator()
    val set      = new HashSet[K]()
    while (iterator.hasNext) {
      val entry = iterator.next()
      set.add(entry.getKey)
    }
    set
  }

  /**
   * Puts the specified key and value into the map, returning the old key if it
   * exists or `null` otherwise.
   */
  def put(key: K, value: V): V = {
    garbageCollect()
    map.put(newWeakKey(key), value)
  }

  /**
   * Puts all entries in the specified map into this map.
   */
  def putAll(that: Map[_ <: K, _ <: V]): Unit = {
    garbageCollect()
    val iterator = that.entrySet.iterator()
    val weakMap  = new HashMap[WeakKey[K], V]()
    while (iterator.hasNext) {
      val entry = iterator.next()
      weakMap.put(newWeakKey(entry.getKey), entry.getValue)
    }
    map.putAll(weakMap)
  }

  /**
   * Puts the specified key and value into the map if the key does not already
   * exist in the map.
   */
  override def putIfAbsent(key: K, value: V): V = {
    garbageCollect()
    map.putIfAbsent(newWeakKey(key), value)
  }

  /**
   * Removes the associated key from the map.
   */
  def remove(key: Any): V = {
    garbageCollect()
    map.remove(newWeakKey(key))
  }

  /**
   * Removes the specified key from the map if the value associated with the
   * key equals the specified value.
   */
  override def remove(key: Any, value: Any): Boolean = {
    garbageCollect()
    map.remove(newWeakKey(key), value)
  }

  /**
   * Replaces the value associated with the specified key with `newValue` if
   * the value currently associated with the key is equal to `oldValue`.
   */
  override def replace(key: K, oldValue: V, newValue: V): Boolean = {
    garbageCollect()
    map.replace(newWeakKey(key), oldValue, newValue)
  }

  /**
   * Replaces the value associated with the specified key with the specified
   * value if the key exists in the map.
   */
  override def replace(key: K, value: V): V = {
    garbageCollect()
    map.replace(newWeakKey(key), value)
  }

  /**
   * Returns the size of the map.
   */
  def size(): Int = {
    garbageCollect()
    map.size()
  }

  /**
   * Returns all values in the map.
   */
  def values(): Collection[V] = {
    garbageCollect()
    map.values()
  }

  /**
   * Traverses the map and removes any keys that have been garbage collected,
   * if there is not already another simultaneous process doing the same thing.
   */
  private def garbageCollect(): Unit =
    if (garbageCollecting.compareAndSet(false, true)) {
      var key = queue.poll()
      while (key ne null) {
        map.remove(key)
        key = queue.poll()
      }
      garbageCollecting.set(false)
    }

  /**
   * Constructs a `java.util.Iterator` that is safe to use with a weak
   * collection. Once `hasNext` has been called the next value, if it exists,
   * will be captured as a strong reference, so calling `next` is guaranteed
   * to be safe if `hasNext` returned `true`.
   */
  private def weakIterator(): Iterator[Entry[K, V]] =
    new WeakIterator(map.entrySet.iterator())

  /**
   * Constructs a weak reference to the specified key with a hashcode equal to
   * the hashcode of the key.
   */
  private def newWeakKey[K](key: K): WeakKey[K] =
    new WeakKey[K](key, queue)
}

object WeakMap {

  /**
   * A `WeakKey[K]` is a weak reference to a key of type `K` with a hashcode
   * equal to the hashcode of the key.
   */
  private final class WeakKey[K](key: K, queue: ReferenceQueue[_ >: K]) extends WeakReference[K](key, queue) { self =>
    override def equals(that: Any): Boolean =
      that match {
        case weakKey: WeakKey[_] => self.get == weakKey.get
        case _                   => false
      }
    override val hashCode: Int =
      key.hashCode
  }

  /**
   * A `WeakIterator` wraps an `Iterator` over a weak map, allowing caller to
   * safely access the entries in the weak map without worrying that the entry
   * will be garbage collected while they are using it. The `WeakIterator`
   * always captures a strong reference to the next value in the underlying
   * iterator, if it exists. So calling `next` is guaranteed to be safe if
   * `hasNext` returned `true`.
   */
  private final class WeakIterator[K, V](iterator: Iterator[Entry[WeakKey[K], V]]) extends Iterator[Entry[K, V]] {

    private var strongReference: Entry[K, V] = null
    findNext()

    /**
     * Returns whether there is another entry in the map.
     */
    def hasNext(): Boolean =
      strongReference ne null

    /**
     * Returns the next entry in the map.
     */
    def next(): Entry[K, V] = {
      val entry = strongReference
      findNext()
      entry
    }

    /**
     * Traverses the underlying iterator until it is exhausted or a value that
     * has not been garbage collected is found, captring a strong reference to
     * it.
     */
    private def findNext(): Unit = {
      var continue = true
      while (continue && iterator.hasNext) {
        val entry = iterator.next()
        val key   = entry.getKey.get
        val value = entry.getValue
        if (key.asInstanceOf[AnyRef] ne null) {
          strongReference = new SimpleImmutableEntry(key, value)
          continue = false
        }
      }
      if (continue) {
        strongReference = null
      }
    }
  }
}
