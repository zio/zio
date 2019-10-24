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

class TSet[A] private (private val tmap: TMap[A, Unit]) extends AnyVal {
  final def contains(a: A): STM[Nothing, Boolean] = tmap.contains(a)

  final def delete(a: A): STM[Nothing, Unit] = ???

  final def diff(that: TSet[A]): STM[Nothing, Unit] = ???

  final def fold[B](zero: B)(op: (B, A) => B): STM[Nothing, B] = ???

  final def foldM[B, E](zero: B)(op: (B, A) => STM[E, B]): STM[E, B] = ???

  final def foreach[E](f: A => STM[E, Unit]): STM[E, Unit] = ???

  final def intersect(that: TSet[A]): STM[Nothing, Unit] = ???

  final def put(a: A): STM[Nothing, Unit] = tmap.put(a, ())

  final def removeIf(p: A => Boolean): STM[Nothing, Unit] = ???

  final def retainIf(p: A => Boolean): STM[Nothing, Unit] = ???

  final def toList: STM[Nothing, List[A]] = tmap.keys

  final def transform(f: A => A): STM[Nothing, Unit] = ???

  final def transformM[E](f: A => STM[E, A]): STM[E, Unit] = ???

  final def union(that: TSet[A]): STM[Nothing, Unit] = ???
}

object TSet {
  final def apply[A](data: A*): STM[Nothing, TSet[A]] = fromIterable(data)

  final def empty[A]: STM[Nothing, TSet[A]] = fromIterable(Nil)

  final def fromIterable[A](data: Iterable[A]): STM[Nothing, TSet[A]] =
    TMap.fromIterable(data.map((_, ()))).map(new TSet(_))
}
