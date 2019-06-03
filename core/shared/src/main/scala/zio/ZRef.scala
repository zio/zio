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

package zio
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
 * A mutable atomic reference for the `IO` monad. This is the `IO` equivalent of
 * a volatile `var`, augmented with atomic operations, which make it useful as a
 * reasonably efficient (if low-level) concurrency primitive.
 *
 * {{{
 * for {
 *   ref <- Ref.make(2)
 *   v   <- ref.update(_ + 3)
 *   _   <- console.putStrLn("Value = " + v) // Value = 5
 * } yield ()
 * }}}
 */
abstract class ZRef[+EA, +EB, -A, +B] extends Serializable { self =>

  /**
   * Reads the value from the `Ref`.
   */
  def get: IO[EB, B]

  /**
   * Atomically modifies the `Ref` with the specified function, which computes
   * a return value for the modification. This is a more powerful version of
   * `update`.
   */
  def modify[X, A1 <: A](f: A => (X, A1)): IO[EA, X]

  /**
   * Atomically modifies the `Ref` with the specified partial function, which computes
   * a return value for the modification if the function is defined in the current value
   * otherwise it returns a default value.
   * This is a more powerful version of `updateSome`.
   */
  def modifySome[X, A1 <: A](default: => X)(pf: PartialFunction[A, (X, A1)]): IO[EA, X]

  /**
   * Writes a new value to the `Ref`, with a guarantee of immediate
   * consistency (at some cost to performance).
   */
  def set(a: A): IO[EA, Unit]

  /**
   * Writes a new value to the `Ref` without providing a guarantee of
   * immediate consistency.
   */
  def setAsync(a: A): IO[EA, Unit]

  /**
   * Atomically modifies the `Ref` with the specified function. This is not
   * implemented in terms of `modify` purely for performance reasons.
   */
  def update[A1 <: A](f: A => A1): IO[EB, B]

  /**
   * Atomically modifies the `Ref` with the specified partial function.
   * if the function is undefined in the current value it returns the old value without changing it.
   */
  def updateSome[A1 <: A](pf: PartialFunction[A, A1]): IO[EB, B]
}

private[zio] trait ZRef_Functions {

}

object Ref extends ZRef_Functions {
  private def unsafeCreate[A](value: AtomicReference[A]): ZRef[Nothing, Nothing, A, A] =
    new ZRef[Nothing, Nothing, A, A] {
      def get: UIO[A] = UIO.effectTotal(value.get)

      def modify[X, A1 <: A](f: A => (X, A1)): UIO[X] = UIO.effectTotal {
        @tailrec def modify_in: X = {
          val current = value.get
          val (x, a1) = f(current)
          if (value.compareAndSet(current, a1)) x else modify_in
        }
        modify_in
      }

      def modifySome[X, A1 <: A](default: => X)(pf: PartialFunction[A, (X, A1)]): UIO[X] = UIO.effectTotal {
        @tailrec def modifySome_in: X = {
          val current = value.get
          val (x, a1) = pf.applyOrElse(current, (_: A) => (default, current))
          if (value.compareAndSet(current, a1)) x else modifySome_in
        }
        modifySome_in
      }

      def set(a: A): UIO[Unit] = UIO.effectTotal(value.set(a))

      def setAsync(a: A): UIO[Unit] = UIO.effectTotal(value.lazySet(a))

      def update[A1 <: A](f: A => A1): UIO[A] = UIO.effectTotal {
        @tailrec def update_in: A = {
          val current = value.get
          val a1      = f(current)
          if (value.compareAndSet(current, a1)) a1 else update_in
        }
        update_in
      }

      def updateSome[A1 <: A](pf: PartialFunction[A, A1]): UIO[A] = UIO.effectTotal {
        @tailrec def updateSome_in: A = {
          val current = value.get
          val a1      = pf.applyOrElse(current, (_: A) => current)
          if (value.compareAndSet(current, a1)) a1 else updateSome_in
        }
        updateSome_in
      }
    }

  def make[A](a: A): UIO[ZRef[Nothing, Nothing, A, A]] = UIO.effectTotal(new AtomicReference[A](a)).map(unsafeCreate)
}

object ZRef extends ZRef_Functions {
}
