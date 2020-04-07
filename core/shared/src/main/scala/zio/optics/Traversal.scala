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

object Traversal {

  /**
   * @see See [[zio.optics.ZTraversal.apply]]
   */
  def apply[S, A](get: S => List[A], set: List[A] => S => Option[S]): Traversal[S, A] =
    ZTraversal(get, set)

  /**
   * @see See [[zio.optics.ZTraversal.filter]]
   */
  def filter[A](f: A => Boolean): Traversal[List[A], A] =
    ZTraversal.filter(f)

  /**
   * @see See [[zio.optics.ZTraversal.slice]]
   */
  def slice[A](from: Int, until: Int): Traversal[Vector[A], A] =
    ZTraversal.slice(from, until)
}
