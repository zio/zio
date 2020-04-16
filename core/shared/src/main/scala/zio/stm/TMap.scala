/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.stm

import zio.Chunk
import zio.stm.ZSTM.internal._

/**
 * Transactional map implemented on top of [[TRef]] and [[TArray]]. Resolves
 * conflicts via chaining.
 */
final class TMap[K, V] private (
  private val tBuckets: TRef[TArray[List[(K, V)]]],
  private val tSize: TRef[Int]
) {

  /**
   * Tests whether or not map contains a key.
   */
  def contains(k: K): USTM[Boolean] =
    get(k).map(_.isDefined)

  /**
   * Removes binding for given key.
   */
  def delete(k: K): USTM[Unit] =
    new STM((journal, _, _, _) => {
      val buckets = tBuckets.unsafeGet(journal)
      val idx     = TMap.indexOf(k, buckets.array.length)
      val bucket  = buckets.array(idx).unsafeGet(journal)

      val (toRemove, toRetain) = bucket.partition(_._1 == k)

      if (toRemove.isEmpty)
        TExit.Succeed(STM.unit)
      else {
        buckets.array(idx) = ZTRef.unsafeMake(toRetain)
        TExit.Succeed(tSize.update(_ - 1))
      }
    }).flatten

  /**
   * Atomically folds using a pure function.
   */
  def fold[A](zero: A)(op: (A, (K, V)) => A): USTM[A] =
    new STM((journal, _, _, _) => {
      val buckets = tBuckets.unsafeGet(journal)
      var res     = zero
      var i       = 0

      while (i < buckets.array.length) {
        val bucket = buckets.array(i)
        val items  = bucket.unsafeGet(journal)

        res = items.foldLeft(res)(op)

        i += 1
      }

      TExit.Succeed(res)
    })

  /**
   * Atomically folds using a transactional function.
   */
  def foldM[A, E](zero: A)(op: (A, (K, V)) => STM[E, A]): STM[E, A] =
    toChunk.flatMap(_.foldLeft[STM[E, A]](STM.succeedNow(zero))((tx, kv) => tx.flatMap(op(_, kv))))

  /**
   * Atomically performs transactional-effect for each binding present in map.
   */
  def foreach[E](f: (K, V) => STM[E, Unit]): STM[E, Unit] =
    foldM(())((_, kv) => f(kv._1, kv._2))

  /**
   * Retrieves value associated with given key.
   */
  def get(k: K): USTM[Option[V]] =
    new STM((journal, _, _, _) => {
      val buckets = tBuckets.unsafeGet(journal)
      val idx     = TMap.indexOf(k, buckets.array.length)
      val bucket  = buckets.array(idx).unsafeGet(journal)

      TExit.Succeed(bucket.find(_._1 == k).map(_._2))
    })

  /**
   * Retrieves value associated with given key or default value, in case the
   * key isn't present.
   */
  def getOrElse(k: K, default: => V): USTM[V] =
    get(k).map(_.getOrElse(default))

  /**
   * Collects all keys stored in map.
   */
  def keys: USTM[List[K]] =
    toList.map(_.map(_._1))

  /**
   * If the key `k` is not already associated with a value, stores the provided
   * value, otherwise merge the existing value with the new one using function `f`
   * and store the result
   */
  def merge(k: K, v: V)(f: (V, V) => V): USTM[V] =
    get(k).flatMap(_.fold(put(k, v).as(v)) { v0 =>
      val v1 = f(v0, v)
      put(k, v1).as(v1)
    })

  /**
   * Stores new binding into the map.
   */
  def put(k: K, v: V): USTM[Unit] = {
    def resize(journal: Journal, buckets: TArray[List[(K, V)]]): TArray[List[(K, V)]] = {
      val capacity = buckets.array.length
      val size     = tSize.unsafeGet(journal)
      val data     = Array.ofDim[(K, V)](size)
      var i        = 0
      var j        = 0

      while (i < capacity) {
        val bucket = buckets.array(i)
        val pairs  = bucket.unsafeGet(journal)

        pairs.foreach { kv =>
          data(j) = kv
          j += 1
        }

        i += 1
      }

      val newCapacity = capacity << 1
      val newBuckets  = Array.fill[List[(K, V)]](newCapacity)(Nil)

      data.foreach { pair =>
        val idx = TMap.indexOf(pair._1, newCapacity)
        newBuckets(idx) = pair :: newBuckets(idx)
      }

      val newArray = Array.ofDim[TRef[List[(K, V)]]](newCapacity)

      i = 0
      while (i < newCapacity) {
        newArray(i) = ZTRef.unsafeMake(newBuckets(i))
        i += 1
      }

      new TArray(newArray)
    }

    new STM((journal, _, _, _) => {
      val buckets      = tBuckets.unsafeGet(journal)
      val capacity     = buckets.array.length
      val idx          = TMap.indexOf(k, capacity)
      val bucket       = buckets.array(idx).unsafeGet(journal)
      val shouldUpdate = bucket.exists(_._1 == k)

      if (shouldUpdate) {
        val newBucket = bucket.map(kv => if (kv._1 == k) (k, v) else kv)
        buckets.array(idx).unsafeSet(journal, newBucket)
      } else {
        val newSize   = tSize.unsafeGet(journal) + 1
        val newBucket = (k, v) :: bucket

        buckets.array(idx).unsafeSet(journal, newBucket)
        tSize.unsafeSet(journal, newSize)

        if (capacity * TMap.LoadFactor < newSize)
          tBuckets.unsafeSet(journal, resize(journal, buckets))
      }

      TExit.Succeed(())
    })
  }

  /**
   * Removes bindings matching predicate.
   */
  def removeIf(p: (K, V) => Boolean): USTM[Unit] =
    tBuckets.get.flatMap(_.transform(_.filterNot(kv => p(kv._1, kv._2))))

  /**
   * Retains bindings matching predicate.
   */
  def retainIf(p: (K, V) => Boolean): USTM[Unit] =
    tBuckets.get.flatMap(_.transform(_.filter(kv => p(kv._1, kv._2))))

  /**
   * Collects all bindings into a list.
   */
  def toList: USTM[List[(K, V)]] =
    fold(List.empty[(K, V)])((acc, kv) => kv :: acc)

  /**
   * Collects all bindings into a chunk.
   */
  def toChunk: USTM[Chunk[(K, V)]] =
    new STM((journal, _, _, _) => {
      val buckets  = tBuckets.unsafeGet(journal)
      val capacity = buckets.array.length
      val size     = tSize.unsafeGet(journal)
      val data     = Array.ofDim[(K, V)](size)
      var i        = 0
      var j        = 0

      while (i < capacity) {
        val bucket = buckets.array(i)
        val pairs  = bucket.unsafeGet(journal)

        pairs.foreach { kv =>
          data(j) = kv
          j += 1
        }

        i += 1
      }

      TExit.Succeed(Chunk.fromArray(data))
    })

  /**
   * Collects all bindings into a map.
   */
  def toMap: USTM[Map[K, V]] =
    fold(Map.empty[K, V])(_ + _)

  /**
   * Atomically updates all bindings using a pure function.
   */
  def transform(f: (K, V) => (K, V)): USTM[Unit] =
    new STM((journal, _, _, _) => {
      val g        = f.tupled
      val buckets  = tBuckets.unsafeGet(journal)
      val capacity = buckets.array.length

      val newBuckets = Array.fill[List[(K, V)]](capacity)(Nil)
      var i          = 0

      while (i < capacity) {
        val bucket = buckets.array(i)
        val pairs  = bucket.unsafeGet(journal)

        val it = pairs.iterator
        while (it.hasNext) {
          val newPair   = g(it.next)
          val idx       = TMap.indexOf(newPair._1, capacity)
          val newBucket = newBuckets(idx)

          if (!newBucket.exists(_._1 == newPair._1))
            newBuckets(idx) = newPair :: newBucket
        }

        i += 1
      }

      val newArray = Array.ofDim[TRef[List[(K, V)]]](capacity)

      i = 0

      while (i < capacity) {
        newArray(i) = ZTRef.unsafeMake(newBuckets(i))
        i += 1
      }

      tBuckets.unsafeSet(journal, new TArray(newArray))

      TExit.Succeed(())
    })

  /**
   * Atomically updates all bindings using a transactional function.
   */
  def transformM[E](f: (K, V) => STM[E, (K, V)]): STM[E, Unit] =
    toChunk.flatMap { data =>
      val g = f.tupled

      STM.foreach(data)(g).flatMap { newData =>
        new STM((journal, _, _, _) => {
          val buckets    = tBuckets.unsafeGet(journal)
          val capacity   = buckets.array.length
          val newBuckets = Array.fill[List[(K, V)]](capacity)(Nil)

          newData.foreach { newPair =>
            val idx       = TMap.indexOf(newPair._1, capacity)
            val newBucket = newBuckets(idx)

            if (!newBucket.exists(_._1 == newPair._1))
              newBuckets(idx) = newPair :: newBucket
          }

          val newArray = Array.ofDim[TRef[List[(K, V)]]](capacity)
          var i        = 0
          while (i < capacity) {
            newArray(i) = ZTRef.unsafeMake(newBuckets(i))
            i += 1
          }

          tBuckets.unsafeSet(journal, new TArray(newArray))
          TExit.Succeed(())
        })
      }
    }

  /**
   * Atomically updates all values using a pure function.
   */
  def transformValues(f: V => V): USTM[Unit] =
    transform((k, v) => k -> f(v))

  /**
   * Atomically updates all values using a transactional function.
   */
  def transformValuesM[E](f: V => STM[E, V]): STM[E, Unit] =
    transformM((k, v) => f(v).map(k -> _))

  /**
   * Collects all values stored in map.
   */
  def values: USTM[List[V]] =
    toList.map(_.map(_._2))
}

