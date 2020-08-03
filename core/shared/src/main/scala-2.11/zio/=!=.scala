/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
 * Copyright 2013-2020 Miles Sabin
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

import scala.annotation.implicitNotFound

/**
 * Evidence type `A` is not equal to type `B`.
 *
 * Based on https://github.com/milessabin/shapeless.
 */
@implicitNotFound("${A} must not be ${B}")
abstract class =!=[A, B] extends Serializable

object =!= {
  def unexpected: Nothing = sys.error("Unexpected invocation")

  implicit def neq[A, B]: A =!= B    = new =!=[A, B] {}
  implicit def neqAmbig1[A]: A =!= A = unexpected
  implicit def neqAmbig2[A]: A =!= A = unexpected
}
