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

/** Wraps array of [[TRef]] and adds methods for convenience.
 * Caution: most of methods are not stack-safe.
 * */
class TArray[A] private (val array: Array[TRef[A]]) extends AnyVal {

  /** Extracts value from ref in array. Returns None when index is out of bounds. */
  def apply(index: Int): STM[Nothing, Option[A]] =
    if (0 <= index && index < array.size) array(index).get.map(Some(_))
    else STM.succeed(None)

  def collect[B](pf: PartialFunction[A, B]): STM[Nothing, TArray[B]] =
    this
      .foldM(List.empty[TRef[B]]) {
        case (acc, a) =>
          if (pf.isDefinedAt(a)) TRef.make(pf(a)).map(tref => tref :: acc)
          else STM.succeed(acc)
      }
      .map(l => TArray(l.reverse.toArray))

  /* Atomically folds [[TArray]] with pure function. */
  def fold[Z](acc: Z)(op: (Z, A) => Z): STM[Nothing, Z] =
    if (array.isEmpty) STM.succeed(acc)
    else array.head.get.flatMap(a => new TArray(array.tail).fold(op(acc, a))(op))

  /** Atomically folds [[TArray]] with STM function. */
  def foldM[E, Z](acc: Z)(op: (Z, A) => STM[E, Z]): STM[E, Z] =
    if (array.isEmpty) STM.succeed(acc)
    else
      for {
        a    <- array.head.get
        acc2 <- op(acc, a)
        res  <- new TArray(array.tail).foldM(acc2)(op)
      } yield res

  /** Atomically performs side-effect for each item in array */
  def foreach[E](f: A => STM[E, Unit]): STM[E, Unit] =
    this.foldM(())((_, a) => f(a))

  /** Creates [[TArray]] of new [[TRef]]s, mapped with pure function. */
  def map[B](f: A => B): STM[Nothing, TArray[B]] =
    this.mapM(f andThen STM.succeed)

  /** Creates [[TArray]] of new [[TRef]]s, mapped with transactional effect. */
  def mapM[E, B](f: A => STM[E, B]): STM[E, TArray[B]] =
    STM.foreach(array)(_.get.flatMap(f).flatMap(b => TRef.make(b))).map(l => new TArray(l.toArray))

  /** Atomically updates all [[TRef]]s inside this array using pure function. */
  def transform(f: A => A): STM[Nothing, Unit] =
    (0 to array.size - 1).foldLeft(STM.succeed(())) {
      case (tx, idx) => array(idx).update(f) *> tx
    }

  /** Atomically updates all elements using transactional effect. */
  def transformM[E](f: A => STM[E, A]): STM[E, Unit] =
    (0 to array.size - 1).foldLeft[STM[E, Unit]](STM.succeed(())) {
      case (tx, idx) =>
        val ref = array(idx)
        ref.get.flatMap(f).flatMap(a => ref.set(a)).flatMap(_ => tx)
    }

  /** Updates element in the array with given function. None signals index out of bounds. */
  def update(index: Int, fn: A => A): STM[Nothing, Option[A]] =
    if (0 <= index && index < array.size) array(index).update(fn).map(Some(_))
    else STM.succeed(None)

  /** Atomically updates element in the array with given transactionall effect. None signals index out of bounds. */
  def updateM[E](index: Int, fn: A => STM[E, A]): STM[E, Option[A]] =
    if (0 <= index && index < array.size) array(index).get.flatMap(fn).map(Some(_))
    else STM.succeed(None)
}

object TArray {

  def apply[A](array: Array[TRef[A]]): TArray[A] = new TArray(array)

}