object TMap {

  /**
   * Makes an empty `TMap`.
   */
  def empty[K, V]: USTM[TMap[K, V]] = fromIterable(Nil)

  /**
   * Makes a new `TMap` initialized with provided iterable.
   */
  def fromIterable[K, V](data: Iterable[(K, V)]): USTM[TMap[K, V]] = {
    val size     = data.size
    val capacity = if (size < InitialCapacity) InitialCapacity else nextPowerOfTwo(size)
    allocate(capacity, data.toList)
  }

  /**
   * Makes a new `TMap` that is initialized with specified values.
   */
  def make[K, V](data: (K, V)*): USTM[TMap[K, V]] = fromIterable(data)

  private final case class UpdateResult(stm: STM[Nothing, Unit])

  private def allocate[K, V](capacity: Int, data: List[(K, V)]): USTM[TMap[K, V]] = {
    val buckets  = Array.fill[List[(K, V)]](capacity)(Nil)
    val distinct = data.toMap

    var size = 0

    val it = distinct.iterator
    while (it.hasNext) {
      val kv  = it.next
      val idx = indexOf(kv._1, capacity)

      buckets(idx) = kv :: buckets(idx)
      size = size + 1
    }

    for {
      tArray   <- TArray.fromIterable(buckets)
      tBuckets <- TRef.make(tArray)
      tSize    <- TRef.make(size)
    } yield new TMap(tBuckets, tSize)
  }

  private def hash[K](k: K): Int = {
    val h = k.hashCode()
    h ^ (h >>> 16)
  }

  private def indexOf[K](k: K, capacity: Int): Int = hash(k) & (capacity - 1)

  private def nextPowerOfTwo(size: Int): Int = {
    val n = -1 >>> Integer.numberOfLeadingZeros(size - 1)
    if (n < 0) 1 else n + 1
  }

  private final val InitialCapacity = 16
  private final val LoadFactor      = 0.75
}
