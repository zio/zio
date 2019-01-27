// Copyright (C) 2017-2018 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.atomic.AtomicReference

/**
 * A mutable atomic reference for the `IO` monad. This is the `IO` equivalent of
 * a volatile `var`, augmented with atomic operations, which make it useful as a
 * reasonably efficient (if low-level) concurrency primitive.
 *
 * {{{
 * for {
 *   ref <- Ref(2)
 *   v   <- ref.update(_ + 3)
 *   _   <- putStrLn("Value = " + v) // Value = 5
 * } yield ()
 * }}}
 */
final class Ref[A] private (private val value: AtomicReference[A]) extends AnyVal with Serializable {

  /**
   * Reads the value from the `Ref`.
   */
  final def get: IO[Nothing, A] = IO.sync(value.get)

  /**
   * Writes a new value to the `Ref`, with a guarantee of immediate
   * consistency (at some cost to performance).
   */
  final def set(a: A): IO[Nothing, Unit] = IO.sync(value.set(a))

  /**
   * Writes a new value to the `Ref` without providing a guarantee of
   * immediate consistency.
   */
  final def setAsync(a: A): IO[Nothing, Unit] = IO.sync(value.lazySet(a))

  /**
   * Atomically modifies the `Ref` with the specified function. This is not
   * implemented in terms of `modify` purely for performance reasons.
   */
  final def update(f: A => A): UIO[A] = IO.sync {
    var loop    = true
    var next: A = null.asInstanceOf[A]

    while (loop) {
      val current = value.get

      next = f(current)

      loop = !value.compareAndSet(current, next)
    }

    next
  }

  /**
   * Atomically modifies the `Ref` with the specified partial function.
   * if the function is undefined in the current value it returns the old value without changing it.
   */
  final def updateSome(pf: PartialFunction[A, A]): IO[Nothing, A] = IO.sync {
    var loop    = true
    var next: A = null.asInstanceOf[A]

    while (loop) {
      val current = value.get

      next = pf.applyOrElse(current, (_: A) => current)

      loop = !value.compareAndSet(current, next)
    }

    next
  }

  /**
   * Atomically modifies the `Ref` with the specified function, which computes
   * a return value for the modification. This is a more powerful version of
   * `update`.
   */
  final def modify[B](f: A => (B, A)): IO[Nothing, B] = IO.sync {
    var loop = true
    var b: B = null.asInstanceOf[B]

    while (loop) {
      val current = value.get

      val tuple = f(current)

      b = tuple._1

      loop = !value.compareAndSet(current, tuple._2)
    }

    b
  }

  /**
   * Atomically modifies the `Ref` with the specified partial function, which computes
   * a return value for the modification if the function is defined in the current value
   * otherwise it returns a default value.
   * This is a more powerful version of `updateSome`.
   */
  final def modifySome[B](default: B)(pf: PartialFunction[A, (B, A)]): IO[Nothing, B] = IO.sync {
    var loop = true
    var b: B = null.asInstanceOf[B]

    while (loop) {
      val current = value.get

      val tuple = pf.applyOrElse(current, (_: A) => (default, current))

      b = tuple._1

      loop = !value.compareAndSet(current, tuple._2)
    }

    b
  }
}

object Ref extends Serializable {

  /**
   * Creates a new `Ref` with the specified value.
   */
  final def make[A](a: A): IO[Nothing, Ref[A]] = IO.sync(new Ref[A](new AtomicReference(a)))
}
