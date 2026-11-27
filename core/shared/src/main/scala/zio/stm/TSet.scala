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

import zio.{Chunk, NonEmptyChunk, Unsafe}

/**
 * A `TSet` is a mutable set that is read and changed inside STM transactions.
 * It is a [[TMap]] from the elements to `Unit`, so elements are compared with
 * `==` and hashed with `hashCode`, and methods that visit several elements do
 * so in an unspecified order.
 *
 * @see
 *   [[https://zio.dev/reference/stm/tset/ TSet]]
 */
final class TSet[A] private (private val tmap: TMap[A, Unit]) extends AnyVal {

  /**
   * Returns whether the set contains `a`.
   */
  def contains(a: A): USTM[Boolean] =
    tmap.contains(a)

  /**
   * Returns whether the set is empty. After `make` or `fromIterable` with a
   * repeated element, the set keeps a count that includes each repeat, so this
   * can return `false` for a set with no elements; `size` counts the elements
   * themselves.
   */
  def isEmpty: USTM[Boolean] =
    tmap.isEmpty

  /**
   * Removes `a` from the set. Does nothing when the set does not contain it.
   */
  def delete(a: A): USTM[Unit] =
    tmap.delete(a)

  /**
   * Removes the elements of `as` from the set.
   */
  def deleteAll(as: Iterable[A]): USTM[Unit] =
    tmap.deleteAll(as)

  /**
   * Removes from this set the elements that `other` contains.
   */
  def diff(other: TSet[A]): USTM[Unit] =
    other.toSet.flatMap(vals => removeIfDiscard(vals.contains))

  /**
   * Folds the elements into one value with `op`, starting from `zero`.
   */
  def fold[B](zero: B)(op: (B, A) => B): USTM[B] =
    tmap.fold(zero)((acc, kv) => op(acc, kv._1))

  /**
   * Folds the elements into one value with the transactional function `op`,
   * starting from `zero`. Stops at the first failure of `op`.
   */
  def foldSTM[B, E](zero: B)(op: (B, A) => STM[E, B]): STM[E, B] =
    tmap.foldSTM(zero)((acc, kv) => op(acc, kv._1))

  /**
   * Runs the transaction `f` for each element. Stops at the first failure of
   * `f`.
   */
  def foreach[E](f: A => STM[E, Unit]): STM[E, Unit] =
    foldSTM(())((_, a) => f(a))

  /**
   * Removes from this set the elements that `other` does not contain.
   */
  def intersect(other: TSet[A]): USTM[Unit] =
    other.toSet.flatMap(vals => retainIfDiscard(vals.contains))

  /**
   * Adds `a` to the set. Does nothing when the set already contains it.
   */
  def put(a: A): USTM[Unit] =
    tmap.put(a, ())

  /**
   * Removes the elements that satisfy `p` and returns them.
   */
  def removeIf(p: A => Boolean): USTM[Chunk[A]] =
    tmap.removeIf((k, _) => p(k)).map(_.map(_._1))

  /**
   * Removes the elements that satisfy `p`.
   */
  def removeIfDiscard(p: A => Boolean): USTM[Unit] =
    tmap.removeIfDiscard((k, _) => p(k))

  /**
   * Keeps the elements that satisfy `p`, removes the others and returns the
   * removed elements.
   */
  def retainIf(p: A => Boolean): USTM[Chunk[A]] =
    tmap.retainIf((k, _) => p(k)).map(_.map(_._1))

  /**
   * Keeps the elements that satisfy `p` and removes the others.
   */
  def retainIfDiscard(p: A => Boolean): USTM[Unit] =
    tmap.retainIfDiscard((k, _) => p(k))

  /**
   * Returns the number of elements in the set.
   */
  def size: USTM[Int] = toList.map(_.size)

  /**
   * Removes the first element at which `pf` is defined and returns `pf` applied
   * to it. When no element matches, the transaction retries, so the fiber waits
   * until another transaction changes the set.
   */
  def takeFirst[B](pf: PartialFunction[A, B]): USTM[B] =
    tmap.takeFirst {
      case (k, _) if pf.isDefinedAt(k) => pf(k)
    }

