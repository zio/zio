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
 * Wraps array of [[TRef]] and adds methods for convenience.
 */
final class TArray[A] private (private val array: Array[TRef[A]]) extends AnyVal {

  /**
   * Extracts value from ref in array.
   */
  final def apply(index: Int): STM[Nothing, A] =
    if (0 <= index && index < array.length) array(index).get
    else STM.die(new ArrayIndexOutOfBoundsException(index))

  /**
   * Finds the result of applying a partial function to the first value in its domain.
   */
  final def collectFirst[B](pf: PartialFunction[A, B]): STM[Nothing, Option[B]] =
    find(pf.isDefinedAt).map(_.map(pf))

  /**
   * Finds the result of applying an transactional partial function to the
   * first value in its domain.
   */
  final def collectFirstM[E, B](pf: PartialFunction[A, STM[E, B]]): STM[E, Option[B]] =
    find(pf.isDefinedAt).flatMap {
      case Some(a) => pf(a).map(Some(_))
      case _       => STM.succeed(None)
    }

  /**
   * Determine if the array contains a specified value.
   */
  final def contains(a: A): STM[Nothing, Boolean] = exists(_ == a)

  /**
   * Count the values in the array matching a predicate.
   */
  final def count(p: A => Boolean): STM[Nothing, Int] =
    fold(0)((n, a) => if (p(a)) n + 1 else n)

  /**
   * Count the values in the array matching a transactional predicate.
   */
  final def countM[E](p: A => STM[E, Boolean]): STM[E, Int] =
    foldM(0)((n, a) => p(a).map(result => if (result) n + 1 else n))

  /**
   * Determine if the array contains a value satisfying a predicate.
   */
  final def exists(p: A => Boolean): STM[Nothing, Boolean] = find(p).map(_.isDefined)

  /**
   * Determine if the array contains a value satisfying a transactional predicate.
   */
  final def existsM[E](p: A => STM[E, Boolean]): STM[E, Boolean] =
    countM(p).map(_ > 0)

  /**
   * Find the first element in the array matching a predicate.
   */
  final def find(p: A => Boolean): STM[Nothing, Option[A]] =
    if (array.isEmpty) STM.succeed(None)
    else
      array.head.get.flatMap { a =>
        if (p(a)) STM.succeed(Some(a))
        else new TArray(array.tail).find(p)
      }

  /**
   * Find the last element in the array matching a predicate.
   */
  final def findLast(p: A => Boolean): STM[Nothing, Option[A]] =
    new TArray(array.reverse).find(p)

  /**
   * Find the last element in the array matching a transactional predicate.
   */
  final def findLastM[E](p: A => STM[E, Boolean]): STM[E, Option[A]] =
    new TArray(array.reverse).findM(p)

  /**
   * Find the first element in the array matching a transactional predicate.
   */
  final def findM[E](p: A => STM[E, Boolean]): STM[E, Option[A]] =
    if (array.isEmpty) STM.succeed(None)
    else
      array.head.get.flatMap { a =>
        p(a).flatMap { result =>
          if (result) STM.succeed(Some(a))
          else new TArray(array.tail).findM(p)
        }
      }

  /**
   * The first entry of the array, if it exists.
   */
  final def firstOption: STM[Nothing, Option[A]] =
    if (array.isEmpty) STM.succeed(None) else array.head.get.map(Some(_))

  /**
   * Atomically folds using a pure function.
   */
  final def fold[Z](acc: Z)(op: (Z, A) => Z): STM[Nothing, Z] =
    if (array.isEmpty) STM.succeed(acc)
    else array.head.get.flatMap(a => new TArray(array.tail).fold(op(acc, a))(op))

  /**
   * Atomically folds using a transactional function.
   */
  final def foldM[E, Z](acc: Z)(op: (Z, A) => STM[E, Z]): STM[E, Z] =
    if (array.isEmpty) STM.succeed(acc)
    else
      array.head.get.flatMap { a =>
        op(acc, a).flatMap(acc2 => new TArray(array.tail).foldM(acc2)(op))
      }

  /**
   * Atomically evaluate the conjunction of a predicate across the members
   * of the array.
   */
  final def forall(p: A => Boolean): STM[Nothing, Boolean] = exists(a => !p(a)).map(!_)

  /**
   * Atomically evaluate the conjunction of a transactional predicate across
   * the members of the array.
   */
  final def forallM[E](p: A => STM[E, Boolean]): STM[E, Boolean] =
    countM(p).map(_ == array.length)

  /**
   * Atomically performs transactional effect for each item in array.
   */
  final def foreach[E](f: A => STM[E, Unit]): STM[E, Unit] =
    foldM(())((_, a) => f(a))

  /**
   * Get the first index of a specific value in the array or -1 if it does
   * not occur.
   */
  final def indexOf(a: A): STM[Nothing, Int] = indexOf(a, 0)

  /**
   * Get the first index of a specific value in the array, starting at a specific
   * index, or -1 if it does not occur.
   */
  final def indexOf(a: A, from: Int): STM[Nothing, Int] = indexWhere(_ == a, from)

  /**
   * Get the index of the first entry in the array matching a predicate.
   */
  final def indexWhere(p: A => Boolean): STM[Nothing, Int] = indexWhere(p, 0)

