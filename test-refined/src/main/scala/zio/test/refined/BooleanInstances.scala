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
import eu.timepit.refined.boolean.Or
import zio.test.magnolia.DeriveGen
import zio.test.Gen
import zio.Random

/**
 * Brings the instances of `BooleanInstances` into scope: `import
 * zio.test.refined.boolean._`.
 */
object boolean extends BooleanInstances

/**
 * Instances for refined values whose predicate is `A Or B`.
 */
trait BooleanInstances {

  /**
   * The implicit `DeriveGen` for `Refined[T, A Or B]`. It takes the `DeriveGen`
   * of `Refined[T, A]` and of `Refined[T, B]`, picks one of the two with equal
   * probability for each value, and wraps the generated `T` as `Refined[T, A Or
   * B]`.
   */
  implicit def orDeriveGen[T, A, B](implicit
    raGen: DeriveGen[Refined[T, A]],
    rbGen: DeriveGen[Refined[T, B]]
  ): DeriveGen[Refined[T, A Or B]] = {
    val genA: Gen[Any, T] = raGen.derive.map(_.value)
    val genB: Gen[Any, T] = rbGen.derive.map(_.value)
    DeriveGen.instance(
      Gen.oneOf[Any, T](genA, genB).map(Refined.unsafeApply)
    )
  }

  /**
   * A generator of `Refined[T, A Or B]` that picks `genA` or `genB` with equal
   * probability for each value and wraps the generated `T` without checking the
   * predicate. `genA` and `genB` are generators of the underlying `T`, not of
   * refined values, and have the same type, so pass them explicitly.
   */
  def orGen[R <: Random, T, A, B](implicit
    genA: Gen[R, T],
    genB: Gen[R, T]
  ): Gen[R, Refined[T, A Or B]] = Gen.oneOf(genA, genB).map(Refined.unsafeApply)
}
