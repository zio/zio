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
class TMap[K, V] private (private val tBuckets: TRef[TArray[List[(K, V)]]], private val tCapacity: TRef[Int]) { self =>
  import TMap.{ allocate, LoadFactor }

  /**
   * Tests whether or not map contains a key.
   */
  final def contains[E](k: K): STM[E, Boolean] =
    get(k).map(_.isDefined)

  /**
   * Removes binding for given key.
   */
  final def delete[E](k: K): STM[E, Unit] =
    for {
      buckets <- tBuckets.get
      idx     <- indexOf(k)
      _       <- buckets.update(idx, _.filterNot(_._1 == k))
    } yield ()

  /**
   * Atomically folds using pure function.
   */
  final def fold[A](zero: A)(op: (A, (K, V)) => A): STM[Nothing, A] =
    for {
      buckets <- tBuckets.get
      res     <- buckets.fold(zero)((acc, chain) => chain.foldLeft(acc)(op))
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
      res     <- buckets.foldM(zero)((acc, chain) => loopM(STM.succeed(acc), chain))
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
   * Creates new [[TMap]] by mapping all bindings using pure function.
   */
  final def map[K2, V2](f: ((K, V)) => (K2, V2)): STM[Nothing, TMap[K2, V2]] =
    self.fold(List.empty[(K2, V2)])((acc, kv) => f(kv) :: acc).flatMap(TMap(_))

  /**
   * Creates new [[TMap]] by mapping all bindings using effectful function.
   */
  final def mapM[E, K2, V2](f: ((K, V)) => STM[E, (K2, V2)]): STM[E, TMap[K2, V2]] =
    self.foldM(List.empty[(K2, V2)])((acc, kv) => f(kv).map(_ :: acc)).flatMap(TMap(_))

  /**
   * Stores new binding into the map.
   */
  final def put[E](k: K, v: V): STM[E, Unit] = {
    def upsert(bucket: List[(K, V)]): List[(K, V)] =
      bucket match {
        case Nil => List(k -> v)
        case xs  => xs.map(kv => if (kv._1 == k) (k, v) else kv)
      }

    def resize(capacity: Int, data: List[(K, V)]): STM[Nothing, Unit] = {
      val newCapacity = capacity * 2

      for {
        tmap       <- allocate(newCapacity, data)
        newBuckets <- tmap.tBuckets.get
        _          <- self.tBuckets.set(newBuckets)
        _          <- self.tCapacity.set(newCapacity)
      } yield ()
    }

    for {
      buckets     <- tBuckets.get
      idx         <- indexOf(k)
      _           <- buckets.update(idx, upsert)
      data        <- self.fold(List.empty[(K, V)])((acc, kv) => kv :: acc)
      capacity    <- tCapacity.get
      needsResize = capacity * LoadFactor < data.size
      _           <- if (needsResize) resize(capacity, data) else STM.unit
    } yield ()
  }

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
  final def apply[K, V](data: => List[(K, V)]): STM[Nothing, TMap[K, V]] = {
    val capacity = if (data.isEmpty) DefaultCapacity else 2 * data.size
    allocate(capacity, data)
  }

  /**
   * Makes an empty `TMap`.
   */
  final def empty[K, V]: STM[Nothing, TMap[K, V]] = apply(List.empty[(K, V)])

  private final def allocate[K, V](capacity: Int, data: => List[(K, V)]): STM[Nothing, TMap[K, V]] = {
    val buckets     = Array.fill[List[(K, V)]](capacity)(Nil)
    val uniqueItems = data.toMap.toList

    uniqueItems.foreach { kv =>
      val idx = kv._1.hashCode() % capacity
      buckets(idx) = kv :: buckets(idx)
    }

    for {
      tBuckets  <- STM.collectAll(buckets.map(b => TRef.make(b)))
      tArray    <- TRef.make(TArray(tBuckets.toArray))
      tCapacity <- TRef.make(capacity)
    } yield new TMap(tArray, tCapacity)
  }

  private final val DefaultCapacity = 100
  private final val LoadFactor      = 0.75
}
