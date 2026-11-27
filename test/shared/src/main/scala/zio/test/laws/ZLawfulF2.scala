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
 * Lawful instances for type constructors with two parameters.
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
object ZLawfulF2 {

  /**
   * `ZLawful` for Divariant type constructors `F[-_, +_]`, such as `Function1`.
   * No `checkAllLaws` overload takes it: check it with `laws.run`, passing a
   * `GenF2` and a generator of `B` values.
   */
  trait Divariant[-CapsBoth[_[-_, +_]], -CapsLeft[_], -CapsRight[_], -R] { self =>

    /**
     * The laws that type constructors with the capability `CapsBoth` are
     * expected to satisfy.
     */
    def laws: ZLawsF2.Divariant[CapsBoth, CapsLeft, CapsRight, R]

    /**
     * Combines this lawful instance with `that`, giving a lawful instance whose
     * laws are `laws + that.laws`.
     */
    def +[CapsBoth1[x[-_, +_]] <: CapsBoth[x], CapsLeft1[x] <: CapsLeft[x], CapsRight1[x] <: CapsRight[x], R1 <: R](
      that: Divariant[CapsBoth1, CapsLeft1, CapsRight1, R1]
    ): Divariant[CapsBoth1, CapsLeft1, CapsRight1, R1] =
      new Divariant[CapsBoth1, CapsLeft1, CapsRight1, R1] {
        val laws = self.laws + that.laws
      }
  }
}