  /**
   * Removes the first element for which `pf` succeeds and returns that result.
   * `pf` fails with `None` to skip an element; when it fails with `Some(e)`,
   * the transaction fails with `e`. When `pf` succeeds for no element, the
   * transaction retries, so the fiber waits until another transaction changes
   * the set.
   */
  def takeFirstSTM[R, E, B](pf: A => ZSTM[R, Option[E], B]): ZSTM[R, E, B] =
    tmap.takeFirstSTM { case (k, _) =>
      pf(k)
    }

  /**
   * Removes every element at which `pf` is defined and returns `pf` applied to
   * each of them. When no element matches, the transaction retries, so the
   * fiber waits until another transaction changes the set.
   */
  def takeSome[B](pf: PartialFunction[A, B]): USTM[NonEmptyChunk[B]] =
    tmap.takeSome {
      case (k, _) if pf.isDefinedAt(k) => pf(k)
    }

  /**
   * Removes every element for which `pf` succeeds and returns those results.
   * `pf` fails with `None` to skip an element; when it fails with `Some(e)`,
   * the transaction fails with `e`. When `pf` succeeds for no element, the
   * transaction retries, so the fiber waits until another transaction changes
   * the set.
   */
  def takeSomeSTM[R, E, B](pf: A => ZSTM[R, Option[E], B]): ZSTM[R, E, NonEmptyChunk[B]] =
    tmap.takeSomeSTM { case (k, _) =>
      pf(k)
    }

  /**
   * Collects all elements into a list.
   */
  def toList: USTM[List[A]] = tmap.keys

  /**
   * Collects all elements into a set.
   */
  def toSet: USTM[Set[A]] = toList.map(_.toSet)

  /**
   * Replaces every element with `f` applied to it. Elements that `f` maps to
   * the same value become one element.
   */
  def transform(f: A => A): USTM[Unit] =
    tmap.transform((k, v) => f(k) -> v)

  /**
   * Replaces every element with the result of the transactional function `f`.
   * Elements that `f` maps to the same value become one element. When `f`
   * fails, the transaction fails with that error.
   */
  def transformSTM[E](f: A => STM[E, A]): STM[E, Unit] =
    tmap.transformSTM((k, v) => f(k).map(_ -> v))

  /**
   * Adds the elements of `other` to this set.
   */
  def union(other: TSet[A]): USTM[Unit] =
    other.foreach(put)
}

/**
 * Constructors for [[TSet]].
 */
object TSet {

  /**
   * Makes an empty `TSet`.
   */
  def empty[A]: USTM[TSet[A]] =
    fromIterable(Nil)

  /**
   * Makes a new `TSet` with the elements of `data`. When `data` repeats an
   * element, the count behind `isEmpty` includes each repeat.
   */
  def fromIterable[A](data: => Iterable[A]): USTM[TSet[A]] =
    make(data.toSeq: _*)

  /**
   * Makes a new `TSet` with the elements of `data`. When `data` repeats an
   * element, the count behind `isEmpty` includes each repeat: after
   * `TSet.make(1, 1)` and `delete(1)`, `isEmpty` returns `false` while `size`
   * returns 0.
   */
  def make[A](data: A*): USTM[TSet[A]] =
    ZSTM.succeed(unsafe.make(data: _*)(Unsafe.unsafe))

  /**
   * Constructors that make a `TSet` outside a transaction. They need an
   * `Unsafe` capability.
   */
  object unsafe {

    /**
     * Makes a new `TSet` with the elements of `data` without running a
     * transaction. When `data` repeats an element, the count behind `isEmpty`
     * includes each repeat.
     */
    def make[A](data: A*)(implicit unsafe: Unsafe): TSet[A] =
      new TSet(TMap.unsafe.make(data.map((_, ())): _*))
  }
}
