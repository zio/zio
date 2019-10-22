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

class TMap[K, V] private (buckets: TRef[TArray[List[(K, V)]]]) { self =>
  import TMap.indexOf

  final def contains[E](k: K): STM[E, Boolean] =
    get(k).map(_.isDefined)

  final def delete[E](k: K): STM[E, TMap[K, V]] =
    accessM(_.update(indexOf(k), _.filterNot(_._1 == k))).as(self)

  final def fold[A](zero: A)(op: (A, (K, V)) => A): STM[Nothing, A] =
    accessM(_.fold(zero)((acc, chain) => chain.foldLeft(acc)(op)))

  final def foldM[A, E](zero: A)(op: (A, (K, V)) => STM[E, A]): STM[E, A] = {
    def loopM(acc: STM[E, A], remaining: List[(K, V)]): STM[E, A] =
      remaining match {
        case Nil          => acc
        case head :: tail => loopM(acc.flatMap(op(_, head)), tail)
      }

    accessM(_.foldM(zero)((acc, chain) => loopM(STM.succeed(acc), chain)))
  }

  final def foreach[E](f: ((K, V)) => STM[E, Unit]): STM[E, Unit] =
    self.foldM(())((_, kv) => f(kv))

  final def get[E](k: K): STM[E, Option[V]] =
    accessM(_.apply(indexOf(k))).map(_.find(_._1 == k).map(_._2))

  final def getOrElse[E](k: K, default: => V): STM[E, V] =
    get(k).map(_.getOrElse(default))

  final def map[K2, V2](f: ((K, V)) => (K2, V2)): STM[Nothing, TMap[K2, V2]] =
    self.fold(List.empty[(K2, V2)])((acc, kv) => f(kv) :: acc).flatMap(TMap(_))

  final def mapM[E, K2, V2](f: ((K, V)) => STM[E, (K2, V2)]): STM[E, TMap[K2, V2]] =
    self.foldM(List.empty[(K2, V2)])((acc, kv) => f(kv).map(_ :: acc)).flatMap(TMap(_))

  final def put[E](k: K, v: V): STM[E, Unit] = {
    def update(bucket: List[(K, V)]): List[(K, V)] =
      bucket match {
        case Nil => List(k -> v)
        case xs  => xs.map(kv => if (kv._1 == k) (k, v) else kv)
      }

    accessM(_.update(indexOf(k), update)).unit
  }

  final def removeIf(p: ((K, V)) => Boolean): STM[Nothing, Unit] =
    accessM(_.transform(_.filterNot(p)))

  final def retainIf(p: ((K, V)) => Boolean): STM[Nothing, Unit] =
    accessM(_.transform(_.filter(p)))

  private def accessM[E, A](f: TArray[List[(K, V)]] => STM[E, A]): STM[E, A] =
    buckets.get.flatMap(f)
}

object TMap {

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

    for {
      bref <- STM.collectAll(buckets.map(b => TRef.make(b)))
      aref <- TRef.make(TArray(bref.toArray))
    } yield new TMap(aref)
  }

  /**
   * Makes an empty `TMap`.
   */
  final def empty[K, V]: STM[Nothing, TMap[K, V]] = apply(List.empty[(K, V)])

  private final val DefaultSize = 1000

  private final def indexOf[K](k: K): Int = k.hashCode() % DefaultSize
}
