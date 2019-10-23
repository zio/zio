/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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
 *
 * Caution: doesn't provide stack-safety guarantees.
 */
class TMap[K, V] private (
  private val tBuckets: TRef[TArray[List[(K, V)]]],
  private val tCapacity: TRef[Int],
  private val tSize: TRef[Int]
) { self =>

  /**
   * Tests whether or not map contains a key.
   */
  final def contains[E](k: K): STM[E, Boolean] =
    get(k).map(_.isDefined)

  /**
   * Removes binding for given key.
   */
  final def delete[E](k: K): STM[E, Unit] = {
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
   * Atomically folds using pure function.
   */
  final def fold[A](zero: A)(op: (A, (K, V)) => A): STM[Nothing, A] =
    for {
      buckets <- tBuckets.get
      res     <- buckets.fold(zero)((acc, bucket) => bucket.foldLeft(acc)(op))
    } yield res

  /**
   * Atomically folds using effectful function.
   */
  final def foldM[A, E](zero: A)(op: (A, (K, V)) => STM[E, A]): STM[E, A] = {
    def loopM(acc: STM[E, A], remaining: List[(K, V)]): STM[E, A] =
      remaining match {
        case Nil          => acc
        case head :: tail => loopM(acc.flatMap(op(_, head)), tail)
      }

    for {
      buckets <- tBuckets.get
      res     <- buckets.foldM(zero)((acc, bucket) => loopM(STM.succeed(acc), bucket))
    } yield res
  }

  /**
   * Atomically performs side-effect for each binding present in map.
   */
  final def foreach[E](f: ((K, V)) => STM[E, Unit]): STM[E, Unit] =
    self.foldM(())((_, kv) => f(kv))

  /**
   * Retrieves value associated with given key.
   */
  final def get[E](k: K): STM[E, Option[V]] =
    for {
      buckets <- tBuckets.get
      idx     <- indexOf(k)
      bucket  <- buckets(idx)
    } yield bucket.find(_._1 == k).map(_._2)

  /**
   * Retrieves value associated with given key or default value, in case the
   * key isn't present.
   */
  final def getOrElse[E](k: K, default: => V): STM[E, V] =
    get(k).map(_.getOrElse(default))

  /**
   * Stores new binding into the map.
   */
  final def put[E](k: K, v: V): STM[E, Unit] = {
    def upsert(bucket: List[(K, V)]): STM[Nothing, List[(K, V)]] = {
      val newBucket = bucket match {
        case Nil => List(k -> v)
        case xs  => xs.map(kv => if (kv._1 == k) (k, v) else kv)
      }

      val diff = newBucket.size - bucket.size

      if (diff == 0) STM.succeed(newBucket) else tSize.update(_ + diff).as(newBucket)
    }

    def resize(newCapacity: Int): STM[Nothing, Unit] =
      for {
        data       <- self.fold(List.empty[(K, V)])((acc, kv) => kv :: acc)
        tmap       <- TMap.allocate(newCapacity, data)
        newBuckets <- tmap.tBuckets.get
        _          <- self.tBuckets.set(newBuckets)
        _          <- self.tCapacity.set(newCapacity)
      } yield ()

    for {
      buckets     <- tBuckets.get
      idx         <- indexOf(k)
      _           <- buckets.updateM(idx, upsert)
      size        <- tSize.get
      capacity    <- tCapacity.get
      needsResize = capacity * TMap.LoadFactor < size
      _           <- if (needsResize) resize(capacity * 2) else STM.unit
    } yield ()
  }

  /**
   * Atomically updates all bindings using pure function.
   */
  final def transform(f: ((K, V)) => (K, V)): STM[Nothing, Unit] =
    for {
      data       <- self.fold(List.empty[(K, V)])((acc, kv) => f(kv) :: acc)
      tmap       <- TMap.fromIterable(data)
      newBuckets <- tmap.tBuckets.get
      _          <- self.tBuckets.set(newBuckets)
    } yield ()

  /**
   * Atomically updates all bindings using effectful function.
   */
  final def transformM[E](f: ((K, V)) => STM[E, (K, V)]): STM[E, Unit] =
    for {
      data       <- self.foldM(List.empty[(K, V)])((acc, kv) => f(kv).map(_ :: acc))
      tmap       <- TMap.fromIterable(data)
      newBuckets <- tmap.tBuckets.get
      _          <- self.tBuckets.set(newBuckets)
    } yield ()

  /**
   * Removes bindings matching predicate.
   */
  final def removeIf(p: ((K, V)) => Boolean): STM[Nothing, Unit] =
    tBuckets.get.flatMap(_.transform(_.filterNot(p)))

  /**
   * Retains bindings matching predicate.
   */
  final def retainIf(p: ((K, V)) => Boolean): STM[Nothing, Unit] =
    tBuckets.get.flatMap(_.transform(_.filter(p)))

  private def indexOf(k: K): STM[Nothing, Int] =
    tCapacity.get.map(c => k.hashCode() % c)
}

object TMap {

  /**
   * Makes a new `TMap` that is initialized with the specified values.
   */
  final def apply[K, V](data: (K, V)*): STM[Nothing, TMap[K, V]] = fromIterable(data)

  /**
   * Makes an empty `TMap`.
   */
  final def empty[K, V]: STM[Nothing, TMap[K, V]] = fromIterable(Nil)

  /**
   * Makes a new `TMap` initialized with provided iterable.
   */
  final def fromIterable[K, V](data: Iterable[(K, V)]): STM[Nothing, TMap[K, V]] = {
    val capacity = if (data.isEmpty) DefaultCapacity else 2 * data.size
    allocate(capacity, data.toList)
  }

  private final def allocate[K, V](capacity: Int, data: List[(K, V)]): STM[Nothing, TMap[K, V]] = {
    val buckets     = Array.fill[List[(K, V)]](capacity)(Nil)
    val uniqueItems = data.toMap.toList

    uniqueItems.foreach { kv =>
      val idx = kv._1.hashCode() % capacity
      buckets(idx) = kv :: buckets(idx)
    }

    for {
      tChains   <- STM.collectAll(buckets.map(b => TRef.make(b)))
      tBuckets  <- TRef.make(TArray(tChains.toArray))
      tCapacity <- TRef.make(capacity)
      tSize     <- TRef.make(uniqueItems.size)
    } yield new TMap(tBuckets, tCapacity, tSize)
  }

  private final val DefaultCapacity = 100
  private final val LoadFactor      = 0.75
}
