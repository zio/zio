/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

package zio.test.laws

import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * `ZLawful[Caps, R]` describes a capability that is expected to satisfy a set
 * of laws. Lawful instances can be combined using `+` to describe a set of
 * capabilities and all of the laws that those capabilities are expected to
 * satisfy.
 *
 * The companion object of a capability usually extends it (through the `Lawful`
 * alias when the laws need no environment), so that `checkAllLaws` can check an
 * instance against all of its laws.
 *
 * {{{
 * import zio.UIO
 * import zio.test._
 *
 * trait Equal[-A] {
 *   def equal(a1: A, a2: A): Boolean
 * }
 *
 * object Equal extends Lawful[Equal] {
 *   val reflexive: Laws.Law1[Equal] =
 *     new Laws.Law1[Equal]("reflexive") {
 *       def apply[A: Equal](a1: A): TestResult =
 *         assertTrue(implicitly[Equal[A]].equal(a1, a1))
 *     }
 *
 *   val laws: Laws[Equal] = reflexive
 *
 *   implicit val stringEqual: Equal[String] = new Equal[String] {
 *     def equal(a1: String, a2: String): Boolean = a1 == a2
 *   }
 * }
 *
 * val result: UIO[TestResult] = checkAllLaws(Equal)(Gen.alphaNumericString)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
trait ZLawful[-Caps[_], -R] { self =>

  /**
   * The laws that instances with the capability `Caps` are expected to satisfy.
   */
  def laws: ZLaws[Caps, R]

  /**
   * Combines this lawful instance with `that`, giving a lawful instance whose
   * laws are `laws + that.laws`. Checking the result needs one instance that
   * has both capabilities.
   */
  def +[Caps1[x] <: Caps[x], R1 <: R](that: ZLawful[Caps1, R1]): ZLawful[Caps1, R1] =
    new ZLawful[Caps1, R1] {
      val laws = self.laws + that.laws
    }
}
