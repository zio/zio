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

object ZLens {

  /**
   * Constructs a lens from a `get` and a  `set` function.
   */
  def apply[S, T, A, B](get: S => A, set: B => S => T): ZLens[S, T, A, B] =
    ZOptic(s => Right(get(s)), b => s => Right(set(b)(s)))

  /**
   * A lens that accesses the first element of a tuple.
   */
  def first[A, B, C]: ZLens[(A, B), (C, B), A, C] =
    ZLens(s => s._1, c => s => s.copy(_1 = c))

  /**
   * A lens that accesses the second element of a tuple.
   */
  def second[A, B, C]: ZLens[(A, B), (A, C), B, C] =
    ZLens(s => s._2, c => s => s.copy(_2 = c))
}
