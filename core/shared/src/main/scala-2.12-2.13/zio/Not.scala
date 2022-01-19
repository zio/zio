/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

import scala.annotation.implicitAmbiguous

/**
 * Provides implicit evidence that an instance of `A` is not in implicit scope.
 */
@implicitAmbiguous("Implicit ${A} defined.")
sealed trait Not[A]

object Not {

  /**
   * Derives a `Not[A]` instance when an instance of `A` is not in implciit
   * scope.
   */
  implicit def Not[A]: Not[A] =
    new Not[A] {}

  /**
   * Derives a `Not[A]` instance when an instance of `A` is in implicit scope.
   * Together with the instance defined below this will cause implicit search to
   * fail due to ambiguous implicits, preventing an instance of `Not[A]` from
   * being in implicit scope when an instance of `A` is in implicit scope.
   */
  implicit def NotAmbiguous1[A](implicit ev: A): Not[A] =
    new Not[A] {}

  /**
   * Derives a `Not[A]` instance when an instance of `A` is in implicit scope.
   * Together with the instance defined above this will cause implicit search to
   * fail due to ambiguous implicits, preventing an instance of `Not[A]` from
   * being in implicit scope when an instance of `A` is in implicit scope.
   */
  implicit def NotAmbiguous2[A](implicit ev: A): Not[A] =
    new Not[A] {}
}
