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
 *   v   <- ref.update[A0 <: A](_ + 3)
 *   _   <- console.putStrLn("Value = " + v) // Value = 5
 * } yield ()
 * }}}
 */
abstract class ZRef[+E, -A, +B] extends Serializable { self =>
  import ZRef._

  /**
   * Reads the value from the `ZRef`.
   */
  def get: IO[E, B]

  /**
   * Atomically modifies the `ZRef` with the specified function, which computes
   * a return value for the modification. This is a more powerful version of
   * `update`.
   */
  def modify[X]: ZRefModify[E, X, A]

  /**
   * Atomically modifies the `ZRef` with the specified partial function, which computes
   * a return value for the modification if the function is defined in the current value
   * otherwise it returns a default value.
   * This is a more powerful version of `updateSome`.
   */
  def modifySome[X](default: => X): ZRefPartialModify[E, X, A]

  /**
   * Writes a new value to the `ZRef`, with a guarantee of immediate
   * consistency (at some cost to performance).
   */
  def set(a: A): IO[E, Unit]

  /**
   * Writes a new value to the `ZRef` without providing a guarantee of
   * immediate consistency.
   */
  def setAsync(a: A): IO[E, Unit]

  /**
   * Atomically modifies the `ZRef` with the specified function. This is not
   * implemented in terms of `modify` purely for performance reasons.
   */
  def update: ZRefUpdate[E, A, B]

  /**
   * Atomically modifies the `ZRef` with the specified partial function.
   * if the function is undefined in the current value it returns the old value without changing it.
   */
  def updateSome: ZRefPartialUpdate[E, A, B]

  final def const[X](x: X): ZRef[E, A, X] = map(_ => x)

  final def map[C](g: B => C): ZRef[E, A, C] = new ZRef[E, A, C] {
    def get: IO[E, C] = self.get map g

    def modify[X]: ZRefModify[E, X, A] = self.modify[X]

    def modifySome[X](default: => X): ZRefPartialModify[E, X, A] = self.modifySome(default)

    def set(a: A): IO[E, Unit] = self.set(a)

    def setAsync(a: A): IO[E, Unit] = self.setAsync(a)

    def update: ZRefUpdate[E, A, C] = self.update map g

    def updateSome: ZRefPartialUpdate[E, A, C] = self.updateSome map g
  }

  final def unit: ZRef[E, A, Unit] = const(())

}

object ZRef {
  abstract class ZRefModify[E, X, A] { self =>
    def apply(f: A => (X, A)): IO[E, X]
  }

  abstract class ZRefUpdate[E, A, B] { self =>
    def apply(f: A => A): IO[E, B]

    def map[C](g: B => C): ZRefUpdate[E, A, C] = new ZRefUpdate[E, A, C] {
      def apply(f: A => A): IO[E, C] = self.apply(f) map g
    }
  }

  abstract class ZRefPartialModify[E, X, A] { self =>
    def apply(pf: PartialFunction[A, (X, A)]): IO[E, X]
  }

  abstract class ZRefPartialUpdate[E, A, B] { self =>
    def apply(pf: PartialFunction[A, A]): IO[E, B]

    def map[C](g: B => C): ZRefPartialUpdate[E, A, C] = new ZRefPartialUpdate[E, A, C] {
      def apply(pf: PartialFunction[A, A]): IO[E, C] = self.apply(pf) map g
    }
  }

  private def unsafeCreate[A](value: AtomicReference[A]): ZRef[Nothing, A, A] =
    new ZRef[Nothing, A, A] {
      def get: UIO[A] = UIO.effectTotal(value.get)

      def modify[X]: ZRefModify[Nothing, X, A] = new ZRefModify[Nothing, X, A] {
        def apply(f: A => (X, A)): UIO[X] = UIO.effectTotal {
          @tailrec def modify_in: X = {
            val current = value.get
            val (x, a1) = f(current)
            if (value.compareAndSet(current, a1)) x else modify_in
          }
          modify_in
        }
      }

      def modifySome[X](default: => X): ZRefPartialModify[Nothing, X, A] = new ZRefPartialModify[Nothing, X, A] {
        def apply(pf: PartialFunction[A, (X, A)]): UIO[X] = UIO.effectTotal {
          @tailrec def modifySome_in: X = {
            val current = value.get
            val (x, a1) = pf.applyOrElse(current, (_: A) => (default, current))
            if (value.compareAndSet(current, a1)) x else modifySome_in
          }
          modifySome_in
        }
      }

      def set(a: A): UIO[Unit] = UIO.effectTotal(value.set(a))

      def setAsync(a: A): UIO[Unit] = UIO.effectTotal(value.lazySet(a))

      def update: ZRefUpdate[Nothing, A, A] = new ZRefUpdate[Nothing, A, A] {
        def apply(f: A => A): UIO[A] = UIO.effectTotal {
          @tailrec def update_in: A = {
            val current = value.get
            val a1      = f(current)
            if (value.compareAndSet(current, a1)) a1 else update_in
          }
          update_in
        }
      }

      def updateSome: ZRefPartialUpdate[Nothing, A, A] = new ZRefPartialUpdate[Nothing, A, A] {
        def apply(pf: PartialFunction[A, A]): UIO[A] = UIO.effectTotal {
          @tailrec def updateSome_in: A = {
            val current = value.get
            val a1      = pf.applyOrElse(current, (_: A) => current)
            if (value.compareAndSet(current, a1)) a1 else updateSome_in
          }
          updateSome_in
        }
      }
    }

  def make[A](a: A): UIO[ZRef[Nothing, A, A]] = UIO.effectTotal(new AtomicReference[A](a)).map(unsafeCreate)
}
