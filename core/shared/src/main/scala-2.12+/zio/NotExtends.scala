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

package zio

trait NotExtends[A, B] extends Serializable

object NotExtends {
  implicit def notExtends0[A, B]: A NotExtends B      = new NotExtends[A, B] {}
  implicit def notExtends1[A <: B, B]: A NotExtends B = ???
  @annotation.implicitAmbiguous(
    "The environment ${A} already contains service ${B}, are you sure you want to overwrite it? Use Has#update to update a service already inside the environment."
  )
  implicit def notExtends2[A <: B, B]: A NotExtends B = ???
}
