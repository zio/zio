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

object Optional {

  /**
   * @see See [[zio.optics.ZOptional.apply]]
   */
  def apply[S, A](get: S => Option[A], set: A => S => Option[S]): Optional[S, A] =
    ZOptional(get, set)

  /**
   * @see See [[zio.optics.ZOptional.index]]
   */
  def headOption[A]: Optional[List[A], A] =
    ZOptional.headOption

  /**
   * @see See [[zio.optics.ZOptional.index]]
   */
  def index[A](n: Int): Optional[Vector[A], A] =
    ZOptional.index(n)
}
