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

  /** Extracts value from ref in array. */
  final def apply(index: Int): STM[Nothing, A] =
    if (0 <= index && index < array.size) array(index).get
    else STM.die(new ArrayIndexOutOfBoundsException(index))

  final def collect[B](pf: PartialFunction[A, B]): STM[Nothing, TArray[B]] =
    this
      .foldM(List.empty[TRef[B]]) {
        case (acc, a) =>
          if (pf.isDefinedAt(a)) TRef.make(pf(a)).map(tref => tref :: acc)
          else STM.succeed(acc)
      }
      .map(l => TArray(l.reverse.toArray))

  /* Atomically folds [[TArray]] with pure function. */
  final def fold[Z](acc: Z)(op: (Z, A) => Z): STM[Nothing, Z] =
    if (array.isEmpty) STM.succeed(acc)
    else array.head.get.flatMap(a => new TArray(array.tail).fold(op(acc, a))(op))

  /** Atomically folds [[TArray]] with STM function. */
  final def foldM[E, Z](acc: Z)(op: (Z, A) => STM[E, Z]): STM[E, Z] =
    if (array.isEmpty) STM.succeed(acc)
    else
      for {
        a    <- array.head.get
        acc2 <- op(acc, a)
        res  <- new TArray(array.tail).foldM(acc2)(op)
      } yield res

  /** Atomically performs side-effect for each item in array */
  final def foreach[E](f: A => STM[E, Unit]): STM[E, Unit] =
    this.foldM(())((_, a) => f(a))

  /** Creates [[TArray]] of new [[TRef]]s, mapped with pure function. */
  final def map[B](f: A => B): STM[Nothing, TArray[B]] =
    this.mapM(f andThen STM.succeed)

  /** Creates [[TArray]] of new [[TRef]]s, mapped with transactional effect. */
  final def mapM[E, B](f: A => STM[E, B]): STM[E, TArray[B]] =
    STM.foreach(array)(_.get.flatMap(f).flatMap(b => TRef.make(b))).map(l => new TArray(l.toArray))

  /** Atomically updates all [[TRef]]s inside this array using pure function. */
  final def transform(f: A => A): STM[Nothing, Unit] =
    (0 to array.size - 1).foldLeft(STM.succeed(())) {
      case (tx, idx) => array(idx).update(f) *> tx
    }

  /** Atomically updates all elements using transactional effect. */
  final def transformM[E](f: A => STM[E, A]): STM[E, Unit] =
    (0 to array.size - 1).foldLeft[STM[E, Unit]](STM.succeed(())) {
      case (tx, idx) =>
        val ref = array(idx)
        ref.get.flatMap(f).flatMap(a => ref.set(a)).flatMap(_ => tx)
    }

  /** Updates element in the array with given function. */
  final def update(index: Int, fn: A => A): STM[Nothing, A] =
    if (0 <= index && index < array.size) array(index).update(fn)
    else STM.die(new ArrayIndexOutOfBoundsException(index))

  /** Atomically updates element in the array with given transactional effect. */
  final def updateM[E](index: Int, fn: A => STM[E, A]): STM[E, A] =
    if (0 <= index && index < array.size)
      for {
        currentVal <- array(index).get
        newVal     <- fn(currentVal)
        _          <- array(index).set(newVal)
      } yield newVal
    else STM.die(new ArrayIndexOutOfBoundsException(index))
}

object TArray {

  final def apply[A](array: Array[TRef[A]]): TArray[A] = new TArray(array)

}
