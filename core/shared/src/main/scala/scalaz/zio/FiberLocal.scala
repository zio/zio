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

package scalaz.zio

import FiberLocal.internal._
import scalaz.zio

/**
 * A container for fiber-local storage. It is the pure equivalent to Java's `ThreadLocal`
 * on a fiber architecture.
 */
final class FiberLocal[A] private (private val state: Ref[State[A]]) extends Serializable {

  /**
   * Reads the value associated with the current fiber.
   */
  final def get: UIO[Option[A]] =
    for {
      maybeFiberId <- new ZIO.GetLocal(this)
      value        <- state.get
    } yield maybeFiberId.flatMap(value.get)

  /**
   * Sets the value associated with the current fiber.
   */
  final def set(value: A): UIO[Unit] =
    for {
      fiberId <- new ZIO.SetLocal(this)
      _       <- state.update(_ + (fiberId -> value))
    } yield ()

  /**
   * Empties the value associated with the current fiber.
   */
  final def empty: UIO[Unit] =
    for {
      fiberId <- new ZIO.SetLocal(this)
      _       <- state.update(_ - fiberId)
    } yield ()

  /**
   * Returns an `IO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber-local data is properly freed via `bracket`.
   */
  final def locally[R, E, B](value: A)(use: ZIO[R, E, B]): ZIO[R, E, B] =
    set(value).bracket_[Any, Nothing].apply[Any](empty)(use) //    TODO: Dotty doesn't infer this properly
}

object FiberLocal {

  /**
   * Creates a new `FiberLocal`.
   */
  @deprecated("Use explicit make[A](initValue).", "1.0-RC5")
  final def make[A]: UIO[FiberLocal[Option[A]]] = make(None)

  /**
   * Creates a new `FiberLocal`.
   */
  final def make[A](initValue: A): UIO[FiberLocal[A]] =
    ???

  private[zio] object internal {
    type State[A] = Map[FiberId, A]
  }

}
