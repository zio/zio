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
 *
 * NOTE: While `Ref` provides the functional equivalent of a mutable reference,
 * the value inside the `Ref` should be immutable. For performance reasons
 * `Ref` is implemented in terms of compare and swap operations rather than
 * synchronization. These operations are not safe for mutable values that do
 * not support concurrent access.
 */
trait ZRef[+EA, +EB, -A, +B] extends Serializable { self =>

  /**
   * Reads the value from the `Ref`.
   *
   * @return `IO[EB, B]` value from the `Ref`
   */
  def get: IO[EB, B]

  /**
   * Atomically writes the specified value to the `Ref`, returning the value
   * immediately before modification. This is not implemented in terms of
   * `modify` purely for performance reasons.
   *
   * @param a value to be written to the `Ref`
   * @return `IO[EA, B]` value of the `Ref` immediately before modification
   */
  def getAndSet(a: A): IO[EA, B]

  /**
   * Atomically modifies the `Ref` with the specified function, returning the
   * value immediately before modification. This is not implemented in terms of
   * `modify` purely for performance reasons.
   *
   * @param f function to atomically modify the `Ref`
   * @return `IO[E, B]` value of the `Ref` immediately before modification
   */
  def getAndUpdate[E](f: B => A)(implicit ev1: EA <:< E, ev2: EB <:< E): IO[E, B]

  /**
   * Atomically modifies the `Ref` with the specified partial function,
   * returning the value immediately before modification.
   * If the function is undefined on the current value it doesn't change it.
   *
   * @param pf partial function to atomically modify the `Ref`
   * @return `IO[E, B]` value of the `Ref` immediately before modification
   */
  def getAndUpdateSome[E](pf: PartialFunction[B, A])(implicit ev1: EA <:< E, ev2: EB <:< E): IO[E, B]

  /**
   * Atomically modifies the `Ref` with the specified function, which computes
   * a return value for the modification. This is a more powerful version of
   * `update`.
   *
   * @param f function which computes a return value for the modification
   * @tparam C type of the value of the `Ref` to be modified
   * @return `IO[E, C]` modified value the `Ref`
   */
  def modify[E, C](f: B => (C, A))(implicit ev1: EA <:< E, ev2: EB <:< E): IO[E, C]

  /**
   * Atomically modifies the `Ref` with the specified partial function, which computes
   * a return value for the modification if the function is defined in the current value
   * otherwise it returns a default value.
   * This is a more powerful version of `updateSome`.
   *
   * @param default default value to be returned if the partial function is not defined on the current value
   * @param pf partial function to be computed on the current value if defined on the current value
   * @tparam C type of the value of the `Ref` to be modified
   * @return `IO[E, C]` modified value of the `Ref`
   */
  def modifySome[E, C](default: C)(pf: PartialFunction[B, (C, A)])(implicit ev1: EA <:< E, ev2: EB <:< E): IO[E, C]

  /**
   * Writes a new value to the `Ref`, with a guarantee of immediate
   * consistency (at some cost to performance).
   *
   * @param a value to be written to the `Ref`
   * @return `IO[EA, Unit]`
   */
  def set(a: A): IO[EA, Unit]

  /**
   * Writes a new value to the `Ref` without providing a guarantee of
   * immediate consistency.
   *
   * @param a value to be written to the `Ref`
   * @return `IO[EA, Unit]`
   */
  def setAsync(a: A): IO[EA, Unit]

  /**
   * Atomically modifies the `Ref` with the specified function. This is not
   * implemented in terms of `modify` purely for performance reasons.
   *
   * @param f function to atomically modify the `Ref`
   * @return `IO[E, Unit]`
   */
  def update[E](f: B => A)(implicit ev1: EA <:< E, ev2: EB <:< E): IO[E, Unit] =
    modify(b => ((), f(b)))

  /**
   * Atomically modifies the `Ref` with the specified function and returns the
   * updated value. This is not implemented in terms of `modify` purely for
   * performance reasons.
   *
   * @param f function to atomically modify the `Ref`
   * @return `IO[E, B]` modified value of the `Ref`
   */
  def updateAndGet[E](f: B => A)(implicit ev1: EA <:< E, ev2: EB <:< E): IO[E, B] =
    modify(b => (b, f(b)))

  /**
   * Atomically modifies the `Ref` with the specified partial function.
   * If the function is undefined on the current value it doesn't change it.
   *
   * @param pf partial function to atomically modify the `Ref`
   * @return `IO[E, Unit]`
   */
  def updateSome[E](pf: PartialFunction[B, A])(implicit ev1: EA <:< E, ev2: EB <:< E): IO[E, Unit]

