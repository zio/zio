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

import scala.annotation.targetName

private[zio] trait UnsafeVersionSpecific {

  transparent inline def unsafe[A](inline f: Unsafe => A): A =
    f(Unsafe.unsafe)

  transparent inline def unsafely[A](inline f: Unsafe ?=> A): A =
    f(using Unsafe.unsafe)

  implicit def implicitFunctionIsFunction[A](f: Unsafe ?=> A): Unsafe => A =
    f(using _)

  @targetName("unsafe")
  @deprecated("use unsafe", "2.1.7")
  def unsafeCompat0[A](f: Unsafe => A): A =
    unsafe(f)

  @targetName("unsafely")
  @deprecated("use unsafely", "2.1.7")
  def unsafelyCompat[A](f: Unsafe ?=> A): A =
    unsafely(f)

}
