/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
 * Copyright 2013-2021 Miles Sabin
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

import scala.annotation.implicitAmbiguous

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
 *
 * Based on https://github.com/milessabin/shapeless.
 */
sealed abstract class =!=[A, B] extends Serializable

/**
 * The instances of `=!=`. On Scala 2, `A =!= A` has two ambiguous candidates,
 * so no instance is found when the two types are the same.
 */
object =!= {
  private val instance: =!=[Any, Any] = new =!=[Any, Any] {}

  /**
   * Throws a `java.lang.RuntimeException` with the message "Unexpected
   * invocation".
   */
  def unexpected: Nothing = sys.error("Unexpected invocation")

  /** Provides the evidence for any two types `A` and `B`. */
  implicit def neq[A, B]: A =!= B = instance.asInstanceOf[=!=[A, B]]

  /**
   * Makes `A =!= A` ambiguous together with `neqAmbig2` and `neq`, so that no
   * evidence is found when the two types are the same. For `Int` the compiler
   * reports "Cannot prove that Int =!= Int".
   */
  @implicitAmbiguous("Cannot prove that ${A} =!= ${A}")
  implicit def neqAmbig1[A]: A =!= A = unexpected

  /** The second candidate that makes `A =!= A` ambiguous, with `neqAmbig1`. */
  implicit def neqAmbig2[A]: A =!= A = unexpected
}
