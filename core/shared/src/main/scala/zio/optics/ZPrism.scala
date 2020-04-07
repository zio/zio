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

object ZPrism {

  /**
   * Constructs a prism from a `get` and a  `set` function.
   */
  def apply[S, T, A, B](get: S => Option[A], set: B => T): ZPrism[S, T, A, B] =
    ZOptic(s => get(s).toRight(()), b => _ => Right(set(b)))

  /**
   * A prism that accesses the `::` case of a `List`.
   */
  def cons[A, B]: ZPrism[List[A], List[B], (A, List[A]), (B, List[B])] =
    ZPrism(s => s match { case h :: t => Some((h, t)); case _ => None }, b => b._1 :: b._2)

  /**
   * A prism that accesses the `Left` case of an `Either`.
   */
  def left[A, B, C]: ZPrism[Either[A, B], Either[C, B], A, C] =
    ZPrism(s => s match { case Left(a) => Some(a); case _ => None }, b => Left(b))

  /**
   * A prism that accesses the `Right` case of an `Either`.
   */
  def right[A, B, C]: ZPrism[Either[A, B], Either[A, C], B, C] =
    ZPrism(s => s match { case Right(b) => Some(b); case _ => None }, b => Right(b))

  /**
   * A prism that accesses the `Some` case of an `Option`.
   */
  def some[A, B]: ZPrism[Option[A], Option[B], A, B] =
    ZPrism(s => s, b => Some(b))
}
