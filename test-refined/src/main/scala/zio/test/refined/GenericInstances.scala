/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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

package zio.test.refined

import eu.timepit.refined.api.Refined
import eu.timepit.refined.generic.Equal
import shapeless.Witness
import zio.test.magnolia.DeriveGen
import zio.test.Gen

/**
 * Brings the instances of `GenericInstances` into scope: `import
 * zio.test.refined.generic._`.
 */
object generic extends GenericInstances

/**
 * Instances for the `Equal[U]` predicate of `eu.timepit.refined.generic`. On
 * Scala 3, implicit search does not find the implicit instance for a literal
 * `U` such as `Refined[Int, Equal[3]]`; on Scala 2 it does.
 */
trait GenericInstances {

  /**
   * A generator of `Refined[T, Equal[U]]` that always produces the value of the
   * witness `U`.
   */
  def equalArbitraryGen[T, U <: T](implicit
    wu: Witness.Aux[U]
  ): Gen[Any, Refined[T, Equal[U]]] =
    Gen.const(wu.value).map(Refined.unsafeApply)

  /**
   * The implicit `DeriveGen` for `Refined[T, Equal[U]]`; it wraps
   * `equalArbitraryGen`.
   */
  implicit def equalArbitrary[T, U <: T](implicit
    wu: Witness.Aux[U]
  ): DeriveGen[Refined[T, Equal[U]]] =
    DeriveGen.instance(equalArbitraryGen(wu))
}
