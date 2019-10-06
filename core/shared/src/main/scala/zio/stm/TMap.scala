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

class TMap[K, V] private (table: TArray[List[(K, V)]]) {
  final def collect[K2, V2](pf: PartialFunction[(K, V), (K2, V2)]): STM[Nothing, TMap[K2, V2]] = ???

  final def contains[E](k: K): STM[E, Boolean] = ???

  final def delete[E](k: K): STM[E, TMap[K, V]] = ???

  final def filter(p: ((K, V)) => Boolean): STM[Nothing, TMap[K, V]] = ???

  final def filterNot(p: ((K, V)) => Boolean): STM[Nothing, TMap[K, V]] = ???

  final def fold[A](acc: A)(op: (A, (K, V)) => A): STM[Nothing, A] = ???

  final def foldM[A, E](acc: A)(op: (A, (K, V)) => STM[E, A]): STM[E, A] = ???

  final def foreach[E](f: ((K, V)) => STM[E, Unit]): STM[E, Unit] = ???

  final def get[E](k: K): STM[E, Option[V]] = ???

  final def getOrElse[E](k: K, default: => V): STM[E, V] = ???

  final def insert[E](k: K, v: V): STM[E, TMap[K, V]] = ???

  final def map[K2, V2](f: ((K, V)) => (K2, V2)): STM[Nothing, TMap[K2, V2]] = ???

  final def mapM[E, K2, V2](f: ((K, V)) => STM[E, (K2, V2)]): STM[E, TMap[K2, V2]] = ???
}

object TMap {
  private final val DefaultSize = 1000

  /**
   * Makes an empty `TMap`.
   */
  final def empty[K, V]: STM[Nothing, TMap[K, V]] = apply(List.empty[(K, V)])

  /**
   * Makes a new `TMap` that is initialized with the specified values.
   */
  final def apply[K, V](items: => List[(K, V)]): STM[Nothing, TMap[K, V]] = {
    val buckets     = Array.fill[List[(K, V)]](DefaultSize)(Nil)
    val uniqueItems = items.toMap.toList

    uniqueItems.foreach { kv =>
      val idx = indexOf(kv._1)
      buckets(idx) = kv :: buckets(idx)
    }

    val stmBuckets = buckets.map(b => TRef.make(b))

    STM.collectAll(stmBuckets).map(refs => new TMap(TArray(refs.toArray)))
  }

  private final def indexOf[K](k: K): Int = k.hashCode() % DefaultSize
}
