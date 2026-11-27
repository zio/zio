/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

import zio.{Chunk, Unsafe}
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * A `TArray` is a fixed-size array whose elements are read and changed inside
 * STM transactions. Each element lives in its own [[TRef]], so transactions
 * that read and change only different indexes do not conflict.
 *
 * @see
 *   [[https://zio.dev/reference/stm/tarray/ TArray]]
 */
final class TArray[A] private[stm] (private[stm] val array: Array[TRef[A]]) extends AnyVal {

  /**
   * Returns the element at `index`. When `index` is outside `0 until size`, the
   * transaction dies with a `java.lang.ArrayIndexOutOfBoundsException`, a
   * defect that `orElse` does not recover from.
   */
  def apply(index: Int): USTM[A] =
    if (0 <= index && index < array.length)
      array(index).get
    else
      STM.die(new ArrayIndexOutOfBoundsException(index))

  /**
   * Finds the result of applying a partial function to the first value in its
   * domain.
   */
  def collectFirst[B](pf: PartialFunction[A, B]): USTM[Option[B]] =
    find(pf.isDefinedAt).map(_.map(pf))

  /**
   * Finds the result of applying a transactional partial function to the first
   * value in its domain.
   */
  def collectFirstSTM[E, B](pf: PartialFunction[A, STM[E, B]]): STM[E, Option[B]] =
    find(pf.isDefinedAt).flatMap {
      case Some(a) => pf(a).map(Some(_))
      case _       => STM.none
    }

  /**
   * Returns whether the array contains an element equal to `a`.
   */
  def contains(a: A): USTM[Boolean] = exists(_ == a)

  /**
   * Counts the elements that satisfy `p`.
   */
  def count(p: A => Boolean): USTM[Int] =
    fold(0)((n, a) => if (p(a)) n + 1 else n)

  /**
   * Counts the elements that satisfy the transactional predicate `p`.
   */
  def countSTM[E](p: A => STM[E, Boolean]): STM[E, Int] =
    foldSTM(0)((n, a) => p(a).map(result => if (result) n + 1 else n))

  /**
   * Returns whether an element satisfies `p`.
   */
  def exists(p: A => Boolean): USTM[Boolean] = find(p).map(_.isDefined)

  /**
   * Returns whether an element satisfies the transactional predicate `p`. Runs
   * `p` on every element, also after a match, so a failure of `p` on a later
   * element fails the transaction.
   */
  def existsSTM[E](p: A => STM[E, Boolean]): STM[E, Boolean] =
    countSTM(p).map(_ > 0)

  /**
   * Returns the first element that satisfies `p`, or `None` when there is none.
   */
  def find(p: A => Boolean): USTM[Option[A]] =
    ZSTM.Effect { (journal, _, _) =>
      var i   = 0
      var res = Option.empty[A]

      while (res.isEmpty && i < array.length) {
        val a = array(i).unsafeGet(journal)

        if (p(a))
          res = Some(a)

        i += 1
      }

      res
    }

  /**
   * Returns the last element that satisfies `p`, or `None` when there is none.
   */
  def findLast(p: A => Boolean): USTM[Option[A]] =
    ZSTM.Effect { (journal, _, _) =>
      var i   = array.length - 1
      var res = Option.empty[A]

      while (res.isEmpty && i >= 0) {
        val a = array(i).unsafeGet(journal)

        if (p(a))
          res = Some(a)

        i -= 1
      }

      res
    }

  /**
   * Returns the last element that satisfies the transactional predicate `p`, or
   * `None` when there is none. Runs `p` from the last element backwards and
   * stops at the first match.
   */
  def findLastSTM[E](p: A => STM[E, Boolean]): STM[E, Option[A]] = {
    val init = (Option.empty[A], array.length - 1)
    val cont = (s: (Option[A], Int)) => s._1.isEmpty && s._2 >= 0

    ZSTM
      .iterate(init)(cont) { s =>
        val idx = s._2
        array(idx).get.flatMap(a => p(a).map(ok => (if (ok) Some(a) else None, idx - 1)))
      }
      .map(_._1)
  }

  /**
   * Returns the first element that satisfies the transactional predicate `p`,
   * or `None` when there is none. Stops at the first match.
   */
  def findSTM[E](p: A => STM[E, Boolean]): STM[E, Option[A]] = {
    val init = (Option.empty[A], 0)
    val cont = (s: (Option[A], Int)) => s._1.isEmpty && s._2 < array.length

    ZSTM
      .iterate(init)(cont) { s =>
        val idx = s._2
        array(idx).get.flatMap(a => p(a).map(ok => (if (ok) Some(a) else None, idx + 1)))
      }
      .map(_._1)
  }

  /**
   * Returns the first element, or `None` when the array is empty.
   */
  def firstOption: USTM[Option[A]] =
    if (array.isEmpty) STM.succeedNow(None) else array.head.get.map(Some(_))

  /**
   * Folds the elements from first to last into one value with `op`, starting
   * from `zero`.
   */
  def fold[Z](zero: Z)(op: (Z, A) => Z): USTM[Z] =
    ZSTM.Effect { (journal, _, _) =>
      var res = zero
      var i   = 0

      while (i < array.length) {
        val value = array(i).unsafeGet(journal)
        res = op(res, value)
        i += 1
      }

      res
    }

  /**
   * Folds the elements from first to last into one value with the transactional
   * function `op`, starting from `zero`. Stops at the first failure of `op`.
   */
  def foldSTM[E, Z](zero: Z)(op: (Z, A) => STM[E, Z]): STM[E, Z] =
    toChunk.flatMap(STM.foldLeft(_)(zero)(op))

  /**
   * Returns whether every element satisfies `p`; `true` for an empty array.
   */
  def forall(p: A => Boolean): USTM[Boolean] = exists(a => !p(a)).map(!_)

  /**
   * Returns whether every element satisfies the transactional predicate `p`;
   * `true` for an empty array. Runs `p` on every element, also after one that
   * does not satisfy it.
   */
  def forallSTM[E](p: A => STM[E, Boolean]): STM[E, Boolean] =
    countSTM(p).map(_ == array.length)

  /**
   * Runs the transaction `f` for each element, from first to last. Stops at the
   * first failure of `f`.
   */
  def foreach[E](f: A => STM[E, Unit]): STM[E, Unit] =
    foldSTM(())((_, a) => f(a))

  /**
   * Returns the first index of an element equal to `a`, or -1 when there is
   * none.
   */
  def indexOf(a: A): USTM[Int] = indexOf(a, 0)

  /**
   * Returns the first index at or after `from` of an element equal to `a`, or
   * -1 when there is none. Returns -1 when `from` is negative.
   */
  def indexOf(a: A, from: Int): USTM[Int] = indexWhere(_ == a, from)

  /**
   * Returns the index of the first element that satisfies `p`, or -1 when there
   * is none.
   */
  def indexWhere(p: A => Boolean): USTM[Int] = indexWhere(p, 0)

  /**
   * Returns the index of the first element at or after `from` that satisfies
   * `p`, or -1 when there is none. Returns -1 when `from` is negative.
   */
  def indexWhere(p: A => Boolean, from: Int): USTM[Int] =
    if (from < 0)
      STM.succeedNow(-1)
    else
      ZSTM.Effect { (journal, _, _) =>
        var i     = from
        var found = false

        while (!found && i < array.length) {
          val a = array(i).unsafeGet(journal)
          found = p(a)
          i += 1
        }

        if (found) i - 1 else -1
      }

  /**
   * Returns the index of the first element that satisfies the transactional
   * predicate `p`, or -1 when there is none.
   */
  def indexWhereSTM[E](p: A => STM[E, Boolean]): STM[E, Int] =
    indexWhereSTM(p, 0)

  /**
   * Returns the index of the first element at or after `from` that satisfies
   * the transactional predicate `p`, or -1 when there is none. Returns -1 when
   * `from` is negative.
   */
  def indexWhereSTM[E](p: A => STM[E, Boolean], from: Int): STM[E, Int] = {
    def forIndex(i: Int): STM[E, Int] =
      if (i < array.length)
        array(i).get.flatMap(p).flatMap(ok => if (ok) STM.succeedNow(i) else forIndex(i + 1))
      else
        STM.succeedNow(-1)

    if (from >= 0) forIndex(from) else STM.succeedNow(-1)
  }

  /**
   * Returns the last index of an element equal to `a`, or -1 when there is
   * none.
   */
  def lastIndexOf(a: A): USTM[Int] =
    if (array.isEmpty) STM.succeedNow(-1) else lastIndexOf(a, array.length - 1)

  /**
   * Returns the last index at or before `end` of an element equal to `a`, or -1
   * when there is none. Returns -1 when `end` is negative, and also when `end`
   * is `size` or more.
   */
  def lastIndexOf(a: A, end: Int): USTM[Int] =
    if (end >= array.length)
      STM.succeedNow(-1)
    else
      ZSTM.Effect { (journal, _, _) =>
        var i     = end
        var found = false

        while (!found && i >= 0) {
          found = array(i).unsafeGet(journal) == a
          i -= 1
        }

        if (found) i + 1 else -1
      }

  /**
   * Returns the last element, or `None` when the array is empty.
   */
  def lastOption: USTM[Option[A]] =
    if (array.isEmpty) STM.succeedNow(None) else array.last.get.map(Some(_))

  /**
   * Returns the greatest element by `ord`, or `None` when the array is empty.
   */
  def maxOption(implicit ord: Ordering[A]): USTM[Option[A]] =
    reduceOption((acc, a) => if (ord.gt(a, acc)) a else acc)

  /**
   * Returns the least element by `ord`, or `None` when the array is empty.
   */
  def minOption(implicit ord: Ordering[A]): USTM[Option[A]] =
    reduceOption((acc, a) => if (ord.lt(a, acc)) a else acc)

  /**
   * Combines the elements from first to last with `op`, or returns `None` when
   * the array is empty. While the running result is `null` (the first element
   * is `null`, or `op` returned `null`), the next element replaces it instead
   * of being passed to `op`, and a final `null` result gives `None`. For
   * example, the elements `null, "b", null, "c"` with `_ + _` give
   * `Some("bnullc")`.
   */
  def reduceOption(op: (A, A) => A): USTM[Option[A]] =
    ZSTM.Effect { (journal, _, _) =>
      var i   = 0
      var res = null.asInstanceOf[A]

      while (i < array.length) {
        val a = array(i).unsafeGet(journal)

        res = res match {
          case null => a
          case v    => op(v, a)
        }

        i += 1
      }

      Option(res)
    }

  /**
   * Combines the elements from first to last with the transactional function
   * `op`, or returns `None` when the array is empty.
   */
  def reduceOptionSTM[E](op: (A, A) => STM[E, A]): STM[E, Option[A]] =
    foldSTM(Option.empty[A]) { (acc, a) =>
      acc match {
        case Some(acc) => op(acc, a).map(Some(_))
        case _         => STM.some(a)
      }
    }

  /**
   * Returns the size of the array.
   */
  def size: Int =
    array.size

  /**
   * Collects all elements into a list.
   */
  def toList: USTM[List[A]] =
    STM.foreach(array.toList)(_.get)

  /**
   * Collects all elements into a chunk.
   */
  def toChunk: USTM[Chunk[A]] =
    STM.foreach(Chunk.fromArray(array))(_.get)

  /**
   * Replaces every element with `f` applied to it.
   */
  def transform(f: A => A): USTM[Unit] =
    ZSTM.Effect { (journal, _, _) =>
      var i = 0

      while (i < array.length) {
        val current = array(i).unsafeGet(journal)
        array(i).unsafeSet(journal, f(current))
        i += 1
      }

      ()
    }

  /**
   * Replaces every element with the result of the transactional function `f`.
   * When `f` fails, the transaction fails with that error.
   */
  def transformSTM[E](f: A => STM[E, A]): STM[E, Unit] =
    STM.foreach(Chunk.fromArray(array))(_.get.flatMap(f)).flatMap { newData =>
      ZSTM.Effect { (journal, _, _) =>
        var i  = 0
        val it = newData.iterator

        while (it.hasNext) {
          array(i).unsafeSet(journal, it.next())
          i += 1
        }

        ()
      }
    }

  /**
   * Replaces the element at `index` with `fn` applied to it. When `index` is
   * outside `0 until size`, the transaction dies with a
   * `java.lang.ArrayIndexOutOfBoundsException`.
   */
  def update(index: Int, fn: A => A): USTM[Unit] =
    if (0 <= index && index < array.length)
      array(index).update(fn)
    else
      STM.die(new ArrayIndexOutOfBoundsException(index))

  /**
   * Replaces the element at `index` with the result of the transactional
   * function `fn`. When `index` is outside `0 until size`, the transaction dies
   * with a `java.lang.ArrayIndexOutOfBoundsException`.
   */
  def updateSTM[E](index: Int, fn: A => STM[E, A]): STM[E, Unit] =
    if (0 <= index && index < array.length)
      for {
        currentVal <- array(index).get
        newVal     <- fn(currentVal)
        _          <- array(index).set(newVal)
      } yield ()
    else STM.die(new ArrayIndexOutOfBoundsException(index))
}

/**
 * Constructors for [[TArray]].
 */
object TArray {

  /**
   * Makes a new `TArray` that is initialized with specified values.
   */
  def make[A](data: A*): USTM[TArray[A]] =
    STM.succeed(unsafe.make(data: _*)(Unsafe.unsafe))

  /**
   * Constructors that make a `TArray` outside a transaction. They need an
   * `Unsafe` capability.
   */
  object unsafe {

    /**
     * Makes a new `TArray` with the elements of `data` without running a
     * transaction.
     */
    def make[A](data: A*)(implicit unsafe: Unsafe): TArray[A] =
      new TArray(data.map(TRef.unsafeMake(_)).toArray)
  }

  /**
   * Makes an empty `TArray`.
   */
  def empty[A]: USTM[TArray[A]] =
    fromIterable(Nil)

  /**
   * Makes a new `TArray` initialized with provided iterable.
   */
  def fromIterable[A](data: => Iterable[A]): USTM[TArray[A]] =
    make(data.toSeq: _*)
}
