/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.{implicitNotFound, static}
import scala.util.NotGiven

/**
 * Evidence that the types `A` and `B` are different. A method that takes an
 * implicit `A =!= B` only compiles for types that the compiler does not see as
 * the same type.
 *
 * {{{
 * def different[A, B](implicit ev: A =!= B): String = "different"
 *
 * val ok: String = different[Int, String]
 * // different[Int, Int] does not compile
 * }}}
 */
@implicitNotFound("${A} must not be ${B}")
sealed abstract class =!=[A, B] extends Serializable

/**
 * The instances of `=!=`. On Scala 3, the instance exists when the compiler
 * cannot find evidence that `A =:= B`.
 */
object =!= {
  @static private val instance: =!=[Any, Any] = new =!=[Any, Any] {}

  /** Provides the evidence when there is no evidence that `A =:= B`. */
  given neq[A, B](using NotGiven[A =:= B]): =!=[A, B] = instance.asInstanceOf[=!=[A, B]]
}
