/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.optics

object ZOptional {

  /**
   * Constructs an optional from a `get` and a  `set` function.
   */
  def apply[S, T, A, B](get: S => Option[A], set: B => S => Option[T]): ZOptional[S, T, A, B] =
    ZOptic(s => get(s).toRight(()), b => s => set(b)(s).toRight(()))

  /**
   * An optional that accesses the head of a `List`.
   */
  def headOption[A]: Optional[List[A], A] =
    ZPrism.cons composeLens ZLens.first

  /**
   * An optional that accesses the specified index of a `Vector`.
   */
  def index[A](n: Int): Optional[Vector[A], A] =
    ZOptional(
      s => if (0 <= n && n < s.length) Some(s(n)) else None,
      a => s => if (0 <= n && n < s.length) Some(s.updated(n, a)) else None
    )
}
