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

object Prism {

  /**
   * @see See [[zio.optics.ZPrism.apply]]
   */
  def apply[S, A](get: S => Option[A], set: A => S): Prism[S, A] =
    ZPrism(get, set)

  /**
   * @see See [[zio.optics.ZPrism.cons]]
   */
  def cons[A, B]: Prism[List[A], (A, List[A])] =
    ZPrism.cons

  /**
   * @see See [[zio.optics.ZPrism.left]]
   */
  def left[A, B]: Prism[Either[A, B], A] =
    ZPrism.left

  /**
   * @see See [[zio.optics.ZPrism.right]]
   */
  def right[A, B]: Prism[Either[A, B], B] =
    ZPrism.right

  /**
   * @see See [[zio.optics.ZPrism.some]]
   */
  def some[A]: Prism[Option[A], A] =
    ZPrism.some
}
