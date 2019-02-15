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
      descriptor <- IO.descriptor
      value      <- state.get
    } yield value.get(descriptor.id)

  /**
   * Sets the value associated with the current fiber.
   */
  final def set(value: A): UIO[Unit] =
    for {
      descriptor <- IO.descriptor
      _          <- state.update(_ + (descriptor.id -> value))
    } yield ()

  /**
   * Empties the value associated with the current fiber.
   */
  final def empty: UIO[Unit] =
    for {
      descriptor <- IO.descriptor
      _          <- state.update(_ - descriptor.id)
    } yield ()

  /**
   * Returns an `IO` that runs with `value` bound to the current fiber.
   *
   * Guarantees that fiber-local data is properly freed via `bracket`.
   */
  final def locally[E, B](value: A)(use: IO[E, B]): IO[E, B] =
    set(value).bracket(_ => empty)(_ => use)

}

object FiberLocal {

  /**
   * Creates a new `FiberLocal`.
   */
  final def make[A]: UIO[FiberLocal[A]] =
    Ref
      .make[internal.State[A]](Map())
      .map(state => new FiberLocal(state))

  private[zio] object internal {
    type State[A] = Map[FiberId, A]
  }

}
