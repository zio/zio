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
 */
class TMap[K, V] private (
  private val tBuckets: TRef[TArray[List[(K, V)]]],
  private val tCapacity: TRef[Int],
  private val tSize: TRef[Int]
) {

  /**
   * Tests whether or not map contains a key.
   */
  final def contains(k: K): STM[Nothing, Boolean] =
    get(k).map(_.isDefined)

  /**
   * Removes binding for given key.
   */
  final def delete(k: K): STM[Nothing, Unit] = {
    def removeMatching(bucket: List[(K, V)]): STM[Nothing, List[(K, V)]] = {
      val (toRemove, toRetain) = bucket.partition(_._1 == k)
      if (toRemove.isEmpty) STM.succeed(toRetain) else tSize.update(_ - toRemove.size).as(toRetain)
    }

    tBuckets.get.flatMap { buckets =>
      indexOf(k).flatMap { idx =>
        buckets.updateM(idx, removeMatching)
      }
    }.unit
  }

  /**
   * Atomically folds using pure function.
   */
  final def fold[A](zero: A)(op: (A, (K, V)) => A): STM[Nothing, A] =
    tBuckets.get.flatMap { buckets =>
      buckets.fold(zero)((acc, bucket) => bucket.foldLeft(acc)(op))
    }

  /**
   * Atomically folds using effectful function.
   */
  final def foldM[A, E](zero: A)(op: (A, (K, V)) => STM[E, A]): STM[E, A] = {
    def loopM(acc: STM[E, A], remaining: List[(K, V)]): STM[E, A] =
      remaining match {
        case Nil          => acc
        case head :: tail => loopM(acc.flatMap(op(_, head)), tail)
      }

    tBuckets.get.flatMap { buckets =>
      buckets.foldM(zero) { (acc, bucket) =>
        loopM(STM.succeed(acc), bucket)
      }
    }
  }

  /**
   * Atomically performs side-effect for each binding present in map.
   */
  final def foreach[E](f: (K, V) => STM[E, Unit]): STM[E, Unit] =
    foldM(())((_, kv) => f(kv._1, kv._2))

  /**
   * Retrieves value associated with given key.
   */
  final def get(k: K): STM[Nothing, Option[V]] =
    tBuckets.get.flatMap { buckets =>
      indexOf(k).flatMap { idx =>
        buckets(idx).map(_.find(_._1 == k).map(_._2))
      }
    }

  /**
   * Retrieves value associated with given key or default value, in case the
   * key isn't present.
   */
  final def getOrElse(k: K, default: => V): STM[Nothing, V] =
    get(k).map(_.getOrElse(default))

  /**
   * Collects all keys stored in map.
   */
  final def keys: STM[Nothing, List[K]] =
    toList.map(_.map(_._1))

  /**
   * Stores new binding into the map.
   */
  final def put(k: K, v: V): STM[Nothing, Unit] = {
    def upsert(bucket: List[(K, V)]): STM[Nothing, List[(K, V)]] = {
      val exists = bucket.exists(_._1 == k)

      if (exists)
        STM.succeed(bucket.map(kv => if (kv._1 == k) (k, v) else kv))
      else
        tSize.update(_ + 1).as((k, v) :: bucket)
    }

    def resize(newCapacity: Int): STM[Nothing, Unit] =
      toList.flatMap { data =>
        TMap.allocate(newCapacity, data).flatMap { tmap =>
          tmap.tBuckets.get.flatMap { newBuckets =>
            tBuckets.set(newBuckets) *> tCapacity.set(newCapacity)
          }
        }
      }

    tBuckets.get.flatMap { buckets =>
      indexOf(k).flatMap { idx =>
        buckets.updateM(idx, upsert).zipRight {
          tSize.get.flatMap { size =>
            tCapacity.get.flatMap { capacity =>
              val needsResize = capacity * TMap.LoadFactor < size
              if (needsResize) resize(capacity * 2) else STM.unit
            }
          }
        }
      }
    }
  }

  /**
   * Removes bindings matching predicate.
   */
  final def removeIf(p: (K, V) => Boolean): STM[Nothing, Unit] =
    tBuckets.get.flatMap(_.transform(_.filterNot(kv => p(kv._1, kv._2))))

  /**
   * Retains bindings matching predicate.
   */
  final def retainIf(p: (K, V) => Boolean): STM[Nothing, Unit] =
    tBuckets.get.flatMap(_.transform(_.filter(kv => p(kv._1, kv._2))))

  /**
   * Collects all bindings into a list.
   */
  final def toList: STM[Nothing, List[(K, V)]] =
    fold(List.empty[(K, V)])((acc, kv) => kv :: acc)

  /**
   * Atomically updates all bindings using pure function.
   */
  final def transform(f: (K, V) => (K, V)): STM[Nothing, Unit] =
    foldMap(f).flatMap(overwriteWith)

  /**
   * Atomically updates all bindings using effectful function.
   */
  final def transformM[E](f: (K, V) => STM[E, (K, V)]): STM[E, Unit] =
    foldMapM(f).flatMap(overwriteWith)

  /**
   * Atomically updates all values using pure function.
   */
  final def transformValues(f: V => V): STM[Nothing, Unit] =
    tBuckets.get.flatMap(_.transform(_.map(kv => kv._1 -> f(kv._2))))

  /**
   * Atomically updates all values using effectful function.
   */
  final def transformValuesM[E](f: V => STM[E, V]): STM[E, Unit] =
    tBuckets.get.flatMap { buckets =>
      buckets.transformM(bucket => STM.collectAll(bucket.map(kv => f(kv._2).map(kv._1 -> _))))
    }

  /**
   * Collects all values stored in map.
   */
  final def values: STM[Nothing, List[V]] =
    toList.map(_.map(_._2))

  private def foldMap(f: (K, V) => (K, V)): STM[Nothing, List[(K, V)]] =
    fold(List.empty[(K, V)])((acc, kv) => f(kv._1, kv._2) :: acc)

  private def foldMapM[E](f: (K, V) => STM[E, (K, V)]): STM[E, List[(K, V)]] =
    foldM(List.empty[(K, V)])((acc, kv) => f(kv._1, kv._2).map(_ :: acc))

  private def indexOf(k: K): STM[Nothing, Int] =
    tCapacity.get.map(c => k.hashCode() % c)

  private def overwriteWith(data: List[(K, V)]): STM[Nothing, Unit] =
    tBuckets.get.flatMap { buckets =>
      tCapacity.get.flatMap { capacity =>
        buckets.transform(_ => Nil).zipRight {
          val updates  = data.map(kv => buckets.update(kv._1.hashCode() % capacity, kv :: _))
          STM.collectAll(updates)
        }
      }
    }.unit
}

object TMap {

  /**
   * Makes a new `TMap` that is initialized with specified values.
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

    STM.collectAll(buckets.map(b => TRef.make(b))).flatMap { tChains =>
      TRef.make(TArray(tChains.toArray)).flatMap { tBuckets =>
        TRef.make(capacity).flatMap { tCapacity =>
          TRef.make(uniqueItems.size).map { tSize =>
            new TMap(tBuckets, tCapacity, tSize)
          }
        }
      }
    }
  }

  private final val DefaultCapacity = 100
  private final val LoadFactor      = 0.75
}