  /**
   * Get the index of the first entry in the array, starting at a specific index,
   * matching a predicate.
   */
  final def indexWhere(p: A => Boolean, from: Int): STM[Nothing, Int] = {
    val len = array.length
    def forIndex(i: Int): STM[Nothing, Int] =
      if (i >= len) STM.succeed(-1)
      else apply(i).flatMap(a => if (p(a)) STM.succeed(i) else forIndex(i + 1))

    if (from >= 0) forIndex(from) else STM.succeed(-1)
  }

  /**
   * Get the index of the first entry in the array matching a transactional
   * predicate.
   */
  final def indexWhereM[E](p: A => STM[E, Boolean]): STM[E, Int] = indexWhereM(p, 0)

  /**
   * Starting at specified index, get the index of the next entry that matches
   * a transactional predicate.
   */
  final def indexWhereM[E](p: A => STM[E, Boolean], from: Int): STM[E, Int] = {
    val len = array.length
    def forIndex(i: Int): STM[E, Int] =
      if (i >= len) STM.succeed(-1)
      else apply(i).flatMap(a => p(a).flatMap(result => if (result) STM.succeed(i) else forIndex(i + 1)))

    if (from >= 0) forIndex(from) else STM.succeed(-1)
  }

  /**
   * Get the last index of a specific value in the array or -1 if it does not occur.
   */
  final def lastIndexOf(a: A): STM[Nothing, Int] =
    if (array.isEmpty) STM.succeed(-1) else lastIndexOf(a, array.length - 1)

  /**
   * Get the first index of a specific value in the array, bounded above by a
   * specific index, or -1 if it does not occur.
   */
  final def lastIndexOf(a: A, end: Int): STM[Nothing, Int] = {
    def forIndex(i: Int): STM[Nothing, Int] =
      if (i < 0) STM.succeed(-1)
      else apply(i).flatMap(ai => if (ai == a) STM.succeed(i) else forIndex(i - 1))

    if (end < array.length) forIndex(end) else STM.succeed(-1)
  }

  /**
   * The last entry in the array, if it exists.
   */
  final def lastOption: STM[Nothing, Option[A]] =
    if (array.isEmpty) STM.succeed(None) else array.last.get.map(Some(_))

  /**
   * Atomically compute the greatest element in the array, if it exists.
   */
  final def maxOption(implicit ord: Ordering[A]): STM[Nothing, Option[A]] =
    reduceOption((acc, a) => if (ord.gt(a, acc)) a else acc)

  /**
   * Atomically compute the least element in the array, if it exists.
   */
  final def minOption(implicit ord: Ordering[A]): STM[Nothing, Option[A]] =
    reduceOption((acc, a) => if (ord.lt(a, acc)) a else acc)

  /**
   * Atomically reduce the array, if non-empty, by a binary operator.
   */
  final def reduceOption(op: (A, A) => A): STM[Nothing, Option[A]] =
    if (array.isEmpty) STM.succeed(None)
    else
      array.head.get
        .flatMap(h => new TArray(array.tail).fold(h)((acc, a) => op(acc, a)))
        .map(Some(_))

  /**
   * Atomically reduce the non-empty array using a transactional binary operator.
   */
  final def reduceOptionM[E](op: (A, A) => STM[E, A]): STM[E, Option[A]] =
    foldM[E, Option[A]](None) { (optAcc, a) =>
      optAcc match {
        case Some(acc) => op(acc, a).map(Some(_))
        case _         => STM.succeed(Some(a))
      }
    }

  /**
   * Atomically updates all elements using a pure function.
   */
  final def transform(f: A => A): STM[Nothing, Unit] =
    array.indices.foldLeft(STM.succeed(())) {
      case (tx, idx) => array(idx).update(f) *> tx
    }

  /**
   * Atomically updates all elements using a transactional effect.
   */
  final def transformM[E](f: A => STM[E, A]): STM[E, Unit] =
    array.indices.foldLeft[STM[E, Unit]](STM.succeed(())) {
      case (tx, idx) =>
        val ref = array(idx)
        ref.get.flatMap(f).flatMap(a => ref.set(a)).flatMap(_ => tx)
    }

  /**
   * Updates element in the array with given function.
   */
  final def update(index: Int, fn: A => A): STM[Nothing, A] =
    if (0 <= index && index < array.length) array(index).update(fn)
    else STM.die(new ArrayIndexOutOfBoundsException(index))

  /**
   * Atomically updates element in the array with given transactional effect.
   */
  final def updateM[E](index: Int, fn: A => STM[E, A]): STM[E, A] =
    if (0 <= index && index < array.length)
      for {
        currentVal <- array(index).get
        newVal     <- fn(currentVal)
        _          <- array(index).set(newVal)
      } yield newVal
    else STM.die(new ArrayIndexOutOfBoundsException(index))
}

object TArray {

  /**
   * Makes a new `TArray` that is initialized with specified values.
   */
  final def make[A](data: A*): STM[Nothing, TArray[A]] = fromIterable(data)

  /**
   * Makes an empty `TArray`.
   */
  final def empty[A]: STM[Nothing, TArray[A]] = fromIterable(Nil)

  /**
   * Makes a new `TArray` initialized with provided iterable.
   */
  final def fromIterable[A](data: Iterable[A]): STM[Nothing, TArray[A]] =
    STM.foreach(data)(TRef.make(_)).map(list => new TArray(list.toArray))
}
