/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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

/**
 * Provides the Scala-version-specific members of the `Unsafe` object. It has no
 * members on Scala 2; Scala 3 adds `unsafely`.
 */
private[zio] transparent trait UnsafeVersionSpecific {

  /**
   * Calls `f` with the `Unsafe` capability in scope and returns its result.
   *
   * {{{
   * // scala-3-only
   * val answer: Exit[Nothing, Int] =
   *   Unsafe.unsafely {
   *     Runtime.default.unsafe.run(ZIO.succeed(42))
   *   }
   * // Success(42)
   * }}}
   */
  def unsafely[A](f: Unsafe ?=> A): A =
    f(using Unsafe)

  /**
   * Converts a context function `Unsafe ?=> A` into a function `Unsafe => A`.
   * The resulting function ignores its argument and calls `f` with the `Unsafe`
   * value, so a context-function block can be passed where an `Unsafe => A` is
   * expected.
   */
  implicit def implicitFunctionIsFunction[A](f: Unsafe ?=> A): Unsafe => A =
    _ => f(using Unsafe)
}
