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

/**
 * Transactional map implemented on top of [[TRef]] and [[TArray]]. Resolves
 * conflicts via chaining.
 */
final class TMap[K, V] private (
  private val tBuckets: TRef[TArray[List[(K, V)]]],
  private val tCapacity: TRef[Int],
  private val tSize: TRef[Int]
) {

  /**
   * Tests whether or not map contains a key.
   */
  def contains(k: K): STM[Nothing, Boolean] =
    get(k).map(_.isDefined)

  /**
   * Removes binding for given key.
   */
  def delete(k: K): STM[Nothing, Unit] = {
    def removeMatching(bucket: List[(K, V)]): STM[Nothing, List[(K, V)]] = {
      val (toRemove, toRetain) = bucket.partition(_._1 == k)
      if (toRemove.isEmpty) STM.succeed(toRetain) else tSize.update(_ - toRemove.size).as(toRetain)
    }

    for {
      buckets <- tBuckets.get
      idx     <- indexOf(k)
      _       <- buckets.updateM(idx, removeMatching)
    } yield ()
  }

  /**
   * Atomically folds using a pure function.
   */
  def fold[A](zero: A)(op: (A, (K, V)) => A): STM[Nothing, A] =
    tBuckets.get.flatMap(_.fold(zero)((acc, bucket) => bucket.foldLeft(acc)(op)))

  /**
   * Atomically folds using a transactional function.
   */
  def foldM[A, E](zero: A)(op: (A, (K, V)) => STM[E, A]): STM[E, A] = {
    def loopM(res: A, remaining: List[(K, V)]): STM[E, A] =
      remaining match {
        case Nil          => STM.succeed(res)
        case head :: tail => op(res, head).flatMap(loopM(_, tail))
      }

    tBuckets.get.flatMap(_.foldM(zero)(loopM))
  }

  /**
   * Atomically performs transactional-effect for each binding present in map.
   */
  def foreach[E](f: (K, V) => STM[E, Unit]): STM[E, Unit] =
    foldM(())((_, kv) => f(kv._1, kv._2))

  /**
   * Retrieves value associated with given key.
   */
  def get(k: K): STM[Nothing, Option[V]] =
    for {
      buckets <- tBuckets.get
      idx     <- indexOf(k)
      bucket  <- buckets(idx)
    } yield bucket.find(_._1 == k).map(_._2)

  /**
   * Retrieves value associated with given key or default value, in case the
   * key isn't present.
   */
  def getOrElse(k: K, default: => V): STM[Nothing, V] =
    get(k).map(_.getOrElse(default))

  /**
   * Collects all keys stored in map.
   */
  def keys: STM[Nothing, List[K]] =
    toList.map(_.map(_._1))

  /**
   * If the key `k` is not already associated with a value, stores the provided
   * value, otherwise merge the existing value with the new one using function `f`
   * and store the result
   */
  def merge(k: K, v: V)(f: (V, V) => V): STM[Nothing, V] =
    get(k).flatMap(_.fold(put(k, v).as(v)) { v0 =>
      val v1 = f(v0, v)
      put(k, v1).as(v1)
    })

  /**
   * Stores new binding into the map.
   */
  def put(k: K, v: V): STM[Nothing, Unit] = {
    def upsert(bucket: List[(K, V)]): STM[Nothing, List[(K, V)]] = {
      val exists = bucket.exists(_._1 == k)

      if (exists)
        STM.succeed(bucket.map(kv => if (kv._1 == k) (k, v) else kv))
      else
        tSize.update(_ + 1).as((k, v) :: bucket)
    }

    def resize(newCapacity: Int): STM[Nothing, Unit] =
      for {
        data       <- toList
        tmap       <- TMap.allocate(newCapacity, data)
        newBuckets <- tmap.tBuckets.get
        _          <- tBuckets.set(newBuckets)
        _          <- tCapacity.set(newCapacity)
      } yield ()

    for {
      buckets     <- tBuckets.get
      idx         <- indexOf(k)
      _           <- buckets.updateM(idx, upsert)
      size        <- tSize.get
      capacity    <- tCapacity.get
      needsResize = capacity * TMap.LoadFactor < size
      _           <- if (needsResize) resize(capacity << 1) else STM.unit
    } yield ()
  }

  /**
   * Removes bindings matching predicate.
   */
  def removeIf(p: (K, V) => Boolean): STM[Nothing, Unit] =
    tBuckets.get.flatMap(_.transform(_.filterNot(kv => p(kv._1, kv._2))))

  /**
   * Retains bindings matching predicate.
   */
  def retainIf(p: (K, V) => Boolean): STM[Nothing, Unit] =
    tBuckets.get.flatMap(_.transform(_.filter(kv => p(kv._1, kv._2))))

  /**
   * Collects all bindings into a list.
   */
  def toList: STM[Nothing, List[(K, V)]] =
    fold(List.empty[(K, V)])((acc, kv) => kv :: acc)

  /**
   * Atomically updates all bindings using a pure function.
   */
  def transform(f: (K, V) => (K, V)): STM[Nothing, Unit] =
    tBuckets.get.flatMap { tArr =>
      val g = f.tupled

      var idx      = 0
      val original = tArr.array
      val capacity = original.length
      val newArr   = Array.ofDim[TRef[List[(K, V)]]](capacity)

      while (idx < capacity) {
        newArr(idx) = TRef.unsafeMake(Nil)
        idx = idx + 1
      }

      val newBuckets = new TArray(newArr)

      val overwrite =
        STM
          .foreach(original)(_.get.map(_.view.map(g)))
          .flatMap[Any, Nothing, Unit] { xs =>
            STM.foreach_(xs.view.flatten.toMap) { kv =>
              newBuckets.update(TMap.indexOf(kv._1, capacity), kv :: _)
            }
          }

      overwrite *> tBuckets.set(newBuckets)
    }

  /**
   * Atomically updates all bindings using a transactional function.
   */
  def transformM[E](f: (K, V) => STM[E, (K, V)]): STM[E, Unit] =
    tBuckets.get.flatMap { tArr =>
      val g = f.tupled

      var idx      = 0
      val original = tArr.array
      val capacity = original.length
      val newArr   = Array.ofDim[TRef[List[(K, V)]]](capacity)

      while (idx < capacity) {
        newArr(idx) = TRef.unsafeMake(Nil)
        idx = idx + 1
      }

      val newBuckets = new TArray(newArr)

      val overwrite =
        STM
          .foreach(original)(_.get.map(_.view.map(g)))
          .flatMap { xs =>
            STM.collectAll(xs.view.flatten.toIterable).flatMap[Any, E, Unit] { items =>
              val distinct = items.toMap
              STM.foreach_(distinct)(kv => newBuckets.update(TMap.indexOf(kv._1, capacity), kv :: _))
            }
          }

      overwrite *> tBuckets.set(newBuckets)
    }

  /**
   * Atomically updates all values using a pure function.
   */
  def transformValues(f: V => V): STM[Nothing, Unit] =
    tBuckets.get.flatMap(_.transform(_.map(kv => kv._1 -> f(kv._2))))

  /**
   * Atomically updates all values using a transactional function.
   */
  def transformValuesM[E](f: V => STM[E, V]): STM[E, Unit] =
    tBuckets.get.flatMap(_.transformM(bucket => STM.collectAll(bucket.map(kv => f(kv._2).map(kv._1 -> _)))))

  /**
   * Collects all values stored in map.
   */
  def values: STM[Nothing, List[V]] =
    toList.map(_.map(_._2))

  private def indexOf(k: K): STM[Nothing, Int] =
    tCapacity.get.map(c => TMap.indexOf(k, c))
}

object TMap {

  /**
   * Makes an empty `TMap`.
   */
  def empty[K, V]: STM[Nothing, TMap[K, V]] = fromIterable(Nil)

  /**
   * Makes a new `TMap` initialized with provided iterable.
   */
  def fromIterable[K, V](data: Iterable[(K, V)]): STM[Nothing, TMap[K, V]] = {
    val size     = data.size
    val capacity = if (size < InitialCapacity) InitialCapacity else nextPowerOfTwo(size)
    allocate(capacity, data.toList)
  }

  /**
   * Makes a new `TMap` that is initialized with specified values.
   */
  def make[K, V](data: (K, V)*): STM[Nothing, TMap[K, V]] = fromIterable(data)

  private def allocate[K, V](capacity: Int, data: List[(K, V)]): STM[Nothing, TMap[K, V]] = {
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
      tChains   <- TArray.fromIterable(buckets)
      tBuckets  <- TRef.make(tChains)
      tCapacity <- TRef.make(capacity)
      tSize     <- TRef.make(size)
    } yield new TMap(tBuckets, tCapacity, tSize)
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