  /**
   * Atomically modifies the `Ref` with the specified partial function.
   * If the function is undefined on the current value it returns the old value
   * without changing it.
   *
   * @param pf partial function to atomically modify the `Ref`
   * @return `IO[E, B]` modified value of the `Ref`
   */
  def updateSomeAndGet[E](pf: PartialFunction[B, A])(implicit ev1: EA <:< E, ev2: EB <:< E): IO[E, B]

}
object ZRef extends Serializable {
  final case class Atomic[A](value: AtomicReference[A]) extends ZRef[Nothing, Nothing, A, A] {
    self =>

    def get: UIO[A] = IO.effectTotal(value.get)
    def getAndSet(a: A): UIO[A] = IO.effectTotal {
      var loop       = true
      var current: A = null.asInstanceOf[A]

      while (loop) {
        current = value.get

        loop = !value.compareAndSet(current, a)
      }

      current
    }
    def getAndUpdate[E](f: A => A)(implicit ev1: Nothing <:< E, ev2: Nothing <:< E): IO[E, A] = IO.effectTotal {
      var loop       = true
      var current: A = null.asInstanceOf[A]

      while (loop) {
        current = value.get

        val next = f(current)

        loop = !value.compareAndSet(current, next)
      }

      current
    }
    def getAndUpdateSome[E](pf: PartialFunction[A, A])(implicit ev1: Nothing <:< E, ev2: Nothing <:< E): IO[E, A] =
      IO.effectTotal {
        var loop       = true
        var current: A = null.asInstanceOf[A]

        while (loop) {
          current = value.get

          val next = pf.applyOrElse(current, (_: A) => current)

          loop = !value.compareAndSet(current, next)
        }

        current
      }
    def modify[E, C](f: A => (C, A))(implicit ev1: Nothing <:< E, ev2: Nothing <:< E): IO[E, C] = IO.effectTotal {
      var loop = true
      var b: C = null.asInstanceOf[C]

      while (loop) {
        val current = value.get

        val tuple = f(current)

        b = tuple._1

        loop = !value.compareAndSet(current, tuple._2)
      }

      b
    }
    def modifySome[E, C](
      default: C
    )(pf: PartialFunction[A, (C, A)])(implicit ev1: Nothing <:< E, ev2: Nothing <:< E): IO[E, C] = IO.effectTotal {
      var loop = true
      var b: C = null.asInstanceOf[C]

      while (loop) {
        val current = value.get

        val tuple = pf.applyOrElse(current, (_: A) => (default, current))

        b = tuple._1

        loop = !value.compareAndSet(current, tuple._2)
      }

      b
    }
    def set(a: A): UIO[Unit]      = IO.effectTotal(value.set(a))
    def setAsync(a: A): UIO[Unit] = IO.effectTotal(value.lazySet(a))
    override def toString: String =
      s"Ref(${value.get})"
    override def update[E](f: A => A)(implicit ev1: Nothing <:< E, ev2: Nothing <:< E): IO[E, Unit] = IO.effectTotal {
      var loop    = true
      var next: A = null.asInstanceOf[A]

      while (loop) {
        val current = value.get

        next = f(current)

        loop = !value.compareAndSet(current, next)
      }

      ()
    }
    override def updateAndGet[E](f: A => A)(implicit ev1: Nothing <:< E, ev2: Nothing <:< E): IO[E, A] =
      IO.effectTotal {
        var loop    = true
        var next: A = null.asInstanceOf[A]

        while (loop) {
          val current = value.get

          next = f(current)

          loop = !value.compareAndSet(current, next)
        }

        next
      }
    def updateSome[E](pf: PartialFunction[A, A])(implicit ev1: Nothing <:< E, ev2: Nothing <:< E): zio.IO[E, Unit] =
      IO.effectTotal {
        var loop    = true
        var next: A = null.asInstanceOf[A]

        while (loop) {
          val current = value.get

          next = pf.applyOrElse(current, (_: A) => current)

          loop = !value.compareAndSet(current, next)
        }

        ()
      }
    def updateSomeAndGet[E](pf: PartialFunction[A, A])(implicit ev1: Nothing <:< E, ev2: Nothing <:< E): IO[E, A] =
      IO.effectTotal {
        var loop    = true
        var next: A = null.asInstanceOf[A]

        while (loop) {
          val current = value.get

          next = pf.applyOrElse(current, (_: A) => current)

          loop = !value.compareAndSet(current, next)
        }

        next
      }

  }

  private final def unsafeCreate[E, A](a: A): ZRef[E, E, A, A] =
    new Atomic[A](new AtomicReference[A](a))

  final def make[E, A](a: A): UIO[ZRef[E, E, A, A]] = IO.effectTotal {
    unsafeCreate(a)
  }
}
